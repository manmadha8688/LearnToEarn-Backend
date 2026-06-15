package com.example.student.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendOtpEmail(String to, String otp) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Your OTP — LearnToEarn");
            helper.setFrom("manmadhajayamangala777@gmail.com");
            helper.setText(buildHtml(otp), true);
            mailSender.send(msg);
        } catch (Exception e) {
            // log but don't throw — async, cannot propagate to caller
            System.err.println("[EmailService] Failed to send OTP to " + to + ": " + e.getMessage());
        }
    }

    private String buildHtml(String otp) {
        return """
            <div style="font-family:'Segoe UI',sans-serif;max-width:480px;margin:0 auto;background:#0D1117;border-radius:16px;overflow:hidden;">
              <div style="background:linear-gradient(135deg,#7C3AED,#9B6ED4);padding:28px 32px;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:1.5rem;letter-spacing:0.05em;">⚔ LearnToEarn</h1>
                <p style="color:rgba(255,255,255,0.8);margin:6px 0 0;font-size:0.875rem;">Email Verification</p>
              </div>
              <div style="padding:32px;">
                <p style="color:#CBD5E1;margin:0 0 20px;font-size:0.95rem;">
                  Use the code below to verify your email. Valid for <strong style="color:#C4B5FD;">10 minutes</strong>.
                </p>
                <div style="background:#161B27;border:2px solid rgba(155,110,212,0.4);border-radius:12px;padding:20px;text-align:center;margin-bottom:20px;">
                  <span style="font-size:2.5rem;font-weight:900;letter-spacing:0.3em;color:#C4B5FD;font-family:monospace;">%s</span>
                </div>
                <p style="color:#64748B;font-size:0.8rem;margin:0;">
                  If you did not request this, you can safely ignore this email.
                </p>
              </div>
              <div style="background:#0A0D14;padding:16px 32px;text-align:center;">
                <p style="color:#475569;font-size:0.75rem;margin:0;">© LearnToEarn · Skill Arena Platform</p>
              </div>
            </div>
            """.formatted(otp);
    }
}
