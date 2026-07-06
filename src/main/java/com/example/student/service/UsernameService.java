package com.example.student.service;

import com.example.student.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates and validates public usernames used in the shareable profile URL (/u/{username}).
 * Rules: 3–20 chars, lowercase letters, digits and underscore only.
 */
@Service
public class UsernameService {

    public static final int MIN_LEN = 3;
    public static final int MAX_LEN = 20;
    private static final Pattern VALID = Pattern.compile("^[a-z0-9_]{3,20}$");

    private final UserRepository userRepository;

    public UsernameService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isValidFormat(String username) {
        return username != null && VALID.matcher(username).matches();
    }

    /** Normalise arbitrary text into a candidate handle (no uniqueness guarantee). */
    public String slugify(String source) {
        if (source == null) return "";
        String s = source.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9]+", "_")   // non-alphanumeric → underscore
                .replaceAll("_+", "_")            // collapse repeats
                .replaceAll("^_|_$", "");         // trim edge underscores
        if (s.length() > MAX_LEN) s = s.substring(0, MAX_LEN);
        return s;
    }

    /**
     * Build a unique username from a preferred base (e.g. full name, else email local part).
     * Falls back to "hunter" and appends numeric suffixes until free.
     */
    public String generateUnique(String preferredBase, String emailFallback) {
        String base = slugify(preferredBase);
        if (base.length() < MIN_LEN) {
            String local = emailFallback == null ? "" : emailFallback.split("@")[0];
            base = slugify(local);
        }
        if (base.length() < MIN_LEN) base = "hunter";

        if (!userRepository.existsByUsername(base)) return base;

        for (int i = 1; i < 10000; i++) {
            String suffix = String.valueOf(i);
            String trimmed = base.length() + suffix.length() > MAX_LEN
                    ? base.substring(0, MAX_LEN - suffix.length())
                    : base;
            String candidate = trimmed + suffix;
            if (!userRepository.existsByUsername(candidate)) return candidate;
        }
        // Extremely unlikely fallback.
        return base + System.currentTimeMillis();
    }
}
