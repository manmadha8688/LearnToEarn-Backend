package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A verifiable certificate issued to a student when they clear a subject gate
 * (Subject Mastery) or complete a career path (Career Path Completion). Field values
 * are snapshots taken at issue time so a certificate stays accurate even if the subject
 * or the user's profile later changes.
 *
 * One certificate per (userId, type, refId) — re-passing with a higher score updates the
 * existing certificate rather than creating a duplicate.
 */
@Document(collection = "certificates")
@CompoundIndex(name = "user_type_ref_unique", def = "{'userId': 1, 'type': 1, 'refId': 1}", unique = true)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Certificate {

    @Id
    private String id;

    /** Public, human-shareable, verifiable code (e.g. LFE-4F9C2A7B). Unique. */
    @Indexed(unique = true)
    private String code;

    private String userId;

    /** Recipient's full name, snapshotted at issue time. */
    private String recipientName;

    /** "SUBJECT" or "ROADMAP". */
    private String type;

    /** subjectId or roadmapId. */
    private String refId;

    /** Human label of what was certified, e.g. "Java Programming" or "Full-Stack Developer". */
    private String credentialTitle;

    /** Category label shown on the certificate, e.g. "Subject Mastery" / "Career Path Completion". */
    private String credentialKind;

    /** Badge tier for roadmaps (INTERVIEW_READY / JOB_READY); SUBJECT_MASTERED for subjects. */
    private String badge;

    private int score;
    private int total;
    private int scorePercent;

    /** Snapshot fields for rendering. */
    private String icon;
    private String color;

    /** Hunter rank at the moment the certificate was issued. */
    private String rankAtIssue;

    private LocalDateTime issuedAt;
    private LocalDateTime updatedAt;
}
