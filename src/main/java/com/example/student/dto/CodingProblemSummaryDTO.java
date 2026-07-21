package com.example.student.dto;

import com.example.student.model.CodingProblem;

/** Lightweight list row for the problems index (no test cases / description). */
public record CodingProblemSummaryDTO(
        String id,
        String title,
        String difficulty,
        String category
) {
    public static CodingProblemSummaryDTO from(CodingProblem p) {
        return new CodingProblemSummaryDTO(p.getId(), p.getTitle(), p.getDifficulty(), p.getCategory());
    }
}
