package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * One row per successful sign-in. Unlike the per-user {@code lastLoginAt}/{@code loginCount}
 * on {@link User}, this is an append-only event log so the admin dashboard can report
 * accurate "logins today" and multi-day trends (a single user can log in many times a day).
 *
 * <p>{@code method} is one of: "password", "google", "guest", "register".
 */
@Document(collection = "login_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginEvent {

    @Id
    private String id;

    private String userId;
    private String email;
    private String role;

    /** How the session was started: password | google | guest | register. */
    private String method;

    @Indexed
    private LocalDateTime createdAt;
}
