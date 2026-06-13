package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
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
    private String city;

    private String contactInfo;  // email or phone (optional)
    private String description;  // extra details (optional)

    private String postedBy;     // user fullName
    private String postedById;   // userId

    private LocalDateTime createdAt;
    private String status;       // ACTIVE | EXPIRED
}
