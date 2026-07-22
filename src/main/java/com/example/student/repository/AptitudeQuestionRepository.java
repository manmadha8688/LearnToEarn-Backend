package com.example.student.repository;

import com.example.student.model.AptitudeQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AptitudeQuestionRepository extends MongoRepository<AptitudeQuestion, String> {

    /** All active questions for a topic, in display order. */
    List<AptitudeQuestion> findByTopicAndIsActiveTrueOrderByOrderAsc(String topic);

    /** All active questions in a category — used for mock exam sampling. */
    List<AptitudeQuestion> findByCategoryAndIsActiveTrue(String category);

    /** All questions for a topic (incl. inactive) — admin panel. */
    List<AptitudeQuestion> findByTopicOrderByOrderAsc(String topic);
}
