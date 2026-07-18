package com.example.student.service;

import com.example.student.dto.ProfileUpdateRequest;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.service.LinkVerificationService.LinkTarget;
import com.example.student.model.Certificate;
import com.example.student.model.Education;
import com.example.student.model.Mission;
import com.example.student.model.MissionSubmission;
import com.example.student.model.Resume;
import com.example.student.model.Roadmap;
import com.example.student.model.Subject;
import com.example.student.model.User;
import com.example.student.model.UserRoadmapBadge;
import com.example.student.model.UserSubjectBadge;
import com.example.student.repository.CertificateRepository;
import com.example.student.repository.MissionRepository;
import com.example.student.repository.MissionSubmissionRepository;
import com.example.student.repository.ResumeRepository;
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
 * joined date, and an OPT-IN public contact email) — never the private login email,
 * password, internal IDs beyond the badge reference, or tokens.
 */
@Service
public class ProfileService {

    private static final int BIO_MAX = 300;
    private static final int NAME_MAX = 60;
    private static final int URL_MAX = 200;
    private static final int LOCATION_MAX = 120;
    private static final int EDU_FIELD_MAX = 120;
    private static final int EMAIL_MAX = 254;
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern URL_RE = Pattern.compile("^https?://[^\\s]{3,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern MOBILE_RE = Pattern.compile("^[+]?\\d[\\d\\s().-]{6,17}$");

    private final UserRepository userRepository;
    private final UsernameService usernameService;
    private final UserSubjectBadgeRepository subjectBadgeRepository;
    private final UserRoadmapBadgeRepository roadmapBadgeRepository;
    private final SubjectRepository subjectRepository;
    private final RoadmapRepository roadmapRepository;
    private final CertificateRepository certificateRepository;
    private final MissionSubmissionRepository missionSubmissionRepository;
    private final MissionRepository missionRepository;
    private final ResumeRepository resumeRepository;
    private final OtpService otpService;
    private final LinkVerificationService linkVerificationService;
    private final CacheService cacheService;

    public ProfileService(UserRepository userRepository,
                          UsernameService usernameService,
                          UserSubjectBadgeRepository subjectBadgeRepository,
                          UserRoadmapBadgeRepository roadmapBadgeRepository,
                          SubjectRepository subjectRepository,
                          RoadmapRepository roadmapRepository,
                          CertificateRepository certificateRepository,
                          MissionSubmissionRepository missionSubmissionRepository,
                          MissionRepository missionRepository,
                          ResumeRepository resumeRepository,
                          OtpService otpService,
                          LinkVerificationService linkVerificationService,
                          CacheService cacheService) {
        this.userRepository = userRepository;
        this.usernameService = usernameService;
        this.subjectBadgeRepository = subjectBadgeRepository;
        this.roadmapBadgeRepository = roadmapBadgeRepository;
        this.subjectRepository = subjectRepository;
        this.roadmapRepository = roadmapRepository;
        this.certificateRepository = certificateRepository;
        this.missionSubmissionRepository = missionSubmissionRepository;
        this.missionRepository = missionRepository;
        this.resumeRepository = resumeRepository;
        this.otpService = otpService;
        this.linkVerificationService = linkVerificationService;
        this.cacheService = cacheService;
    }

    // ── Public profile ────────────────────────────────────────────────
    // Short-TTL cached: this is a public, unauthenticated, read-heavy endpoint. A
    // self-edit evicts it immediately; badge/cert changes surface within the 90s TTL.
    public Map<String, Object> getPublicProfile(String username) {
        return cacheService.get("publicProfile", username, () -> buildPublicProfile(username));
    }

    private Map<String, Object> buildPublicProfile(String username) {
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
        res.put("githubVerified", user.getGithubId() != null && !user.getGithubId().isBlank());
        res.put("githubLogin", user.getGithubLogin() != null ? user.getGithubLogin() : "");
        res.put("linkedinUrl", user.getLinkedinUrl() != null ? user.getLinkedinUrl() : "");
        res.put("portfolioUrl", user.getPortfolioUrl() != null ? user.getPortfolioUrl() : "");
        res.put("location", user.getLocation() != null ? user.getLocation() : "");
        res.put("mobile", user.getMobile() != null ? user.getMobile() : "");
        res.put("education", user.getEducation());
        // Opt-in contact email for public profile display (resolved — may be login email when user chose "same").
        res.put("publicEmail", ProfileContactEmail.resolve(user));
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

        // Mission work — the hunter's shipped builds (GitHub repo + live demo).
        // Only submissions with at least one link are surfaced.
        List<MissionSubmission> submissions = missionSubmissionRepository.findByUserId(user.getId()).stream()
                .filter(s -> hasText(s.getRepoUrl()) || hasText(s.getDeployUrl()))
                .collect(Collectors.toList());
        List<Map<String, Object>> missionWork = new java.util.ArrayList<>();
        if (!submissions.isEmpty()) {
            Map<String, Mission> missionsById = new LinkedHashMap<>();
            for (Mission m : missionRepository.findAllById(
                    submissions.stream().map(MissionSubmission::getMissionId).collect(Collectors.toList()))) {
                missionsById.put(m.getId(), m);
            }
            for (MissionSubmission s : submissions) {
                Mission m = missionsById.get(s.getMissionId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", m != null && m.getTitle() != null ? m.getTitle() : "Mission build");
                item.put("rank", m != null ? m.getRank() : null);
                item.put("techStack", m != null && m.getTechStack() != null ? m.getTechStack() : List.of());
                item.put("repoUrl", hasText(s.getRepoUrl()) ? s.getRepoUrl() : null);
                item.put("deployUrl", hasText(s.getDeployUrl()) ? s.getDeployUrl() : null);
                item.put("submittedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString()
                        : (s.getCreatedAt() != null ? s.getCreatedAt().toString() : null));
                missionWork.add(item);
            }
        }
        res.put("missionWorkCount", missionWork.size());
        res.put("missionWork", missionWork);

        // Featured resume — only when the student picked one AND its share link is still on.
        // Turning share off (or deleting) clears featuredResumeId; this is a safety net.
        Map<String, Object> featuredResume = null;
        if (hasText(user.getFeaturedResumeId())) {
            featuredResume = resumeRepository.findById(user.getFeaturedResumeId())
                    .filter(r -> user.getId().equals(r.getUserId()))
                    .filter(r -> r.isShared() && hasText(r.getShareSlug()))
                    .map(r -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("title", hasText(r.getTitle()) ? r.getTitle() : "Resume");
                        m.put("slug", r.getShareSlug());
                        m.put("updatedAt", r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
                        return m;
                    })
                    .orElse(null);
        }
        res.put("resume", featuredResume);
        return res;
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private Map<String, Object> badge(String type, String title, int score, int total, Object earnedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("title", title);
        m.put("score", score);
        m.put("total", total);
        m.put("scorePercent", total > 0 ? (int) Math.round(score * 100.0 / total) : 0);
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
        String previousUsername = user.getPublicUsername();
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

        // GitHub profile link — OAuth only (GitHubLinkService). Manual URLs are not accepted.
        if (req.getGithubUrl() != null && !req.getGithubUrl().trim().isEmpty()) {
            if (user.getGithubId() == null || user.getGithubId().isBlank()) {
                throw new IllegalArgumentException("Connect GitHub to add your profile link.");
            }
            String incoming = req.getGithubUrl().trim();
            String current = user.getGithubUrl() != null ? user.getGithubUrl().trim() : "";
            if (!incoming.equals(current)) {
                throw new IllegalArgumentException("Disconnect GitHub to change your GitHub link.");
            }
        }
        verifyProfileLinks(req);
        if (req.getLinkedinUrl() != null) {
            String linkedin = cleanLinkedInUrl(req.getLinkedinUrl());
            assertLinkNotUsedByAnother(user, linkedin, userRepository::findFirstByLinkedinUrl, "LinkedIn");
            user.setLinkedinUrl(linkedin);
        }
        if (req.getPortfolioUrl() != null) {
            String portfolio = cleanPortfolioUrl(req.getPortfolioUrl());
            assertLinkNotUsedByAnother(user, portfolio, userRepository::findFirstByPortfolioUrl, "Portfolio");
            user.setPortfolioUrl(portfolio);
        }

        if (req.getLocation() != null) {
            String loc = req.getLocation().trim();
            if (loc.length() > LOCATION_MAX) throw new IllegalArgumentException("Location is too long");
            user.setLocation(loc.isEmpty() ? null : loc);
        }

        if (req.getMobile() != null) {
            String mobile = req.getMobile().trim();
            if (mobile.isEmpty()) {
                user.setMobile(null);
            } else {
                if (mobile.length() > 20) throw new IllegalArgumentException("Mobile number is too long");
                if (!MOBILE_RE.matcher(mobile).matches())
                    throw new IllegalArgumentException("Enter a valid mobile number");
                user.setMobile(mobile);
            }
        }

        // Contact email preference — same as login (explicit opt-in) or a separate public address.
        if (req.getUseLoginEmailForContact() != null) {
            boolean useLogin = Boolean.TRUE.equals(req.getUseLoginEmailForContact());
            user.setUseLoginEmailForContact(useLogin);
            if (useLogin) user.setPublicEmail(null);
        }

        // Custom public contact email — only when not using login email.
        if (req.getPublicEmail() != null && !Boolean.TRUE.equals(user.getUseLoginEmailForContact())) {
            applyContactEmail(user, req.getPublicEmail());
        }

        if (req.getEducation() != null) {
            user.setEducation(sanitizeEducation(req.getEducation()));
        }

        if (req.getPublicProfile() != null) {
            user.setPublicProfile(req.getPublicProfile());
        }

        // Featured resume — must already have its share link ON in Resume Studio.
        // Blank/empty clears the selection (show no resume on the public profile).
        if (req.getFeaturedResumeId() != null) {
            String rid = req.getFeaturedResumeId().trim();
            if (rid.isEmpty()) {
                user.setFeaturedResumeId(null);
            } else {
                Resume resume = resumeRepository.findByIdAndUserId(rid, user.getId())
                        .orElseThrow(() -> new IllegalArgumentException("That resume was not found"));
                if (!resume.isShared() || resume.getShareSlug() == null || resume.getShareSlug().isBlank()) {
                    throw new IllegalArgumentException(
                            "Turn on the share link for this resume in Resume Studio before showing it on your profile");
                }
                user.setFeaturedResumeId(resume.getId());
            }
        }

        // role, xp, login email, password, etc. are intentionally never read from the request.
        User saved = userRepository.save(user);
        // Bust the cached public profile (old + new handle) so self-edits show immediately.
        if (previousUsername != null) cacheService.evict("publicProfile", previousUsername);
        String currentUsername = saved.getPublicUsername();
        if (currentUsername != null && !currentUsername.equals(previousUsername)) {
            cacheService.evict("publicProfile", currentUsername);
        }
        return saved;
    }

    /** Send OTP to verify a new/changed contact email (authenticated profile editor). */
    public long sendContactEmailOtp(User user, String rawEmail, String clientIp) {
        String normalized = normalizeContactEmail(rawEmail);
        assertContactEmailAllowed(user, normalized);
        return otpService.sendContactEmailOtp(user.getId(), normalized, clientIp);
    }

    /** Verify the OTP for a contact email before saving the profile. */
    public boolean verifyContactEmailOtp(User user, String rawEmail, String otp) {
        if (otp == null || otp.isBlank())
            throw new IllegalArgumentException("OTP is required");
        String normalized = normalizeContactEmail(rawEmail);
        assertContactEmailAllowed(user, normalized);
        return otpService.verifyContactEmailOtp(user.getId(), normalized, otp.trim());
    }

    private void applyContactEmail(User user, String rawEmail) {
        String pemail = rawEmail == null ? "" : rawEmail.trim();
        if (pemail.isEmpty()) {
            user.setPublicEmail(null);
            return;
        }
        String normalized = normalizeContactEmail(pemail);
        assertContactEmailAllowed(user, normalized);

        String current = user.getPublicEmail() != null ? user.getPublicEmail().trim().toLowerCase() : "";
        if (!normalized.equals(current)
                && !otpService.isContactEmailVerified(user.getId(), normalized)) {
            throw new IllegalArgumentException(
                    "Verify your contact email with the OTP we sent before saving.");
        }
        user.setPublicEmail(normalized);
        if (!normalized.equals(current)) {
            otpService.clearContactEmail(user.getId(), normalized);
        }
    }

    private String normalizeContactEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank())
            throw new IllegalArgumentException("Email is required");
        String pemail = rawEmail.trim();
        if (pemail.length() > EMAIL_MAX) throw new IllegalArgumentException("Contact email is too long");
        if (!EMAIL_RE.matcher(pemail).matches())
            throw new IllegalArgumentException("Contact email must be a valid email address");
        return pemail.toLowerCase();
    }

    private void assertContactEmailAllowed(User user, String normalized) {
        String loginEmail = user.getEmail().trim().toLowerCase();
        if (normalized.equals(loginEmail)) {
            throw new IllegalArgumentException(
                    "Choose \"Same as login\" to use your login email on your profile.");
        }
        if (userRepository.existsByEmail(normalized)) {
            throw new IllegalArgumentException(
                    "That email is already registered as a login address. Use a different contact email.");
        }
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
        String years = trimCap(e.getYears(), 20);
        if (years == null) years = trimCap(e.getGraduationYear(), 20);
        String cgpa = trimCap(e.getCgpa(), 20);
        if (degree == null && field == null && institution == null && years == null && cgpa == null) return null;
        return Education.builder()
                .degree(degree).fieldOfStudy(field).institution(institution)
                .years(years).cgpa(cgpa)
                .build();
    }

    private String trimCap(String raw, int max) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }

