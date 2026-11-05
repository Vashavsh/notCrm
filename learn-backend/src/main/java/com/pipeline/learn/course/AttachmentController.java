package com.pipeline.learn.course;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pipeline.learn.auth.LearnPrincipal;
import com.pipeline.learn.common.ApiException;

@RestController
public class AttachmentController {

    private final AttachmentService service;
    private final AttachmentRepository attachments;

    public AttachmentController(AttachmentService service, AttachmentRepository attachments) {
        this.service = service;
        this.attachments = attachments;
    }

    @PostMapping("/api/lessons/{lessonId}/attachment")
    public Map<String, Object> upload(@PathVariable String lessonId,
            @RequestParam(required = false) String questionId, @RequestParam(required = false) String attemptId,
            @RequestParam(value = "file", required = false) MultipartFile file, @AuthenticationPrincipal LearnPrincipal student) {
        return service.upload(student.accountId(), lessonId, questionId, attemptId, file);
    }

    /** A picture is shown only to the student whose answer it belongs to. */
    @GetMapping("/api/attachments/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal LearnPrincipal student) {
        Attachment attachment = attachments.findOwned(id, student.accountId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Изображение не найдено."));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"graph.png\"")
                .body(attachment.getImage());
    }
}
