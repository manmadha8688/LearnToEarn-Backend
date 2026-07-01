package com.example.student.repository;

import com.example.student.model.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FeedbackRepository extends MongoRepository<Feedback, String> {
    Page<Feedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
