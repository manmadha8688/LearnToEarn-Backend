package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.service.ResumeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    // Auth required — the current user's saved resume (empty object if none)
    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(resumeService.get(user.getId()));
    }

    // Auth required — save/update the current user's resume
    @PutMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> data,
                                                     @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(resumeService.save(user.getId(), data));
    }
}
