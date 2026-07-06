package com.example.student.repository;

import com.example.student.model.LoginEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;

public interface LoginEventRepository extends MongoRepository<LoginEvent, String> {
    long countByCreatedAtAfter(LocalDateTime start);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByMethodAndCreatedAtBetween(String method, LocalDateTime start, LocalDateTime end);
}
