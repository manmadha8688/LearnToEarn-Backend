package com.example.student.dto;

import com.example.student.model.Education;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @Size(max = 100)
    private String fullName;

    @Size(max = 20)
    private String username;

    @Size(max = 300)
    private String bio;

    @Size(max = 20)
    private String avatarColor;

    @Size(max = 200)
    private String githubUrl;

    @Size(max = 200)
    private String linkedinUrl;

    @Size(max = 200)
    private String portfolioUrl;

    @Size(max = 120)
    private String location;

    // Optional public contact email (opt-in). Blank clears it. Format validated in the service.
    @Size(max = 254)
    private String publicEmail;

    private Education education;

    private Boolean publicProfile;

    // Id of the resume to feature on the public profile. Blank/empty clears it (show none).
    @Size(max = 40)
    private String featuredResumeId;
}
