package com.example.student.repository;

import com.example.student.model.QuizAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends MongoRepository<QuizAttempt, String> {
    List<QuizAttempt> findByUserIdAndTypeAndRefId(String userId, String type, String refId);
    List<QuizAttempt> findByUserIdAndTypeAndPassedTrue(String userId, String type);
    long countByUserIdAndTypeAndPassedTrue(String userId, String type);
    Optional<QuizAttempt> findTopByUserIdAndTypeAndRefIdOrderByTakenAtDesc(String userId, String type, String refId);
    boolean existsByUserIdAndTypeAndRefIdAndPassedTrue(String userId, String type, String refId);
    // Used by ProgressService to detect first concept of the day (excludes current concept)
    boolean existsByUserIdAndTypeAndPassedTrueAndTakenAtAfterAndRefIdNot(
            String userId, String type, LocalDateTime takenAtAfter, String refId);
    // Used by ProgressSummary to show if ANY concept was completed today
    boolean existsByUserIdAndTypeAndPassedTrueAndTakenAtAfter(
            String userId, String type, LocalDateTime takenAtAfter);
    long countByUserIdAndTypeAndRefId(String userId, String type, String refId);
    void deleteByTypeAndRefId(String type, String refId);
    void deleteByTypeAndRefIdIn(String type, java.util.List<String> refIds);
    void deleteByUserId(String userId);
}
