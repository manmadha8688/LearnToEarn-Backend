package com.example.student.repository;

import com.example.student.model.UserSubjectBadge;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserSubjectBadgeRepository extends MongoRepository<UserSubjectBadge, String> {
    Optional<UserSubjectBadge> findByUserIdAndSubjectId(String userId, String subjectId);
    boolean existsByUserIdAndSubjectId(String userId, String subjectId);
    List<UserSubjectBadge> findByUserId(String userId);
    void deleteBySubjectId(String subjectId);
    void deleteByUserId(String userId);
}
