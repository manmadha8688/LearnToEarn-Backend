package com.example.student.repository;

import com.example.student.dto.SubjectCompletionAgg;
import com.example.student.model.UserConceptProgress;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserConceptProgressRepository extends MongoRepository<UserConceptProgress, String> {

    boolean existsByUserIdAndConceptId(String userId, String conceptId);

    Optional<UserConceptProgress> findByUserIdAndConceptId(String userId, String conceptId);

    List<UserConceptProgress> findByUserId(String userId);

    List<UserConceptProgress> findByUserIdAndSubjectId(String userId, String subjectId);

    long countByUserId(String userId);

    long countByUserIdAndSubjectId(String userId, String subjectId);

    void deleteByUserIdAndConceptId(String userId, String conceptId);
    void deleteByConceptId(String conceptId);
    void deleteBySubjectId(String subjectId);
    void deleteByUserId(String userId);

    @Aggregation(pipeline = {
        "{ '$group': { '_id': '$subjectId', 'subjectTitle': { '$first': '$subjectTitle' }, 'subjectIcon': { '$first': '$subjectIcon' }, 'count': { '$sum': 1 } } }",
        "{ '$sort': { 'count': -1 } }",
        "{ '$limit': 5 }",
        "{ '$project': { 'subjectId': '$_id', 'subjectTitle': 1, 'subjectIcon': 1, 'count': 1, '_id': 0 } }"
    })
    List<SubjectCompletionAgg> findTopSubjectsByCompletion();
}
