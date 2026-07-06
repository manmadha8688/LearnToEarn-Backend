package com.example.student.service;

import com.example.student.model.LoginEvent;
import com.example.student.model.User;
import com.example.student.repository.LoginEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Records an append-only login event on every successful sign-in. Best-effort:
 * a failure here must never block the actual login, so all writes are wrapped
 * and swallowed with a warning.
 */
@Service
public class LoginEventService {

    private static final Logger log = LoggerFactory.getLogger(LoginEventService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final LoginEventRepository loginEventRepository;

    public LoginEventService(LoginEventRepository loginEventRepository) {
        this.loginEventRepository = loginEventRepository;
    }

    /** method: password | google | guest | register */
    public void record(User user, String method) {
        try {
            loginEventRepository.save(LoginEvent.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .method(method)
                    .createdAt(LocalDateTime.now(IST))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record login event ({}) for {}: {}", method,
                    user.getEmail(), e.getMessage());
        }
    }
}
