package com.example.student.controller;

import com.example.student.model.AptitudeGroup;
import com.example.student.model.AptitudeQuestion;
import com.example.student.service.AptitudeAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin CRUD for the aptitude feature (groups, topics across all three topic
 * collections, and practice questions). ADMIN-only — mirrors the security and
 * response conventions of {@link AdminController}. Every mutation evicts the
 * shared "aptitude" cache inside the service so student reads update at once.
 */
@RestController
@RequestMapping("/api/admin/aptitude")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAptitudeController {

    private final AptitudeAdminService service;

    public AdminAptitudeController(AptitudeAdminService service) {
        this.service = service;
    }

    // ── GROUPS ───────────────────────────────────────────────────────────────

    @GetMapping("/groups")
    public ResponseEntity<?> listGroups() {
        return ResponseEntity.ok(service.listGroups());
    }

    @PostMapping("/groups")
    public ResponseEntity<?> createGroup(@RequestBody AptitudeGroup group) {
        return ResponseEntity.ok(service.createGroup(group));
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<?> updateGroup(@PathVariable String id, @RequestBody AptitudeGroup group) {
        return ResponseEntity.ok(service.updateGroup(id, group));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<?> deleteGroup(@PathVariable String id) {
        service.deleteGroup(id);
        return ResponseEntity.ok(Map.of("message", "Group deleted"));
    }

    // ── TOPICS ─────────────────────────────────────────────────────────────

    /** List a group's topics (metadata + status, incl. inactive). */
    @GetMapping("/topics/{group}")
    public ResponseEntity<List<?>> listTopics(@PathVariable String group) {
        return ResponseEntity.ok(service.listTopics(group));
    }

    /** Full topic (with lesson) by slug — for the edit form. */
    @GetMapping("/topic/{topicId}")
    public ResponseEntity<?> getTopic(@PathVariable String topicId) {
        Object topic = service.getTopic(topicId);
        return topic != null ? ResponseEntity.ok(topic) : ResponseEntity.notFound().build();
    }

    @PostMapping("/topics")
    public ResponseEntity<?> createTopic(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.createTopic(body));
    }

    @PutMapping("/topics/{id}")
    public ResponseEntity<?> updateTopic(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.updateTopic(id, body));
    }

    @DeleteMapping("/topics/{id}")
    public ResponseEntity<?> deleteTopic(@PathVariable String id) {
        service.deleteTopic(id);
        return ResponseEntity.ok(Map.of("message", "Topic deleted"));
    }

    // ── QUESTIONS ────────────────────────────────────────────────────────────

    @GetMapping("/questions/{topic}")
    public ResponseEntity<?> listQuestions(@PathVariable String topic) {
        return ResponseEntity.ok(service.listQuestions(topic));
    }

    @PostMapping("/questions")
    public ResponseEntity<?> createQuestion(@RequestBody AptitudeQuestion question) {
        return ResponseEntity.ok(service.createQuestion(question));
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable String id, @RequestBody AptitudeQuestion question) {
        return ResponseEntity.ok(service.updateQuestion(id, question));
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable String id) {
        service.deleteQuestion(id);
        return ResponseEntity.ok(Map.of("message", "Question deleted"));
    }
}
