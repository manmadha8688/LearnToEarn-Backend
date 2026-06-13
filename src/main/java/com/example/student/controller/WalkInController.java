package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.model.WalkIn;
import com.example.student.service.WalkInService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WalkInController {

    private final WalkInService walkInService;

    // ── Public: get active walk-ins ──────────────────────────
    @GetMapping("/api/walkins")
    public ResponseEntity<List<WalkIn>> getWalkIns(
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(walkInService.getActiveWalkIns(city));
    }

    // ── Public: get one ──────────────────────────────────────
    @GetMapping("/api/walkins/{id}")
    public ResponseEntity<?> getWalkIn(@PathVariable String id) {
        return walkInService.getById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Authenticated: post a walk-in ────────────────────────
    @PostMapping("/api/walkins")
    public ResponseEntity<?> createWalkIn(
            @RequestBody WalkIn walkIn,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body("Login required");
        try {
            WalkIn saved = walkInService.createWalkIn(walkIn, user.getEmail());
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Admin: update a walk-in ──────────────────────────────
    @PutMapping("/api/admin/walkins/{id}")
    public ResponseEntity<?> updateWalkIn(
            @PathVariable String id,
            @RequestBody WalkIn walkIn,
            @AuthenticationPrincipal User user) {
        if (user == null || !"ADMIN".equals(user.getRole()))
            return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(walkInService.updateWalkIn(id, walkIn));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Delete: own post or admin ────────────────────────────
    @DeleteMapping("/api/walkins/{id}")
    public ResponseEntity<?> deleteWalkIn(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        try {
            boolean isAdmin = "ADMIN".equals(user.getRole());
            walkInService.deleteWalkIn(id, user.getEmail(), isAdmin);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Admin: get all (including expired) ───────────────────
    @GetMapping("/api/admin/walkins")
    public ResponseEntity<List<WalkIn>> getAllWalkIns(@AuthenticationPrincipal User user) {
        if (user == null || !"ADMIN".equals(user.getRole()))
            return ResponseEntity.status(403).build();
        return ResponseEntity.ok(walkInService.getAll());
    }
}
