package com.example.student.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student's single, top-level education entry, embedded in the User document.
 * Freshers typically have one degree, so this is a flat object rather than a list.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Education {
    private String degree;         // e.g. B.Tech, B.Sc, MCA
    private String fieldOfStudy;   // branch / specialization, e.g. Computer Science & Engineering
    private String institution;    // college / university name
    private String years;          // e.g. 2021 - 2025 (start – graduation)
    private String graduationYear; // legacy pass-out year (deprecated)
    private String cgpa;           // CGPA or percentage, e.g. 8.2/10 or 82%
}
