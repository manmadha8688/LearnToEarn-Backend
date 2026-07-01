package com.example.student.controller;

import com.example.student.model.Feedback;
import com.example.student.model.User;
import com.example.student.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<?> submit(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        String userId = user != null ? user.getId() : null;
        Feedback saved = feedbackService.submit(body, userId);
        return ResponseEntity.ok(Map.of("message", "Feedback received. Thank you!", "id", saved.getId()));
    }

    // Admin only — view paginated feedback
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return ResponseEntity.ok(feedbackService.getPaged(page, size));
    }
}
