package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.service.ConceptService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConceptController {

    private final ConceptService conceptService;

    public ConceptController(ConceptService conceptService) {
        this.conceptService = conceptService;
    }

    @GetMapping("/api/subjects/{subjectId}/concepts")
    public ResponseEntity<?> getConceptsForSubject(@PathVariable String subjectId,
                                                    @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(conceptService.getConceptsForSubject(subjectId, user.getId()));
    }

    @GetMapping("/api/concepts/{id}")
    public ResponseEntity<?> getConceptDetail(@PathVariable String id,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(conceptService.getConceptDetail(id, user.getId()));
    }

    @GetMapping("/api/concepts/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        return ResponseEntity.ok(conceptService.search(q));
    }
}