    private void verifyProfileLinks(ProfileUpdateRequest req) {
        boolean skip = Boolean.TRUE.equals(req.getSkipLinkVerification());
        List<LinkTarget> targets = new java.util.ArrayList<>();
        if (req.getLinkedinUrl() != null && !req.getLinkedinUrl().trim().isEmpty()) {
            String normalized = linkVerificationService.normalizeUrl(req.getLinkedinUrl().trim());
            if (normalized == null || !linkVerificationService.isValidLinkedInProfileUrl(normalized)) {
                throw new IllegalArgumentException(
                        "Enter a LinkedIn profile URL like https://www.linkedin.com/in/your-name");
            }
            targets.add(new LinkTarget("LinkedIn",
                    linkVerificationService.canonicalLinkedInUrl(normalized)));
        }
        if (req.getPortfolioUrl() != null && !req.getPortfolioUrl().trim().isEmpty()) {
            String normalized = linkVerificationService.normalizeUrl(req.getPortfolioUrl().trim());
            if (normalized == null) {
                throw new IllegalArgumentException("Portfolio link must be a full URL starting with https://");
            }
            targets.add(new LinkTarget("Portfolio",
                    linkVerificationService.canonicalPortfolioUrl(normalized)));
        }
        linkVerificationService.requireVerified(targets, skip);
    }

