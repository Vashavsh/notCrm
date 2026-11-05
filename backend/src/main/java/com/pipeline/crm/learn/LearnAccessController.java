package com.pipeline.crm.learn;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pipeline.crm.security.CurrentUser;
import com.pipeline.crm.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/students/{studentId}/learn")
@RequiredArgsConstructor
public class LearnAccessController {

    private final LearnAccessService service;
    private final SecurityUtils securityUtils;

    @GetMapping
    public LearnAccessDto get(@PathVariable UUID studentId) {
        return service.get(studentId, actor());
    }

    @PostMapping("/issue")
    public LearnAccessDto issue(@PathVariable UUID studentId) {
        return service.issue(studentId, actor());
    }

    @PostMapping("/reset-password")
    public LearnAccessDto resetPassword(@PathVariable UUID studentId) {
        return service.resetPassword(studentId, actor());
    }

    private CurrentUser actor() {
        return securityUtils.currentUser();
    }
}
