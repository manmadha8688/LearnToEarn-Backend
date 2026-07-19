package com.example.student.service;

import com.example.student.model.Mission;
import com.example.student.model.MissionSubmission;
import com.example.student.model.User;
import com.example.student.repository.MissionRepository;
import com.example.student.repository.MissionSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MissionSubmissionService {

    private static final int MAX_URL = 500;
    // http(s) URL with a host; anything else is rejected
    private static final Pattern URL = Pattern.compile("^https?://[^\\s/$.?#].[^\\s]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_REPO = Pattern.compile(
            "^https?://(?:www\\.)?github\\.com/([a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38})/([a-zA-Z0-9._-]+)(?:/.*)?(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    private final MissionSubmissionRepository submissionRepo;
    private final MissionRepository missionRepo;
    private final LinkVerificationService linkVerificationService;
    private final ProgressService progressService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();

    public MissionSubmissionService(MissionSubmissionRepository submissionRepo, MissionRepository missionRepo,
                                    LinkVerificationService linkVerificationService,
                                    ProgressService progressService) {
        this.submissionRepo = submissionRepo;
        this.missionRepo = missionRepo;
        this.linkVerificationService = linkVerificationService;
        this.progressService = progressService;
    }

    /** Current hunter's submission for a mission, or null if none yet. */
    public MissionSubmission get(String userId, String missionId) {
        return submissionRepo.findByUserIdAndMissionId(userId, missionId).orElse(null);
    }

    /** All of the current hunter's submissions (for the mission board's per-card status). */
    public List<MissionSubmission> listForUser(String userId) {
        return submissionRepo.findByUserId(userId);
    }

    /** Create or update repo and/or deploy link for a mission (one field per request). */
    public MissionSubmission save(User user, String missionId, String repoUrl, String deployUrl,
                                  boolean skipLinkVerification, String target) {
        Mission mission = missionRepo.findById(missionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mission not found"));
        if (target == null || target.isBlank())
            throw new IllegalArgumentException("Save target is required (repo or deploy).");

        MissionSubmission s = submissionRepo.findByUserIdAndMissionId(user.getId(), missionId)
                .orElseGet(() -> MissionSubmission.builder()
                        .userId(user.getId())
                        .missionId(missionId)
                        .build());

        int xpDelta = switch (target.trim().toLowerCase()) {
            case "repo" -> saveRepo(user, s, repoUrl, mission.getRank());
            case "deploy" -> saveDeploy(user, s, deployUrl, skipLinkVerification, mission.getRank());
            default -> throw new IllegalArgumentException("Save target must be repo or deploy.");
        };
        MissionSubmission saved = submissionRepo.save(s);
        saved.setXpEarned(xpDelta);
        return saved;
    }

    /** @return XP delta (+ awarded for a newly linked repo, − reversed if the link was cleared) */
    private int saveRepo(User user, MissionSubmission s, String repoUrl, String missionRank) {
        if (repoUrl == null || repoUrl.isBlank()) {
            s.setRepoUrl(null);
            s.setRepoKey(null);
            return reverseXp(user.getId(), s.getRepoXp(), amt -> s.setRepoXp(0));
        }
        String repo = clean(repoUrl, "Repository link");
        if (repo == null)
            throw new IllegalArgumentException("Paste your GitHub repository link before saving.");
        ParsedRepo parsed = parseGitHubRepo(repo);
        String repoKey = repoKey(parsed);
        validateGitHubRepo(user, s, parsed, repoKey);
        s.setRepoUrl(canonicalRepoUrl(parsed));
        s.setRepoKey(repoKey);
        // Award once — swapping to a different (still-owned) repo does not re-award.
        return awardXp(user.getId(), s.getRepoXp(), repoXpFor(missionRank), amt -> s.setRepoXp(amt));
    }

    /** @return XP delta (+ awarded for a newly linked live demo, − reversed if the link was cleared) */
    private int saveDeploy(User user, MissionSubmission s, String deployUrl, boolean skipLinkVerification,
                           String missionRank) {
        if (deployUrl == null || deployUrl.isBlank()) {
            s.setDeployUrl(null);
            return reverseXp(user.getId(), s.getDeployXp(), amt -> s.setDeployXp(0));
        }
        String normalized = linkVerificationService.normalizeUrl(deployUrl.trim());
        if (normalized == null)
            throw new IllegalArgumentException("Enter a full live demo URL like https://your-app.onrender.com");
        linkVerificationService.requireVerified(
                List.of(new LinkVerificationService.LinkTarget("Live demo", normalized)),
                skipLinkVerification);
        s.setDeployUrl(normalized);
        return awardXp(user.getId(), s.getDeployXp(), deployXpFor(missionRank), amt -> s.setDeployXp(amt));
    }

    /** Grant {@code amount} XP once; no-op (returns 0) if this link was already rewarded. */
    private int awardXp(String userId, int alreadyAwarded, int amount, java.util.function.IntConsumer record) {
        if (alreadyAwarded > 0 || amount <= 0) return 0;
        progressService.awardXp(userId, amount);
        record.accept(amount);
        return amount;
    }

    /** Reverse previously-granted XP when a link is cleared. Returns a negative delta. */
    private int reverseXp(String userId, int alreadyAwarded, java.util.function.IntConsumer clear) {
        if (alreadyAwarded <= 0) return 0;
        progressService.deductXp(userId, alreadyAwarded);
        clear.accept(0);
        return -alreadyAwarded;
    }

    // Mission-link XP by mission rank. A live deploy (proof it runs) is worth more than the
    // repo. Kept modest on purpose — XP also flows from quizzes, daily bonus, and badges.
    private static int repoXpFor(String rank) {
        return switch (rank == null ? "" : rank.trim().toUpperCase()) {
            case "S" -> 200;
            case "A" -> 140;
            case "B" -> 100;
            case "C" -> 60;
            default  -> 40; // D and anything unset/unknown
        };
    }

    private static int deployXpFor(String rank) {
        return switch (rank == null ? "" : rank.trim().toUpperCase()) {
            case "S" -> 340;
            case "A" -> 220;
            case "B" -> 140;
            case "C" -> 90;
            default  -> 50; // D and anything unset/unknown
        };
    }

    private void validateGitHubRepo(User user, MissionSubmission current, ParsedRepo parsed, String repoKey) {
        if (user.getGithubId() == null || user.getGithubId().isBlank())
            throw new IllegalArgumentException(
                    "Connect your GitHub account on this page before adding a repository.");
        if (parsed == null)
            throw new IllegalArgumentException(
                    "Enter a GitHub repository link like https://github.com/your-username/project-name");

        String login = user.getGithubLogin();
        if (login == null || login.isBlank())
            throw new IllegalArgumentException(
                    "Connect your GitHub account on this page before adding a repository.");
        if (!parsed.owner().equalsIgnoreCase(login))
            throw new IllegalArgumentException(
                    "This repo must be under your connected GitHub account (@"
                            + login + "). Example: https://github.com/" + login + "/your-project");

        boolean sameRepo = current.getRepoKey() != null
                && current.getRepoKey().equalsIgnoreCase(repoKey);
        if (!sameRepo)
            assertRepoExistsOnGitHub(parsed.owner(), parsed.repo());

        Optional<MissionSubmission> existing = submissionRepo.findFirstByRepoKeyIgnoreCase(repoKey);
        if (existing.isPresent()) {
            MissionSubmission other = existing.get();
            String currentId = current.getId();
            if (currentId == null || !other.getId().equals(currentId))
                throw new IllegalArgumentException(
                        "This repository is already linked to another mission. Use a different repo for each mission.");
        }
    }

    /** Public GitHub API check — confirms the repo exists (public repos only without OAuth). */
    private void assertRepoExistsOnGitHub(String owner, String repo) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "LearnForEarn-ARISE")
                    .timeout(java.time.Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) {
                throw new IllegalArgumentException(
                        "We couldn't find that repository on GitHub. Check the spelling and repo name, "
                                + "or make sure the repository exists and is public.");
            }
            if (res.statusCode() == 403) {
                throw new IllegalArgumentException(
                        "We couldn't verify that repository right now. If it's private, make it public or try again shortly.");
            }
            if (res.statusCode() != 200) {
                throw new IllegalArgumentException(
                        "We couldn't verify that repository on GitHub. Please try again in a moment.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "We couldn't reach GitHub to verify your repository. Check your connection and try again.");
        }
    }

    private String repoKey(ParsedRepo parsed) {
        if (parsed == null) return null;
        return parsed.owner().toLowerCase() + "/" + parsed.repo().toLowerCase();
    }

    private String canonicalRepoUrl(ParsedRepo parsed) {
        return "https://github.com/" + parsed.owner() + "/" + parsed.repo();
    }

    private ParsedRepo parseGitHubRepo(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher m = GITHUB_REPO.matcher(raw.trim());
        if (!m.matches()) return null;
        String repo = m.group(2);
        if (repo.endsWith(".git")) repo = repo.substring(0, repo.length() - 4);
        return new ParsedRepo(m.group(1), repo);
    }

    private record ParsedRepo(String owner, String repo) {}

    /** Trim, validate as an http(s) URL, and enforce a length cap. Blank → null (clears the field). */
    private String clean(String raw, String label) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if (v.length() > MAX_URL)
            throw new IllegalArgumentException(label + " is too long (500 characters max).");
        if (!URL.matcher(v).matches())
            throw new IllegalArgumentException(label + " must start with http:// or https://");
        return v;
    }
}
