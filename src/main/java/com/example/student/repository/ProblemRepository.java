package com.example.student.repository;

import com.example.student.model.ProblemQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProblemRepository extends MongoRepository<ProblemQuestion, String> {
    List<ProblemQuestion> findAllByOrderByOrderIndexAsc();
    List<ProblemQuestion> findByTracksContainingOrderByOrderIndexAsc(String track);
    List<ProblemQuestion> findByIsInterviewTrueOrderByOrderIndexAsc();
}
