package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.service.SubjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public ResponseEntity<?> getAllSubjects(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subjectService.getAllSubjects(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSubjectDetail(@PathVariable String id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subjectService.getSubjectDetail(id, user.getId()));
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subjectService.search(q, user.getId()));
    }
}
