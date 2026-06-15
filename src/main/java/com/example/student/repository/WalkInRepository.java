package com.example.student.repository;

import com.example.student.model.WalkIn;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WalkInRepository extends MongoRepository<WalkIn, String> {
    List<WalkIn> findAllByOrderByCreatedAtDesc();
    List<WalkIn> findByStatusOrderByWalkInDateAsc(String status);
    List<WalkIn> findByCityIgnoreCaseAndStatus(String city, String status);
    List<WalkIn> findByPostedByIdOrderByCreatedAtDesc(String postedById);
    long countByStatus(String status);
}
