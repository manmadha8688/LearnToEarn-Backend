package com.example.student.controller;

import com.example.student.dto.AptitudeCategoryDTO;
import com.example.student.dto.AptitudeGroupDTO;
import com.example.student.model.AptitudeQuestion;
import com.example.student.service.AptitudeService;
import org.springframework.http.ResponseEntity;
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
}
