package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
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

    /** Normalized {@code owner/repo} (lowercase) for global duplicate detection. */
    private String repoKey;

    /**
     * XP already granted for this submission's repo / deploy link, keyed by mission rank
     * at award time. Stored so we award each link only once and can reverse the exact
     * amount if the link is later removed (no XP farming via add/remove).
     */
    @Builder.Default
    private int repoXp = 0;

    @Builder.Default
    private int deployXp = 0;

    /**
     * XP delta from the current save (+ awarded, − reversed). Not persisted — carried
     * back in the save response so the UI can show a "+N XP" toast.
     */
    @Transient
    @Builder.Default
    private int xpEarned = 0;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
