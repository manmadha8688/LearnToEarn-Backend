package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.model.WalkIn;
import com.example.student.service.WalkInService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WalkInController {

    private final WalkInService walkInService;

    // ── Public: get active walk-ins ──────────────────────────
    // Principal is optional (route is permitAll); when a valid session cookie is
    // present we flag the caller's own posts so the UI can show delete.
    @GetMapping("/api/walkins")
    public ResponseEntity<List<WalkIn>> getWalkIns(
            @RequestParam(required = false) String city,
            @AuthenticationPrincipal User user) {
        List<WalkIn> list = walkInService.getActiveWalkIns(city);
        list.forEach(w -> w.setMine(isOwner(w, user)));
        return ResponseEntity.ok(list);
    }

    // ── Public: get one ──────────────────────────────────────
    @GetMapping("/api/walkins/{id}")
    public ResponseEntity<?> getWalkIn(@PathVariable String id,
            @AuthenticationPrincipal User user) {
        return walkInService.getById(id)
                .map(w -> { w.setMine(isOwner(w, user)); return w; })
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Authenticated: post a walk-in ────────────────────────
    @PostMapping("/api/walkins")
    public ResponseEntity<?> createWalkIn(
            @jakarta.validation.Valid @RequestBody WalkIn walkIn,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Login required"));
        try {
            WalkIn saved = walkInService.createWalkIn(walkIn, user.getEmail());
            saved.setMine(true); // poster owns it — show delete immediately
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private boolean isOwner(WalkIn w, User user) {
        return user != null && user.getId() != null && user.getId().equals(w.getPostedById());
    }

    // ── Admin: update a walk-in ──────────────────────────────
    @PutMapping("/api/admin/walkins/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateWalkIn(
            @PathVariable String id,
            @jakarta.validation.Valid @RequestBody WalkIn walkIn,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
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

    // ── Admin: get all (including expired), paginated ────────
    @GetMapping("/api/admin/walkins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<WalkIn>> getAllWalkIns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(walkInService.getAllPaged(page, size));
    }
}
