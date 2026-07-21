package com.example.student.controller;

import com.example.student.dto.CodeSubmissionDTO;
import com.example.student.dto.CodingProblemDTO;
import com.example.student.dto.CodingProblemSummaryDTO;
import com.example.student.model.CodingProblem;
import com.example.student.model.User;
import com.example.student.security.RateLimiterService;
import com.example.student.service.CodeExecutionService;
import com.example.student.service.CodingProblemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * LeetCode-style coding platform. Problem browsing (GET) is public; running,
 * submitting, and submission history are authenticated and rate limited via the
 * shared {@link RateLimiterService}. The compile/run engine is reused unchanged
 * from {@link CodeExecutionService} through {@link CodingProblemService}.
 */
@RestController
public class CodingPlatformController {

    private static final int MAX_CODE_CHARS = 50_000;

    private final CodingProblemService service;
    private final CodeExecutionService codeService;
    private final RateLimiterService rateLimiter;

    public CodingPlatformController(CodingProblemService service,
                                    CodeExecutionService codeService,
                                    RateLimiterService rateLimiter) {
        this.service = service;
        this.codeService = codeService;
        this.rateLimiter = rateLimiter;
    }

    // ── Problem browsing (public) ──────────────────────────────────────────

    @GetMapping("/api/coding-problems")
    public ResponseEntity<List<CodingProblemSummaryDTO>> list() {
        return ResponseEntity.ok(service.listProblems());
    }

    @GetMapping("/api/coding-problems/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        CodingProblemDTO dto = service.getProblemDTO(id);
        if (dto == null) return ResponseEntity.status(404).body(Map.of("error", "Problem not found."));
        return ResponseEntity.ok(dto);
    }

    // ── Run / Submit (authenticated + rate limited) ────────────────────────

    @PostMapping("/api/code/run")
    public ResponseEntity<?> run(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        Object gate = guard(user, "coding-run", body);
        if (gate instanceof ResponseEntity<?> r) return r;
        CodingProblem problem = (CodingProblem) gate;

        Map<String, Object> result = service.run(problem, body.get("code"), body.getOrDefault("language", ""));
        if ("BLOCKED".equals(result.get("status"))) return ResponseEntity.badRequest().body(result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/code/submit")
    public ResponseEntity<?> submit(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        Object gate = guard(user, "coding-submit", body);
        if (gate instanceof ResponseEntity<?> r) return r;
        CodingProblem problem = (CodingProblem) gate;

        Map<String, Object> result = service.submit(user.getId(), problem, body.get("code"),
                body.getOrDefault("language", ""));
        if ("BLOCKED".equals(result.get("status"))) return ResponseEntity.badRequest().body(result);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/code/submissions/{problemId}")
    public ResponseEntity<?> submissions(@PathVariable String problemId, @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Please sign in."));
        return ResponseEntity.ok(service.submissions(user.getId(), problemId));
    }

    /**
     * Shared auth + rate-limit + input validation for run/submit. Returns a
     * {@link ResponseEntity} to short-circuit on any failure, or the resolved
     * {@link CodingProblem} when everything checks out.
     */
    private Object guard(User user, String bucket, Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Please sign in to run code."));

        long retryAfter = rateLimiter.hit(bucket, user.getId(), 10, 60);
        if (retryAfter > 0) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many runs. Please wait a moment and try again.",
                    "retryAfter", retryAfter));
        }

        String code = body.get("code");
        String language = body.getOrDefault("language", "");
        String problemId = body.get("problemId");

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is required."));
        }
        if (code.length() > MAX_CODE_CHARS) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is too long (max 50,000 characters)."));
        }
        if (!codeService.isSupported(language)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported language. Use python, java, c or cpp."));
        }
        if (problemId == null || problemId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Problem is required."));
        }

        CodingProblem problem = service.findEntity(problemId);
        if (problem == null) return ResponseEntity.status(404).body(Map.of("error", "Problem not found."));

        List<String> allowed = problem.getSupportedLanguages();
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(language)) {
            return ResponseEntity.badRequest().body(Map.of("error", "This problem does not support " + language + "."));
        }
        return problem;
    }
}
