package com.pipeline.learn.course;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.learn.account.Account;
import com.pipeline.learn.account.AccountRepository;
import com.pipeline.learn.common.ApiException;
import com.pipeline.learn.course.CourseContent.Lesson;

/**
 * Everything a student does inside a lesson. Every write first locks the student's account row, so two
 * browser tabs or a double click are handled one after the other. Every write also carries the id of the
 * attempt the page was showing; if the student restarted the lesson in another tab the id no longer matches
 * and the write is refused with 409, instead of landing in an attempt the student is not looking at.
 */
@Service
public class LessonService {

    private static final Set<String> PRIVATE_QUESTION_KEYS = Set.of("answer", "correct", "tolerance", "feedback", "wrongFeedback");
    private static final Set<String> SELF_ASSESSMENTS = Set.of("correct", "partial", "incorrect");

    private final CourseContent content;
    private final AccountRepository accounts;
    private final LessonProgressRepository progressRepository;
    private final AttemptRepository attemptRepository;
    private final AttachmentRepository attachmentRepository;
    private final Grader grader;
    private final ObjectMapper json;

    public LessonService(CourseContent content, AccountRepository accounts, LessonProgressRepository progressRepository,
            AttemptRepository attemptRepository, AttachmentRepository attachmentRepository, Grader grader, ObjectMapper json) {
        this.content = content;
        this.accounts = accounts;
        this.progressRepository = progressRepository;
        this.attemptRepository = attemptRepository;
        this.attachmentRepository = attachmentRepository;
        this.grader = grader;
        this.json = json;
    }

    /** Opening a lesson: remembers the visit (first and last time) and returns the lesson with the student's state. */
    @Transactional
    public Map<String, Object> open(UUID accountId, String lessonId) {
        Lesson lesson = lesson(lessonId);
        Account account = lock(accountId);
        LessonProgress progress = currentProgress(account.getId(), lessonId);
        progress.markOpened(Instant.now());
        progressRepository.save(progress);
        return detail(account, lesson, progress);
    }

    @Transactional
    public Map<String, Object> answer(UUID accountId, String lessonId, Map<String, Object> data) {
        Lesson lesson = lesson(lessonId);
        String questionId = questionId(lesson, data);
        Map<String, Object> question = lesson.questions().get(questionId);
        Account account = lock(accountId);
        LessonProgress progress = currentProgress(account.getId(), lessonId);
        Attempt attempt = writableAttempt(progress, data);

        // Submitting the same question again changes nothing: the first answer stands.
        Map<String, Object> existing = attempt.getAnswers().get(questionId) == null ? null
                : new LinkedHashMap<>(attempt.getAnswers().get(questionId));
        if (existing == null) {
            existing = grader.grade(question, data.get("value"),
                    attachmentRepository.existsByAttemptIdAndQuestionId(attempt.getId(), questionId));
        }
        Object assessment = data.get("selfAssessment");
        if (assessment != null) {
            if (!"open".equals(question.get("kind")) || !Boolean.TRUE.equals(question.get("requiresSelfAssessment"))) {
                throw bad("Самооценка не требуется для этого задания.");
            }
            if (!(assessment instanceof String s) || !SELF_ASSESSMENTS.contains(s)) {
                throw bad("Выберите самооценку ответа.");
            }
            if (existing.get("selfAssessment") == null) {
                existing.put("selfAssessment", s(assessment));
            }
        }
        attempt.putAnswer(questionId, existing);
        attempt.removeDraft(questionId);
        attemptRepository.save(attempt);
        return detail(account, lesson, progress);
    }

