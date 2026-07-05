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
    private static final String FROM_EMAIL = "noreply@learnforearn.in";
    private static final String FROM_NAME  = "learnforearn";
    private static final String APP_URL    = "https://learnforearn.in";
    private static final String DASHBOARD_URL = APP_URL + "/skill-arena/dashboard";

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
                log.error("BREVO_API_KEY is not set — emails will fail in production.");
            }
        }
    }

    // ── OTP: email verification (registration) ───────────────────────
    public void sendOtpEmail(String to, String otp) {
        sendOtpEmail(
            to, otp,
            "Verify your email",
            "Your learnforearn verification code",
            "Use the code below to verify your email and finish creating your account."
        );
    }

    // ── OTP: password reset ──────────────────────────────────────────
    public void sendPasswordResetOtpEmail(String to, String otp) {
        sendOtpEmail(
            to, otp,
            "Reset your password",
            "Reset your learnforearn password",
            "Use the code below to reset your password. If you did not request this, no action is needed."
        );
    }

    private void sendOtpEmail(String to, String otp, String purpose, String subject, String intro) {
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
            String html = buildOtpHtml(otp, purpose, intro, recipient);
            String text = buildOtpText(otp, purpose, intro, recipient);
            String body = buildPayload(recipient, subject, html, text);
            HttpResponse<String> response = send(body);
            log.info("OTP email sent to {} (Brevo status {})", recipient, response.statusCode());
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Failed to send OTP email. Please try again in a moment.");
        }
    }

    // ── Welcome email (after successful registration) ─────────────────
    // Best-effort: never throws — registration must succeed even if the email fails.
    public void sendWelcomeEmail(String to, String fullName) {
        String recipient = to == null ? "" : to.trim().toLowerCase();
        String name = firstName(fullName);

        if (apiKey == null || apiKey.isBlank()) {
            if (isLocalDev()) log.warn("BREVO_API_KEY not set — skipping welcome email for {}", recipient);
            return;
        }

        try {
            String html = buildWelcomeHtml(name);
            String text = buildWelcomeText(name);
            String body = buildPayload(recipient, "Welcome to learnforearn — your Hunter path begins", html, text);
            HttpResponse<String> response = send(body);
            log.info("Welcome email sent to {} (Brevo status {})", recipient, response.statusCode());
        } catch (Exception e) {
            log.warn("Welcome email could not be sent to {}: {}", recipient, e.getMessage());
        }
    }

    // ── Password changed confirmation (after reset completes) ─────────
    // Best-effort: never throws — the password change itself has already succeeded.
    public void sendPasswordChangedEmail(String to, String fullName) {
        String recipient = to == null ? "" : to.trim().toLowerCase();
        String name = firstName(fullName);

        if (apiKey == null || apiKey.isBlank()) {
            if (isLocalDev()) log.warn("BREVO_API_KEY not set — skipping password-changed email for {}", recipient);
            return;
        }

        try {
            String html = buildPasswordChangedHtml(name);
            String text = buildPasswordChangedText(name);
            String body = buildPayload(recipient, "Your learnforearn password was changed", html, text);
            HttpResponse<String> response = send(body);
            log.info("Password-changed email sent to {} (Brevo status {})", recipient, response.statusCode());
        } catch (Exception e) {
            log.warn("Password-changed email could not be sent to {}: {}", recipient, e.getMessage());
        }
    }

    private boolean isLocalDev() {
        return activeProfiles.contains("local");
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Hunter";
        return fullName.trim().split("\\s+")[0];
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

    private String buildPayload(String toEmail, String subject, String htmlContent, String textContent) throws Exception {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", FROM_NAME);
        sender.put("email", FROM_EMAIL);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender);
        payload.put("to", List.of(Map.of("email", toEmail)));
        payload.put("subject", subject);
        payload.put("htmlContent", htmlContent);
        if (textContent != null && !textContent.isBlank()) {
            payload.put("textContent", textContent);
        }
        return objectMapper.writeValueAsString(payload);
    }

    // ── Shared HTML shell ─────────────────────────────────────────────
    private String shell(String purpose, String innerHtml) {
        return "<div style='font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;max-width:480px;margin:0 auto;background:#0D1117;border-radius:16px;overflow:hidden;'>" +
               "<div style='background:linear-gradient(135deg,#7C3AED,#9B6ED4);padding:28px 32px;text-align:center;'>" +
               "<h1 style='color:#fff;margin:0;font-size:1.5rem;letter-spacing:0.02em;'>&#9876; learnforearn</h1>" +
               "<p style='color:rgba(255,255,255,0.85);margin:6px 0 0;font-size:0.875rem;'>" + purpose + "</p></div>" +
               "<div style='padding:32px;'>" + innerHtml + "</div>" +
               "<div style='background:#0A0D14;padding:16px 32px;text-align:center;'>" +
               "<p style='color:#475569;font-size:0.75rem;margin:0;'>&#169; learnforearn &middot; ARISE Skill Arena</p></div></div>";
    }

    private String buildOtpHtml(String otp, String purpose, String intro, String recipient) {
        String inner =
            "<p style='color:#94A3B8;margin:0 0 4px;font-size:0.8rem;'>Requested for <strong style='color:#CBD5E1;'>" + recipient + "</strong></p>" +
            "<p style='color:#CBD5E1;margin:0 0 20px;font-size:0.95rem;'>" + intro + " This code is valid for <strong style='color:#C4B5FD;'>10 minutes</strong>.</p>" +
            "<div style='background:#161B27;border:2px solid rgba(155,110,212,0.4);border-radius:12px;padding:20px;text-align:center;margin-bottom:20px;'>" +
            "<span style='font-size:2.5rem;font-weight:900;letter-spacing:0.3em;color:#C4B5FD;font-family:monospace;'>" + otp + "</span></div>" +
            "<p style='color:#64748B;font-size:0.8rem;margin:0 0 6px;'>Never share this code. learnforearn will never ask for it over phone, chat, or social media.</p>" +
            "<p style='color:#64748B;font-size:0.8rem;margin:0;'>If you did not request this, you can safely ignore this email.</p>";
        return shell(purpose, inner);
    }

    private String buildOtpText(String otp, String purpose, String intro, String recipient) {
        return "learnforearn — " + purpose + "\n\n" +
               "Requested for: " + recipient + "\n" +
               intro + "\n\n" +
               "Your code: " + otp + "\n" +
               "This code is valid for 10 minutes.\n\n" +
               "Never share this code. learnforearn will never ask for it over phone, chat, or social media.\n" +
               "If you did not request this, you can safely ignore this email.\n\n" +
               "— learnforearn · ARISE Skill Arena";
    }

    private String buildWelcomeHtml(String name) {
        String inner =
            "<p style='color:#E2E8F0;margin:0 0 16px;font-size:1.05rem;'>Welcome, " + name + ".</p>" +
            "<p style='color:#CBD5E1;margin:0 0 20px;font-size:0.95rem;line-height:1.6;'>" +
            "Your account is ready. Everyone starts at <strong style='color:#C4B5FD;'>Rank E</strong> — S-rank is earned one concept at a time. Here is how to begin:</p>" +
            "<ul style='color:#CBD5E1;font-size:0.92rem;line-height:1.7;padding-left:18px;margin:0 0 24px;'>" +
            "<li>Pick a subject and complete your first concept.</li>" +
            "<li>Take a quiz to earn XP — the first concept each day carries a bonus.</li>" +
            "<li>Enroll in a roadmap if you prefer a guided path.</li></ul>" +
            "<div style='text-align:center;margin-bottom:20px;'>" +
            "<a href='" + DASHBOARD_URL + "' style='display:inline-block;background:linear-gradient(135deg,#7C3AED,#9B6ED4);color:#fff;text-decoration:none;font-weight:700;padding:13px 28px;border-radius:10px;font-size:0.95rem;'>Enter the Skill Arena</a></div>" +
            "<p style='color:#64748B;font-size:0.8rem;margin:0;'>Rank E today. The climb starts now.</p>";
        return shell("Your Hunter path begins", inner);
    }

    private String buildWelcomeText(String name) {
        return "Welcome to learnforearn, " + name + ".\n\n" +
               "Your account is ready. Everyone starts at Rank E — S-rank is earned one concept at a time.\n\n" +
               "How to begin:\n" +
               " - Pick a subject and complete your first concept.\n" +
               " - Take a quiz to earn XP — the first concept each day carries a bonus.\n" +
               " - Enroll in a roadmap if you prefer a guided path.\n\n" +
               "Enter the Skill Arena: " + DASHBOARD_URL + "\n\n" +
               "Rank E today. The climb starts now.\n\n" +
               "— learnforearn · ARISE Skill Arena";
    }

    private String buildPasswordChangedHtml(String name) {
        String inner =
            "<p style='color:#E2E8F0;margin:0 0 16px;font-size:1.05rem;'>Hi " + name + ",</p>" +
            "<p style='color:#CBD5E1;margin:0 0 20px;font-size:0.95rem;line-height:1.6;'>" +
            "This is a confirmation that your learnforearn password was just changed. You can now sign in with your new password.</p>" +
            "<div style='text-align:center;margin-bottom:20px;'>" +
            "<a href='" + APP_URL + "/login' style='display:inline-block;background:linear-gradient(135deg,#7C3AED,#9B6ED4);color:#fff;text-decoration:none;font-weight:700;padding:13px 28px;border-radius:10px;font-size:0.95rem;'>Sign in</a></div>" +
            "<div style='background:#161B27;border:1px solid rgba(239,68,68,0.35);border-radius:10px;padding:16px;'>" +
            "<p style='color:#FCA5A5;font-size:0.85rem;margin:0;'>If you did not make this change, your account may be at risk. Please reset your password immediately and contact support.</p></div>";
        return shell("Password changed", inner);
    }

    private String buildPasswordChangedText(String name) {
        return "Hi " + name + ",\n\n" +
               "This is a confirmation that your learnforearn password was just changed. " +
               "You can now sign in with your new password.\n\n" +
               "Sign in: " + APP_URL + "/login\n\n" +
               "If you did not make this change, your account may be at risk. " +
               "Please reset your password immediately and contact support.\n\n" +
               "— learnforearn · ARISE Skill Arena";
    }
}
