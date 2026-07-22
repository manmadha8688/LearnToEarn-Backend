package com.example.student.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Lazy(false) // @Scheduled cleanup must be eager so its task registers under lazy-init
public class OtpService {

    private final EmailService emailService;

    private final ConcurrentHashMap<String, OtpEntry> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OtpEntry> resetStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OtpEntry> contactStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IpLimit> ipLimits = new ConcurrentHashMap<>();
    // Failed verification counters per email — caps brute-force guessing of the 6-digit code.
    private final ConcurrentHashMap<String, Integer> verifyFails = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> resetVerifyFails = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> contactVerifyFails = new ConcurrentHashMap<>();

    private static final int MAX_SENDS_PER_IP_PER_HOUR = 10;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    // Cryptographically strong RNG so 6-digit OTPs are not predictable.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        store.put(email, new OtpEntry(otp, now.plusMinutes(10), now, false));
        verifyFails.remove(email); // fresh code → reset the guess counter
        recordIpSend(clientIp);
        try {
            emailService.sendOtpEmail(email, otp);
        } catch (RuntimeException e) {
            store.remove(email);
            throw e;
        }
        return 0;
    }

    // ── Verify OTP ───────────────────────────────────────────────────
    public boolean verifyOtp(String email, String otp) {
        OtpEntry entry = store.get(email);
        if (entry == null) return false;
        if (entry.expiresAt().isBefore(now())) { store.remove(email); verifyFails.remove(email); return false; }
        if (!entry.otp().equals(otp.trim())) {
            // Too many wrong guesses → invalidate the code so it can't be brute-forced.
            // A new code must be requested (send is separately rate-limited).
            if (verifyFails.merge(email, 1, Integer::sum) >= MAX_VERIFY_ATTEMPTS) {
                store.remove(email);
                verifyFails.remove(email);
            }
            return false;
        }
        store.put(email, new OtpEntry(entry.otp(), entry.expiresAt(), entry.sentAt(), true));
        verifyFails.remove(email);
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

    // ── Password reset OTP (separate store) ──────────────────────────
    public long sendResetOtp(String email, String clientIp) {
        checkIpLimit(clientIp);

        OtpEntry existing = resetStore.get(email);
        LocalDateTime now = now();

        if (existing != null) {
            long cooldown = ChronoUnit.SECONDS.between(now, existing.sentAt().plusSeconds(60));
            if (cooldown > 0) return cooldown;
        }

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        resetStore.put(email, new OtpEntry(otp, now.plusMinutes(10), now, false));
        resetVerifyFails.remove(email); // fresh code → reset the guess counter
        recordIpSend(clientIp);
        try {
            emailService.sendPasswordResetOtpEmail(email, otp);
        } catch (RuntimeException e) {
            resetStore.remove(email);
            throw e;
        }
        return 0;
    }

    public boolean verifyResetOtp(String email, String otp) {
        OtpEntry entry = resetStore.get(email);
        if (entry == null) return false;
        if (entry.expiresAt().isBefore(now())) { resetStore.remove(email); resetVerifyFails.remove(email); return false; }
        if (!entry.otp().equals(otp.trim())) {
            if (resetVerifyFails.merge(email, 1, Integer::sum) >= MAX_VERIFY_ATTEMPTS) {
                resetStore.remove(email);
                resetVerifyFails.remove(email);
            }
            return false;
        }
        resetStore.put(email, new OtpEntry(entry.otp(), entry.expiresAt(), entry.sentAt(), true));
        resetVerifyFails.remove(email);
        return true;
    }

    public boolean isResetVerified(String email) {
        OtpEntry entry = resetStore.get(email);
        if (entry == null) return false;
        if (entry.expiresAt().isBefore(now())) { resetStore.remove(email); return false; }
        return entry.verified();
    }

    public void clearReset(String email) {
        resetStore.remove(email);
    }

    // ── Profile contact email OTP (keyed by userId:email) ──────────────
    private static String contactKey(String userId, String email) {
        return userId + ":" + email.trim().toLowerCase();
    }

    public long sendContactEmailOtp(String userId, String email, String clientIp) {
        checkIpLimit(clientIp);

        String key = contactKey(userId, email);
        OtpEntry existing = contactStore.get(key);
        LocalDateTime now = now();

        if (existing != null) {
            long cooldown = ChronoUnit.SECONDS.between(now, existing.sentAt().plusSeconds(60));
            if (cooldown > 0) return cooldown;
        }

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        contactStore.put(key, new OtpEntry(otp, now.plusMinutes(10), now, false));
        contactVerifyFails.remove(key);
        recordIpSend(clientIp);
        try {
            emailService.sendContactEmailOtpEmail(email, otp);
        } catch (RuntimeException e) {
            contactStore.remove(key);
            throw e;
        }
        return 0;
    }

    public boolean verifyContactEmailOtp(String userId, String email, String otp) {
        String key = contactKey(userId, email);
        OtpEntry entry = contactStore.get(key);
        if (entry == null) return false;
        if (entry.expiresAt().isBefore(now())) {
            contactStore.remove(key);
            contactVerifyFails.remove(key);
            return false;
        }
        if (!entry.otp().equals(otp.trim())) {
            if (contactVerifyFails.merge(key, 1, Integer::sum) >= MAX_VERIFY_ATTEMPTS) {
                contactStore.remove(key);
                contactVerifyFails.remove(key);
            }
            return false;
        }
        contactStore.put(key, new OtpEntry(entry.otp(), entry.expiresAt(), entry.sentAt(), true));
        contactVerifyFails.remove(key);
        return true;
    }

    public boolean isContactEmailVerified(String userId, String email) {
        String key = contactKey(userId, email);
        OtpEntry entry = contactStore.get(key);
        if (entry == null) return false;
        if (entry.expiresAt().isBefore(now())) {
            contactStore.remove(key);
            return false;
        }
        return entry.verified();
    }

    public void clearContactEmail(String userId, String email) {
        contactStore.remove(contactKey(userId, email));
    }

    // ── Scheduled cleanup — every 15 minutes ─────────────────────────
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void cleanupExpired() {
        LocalDateTime now = now();
        store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        resetStore.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        contactStore.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        ipLimits.entrySet().removeIf(e ->
            ChronoUnit.HOURS.between(e.getValue().windowStart(), now) >= 1
        );
        // Drop orphaned guess counters for codes that are no longer active.
        verifyFails.keySet().removeIf(k -> !store.containsKey(k));
        resetVerifyFails.keySet().removeIf(k -> !resetStore.containsKey(k));
        contactVerifyFails.keySet().removeIf(k -> !contactStore.containsKey(k));
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
