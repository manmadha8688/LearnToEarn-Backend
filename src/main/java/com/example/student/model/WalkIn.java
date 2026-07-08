package com.example.student.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "walkins")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalkIn {

    @Id
    private String id;

    private String companyName;
    private String role;
    private List<String> skills;

    private String walkInDate;   // ISO date string "2026-06-20"
    private String walkInTime;   // e.g. "10 AM – 2 PM"
    private String location;     // venue / address
    @Indexed
    private String city;         // filtered by city on the jobs board

    private String contactInfo;  // email or phone (optional)
    private String description;  // extra details (optional)

    private String postedBy;     // user fullName (shown publicly as "by <name>")

    // Internal owner id — never sent to clients (would leak a user's Mongo id publicly).
    // Ownership is surfaced to the UI via the transient `mine` flag instead.
    @JsonIgnore
    private String postedById;   // userId

    private LocalDateTime createdAt;
    @Indexed
    private String status;       // ACTIVE | EXPIRED — every list query filters on this

    // Per-request, not persisted: true when the requesting user owns this post.
    // Lets the client show the "delete" action without exposing postedById.
    @Transient
    @Builder.Default
    private boolean mine = false;
}
