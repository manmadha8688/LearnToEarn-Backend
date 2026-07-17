package com.example.student.controller;

import com.example.student.dto.ProfileUpdateRequest;
import com.example.student.model.User;
import com.example.student.service.GitHubLinkService;
import com.example.student.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;
    private final GitHubLinkService gitHubLinkService;

    public ProfileController(ProfileService profileService, GitHubLinkService gitHubLinkService) {
        this.profileService = profileService;
        this.gitHubLinkService = gitHubLinkService;
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
            res.put("githubLogin", updated.getGithubLogin() != null ? updated.getGithubLogin() : "");
            res.put("githubConnected", updated.getGithubId() != null && !updated.getGithubId().isBlank());
            res.put("linkedinUrl", updated.getLinkedinUrl() != null ? updated.getLinkedinUrl() : "");
            res.put("portfolioUrl", updated.getPortfolioUrl() != null ? updated.getPortfolioUrl() : "");
            res.put("location", updated.getLocation() != null ? updated.getLocation() : "");
            res.put("education", updated.getEducation());
            res.put("publicProfile", updated.getPublicProfile() == null ? Boolean.TRUE : updated.getPublicProfile());
            res.put("featuredResumeId", updated.getFeaturedResumeId() != null ? updated.getFeaturedResumeId() : "");
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Authenticated — live username availability check for the profile editor.
    @GetMapping("/profile/check-username")
    public ResponseEntity<Map<String, Object>> checkUsername(@AuthenticationPrincipal User user,
                                                             @RequestParam(name = "username", required = false) String username) {
        return ResponseEntity.ok(profileService.checkUsernameAvailability(user, username));
    }

    /**
     * Return the GitHub authorize URL (preferred — uses the same axios + cookie flow as the rest of the app).
     * Avoids a cross-origin full-page hop to the API host, which often fails on localhost (5173 → 8080).
     */
    @GetMapping("/profile/github/connect-url")
    public ResponseEntity<?> connectGitHubUrl(@AuthenticationPrincipal User user,
                                              HttpServletRequest request) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        try {
            return ResponseEntity.ok(Map.of("url", gitHubLinkService.buildAuthorizeUrl(user, request)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", "GitHub connect is not available right now."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Legacy redirect entry — kept for direct links; prefer {@code /profile/github/connect-url} from the SPA. */
    @GetMapping("/profile/github/connect")
    public void connectGitHub(@AuthenticationPrincipal User user,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        if (user == null) {
            response.sendError(401, "Unauthorized");
            return;
        }
        try {
            response.sendRedirect(gitHubLinkService.buildAuthorizeUrl(user, request));
        } catch (IllegalStateException e) {
            response.sendRedirect(gitHubLinkService.frontendErrorRedirect("unavailable"));
        } catch (IllegalArgumentException e) {
            response.sendRedirect(gitHubLinkService.frontendErrorRedirect("guest"));
        }
    }

    /** Remove the linked GitHub account from the profile. */
    @DeleteMapping("/profile/github")
    public ResponseEntity<?> disconnectGitHub(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        try {
            User updated = gitHubLinkService.disconnect(user);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("githubUrl", "");
            res.put("githubLogin", "");
            res.put("githubConnected", false);
            res.put("fullName", updated.getFullName());
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
