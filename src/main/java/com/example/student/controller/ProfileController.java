package com.example.student.controller;

import com.example.student.dto.ProfileUpdateRequest;
import com.example.student.model.User;
import com.example.student.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    // Public — anyone with the link can view the safe, shareable profile.
    @GetMapping("/public/profile/{username}")
    public ResponseEntity<Map<String, Object>> publicProfile(@PathVariable String username) {
        return ResponseEntity.ok(profileService.getPublicProfile(username.trim().toLowerCase()));
    }

    // Authenticated — update own profile (settings page).
    @PutMapping("/profile/me")
    public ResponseEntity<?> updateOwn(@AuthenticationPrincipal User user,
                                       @Valid @RequestBody ProfileUpdateRequest req) {
        try {
            User updated = profileService.updateOwnProfile(user, req);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("fullName", updated.getFullName());
            res.put("username", updated.getPublicUsername());
            res.put("bio", updated.getBio() != null ? updated.getBio() : "");
            res.put("avatarColor", updated.getAvatarColor());
            res.put("githubUrl", updated.getGithubUrl() != null ? updated.getGithubUrl() : "");
            res.put("linkedinUrl", updated.getLinkedinUrl() != null ? updated.getLinkedinUrl() : "");
            res.put("portfolioUrl", updated.getPortfolioUrl() != null ? updated.getPortfolioUrl() : "");
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
