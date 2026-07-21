package com.example.student.dto;

import com.example.student.model.ProblemQuestion;

import java.util.List;

/**
 * Lightweight list row for Code Gym track pages.
 * Omits statement body, solutions, and all test-case I/O.
 */
public record ProblemSummaryDTO(
        String id,
        String track,
        int orderIndex,
        String level,
        String category,
        String title,
        List<String> topics,
        boolean judgeable
) {
    public static ProblemSummaryDTO from(ProblemQuestion p) {
        boolean judgeable = !p.effectiveTestCases().isEmpty();
        return new ProblemSummaryDTO(
                p.getId(),
                p.getTrack(),
                p.getOrderIndex(),
                p.getLevel(),
                p.getCategory(),
                p.getTitle(),
                p.getTopics(),
                judgeable
        );
    }
}
