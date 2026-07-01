package com.example.student.service;

import com.example.student.model.Feedback;
import com.example.student.repository.FeedbackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public Feedback submit(Map<String, Object> body, String userId) {
        // Type-safe extraction: tolerate malformed client payloads without throwing ClassCastException.
        Object ratingObj = body.get("rating");
        int rating = ratingObj instanceof Number ? ((Number) ratingObj).intValue() : 0;
        String experience    = body.get("experience")   instanceof String s ? s : "";
        String category      = body.get("category")     instanceof String s ? s : null;
        String categoryNote  = body.get("categoryNote") instanceof String s ? s : null;
        Object usefulObj = body.get("isUseful");
        Boolean isUseful = usefulObj instanceof Boolean b ? b : null;

        Feedback fb = Feedback.builder()
                .rating(rating)
                .experience(experience)
                .category(category)
                .categoryNote(categoryNote)
                .isUseful(isUseful)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build();

        return feedbackRepository.save(fb);
    }

    public Page<Feedback> getPaged(int page, int size) {
        return feedbackRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }
}
