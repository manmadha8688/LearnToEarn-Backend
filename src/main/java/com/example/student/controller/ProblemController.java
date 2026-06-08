package com.example.student.controller;

import com.example.student.model.ProblemQuestion;
import com.example.student.repository.ProblemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemRepository repo;

    public ProblemController(ProblemRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<List<ProblemQuestion>> getAll(
            @RequestParam(required = false) String track) {
        if (track != null)
            return ResponseEntity.ok(repo.findByTracksContainingOrderByOrderIndexAsc(track));
        return ResponseEntity.ok(repo.findAllByOrderByOrderIndexAsc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemQuestion> getOne(@PathVariable String id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
