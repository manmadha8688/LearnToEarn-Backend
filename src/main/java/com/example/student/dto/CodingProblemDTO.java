package com.example.student.dto;

import com.example.student.model.CodingProblem;

import java.util.List;

/**
 * Public-facing view of a {@link CodingProblem}. Deliberately omits
 * {@code hiddenTestCases} — hidden cases must never reach the client.
 */
public record CodingProblemDTO(
        String id,
        String title,
        String difficulty,
        String category,
        String description,
        List<String> constraints,
        List<CodingProblem.Example> examples,
        List<CodingProblem.IOCase> sampleTestCases,
        List<String> supportedLanguages,
        int timeLimit,
        int memoryLimit
) {
    public static CodingProblemDTO from(CodingProblem p) {
        return new CodingProblemDTO(
                p.getId(),
                p.getTitle(),
                p.getDifficulty(),
                p.getCategory(),
                p.getDescription(),
                p.getConstraints(),
                p.getExamples(),
                p.getSampleTestCases(),
                p.getSupportedLanguages(),
                p.getTimeLimit(),
                p.getMemoryLimit()
        );
    }
}
