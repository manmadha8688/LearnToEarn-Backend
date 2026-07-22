package com.example.student.controller;

import com.example.student.dto.ProblemDetailDTO;
import com.example.student.dto.ProblemSummaryDTO;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.service.ProblemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Code Gym problem reads. Authenticated (see SecurityConfig — not in permitAll).
 * Returns DTOs that never leak hidden test expected outputs.
 */
@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;
    private final UserRepository userRepository;

    public ProblemController(ProblemService problemService, UserRepository userRepository) {
        this.problemService = problemService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<ProblemSummaryDTO>> getAll(
            @RequestParam(required = false) String track,
            @AuthenticationPrincipal User principal) {
        List<ProblemSummaryDTO> rows = problemService.list(track);
        Set<String> solved = freshSolvedIds(principal);
        if (solved.isEmpty()) return ResponseEntity.ok(rows);
        return ResponseEntity.ok(rows.stream()
                .map(r -> r.withSolved(solved.contains(r.id())))
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable String id, @AuthenticationPrincipal User principal) {
        ProblemDetailDTO p = problemService.getDetail(id);
        if (p == null) return ResponseEntity.status(404).body(Map.of("error", "Problem not found."));
        // isSolved from Mongo ledger — not the short-lived Security principal cache.
        boolean solved = freshSolvedIds(principal).contains(id);
        return ResponseEntity.ok(p.withSolved(solved));
    }

    private Set<String> freshSolvedIds(User principal) {
        if (principal == null || principal.getId() == null) return Collections.emptySet();
        return userRepository.findById(principal.getId())
                .map(User::getSolvedProblemIds)
                .filter(ids -> ids != null && !ids.isEmpty())
                .orElse(Collections.emptySet());
    }
}
