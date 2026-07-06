package com.example.student.controller;

import com.example.student.dto.AuthResponse;
import com.example.student.dto.LoginRequest;
import com.example.student.dto.RegisterRequest;
import com.example.student.model.User;
import com.example.student.security.LoginAttemptService;
import com.example.student.service.AuthService;
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
    private final LoginAttemptService loginAttemptService;

    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    public AuthController(AuthService authService, LoginAttemptService loginAttemptService) {
        this.authService = authService;
        this.loginAttemptService = loginAttemptService;
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
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
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
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    @PostMapping("/guest")
    public ResponseEntity<AuthResponse> guest(@RequestBody(required = false) java.util.Map<String, String> body, HttpServletResponse response) {
        String guestId = body != null ? body.get("guestId") : null;
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
        res.put("username",      user.getUsername());
        res.put("bio",           user.getBio() != null ? user.getBio() : "");
        res.put("role",          user.getRole());
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
