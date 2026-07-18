package com.example.student.repository;

import com.example.student.model.Bookmark;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends MongoRepository<Bookmark, String> {
    List<Bookmark> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<Bookmark> findByUserIdAndTypeAndRefId(String userId, String type, String refId);
    boolean existsByUserIdAndTypeAndRefId(String userId, String type, String refId);
    void deleteByUserId(String userId);
    void deleteByTypeAndRefId(String type, String refId);
}
