package com.example.student.service;

import com.example.student.model.Education;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.security.UserDetailsServiceImpl;
import org.springframework.stereotype.Service;

/**
 * Awards one-time "profile completion" XP. Each item (personal, education, the three
 * social links, and the featured resume) grants XP the FIRST time it is observed complete
 * and never again — the per-item flags on {@link User} are the ledger. XP is never reversed
 * when a field is cleared or a link disconnected.
 *
 * <p>Called after a profile save ({@code ProfileService}) and after a GitHub connect
 * ({@code GitHubLinkService}). The award is batched onto the same {@link User} and persisted
 * here, then the read caches (progress summary, hunter stats) and the auth-lookup cache are
 * evicted so the new XP/rank shows immediately and a stale principal can't double-award.
 */
@Service
public class ProfileXpService {

    // Per-item XP. Resume is the most valuable (a real, recruiter-facing artifact);
    // each social link is rewarded separately; personal/education are quick wins.
    static final int PERSONAL_XP  = 25;  // fullName + bio + location + contact email
    static final int EDUCATION_XP = 30;  // degree + fieldOfStudy + institution + years + cgpa
    static final int GITHUB_XP    = 20;  // GitHub connected
    static final int LINKEDIN_XP  = 20;  // LinkedIn link saved
    static final int PORTFOLIO_XP = 20;  // Portfolio link saved
    static final int RESUME_XP    = 60;  // a resume created and featured on the public profile

    private final UserRepository userRepository;
    private final ProgressService progressService;
    private final CacheService cacheService;
    private final UserDetailsServiceImpl userDetailsService;

    public ProfileXpService(UserRepository userRepository,
                            ProgressService progressService,
                            CacheService cacheService,
                            UserDetailsServiceImpl userDetailsService) {
        this.userRepository = userRepository;
        this.progressService = progressService;
        this.cacheService = cacheService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Grant XP for any profile item that just became complete. Mutates + persists {@code user}
     * only when something was awarded, and returns the total XP granted this call (0 if none).
     */
    public int applyAwards(User user) {
        if (user == null || "GUEST".equals(user.getRole())) return 0;

        int gained = 0;
        if (!user.isPersonalXpAwarded() && personalComplete(user)) {
            gained += PERSONAL_XP;
            user.setPersonalXpAwarded(true);
        }
        if (!user.isEducationXpAwarded() && educationComplete(user.getEducation())) {
            gained += EDUCATION_XP;
            user.setEducationXpAwarded(true);
        }
        if (!user.isGithubXpAwarded() && hasText(user.getGithubId())) {
            gained += GITHUB_XP;
            user.setGithubXpAwarded(true);
        }
        if (!user.isLinkedinXpAwarded() && hasText(user.getLinkedinUrl())) {
            gained += LINKEDIN_XP;
            user.setLinkedinXpAwarded(true);
        }
        if (!user.isPortfolioXpAwarded() && hasText(user.getPortfolioUrl())) {
            gained += PORTFOLIO_XP;
            user.setPortfolioXpAwarded(true);
        }
        if (!user.isResumeXpAwarded() && hasText(user.getFeaturedResumeId())) {
            gained += RESUME_XP;
            user.setResumeXpAwarded(true);
        }

        if (gained > 0) {
            progressService.applyXp(user, gained);
            userRepository.save(user);
            cacheService.evict("progress", "summary:" + user.getId());
            cacheService.evict("hunterStats", user.getId());
            cacheService.evict("dashboardBootstrap", user.getId());
            // Drop the cached auth principal so the next request reloads fresh flags and
            // can't award the same item twice within the lookup cache window.
            userDetailsService.evict(user.getUsername());
        }
        user.setXpEarned(gained);
        return gained;
    }

    // Contact email counts as chosen when the user opted into "same as login" OR set a
    // (verified, per ProfileService) public email.
    private boolean personalComplete(User u) {
        boolean emailChosen = Boolean.TRUE.equals(u.getUseLoginEmailForContact()) || hasText(u.getPublicEmail());
        return hasText(u.getFullName()) && hasText(u.getBio()) && hasText(u.getLocation()) && emailChosen;
    }

    private boolean educationComplete(Education e) {
        return e != null
                && hasText(e.getDegree()) && hasText(e.getFieldOfStudy()) && hasText(e.getInstitution())
                && hasText(e.getYears()) && hasText(e.getCgpa());
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
