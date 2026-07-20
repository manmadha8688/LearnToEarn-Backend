package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.security.RateLimiterService;
import com.example.student.service.CodeExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Phase 1 code runner. Authenticated (see SecurityConfig `anyRequest().authenticated()`)
 * and rate limited to 10 runs/minute per user via the shared {@link RateLimiterService}.
 */
@RestController
@RequestMapping("/api/code")
public class CodeExecutionController {

    private static final int MAX_CODE_CHARS = 50_000;

    private final CodeExecutionService codeService;
    private final RateLimiterService rateLimiter;

    public CodeExecutionController(CodeExecutionService codeService, RateLimiterService rateLimiter) {
        this.codeService = codeService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, String> body,
                                     @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Please sign in to run code."));
        }

        long retryAfter = rateLimiter.hit("code-execute", user.getId(), 10, 60);
        if (retryAfter > 0) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many runs. Please wait a moment and try again.",
                "retryAfter", retryAfter));
        }

        String code = body.get("code");
        String language = body.getOrDefault("language", "");

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is required."));
        }
        if (code.length() > MAX_CODE_CHARS) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is too long (max 50,000 characters)."));
        }
        if (!codeService.isSupported(language)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported language. Use python, java, c or cpp."));
        }

        Map<String, Object> result = codeService.execute(code, language);
        // Blocked constructs are a bad request; run outcomes (SUCCESS/ERROR/TIMEOUT) are 200.
        if ("BLOCKED".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
