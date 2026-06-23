package com.example.student.controller;

import com.example.student.dto.AuthResponse;
import com.example.student.dto.LoginRequest;
import com.example.student.dto.RegisterRequest;
import com.example.student.model.User;
import com.example.student.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    private void setJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 24h — matches JWT expiry
        response.addCookie(cookie);
    }

    private void clearJwtCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
        AuthResponse auth = authService.register(req);
        setJwtCookie(response, auth.getToken());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        AuthResponse auth = authService.login(req);
        setJwtCookie(response, auth.getToken());
        return ResponseEntity.ok(auth);
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
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id",            user.getId());
        res.put("fullName",      user.getFullName());
        res.put("email",         user.getEmail());
        res.put("role",          user.getRole());
        res.put("collegeName",   user.getCollegeName() != null ? user.getCollegeName() : "");
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
