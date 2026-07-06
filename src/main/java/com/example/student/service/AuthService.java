package com.example.student.service;

import com.example.student.dto.AuthResponse;
import com.example.student.dto.LoginRequest;
import com.example.student.dto.RegisterRequest;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final EmailService emailService;
    private final UsernameService usernameService;
    private final LoginEventService loginEventService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, AuthenticationManager authenticationManager,
                       OtpService otpService, EmailService emailService,
                       UsernameService usernameService, LoginEventService loginEventService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.otpService = otpService;
        this.emailService = emailService;
        this.usernameService = usernameService;
        this.loginEventService = loginEventService;
    }

    public AuthResponse register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (!otpService.isVerified(email))
            throw new RuntimeException("Email not verified. Please verify your email with OTP first.");

        java.util.Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User u = existing.get();
            // One email = one account. If it's a Google-only account, guide them to Google.
            if (isGoogleOnly(u))
                throw new RuntimeException("This account uses Google Sign-In. Please continue with Google.");
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setFullName(req.getFullName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("STUDENT");
        user.setProviders(new java.util.ArrayList<>(java.util.List.of("local")));
        user.setAvatarColor("#4F46E5");
        user.setIsActive(true);
        user.setUsername(usernameService.generateUnique(req.getFullName(), email));
        // Registration immediately signs the user in — count it as their first login
        // so activity metrics (last login, login count, logins today) stay accurate.
        user.setLastLoginAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        user.setLoginCount(1);

        User saved = userRepository.save(user);
        otpService.clear(email); // clean up OTP entry after successful registration
        loginEventService.record(saved, "register");
        emailService.sendWelcomeEmail(saved.getEmail(), saved.getFullName()); // best-effort, never throws
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getTokenVersion());
        return new AuthResponse(token,
                new AuthResponse.UserDto(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole()));
    }

    public AuthResponse login(LoginRequest req) {
        // Normalise to match how emails are stored at registration (trimmed + lowercase),
        // so login is not case-sensitive.
        String email = req.getEmail() == null ? "" : req.getEmail().trim().toLowerCase();

        // Block password login for accounts that only linked a social provider (no password set),
        // with a clear message instead of a generic "invalid credentials".
        userRepository.findByEmail(email).ifPresent(u -> {
            if (isGoogleOnly(u))
                throw new RuntimeException("This account uses Google Sign-In. Please continue with Google.");
        });

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.getPassword()));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastLoginAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        user.setLoginCount(user.getLoginCount() + 1);
        userRepository.save(user);
        loginEventService.record(user, "password");
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getTokenVersion());
        return new AuthResponse(token,
                new AuthResponse.UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getRole()));
    }

    public AuthResponse guestLogin(String guestId) {
        // Reuse the same guest account for this device if it still exists (within 3-day window)
        if (guestId != null && !guestId.isBlank()) {
            java.util.Optional<User> existing = userRepository.findById(guestId);
            if (existing.isPresent() && "GUEST".equals(existing.get().getRole())) {
                User guest = existing.get();
                guest.setLastLoginAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
                guest.setLoginCount(guest.getLoginCount() + 1);
                userRepository.save(guest);
                loginEventService.record(guest, "guest");
                String token = jwtUtil.generateToken(guest.getEmail(), guest.getRole(), guest.getTokenVersion());
                return new AuthResponse(token,
                        new AuthResponse.UserDto(guest.getId(), guest.getFullName(), guest.getEmail(), guest.getRole()));
            }
        }

        // Guest account not found or expired — create a new one
        long ts = System.currentTimeMillis();
        String guestEmail = "guest_" + ts + "@guest.local";
        String guestName  = "Guest#" + ((ts % 9000) + 1000);

        User guest = new User();
        guest.setFullName(guestName);
        guest.setEmail(guestEmail);
        guest.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        guest.setRole("GUEST");
        guest.setAvatarColor("#64748B");
        guest.setIsActive(true);
        guest.setLastLoginAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        guest.setLoginCount(1);

        User saved = userRepository.save(guest);
        loginEventService.record(saved, "guest");
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getTokenVersion());
        return new AuthResponse(token,
                new AuthResponse.UserDto(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole()));
    }

    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setLastLogoutAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
            // Invalidate every JWT issued before this logout.
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
        });
    }

    public User getMe(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * True when the account can only sign in through a social provider (e.g. Google) and has
     * no usable local password — i.e. providers contains a social provider but not "local".
     */
    private boolean isGoogleOnly(User user) {
        java.util.List<String> providers = user.getProviders();
        if (providers == null || providers.isEmpty()) return false;
        return !providers.contains("local") && providers.contains("google");
    }

    public void resetPassword(String email, String newPassword) {
        if (!otpService.isResetVerified(email))
            throw new RuntimeException("Email not verified. Please verify OTP first.");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if ("GUEST".equals(user.getRole()))
            throw new RuntimeException("Guest accounts cannot reset password.");
        if (isGoogleOnly(user))
            throw new RuntimeException("This account uses Google Sign-In. Please continue with Google.");

        user.setPassword(passwordEncoder.encode(newPassword));
        // Invalidate every existing session after a password change.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        otpService.clearReset(email);
        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFullName()); // best-effort, never throws
    }
}
