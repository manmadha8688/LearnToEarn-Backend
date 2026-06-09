package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
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
    private String pageUrl;
    private String pageTitle;   // e.g. "Quiz — Variables and Input", "Mission — Bank Account Manager"

    // Report details
    private String type;        // NO_QUESTIONS | WRONG_CONTENT | BROKEN | MISSING_CONTENT | OTHER
    private String description;

    // Admin
    @Builder.Default
    private String status = "OPEN";  // OPEN | IN_PROGRESS | RESOLVED

    private String adminNote;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
