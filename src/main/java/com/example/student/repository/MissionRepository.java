package com.example.student.repository;

import com.example.student.model.Mission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MissionRepository extends MongoRepository<Mission, String> {
    List<Mission> findByPublishedTrueOrderByOrderIndexAsc();
    List<Mission> findAllByOrderByOrderIndexAsc();
}
