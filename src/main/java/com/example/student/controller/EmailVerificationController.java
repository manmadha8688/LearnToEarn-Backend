package com.example.student.controller;

import com.example.student.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final OtpService otpService;

    public EmailVerificationController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));

        try {
            long cooldown = otpService.sendOtp(email.trim().toLowerCase(), getClientIp(request));
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
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
