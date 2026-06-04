package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "feedbacks")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Feedback {

    @Id
    private String id;

    private int rating;               // 1–5 stars
    private String experience;        // free-text
    private String category;          // optional: Bug / Suggestion / Content / Other
    private String categoryNote;      // optional: detail text for the selected category
    private Boolean isUseful;         // true / false
    private String userId;            // optional — set if user is logged in

    private LocalDateTime createdAt;
}
