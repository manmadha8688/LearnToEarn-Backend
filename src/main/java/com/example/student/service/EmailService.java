package com.example.student.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class EmailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String FROM_EMAIL = "manmadhajayamangala777@gmail.com";
    private static final String FROM_NAME  = "LearnToEarn";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Async
    public void sendOtpEmail(String to, String otp) {
        try {
            String body = buildPayload(to, "Your OTP — LearnToEarn", buildOtpHtml(otp));
            send(body);
            System.out.println("[EmailService] OTP sent to " + to);
        } catch (Exception e) {
            System.err.println("[EmailService] FAILED: " + e.getMessage());
        }
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

    private String buildPayload(String toEmail, String subject, String htmlContent) {
        return String.format("""
                {
                  "sender": {"name": "%s", "email": "%s"},
                  "to": [{"email": "%s"}],
                  "subject": "%s",
                  "htmlContent": %s
                }
                """,
                FROM_NAME, FROM_EMAIL, toEmail, subject,
                toJson(htmlContent)
        );
    }

    private String toJson(String html) {
        return "\"" + html.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\"";
    }

    private String buildOtpHtml(String otp) {
        return "<div style='font-family:Segoe UI,sans-serif;max-width:480px;margin:0 auto;background:#0D1117;border-radius:16px;overflow:hidden;'>" +
               "<div style='background:linear-gradient(135deg,#7C3AED,#9B6ED4);padding:28px 32px;text-align:center;'>" +
               "<h1 style='color:#fff;margin:0;font-size:1.5rem;'>&#9876; LearnToEarn</h1>" +
               "<p style='color:rgba(255,255,255,0.8);margin:6px 0 0;font-size:0.875rem;'>Email Verification</p></div>" +
               "<div style='padding:32px;'>" +
               "<p style='color:#CBD5E1;margin:0 0 20px;font-size:0.95rem;'>Use the code below to verify your email. Valid for <strong style='color:#C4B5FD;'>10 minutes</strong>.</p>" +
               "<div style='background:#161B27;border:2px solid rgba(155,110,212,0.4);border-radius:12px;padding:20px;text-align:center;margin-bottom:20px;'>" +
               "<span style='font-size:2.5rem;font-weight:900;letter-spacing:0.3em;color:#C4B5FD;font-family:monospace;'>" + otp + "</span></div>" +
               "<p style='color:#64748B;font-size:0.8rem;margin:0;'>If you did not request this, you can safely ignore this email.</p></div>" +
               "<div style='background:#0A0D14;padding:16px 32px;text-align:center;'>" +
               "<p style='color:#475569;font-size:0.75rem;margin:0;'>&#169; LearnToEarn &middot; Skill Arena Platform</p></div></div>";
    }
}
