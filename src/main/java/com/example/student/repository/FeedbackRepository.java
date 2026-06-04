package com.example.student.repository;

import com.example.student.model.Feedback;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FeedbackRepository extends MongoRepository<Feedback, String> {
    List<Feedback> findAllByOrderByCreatedAtDesc();
}
