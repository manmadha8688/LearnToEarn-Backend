package com.example.student.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private final EmailService emailService;

    private final ConcurrentHashMap<String, OtpEntry> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IpLimit> ipLimits = new ConcurrentHashMap<>();

    private static final int MAX_SENDS_PER_IP_PER_HOUR = 10;

    public OtpService(EmailService emailService) {
        this.emailService = emailService;
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
        emailService.sendOtpEmail(email, otp); // called on a DIFFERENT bean — @Async works correctly
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

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }
}
