package com.example.student.repository;

import com.example.student.model.MissionSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MissionSubmissionRepository extends MongoRepository<MissionSubmission, String> {
    Optional<MissionSubmission> findByUserIdAndMissionId(String userId, String missionId);
    List<MissionSubmission> findByUserId(String userId);
    Optional<MissionSubmission> findFirstByRepoKeyIgnoreCase(String repoKey);
    void deleteByUserId(String userId);
    void deleteByMissionId(String missionId);
}
