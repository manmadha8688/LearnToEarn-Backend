package com.example.student.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private final JavaMailSender mailSender;

    // email -> OTP entry
    private final ConcurrentHashMap<String, OtpEntry> store = new ConcurrentHashMap<>();
    // ip -> request count in current window
    private final ConcurrentHashMap<String, IpLimit> ipLimits = new ConcurrentHashMap<>();

    private static final int MAX_SENDS_PER_IP_PER_HOUR = 10;

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    record OtpEntry(String otp, LocalDateTime expiresAt, LocalDateTime sentAt, boolean verified) {}
    record IpLimit(int count, LocalDateTime windowStart) {}

    // ── Send OTP ─────────────────────────────────────────────────────
    public long sendOtp(String email, String clientIp) {
        checkIpLimit(clientIp);

        OtpEntry existing = store.get(email);
        LocalDateTime now = now();

        if (existing != null) {
            long cooldown = ChronoUnit.SECONDS.between(now, existing.sentAt().plusSeconds(60));
            if (cooldown > 0) return cooldown;
        }

        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        store.put(email, new OtpEntry(otp, now.plusMinutes(10), now, false));
        recordIpSend(clientIp);
        sendEmail(email, otp);
        return 0;
    }

    // ── Verify OTP ───────────────────────────────────────────────────
    public boolean verifyOtp(String email, String otp) {
        OtpEntry entry = store.get(email);
        if (entry == null) return false;
        if (entry.expiresAt().isBefore(now())) { store.remove(email); return false; }
        if (!entry.otp().equals(otp.trim())) return false;
        store.put(email, new OtpEntry(entry.otp(), entry.expiresAt(), entry.sentAt(), true));
        return true;
    }

    // ── Check verified ───────────────────────────────────────────────
    public boolean isVerified(String email) {
        OtpEntry entry = store.get(email);
        if (entry == null) return false;
        if (entry.expiresAt().isBefore(now())) { store.remove(email); return false; }
        return entry.verified();
    }

    // ── Clear after registration ─────────────────────────────────────
    public void clear(String email) {
        store.remove(email);
    }

    // ── Scheduled cleanup — every 15 minutes ─────────────────────────
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void cleanupExpired() {
        LocalDateTime now = now();
        store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        ipLimits.entrySet().removeIf(e ->
            ChronoUnit.HOURS.between(e.getValue().windowStart(), now) >= 1
        );
    }

    // ── IP rate limiting ─────────────────────────────────────────────
    private void checkIpLimit(String ip) {
        if (ip == null) return;
        IpLimit limit = ipLimits.get(ip);
        LocalDateTime now = now();
        if (limit != null && ChronoUnit.HOURS.between(limit.windowStart(), now) < 1) {
            if (limit.count() >= MAX_SENDS_PER_IP_PER_HOUR)
                throw new RuntimeException("Too many OTP requests. Try again later.");
        }
    }

    private void recordIpSend(String ip) {
        if (ip == null) return;
        IpLimit limit = ipLimits.get(ip);
        LocalDateTime now = now();
        if (limit == null || ChronoUnit.HOURS.between(limit.windowStart(), now) >= 1) {
            ipLimits.put(ip, new IpLimit(1, now));
        } else {
            ipLimits.put(ip, new IpLimit(limit.count() + 1, limit.windowStart()));
        }
    }

    // ── Email ────────────────────────────────────────────────────────
    private void sendEmail(String to, String otp) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Your OTP — LearnToEarn");
            helper.setFrom("manmadhajayamangala777@gmail.com");
            helper.setText(buildHtml(otp), true);
            mailSender.send(msg);
        } catch (Exception e) {
            throw new RuntimeException("Could not send OTP email. Please try again.");
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

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }
}
