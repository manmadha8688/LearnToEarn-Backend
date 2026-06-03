package com.example.student.repository;

import com.example.student.model.Roadmap;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RoadmapRepository extends MongoRepository<Roadmap, String> {
    List<Roadmap> findByIsPublishedTrue();
}
