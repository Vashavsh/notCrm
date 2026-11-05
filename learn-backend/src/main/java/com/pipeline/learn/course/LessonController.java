package com.pipeline.learn.course;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pipeline.learn.auth.LearnPrincipal;

@RestController
@RequestMapping("/api/lessons/{lessonId}")
public class LessonController {

    private final LessonService lessons;

    public LessonController(LessonService lessons) {
        this.lessons = lessons;
    }

    @GetMapping
    public Map<String, Object> open(@PathVariable String lessonId, @AuthenticationPrincipal LearnPrincipal student) {
        return lessons.open(student.accountId(), lessonId);
    }

    @PostMapping("/answers")
    public Map<String, Object> answer(@PathVariable String lessonId, @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal LearnPrincipal student) {
        return lessons.answer(student.accountId(), lessonId, body);
    }

    @PostMapping("/drafts")
    public Map<String, Object> draft(@PathVariable String lessonId, @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal LearnPrincipal student) {
        return lessons.saveDraft(student.accountId(), lessonId, body);
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(@PathVariable String lessonId, @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal LearnPrincipal student) {
        return lessons.complete(student.accountId(), lessonId, body);
    }

    @PostMapping("/restart")
    public Map<String, Object> restart(@PathVariable String lessonId, @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal LearnPrincipal student) {
        return lessons.restart(student.accountId(), lessonId, body);
    }
}
