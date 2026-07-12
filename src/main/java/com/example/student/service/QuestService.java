package com.example.student.service;

import com.example.student.model.UserDailyQuest;
import com.example.student.repository.QuizAttemptRepository;
import com.example.student.repository.UserDailyQuestRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily quests, tracked server-side so progress survives reloads / device changes and
 * rewards are controlled by the server (the old system was localStorage-only and its
 * "+XP" was purely cosmetic).
 *
 * Two quests per IST day:
 *   q1 "Complete 1 concept" — done when a CONCEPT quiz was passed today. Its reward is the
 *      +50 daily bonus already granted by {@link ProgressService#completeConcept} (we never
 *      re-award it here — we only surface it).
 *   q2 "Study for 30 min" — done when 30 minutes of REAL time on the arena accumulates via
 *      study pings. Awards {@link #STUDY_QUEST_XP} exactly once, the moment the target is hit.
 */
@Service
public class QuestService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public static final int STUDY_TARGET_SECONDS = 1800; // 30 minutes
    public static final int STUDY_QUEST_XP = 40;
    public static final int CONCEPT_QUEST_XP = 50;       // == the daily bonus already granted

    // Cap the elapsed time credited per ping. The client pings roughly once a minute; capping
    // at 90s means a tab left asleep for hours can't dump a huge chunk, and rapid/replayed
    // pings can't fast-forward the timer (elapsed is measured from the server's lastPingAt).
    private static final int MAX_PING_GAP_SECONDS = 90;

    private final UserDailyQuestRepository questRepo;
    private final QuizAttemptRepository attemptRepo;
    private final ProgressService progressService;

    public QuestService(UserDailyQuestRepository questRepo,
                        QuizAttemptRepository attemptRepo,
                        ProgressService progressService) {
        this.questRepo = questRepo;
        this.attemptRepo = attemptRepo;
        this.progressService = progressService;
    }

    public Map<String, Object> getQuests(String userId) {
        return toState(loadToday(userId), userId);
    }

    /**
     * Credits real elapsed time (server-measured) toward the study quest and, the first time
     * the 30-minute target is reached, awards the study-quest XP exactly once.
     *
     * Deliberately NOT wrapped in a multi-document @Transactional: the client fires pings
     * concurrently (React StrictMode double-mounts in dev, multiple tabs in prod), and a
     * transaction turns the unique-index insert race into a hard {@code WriteConflict}. Instead
     * we rely on optimistic locking ({@code @Version}) + the {@code user_date_unique} index and
     * retry a handful of times, re-reading fresh state each attempt. XP is only awarded on the
     * attempt whose save actually succeeds and that flipped {@code studyQuestClaimed}, so no
     * double-award is possible.
     */
    public Map<String, Object> recordStudyPing(String userId) {
        for (int attempt = 0; attempt < 4; attempt++) {
            UserDailyQuest q = loadToday(userId);

            // Already done for the day — nothing to persist.
            if (q.isStudyQuestClaimed()) {
                return toState(q, userId);
            }

            LocalDateTime now = LocalDateTime.now(IST);
            if (q.getLastPingAt() != null) {
                long delta = Duration.between(q.getLastPingAt(), now).getSeconds();
                if (delta > 0) {
                    delta = Math.min(delta, MAX_PING_GAP_SECONDS);
                    int updated = (int) Math.min(STUDY_TARGET_SECONDS, q.getStudySeconds() + delta);
                    q.setStudySeconds(updated);
                }
            }
            q.setLastPingAt(now);

            boolean justCompleted = q.getStudySeconds() >= STUDY_TARGET_SECONDS;
            if (justCompleted) {
                q.setStudyQuestClaimed(true);
            }

            try {
                questRepo.save(q);
                // Award only after the winning save committed — guarantees exactly-once.
                if (justCompleted) {
                    progressService.awardXp(userId, STUDY_QUEST_XP);
                }
                return toState(q, userId);
            } catch (DuplicateKeyException | OptimisticLockingFailureException e) {
                // A concurrent ping created/updated today's doc first — re-read and retry.
            } catch (DataAccessException e) {
                // Transient Mongo transient/write-conflict labels can surface here too; retry.
                if (attempt == 3) break;
            }
        }
        // Give up persisting this tick; return the latest known state so the UI stays correct.
        return getQuests(userId);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Today's quest doc (a fresh transient one if the user hasn't started the day yet). */
    private UserDailyQuest loadToday(String userId) {
        LocalDate today = LocalDate.now(IST);
        return questRepo.findByUserIdAndQuestDate(userId, today)
                .orElseGet(() -> UserDailyQuest.builder()
                        .userId(userId).questDate(today).studySeconds(0).studyQuestClaimed(false)
                        .build());
    }

    private boolean conceptDoneToday(String userId) {
        return attemptRepo.existsByUserIdAndTypeAndPassedTrueAndTakenAtAfter(
                userId, "CONCEPT", LocalDate.now(IST).atStartOfDay());
    }

    private Map<String, Object> toState(UserDailyQuest q, String userId) {
        boolean conceptDone = conceptDoneToday(userId);
        int studySeconds = Math.min(q.getStudySeconds(), STUDY_TARGET_SECONDS);
        boolean studyDone = q.isStudyQuestClaimed() || studySeconds >= STUDY_TARGET_SECONDS;

        List<Map<String, Object>> quests = new ArrayList<>();
        Map<String, Object> q1 = new LinkedHashMap<>();
        q1.put("id", "q1");
        q1.put("label", "Complete 1 concept");
        q1.put("xp", CONCEPT_QUEST_XP);
        q1.put("done", conceptDone);
        quests.add(q1);

        Map<String, Object> q2 = new LinkedHashMap<>();
        q2.put("id", "q2");
        q2.put("label", "Study for 30 min");
        q2.put("xp", STUDY_QUEST_XP);
        q2.put("done", studyDone);
        q2.put("progressSeconds", studySeconds);
        q2.put("targetSeconds", STUDY_TARGET_SECONDS);
        quests.add(q2);

        int doneCount = (conceptDone ? 1 : 0) + (studyDone ? 1 : 0);
        int earnedXp = (conceptDone ? CONCEPT_QUEST_XP : 0) + (studyDone ? STUDY_QUEST_XP : 0);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("date", LocalDate.now(IST).toString());
        res.put("conceptDone", conceptDone);
        res.put("studySeconds", studySeconds);
        res.put("studyTargetSeconds", STUDY_TARGET_SECONDS);
        res.put("studyDone", studyDone);
        res.put("doneCount", doneCount);
        res.put("totalCount", 2);
        res.put("earnedXp", earnedXp);
        res.put("quests", quests);
        return res;
    }
}
