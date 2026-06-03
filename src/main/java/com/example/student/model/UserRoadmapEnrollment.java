package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "user_roadmap_enrollments")
@CompoundIndex(def = "{'userId': 1, 'roadmapId': 1}", unique = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserRoadmapEnrollment {

    @Id
    private String id;

    private String userId;
    private String roadmapId;
    private LocalDateTime enrolledAt;
    private boolean paused;
}
