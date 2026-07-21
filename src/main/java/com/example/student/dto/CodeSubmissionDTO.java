package com.example.student.dto;

import com.example.student.model.CodeSubmission;

import java.time.Instant;

/**
 * A student's own submission, returned by the submissions-history endpoint.
 * Only ever built for the authenticated owner, so returning {@code code} is safe
 * (the UI shows it when a submission row is clicked).
 */
public record CodeSubmissionDTO(
        String id,
        String language,
        String code,
        String verdict,
        int passedCases,
        int totalCases,
        long runtime,
        Instant submittedAt
) {
    public static CodeSubmissionDTO from(CodeSubmission s) {
        return new CodeSubmissionDTO(
                s.getId(),
                s.getLanguage(),
                s.getCode(),
                s.getVerdict(),
                s.getPassedCases(),
                s.getTotalCases(),
                s.getRuntime(),
                s.getSubmittedAt()
        );
    }
}
