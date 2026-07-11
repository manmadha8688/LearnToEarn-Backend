package com.example.student.controller;

import com.example.student.dto.AptitudeCategoryDTO;
import com.example.student.dto.AptitudeGroupDTO;
import com.example.student.model.AptitudeQuestion;
import com.example.student.model.AptitudeTopic;
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

    /** All topics in a group. */
    @GetMapping("/topics/{group}")
    public ResponseEntity<List<AptitudeTopic>> getTopics(@PathVariable String group) {
        return ResponseEntity.ok(service.getTopics(group));
    }

    /** A single topic by slug. */
    @GetMapping("/topic/{topicId}")
    public ResponseEntity<AptitudeTopic> getTopic(@PathVariable String topicId) {
        AptitudeTopic topic = service.getTopic(topicId);
        return topic != null ? ResponseEntity.ok(topic) : ResponseEntity.notFound().build();
    }

    /** Practice questions for a topic. */
    @GetMapping("/questions/{topicId}")
    public ResponseEntity<List<AptitudeQuestion>> getQuestions(@PathVariable String topicId) {
        return ResponseEntity.ok(service.getQuestions(topicId));
    }
}
