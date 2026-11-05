package com.pipeline.learn.course;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.pipeline.learn.common.ApiException;
import com.pipeline.learn.course.CourseContent.Lesson;

/** Pictures attached to open answers (graphs, screenshots). One picture per question and attempt; a new one replaces the old. */
@Service
public class AttachmentService {

    private final LessonService lessons;
    private final ImageSanitizer sanitizer;
    private final AttachmentRepository attachments;
    private final TransactionTemplate transaction;

    public AttachmentService(LessonService lessons, ImageSanitizer sanitizer, AttachmentRepository attachments,
            TransactionTemplate transaction) {
        this.lessons = lessons;
        this.sanitizer = sanitizer;
        this.attachments = attachments;
        this.transaction = transaction;
    }

    public Map<String, Object> upload(UUID accountId, String lessonId, String questionId, String attemptId, MultipartFile file) {
        Lesson lesson = lessons.lesson(lessonId);
        Map<String, Object> request = new HashMap<>();
        request.put("questionId", questionId);
        String id = lessons.questionId(lesson, request);
        if (!"open".equals(lesson.questions().get(id).get("kind"))) {
            throw LessonService.bad("Изображение можно прикрепить к открытому заданию.");
        }
        if (file == null || file.isEmpty() || file.getSize() > ImageSanitizer.MAX_BYTES) {
            throw LessonService.bad("Выберите изображение PNG, JPEG или WebP размером до 6 МБ.");
        }
        byte[] png;
        try {
            png = sanitizer.toPng(file.getBytes());
        } catch (IOException e) {
            throw LessonService.bad("Файл не удалось прочитать как изображение.");
        }

        // In a multipart form the attempt id is a decimal string.
        Map<String, Object> data = new HashMap<>();
        data.put("attemptId", attemptId != null && attemptId.matches("[0-9]{1,18}") ? Long.valueOf(attemptId) : null);

        return transaction.execute(status -> {
            lessons.lock(accountId);
            LessonProgress progress = lessons.currentProgress(accountId, lessonId);
            Attempt attempt = lessons.writableAttempt(progress, data);
            if (attempt.getAnswers().containsKey(id)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Ответ уже отправлен. Для нового изображения начните повторное прохождение.");
            }
            Attachment saved = attachments.saveAndFlush(new Attachment(attempt.getId(), id, png));
            attachments.deleteOthers(attempt.getId(), id, saved.getId());
            return Map.<String, Object>of("attachmentUrl", "/api/attachments/" + saved.getId());
        });
    }
}
