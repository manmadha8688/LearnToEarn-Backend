package com.example.student.repository;

import com.example.student.model.Concept;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConceptRepository extends MongoRepository<Concept, String> {
    List<Concept> findBySubjectIdOrderByOrderIndex(String subjectId);
    List<Concept> findByTitleContainingIgnoreCase(String title);
    long countBySubjectId(String subjectId);
    Optional<Concept> findBySubjectIdAndOrderIndex(String subjectId, int orderIndex);
    void deleteBySubjectId(String subjectId);
}
