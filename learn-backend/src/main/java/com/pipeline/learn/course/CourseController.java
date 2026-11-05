package com.pipeline.learn.course;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pipeline.learn.auth.LearnPrincipal;

@RestController
public class CourseController {

    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    @GetMapping("/api/course")
    public CourseService.CourseResponse course(@AuthenticationPrincipal LearnPrincipal student) {
        return courses.course(student.accountId());
    }
}
