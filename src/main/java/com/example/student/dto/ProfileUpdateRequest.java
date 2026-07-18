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

    @Size(max = 20)
    private String mobile;

    // Optional public contact email when useLoginEmailForContact is false. Blank clears it.
    @Size(max = 254)
    private String publicEmail;

    /** When true, login email is the resume / public-profile contact (user opt-in). */
    private Boolean useLoginEmailForContact;

    private Education education;

    private Boolean publicProfile;

    // Id of the resume to feature on the public profile. Blank/empty clears it (show none).
    @Size(max = 40)
    private String featuredResumeId;

    /** When true, skip automated link reachability checks (user confirmed manually). */
    private Boolean skipLinkVerification;
}
