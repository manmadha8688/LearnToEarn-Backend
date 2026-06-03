package com.example.student.repository;

import com.example.student.model.Subject;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SubjectRepository extends MongoRepository<Subject, String> {
    List<Subject> findByTitleContainingIgnoreCase(String title);
}
