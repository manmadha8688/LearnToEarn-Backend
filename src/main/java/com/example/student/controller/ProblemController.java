package com.example.student.controller;

import com.example.student.dto.ProblemDetailDTO;
import com.example.student.dto.ProblemSummaryDTO;
import com.example.student.model.User;
import com.example.student.service.ProblemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Code Gym problem reads. Authenticated (see SecurityConfig — not in permitAll).
 * Returns DTOs that never leak hidden test expected outputs.
 */
@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public ResponseEntity<List<ProblemSummaryDTO>> getAll(
            @RequestParam(required = false) String track) {
        return ResponseEntity.ok(problemService.list(track));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable String id, @AuthenticationPrincipal User user) {
        ProblemDetailDTO p = problemService.getDetail(id);
        if (p == null) return ResponseEntity.status(404).body(Map.of("error", "Problem not found."));
        // isSolved is per-user, derived from the authenticated user's solved ledger — the
        // cached DTO stays user-agnostic, so this flag is applied on the way out.
        boolean solved = user != null && user.getSolvedProblemIds() != null
                && user.getSolvedProblemIds().contains(id);
        return ResponseEntity.ok(p.withSolved(solved));
    }
}
