package com.example.student.controller;

import com.example.student.repository.UserRepository;
import com.example.student.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final OtpService otpService;
    private final UserRepository userRepository;

    public EmailVerificationController(OtpService otpService, UserRepository userRepository) {
        this.otpService = otpService;
        this.userRepository = userRepository;
    }


    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));

        String normalized = email.trim().toLowerCase();

        // Check email exists BEFORE sending OTP
        if (userRepository.existsByEmail(normalized))
            return ResponseEntity.status(409).body(Map.of("error", "An account with this email already exists."));

        try {
            long cooldown = otpService.sendOtp(normalized, getClientIp(request));
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

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp   = body.get("otp");
        if (email == null || otp == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Email and OTP are required"));

        boolean ok = otpService.verifyOtp(email.trim().toLowerCase(), otp.trim());
        if (!ok)
            return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired OTP. Please try again."));

        return ResponseEntity.ok(Map.of("verified", true));
    }

    private String getClientIp(HttpServletRequest request) {
        return com.example.student.security.ClientIpResolver.resolve(request);
    }
}
