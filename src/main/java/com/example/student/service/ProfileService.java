package com.example.student.service;

import com.example.student.dto.ProfileUpdateRequest;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.Roadmap;
import com.example.student.model.Subject;
import com.example.student.model.User;
import com.example.student.model.UserRoadmapBadge;
import com.example.student.model.UserSubjectBadge;
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
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern URL_RE = Pattern.compile("^https?://[^\\s]{3,}$", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final UsernameService usernameService;
    private final UserSubjectBadgeRepository subjectBadgeRepository;
    private final UserRoadmapBadgeRepository roadmapBadgeRepository;
    private final SubjectRepository subjectRepository;
    private final RoadmapRepository roadmapRepository;

    public ProfileService(UserRepository userRepository,
                          UsernameService usernameService,
                          UserSubjectBadgeRepository subjectBadgeRepository,
                          UserRoadmapBadgeRepository roadmapBadgeRepository,
                          SubjectRepository subjectRepository,
                          RoadmapRepository roadmapRepository) {
        this.userRepository = userRepository;
        this.usernameService = usernameService;
        this.subjectBadgeRepository = subjectBadgeRepository;
        this.roadmapBadgeRepository = roadmapBadgeRepository;
        this.subjectRepository = subjectRepository;
        this.roadmapRepository = roadmapRepository;
    }

    // ── Public profile ────────────────────────────────────────────────
    public Map<String, Object> getPublicProfile(String username) {
        User user = userRepository.findByUsername(username)
                .filter(u -> !"GUEST".equals(u.getRole()))
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

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
        res.put("badgeCount", badges.size());
        res.put("badges", badges);
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

        // role, xp, email, password, etc. are intentionally never read from the request.
        return userRepository.save(user);
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
