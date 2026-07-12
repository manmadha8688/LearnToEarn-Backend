package com.example.student.repository;

import com.example.student.model.Certificate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends MongoRepository<Certificate, String> {
    List<Certificate> findByUserIdOrderByIssuedAtDesc(String userId);
    Optional<Certificate> findByUserIdAndTypeAndRefId(String userId, String type, String refId);
    Optional<Certificate> findByCode(String code);
    boolean existsByCode(String code);
    void deleteByUserId(String userId);
    void deleteByTypeAndRefId(String type, String refId);
}
