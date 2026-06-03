package com.example.student.repository;

import com.example.student.model.RoadmapSubject;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RoadmapSubjectRepository extends MongoRepository<RoadmapSubject, String> {
    List<RoadmapSubject> findByRoadmapIdOrderByOrderIndex(String roadmapId);
    Optional<RoadmapSubject> findByRoadmapIdAndSubjectId(String roadmapId, String subjectId);
    void deleteByRoadmapIdAndSubjectId(String roadmapId, String subjectId);
    void deleteBySubjectId(String subjectId);
    void deleteByRoadmapId(String roadmapId);
}