    @Transactional
    public Map<String, Object> saveDraft(UUID accountId, String lessonId, Map<String, Object> data) {
        Lesson lesson = lesson(lessonId);
        String questionId = questionId(lesson, data);
        String kind = (String) lesson.questions().get(questionId).get("kind");
        Object value = data.get("value");
        if (!(kind.equals("open") || kind.equals("number")) || !(value instanceof String text) || text.length() > 30000) {
            throw bad("Некорректный черновик ответа.");
        }
        Account account = lock(accountId);
        LessonProgress progress = currentProgress(account.getId(), lessonId);
        Attempt attempt = writableAttempt(progress, data);
        if (attempt.getAnswers().containsKey(questionId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ответ уже отправлен.");
        }
        attempt.putDraft(questionId, text);
        attemptRepository.save(attempt);
        return Map.of("saved", true);
    }

    /** The student's own "lesson passed" mark. It does not depend on the answers. */
    @Transactional
    public Map<String, Object> complete(UUID accountId, String lessonId, Map<String, Object> data) {
        Lesson lesson = lesson(lessonId);
        Account account = lock(accountId);
        LessonProgress progress = currentProgress(account.getId(), lessonId);
        Attempt attempt = writableAttempt(progress, data);
        attempt.markCompleted(Instant.now());
        attemptRepository.save(attempt);
        progress.markCompleted(attempt.getCompletedAt());
        progressRepository.save(progress);
        return detail(account, lesson, progress);
    }

    /** "Start again": a new empty attempt; the old attempts and the passed mark stay. */
    @Transactional
    public Map<String, Object> restart(UUID accountId, String lessonId, Map<String, Object> data) {
        Lesson lesson = lesson(lessonId);
        Account account = lock(accountId);
        LessonProgress progress = currentProgress(account.getId(), lessonId);
        writableAttempt(progress, data);
        Attempt fresh = attemptRepository.saveAndFlush(new Attempt(progress.getId()));
        progress.setCurrentAttemptId(fresh.getId());
        progressRepository.save(progress);
        return detail(account, lesson, progress);
    }

    // ---- helpers shared with the attachment endpoints ----

    Account lock(UUID accountId) {
        return accounts.lockById(accountId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Войдите в аккаунт."));
    }

    Lesson lesson(String lessonId) {
        return content.lesson(lessonId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Урок не найден."));
    }

    /** The student's progress row for the lesson, created with its first attempt on first use. Caller holds the account lock. */
    LessonProgress currentProgress(UUID accountId, String lessonId) {
        LessonProgress progress = progressRepository.findByAccountIdAndLessonId(accountId, lessonId)
                .orElseGet(() -> progressRepository.saveAndFlush(new LessonProgress(accountId, lessonId)));
        if (progress.getCurrentAttemptId() == null) {
            Attempt attempt = attemptRepository.saveAndFlush(new Attempt(progress.getId()));
            progress.setCurrentAttemptId(attempt.getId());
            progress = progressRepository.save(progress);
        }
        return progress;
    }

    /** The attempt the page says it is showing; anything else is a stale page. */
    Attempt writableAttempt(LessonProgress progress, Map<String, Object> data) {
        Object supplied = data.get("attemptId");
        boolean isInteger = supplied instanceof Integer || supplied instanceof Long;
        if (!isInteger || ((Number) supplied).longValue() != progress.getCurrentAttemptId()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Попытка изменилась в другой вкладке. Скопируйте несохранённый текст и обновите урок.", "attempt_conflict");
        }
        return attemptRepository.findById(progress.getCurrentAttemptId()).orElseThrow();
    }

    String questionId(Lesson lesson, Map<String, Object> data) {
        if (!(data.get("questionId") instanceof String id)) {
            throw bad("Не указан вопрос.");
        }
        if (!lesson.questions().containsKey(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Вопрос не найден.");
        }
        return id;
    }

    static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static String s(Object value) {
        return (String) value;
    }

    // ---- the response ----

    Map<String, Object> detail(Account account, Lesson lesson, LessonProgress progress) {
        Attempt attempt = attemptRepository.findById(progress.getCurrentAttemptId()).orElseThrow();
        String name = account.getFullName();

        Map<String, Object> answers = new LinkedHashMap<>();
        attempt.getAnswers().forEach((id, item) -> {
            Map<String, Object> copy = new LinkedHashMap<>(item);
            copy.put("feedback", Personalizer.personalize(item.get("feedback") instanceof String f ? f : "", name));
            answers.put(id, copy);
        });
        for (AttachmentRef ref : attachmentRepository.refsOf(attempt.getId())) {
            Map<String, Object> item = answers.get(ref.questionId()) instanceof Map<?, ?> existing
                    ? asMap(existing) : emptyAnswer(attempt.getDrafts().getOrDefault(ref.questionId(), ""));
            item.put("attachmentUrl", "/api/attachments/" + ref.id());
            answers.put(ref.questionId(), item);
        }

        Map<String, Object> attemptView = new LinkedHashMap<>();
        attemptView.put("id", attempt.getId());
        attemptView.put("answers", answers);
        attemptView.put("drafts", attempt.getDrafts());
        attemptView.put("completed", progress.getCompletedAt() != null);

        Map<String, Object> progressView = new LinkedHashMap<>();
        progressView.put("openedAt", progress.getOpenedAt());
        progressView.put("lastOpenedAt", progress.getLastOpenedAt());
        progressView.put("completedAt", progress.getCompletedAt());

        List<Lesson> all = content.lessons();
        int position = all.indexOf(lesson);
        Map<String, Object> navigation = new LinkedHashMap<>();
        navigation.put("previous", position > 0 ? all.get(position - 1).id() : null);
        navigation.put("next", position + 1 < all.size() ? all.get(position + 1).id() : null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lesson", publicLesson(lesson, name));
        result.put("attempt", attemptView);
        result.put("progress", progressView);
        result.put("navigation", navigation);
        return result;
    }

    private static Map<String, Object> emptyAnswer(String draft) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", draft);
        item.put("correct", null);
        item.put("feedback", "");
        item.put("selfAssessment", null);
        item.put("submitted", false);
        return item;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<?, ?> map) {
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    /** A copy of the lesson for the student: no answers, no explanations, the placeholder name replaced. */
    Map<String, Object> publicLesson(Lesson lesson, String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", lesson.id());
        result.put("moduleId", lesson.moduleId());
        result.put("order", lesson.order());
        result.put("title", lesson.title());
        result.put("summary", lesson.summary());
        result.put("minutes", lesson.minutes());
        result.put("kind", lesson.kind());
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Map<String, Object> original : lesson.sections()) {
            Map<String, Object> section = json.convertValue(original, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            if ("question".equals(section.get("type"))) {
                PRIVATE_QUESTION_KEYS.forEach(section::remove);
            }
            for (String key : List.of("html", "prompt")) {
                if (section.get(key) instanceof String text) {
                    section.put(key, Personalizer.personalize(text, name));
                }
            }
            sections.add(section);
        }
        result.put("sections", sections);
        return result;
    }
}
