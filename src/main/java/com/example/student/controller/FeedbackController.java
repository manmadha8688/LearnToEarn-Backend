package com.example.student.controller;

import com.example.student.model.Feedback;
import com.example.student.model.User;
import com.example.student.security.RateLimiterService;
import com.example.student.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final RateLimiterService rateLimiter;

    public FeedbackController(FeedbackService feedbackService, RateLimiterService rateLimiter) {
        this.feedbackService = feedbackService;
        this.rateLimiter = rateLimiter;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    @PostMapping
    public ResponseEntity<?> submit(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        long retryAfter = rateLimiter.hit("feedback", getClientIp(request), 10, 3600);
        if (retryAfter > 0) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many submissions. Please try again later.",
                "retryAfter", retryAfter));
        }
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
