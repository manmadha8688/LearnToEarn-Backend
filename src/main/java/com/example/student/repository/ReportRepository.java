package com.example.student.repository;

import com.example.student.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReportRepository extends MongoRepository<Report, String> {
    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(String status);
}
