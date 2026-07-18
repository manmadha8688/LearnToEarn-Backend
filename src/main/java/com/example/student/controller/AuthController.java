package com.example.student.controller;

import com.example.student.dto.AuthResponse;
import com.example.student.dto.LoginRequest;
import com.example.student.dto.RegisterRequest;
import com.example.student.model.User;
import com.example.student.security.ClientIpResolver;
import com.example.student.security.LoginAttemptService;
import com.example.student.security.RateLimiterService;
import com.example.student.service.AuthService;
import com.example.student.service.GoogleAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final LoginAttemptService loginAttemptService;
    private final RateLimiterService rateLimiter;

    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    public AuthController(AuthService authService, GoogleAuthService googleAuthService,
                          LoginAttemptService loginAttemptService, RateLimiterService rateLimiter) {
        this.authService = authService;
        this.googleAuthService = googleAuthService;
        this.loginAttemptService = loginAttemptService;
        this.rateLimiter = rateLimiter;
    }

    private void setJwtCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(86400)
                // SameSite=None required for cross-origin (Vercel frontend + Render backend)
                // SameSite=Lax for local HTTP dev (secure=false)
                .sameSite(secureCookie ? "None" : "Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearJwtCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite(secureCookie ? "None" : "Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req,
                                      HttpServletRequest request, HttpServletResponse response) {
        long retryAfter = rateLimiter.hit("register", getClientIp(request), 10, 3600);
        if (retryAfter > 0) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many attempts. Please try again later.",
                "retryAfter", retryAfter));
        }
        AuthResponse auth = authService.register(req);
        setJwtCookie(response, auth.getToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
        String email = req.getEmail() == null ? "" : req.getEmail().trim().toLowerCase();
        String ip = getClientIp(request);

        long lockedFor = loginAttemptService.blockedForSeconds(email, ip);
        if (lockedFor > 0) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many failed attempts. Please try again later.",
                "retryAfter", lockedFor));
        }

        try {
            AuthResponse auth = authService.login(req);
            loginAttemptService.reset(email, ip);
            setJwtCookie(response, auth.getToken());
            return ResponseEntity.ok(auth);
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(email, ip);
            throw e; // handled by GlobalExceptionHandler → 401 "Invalid email or password"
        }
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }

    @PostMapping("/google")
    public ResponseEntity<?> google(@RequestBody Map<String, String> body,
                                    HttpServletRequest request, HttpServletResponse response) {
        String ip = getClientIp(request);
        // Reuse the IP-based limiter (keyed on a fixed marker) so a flood of bad tokens
        // from one IP is throttled the same way repeated failed logins are.
        long lockedFor = loginAttemptService.blockedForSeconds("google", ip);
        if (lockedFor > 0) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many attempts. Please try again later.",
                "retryAfter", lockedFor));
        }

        String credential = body != null ? body.get("credential") : null;
        try {
            AuthResponse auth = googleAuthService.loginWithGoogle(credential);
            loginAttemptService.reset("google", ip);
            setJwtCookie(response, auth.getToken());
            return ResponseEntity.ok(auth);
        } catch (RuntimeException e) {
            loginAttemptService.recordFailure("google", ip);
            throw e; // GlobalExceptionHandler → 400 with the user-safe message
        }
    }

    @PostMapping("/guest")
    public ResponseEntity<?> guest(@RequestBody(required = false) java.util.Map<String, String> body,
                                   HttpServletRequest request, HttpServletResponse response) {
        String guestId = body != null ? body.get("guestId") : null;
        String ip = getClientIp(request);
        // Brand-new guest creation is the expensive path — throttle it tightly.
        if (guestId == null || guestId.isBlank()) {
            long retryAfter = rateLimiter.hit("guest", ip, 20, 3600);
            if (retryAfter > 0) {
                return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many guest sessions from this network. Please try again later.",
                    "retryAfter", retryAfter));
            }
        } else {
            // Reuse path: generous cap so real users reloading are never blocked, but a
            // flood of guessed device tokens from one IP is still throttled.
            long retryAfter = rateLimiter.hit("guest-reuse", ip, 60, 3600);
            if (retryAfter > 0) {
                return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many guest sessions from this network. Please try again later.",
                    "retryAfter", retryAfter));
            }
        }
        AuthResponse auth = authService.guestLogin(guestId);
        setJwtCookie(response, auth.getToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user, HttpServletResponse response) {
        if (user != null) {
            authService.logout(user.getEmail());
        }
        clearJwtCookie(response);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id",            user.getId());
        res.put("fullName",      user.getFullName());
        res.put("email",         user.getEmail());
        res.put("username",      user.getPublicUsername());
        res.put("bio",           user.getBio() != null ? user.getBio() : "");
        res.put("githubUrl",     user.getGithubUrl() != null ? user.getGithubUrl() : "");
        res.put("githubLogin",   user.getGithubLogin() != null ? user.getGithubLogin() : "");
        res.put("githubConnected", user.getGithubId() != null && !user.getGithubId().isBlank());
        res.put("linkedinUrl",   user.getLinkedinUrl() != null ? user.getLinkedinUrl() : "");
        res.put("portfolioUrl",  user.getPortfolioUrl() != null ? user.getPortfolioUrl() : "");
        res.put("publicEmail",   user.getPublicEmail() != null ? user.getPublicEmail() : "");
        res.put("useLoginEmailForContact", Boolean.TRUE.equals(user.getUseLoginEmailForContact()));
        res.put("location",      user.getLocation() != null ? user.getLocation() : "");
        res.put("mobile",        user.getMobile() != null ? user.getMobile() : "");
        res.put("education",     user.getEducation());
        res.put("publicProfile", user.getPublicProfile() == null ? Boolean.TRUE : user.getPublicProfile());
        res.put("featuredResumeId", user.getFeaturedResumeId() != null ? user.getFeaturedResumeId() : "");
        res.put("role",          user.getRole());
        res.put("providers",     user.getProviders() != null ? user.getProviders() : java.util.List.of());
        res.put("avatarColor",   user.getAvatarColor() != null ? user.getAvatarColor() : "#4F46E5");
        res.put("xp",            user.getXp());
        res.put("level",         user.getLevel());
        res.put("rank",          user.getRank() != null ? user.getRank() : "E");
        res.put("createdAt",     user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
        res.put("lastLoginAt",   user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
        res.put("lastLogoutAt",  user.getLastLogoutAt() != null ? user.getLastLogoutAt().toString() : null);
        res.put("loginCount",    user.getLoginCount());
        return ResponseEntity.ok(res);
    }
}
