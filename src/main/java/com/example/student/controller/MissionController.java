package com.example.student.controller;

import com.example.student.model.Mission;
import com.example.student.model.MissionSubmission;
import com.example.student.model.User;
import com.example.student.repository.MissionRepository;
import com.example.student.service.CacheService;
import com.example.student.service.MissionSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    public ResponseEntity<Mission> getOne(@PathVariable String id,
                                          @AuthenticationPrincipal User user) {
        Mission m = cacheService.get("missions", "id:" + id,
                () -> missionRepository.findById(id).orElse(null));
        if (m == null) return ResponseEntity.notFound().build();
        if (!m.isPublished() && (user == null || !"ADMIN".equals(user.getRole()))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(m);
    }

    // Auth required — the current hunter's submitted links across all missions, for the
    // board cards. Returns only missions with at least one saved link: [{missionId, repoUrl?, deployUrl?}]
    @GetMapping("/submissions")
    public ResponseEntity<List<Map<String, String>>> mySubmissions(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.ok(List.of());
        List<Map<String, String>> out = submissionService.listForUser(user.getId()).stream()
                .filter(s -> notBlank(s.getRepoUrl()) || notBlank(s.getDeployUrl()))
                .map(s -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("missionId", s.getMissionId());
                    if (notBlank(s.getRepoUrl())) m.put("repoUrl", s.getRepoUrl());
                    if (notBlank(s.getDeployUrl())) m.put("deployUrl", s.getDeployUrl());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(out);
    }

    // Auth required — the current hunter's submitted repo + live demo links (null if none)
    @GetMapping("/{id}/submission")
    public ResponseEntity<MissionSubmission> getSubmission(@PathVariable String id,
                                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(submissionService.get(user.getId(), id));
    }

    // Auth required — save/update the hunter's repo + live demo links for this mission
    @PutMapping("/{id}/submission")
    public ResponseEntity<?> saveSubmission(@PathVariable String id,
                                            @Valid @RequestBody Map<String, Object> body,
                                            @AuthenticationPrincipal User user) {
        try {
            String target = asString(body.get("target"));
            MissionSubmission saved = submissionService.save(
                    user, id,
                    asString(body.get("repoUrl")),
                    asString(body.get("deployUrl")),
                    parseBoolean(body.get("skipLinkVerification")),
                    target);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String asString(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value == null) return false;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }
}
