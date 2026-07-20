package com.example.student.service;

import com.example.student.model.Resume;
import com.example.student.model.User;
import com.example.student.repository.ResumeRepository;
import com.example.student.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    // A user may keep at most this many resumes (e.g. tailored per role).
    private static final int MAX_RESUMES = 3;
    // Guard against oversized payloads — a resume is small; anything huge is abuse.
    private static final int MAX_KEYS = 40;

    private static final String SLUG_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"; // no ambiguous chars
    private static final int SLUG_LEN = 10;
    private final SecureRandom random = new SecureRandom();

    private final ResumeRepository resumeRepo;
    private final UserRepository userRepository;
    private final LinkVerificationService linkVerificationService;

    public ResumeService(ResumeRepository resumeRepo, UserRepository userRepository,
                         LinkVerificationService linkVerificationService) {
        this.resumeRepo = resumeRepo;
        this.userRepository = userRepository;
        this.linkVerificationService = linkVerificationService;
    }

    /** All of the user's resumes, newest-updated first, as safe view maps. */
    public List<Map<String, Object>> list(String userId) {
        String featuredId = userRepository.findById(userId).map(User::getFeaturedResumeId).orElse(null);
        return resumeRepo.findByUserId(userId, Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .map(r -> toView(r, featuredId))
                .collect(Collectors.toList());
    }

    /** Create a new resume for the user. Enforces the per-user cap. */
    public Map<String, Object> create(String userId, String title, Map<String, Object> data,
                                      boolean skipLinkVerification) {
        Map<String, Object> stored = ResumeProfileMerge.stripProfileFields(data);
        validateData(stored);
        normalizeProjectLinks(stored);
        verifyResumeLinks(stored, skipLinkVerification);
        if (resumeRepo.countByUserId(userId) >= MAX_RESUMES)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You can keep up to " + MAX_RESUMES + " resumes. Delete one to add another.");

        Resume resume = Resume.builder()
                .userId(userId)
                .title(cleanTitle(title))
                .data(stored)
                .shared(false)
                .build();
        return toView(resumeRepo.save(resume), featuredIdOf(userId));
    }

    /** Update the title/content of a resume the user owns. */
    public Map<String, Object> update(String userId, String id, String title, Map<String, Object> data,
                                      boolean skipLinkVerification) {
        Map<String, Object> stored = ResumeProfileMerge.stripProfileFields(data);
        validateData(stored);
        normalizeProjectLinks(stored);
        verifyResumeLinks(stored, skipLinkVerification);
        Resume resume = ownedOr404(userId, id);
        resume.setTitle(cleanTitle(title));
        resume.setData(stored);
        return toView(resumeRepo.save(resume), featuredIdOf(userId));
    }

    /** Delete a resume the user owns. Also clears it from the public profile if it was featured. */
    public void delete(String userId, String id) {
        Resume resume = ownedOr404(userId, id);
        clearFeaturedIfMatches(userId, id);
        resumeRepo.delete(resume);
    }

    /**
     * Turn sharing on or off. Turning on mints an unguessable slug (once);
     * turning off keeps the slug but makes the public link 404 until re-enabled,
     * and auto-removes this resume from the user's public profile if it was featured
     * (so the profile never points at a broken link).
     */
    public Map<String, Object> setShared(String userId, String id, boolean makePublic) {
        Resume resume = ownedOr404(userId, id);
        if (makePublic && (resume.getShareSlug() == null || resume.getShareSlug().isBlank())) {
            resume.setShareSlug(uniqueSlug());
        }
        resume.setShared(makePublic);
        Resume saved = resumeRepo.save(resume);
        boolean clearedFromProfile = false;
        if (!makePublic) {
            clearedFromProfile = clearFeaturedIfMatches(userId, id);
        }
        Map<String, Object> view = toView(saved, clearedFromProfile ? null : featuredIdOf(userId));
        view.put("clearedFromProfile", clearedFromProfile);
        return view;
    }

    /**
     * Public read by share slug. Distinguishes two cases so the share page can
     * show two different screens: 404 when the slug does not exist (never
     * created / deleted), 403 "private" when it exists but sharing is off.
     * Profile fields merged live from owner.
     */
    public Map<String, Object> getPublic(String slug) {
        Resume resume = resumeRepo.findByShareSlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This resume link does not exist."));
        if (!resume.isShared()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "private");
        }
        User owner = userRepository.findById(resume.getUserId()).orElse(null);
        Map<String, Object> merged = ResumeProfileMerge.mergeFromUser(owner, resume.getData());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("title", resume.getTitle());
        res.put("data", merged);
        res.put("updatedAt", resume.getUpdatedAt());
        return res;
    }

    // ── helpers ──

    private Resume ownedOr404(String userId, String id) {
        return resumeRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));
    }

    /** If this resume is the one featured on the profile, clear that pick. */
    private boolean clearFeaturedIfMatches(String userId, String resumeId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;
        if (resumeId == null || !resumeId.equals(user.getFeaturedResumeId())) return false;
        user.setFeaturedResumeId(null);
        userRepository.save(user);
        return true;
    }

    private void validateData(Map<String, Object> data) {
        if (data == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume content is required");
        if (data.size() > MAX_KEYS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume content is too large");
        // Empty resume (no sections yet) is allowed — profile fields live on User.
    }

    @SuppressWarnings("unchecked")
    private void normalizeProjectLinks(Map<String, Object> data) {
        Object raw = data.get("projects");
        if (!(raw instanceof List<?> projects)) return;
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            Object item = projects.get(i);
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            Object linkObj = copy.get("link");
            if (linkObj != null) {
                String link = String.valueOf(linkObj).trim();
                if (!link.isEmpty()) {
                    String normalized = linkVerificationService.normalizeUrl(link);
                    if (normalized == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Project " + (i + 1) + ": enter a full link starting with https://");
                    }
                    copy.put("link", normalized);
                }
            }
            out.add(copy);
        }
        data.put("projects", out);
    }

    @SuppressWarnings("unchecked")
    private void verifyResumeLinks(Map<String, Object> data, boolean skip) {
        Object raw = data.get("projects");
        if (!(raw instanceof List<?> projects)) return;
        java.util.List<LinkVerificationService.LinkTarget> targets = new java.util.ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            Object item = projects.get(i);
            if (!(item instanceof Map<?, ?> map)) continue;
            Object linkObj = map.get("link");
            if (linkObj == null) continue;
            String link = String.valueOf(linkObj).trim();
            if (link.isEmpty()) continue;
            targets.add(new LinkVerificationService.LinkTarget("Project " + (i + 1) + " link", link));
        }
        linkVerificationService.requireVerified(targets, skip);
    }

    private String cleanTitle(String title) {
        String t = title == null ? "" : title.trim();
        if (t.length() > 60) t = t.substring(0, 60);
        return t.isBlank() ? "My Resume" : t;
    }

    private String uniqueSlug() {
        for (int attempt = 0; attempt < 6; attempt++) {
            String slug = randomSlug();
            if (!resumeRepo.existsByShareSlug(slug)) return slug;
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not create a share link. Please try again.");
    }

    private String randomSlug() {
        StringBuilder sb = new StringBuilder(SLUG_LEN);
        for (int i = 0; i < SLUG_LEN; i++) {
            sb.append(SLUG_ALPHABET.charAt(random.nextInt(SLUG_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String featuredIdOf(String userId) {
        return userRepository.findById(userId).map(User::getFeaturedResumeId).orElse(null);
    }

    /** Owner-facing view of a resume (includes content + share state). */
    private Map<String, Object> toView(Resume r, String featuredId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("title", r.getTitle() != null ? r.getTitle() : "My Resume");
        m.put("data", ResumeProfileMerge.stripProfileFields(r.getData()));
        m.put("isPublic", r.isShared());
        m.put("shareSlug", r.getShareSlug());
        m.put("updatedAt", r.getUpdatedAt());
        // Helpful for the builder: is this the one currently on the public profile?
        m.put("featured", r.getId() != null && r.getId().equals(featuredId));
        return m;
    }
}
