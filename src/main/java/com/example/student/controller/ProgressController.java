package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.service.ProgressService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @PostMapping("/concept/{conceptId}/complete")
    public ResponseEntity<?> complete(@PathVariable String conceptId,
                                       @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(progressService.completeConcept(conceptId, user.getId()));
    }

    @DeleteMapping("/concept/{conceptId}/uncomplete")
    public ResponseEntity<?> uncomplete(@PathVariable String conceptId,
                                         @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(progressService.uncompleteConcept(conceptId, user.getId()));
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(progressService.getProgressSummary(user.getId()));
    }

    @GetMapping("/hunter-stats")
    public ResponseEntity<?> hunterStats(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(progressService.getHunterStats(user.getId()));
    }
}
