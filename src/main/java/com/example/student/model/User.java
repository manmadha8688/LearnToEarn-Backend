package com.example.student.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Document(collection = "users")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class User implements UserDetails {

    @Id
    private String id;

    private String fullName;

    @Indexed(unique = true)
    private String email;

    // Public handle for the shareable profile URL (/u/{username}). Unique, sparse
    // (guests never get one). Index is created explicitly in DataIntegrityMigration.
    private String username;

    // Short public bio shown on the shareable profile. Optional.
    private String bio;

    // Career links shown on the public profile (all optional). Full https URLs.
    private String githubUrl;
    private String linkedinUrl;
    private String portfolioUrl;

    // Optional public contact email shown on the shareable profile (recruiter contact).
    // Deliberately SEPARATE from the private login `email` above, which is never exposed
    // publicly. Opt-in: empty/null means it is hidden from the public profile.
    private String publicEmail;

    // Personal profile details (settings page). All optional.
    private String location;
    private Education education;

    // The single resume the student chose to feature on their public profile.
    // References a Resume._id the user owns; null = show no resume. When set, that
    // resume is auto-shared so the public link always resolves.
    private String featuredResumeId;

    // Profile visibility: true → anyone with the /u/{username} link can view it,
    // false → private (public link 404s). Null (legacy accounts) is treated as public.
    @Builder.Default
    private Boolean publicProfile = true;

    // Password hash for local (email + password) auth. NULL for accounts created
    // via a social provider only (e.g. Google) — those users cannot use password login.
    @JsonIgnore
    private String password;

    // Stable Google account identifier (the "sub" claim). Sparse-unique index created
    // in DataIntegrityMigration. NULL for accounts that never linked Google.
    // @JsonIgnore: never expose in any response (defense-in-depth if the entity is
    // ever serialized directly instead of via a DTO/map).
    @JsonIgnore
    private String googleId;

    // Secret device token for GUEST session persistence. A server-generated random UUID
    // (not the guessable Mongo _id) that the guest's browser stores and presents to reuse
    // its account across reloads. Sparse-unique index created in DataIntegrityMigration.
    // @JsonIgnore: the token is only ever returned once, via AuthResponse.guestToken — it
    // must never leak through direct entity serialization.
    @JsonIgnore
    private String guestDeviceToken;

    // Auth methods linked to this single account: "local", "google" (future: "github", "microsoft").
    // One email = one account; providers accumulate here as the user links more methods.
    @Builder.Default
    private List<String> providers = new ArrayList<>();

    @Builder.Default
    private String role = "STUDENT";

    @Builder.Default
    private String avatarColor = "#4F46E5";

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private long xp = 0L;

    @Builder.Default
    private int level = 1;

    @Builder.Default
    private String rank = "E";

    @CreatedDate
    private LocalDateTime createdAt;

    // ── Activity tracking ─────────────────────────────────
    private LocalDateTime lastLoginAt;
    private LocalDateTime lastLogoutAt;

    @Builder.Default
    private int loginCount = 0;

    // Bumped on logout and password reset. Embedded in the JWT at issue time and
    // re-checked on every request, so old tokens stop working immediately (revocation)
    // instead of staying valid until their 24h expiry.
    // @JsonIgnore: internal security counter — must never reach the client.
    @JsonIgnore
    @Builder.Default
    private long tokenVersion = 0L;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    // NOTE: getUsername() is the Spring Security login identifier (email), so it shadows
    // the Lombok getter for the `username` field. Read the public profile handle via this.
    public String getPublicUsername() { return username; }

    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return !Boolean.FALSE.equals(isActive); }
}
