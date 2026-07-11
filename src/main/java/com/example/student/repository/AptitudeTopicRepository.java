package com.example.student.repository;

import com.example.student.model.AptitudeTopic;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AptitudeTopicRepository extends MongoRepository<AptitudeTopic, String> {

    /**
     * All active topics — WITHOUT the heavy learnIt/crackIt blocks (list view).
     */
    @Query(value = "{ 'isActive': true }",
           fields = "{ 'learnIt': 0, 'crackIt': 0, 'videos': 0 }",
           sort = "{ 'order': 1 }")
    List<AptitudeTopic> findActiveLight();

    /**
     * Active topics in one category — WITHOUT the heavy lesson blocks (list view).
     */
    @Query(value = "{ 'category': ?0, 'isActive': true }",
           fields = "{ 'learnIt': 0, 'crackIt': 0 }",
           sort = "{ 'order': 1 }")
    List<AptitudeTopic> findCategoryLight(String category);

    /**
     * Active topics in one group — WITHOUT the heavy lesson blocks (list view).
     */
    @Query(value = "{ 'group': ?0, 'isActive': true }",
           fields = "{ 'learnIt': 0, 'crackIt': 0 }",
           sort = "{ 'order': 1 }")
    List<AptitudeTopic> findGroupLight(String group);

    /** Full topic (includes learnIt/crackIt) — single-topic view. */
    Optional<AptitudeTopic> findByTopic(String topic);

    boolean existsByTopic(String topic);
}
