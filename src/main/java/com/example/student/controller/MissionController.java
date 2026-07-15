package com.example.student.controller;

import com.example.student.model.Mission;
import com.example.student.model.MissionSubmission;
import com.example.student.model.User;
import com.example.student.repository.MissionRepository;
import com.example.student.service.CacheService;
import com.example.student.service.MissionSubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionRepository missionRepository;
    private final CacheService cacheService;
    private final MissionSubmissionService submissionService;

    public MissionController(MissionRepository missionRepository, CacheService cacheService,
                             MissionSubmissionService submissionService) {
        this.missionRepository = missionRepository;
        this.cacheService = cacheService;
        this.submissionService = submissionService;
    }

    // Public — list page
    @GetMapping
    public ResponseEntity<List<Mission>> getAll() {
        List<Mission> missions = cacheService.get("missions", "all",
                () -> missionRepository.findByPublishedTrueOrderByOrderIndexAsc());
        return ResponseEntity.ok(missions);
    }

    // Auth required — detail page
    @GetMapping("/{id}")
    public ResponseEntity<Mission> getOne(@PathVariable String id) {
        Mission m = cacheService.get("missions", "id:" + id,
                () -> missionRepository.findById(id).orElse(null));
        return m != null ? ResponseEntity.ok(m) : ResponseEntity.notFound().build();
    }

    // Auth required — the current hunter's submitted repo + live demo links (null if none)
    @GetMapping("/{id}/submission")
    public ResponseEntity<MissionSubmission> getSubmission(@PathVariable String id,
                                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(submissionService.get(user.getId(), id));
    }

    // Auth required — save/update the hunter's repo + live demo links for this mission
    @PutMapping("/{id}/submission")
    public ResponseEntity<MissionSubmission> saveSubmission(@PathVariable String id,
                                                            @RequestBody Map<String, String> body,
                                                            @AuthenticationPrincipal User user) {
        MissionSubmission saved = submissionService.save(
                user.getId(), id, body.get("repoUrl"), body.get("deployUrl"));
        return ResponseEntity.ok(saved);
    }
}
