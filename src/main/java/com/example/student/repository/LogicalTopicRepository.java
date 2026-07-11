package com.example.student.repository;

import com.example.student.model.LogicalTopic;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Logical-reasoning topics live in their own collection ({@code logical_topics})
 * with a purpose-built lesson model. Queries mirror {@link AptitudeTopicRepository}
 * so the shared navigation behaves identically.
 */
public interface LogicalTopicRepository extends MongoRepository<LogicalTopic, String> {

    /** All active topics — WITHOUT the heavy lesson block (list view). */
    @Query(value = "{ 'isActive': true }",
           fields = "{ 'lesson': 0, 'videos': 0 }",
           sort = "{ 'order': 1 }")
    List<LogicalTopic> findActiveLight();

    /** Active topics in one group — WITHOUT the heavy lesson block. */
    @Query(value = "{ 'group': ?0, 'isActive': true }",
           fields = "{ 'lesson': 0 }",
           sort = "{ 'order': 1 }")
    List<LogicalTopic> findGroupLight(String group);

    /** Admin list of topics in a group — includes INACTIVE, without the lesson block. */
    @Query(value = "{ 'group': ?0 }",
           fields = "{ 'lesson': 0 }",
           sort = "{ 'order': 1 }")
    List<LogicalTopic> findGroupAdmin(String group);

    /** Full topic (includes lesson) — single-topic view. */
    Optional<LogicalTopic> findByTopic(String topic);

    boolean existsByTopic(String topic);
}
