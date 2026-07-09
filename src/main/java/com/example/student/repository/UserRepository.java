package com.example.student.repository;

import com.example.student.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByGuestDeviceToken(String guestDeviceToken);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    long countByRole(String role);
    List<User> findTop5ByOrderByCreatedAtDesc();
    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);

    Page<User> findByRole(String role, Pageable pageable);
    Page<User> findByRoleNot(String role, Pageable pageable);
    Page<User> findByRoleAndFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String role, String name, String email, Pageable pageable);
    Page<User> findByRoleNotAndFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String role, String name, String email, Pageable pageable);

    // Admin dashboard metrics
    long countByCreatedAtAfter(LocalDateTime start);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByProvidersContaining(String provider);

    // Guest management
    List<User> findByRole(String role);
    List<User> findByRoleAndCreatedAtBefore(String role, LocalDateTime cutoff);
}
