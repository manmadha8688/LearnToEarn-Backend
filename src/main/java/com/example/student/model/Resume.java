package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A student's resume. One document per user. The builder (frontend) owns the
 * section schema, so the content is stored as a flexible {@code data} map —
 * this lets us add/reorder resume sections without a backend migration.
 */
@Document(collection = "resumes")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Resume {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    /** Full resume content produced by the builder (header, summary, skills, experience, …). */
    private Map<String, Object> data;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
