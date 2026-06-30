package com.example.student.controller;

import com.example.student.dto.ResetPasswordRequest;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.service.AuthService;
import com.example.student.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final AuthService authService;

    public PasswordResetController(OtpService otpService, UserRepository userRepository, AuthService authService) {
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> sendResetOtp(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));

        String normalized = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null)
            return ResponseEntity.status(404).body(Map.of("error", "No account found with this email."));
        if ("GUEST".equals(user.getRole()))
            return ResponseEntity.badRequest().body(Map.of("error", "Guest accounts cannot reset password. Please register instead."));

        try {
            long cooldown = otpService.sendResetOtp(normalized, getClientIp(request));
            if (cooldown > 0)
                return ResponseEntity.status(429).body(Map.of(
                    "error", "Please wait before requesting another OTP.",
                    "retryAfter", cooldown
                ));
            return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<?> verifyResetOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp   = body.get("otp");
        if (email == null || otp == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Email and OTP are required"));

        String normalized = email.trim().toLowerCase();
        if (!userRepository.existsByEmail(normalized))
            return ResponseEntity.status(404).body(Map.of("error", "No account found with this email."));

        boolean ok = otpService.verifyResetOtp(normalized, otp.trim());
        if (!ok)
            return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired OTP. Please try again."));

        return ResponseEntity.ok(Map.of("verified", true));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        String normalized = req.getEmail().trim().toLowerCase();
        authService.resetPassword(normalized, req.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
