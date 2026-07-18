package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    // ── Owner endpoints (auth required) ──

    @GetMapping("/resumes")
    public ResponseEntity<List<Map<String, Object>>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(resumeService.list(user.getId()));
    }

    @PostMapping("/resumes")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal User user,
                                                       @Valid @RequestBody Map<String, Object> body) {
        String title = asString(body.get("title"));
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        boolean skip = parseBoolean(body.get("skipLinkVerification"));
        return ResponseEntity.ok(resumeService.create(user.getId(), title, data, skip));
    }

    @PutMapping("/resumes/{id}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> update(@AuthenticationPrincipal User user,
                                                       @PathVariable String id,
                                                       @Valid @RequestBody Map<String, Object> body) {
        String title = asString(body.get("title"));
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        boolean skip = parseBoolean(body.get("skipLinkVerification"));
        return ResponseEntity.ok(resumeService.update(user.getId(), id, title, data, skip));
    }

    @DeleteMapping("/resumes/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable String id) {
        resumeService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resumes/{id}/share")
    public ResponseEntity<Map<String, Object>> setShared(@AuthenticationPrincipal User user,
                                                         @PathVariable String id,
                                                         @Valid @RequestBody Map<String, Object> body) {
        boolean makePublic = Boolean.TRUE.equals(body.get("public"));
        return ResponseEntity.ok(resumeService.setShared(user.getId(), id, makePublic));
    }

    // ── Public read (no auth) — anyone with the link ──

    @GetMapping("/public/resume/{slug}")
    public ResponseEntity<Map<String, Object>> getPublic(@PathVariable String slug) {
        return ResponseEntity.ok(resumeService.getPublic(slug.trim()));
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value == null) return false;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }
}
