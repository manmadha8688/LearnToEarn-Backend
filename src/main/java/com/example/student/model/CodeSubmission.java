package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One run of the Submit action against a coding problem's hidden test cases,
 * saved so a student can see their own submission history and verdicts.
 *
 * NOTE: auto-index-creation is disabled in this project, so this @CompoundIndex
 * is inert until added to DataIntegrityMigration.ensureIndexes(). The query
 * findByUserIdAndProblemIdOrderBySubmittedAtDesc still works without it (small
 * per-user result sets); the index is a scale hint for later.
 */
@Document(collection = "code_submissions")
@CompoundIndex(name = "sub_user_problem_time", def = "{'userId': 1, 'problemId': 1, 'submittedAt': -1}")
@Getter @Setter @NoArgsConstructor
public class CodeSubmission {

    @Id
    private String id;

    private String userId;
    private String problemId;
    private String language;
    private String code;

    private String verdict;     // Accepted | Wrong Answer | Time Limit Exceeded | Runtime Error | Compile Error
    private int passedCases;
    private int totalCases;
    private long runtime;        // total ms across the judged cases

    private Instant submittedAt;
}
