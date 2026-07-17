package com.example.student.controller;

import com.example.student.service.GitHubLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * GitHub OAuth callback — browser redirect from GitHub after the user approves.
 * Public endpoint; user identity is carried in the signed {@code state} param.
 */
@RestController
@RequestMapping("/api/auth")
public class GitHubAuthController {

    private final GitHubLinkService gitHubLinkService;

    public GitHubAuthController(GitHubLinkService gitHubLinkService) {
        this.gitHubLinkService = gitHubLinkService;
    }

    @GetMapping("/github/callback")
    public void callback(@RequestParam(name = "code", required = false) String code,
                         @RequestParam(name = "state", required = false) String state,
                         @RequestParam(name = "error", required = false) String error,
                         @RequestParam(name = "error_description", required = false) String errorDescription,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        if (error != null && !error.isBlank()) {
            String reason = "denied";
            if ("access_denied".equals(error)) reason = "denied";
            response.sendRedirect(gitHubLinkService.frontendErrorRedirect(reason, state));
            return;
        }
        try {
            gitHubLinkService.handleCallback(code, state, request);
            response.sendRedirect(gitHubLinkService.frontendRedirect("github=connected", state));
        } catch (IllegalArgumentException e) {
            String reason = mapReason(e.getMessage());
            response.sendRedirect(gitHubLinkService.frontendErrorRedirect(reason, state));
        } catch (Exception e) {
            response.sendRedirect(gitHubLinkService.frontendErrorRedirect("failed", state));
        }
    }

    private static String mapReason(String message) {
        if (message == null) return "failed";
        if (message.contains("already linked")) return "already_linked";
        if (message.contains("authorization code") || message.contains("OAuth state")) return "invalid";
        return "failed";
    }
}