    /** Trims a link; returns null when blank (clears it) or validates it is a full http(s) URL. */
    private String cleanLinkedInUrl(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        String normalized = linkVerificationService.normalizeUrl(v);
        if (normalized == null || !linkVerificationService.isValidLinkedInProfileUrl(normalized)) {
            throw new IllegalArgumentException(
                    "Enter a LinkedIn profile URL like https://www.linkedin.com/in/your-name");
        }
        return linkVerificationService.canonicalLinkedInUrl(normalized);
    }

    private String cleanPortfolioUrl(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if (v.length() > URL_MAX)
            throw new IllegalArgumentException("Portfolio link is too long");
        String normalized = linkVerificationService.normalizeUrl(v);
        if (normalized == null || !URL_RE.matcher(normalized).matches())
            throw new IllegalArgumentException("Portfolio link must be a full URL starting with https://");
        return linkVerificationService.canonicalPortfolioUrl(normalized);
    }

    private void assertLinkNotUsedByAnother(User user, String url,
                                            java.util.function.Function<String, java.util.Optional<User>> finder,
                                            String label) {
        if (url == null) return;
        java.util.Optional<User> existing = finder.apply(url);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
            throw new IllegalArgumentException(
                    "This " + label + " link is already linked to another account.");
        }
    }

}
