package com.example.student.service;

import com.example.student.model.Feedback;
import com.example.student.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public Feedback submit(Map<String, Object> body, String userId) {
        int rating = body.containsKey("rating") ? ((Number) body.get("rating")).intValue() : 0;
        String experience    = (String) body.getOrDefault("experience", "");
        String category      = (String) body.getOrDefault("category", null);
        String categoryNote  = (String) body.getOrDefault("categoryNote", null);
        Boolean isUseful     = body.containsKey("isUseful") ? (Boolean) body.get("isUseful") : null;

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

    public List<Feedback> getAll() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc();
    }
}
