package com.example.student.repository;

import com.example.student.model.LoginEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginEventRepository extends MongoRepository<LoginEvent, String> {
    long countByCreatedAtAfter(LocalDateTime start);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByMethodAndCreatedAtBetween(String method, LocalDateTime start, LocalDateTime end);

    // Batch read of a whole window for in-memory day bucketing (admin login trend).
    List<LoginEvent> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
