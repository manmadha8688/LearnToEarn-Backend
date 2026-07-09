package com.example.student.model;

import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "reports")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Report {

    @Id
    private String id;

    private String userId;
    private String userEmail;
    private String userName;

    // Page context
    @Size(max = 500)
    private String pageUrl;
    @Size(max = 300)
    private String pageTitle;   // e.g. "Quiz — Variables and Input", "Mission — Bank Account Manager"

    // Report details
    @Size(max = 40)
    private String type;        // NO_QUESTIONS | WRONG_CONTENT | BROKEN | MISSING_CONTENT | UI_ISSUE | WRONG_ANSWER | SUGGESTION | OTHER
    @Size(max = 5000, message = "Description is too long")
    private String description;
    @Size(max = 2000)
    private String expectedBehavior;  // optional — what the user expected instead
    @Size(max = 4000)
    private String context;           // JSON — page params, viewport, entity ids

    // Admin
    @Indexed
    @Builder.Default
    private String status = "OPEN";  // OPEN | IN_PROGRESS | RESOLVED — filtered/counted by status

    private String adminNote;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
