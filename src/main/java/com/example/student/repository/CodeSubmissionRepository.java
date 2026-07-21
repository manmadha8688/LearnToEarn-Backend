package com.example.student.repository;

import com.example.student.model.CodeSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CodeSubmissionRepository extends MongoRepository<CodeSubmission, String> {
    // Newest-first history for one student on one problem.
    List<CodeSubmission> findByUserIdAndProblemIdOrderBySubmittedAtDesc(String userId, String problemId);
}
