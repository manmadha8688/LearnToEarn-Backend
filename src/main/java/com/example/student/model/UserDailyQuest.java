package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One document per user per IST calendar day, holding that day's daily-quest state.
 * This replaces the old client-only localStorage quest tracking so quest progress
 * (especially the study-time quest) survives reloads, works across devices, and can
 * award real XP that the server controls.
 */
@Document(collection = "user_daily_quests")
@CompoundIndex(name = "user_date_unique", def = "{'userId': 1, 'questDate': 1}", unique = true)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserDailyQuest {

    @Id
    private String id;

    /** Optimistic-lock version so concurrent study pings can't clobber each other. */
    @Version
    private Long version;

    private String userId;

    /** The IST calendar day this quest state belongs to. */
    private LocalDate questDate;

    /** Accumulated real (server-measured) study seconds on the arena for the day. */
    @Builder.Default
    private int studySeconds = 0;

    /** Server timestamp of the last study ping — used to measure real elapsed time. */
    private LocalDateTime lastPingAt;

    /** True once the study-time quest reward has been granted (prevents double XP). */
    @Builder.Default
    private boolean studyQuestClaimed = false;
}
