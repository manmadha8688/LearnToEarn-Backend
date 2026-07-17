package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A student's resume. A user may keep up to a few resumes (e.g. "Frontend",
 * "Data Analyst"). The builder (frontend) owns the section schema, so the content
 * is stored as a flexible {@code data} map — this lets us add/reorder resume
 * sections without a backend migration.
 *
 * <p>When shared, the resume gets an unguessable {@code shareSlug}; anyone with the
 * link (/r/{slug}) can view the live web version and download the PDF. Sharing is
 * opt-in ({@code shared}) and revocable (toggle off → the link 404s).
 *
 * <p>Note: the flag is named {@code shared} (not {@code isPublic}) because Lombok +
 * Spring Data MongoDB mishandle boolean fields that start with {@code is}.
 */
@Document(collection = "resumes")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Resume {

    @Id
    private String id;

    /** Owner. Not unique — a user may have multiple resumes. */
    private String userId;

    /** Short label the user gives this resume, e.g. "Full Stack". */
    private String title;

    /** Full resume content produced by the builder (header, summary, skills, projects, …). */
    private Map<String, Object> data;

    /** Unguessable public share token. Null until the user turns sharing on. */
    @Indexed(unique = true, sparse = true)
    private String shareSlug;

    /** Whether the share link is currently live. */
    @Field("shared")
    @Builder.Default
    private boolean shared = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
