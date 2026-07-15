package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A hunter's submitted work for a mission — the GitHub repo and the live/deployed URL.
 * One document per (userId, missionId); saving again upserts the same record.
 */
@Document(collection = "mission_submissions")
@CompoundIndex(name = "user_mission_uk", def = "{'userId': 1, 'missionId': 1}", unique = true)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MissionSubmission {

    @Id
    private String id;

    private String userId;
    private String missionId;

    private String repoUrl;
    private String deployUrl;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
