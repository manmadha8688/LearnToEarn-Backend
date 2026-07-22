package com.example.student.controller;

import com.example.student.dto.ResetPasswordRequest;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.security.RateLimiterService;
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
    private final RateLimiterService rateLimiter;

    public PasswordResetController(OtpService otpService, UserRepository userRepository,
                                   AuthService authService, RateLimiterService rateLimiter) {
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> sendResetOtp(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));

        String normalized = email.trim().toLowerCase();
        // Uniform response: never reveal whether an account exists (anti-enumeration).
        // A reset code is only actually mailed to eligible (non-guest) accounts, but the
        // caller always sees the same "if it exists, we've sent a code" message.
        String neutral = "If an account exists for that email, a reset code has been sent.";
        User user = userRepository.findByEmail(normalized).orElse(null);
        boolean eligible = user != null && !"GUEST".equals(user.getRole());
        if (!eligible)
            return ResponseEntity.ok(Map.of("message", neutral));

        try {
            long cooldown = otpService.sendResetOtp(normalized, getClientIp(request));
            if (cooldown > 0)
                return ResponseEntity.status(429).body(Map.of(
                    "error", "Please wait before requesting another OTP.",
                    "retryAfter", cooldown
                ));
            return ResponseEntity.ok(Map.of("message", neutral));
        } catch (RuntimeException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<?> verifyResetOtp(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        String otp   = body.get("otp");
        if (email == null || otp == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Email and OTP are required"));

        // Throttle OTP-verification attempts per IP so a 6-digit reset code can't be brute-forced.
        // 20 tries / 10 min is far above any legitimate reset flow but makes guessing infeasible.
        long retryAfter = rateLimiter.hit("reset-verify", getClientIp(request), 20, 600);
        if (retryAfter > 0)
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many attempts. Please try again later.",
                "retryAfter", retryAfter));

        String normalized = email.trim().toLowerCase();
        // Same generic failure for unknown email and wrong/expired code (anti-enumeration).
        // OTPs are only ever issued to real accounts, so an unknown email simply has no
        // valid code to match.
        boolean ok = userRepository.existsByEmail(normalized)
                && otpService.verifyResetOtp(normalized, otp.trim());
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
        return com.example.student.security.ClientIpResolver.resolve(request);
    }
}
