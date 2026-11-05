package com.pipeline.learn.course;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pipeline.learn.course.CourseContent.Lesson;

@Service
public class CourseService {

    public record LessonCard(String id, String title, int order, Integer minutes, String summary, String kind,
            String status, int answered, int total) {
    }

    public record ModuleView(String id, int stage, int order, String title, String description, boolean available,
            List<LessonCard> lessons) {
    }

    public record ProgressSummary(int completed, int total, String resumeLessonId) {
    }

    public record CourseResponse(List<ModuleView> modules, ProgressSummary progress) {
    }

    private final CourseContent content;
    private final LessonProgressRepository progressRepository;
    private final AttemptRepository attemptRepository;

    public CourseService(CourseContent content, LessonProgressRepository progressRepository,
            AttemptRepository attemptRepository) {
        this.content = content;
        this.progressRepository = progressRepository;
        this.attemptRepository = attemptRepository;
    }

    /**
     * The course map for one student: every module with its lessons and the student's status in each.
     * All lessons are open to every active student (the whole course belongs to the first CRM stage), so
     * there is no "locked" status here; the stage of a block is reported but not enforced.
     */
    @Transactional(readOnly = true)
    public CourseResponse course(UUID accountId) {
        Map<String, LessonProgress> progress = progressRepository.findByAccountId(accountId).stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, p -> p));
        List<Long> attemptIds = progress.values().stream().map(LessonProgress::getCurrentAttemptId)
                .filter(id -> id != null).toList();
        Map<Long, Attempt> attempts = new HashMap<>();
        attemptRepository.findAllById(attemptIds).forEach(a -> attempts.put(a.getId(), a));

        List<LessonCard> cards = new ArrayList<>();
        Map<String, List<LessonCard>> byModule = new HashMap<>();
        int completed = 0;
        String resume = null;
        for (Lesson lesson : content.lessons()) {
            LessonProgress p = progress.get(lesson.id());
            Attempt attempt = p == null || p.getCurrentAttemptId() == null ? null : attempts.get(p.getCurrentAttemptId());
            boolean done = p != null && p.getCompletedAt() != null;
            int answered = 0;
            if (attempt != null) {
                for (Map.Entry<String, Map<String, Object>> answer : attempt.getAnswers().entrySet()) {
                    if (lesson.questions().containsKey(answer.getKey()) && Boolean.TRUE.equals(answer.getValue().get("submitted"))) {
                        answered++;
                    }
                }
            }
            String status = done ? "completed" : attempt != null ? "in_progress" : "available";
            if (done) {
                completed++;
            } else if (resume == null) {
                resume = lesson.id();
            }
            LessonCard card = new LessonCard(lesson.id(), lesson.title(), lesson.order(), lesson.minutes(),
                    lesson.summary(), lesson.kind(), status, answered, lesson.questions().size());
            cards.add(card);
            byModule.computeIfAbsent(lesson.moduleId(), k -> new ArrayList<>()).add(card);
        }

        // Continue where the student last was: the most recently opened lesson that is not finished.
        String recent = progress.values().stream()
                .filter(p -> p.getCompletedAt() == null && p.getLastOpenedAt() != null && content.lesson(p.getLessonId()).isPresent())
                .max(Comparator.comparing(LessonProgress::getLastOpenedAt))
                .map(LessonProgress::getLessonId).orElse(null);
        if (recent != null) {
            resume = recent;
        }
        if (resume == null && !cards.isEmpty()) {
            resume = cards.get(cards.size() - 1).id();
        }

        List<ModuleView> modules = new ArrayList<>();
        int order = 1;
        for (CourseContent.Module module : CourseContent.MODULES) {
            List<LessonCard> lessons = byModule.getOrDefault(module.id(), List.of());
            modules.add(new ModuleView(module.id(), content.stageOf(module.id()), order++, module.title(),
                    module.description(), !lessons.isEmpty(), lessons));
        }
        return new CourseResponse(modules, new ProgressSummary(completed, cards.size(), resume));
    }
}
