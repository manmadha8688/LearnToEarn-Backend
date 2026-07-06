package com.example.student.service;

import com.example.student.dto.AuthResponse;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.security.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles "Continue with Google". The frontend obtains a Google ID token (via Google
 * Identity Services) and posts it here; we verify it server-side, then find, link, or
 * create the single account for that email and issue the same httpOnly JWT cookie as
 * password login.
 *
 * <p>Invariant: one email = one account. Google either logs into / links onto the
 * existing account for that email, or creates a brand-new one — never a duplicate.
 */
@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String PROVIDER = "google";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UsernameService usernameService;
    private final EmailService emailService;
    private final LoginEventService loginEventService;

    @Value("${google.client-id:}")
    private String googleClientId;

    private volatile GoogleIdTokenVerifier verifier;

    public GoogleAuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                             JwtUtil jwtUtil, UsernameService usernameService,
                             EmailService emailService, LoginEventService loginEventService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.usernameService = usernameService;
        this.emailService = emailService;
        this.loginEventService = loginEventService;
    }

    public AuthResponse loginWithGoogle(String credential) {
        if (credential == null || credential.isBlank())
            throw new RuntimeException("Missing Google credential. Please try again.");
        if (googleClientId == null || googleClientId.isBlank())
            throw new RuntimeException("Google sign-in is not available right now.");

        GoogleIdToken.Payload payload = verify(credential);

        String email = payload.getEmail();
        if (email == null || email.isBlank())
            throw new RuntimeException("Google did not share an email for this account.");
        email = email.trim().toLowerCase();

        if (!Boolean.TRUE.equals(payload.getEmailVerified()))
            throw new RuntimeException("Your Google email is not verified. Please verify it with Google first.");

        String googleId = payload.getSubject();
        String name = (String) payload.get("name");

        // 1. Returning Google user — matched by stable Google id.
        Optional<User> byGoogle = userRepository.findByGoogleId(googleId);
        if (byGoogle.isPresent()) {
            return issueForExisting(byGoogle.get());
        }

        // 2. Existing account with the same email — link Google onto it (no duplicate account).
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setGoogleId(googleId);
            addProvider(user, PROVIDER);
            return issueForExisting(user);
        }

        // 3. Brand-new user — create a Google-only account (no password).
        return createGoogleUser(email, googleId, name);
    }

    private GoogleIdToken.Payload verify(String credential) {
        try {
            GoogleIdToken idToken = getVerifier().verify(credential);
            if (idToken == null)
                throw new RuntimeException("Google sign-in could not be verified. Please try again.");
            return idToken.getPayload();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw new RuntimeException("Google sign-in could not be verified. Please try again.");
        }
    }

    private GoogleIdTokenVerifier getVerifier() {
        GoogleIdTokenVerifier v = verifier;
        if (v == null) {
            synchronized (this) {
                v = verifier;
                if (v == null) {
                    v = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                            .setAudience(Collections.singletonList(googleClientId))
                            .build();
                    verifier = v;
                }
            }
        }
        return v;
    }

    private AuthResponse issueForExisting(User user) {
        user.setLastLoginAt(LocalDateTime.now(IST));
        user.setLoginCount(user.getLoginCount() + 1);
        User saved = userRepository.save(user);
        loginEventService.record(saved, "google");
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getTokenVersion());
        return new AuthResponse(token,
                new AuthResponse.UserDto(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole()));
    }

    private AuthResponse createGoogleUser(String email, String googleId, String name) {
        String fullName = (name != null && !name.isBlank())
                ? name.trim()
                : email.substring(0, email.indexOf('@'));

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setGoogleId(googleId);
        // Random, unusable password — Google-only accounts never authenticate by password.
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole("STUDENT");
        user.setProviders(new ArrayList<>(List.of(PROVIDER)));
        user.setAvatarColor("#4F46E5");
        user.setIsActive(true);
        user.setUsername(usernameService.generateUnique(fullName, email));
        user.setLastLoginAt(LocalDateTime.now(IST));
        user.setLoginCount(1);

        User saved = userRepository.save(user);
        loginEventService.record(saved, "google");
        emailService.sendWelcomeEmail(saved.getEmail(), saved.getFullName()); // best-effort, never throws
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getTokenVersion());
        return new AuthResponse(token,
                new AuthResponse.UserDto(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole()));
    }

    private void addProvider(User user, String provider) {
        List<String> providers = user.getProviders();
        if (providers == null) {
            providers = new ArrayList<>();
            user.setProviders(providers);
        }
        if (!providers.contains(provider)) {
            providers.add(provider);
        }
    }
}
