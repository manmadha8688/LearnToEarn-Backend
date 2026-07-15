package com.example.student.service;

import com.example.student.dto.ProfileUpdateRequest;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.Certificate;
import com.example.student.model.Education;
import com.example.student.model.Roadmap;
import com.example.student.model.Subject;
import com.example.student.model.User;
import com.example.student.model.UserRoadmapBadge;
import com.example.student.model.UserSubjectBadge;
import com.example.student.repository.CertificateRepository;
import com.example.student.repository.RoadmapRepository;
import com.example.student.repository.SubjectRepository;
import com.example.student.repository.UserRepository;
import com.example.student.repository.UserRoadmapBadgeRepository;
import com.example.student.repository.UserSubjectBadgeRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Read-only public profile assembly + authenticated self-update.
 * Public payload exposes ONLY safe fields (name, handle, avatar color, bio, rank, badges,
 * joined date) — never email, password, internal IDs beyond the badge reference, or tokens.
 */
@Service
public class ProfileService {

    private static final int BIO_MAX = 300;
    private static final int NAME_MAX = 60;
    private static final int URL_MAX = 200;
    private static final int LOCATION_MAX = 120;
    private static final int EDU_FIELD_MAX = 120;
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern URL_RE = Pattern.compile("^https?://[^\\s]{3,}$", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final UsernameService usernameService;
    private final UserSubjectBadgeRepository subjectBadgeRepository;
    private final UserRoadmapBadgeRepository roadmapBadgeRepository;
    private final SubjectRepository subjectRepository;
    private final RoadmapRepository roadmapRepository;
    private final CertificateRepository certificateRepository;

    public ProfileService(UserRepository userRepository,
                          UsernameService usernameService,
                          UserSubjectBadgeRepository subjectBadgeRepository,
                          UserRoadmapBadgeRepository roadmapBadgeRepository,
                          SubjectRepository subjectRepository,
                          RoadmapRepository roadmapRepository,
                          CertificateRepository certificateRepository) {
        this.userRepository = userRepository;
        this.usernameService = usernameService;
        this.subjectBadgeRepository = subjectBadgeRepository;
        this.roadmapBadgeRepository = roadmapBadgeRepository;
        this.subjectRepository = subjectRepository;
        this.roadmapRepository = roadmapRepository;
        this.certificateRepository = certificateRepository;
    }

    // ── Public profile ────────────────────────────────────────────────
    public Map<String, Object> getPublicProfile(String username) {
        User user = userRepository.findByUsername(username)
                .filter(u -> !"GUEST".equals(u.getRole()))
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        // Private profiles are invisible to everyone but the owner (legacy null = public).
        if (Boolean.FALSE.equals(user.getPublicProfile())) {
            throw new ResourceNotFoundException("Profile not found");
        }

        List<UserSubjectBadge> subjectBadges = subjectBadgeRepository.findByUserId(user.getId());
        List<UserRoadmapBadge> roadmapBadges = roadmapBadgeRepository.findByUserId(user.getId());

        Map<String, String> subjectTitles = titleMap(
                subjectBadges.stream().map(UserSubjectBadge::getSubjectId).collect(Collectors.toList()),
                subjectRepository::findAllById, Subject::getId, Subject::getTitle);
        Map<String, String> roadmapTitles = titleMap(
                roadmapBadges.stream().map(UserRoadmapBadge::getRoadmapId).collect(Collectors.toList()),
                roadmapRepository::findAllById, Roadmap::getId, Roadmap::getTitle);

        List<Map<String, Object>> badges = new java.util.ArrayList<>();
        for (UserSubjectBadge b : subjectBadges) {
            badges.add(badge("GATE_MASTERED",
                    subjectTitles.getOrDefault(b.getSubjectId(), "Subject"),
                    b.getScore(), b.getTotal(), b.getEarnedAt()));
        }
        for (UserRoadmapBadge b : roadmapBadges) {
            badges.add(badge(b.getBadge(),
                    roadmapTitles.getOrDefault(b.getRoadmapId(), "Roadmap"),
                    b.getScore(), b.getTotal(), b.getEarnedAt()));
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("fullName", user.getFullName());
        res.put("username", user.getPublicUsername());
        res.put("avatarColor", user.getAvatarColor() != null ? user.getAvatarColor() : "#4F46E5");
        res.put("bio", user.getBio() != null ? user.getBio() : "");
        res.put("rank", user.getRank() != null ? user.getRank() : "E");
        res.put("xp", user.getXp());
        res.put("level", user.getLevel());
        res.put("joinedAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        res.put("githubUrl", user.getGithubUrl() != null ? user.getGithubUrl() : "");
        res.put("linkedinUrl", user.getLinkedinUrl() != null ? user.getLinkedinUrl() : "");
        res.put("portfolioUrl", user.getPortfolioUrl() != null ? user.getPortfolioUrl() : "");
        res.put("location", user.getLocation() != null ? user.getLocation() : "");
        res.put("education", user.getEducation());
        res.put("email", user.getEmail() != null ? user.getEmail() : "");
        res.put("badgeCount", badges.size());
        res.put("badges", badges);

        // Certificates — publicly verifiable, so safe to surface (title, kind, score,
        // rank, issue date, and the verification code). No internal IDs.
        List<Map<String, Object>> certificates = new java.util.ArrayList<>();
        for (Certificate c : certificateRepository.findByUserIdOrderByIssuedAtDesc(user.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c.getCode());
            m.put("title", c.getCredentialTitle());
            m.put("kind", c.getCredentialKind());
            m.put("score", c.getScore());
            m.put("total", c.getTotal());
            m.put("scorePercent", c.getScorePercent());
            m.put("icon", c.getIcon());
            m.put("color", c.getColor());
            m.put("rankAtIssue", c.getRankAtIssue());
            m.put("issuedAt", c.getIssuedAt() != null ? c.getIssuedAt().toString() : null);
            certificates.add(m);
        }
        res.put("certificateCount", certificates.size());
        res.put("certificates", certificates);
        return res;
    }

    private Map<String, Object> badge(String type, String title, int score, int total, Object earnedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("title", title);
        m.put("score", score);
        m.put("total", total);
        m.put("earnedAt", earnedAt != null ? earnedAt.toString() : null);
        return m;
    }

    private <T> Map<String, String> titleMap(List<String> ids,
                                             Function<List<String>, Iterable<T>> finder,
                                             Function<T, String> idGetter,
                                             Function<T, String> titleGetter) {
        if (ids.isEmpty()) return Map.of();
        Map<String, String> map = new LinkedHashMap<>();
        for (T t : finder.apply(ids)) map.put(idGetter.apply(t), titleGetter.apply(t));
        return map;
    }

    // ── Self update (settings) ────────────────────────────────────────
    public User updateOwnProfile(User user, ProfileUpdateRequest req) {
        if (req.getFullName() != null) {
            String name = req.getFullName().trim();
            if (name.isEmpty()) throw new IllegalArgumentException("Display name cannot be empty");
            if (name.length() > NAME_MAX) throw new IllegalArgumentException("Display name is too long");
            user.setFullName(name);
        }

        if (req.getUsername() != null) {
            String uname = req.getUsername().trim().toLowerCase();
            if (!uname.equals(user.getPublicUsername())) {
                if (!usernameService.isValidFormat(uname))
                    throw new IllegalArgumentException(
                            "Username must be 3–20 characters: lowercase letters, numbers or underscore");
                if (userRepository.existsByUsername(uname))
                    throw new IllegalArgumentException("That username is already taken");
                user.setUsername(uname);
            }
        }

        if (req.getBio() != null) {
            String bio = req.getBio().trim();
            if (bio.length() > BIO_MAX) throw new IllegalArgumentException("Bio must be 300 characters or fewer");
            user.setBio(bio);
        }

        if (req.getAvatarColor() != null && !req.getAvatarColor().isBlank()) {
            String color = req.getAvatarColor().trim();
            if (!HEX_COLOR.matcher(color).matches())
                throw new IllegalArgumentException("Avatar colour must be a hex value like #4F46E5");
            user.setAvatarColor(color);
        }

        // Career links — each optional; empty string clears it, otherwise must be a full URL.
        if (req.getGithubUrl() != null)    user.setGithubUrl(cleanUrl(req.getGithubUrl(), "GitHub"));
        if (req.getLinkedinUrl() != null)  user.setLinkedinUrl(cleanUrl(req.getLinkedinUrl(), "LinkedIn"));
        if (req.getPortfolioUrl() != null) user.setPortfolioUrl(cleanUrl(req.getPortfolioUrl(), "Portfolio"));

        if (req.getLocation() != null) {
            String loc = req.getLocation().trim();
            if (loc.length() > LOCATION_MAX) throw new IllegalArgumentException("Location is too long");
            user.setLocation(loc.isEmpty() ? null : loc);
        }

        if (req.getEducation() != null) {
            user.setEducation(sanitizeEducation(req.getEducation()));
        }

        if (req.getPublicProfile() != null) {
            user.setPublicProfile(req.getPublicProfile());
        }

        // role, xp, login email, password, etc. are intentionally never read from the request.
        return userRepository.save(user);
    }

    /** True when the handle is a valid format AND free (the caller's current handle counts as free). */
    public Map<String, Object> checkUsernameAvailability(User current, String raw) {
        String uname = raw == null ? "" : raw.trim().toLowerCase();
        Map<String, Object> res = new LinkedHashMap<>();
        boolean valid = usernameService.isValidFormat(uname);
        boolean available = valid
                && (uname.equals(current.getPublicUsername()) || !userRepository.existsByUsername(uname));
        res.put("valid", valid);
        res.put("available", available);
        return res;
    }

    /** Trim/cap each education field; returns null when every field is blank (clears it). */
    private Education sanitizeEducation(Education e) {
        String degree = trimCap(e.getDegree(), EDU_FIELD_MAX);
        String field = trimCap(e.getFieldOfStudy(), EDU_FIELD_MAX);
        String institution = trimCap(e.getInstitution(), EDU_FIELD_MAX);
        String year = trimCap(e.getGraduationYear(), 10);
        String cgpa = trimCap(e.getCgpa(), 20);
        if (degree == null && field == null && institution == null && year == null && cgpa == null) return null;
        return Education.builder()
                .degree(degree).fieldOfStudy(field).institution(institution)
                .graduationYear(year).cgpa(cgpa)
                .build();
    }

    private String trimCap(String raw, int max) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }

    /** Trims a link; returns null when blank (clears it) or validates it is a full http(s) URL. */
    private String cleanUrl(String raw, String label) {
        String url = raw.trim();
        if (url.isEmpty()) return null;
        if (url.length() > URL_MAX)
            throw new IllegalArgumentException(label + " link is too long");
        if (!URL_RE.matcher(url).matches())
            throw new IllegalArgumentException(label + " link must be a full URL starting with https://");
        return url;
    }
}
