package com.example.student.controller;

import com.example.student.dto.AuthResponse;
import com.example.student.dto.LoginRequest;
import com.example.student.dto.RegisterRequest;
import com.example.student.model.User;
import com.example.student.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/guest")
    public ResponseEntity<AuthResponse> guest(@RequestBody(required = false) java.util.Map<String, String> body) {
        String guestId = body != null ? body.get("guestId") : null;
        return ResponseEntity.ok(authService.guestLogin(guestId));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id",          user.getId());
        res.put("fullName",    user.getFullName());
        res.put("email",       user.getEmail());
        res.put("role",        user.getRole());
        res.put("collegeName", user.getCollegeName() != null ? user.getCollegeName() : "");
        res.put("avatarColor", user.getAvatarColor() != null ? user.getAvatarColor() : "#4F46E5");
        res.put("xp",          user.getXp());
        res.put("level",       user.getLevel());
        res.put("rank",        user.getRank() != null ? user.getRank() : "E");
        res.put("createdAt",   user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
        return ResponseEntity.ok(res);
    }
}
