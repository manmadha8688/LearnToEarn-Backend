package com.example.student.config;

import com.example.student.model.*;
import com.example.student.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubjectRepository subjectRepository;
    private final ConceptRepository conceptRepository;
    private final QuestionRepository questionRepository;
    private final com.example.student.repository.UserConceptProgressRepository progressRepository;
    private final com.example.student.repository.QuizAttemptRepository attemptRepository;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      SubjectRepository subjectRepository, ConceptRepository conceptRepository,
                      QuestionRepository questionRepository,
                      com.example.student.repository.UserConceptProgressRepository progressRepository,
                      com.example.student.repository.QuizAttemptRepository attemptRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.subjectRepository = subjectRepository;
        this.conceptRepository = conceptRepository;
        this.questionRepository = questionRepository;
        this.progressRepository = progressRepository;
        this.attemptRepository = attemptRepository;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        
        if (subjectRepository.findAll().stream().noneMatch(s -> "Python Basics".equals(s.getTitle()))) {
            seedPythonBasics();
        }
        reconcile();
        repairConceptProgress();
        backfillUserStats();
        if (questionRepository.count() == 0) {
            seedQuestions();
        }
    }

    // ─── PUBLIC API (called by AdminController) ───────────────────────────────
    public void reconcileRichContent() {
        reconcile();
    }

    // ─── REPAIR ──────────────────────────────────────────────────────────────
    // Creates missing UserConceptProgress entries for any passed concept quiz attempts.
    // Fixes data where attempt.passed=true but no progress entry was created.
    private void repairConceptProgress() {
        List<com.example.student.model.QuizAttempt> passedAttempts = attemptRepository.findAll()
            .stream()
            .filter(a -> "CONCEPT".equals(a.getType()) && a.isPassed())
            .collect(Collectors.toList());

        for (com.example.student.model.QuizAttempt attempt : passedAttempts) {
            if (!progressRepository.existsByUserIdAndConceptId(attempt.getUserId(), attempt.getRefId())) {
                Optional<Concept> conceptOpt = conceptRepository.findById(attempt.getRefId());
                if (conceptOpt.isPresent()) {
                    Concept concept = conceptOpt.get();
                    UserConceptProgress progress = new UserConceptProgress();
                    progress.setUserId(attempt.getUserId());
                    progress.setConceptId(attempt.getRefId());
                    progress.setSubjectId(concept.getSubjectId());
                    progress.setSubjectTitle(concept.getSubjectTitle());
                    progress.setSubjectIcon(concept.getSubjectIcon());
                    // Use the actual quiz completion time so isFirstToday works correctly
                    progress.setCompletedAt(attempt.getTakenAt());
                    progressRepository.save(progress);
                }
            }
        }
    }

    // ─── ADMIN ───────────────────────────────────────────────────────────────
    private void seedAdmin() {
        userRepository.findByEmail("admin@demo.com").ifPresentOrElse(
            u -> { if (!Boolean.TRUE.equals(u.getIsActive())) { u.setIsActive(true); userRepository.save(u); } },
            () -> {
                User admin = new User();
                admin.setFullName("Admin");
                admin.setEmail("admin@demo.com");
                admin.setPassword(passwordEncoder.encode("Admin@123"));
                admin.setRole("ADMIN");
                admin.setCollegeName("Platform");
                admin.setAvatarColor("#4F46E5");
                admin.setIsActive(true);
                userRepository.save(admin);
            }
        );
    }

    // ─── RECONCILE ────────────────────────────────────────────────────────────
    private void reconcile() {
        List<Subject> subjects = subjectRepository.findAll();

        List<Subject> subjectsToSave = subjects.stream()
            .filter(s -> {
                int actual = (int) conceptRepository.countBySubjectId(s.getId());
                if (s.getTotalConcepts() != actual) { s.setTotalConcepts(actual); return true; }
                return false;
            }).collect(Collectors.toList());
        if (!subjectsToSave.isEmpty()) subjectRepository.saveAll(subjectsToSave);

        List<Subject> nullRankSubjects = subjects.stream()
            .filter(s -> s.getRank() == null)
            .peek(s -> s.setRank("E"))
            .collect(Collectors.toList());
        if (!nullRankSubjects.isEmpty()) subjectRepository.saveAll(nullRankSubjects);

        List<Concept> nullRankConcepts = conceptRepository.findAll().stream()
            .filter(c -> c.getRank() == null)
            .peek(c -> c.setRank("E"))
            .collect(Collectors.toList());
        if (!nullRankConcepts.isEmpty()) conceptRepository.saveAll(nullRankConcepts);

        Map<String, String> cssConceptRanks = Map.ofEntries(
            Map.entry("How CSS Works",                      "D"),
            Map.entry("CSS Selectors",                      "D"),
            Map.entry("CSS Specificity and Cascade",        "C"),
            Map.entry("CSS Units",                          "C"),
            Map.entry("Box Model",                          "C"),
            Map.entry("Display Properties",                 "C"),
            Map.entry("CSS Positioning",                    "B"),
            Map.entry("z-index and Stacking Context",       "B"),
            Map.entry("CSS Pseudo-classes and Pseudo-elements", "B"),
            Map.entry("CSS Transitions",                    "B"),
            Map.entry("CSS Keyframes and Animations",       "B"),
            Map.entry("Flexbox",                            "A"),
            Map.entry("CSS Grid",                           "A"),
            Map.entry("Responsive Design",                  "A"),
            Map.entry("CSS Variables",                      "B")
        );

        subjectRepository.findAll().stream()
            .filter(s -> "CSS Fundamentals".equals(s.getTitle()))
            .findFirst()
            .ifPresent(css -> {
                if (!"A".equals(css.getRank())) { css.setRank("A"); subjectRepository.save(css); }
                List<Concept> toUpdate = conceptRepository.findBySubjectIdOrderByOrderIndex(css.getId())
                    .stream()
                    .filter(c -> {
                        String intended = cssConceptRanks.get(c.getTitle());
                        if (intended != null && !intended.equals(c.getRank())) { c.setRank(intended); return true; }
                        return false;
                    }).collect(Collectors.toList());
                if (!toUpdate.isEmpty()) conceptRepository.saveAll(toUpdate);
            });
    }

    // ─── BACKFILL USER STATS ─────────────────────────────────────────────────
    private void backfillUserStats() {
        // Preserve quiz-score XP (score*10 + daily bonus). Only use the 50-per-concept
        // baseline for users who have never earned any XP (new installs / fresh docs).
        List<com.example.student.model.User> toSave = new java.util.ArrayList<>();
        userRepository.findAll().forEach(user -> {
            if ("ADMIN".equals(user.getRole())) return;
            long completed = progressRepository.countByUserId(user.getId());
            long xp = (user.getXp() > 0) ? user.getXp() : completed * 50L;
            int  level = Math.max(1, (int)(xp / 200));
            String rank;
            if      (xp >= 10000) rank = "S";
            else if (xp >= 6000)  rank = "A";
            else if (xp >= 3000)  rank = "B";
            else if (xp >= 1500)  rank = "C";
            else if (xp >= 500)   rank = "D";
            else                  rank = "E";
            user.setXp(xp);
            user.setLevel(level);
            user.setRank(rank);
            toSave.add(user);
        });
        if (!toSave.isEmpty()) userRepository.saveAll(toSave);
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private Subject sub(String title, String desc, String icon, String color, String rank) {
        Subject s = new Subject();
        s.setTitle(title); s.setDescription(desc); s.setIcon(icon);
        s.setColor(color); s.setRank(rank); s.setTotalConcepts(0);
        return s;
    }

    private Concept conceptRich(Subject subject, String title,
            String intro, String simple, String technical, String syntax,
            List<Concept.ConceptExample> examples, List<String> keyPoints,
            String tip, List<String> mistakes, int minutes, int order, String rank) {
        Concept c = new Concept();
        c.setSubjectId(subject.getId());
        c.setSubjectTitle(subject.getTitle());
        c.setSubjectIcon(subject.getIcon());
        c.setTitle(title);
        c.setIntroduction(intro);
        c.setExplanationSimple(simple);
        c.setExplanationTechnical(technical);
        c.setSyntax(syntax);
        c.setExamples(examples);
        c.setKeyPoints(keyPoints);
        c.setTip(tip);
        c.setCommonMistakes(mistakes);
        c.setEstimatedMinutes(minutes);
        c.setOrderIndex(order);
        c.setRank(rank != null ? rank : "E");
        return c;
    }

 

    

    }