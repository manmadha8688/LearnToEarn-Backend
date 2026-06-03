package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.service.RoadmapService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/roadmaps")
public class RoadmapController {

    private final RoadmapService roadmapService;

    public RoadmapController(RoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    @GetMapping
    public ResponseEntity<?> getAllRoadmaps(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(roadmapService.getAllRoadmaps(user.getId()));
    }

    @GetMapping("/enrolled")
    public ResponseEntity<?> getEnrolledRoadmaps(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(roadmapService.getEnrolledRoadmaps(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRoadmapDetail(@PathVariable String id,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(roadmapService.getRoadmapDetail(id, user.getId()));
    }

    @PostMapping("/{id}/enroll")
    public ResponseEntity<?> enroll(@PathVariable String id, @AuthenticationPrincipal User user) {
        roadmapService.enroll(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "Enrolled"));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<?> pause(@PathVariable String id, @AuthenticationPrincipal User user) {
        roadmapService.pauseHunt(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "Paused"));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resume(@PathVariable String id, @AuthenticationPrincipal User user) {
        roadmapService.resumeHunt(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "Resumed"));
    }
}
