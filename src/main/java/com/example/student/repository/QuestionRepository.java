package com.example.student.repository;

import com.example.student.model.Question;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface QuestionRepository extends MongoRepository<Question, String> {
    List<Question> findByConceptId(String conceptId);
    List<Question> findByConceptIdIn(List<String> conceptIds);
    List<Question> findBySubjectId(String subjectId);
    List<Question> findBySubjectIdIn(List<String> subjectIds);
    long countByConceptId(String conceptId);
    void deleteByConceptId(String conceptId);
    void deleteBySubjectId(String subjectId);
}
