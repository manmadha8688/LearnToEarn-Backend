package com.example.student.controller;

import com.example.student.model.ProblemQuestion;
import com.example.student.model.User;
import com.example.student.security.CodeRateLimiterService;
import com.example.student.service.CodeExecutionService;
import com.example.student.service.ProblemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 code runner. Authenticated (see SecurityConfig `anyRequest().authenticated()`)
 * and rate limited to 10 runs + 10 submits/minute per user via the Redis-backed
 * {@link CodeRateLimiterService} (holds across all Render instances).
 */
@RestController
@RequestMapping("/api/code")
public class CodeExecutionController {

    private static final int MAX_CODE_CHARS = 50_000;

    private final CodeExecutionService codeService;
    private final CodeRateLimiterService rateLimiter;
    private final ProblemService problemService;
    private final com.example.student.service.CodeSolveXpService solveXpService;

    public CodeExecutionController(CodeExecutionService codeService, CodeRateLimiterService rateLimiter,
                                   ProblemService problemService,
                                   com.example.student.service.CodeSolveXpService solveXpService) {
        this.codeService = codeService;
        this.rateLimiter = rateLimiter;
        this.problemService = problemService;
        this.solveXpService = solveXpService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, String> body,
                                     @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Please sign in to run code."));
        }

        long retryAfter = rateLimiter.hit(user.getId(), "run", 10);
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

    /**
     * Judge student code against a problem's test cases (Code Gym).
     * mode "run" uses only the visible sample cases; "submit" uses all cases.
     */
    @PostMapping("/judge")
    public ResponseEntity<?> judge(@RequestBody Map<String, String> body,
                                   @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Please sign in to run code."));
        }

        long retryAfter = rateLimiter.hit(user.getId(), "submit", 10);
        if (retryAfter > 0) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many runs. Please wait a moment and try again.",
                "retryAfter", retryAfter));
        }

        String problemId = body.get("problemId");
        String code = body.get("code");
        String language = body.getOrDefault("language", "");
        boolean onlySamples = !"submit".equalsIgnoreCase(body.getOrDefault("mode", "run"));

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

        ProblemQuestion problem = problemService.getEntity(problemId);
        if (problem == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Problem not found."));
        }
        List<ProblemQuestion.TestCase> tcs = problem.effectiveTestCases();
        if (tcs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "This problem has no test cases yet."));
        }

        List<CodeExecutionService.Case> cases = new ArrayList<>(tcs.size());
        for (ProblemQuestion.TestCase t : tcs) {
            cases.add(new CodeExecutionService.Case(t.getInput(), t.getExpectedOutput(), t.isSample()));
        }

        Map<String, Object> result = codeService.judge(code, language, cases, onlySamples);
        if ("BLOCKED".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }

        // One-time XP + "Solved" state: only a real Submit that passes every case counts.
        if (!onlySamples && "ACCEPTED".equals(result.get("status"))) {
            var solve = solveXpService.award(user.getId(), problem);
            Map<String, Object> resp = new java.util.LinkedHashMap<>(result);
            resp.put("solved", solve.solved());
            resp.put("firstSolve", solve.firstSolve());
            resp.put("xpEarned", solve.xpEarned());
            resp.put("newXp", solve.newXp());
            resp.put("newRank", solve.rank());
            resp.put("rankUp", solve.rankUp());
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.ok(result);
    }
}
