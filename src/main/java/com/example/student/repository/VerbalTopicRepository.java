package com.example.student.repository;

import com.example.student.model.VerbalTopic;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Verbal / English topics live in their own collection ({@code verbal_topics})
 * with a purpose-built lesson model. Queries mirror {@link AptitudeTopicRepository}
 * so the shared navigation behaves identically.
 */
public interface VerbalTopicRepository extends MongoRepository<VerbalTopic, String> {

    /** All active topics — WITHOUT the heavy lesson block (list view). */
    @Query(value = "{ 'isActive': true }",
           fields = "{ 'lesson': 0, 'videos': 0 }",
           sort = "{ 'order': 1 }")
    List<VerbalTopic> findActiveLight();

    /** Active topics in one group — WITHOUT the heavy lesson block. */
    @Query(value = "{ 'group': ?0, 'isActive': true }",
           fields = "{ 'lesson': 0 }",
           sort = "{ 'order': 1 }")
    List<VerbalTopic> findGroupLight(String group);

    /** Full topic (includes lesson) — single-topic view. */
    Optional<VerbalTopic> findByTopic(String topic);

    boolean existsByTopic(String topic);
}
