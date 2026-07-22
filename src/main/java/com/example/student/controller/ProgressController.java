package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.service.ProgressService;
import com.example.student.service.QuestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;
    private final QuestService questService;

    public ProgressController(ProgressService progressService, QuestService questService) {
        this.progressService = progressService;
        this.questService = questService;
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
        return ResponseEntity.ok(progressService.getProgressSummary(user));
    }

    @GetMapping("/hunter-stats")
    public ResponseEntity<?> hunterStats(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(progressService.getHunterStats(user.getId()));
    }

    /** Today's daily-quest state (concept quest + 45-min study quest). */
    @GetMapping("/quests")
    public ResponseEntity<?> quests(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(questService.getQuests(user.getId()));
    }

    /**
     * Heartbeat sent by the arena while the student is active. Credits real elapsed time
     * toward the study quest and awards its XP once the 45-minute target is reached.
     */
    @PostMapping("/study-ping")
    public ResponseEntity<?> studyPing(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(questService.recordStudyPing(user.getId()));
    }
}
