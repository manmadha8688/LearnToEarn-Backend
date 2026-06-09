package com.example.student.config;

import com.example.student.model.Concept;
import com.example.student.model.ProblemQuestion;
import com.example.student.model.Question;
import com.example.student.model.Roadmap;
import com.example.student.model.User;
import com.example.student.repository.ConceptRepository;
import com.example.student.repository.ProblemRepository;
import com.example.student.repository.QuestionRepository;
import com.example.student.repository.RoadmapRepository;
import com.example.student.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProblemRepository problemRepository;
    private final RoadmapRepository roadmapRepository;
    private final QuestionRepository questionRepository;
    private final ConceptRepository conceptRepository;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      ProblemRepository problemRepository,
                      RoadmapRepository roadmapRepository,
                      QuestionRepository questionRepository,
                      ConceptRepository conceptRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.problemRepository = problemRepository;
        this.roadmapRepository = roadmapRepository;
        this.questionRepository = questionRepository;
        this.conceptRepository = conceptRepository;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        cleanupLegacyGuests();
        seedRoadmapRichInfo();
        if (problemRepository.count() < 10) {
            problemRepository.deleteAll();
            seedProblems();
        }
        
    }

    public void reconcileRichContent() { }

    // ─── Guest Cleanup ───────────────────────────────────────────────────────
    // One-time migration: delete legacy guest accounts created before activity tracking
    private void cleanupLegacyGuests() {
        List<User> legacyGuests = userRepository.findByRole("GUEST").stream()
            .filter(u -> u.getLastLoginAt() == null)
            .collect(java.util.stream.Collectors.toList());
        if (!legacyGuests.isEmpty()) {
            userRepository.deleteAll(legacyGuests);
            System.out.println("[GuestCleanup] Removed " + legacyGuests.size() + " legacy guest account(s)");
        }
    }

    // ─── Admin ───────────────────────────────────────────────────────────────
    private void seedAdmin() {
        userRepository.findByEmail("admin@demo.com").ifPresentOrElse(
            u -> { if (!Boolean.TRUE.equals(u.getIsActive())) { u.setIsActive(true); userRepository.save(u); } },
            () -> {
                User a = new User();
                a.setFullName("Admin"); a.setEmail("admin@demo.com");
                a.setPassword(passwordEncoder.encode("***REMOVED***"));
                a.setRole("ADMIN"); a.setCollegeName("Platform");
                a.setAvatarColor("#4F46E5"); a.setIsActive(true);
                userRepository.save(a);
            }
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private ProblemQuestion.SolutionCode sc(String c, String py, String java, String cpp) {
        return new ProblemQuestion.SolutionCode(c, py, java, cpp);
    }
    private ProblemQuestion.SolutionVariant sv(String logic, String tc, String sc, ProblemQuestion.SolutionCode code) {
        return new ProblemQuestion.SolutionVariant(logic, tc, sc, code);
    }
    private ProblemQuestion.Solutions sols(ProblemQuestion.SolutionVariant b, ProblemQuestion.SolutionVariant n, ProblemQuestion.SolutionVariant o) {
        return new ProblemQuestion.Solutions(b, n, o);
    }
    private ProblemQuestion.Solutions same(ProblemQuestion.SolutionVariant v) {
        return new ProblemQuestion.Solutions(v, v, v);
    }

    private ProblemQuestion base(List<String> tracks, List<String> topics, String cat,
            String level, String type, String title, int order) {
        ProblemQuestion p = new ProblemQuestion();
        p.setTracks(tracks); p.setTopics(topics); p.setCategory(cat);
        p.setLevel(level); p.setType(type); p.setTitle(title); p.setOrderIndex(order);
        return p;
    }

    // ─── Entry point ─────────────────────────────────────────────────────────
    private void seedProblems() {
        List<ProblemQuestion> all = new java.util.ArrayList<>();
        
        problemRepository.saveAll(all);
        System.out.println("✅ " + all.size() + " problem questions seeded");
    }


    // ─── Roadmap Rich Info ────────────────────────────────────────────────────
    // Patches existing roadmaps with overview/tools/outcomes — never creates/deletes
    private void seedRoadmapRichInfo() {
        roadmapRepository.findAll().forEach(r -> {
            if (r.getRoleTargets() != null && !r.getRoleTargets().isEmpty()) return; // already seeded

            boolean changed = false;
            String t = r.getTitle() != null ? r.getTitle().toLowerCase() : "";

            if (t.contains("python")) {
                r.setRoleTargets(List.of(
                    "Python Full Stack Developer",
                    "Python Backend Developer",
                    "Django / Flask Developer",
                    "API Developer",
                    "Software Engineer — Python"
                ));
                r.setOverview("A complete path from Python basics to deploying full-stack web applications. You will learn Python programming, web frameworks, databases, REST APIs, and cloud deployment — everything needed to become a Python full-stack developer.");
                r.setWhyLearn("Python is the most in-demand language in 2024. Used in web development, data science, AI, and automation. Companies like Google, Instagram, and Netflix run on Python. One language opens multiple career doors.");
                r.setForWho("Students who want to build web applications using Python. Ideal for freshers targeting backend or full-stack roles in product and service companies.");
                r.setPrerequisites(List.of(
                    "Basic computer usage",
                    "Understanding of how the internet works",
                    "Basic math and logical thinking"
                ));
                r.setToolsRequired(List.of(
                    "VS Code",
                    "Python 3.10+",
                    "Git and GitHub",
                    "Postman (API testing)",
                    "MySQL or PostgreSQL"
                ));
                r.setOutcomes(List.of(
                    "Build full-stack Python web applications",
                    "Work with relational and non-relational databases",
                    "Create and consume REST APIs",
                    "Write clean, production-ready Python code",
                    "Deploy applications to cloud platforms",
                    "Clear Python developer interviews"
                ));
                changed = true;
            } else if (t.contains("java")) {
                r.setRoleTargets(List.of(
                    "Java Full Stack Developer",
                    "Java Backend Developer",
                    "Spring Boot Developer",
                    "Software Engineer — Java",
                    "Enterprise Java Developer"
                ));
                r.setOverview("A structured path from Java core concepts to building enterprise-grade full-stack applications. Covers Java, Spring Boot, databases, and deployment — the stack used by TCS, Infosys, Wipro, and most Indian IT companies.");
                r.setWhyLearn("Java is the backbone of enterprise software in India. TCS, Infosys, Wipro, Capgemini, and almost all service-based companies use Java. Spring Boot is the number one backend framework in enterprise. Mastering this stack opens doors to lakhs of openings.");
                r.setForWho("Students targeting service-based companies (TCS, Infosys, Wipro, Capgemini) or product companies using Java. Perfect for freshers wanting a strong, stable, and high-paying career in software.");
                r.setPrerequisites(List.of(
                    "Basic programming knowledge (any language)",
                    "Understanding of OOP concepts",
                    "HTML and CSS basics"
                ));
                r.setToolsRequired(List.of(
                    "VS Code or IntelliJ IDEA",
                    "JDK 17+",
                    "Maven or Gradle",
                    "Git and GitHub",
                    "Postman",
                    "MySQL Workbench"
                ));
                r.setOutcomes(List.of(
                    "Build enterprise Java applications with Spring Boot",
                    "Design and work with relational databases",
                    "Create secure REST APIs with JWT authentication",
                    "Write unit and integration tests",
                    "Deploy applications to cloud platforms",
                    "Clear Java developer interviews at major companies"
                ));
                changed = true;
            }

            if (changed) {
                roadmapRepository.save(r);
                System.out.println("✅ Roadmap rich info seeded — " + r.getTitle());
            }
        });
    }



}
