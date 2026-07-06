package com.example.student.service;

import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.Bookmark;
import com.example.student.repository.BookmarkRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class BookmarkService {

    private static final Set<String> ALLOWED_TYPES = Set.of("SUBJECT", "ROADMAP", "MISSION", "PROBLEM", "AITOOL", "GUIDE");

    private final BookmarkRepository bookmarkRepository;

    public BookmarkService(BookmarkRepository bookmarkRepository) {
        this.bookmarkRepository = bookmarkRepository;
    }

    public List<Bookmark> list(String userId) {
        return bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Bookmark add(String userId, String type, String refId, String title, String description, String icon) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(normalizedType))
            throw new IllegalArgumentException("Unsupported bookmark type");
        if (refId == null || refId.isBlank())
            throw new IllegalArgumentException("Missing reference id");

        // Idempotent: return the existing bookmark instead of creating a duplicate.
        return bookmarkRepository.findByUserIdAndTypeAndRefId(userId, normalizedType, refId)
                .orElseGet(() -> {
                    Bookmark b = Bookmark.builder()
                            .userId(userId)
                            .type(normalizedType)
                            .refId(refId)
                            .title(title)
                            .description(description)
                            .icon(icon)
                            .build();
                    return bookmarkRepository.save(b);
                });
    }

    public void removeByRef(String userId, String type, String refId) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        bookmarkRepository.findByUserIdAndTypeAndRefId(userId, normalizedType, refId)
                .ifPresent(bookmarkRepository::delete);
    }

    public void removeById(String userId, String id) {
        Bookmark b = bookmarkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bookmark not found"));
        // Owner-only: never allow deleting someone else's bookmark.
        if (!b.getUserId().equals(userId))
            throw new ResourceNotFoundException("Bookmark not found");
        bookmarkRepository.delete(b);
    }
}
