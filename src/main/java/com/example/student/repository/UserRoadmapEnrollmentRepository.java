package com.example.student.repository;

import com.example.student.model.UserRoadmapEnrollment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserRoadmapEnrollmentRepository extends MongoRepository<UserRoadmapEnrollment, String> {
    List<UserRoadmapEnrollment> findByUserId(String userId);
    boolean existsByUserIdAndRoadmapId(String userId, String roadmapId);
    java.util.Optional<UserRoadmapEnrollment> findByUserIdAndRoadmapId(String userId, String roadmapId);
    void deleteByRoadmapId(String roadmapId);
}
