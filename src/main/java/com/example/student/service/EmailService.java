package com.example.student.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String FROM_EMAIL = "manmadhajayamangala777@gmail.com";
    private static final String FROM_NAME  = "LearnToEarn";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    @Value("${brevo.api.key:}")
    private String apiKey;

    @Value("${spring.profiles.active:local}")
    private String activeProfiles;

    public EmailService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void logEmailConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            if (isLocalDev()) {
                log.warn("BREVO_API_KEY is not set — OTP emails will be logged to console in local dev only.");
            } else {
                log.error("BREVO_API_KEY is not set — OTP emails will fail in production.");
            }
        }
    }

    public void sendOtpEmail(String to, String otp) {
        sendOtpEmail(to, otp, "Email Verification", "Use the code below to verify your email.");
    }

    public void sendPasswordResetOtpEmail(String to, String otp) {
        sendOtpEmail(to, otp, "Password Reset", "Use the code below to reset your password.");
    }

    private void sendOtpEmail(String to, String otp, String purpose, String intro) {
        String recipient = to.trim().toLowerCase();

        if (apiKey == null || apiKey.isBlank()) {
            if (isLocalDev()) {
                log.warn("==================================================");
                log.warn("BREVO_API_KEY not set — DEV OTP for {}: {}", recipient, otp);
                log.warn("Set BREVO_API_KEY in your environment to send real emails.");
                log.warn("==================================================");
                return;
            }
            throw new RuntimeException("Email service is not configured. Please contact support.");
        }

        try {
            String html = buildOtpHtml(otp, purpose, intro);
            String body = buildPayload(recipient, "Your OTP - LearnToEarn", html);
            HttpResponse<String> response = send(body);
            log.info("OTP email sent to {} (Brevo status {})", recipient, response.statusCode());
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Failed to send OTP email. Please try again in a moment.");
        }
    }

    private boolean isLocalDev() {
        return activeProfiles.contains("local");
    }

    private HttpResponse<String> send(String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BREVO_URL))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Brevo error " + response.statusCode() + ": " + response.body());
        }
        return response;
    }

    private String buildPayload(String toEmail, String subject, String htmlContent) throws Exception {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", FROM_NAME);
        sender.put("email", FROM_EMAIL);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender);
        payload.put("to", List.of(Map.of("email", toEmail)));
        payload.put("subject", subject);
        payload.put("htmlContent", htmlContent);
        return objectMapper.writeValueAsString(payload);
    }

    private String buildOtpHtml(String otp, String purpose, String intro) {
        return "<div style='font-family:Segoe UI,sans-serif;max-width:480px;margin:0 auto;background:#0D1117;border-radius:16px;overflow:hidden;'>" +
               "<div style='background:linear-gradient(135deg,#7C3AED,#9B6ED4);padding:28px 32px;text-align:center;'>" +
               "<h1 style='color:#fff;margin:0;font-size:1.5rem;'>&#9876; LearnToEarn</h1>" +
               "<p style='color:rgba(255,255,255,0.8);margin:6px 0 0;font-size:0.875rem;'>" + purpose + "</p></div>" +
               "<div style='padding:32px;'>" +
               "<p style='color:#CBD5E1;margin:0 0 20px;font-size:0.95rem;'>" + intro + " Valid for <strong style='color:#C4B5FD;'>10 minutes</strong>.</p>" +
               "<div style='background:#161B27;border:2px solid rgba(155,110,212,0.4);border-radius:12px;padding:20px;text-align:center;margin-bottom:20px;'>" +
               "<span style='font-size:2.5rem;font-weight:900;letter-spacing:0.3em;color:#C4B5FD;font-family:monospace;'>" + otp + "</span></div>" +
               "<p style='color:#64748B;font-size:0.8rem;margin:0;'>If you did not request this, you can safely ignore this email.</p></div>" +
               "<div style='background:#0A0D14;padding:16px 32px;text-align:center;'>" +
               "<p style='color:#475569;font-size:0.75rem;margin:0;'>&#169; LearnToEarn &middot; Skill Arena Platform</p></div></div>";
    }
}
