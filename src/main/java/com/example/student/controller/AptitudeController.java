package com.example.student.controller;

import com.example.student.dto.AptitudeCategoryDTO;
import com.example.student.dto.AptitudeGroupDTO;
import com.example.student.dto.AptitudeMockPaperDTO;
import com.example.student.dto.AptitudeMockResultDTO;
import com.example.student.dto.AptitudeMockSubmitDTO;
import com.example.student.model.AptitudeQuestion;
import com.example.student.model.User;
import com.example.student.service.AptitudeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public read-only aptitude endpoints. Navigation nests as:
 * category → group → topic. Questions/quizzes come later — there is no
 * answer-checking endpoint yet.
 */
@RestController
@RequestMapping("/api/aptitude")
public class AptitudeController {

    private final AptitudeService service;

    public AptitudeController(AptitudeService service) {
        this.service = service;
    }

    /** All 4 categories with their group + topic counts. */
    @GetMapping("/categories")
    public ResponseEntity<List<AptitudeCategoryDTO>> getCategories() {
        return ResponseEntity.ok(service.getCategories());
    }

    /** All groups in a category. */
    @GetMapping("/groups/{category}")
    public ResponseEntity<List<AptitudeGroupDTO>> getGroups(@PathVariable String category) {
        return ResponseEntity.ok(service.getGroups(category));
    }

    /**
     * All topics in a group. The element type depends on the group's category
     * (quantitative → AptitudeTopic, logical → LogicalTopic, verbal → VerbalTopic);
     * every shape carries the same navigation metadata the frontend reads.
     */
    @GetMapping("/topics/{group}")
    public ResponseEntity<List<?>> getTopics(@PathVariable String group) {
        return ResponseEntity.ok(service.getTopics(group));
    }

    /** A single topic by slug (shape varies by category — see {@link #getTopics}). */
    @GetMapping("/topic/{topicId}")
    public ResponseEntity<?> getTopic(@PathVariable String topicId) {
        Object topic = service.getTopic(topicId);
        return topic != null ? ResponseEntity.ok(topic) : ResponseEntity.notFound().build();
    }

    /** Practice questions for a topic. */
    @GetMapping("/questions/{topicId}")
    public ResponseEntity<List<AptitudeQuestion>> getQuestions(@PathVariable String topicId) {
        return ResponseEntity.ok(service.getQuestions(topicId));
    }

    /** Full 50-question core mock paper (20 quant + 15 logical + 15 verbal). Requires login. */
    @GetMapping("/mock/paper")
    public ResponseEntity<AptitudeMockPaperDTO> getMockPaper(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.buildMockPaper(user.getId()));
    }

    /** Mock retry cooldown + best score for the aptitude hub. */
    @GetMapping("/mock/status")
    public ResponseEntity<?> getMockStatus(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.getMockStatus(user.getId()));
    }

    /** Recent mock attempts (score summary only). */
    @GetMapping("/mock/history")
    public ResponseEntity<?> getMockHistory(@AuthenticationPrincipal User user,
                                            @RequestParam(defaultValue = "10") int limit) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.getMockHistory(user.getId(), limit));
    }

    /** Grade a mock submission — summary stored; full review returned once on submit. */
    @PostMapping("/mock/submit")
    public ResponseEntity<AptitudeMockResultDTO> submitMock(@RequestBody AptitudeMockSubmitDTO body,
                                                            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.submitMock(body, user.getId()));
    }
}
