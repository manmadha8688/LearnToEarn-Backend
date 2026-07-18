package com.example.student.service;

import com.example.student.model.User;

/** Resolves the contact email shown on resumes and the public profile (never implicit login fallback). */
public final class ProfileContactEmail {

    private ProfileContactEmail() {}

    public static String resolve(User user) {
        if (user == null) return "";
        if (Boolean.TRUE.equals(user.getUseLoginEmailForContact())) {
            return user.getEmail() != null ? user.getEmail().trim() : "";
        }
        return user.getPublicEmail() != null ? user.getPublicEmail().trim() : "";
    }
}
