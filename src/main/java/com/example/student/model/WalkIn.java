package com.example.student.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
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

    @Size(max = 200, message = "Company name is too long")
    private String companyName;
    @Size(max = 200, message = "Role is too long")
    private String role;
    @Size(max = 50, message = "Too many skills")
    private List<@Size(max = 60) String> skills;

    @Size(max = 40)
    private String walkInDate;   // ISO date string "2026-06-20"
    @Size(max = 80)
    private String walkInTime;   // e.g. "10 AM – 2 PM"
    @Size(max = 500)
    private String location;     // venue / address
    @Indexed
    @Size(max = 120)
    private String city;         // filtered by city on the jobs board

    @Size(max = 300)
    private String contactInfo;  // email or phone (optional)
    @Size(max = 5000, message = "Description is too long")
    private String description;  // extra details (optional)

    @Size(max = 120)
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
