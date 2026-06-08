package com.example.student.controller;

import com.example.student.model.ProblemQuestion;
import com.example.student.repository.ProblemRepository;
import com.example.student.service.CacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemRepository repo;
    private final CacheService cacheService;

    public ProblemController(ProblemRepository repo, CacheService cacheService) {
        this.repo = repo;
        this.cacheService = cacheService;
    }

    @GetMapping
    public ResponseEntity<List<ProblemQuestion>> getAll(
            @RequestParam(required = false) String track) {
        String cacheKey = track != null ? "track:" + track : "all";
        List<ProblemQuestion> problems = cacheService.get("problems", cacheKey,
                () -> track != null
                        ? repo.findByTracksContainingOrderByOrderIndexAsc(track)
                        : repo.findAllByOrderByOrderIndexAsc());
        return ResponseEntity.ok(problems);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemQuestion> getOne(@PathVariable String id) {
        ProblemQuestion p = cacheService.get("problems", "id:" + id,
                () -> repo.findById(id).orElse(null));
        return p != null ? ResponseEntity.ok(p) : ResponseEntity.notFound().build();
    }
}
