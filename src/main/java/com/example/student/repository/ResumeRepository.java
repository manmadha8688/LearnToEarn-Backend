package com.example.student.repository;

import com.example.student.model.Resume;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends MongoRepository<Resume, String> {

    List<Resume> findByUserId(String userId, Sort sort);

    long countByUserId(String userId);

    Optional<Resume> findByIdAndUserId(String id, String userId);

    Optional<Resume> findByShareSlug(String shareSlug);

    boolean existsByShareSlug(String shareSlug);
}
