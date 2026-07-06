package com.example.student.repository;

import com.example.student.model.WalkIn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WalkInRepository extends MongoRepository<WalkIn, String> {
    Page<WalkIn> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<WalkIn> findByStatusOrderByWalkInDateAsc(String status);
    List<WalkIn> findByCityIgnoreCaseAndStatus(String city, String status);
    List<WalkIn> findByPostedByIdOrderByCreatedAtDesc(String postedById);
    long countByStatus(String status);
}
