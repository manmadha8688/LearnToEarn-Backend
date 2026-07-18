package com.example.student.service;

import com.example.student.model.Education;
import com.example.student.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Personal info, education and career links live on {@link User} (My Profile).
 * Resume {@code data} stores only builder-owned sections (objective, skills, projects, …).
 * This helper strips profile keys on write and merges the live profile on public read.
 */
public final class ResumeProfileMerge {

    private static final Set<String> PROFILE_KEYS = Set.of(
            "fullName", "email", "mobile", "linkedin", "github", "portfolio", "education"
    );

    private ResumeProfileMerge() {}

    /** Keep only resume-builder fields before persisting. */
    public static Map<String, Object> stripProfileFields(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return new LinkedHashMap<>();
        Map<String, Object> out = new LinkedHashMap<>(data);
        PROFILE_KEYS.forEach(out::remove);
        return out;
    }

    /** Overlay the owner's current My Profile onto stored resume content (for shared view / PDF). */
    public static Map<String, Object> mergeFromUser(User user, Map<String, Object> stored) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (stored != null) merged.putAll(stored);
        PROFILE_KEYS.forEach(merged::remove);
        if (user == null) return merged;

        merged.put("fullName", textOrEmpty(user.getFullName()));
        merged.put("email", contactEmail(user));
        merged.put("mobile", textOrEmpty(user.getMobile()));
        merged.put("linkedin", textOrEmpty(user.getLinkedinUrl()));
        merged.put("github", textOrEmpty(user.getGithubUrl()));
        merged.put("portfolio", textOrEmpty(user.getPortfolioUrl()));
        merged.put("education", educationForResume(user.getEducation()));
        return merged;
    }

    private static String contactEmail(User user) {
        return ProfileContactEmail.resolve(user);
    }

    private static List<Map<String, Object>> educationForResume(Education edu) {
        List<Map<String, Object>> list = new ArrayList<>(1);
        Map<String, Object> row = new LinkedHashMap<>();
        if (edu == null) {
            row.put("degree", "");
            row.put("branch", "");
            row.put("college", "");
            row.put("years", "");
            row.put("cgpa", "");
            list.add(row);
            return list;
        }
        row.put("degree", textOrEmpty(edu.getDegree()));
        row.put("branch", textOrEmpty(edu.getFieldOfStudy()));
        row.put("college", textOrEmpty(edu.getInstitution()));
        row.put("years", educationYears(edu));
        row.put("cgpa", textOrEmpty(edu.getCgpa()));
        list.add(row);
        return list;
    }

    private static String educationYears(Education edu) {
        if (hasText(edu.getYears())) return edu.getYears().trim();
        if (hasText(edu.getGraduationYear())) return edu.getGraduationYear().trim();
        return "";
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String textOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
