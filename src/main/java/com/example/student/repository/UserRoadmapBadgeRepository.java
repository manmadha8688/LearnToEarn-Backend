package com.example.student.repository;

import com.example.student.model.UserRoadmapBadge;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoadmapBadgeRepository extends MongoRepository<UserRoadmapBadge, String> {
    Optional<UserRoadmapBadge> findByUserIdAndRoadmapId(String userId, String roadmapId);
    boolean existsByUserIdAndRoadmapId(String userId, String roadmapId);
    List<UserRoadmapBadge> findByUserId(String userId);
    void deleteByRoadmapId(String roadmapId);
    void deleteByUserId(String userId);
}
