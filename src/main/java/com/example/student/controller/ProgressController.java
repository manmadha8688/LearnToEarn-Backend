package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.security.RateLimiterService;
import com.example.student.service.ProgressService;
import com.example.student.service.QuestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;
    private final QuestService questService;
    private final RateLimiterService rateLimiter;

    public ProgressController(ProgressService progressService, QuestService questService,
                              RateLimiterService rateLimiter) {
        this.progressService = progressService;
        this.questService = questService;
        this.rateLimiter = rateLimiter;
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
        // The arena pings ~once every 5 min (12/hour); this per-user ceiling (30/min) only
        // trips on a buggy/abusive client spamming the write path. XP is unaffected either way
        // (QuestService credits REAL elapsed time), and the client swallows non-2xx pings, so a
        // throttled tick is invisible to the user — it just protects the DB write path.
        long retryAfter = rateLimiter.hit("study-ping", user.getId(), 30, 60);
        if (retryAfter > 0) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many study pings. Please slow down.",
                "retryAfter", retryAfter));
        }
        return ResponseEntity.ok(questService.recordStudyPing(user.getId()));
    }
}
