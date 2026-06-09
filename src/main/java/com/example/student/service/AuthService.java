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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        User user = new User();
        user.setFullName(req.getFullName());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("STUDENT");
        user.setCollegeName(req.getCollegeName());
        user.setAvatarColor("#4F46E5");
        user.setIsActive(true);

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole());
        return new AuthResponse(token,
                new AuthResponse.UserDto(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole()));
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastLoginAt(java.time.LocalDateTime.now());
        user.setLoginCount(user.getLoginCount() + 1);
        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return new AuthResponse(token,
                new AuthResponse.UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getRole()));
    }

    public AuthResponse guestLogin(String guestId) {
        // Reuse the same guest account for this device if it still exists (within 3-day window)
        if (guestId != null && !guestId.isBlank()) {
            java.util.Optional<User> existing = userRepository.findById(guestId);
            if (existing.isPresent() && "GUEST".equals(existing.get().getRole())) {
                User guest = existing.get();
                guest.setLastLoginAt(java.time.LocalDateTime.now());
                guest.setLoginCount(guest.getLoginCount() + 1);
                userRepository.save(guest);
                String token = jwtUtil.generateToken(guest.getEmail(), guest.getRole());
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
        guest.setLastLoginAt(java.time.LocalDateTime.now());
        guest.setLoginCount(1);

        User saved = userRepository.save(guest);
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole());
        return new AuthResponse(token,
                new AuthResponse.UserDto(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole()));
    }

    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setLastLogoutAt(java.time.LocalDateTime.now());
            userRepository.save(user);
        });
    }

    public User getMe(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
