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
        if (subjectRepository.findAll().stream().noneMatch(s -> "CSS Fundamentals".equals(s.getTitle()))) {
            seedCssFundamentals();
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

    // ─── CSS FUNDAMENTALS ─────────────────────────────────────────────────────
    private void seedCssFundamentals() {
        Subject css = subjectRepository.save(sub(
            "CSS Fundamentals",
            "Style and design web pages — control colors, layouts, fonts, and animations",
            "🎨", "#264DE4", "A"
        ));

        List<Concept> concepts = List.of(

            // ── 1. How CSS Works ──────────────────────────────────────────
            conceptRich(css, "How CSS Works",
                "CSS connects to HTML in three ways — inline, internal, and external — and external stylesheets are always the correct choice for real projects.",
                "Think of HTML as a plain notebook and CSS as a designer who decorates it. You can ask the designer to decorate one specific word right there (inline — messy), write decoration instructions on the first page of the notebook (internal — better), or keep a separate design manual that applies to every notebook in the building (external — professional). External stylesheets are the design manual approach: one file, change it once, every page in your site updates instantly.",
                "CSS (Cascading Style Sheets) is applied to HTML via three methods. Inline: `style` attribute directly on an element — highest specificity, hardest to maintain, no reusability. Internal: `<style>` tag inside `<head>` — scoped to one HTML file. External: `<link rel=\"stylesheet\" href=\"style.css\">` — one file shared across all pages, cached by browsers after first load, easiest to maintain. The browser's rendering pipeline: parse HTML → build DOM → parse CSS → build CSSOM → combine into Render Tree → layout → paint. CSS rules: selector + declaration block containing property-value pairs. Comments: `/* comment */`.",
                "/* ── 1. Inline CSS (avoid) ── */\n<p style=\"color: red; font-size: 18px;\">Red text</p>\n\n/* ── 2. Internal CSS (single page only) ── */\n<head>\n  <style>\n    p {\n      color: blue;\n      font-size: 16px;\n    }\n  </style>\n</head>\n\n/* ── 3. External CSS (correct approach) ── */\n/* In HTML <head>: */\n<link rel=\"stylesheet\" href=\"styles.css\" />\n\n/* In styles.css: */\nbody {\n  font-family: Arial, sans-serif;\n  margin: 0;\n  padding: 0;\n  background-color: #f5f5f5;\n}\n\nh1 {\n  color: #264DE4;\n  font-size: 2rem;\n}\n\np {\n  color: #333;\n  line-height: 1.6;\n}",
                List.of(
                    new Concept.ConceptExample("External stylesheet — the correct way",
                        "Link an external CSS file to an HTML page.",
                        "<!-- index.html -->\n<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\" />\n  <title>My Page</title>\n  <link rel=\"stylesheet\" href=\"styles.css\" />\n</head>\n<body>\n  <h1>Hello World</h1>\n  <p>This is styled with external CSS.</p>\n</body>\n</html>\n\n/* styles.css */\nbody {\n  font-family: 'Segoe UI', sans-serif;\n  background: #f0f4f8;\n  margin: 0;\n  padding: 20px;\n}\n\nh1 {\n  color: #264DE4;\n  border-bottom: 3px solid #264DE4;\n  padding-bottom: 8px;\n}\n\np {\n  color: #555;\n  font-size: 1rem;\n  line-height: 1.6;\n}",
                        "Page renders with:\n- Light blue-gray background\n- Blue heading with underline\n- Gray paragraph text\n- Clean sans-serif font throughout"),
                    new Concept.ConceptExample("CSS rule anatomy",
                        "Break down a CSS rule into its parts.",
                        "/* Selector: targets which HTML elements */\nh1 {\n\n  /* Property: what to change */\n  /* Value:    what to change it to */\n  color: navy;          /* text color */\n  font-size: 2rem;      /* font size */\n  font-weight: bold;    /* thickness */\n  text-align: center;   /* alignment */\n  margin-bottom: 16px;  /* space below */\n\n  /* Shorthand properties */\n  border: 2px solid navy;  /* width style color */\n  padding: 8px 16px;       /* top/bottom left/right */\n}\n\n/* Multiple selectors, same styles */\nh1, h2, h3 {\n  font-family: Georgia, serif;\n}\n\n/* CSS comment */\n/* This entire block is a comment */",
                        "h1 elements render as:\n- Navy colored, 2rem size, bold, centered\n- 2px navy border all around\n- 8px top/bottom padding, 16px left/right padding\n- 16px space below"),
                    new Concept.ConceptExample("Inline vs internal vs external comparison",
                        "See why external is always preferred.",
                        "<!-- Inline: only affects this one element -->\n<p style=\"color: red;\">Red only here</p>\n\n<!-- Internal: affects all p on this page only -->\n<style>\n  p { color: green; }\n</style>\n\n<!-- External: affects all p on EVERY page -->\n<!-- <link rel=\"stylesheet\" href=\"styles.css\"> -->\n/* styles.css */\np { color: blue; }\n\n/* If you have 50 pages and need to change text color,\n   inline = edit 50 files line by line\n   internal = edit 50 files (one <style> per file)\n   external = edit ONE line in ONE file. Done. */",
                        "All three make paragraphs a different color.\nBut only external CSS scales to real projects.\nChange ONE .css file → every page on your site updates.")
                ),
                List.of(
                    "Always use external CSS for real projects — one .css file controls the entire site",
                    "Inline CSS has the highest specificity and is the hardest to override — avoid it except for quick tests",
                    "The <link> tag goes inside <head>, not inside <body>",
                    "Multiple CSS files can be linked: one for base styles, one for components, one for pages",
                    "Browsers cache external CSS after first load — makes every subsequent page visit faster"
                ),
                "Name your external CSS file `styles.css` or `main.css` and put it in a `css/` or `styles/` folder. Keep it separate from HTML files. When a browser loads your site, it downloads and caches the CSS file — every other page then loads instantly from cache instead of downloading the CSS again.",
                List.of(
                    "Putting <link> inside <body> instead of <head> — causes Flash Of Unstyled Content (FOUC) where the page briefly renders without CSS",
                    "Forgetting href in the link tag — <link rel=\"stylesheet\"> with no href silently fails",
                    "Using inline style for everything — impossible to maintain, impossible to override, no reusability",
                    "Putting CSS directly in HTML files instead of external files — you lose the caching benefit and have to update every page separately"
                ),
                20, 1, "D"),

            // ── 2. CSS Selectors ──────────────────────────────────────────
            conceptRich(css, "CSS Selectors",
                "CSS selectors target exactly which HTML elements to style — from single elements to complex combinations.",
                "A CSS selector is like an address for your postman. The more specific the address, the more precisely your letter (style) is delivered. 'Deliver to everyone' = element selector. 'Deliver to everyone named John' = class selector. 'Deliver to flat 4B specifically' = ID selector. You can also write addresses like 'deliver to any John living inside a blue house' — that's a descendant selector. Learning selectors is learning how to aim your CSS precisely.",
                "CSS selectors: **Basic** — element (`p`), class (`.card`), ID (`#header`), universal (`*`). **Combinator** — descendant (`div p`, all p inside div), child (`div > p`, direct children only), adjacent sibling (`h1 + p`, immediately after), general sibling (`h1 ~ p`, all after). **Attribute** — `[type=\"text\"]`, `[href^=\"https\"]` (starts with), `[href$=\".pdf\"]` (ends with), `[class*=\"btn\"]` (contains). **Pseudo-class** — `:hover`, `:focus`, `:nth-child(2n)`, `:not(.active)`. **Pseudo-element** — `::before`, `::after`, `::first-line`. Grouping: `h1, h2, h3 { }` applies same styles to multiple selectors.",
                "/* ── Basic selectors ── */\np { color: gray; }           /* all <p> elements */\n.card { padding: 16px; }     /* class=\"card\" */\n#header { height: 60px; }    /* id=\"header\" */\n* { box-sizing: border-box; } /* every element */\n\n/* ── Combinator selectors ── */\nnav a { color: white; }          /* all <a> inside <nav> */\nnav > a { font-weight: bold; }   /* direct <a> children of nav */\nh1 + p { font-size: 1.1rem; }   /* <p> immediately after <h1> */\nh1 ~ p { color: #555; }         /* all <p> siblings after <h1> */\n\n/* ── Attribute selectors ── */\ninput[type=\"email\"] { border-color: blue; }\na[href^=\"https\"] { color: green; }   /* starts with https */\na[href$=\".pdf\"] { color: red; }      /* ends with .pdf */\n[class*=\"btn\"] { cursor: pointer; }  /* class contains btn */\n\n/* ── Grouping ── */\nh1, h2, h3 {\n  font-family: Georgia, serif;\n  color: #1a1a1a;\n}",
                List.of(
                    new Concept.ConceptExample("Basic selectors in action",
                        "Style elements using element, class, and ID selectors.",
                        "/* CSS */\n/* Element: all paragraphs */\np {\n  font-size: 1rem;\n  line-height: 1.6;\n  color: #333;\n}\n\n/* Class: any element with class=\"highlight\" */\n.highlight {\n  background: yellow;\n  font-weight: bold;\n}\n\n/* ID: unique element with id=\"hero\" */\n#hero {\n  font-size: 3rem;\n  color: #264DE4;\n  text-align: center;\n}\n\n/* HTML */\n<h1 id=\"hero\">Welcome to CSS</h1>\n<p>This is a normal paragraph.</p>\n<p class=\"highlight\">This paragraph is highlighted.</p>\n<span class=\"highlight\">This span is also highlighted.</span>",
                        "Welcome to CSS  ← large, blue, centered (ID)\nThis is a normal paragraph.  ← gray, 1rem\nThis paragraph is highlighted.  ← yellow background, bold\nThis span is also highlighted.  ← same yellow, bold"),
                    new Concept.ConceptExample("Combinator selectors",
                        "Target elements based on their relationship to other elements.",
                        "/* HTML structure */\n<nav>\n  <a href=\"/\">Home</a>\n  <div>\n    <a href=\"/about\">About</a>  /* NOT a direct child of nav */\n  </div>\n</nav>\n\n<h2>Section Title</h2>\n<p>First paragraph after h2</p>\n<p>Second paragraph after h2</p>\n\n/* CSS */\n/* All <a> inside nav (includes nested) */\nnav a { color: white; text-decoration: none; }\n\n/* Only direct <a> children of nav */\nnav > a { font-weight: bold; }\n\n/* Only the <p> immediately after h2 */\nh2 + p { font-size: 1.1rem; font-weight: 500; }\n\n/* All <p> that come after h2 (siblings) */\nh2 ~ p { color: #555; }",
                        "nav a: Home link = white, About link = white (both styled)\nnav > a: Home link = bold (direct child), About link = NOT bold (nested)\nh2 + p: 'First paragraph' = 1.1rem, bold (adjacent)\nh2 ~ p: Both paragraphs = gray (general sibling)"),
                    new Concept.ConceptExample("Attribute selectors — practical uses",
                        "Target elements by their attribute values.",
                        "/* Style different input types differently */\ninput[type=\"text\"] {\n  border: 1px solid #ccc;\n  border-radius: 4px;\n}\n\ninput[type=\"submit\"] {\n  background: #264DE4;\n  color: white;\n  border: none;\n  cursor: pointer;\n}\n\n/* External links get an arrow icon */\na[href^=\"http\"] {\n  color: #264DE4;\n}\n\na[href^=\"http\"]::after {\n  content: ' ↗';\n  font-size: 0.75em;\n}\n\n/* PDF links turn red */\na[href$=\".pdf\"] {\n  color: red;\n}\n\na[href$=\".pdf\"]::before {\n  content: '📄 ';\n}",
                        "text input: gray border, rounded\nsubmit button: blue background, white text\nExternal links: blue with ↗ arrow after\nPDF links: red with 📄 icon before")
                ),
                List.of(
                    "Class selectors (.card) can be reused on many elements; ID selectors (#header) must be unique per page",
                    "Descendant (div p) selects ALL nested p; child (div > p) selects ONLY direct p children",
                    "Grouping (h1, h2, h3) applies the same styles to multiple selectors — saves repetition",
                    "Attribute selectors [attr^=val] (starts), [attr$=val] (ends), [attr*=val] (contains) are powerful for forms and links",
                    "Avoid over-qualifying selectors: .card p is better than div.container > section > .card > p"
                ),
                "Prefer class selectors over element selectors for styling. Element selectors (`p { color: gray }`) affect every paragraph on every page — that's often too broad. A class selector (`.body-text { color: gray }`) only affects what you explicitly mark. This gives you control without unexpected side effects.",
                List.of(
                    "Using IDs for styling — IDs have very high specificity and are hard to override. Use classes for styling, IDs for JavaScript and anchor links",
                    "Over-nested selectors: nav ul li a.link — if HTML changes, CSS breaks. Use a flat class instead: .nav-link",
                    "Forgetting the dot for class: p.card means a <p> with class card; .card means any element with class card",
                    "Using * (universal selector) for general resets without box-sizing — this is fine, but * with complex styles is a performance risk"
                ),
                25, 2, "D"),

            // ── 3. CSS Specificity and Cascade ────────────────────────────
            conceptRich(css, "CSS Specificity and Cascade",
                "Specificity is the scoring system CSS uses to decide which style wins when multiple rules target the same element.",
                "Imagine you call a restaurant to order food. Your friend calls too and orders something different for the same table. Who wins? The one with more authority wins. In CSS: inline style is the manager (beats everyone), ID is the senior staff, class is regular staff, and element is a new intern. When two rules have equal authority, the one written last wins — like the last instruction given to the intern. This whole system is the 'cascade' — styles flow down from multiple sources and the rules decide which one sticks.",
                "The **cascade** determines which CSS rule applies when multiple rules target the same element. Priority order (highest to lowest): 1. `!important` (avoid — nuclear option), 2. Inline styles (`style=\"\"`), 3. ID selectors (#id), 4. Class/attribute/pseudo-class selectors (.class, [attr], :hover), 5. Element/pseudo-element selectors (p, ::before). **Specificity score** (a, b, c): a = ID count, b = class/attribute/pseudo-class count, c = element count. Example: `#nav .link:hover` = (1,2,0) = 120. **Inheritance**: some properties (color, font-family, font-size) inherit from parent; others (margin, padding, border) do not. `inherit`, `initial`, `unset`, `revert` keywords control inheritance explicitly.",
                "/* Specificity scores shown as (ID, CLASS, ELEMENT) */\n\np              { color: black; }   /* (0,0,1) = 1   */\n.text          { color: blue;  }   /* (0,1,0) = 10  */\n#main          { color: green; }   /* (1,0,0) = 100 */\n\n/* Combinations */\ndiv p          { color: gray;  }   /* (0,0,2) = 2   */\n.card p        { color: navy;  }   /* (0,1,1) = 11  */\n.card .title   { color: teal;  }   /* (0,2,0) = 20  */\n#header .nav a { color: white; }   /* (1,1,1) = 111 */\n\n/* Same specificity: LAST one wins */\np { color: red; }\np { color: blue; }   /* wins — written later */\n\n/* !important: avoid but it overrides everything */\np { color: red !important; }   /* wins over everything */\n\n/* Inheritance */\nbody {\n  color: #333;        /* inherited by all children */\n  font-family: Arial; /* inherited by all children */\n  padding: 0;         /* NOT inherited */\n}\n\n/* Force inheritance */\nbutton { color: inherit; } /* takes parent's color */",
                List.of(
                    new Concept.ConceptExample("Specificity in action",
                        "See which rule wins when multiple styles target the same element.",
                        "/* CSS */\np { color: black; }           /* (0,0,1) specificity: 1  */\n.text { color: blue; }        /* (0,1,0) specificity: 10 */\n#intro { color: green; }      /* (1,0,0) specificity: 100 */\n\n/* HTML */\n<p>Black paragraph</p>\n<p class=\"text\">Blue paragraph</p>\n<p id=\"intro\" class=\"text\">Which color wins?</p>",
                        "Black paragraph  ← p rule wins (only rule)\nBlue paragraph   ← .text wins over p (10 > 1)\nWhich color wins? ← GREEN — #intro wins (100 > 10 > 1)"),
                    new Concept.ConceptExample("The cascade — order matters",
                        "When specificity is equal, the LAST rule written wins.",
                        "/* Both have same specificity: (0,1,0) = 10 */\n.button { background: blue; }\n.button { background: red; }   /* wins — comes after */\n\n/* Multiple stylesheets — later link wins */\n/* <link href=\"base.css\" />      loaded first */\n/* <link href=\"override.css\" />  loaded second — wins */\n\n/* base.css */\n.card { padding: 16px; border: 1px solid #ccc; }\n\n/* override.css */\n.card { border: 2px solid blue; } /* overrides base.css border */",
                        "Button shows RED background (last rule wins)\n\nCard has:\n  padding: 16px (from base.css — not overridden)\n  border: 2px solid blue (from override.css — wins)"),
                    new Concept.ConceptExample("Debugging specificity",
                        "How to fix CSS that is not applying as expected.",
                        "/* PROBLEM: you added a class but color doesn't change */\n#main-content p { color: black; }  /* (1,0,1) = 101 */\n.highlight { color: yellow; }       /* (0,1,0) = 10 — loses! */\n\n/* <p id=\"main-content\"> inside #main-content */\n/* The .highlight class (10) LOSES to #main-content p (101) */\n\n/* FIX 1: increase specificity of fix */\n#main-content p.highlight { color: yellow; }  /* (1,1,1) = 111 wins */\n\n/* FIX 2: restructure to avoid ID in CSS (better long-term) */\n.content-area p { color: black; }  /* (0,1,1) = 11 */\n.highlight { color: yellow; }       /* (0,1,0) = 10 — still loses */\n.content-area p.highlight { color: yellow; } /* (0,2,1) = 21 wins */\n\n/* FIX 3 (last resort): !important */\n.highlight { color: yellow !important; } /* wins over everything */",
                        "Understanding why your CSS isn't working:\n1. Open browser DevTools (F12)\n2. Click element\n3. See Styles panel — crossed-out rules lost the specificity battle\n4. The winning rule shows without strikethrough")
                ),
                List.of(
                    "Specificity score: ID=100, Class/Attribute/Pseudo-class=10, Element/Pseudo-element=1",
                    "When specificity is equal, the rule that appears LAST in the CSS wins",
                    "Inline styles always beat any selector (except !important) — avoid them for this reason",
                    "!important breaks the cascade and makes debugging extremely painful — avoid it except for utility classes",
                    "color and font-family are inherited by children; margin, padding, border are NOT inherited"
                ),
                "When your CSS isn't applying, open browser DevTools (F12), click the element, and look at the Styles panel. Crossed-out rules lost the specificity battle. The winning rule shows without strikethrough. This is the fastest way to debug CSS — never guess, always inspect.",
                List.of(
                    "Using !important to fix everything — it creates a specificity war that escalates endlessly and makes your CSS unmaintainable",
                    "Using ID selectors for styling — IDs have 10x the specificity of classes, making overrides difficult",
                    "Writing overly specific selectors (div.container > section > article p) — fragile and hard to override",
                    "Not understanding why CSS isn't applying — always check DevTools Styles panel before adding more CSS"
                ),
                25, 3, "C"),

            // ── 4. CSS Units ──────────────────────────────────────────────
            conceptRich(css, "CSS Units",
                "CSS units define measurements — px for fixed sizes, rem for scalable sizes, % for relative sizes, and vh/vw for viewport-based sizes.",
                "Think of CSS units like measuring tools. A ruler (px) measures a fixed distance that never changes — 100px is always 100px. A percentage (%) measures relative to something else — 50% of your parent box. A rem is like saying 'three times the base font size' — it scales if the user zooms. vh and vw measure the screen — 100vh means the full height of the browser window. Choosing the right unit is like choosing the right measuring tool for the job.",
                "**Absolute units**: `px` (pixels — device pixels, fixed). **Relative units**: `%` (percentage of parent), `em` (relative to element's own font-size — compounding), `rem` (relative to root `<html>` font-size — 1rem = 16px by default, consistent), `vw` (1% of viewport width), `vh` (1% of viewport height), `vmin` (1% of smaller viewport dimension), `vmax` (1% of larger dimension). **ch** (width of '0' character), **ex** (x-height). `rem` is preferred for font sizes and spacing because it respects user browser zoom settings. `px` is appropriate for borders, box shadows, and fine details. `%` is ideal for widths in fluid layouts. `vh`/`vw` are perfect for full-screen sections.",
                "/* ── Absolute ── */\n.box { width: 300px; height: 200px; } /* fixed, never changes */\n\n/* ── Percentage (relative to parent) ── */\n.child { width: 50%; }  /* half of parent's width */\n\n/* ── em (relative to element's font-size) ── */\n.parent { font-size: 16px; }\n.parent p { padding: 1em; }   /* = 16px */\n.parent h2 { font-size: 1.5em; padding: 1em; }\n/* h2 font-size = 24px, padding = 24px (compounds!) */\n\n/* ── rem (relative to html font-size — preferred) ── */\nhtml { font-size: 16px; }  /* default */\n.heading { font-size: 2rem; }    /* = 32px always */\n.subtext { font-size: 0.875rem; } /* = 14px always */\n.spacing { margin: 1.5rem; }     /* = 24px always */\n\n/* ── Viewport units ── */\n.hero { height: 100vh; }       /* full screen height */\n.sidebar { width: 25vw; }     /* 25% of window width */\n.banner { font-size: 5vw; }   /* scales with window */\n\n/* ── ch for input width ── */\ninput[type=\"text\"] { width: 30ch; } /* fits 30 characters */",
                List.of(
                    new Concept.ConceptExample("px vs rem for font sizes",
                        "Why rem is better than px for accessibility.",
                        "/* px: ignores user browser font size setting */\n.bad {\n  font-size: 14px; /* stays 14px even if user set browser to large */\n}\n\n/* rem: respects user preference */\nhtml { font-size: 16px; } /* browser default */\n\n.good {\n  font-size: 0.875rem; /* 14px normally */\n  /* but if user sets browser font to 20px,\n     this becomes 17.5px automatically */\n}\n\n/* Common rem scale */\nhtml  { font-size: 16px;    }\nh1    { font-size: 2.5rem;  }  /* 40px */\nh2    { font-size: 2rem;    }  /* 32px */\nh3    { font-size: 1.5rem;  }  /* 24px */\np     { font-size: 1rem;    }  /* 16px */\nsmall { font-size: 0.875rem; } /* 14px */",
                        "With rem: if user zooms browser or sets larger font,\nall rem values scale up proportionally.\nWith px: user zoom has no effect — accessibility failure."),
                    new Concept.ConceptExample("% and viewport units for layout",
                        "Use % for fluid widths and vh/vw for full-screen sections.",
                        "/* Full-screen hero section */\n.hero {\n  width: 100%;    /* full page width */\n  height: 100vh;  /* full browser window height */\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  background: #264DE4;\n}\n\n/* Fluid two-column layout */\n.container { width: 90%; max-width: 1200px; margin: 0 auto; }\n.sidebar   { width: 25%; }\n.main      { width: 75%; }\n\n/* Responsive font that scales with viewport */\n.hero-title {\n  font-size: clamp(1.5rem, 5vw, 4rem);\n  /* min: 1.5rem  |  preferred: 5vw  |  max: 4rem */\n}",
                        "hero: fills entire browser window\ncontainer: 90% wide, max 1200px, centered\nsidebar: 25% of container\nmain: 75% of container\nhero-title: scales between 24px and 64px based on screen width"),
                    new Concept.ConceptExample("em compounding problem and rem solution",
                        "Show why em compounds in nested elements and how rem avoids it.",
                        "/* em compounds — can cause unexpected sizes */\nhtml { font-size: 16px; }\n\n.parent  { font-size: 1.25em; }  /* 16 × 1.25 = 20px */\n.child   { font-size: 1.25em; }  /* 20 × 1.25 = 25px */\n.grandchild { font-size: 1.25em; } /* 25 × 1.25 = 31.25px! */\n\n/* rem doesn't compound — always relative to html */\n.parent     { font-size: 1.25rem; } /* always 20px */\n.child      { font-size: 1.25rem; } /* always 20px */\n.grandchild { font-size: 1.25rem; } /* always 20px */",
                        "em: parent=20px, child=25px, grandchild=31px (getting bigger!)\nrem: parent=20px, child=20px, grandchild=20px (always consistent)")
                ),
                List.of(
                    "Use rem for font sizes — it respects user browser zoom and accessibility settings",
                    "Use px for borders, shadows, and fine details that should never scale",
                    "Use % for widths in fluid layouts that should adapt to their container",
                    "Use vh/vw for full-screen sections and elements that should relate to the viewport",
                    "em compounds in nested elements — a grandchild with font-size:1.2em inside nested parents gets unexpectedly large"
                ),
                "Always use `clamp(min, preferred, max)` for responsive typography. `font-size: clamp(1rem, 4vw, 2.5rem)` means: never smaller than 16px, never larger than 40px, and scales smoothly in between. This replaces multiple media query breakpoints for font sizes with a single line.",
                List.of(
                    "Using px for all font sizes — px ignores the user's browser font size preference, breaking accessibility for visually impaired users",
                    "Not understanding that em is relative to the element's OWN font-size — this causes compounding in nested elements",
                    "Using vh for mobile heights without considering mobile browser bars — 100vh on mobile includes the browser's address bar, causing overflow",
                    "Mixing units inconsistently — if you use rem for spacing on one component, use rem everywhere for consistency"
                ),
                20, 4, "C"),

            // ── 5. Box Model ──────────────────────────────────────────────
            conceptRich(css, "Box Model",
                "Every HTML element is a rectangular box made of content, padding, border, and margin — the box model is the foundation of all CSS layout.",
                "Imagine a photo in a picture frame. The photo itself is the content. The white mat around the photo is the padding — it separates the photo from the frame. The wooden frame is the border. The gap between this frame and the next frame on the wall is the margin. Every HTML element works exactly like this: content inside, padding for breathing room, border around the edge, and margin for space between elements. Understanding this is understanding CSS layout.",
                "The box model consists of four layers from inside out: **content** (actual text/image), **padding** (space inside the border), **border** (visible edge), **margin** (space outside — transparent, shows parent background). Two models: `content-box` (default — width/height = content only; total = content + padding + border) and `border-box` (width/height includes padding and border — much more intuitive). `box-sizing: border-box` is universally applied via `* { box-sizing: border-box }`. Margin collapsing: vertical margins between adjacent elements collapse to the larger value. `outline` is like border but outside the box model — doesn't affect layout.",
                "/* Box model layers */\n.element {\n  /* Content */\n  width: 300px;\n  height: 150px;\n\n  /* Padding (inside the border) */\n  padding: 20px;            /* all sides */\n  padding: 10px 20px;       /* top/bottom left/right */\n  padding: 10px 15px 20px 15px; /* top right bottom left */\n\n  /* Border */\n  border: 2px solid #264DE4;\n  border-radius: 8px;       /* rounded corners */\n\n  /* Margin (outside the border) */\n  margin: 16px;             /* all sides */\n  margin: 0 auto;           /* top/bottom=0, left/right=auto (centers block) */\n}\n\n/* content-box (default): confusing math */\n/* total width = 300 + 20+20 padding + 2+2 border = 344px */\n.default { box-sizing: content-box; width: 300px; padding: 20px; border: 2px solid; }\n\n/* border-box: intuitive — width IS the total */\n/* total width = exactly 300px (padding and border fit inside) */\n.better { box-sizing: border-box; width: 300px; padding: 20px; border: 2px solid; }\n\n/* Universal reset — use this on every project */\n*, *::before, *::after {\n  box-sizing: border-box;\n}",
                List.of(
                    new Concept.ConceptExample("Visualising the box model",
                        "Apply all four box model layers to a card component.",
                        "/* CSS */\n* { box-sizing: border-box; }\n\n.card {\n  width: 320px;\n\n  /* Padding: space between content and border */\n  padding: 24px;\n\n  /* Border: visible edge */\n  border: 2px solid #264DE4;\n  border-radius: 12px;\n\n  /* Margin: space between this card and other elements */\n  margin: 16px;\n\n  /* Background fills content + padding area */\n  background: white;\n\n  /* Shadow outside the border (doesn't affect layout) */\n  box-shadow: 0 4px 12px rgba(0,0,0,0.1);\n}\n\n.card h2 { margin-top: 0; }\n\n/* HTML */\n<div class=\"card\">\n  <h2>Course Title</h2>\n  <p>Course description here.</p>\n</div>",
                        "Card: exactly 320px wide (border-box)\n24px of breathing room on all 4 sides inside\n2px blue border with rounded corners\n16px gap between this card and next element\nWhite background fills the padding area"),
                    new Concept.ConceptExample("content-box vs border-box",
                        "Why border-box is always better.",
                        "/* content-box (DEFAULT — confusing) */\n.content-box {\n  box-sizing: content-box; /* default */\n  width: 200px;\n  padding: 20px;\n  border: 2px solid;\n  /* ACTUAL total width = 200 + 40 + 4 = 244px */\n  /* Your box is BIGGER than what you set! */\n}\n\n/* border-box (BETTER — what you see is what you get) */\n.border-box {\n  box-sizing: border-box;\n  width: 200px;\n  padding: 20px;\n  border: 2px solid;\n  /* ACTUAL total width = exactly 200px */\n  /* Padding and border fit INSIDE the 200px */\n}\n\n/* Apply globally at start of every project */\n*, *::before, *::after {\n  box-sizing: border-box;\n  margin: 0;\n  padding: 0;\n}",
                        "content-box: set 200px, actual size 244px (surprise!)\nborder-box: set 200px, actual size 200px (predictable)\n\nborder-box makes layout math simple:\n3 cards × 200px = 600px. Always. No surprises."),
                    new Concept.ConceptExample("Margin auto for centering and collapse",
                        "Center elements with margin auto and understand margin collapse.",
                        "/* Center a block element horizontally */\n.container {\n  width: 800px;\n  max-width: 100%; /* responsive */\n  margin: 0 auto; /* top/bottom=0, left/right=auto = centered */\n}\n\n/* Margin collapse: two vertical margins meet,\n   the LARGER one wins (they don't add together) */\n.first-box  { margin-bottom: 32px; }\n.second-box { margin-top: 16px; }\n/* Gap between them = 32px (NOT 48px) */\n\n/* Margin collapse DOESN'T happen for: */\n/* - horizontal margins */\n/* - flex/grid children */\n/* - elements with overflow: hidden */\n/* - elements with position: absolute/fixed */",
                        "container: perfectly centered on the page\n\nfirst-box → 32px gap → second-box\n(NOT 48px — margins collapsed to the larger value)")
                ),
                List.of(
                    "Always start every CSS project with: *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }",
                    "border-box: the width you set IS the total width — padding and border fit inside",
                    "margin: 0 auto centers a block element horizontally (requires a defined width)",
                    "Background color fills the content + padding area but NOT the margin",
                    "Vertical margins between sibling elements collapse to the larger value — they do not add"
                ),
                "Set `box-sizing: border-box` globally before writing any other CSS. Without it, adding padding to a 100% width element makes it overflow its parent. This is the single most common beginner CSS bug and `box-sizing: border-box` eliminates it entirely.",
                List.of(
                    "Forgetting box-sizing: border-box — setting width: 100% then adding padding causes the element to overflow its container",
                    "Using margin when you need padding (or vice versa) — padding adds space inside the border, margin adds space outside",
                    "Trying to use margin-top on inline elements (span, a) — inline elements ignore top/bottom margins, use padding instead",
                    "Expecting vertical margins to add up — margin collapse means 30px + 20px = 30px gap, not 50px"
                ),
                25, 5, "C"),

            // ── 6. Display Properties ─────────────────────────────────────
            conceptRich(css, "Display Properties",
                "The display property controls how an element participates in layout — block, inline, inline-block, flex, grid, and none are the most important values.",
                "Every HTML element is either a block or an inline element by default. Block elements (like paragraphs and divs) are like bricks — they always start on a new line and take up the full width. Inline elements (like spans and links) are like words in a sentence — they sit side by side and only take up as much space as their content. inline-block is a hybrid — it sits inline like a word but respects width and height like a brick. flex and grid are superpowers that change how children arrange themselves.",
                "`display` controls two things: the outer formatting context (how the element participates in the parent layout) and the inner formatting context (how its children lay out). Values: `block` — starts on new line, full width, respects width/height/margin. `inline` — flows in text, ignores width/height/vertical margin. `inline-block` — flows inline but respects all box model properties. `none` — removes element from layout entirely (different from `visibility: hidden` which keeps the space). `flex` — enables flexbox layout for children. `grid` — enables grid layout for children. `inline-flex`/`inline-grid` — flex/grid but as inline element. `table`, `table-cell`, etc. for table-like layout without HTML tables.",
                "/* Block: full width, new line */\ndiv   { display: block; }  /* default */\np     { display: block; }  /* default */\n\n/* Inline: flows with text, no width/height */\nspan  { display: inline; } /* default */\na     { display: inline; } /* default */\nstrong { display: inline; } /* default */\n\n/* inline-block: inline positioning + box model */\n.badge {\n  display: inline-block;\n  width: 80px;\n  height: 24px;\n  padding: 2px 8px;\n  background: #264DE4;\n  color: white;\n  border-radius: 12px;\n}\n\n/* none: removes from layout + visual rendering */\n.hidden { display: none; }\n\n/* visibility: hidden keeps the space */\n.invisible { visibility: hidden; }\n\n/* flex and grid (covered in their own concepts) */\n.flex-container { display: flex; }\n.grid-container { display: grid; }",
                List.of(
                    new Concept.ConceptExample("Block vs inline vs inline-block",
                        "See the visual difference between the three basic display values.",
                        "/* CSS */\n.block-el {\n  display: block;\n  background: #e0f0ff;\n  padding: 8px;\n  margin-bottom: 8px;\n}\n\n.inline-el {\n  display: inline;\n  background: #fff0e0;\n  padding: 8px;\n  /* width and height are IGNORED for inline */\n}\n\n.inline-block-el {\n  display: inline-block;\n  background: #e0ffe0;\n  padding: 8px;\n  width: 120px;  /* this WORKS for inline-block */\n  height: 40px;  /* this WORKS for inline-block */\n}\n\n/* HTML */\n<div class=\"block-el\">Block 1</div>\n<div class=\"block-el\">Block 2</div>\n<span class=\"inline-el\">Inline 1</span>\n<span class=\"inline-el\">Inline 2</span>\n<span class=\"inline-block-el\">Inl-Block 1</span>\n<span class=\"inline-block-el\">Inl-Block 2</span>",
                        "Block 1  ← full width, own line\nBlock 2  ← full width, own line\nInline 1 Inline 2  ← side by side like words\n[120×40] [120×40]  ← side by side, fixed size"),
                    new Concept.ConceptExample("display none vs visibility hidden",
                        "Understand the difference — one removes space, one keeps it.",
                        "<div class=\"box\">Box One</div>\n<div class=\"gone\">Box Two (display none)</div>\n<div class=\"box\">Box Three</div>\n\n<div class=\"box\">Box A</div>\n<div class=\"invisible\">Box B (visibility hidden)</div>\n<div class=\"box\">Box C</div>\n\n/* CSS */\n.box        { background: #264DE4; color: white; padding: 10px; margin: 4px; }\n.gone       { display: none; }       /* removed from layout */\n.invisible  { visibility: hidden; }  /* invisible but SPACE remains */",
                        "Row 1: Box One  Box Three  ← Two is completely gone, no gap\nRow 2: Box A  [gap]  Box C  ← B is invisible but space remains"),
                    new Concept.ConceptExample("Converting inline elements to block for layout",
                        "Change display to make inline elements behave as blocks.",
                        "/* Nav links are inline by default */\n/* To stack them vertically: */\nnav a {\n  display: block;  /* each link gets its own line */\n  padding: 12px 16px;\n  color: white;\n  text-decoration: none;\n  background: #1a1a2e;\n  border-bottom: 1px solid #444;\n}\n\nnav a:hover {\n  background: #264DE4;\n}\n\n/* To make images responsive: */\nimg {\n  display: block;    /* removes bottom gap caused by inline baseline */\n  max-width: 100%;   /* never wider than container */\n  height: auto;      /* maintain aspect ratio */\n}",
                        "Navigation links stack vertically:\n[Home        ]\n[About       ]\n[Contact     ]\n\nImage fills its container width and never overflows.")
                ),
                List.of(
                    "block elements: new line, full width, respect all box model properties",
                    "inline elements: flow with text, ignore width/height/top-bottom margin",
                    "inline-block: sits inline but respects all box model properties — use for buttons, badges, nav items",
                    "display: none removes the element and its space; visibility: hidden hides it but keeps the space",
                    "display: flex and display: grid are the modern layout tools — covered in Flexbox and Grid concepts"
                ),
                "Always set `display: block` and `max-width: 100%` on images. By default images are `inline` — this creates a small gap at the bottom caused by the text baseline. Changing to block eliminates that gap and `max-width: 100%` makes every image responsive automatically.",
                List.of(
                    "Trying to set width/height on inline elements — span and a ignore these. Use inline-block or block instead",
                    "Confusing display:none (removes from layout) with visibility:hidden (keeps space) — they behave very differently",
                    "Not knowing that block elements are 100% of parent width by default — no need to set width:100% on a div",
                    "Forgetting that changing display also affects child layout — display:flex makes the element a flex container for its children"
                ),
                20, 6, "C"),

            // ── 7. CSS Positioning ────────────────────────────────────────
            conceptRich(css, "CSS Positioning",
                "The position property controls where an element is placed — static follows normal flow, relative shifts from normal, absolute pins to a parent, fixed sticks to the viewport, sticky is a hybrid.",
                "Normal document flow is like standing in a queue. Everyone stands in order, taking up space. Static is the default — you stand in the queue. Relative is shifting slightly without leaving your spot — you lean left 10px but your space in the queue is still reserved. Absolute is stepping OUT of the queue — you go stand exactly where you want and everyone else acts like you're not there. Fixed is floating above everyone attached to the ceiling — you stay visible no matter how far they queue extends. Sticky is standing in the queue but once you reach the top of the screen, you stick there.",
                "`position` values: `static` (default — normal flow, `top/right/bottom/left/z-index` have no effect), `relative` (offset from own normal position with `top/right/bottom/left`, original space preserved), `absolute` (removed from flow, positioned relative to nearest non-static ancestor), `fixed` (removed from flow, positioned relative to viewport — stays during scroll), `sticky` (hybrid — in flow until threshold, then fixed). The **containing block** for absolute elements is the nearest ancestor with `position: relative/absolute/fixed/sticky` (or the viewport). Always add `position: relative` to a parent when using `position: absolute` on a child. `inset: 0` is shorthand for `top: 0; right: 0; bottom: 0; left: 0`.",
                "/* Static (default) */\n.normal { position: static; } /* top/left have no effect */\n\n/* Relative: offset from own position */\n.shifted {\n  position: relative;\n  top: 20px;   /* move 20px down from where it would be */\n  left: 10px;  /* move 10px right */\n  /* original space still reserved in flow */\n}\n\n/* Absolute: position relative to nearest positioned ancestor */\n.parent {\n  position: relative; /* makes this the containing block */\n  width: 400px;\n  height: 300px;\n}\n\n.badge {\n  position: absolute;\n  top: 8px;\n  right: 8px;  /* top-right corner of .parent */\n}\n\n/* Fixed: stays during scroll */\n.navbar {\n  position: fixed;\n  top: 0;\n  left: 0;\n  right: 0;\n  height: 60px;\n  z-index: 100;\n}\n\n/* Sticky: in flow until threshold */\n.section-header {\n  position: sticky;\n  top: 60px; /* sticks 60px from top (below fixed navbar) */\n}",
                List.of(
                    new Concept.ConceptExample("Absolute badge on a card",
                        "Place a badge in the corner of a card using absolute positioning.",
                        "/* CSS */\n* { box-sizing: border-box; }\n\n.card {\n  position: relative; /* containing block for badge */\n  width: 280px;\n  padding: 24px;\n  border: 1px solid #ddd;\n  border-radius: 12px;\n  margin: 20px;\n}\n\n.badge {\n  position: absolute;\n  top: -10px;    /* 10px above the card top */\n  right: 16px;   /* 16px from card right edge */\n  background: #264DE4;\n  color: white;\n  padding: 2px 10px;\n  border-radius: 99px;\n  font-size: 0.75rem;\n  font-weight: bold;\n}\n\n/* HTML */\n<div class=\"card\">\n  <span class=\"badge\">NEW</span>\n  <h3>JavaScript Course</h3>\n  <p>Learn JS from scratch.</p>\n</div>",
                        "Card with:\n[NEW] badge floating in top-right, half above the card\nTitle and description inside the card\nBadge does not affect the card's layout"),
                    new Concept.ConceptExample("Fixed navigation bar",
                        "A navbar that stays visible while scrolling.",
                        "/* CSS */\n.navbar {\n  position: fixed;\n  top: 0;\n  left: 0;\n  right: 0;       /* span full width */\n  height: 64px;\n  background: #1a1a2e;\n  display: flex;\n  align-items: center;\n  padding: 0 24px;\n  z-index: 1000;  /* above all other content */\n  box-shadow: 0 2px 8px rgba(0,0,0,0.2);\n}\n\n/* Add top padding to body to prevent content hiding under navbar */\nbody {\n  padding-top: 64px;\n}\n\n.navbar a {\n  color: white;\n  text-decoration: none;\n  margin-right: 24px;\n}",
                        "Navbar stays at top of screen during scroll.\nContent starts below the navbar (padding-top: 64px).\nnavbar z-index: 1000 keeps it above dropdowns and modals."),
                    new Concept.ConceptExample("Sticky section headers",
                        "Section headings that stick when scrolled to.",
                        "/* CSS */\nbody { padding-top: 64px; } /* for fixed navbar */\n\n.section-title {\n  position: sticky;\n  top: 64px; /* sticks just below the fixed navbar */\n  background: white;\n  padding: 12px 24px;\n  border-bottom: 2px solid #264DE4;\n  z-index: 10;\n  font-weight: bold;\n}\n\n.section-content {\n  padding: 24px;\n  min-height: 400px; /* so there's content to scroll */\n}\n\n/* HTML */\n<section>\n  <h2 class=\"section-title\">JavaScript</h2>\n  <div class=\"section-content\">... lots of content ...</div>\n</section>\n<section>\n  <h2 class=\"section-title\">React</h2>\n  <div class=\"section-content\">... lots of content ...</div>\n</section>",
                        "On scroll:\n- 'JavaScript' heading sticks below navbar\n- When 'React' section reaches that point, 'React' pushes 'JavaScript' up\n- Like a table of contents that tracks your reading position")
                ),
                List.of(
                    "static is the default — top/left/z-index have no effect on static elements",
                    "relative keeps the element in the normal flow — its original space is preserved",
                    "absolute removes the element from flow — always add position:relative to the parent container",
                    "fixed stays in the same viewport position during scroll — always add padding-top to body equal to navbar height",
                    "sticky requires a top/bottom value to work — position:sticky alone does nothing"
                ),
                "When using `position: absolute` and the element isn't where you expect, it's almost always because the ancestor doesn't have `position: relative`. Open DevTools, find the absolutely positioned element, and check which element is its containing block — add `position: relative` to the right parent.",
                List.of(
                    "Forgetting position:relative on the parent of an absolute child — the child escapes to the nearest positioned ancestor or the viewport",
                    "Not adding body padding-top equal to fixed navbar height — content hides behind the navbar",
                    "Using position:absolute for layouts instead of flexbox/grid — absolute positioning is for overlays, badges, and tooltips, not general layout",
                    "Sticky not working — check that no ancestor has overflow:hidden set (this breaks sticky positioning)"
                ),
                25, 7, "B"),

            // ── 8. z-index and Stacking Context ───────────────────────────
            conceptRich(css, "z-index and Stacking Context",
                "z-index controls the vertical stacking order of elements — higher numbers appear in front of lower numbers.",
                "Imagine a stack of papers on your desk. Each paper is a webpage element. The paper on top covers the ones below. z-index is the order in that stack. Paper with z-index 10 is above paper with z-index 5. But here's the twist: if you put papers into a folder, the folder has its own stack. A paper inside a low-priority folder can never appear above a paper outside a high-priority folder, no matter how high its z-index number. That folder is a 'stacking context' in CSS.",
                "`z-index` works only on positioned elements (`position` not `static`) and flex/grid children. Higher values stack in front. Default is `auto` (same as 0). **Stacking context**: created by `position: relative/absolute/fixed/sticky` with a `z-index` not `auto`, `opacity < 1`, `transform`, `filter`, `isolation: isolate`, `will-change`. Elements inside a stacking context are stacked relative to each other — they cannot escape the context. The stacking order within a context: background → negative z-index → block elements → floated elements → inline elements → z-index 0 → positive z-index. `isolation: isolate` creates a stacking context without other side effects — useful for containing z-index values.",
                "/* z-index only works on positioned elements */\n.box {\n  position: relative; /* required for z-index to work */\n  z-index: 1;\n}\n\n/* Common z-index scale for a project */\n:root {\n  --z-dropdown:  100;\n  --z-sticky:    200;\n  --z-overlay:   300;\n  --z-modal:     400;\n  --z-toast:     500;\n  --z-tooltip:   600;\n}\n\n.navbar   { position: fixed;    z-index: var(--z-sticky); }\n.modal    { position: fixed;    z-index: var(--z-modal); }\n.dropdown { position: absolute; z-index: var(--z-dropdown); }\n.toast    { position: fixed;    z-index: var(--z-toast); }\n\n/* Create stacking context without positioning side effects */\n.card-group {\n  isolation: isolate; /* cards' z-indexes are contained here */\n}",
                List.of(
                    new Concept.ConceptExample("Basic z-index layering",
                        "Stack three overlapping boxes using z-index.",
                        "/* CSS */\n.container { position: relative; height: 200px; }\n\n.box {\n  position: absolute;\n  width: 150px;\n  height: 100px;\n  border-radius: 8px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  font-weight: bold;\n  color: white;\n}\n\n.red   { background: #EF4444; top: 0;   left: 0;   z-index: 1; }\n.blue  { background: #264DE4; top: 30px; left: 30px; z-index: 2; }\n.green { background: #22C55E; top: 60px; left: 60px; z-index: 3; }\n\n/* HTML */\n<div class=\"container\">\n  <div class=\"box red\">z=1</div>\n  <div class=\"box blue\">z=2</div>\n  <div class=\"box green\">z=3</div>\n</div>",
                        "Green (z=3) appears on top\nBlue (z=2) appears in middle\nRed (z=1) appears at the bottom\nAll three overlap diagonally"),
                    new Concept.ConceptExample("Stacking context trap",
                        "Why z-index:9999 on a child still doesn't appear above a sibling parent.",
                        "/* CSS */\n.parent-A { position: relative; z-index: 1; }\n.parent-B { position: relative; z-index: 2; }\n\n.child-of-A {\n  position: relative;\n  z-index: 9999; /* WON'T beat parent-B's children! */\n  background: red;\n}\n\n.child-of-B {\n  position: relative;\n  z-index: 1; /* still appears above child-of-A */\n  background: blue;\n}\n\n/* Why? parent-A (z-index:1) loses to parent-B (z-index:2).\n   No matter how high child-of-A's z-index, it's trapped\n   inside parent-A's stacking context. */",
                        "Blue child appears above red child\nEven though red has z-index:9999 and blue has z-index:1\n\nFix: increase parent-A's z-index above parent-B\nor restructure the HTML"),
                    new Concept.ConceptExample("Modal overlay pattern",
                        "The classic full-screen modal overlay using z-index.",
                        "/* CSS */\n/* Overlay covers entire viewport */\n.modal-overlay {\n  position: fixed;\n  inset: 0; /* top:0 right:0 bottom:0 left:0 */\n  background: rgba(0, 0, 0, 0.6);\n  z-index: 400;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n}\n\n/* Modal box centered in overlay */\n.modal {\n  background: white;\n  border-radius: 12px;\n  padding: 32px;\n  max-width: 500px;\n  width: 90%;\n  position: relative; /* for close button */\n  z-index: 401;\n}\n\n.close-btn {\n  position: absolute;\n  top: 12px;\n  right: 12px;\n  cursor: pointer;\n}",
                        "Dark semi-transparent overlay covers entire screen\nWhite modal box centered on top\nClose button pinned to top-right of modal\nContent behind overlay is visible but dimmed")
                ),
                List.of(
                    "z-index only works on elements with position other than static (or flex/grid children)",
                    "Higher z-index = appears in front; lower z-index = appears behind",
                    "A stacking context traps its children — they cannot escape it regardless of z-index value",
                    "opacity < 1, transform, and filter also create stacking contexts — this often causes unexpected z-index behavior",
                    "Use a z-index scale (--z-dropdown: 100, --z-modal: 400) to keep z-index values organized"
                ),
                "Never use random high z-index values like 9999 or 99999. Create a CSS variable scale in :root — `--z-dropdown: 100; --z-modal: 400; --z-toast: 500`. When everything has meaningful names and ordered values, you'll never fight z-index wars again. If you need something above a modal, use `--z-toast: 500`.",
                List.of(
                    "Using z-index on static elements — z-index is ignored on position:static elements",
                    "The z-index:9999 trap — a high z-index inside a low-stacking-context parent still loses to elements in higher contexts",
                    "Not knowing that opacity<1, transform, and filter create stacking contexts — this is why 'my dropdown appears behind the header' happens",
                    "Using arbitrary z-index values (9, 10, 999, 9999) without a system — escalates into unmaintainable z-index wars"
                ),
                20, 8, "B"),

            // ── 9. CSS Pseudo-classes and Pseudo-elements ─────────────────
            conceptRich(css, "CSS Pseudo-classes and Pseudo-elements",
                "Pseudo-classes style elements based on their state (:hover, :focus) and pseudo-elements style specific parts of an element (::before, ::after).",
                "Pseudo-classes are like moods. Your button normally looks calm. When someone hovers over it, it's in an 'excited' mood (:hover) — change the color. When it's clicked, it's in an 'active' mood (:active) — make it look pressed. When a form field is being typed in, it's in a 'focused' mood (:focus) — add a highlight. Pseudo-elements are different — they're invisible extra boxes you can attach to an element. ::before attaches an empty box before the content, ::after attaches one after. You fill them with design elements using CSS without touching HTML.",
                "**Pseudo-classes** (single colon) target elements in specific states: `:hover`, `:focus`, `:active`, `:visited`, `:checked`, `:disabled`, `:required`, `:valid`/`:invalid`. Structural: `:first-child`, `:last-child`, `:nth-child(n)`, `:nth-of-type(n)`, `:not(selector)`, `:empty`, `:root`. **Pseudo-elements** (double colon) target parts of an element: `::before` (insert content before), `::after` (insert content after), `::first-line`, `::first-letter`, `::placeholder`, `::selection`, `::marker`. `::before` and `::after` require `content: ''` property (even empty string) to render. They are inline by default. They don't appear in the DOM — JavaScript can't select them.",
                "/* ── Pseudo-classes ── */\na:hover    { color: blue; text-decoration: underline; }\na:visited  { color: purple; }\na:active   { color: red; }\n\nbutton:hover    { background: darkblue; transform: translateY(-2px); }\nbutton:active   { transform: translateY(0); } /* pressed effect */\n\ninput:focus     { outline: 2px solid #264DE4; border-color: #264DE4; }\ninput:valid     { border-color: green; }\ninput:invalid   { border-color: red; }\ninput:disabled  { opacity: 0.5; cursor: not-allowed; }\n\n/* Structural pseudo-classes */\nli:first-child  { font-weight: bold; }\nli:last-child   { border-bottom: none; }\nli:nth-child(2n){ background: #f5f5f5; } /* even rows */\nli:nth-child(odd){ background: white; }  /* odd rows */\np:not(.special) { color: gray; }\n\n/* ── Pseudo-elements ── */\n/* ::before and ::after need content property */\n.btn::before {\n  content: '→ ';\n}\n\n.required-label::after {\n  content: ' *';\n  color: red;\n}\n\np::first-letter {\n  font-size: 2em;\n  font-weight: bold;\n  float: left;\n  margin-right: 4px;\n}\n\n::selection {\n  background: #264DE4;\n  color: white;\n}",
                List.of(
                    new Concept.ConceptExample("Interactive button states",
                        "Style hover, active, and focus states for a complete button.",
                        "/* CSS */\n.btn {\n  padding: 12px 24px;\n  background: #264DE4;\n  color: white;\n  border: none;\n  border-radius: 8px;\n  font-size: 1rem;\n  cursor: pointer;\n  transition: all 0.2s ease; /* smooth state changes */\n}\n\n/* Mouse hovering */\n.btn:hover {\n  background: #1a3ab8;\n  transform: translateY(-2px);\n  box-shadow: 0 4px 12px rgba(38,78,228,0.4);\n}\n\n/* Button being clicked */\n.btn:active {\n  transform: translateY(0);\n  box-shadow: none;\n}\n\n/* Keyboard focus (accessibility) */\n.btn:focus {\n  outline: 3px solid #60A5FA;\n  outline-offset: 2px;\n}\n\n/* Disabled state */\n.btn:disabled {\n  background: #ccc;\n  cursor: not-allowed;\n  transform: none;\n}\n\n/* HTML */\n<button class=\"btn\">Click Me</button>",
                        "Normal: blue button\nHover: darker blue, slightly raised with shadow\nClick: drops back down (no shadow)\nFocus: blue outline ring (keyboard users can see focus)\nDisabled: gray, cursor shows 'not-allowed'"),
                    new Concept.ConceptExample("nth-child for table zebra striping",
                        "Alternate row colors without adding classes to HTML.",
                        "/* CSS */\ntable { border-collapse: collapse; width: 100%; }\n\nth { background: #264DE4; color: white; padding: 12px; }\ntd { padding: 10px 12px; }\n\n/* Odd rows: white */\ntr:nth-child(odd) { background: white; }\n\n/* Even rows: light gray */\ntr:nth-child(even) { background: #f0f4ff; }\n\n/* Highlight on hover */\ntbody tr:hover { background: #dce8ff; }\n\n/* First column bold */\ntd:first-child { font-weight: bold; }\n\n/* Last column right-aligned (for numbers) */\ntd:last-child { text-align: right; }\n\n/* HTML */\n<table>\n  <tr><th>Name</th><th>Course</th><th>Score</th></tr>\n  <tr><td>Alice</td><td>HTML</td><td>95</td></tr>\n  <tr><td>Bob</td><td>CSS</td><td>88</td></tr>\n  <tr><td>Carol</td><td>JS</td><td>91</td></tr>\n</table>",
                        "Blue header row\nAlice: white background\nBob: light blue background\nCarol: white background\nHover: any row turns darker blue"),
                    new Concept.ConceptExample("::before and ::after for decorative elements",
                        "Add icons, decorations, and effects using pseudo-elements.",
                        "/* Quote block with decorative marks */\nblockquote {\n  position: relative;\n  padding: 24px 32px;\n  background: #f0f4ff;\n  border-left: 4px solid #264DE4;\n  border-radius: 0 8px 8px 0;\n}\n\nblockquote::before {\n  content: '\"';\n  position: absolute;\n  top: -10px;\n  left: 16px;\n  font-size: 4rem;\n  color: #264DE4;\n  opacity: 0.3;\n  line-height: 1;\n}\n\n/* Required field marker */\nlabel.required::after {\n  content: ' *';\n  color: #EF4444;\n  font-weight: bold;\n}\n\n/* Clearfix using ::after (for float layouts) */\n.clearfix::after {\n  content: '';\n  display: table;\n  clear: both;\n}",
                        "Blockquote: light blue box with left blue border\nLarge decorative quotation mark in the corner\n\nRequired label: 'Email *' with red asterisk\n(No HTML change needed — CSS adds the asterisk)")
                ),
                List.of(
                    "Pseudo-classes use single colon (:hover) — pseudo-elements use double colon (::before)",
                    "::before and ::after MUST have content property to render — use content: '' for decorative elements",
                    "Pseudo-elements don't exist in the DOM — JavaScript cannot select them with querySelector",
                    ":nth-child(2n) = even rows; :nth-child(2n+1) or :nth-child(odd) = odd rows",
                    "Never remove outline on :focus — it's essential for keyboard and accessibility users. Restyle it, don't remove it"
                ),
                "Never write `*:focus { outline: none; }` to remove the focus ring. Keyboard users and people with motor disabilities navigate with Tab key — the focus ring is the only way they can see where they are on the page. Restyle the outline to match your design: `*:focus { outline: 2px solid #264DE4; outline-offset: 2px; }` — beautiful AND accessible.",
                List.of(
                    "Removing focus outline entirely — breaks keyboard navigation accessibility. Restyle it, never remove it",
                    "Forgetting content:'' on ::before/::after — without content property, pseudo-elements don't render at all",
                    "Using :hover on mobile — touch devices don't have hover states. If functionality requires hover, add a fallback for touch",
                    "Confusing :nth-child with :nth-of-type — :nth-child counts all siblings; :nth-of-type counts only same-type siblings"
                ),
                25, 9, "B"),

            // ── 10. CSS Transitions ───────────────────────────────────────
            conceptRich(css, "CSS Transitions",
                "CSS transitions smoothly animate property changes over time — hover effects, state changes, and UI feedback without JavaScript.",
                "Without transitions, CSS changes happen in a snap — instant, jarring. With transitions, they glide. Think of a door. Without transitions, clicking a button is like the door teleporting from closed to open instantly. With transitions, the door swings smoothly open in 0.3 seconds. The transition property tells CSS: when this element's color/size/position changes, don't snap — glide from old to new over this duration.",
                "`transition` shorthand: `transition: property duration timing-function delay`. **Properties**: any animatable CSS property, or `all` (avoid — expensive). **Duration**: time in `s` or `ms`. **Timing functions**: `ease` (default — slow start, fast, slow end), `linear` (constant speed), `ease-in` (slow start), `ease-out` (slow end), `ease-in-out`, `cubic-bezier(x1,y1,x2,y2)`. **Delay**: wait before transition starts. Multiple transitions: `transition: color 0.2s ease, transform 0.3s ease`. Only properties that have an intermediate state can transition — `display` cannot (use opacity + visibility instead). `will-change: transform` hints to browser for GPU acceleration.",
                "/* Basic transition */\n.btn {\n  background: #264DE4;\n  transition: background 0.3s ease;\n}\n.btn:hover { background: #1a3ab8; }\n\n/* Multiple properties */\n.card {\n  transform: translateY(0);\n  box-shadow: 0 2px 8px rgba(0,0,0,0.1);\n  transition:\n    transform 0.3s ease,\n    box-shadow 0.3s ease;\n}\n.card:hover {\n  transform: translateY(-4px);\n  box-shadow: 0 8px 24px rgba(0,0,0,0.15);\n}\n\n/* Timing functions comparison */\n.ease        { transition: transform 1s ease; }\n.linear      { transition: transform 1s linear; }\n.ease-in     { transition: transform 1s ease-in; }\n.ease-out    { transition: transform 1s ease-out; }\n.ease-in-out { transition: transform 1s ease-in-out; }\n\n/* Show/hide with opacity + visibility (not display) */\n.tooltip {\n  opacity: 0;\n  visibility: hidden;\n  transition: opacity 0.2s ease, visibility 0.2s ease;\n}\n.parent:hover .tooltip {\n  opacity: 1;\n  visibility: visible;\n}",
                List.of(
                    new Concept.ConceptExample("Button hover effects",
                        "Create polished button hover states with smooth transitions.",
                        "/* CSS */\n.btn {\n  display: inline-block;\n  padding: 12px 28px;\n  background: #264DE4;\n  color: white;\n  border: 2px solid #264DE4;\n  border-radius: 8px;\n  font-size: 1rem;\n  cursor: pointer;\n  transition: all 0.25s ease;\n  text-decoration: none;\n}\n\n/* Filled to ghost effect */\n.btn:hover {\n  background: transparent;\n  color: #264DE4;\n}\n\n/* Ghost button */\n.btn-ghost {\n  background: transparent;\n  color: #264DE4;\n  border: 2px solid #264DE4;\n  transition: all 0.25s ease;\n}\n\n/* Ghost to filled effect */\n.btn-ghost:hover {\n  background: #264DE4;\n  color: white;\n}",
                        "Normal: solid blue button with white text\nHover: transparent with blue text and border\n\nGhost normal: transparent with blue border\nGhost hover: solid blue fills in\n\nBoth transitions take 0.25 seconds"),
                    new Concept.ConceptExample("Card lift effect",
                        "Cards that rise and cast a deeper shadow on hover.",
                        "/* CSS */\n.card {\n  background: white;\n  border-radius: 12px;\n  padding: 24px;\n  border: 1px solid #e5e7eb;\n  transform: translateY(0);\n  box-shadow: 0 1px 3px rgba(0,0,0,0.08);\n  transition:\n    transform 0.3s ease,\n    box-shadow 0.3s ease,\n    border-color 0.3s ease;\n  cursor: pointer;\n}\n\n.card:hover {\n  transform: translateY(-6px); /* float up */\n  box-shadow: 0 12px 32px rgba(38,78,228,0.15);\n  border-color: #264DE4;\n}\n\n.card h3 {\n  color: #1a1a2e;\n  margin-bottom: 8px;\n}\n\n.card p {\n  color: #6b7280;\n  font-size: 0.9rem;\n}",
                        "Normal: flat card, barely visible shadow, gray border\nHover over: card floats 6px upward, deeper blue shadow, blue border\nBoth transitions run simultaneously and smoothly"),
                    new Concept.ConceptExample("Smooth show and hide without display:none",
                        "transition doesn't work with display — use opacity and visibility instead.",
                        "/* WRONG: can't transition display */\n.menu { display: none; }\n.show .menu { display: block; } /* snaps instantly, no transition */\n\n/* CORRECT: transition opacity + visibility */\n.menu {\n  opacity: 0;\n  visibility: hidden;\n  transform: translateY(-8px);\n  transition:\n    opacity 0.25s ease,\n    visibility 0.25s ease,\n    transform 0.25s ease;\n}\n\n.show .menu {\n  opacity: 1;\n  visibility: visible;\n  transform: translateY(0);\n}\n\n/* visibility: hidden keeps space but is not interactive */\n/* Combined with opacity:0 creates proper fade effect */",
                        "Without fix: menu appears/disappears instantly with no animation\nWith fix: menu fades in and slides down smoothly in 0.25 seconds\nvisibility ensures element is not clickable when invisible")
                ),
                List.of(
                    "transition goes on the DEFAULT state, not the :hover state — so the transition runs both ways (in and out)",
                    "Always specify which properties to transition — avoid transition: all (too expensive for performance)",
                    "duration and timing-function must match your context: 0.1-0.2s for small UI feedback, 0.3-0.5s for larger movements",
                    "display:none cannot be transitioned — use opacity + visibility for fade effects",
                    "transform (scale, translate, rotate) transitions are GPU-accelerated — they're smoother than transitioning width or height"
                ),
                "Put the `transition` property on the element's base state, never inside `:hover`. If you put transition in `:hover`, the animation plays smoothly when hovering but snaps back instantly when the mouse leaves. Base state transition plays both entering AND leaving the hover state.",
                List.of(
                    "Putting transition inside :hover instead of the base selector — exit transition won't play",
                    "Transitioning all properties with transition: all — triggers layout recalculations on every animated property, hurting performance",
                    "Animating margin, padding, width, height — these trigger layout reflow. Animate transform and opacity instead",
                    "No transition on focus states — keyboard users get instant, jarring state changes. Add the same transitions to :focus as :hover"
                ),
                20, 10, "B"),

            // ── 11. CSS Keyframes and Animations ──────────────────────────
            conceptRich(css, "CSS Keyframes and Animations",
                "@keyframes defines multi-step animations that run automatically — no hover required, loopable, and controllable with JavaScript.",
                "Transitions are like elevators — they take you smoothly from floor 1 to floor 3 when something triggers them. Keyframe animations are like choreographed dances — you define multiple poses (keyframes) at specific moments, and the element performs the whole routine on its own. You can make something spin, pulse, bounce, fade in, slide from the left — all without a trigger. You can loop it forever, pause it, reverse it, and even control it from JavaScript.",
                "`@keyframes name { from/0% { } to/100% { } }` defines the animation sequence. `animation` shorthand: `name duration timing-function delay iteration-count direction fill-mode play-state`. Key properties: `animation-iteration-count: infinite` (loop forever), `animation-direction: alternate` (play forward then reverse), `animation-fill-mode: forwards` (hold last keyframe after end), `animation-play-state: paused/running`. Multiple animations: comma-separated. Performance: animate `transform` and `opacity` — they are composited on GPU and don't trigger layout or paint. `prefers-reduced-motion` media query respects users who have motion sensitivity.",
                "/* ── Define keyframes ── */\n@keyframes fadeIn {\n  from { opacity: 0; }\n  to   { opacity: 1; }\n}\n\n@keyframes slideUp {\n  from { opacity: 0; transform: translateY(30px); }\n  to   { opacity: 1; transform: translateY(0); }\n}\n\n@keyframes pulse {\n  0%, 100% { transform: scale(1); }\n  50%       { transform: scale(1.08); }\n}\n\n@keyframes spin {\n  from { transform: rotate(0deg); }\n  to   { transform: rotate(360deg); }\n}\n\n@keyframes typing {\n  from { width: 0; }\n  to   { width: 100%; }\n}\n\n/* ── Apply animations ── */\n.fade-in { animation: fadeIn 0.5s ease forwards; }\n\n.slide-up {\n  animation: slideUp 0.6s ease forwards;\n}\n\n.pulse {\n  animation: pulse 1.5s ease-in-out infinite;\n}\n\n.spinner {\n  animation: spin 0.8s linear infinite;\n}\n\n/* Multiple animations */\n.hero-title {\n  animation:\n    fadeIn 0.5s ease forwards,\n    slideUp 0.6s ease forwards;\n}\n\n/* Respect reduced motion */\n@media (prefers-reduced-motion: reduce) {\n  * { animation: none !important; transition: none !important; }\n}",
                List.of(
                    new Concept.ConceptExample("Loading spinner",
                        "A CSS-only loading spinner using rotation animation.",
                        "/* CSS */\n.spinner {\n  width: 40px;\n  height: 40px;\n  border: 4px solid #e0e7ff;\n  border-top-color: #264DE4;\n  border-radius: 50%;\n  animation: spin 0.8s linear infinite;\n}\n\n@keyframes spin {\n  to { transform: rotate(360deg); }\n}\n\n/* Size variants */\n.spinner-sm { width: 24px; height: 24px; border-width: 3px; }\n.spinner-lg { width: 64px; height: 64px; border-width: 6px; }\n\n/* HTML */\n<div class=\"spinner\" aria-label=\"Loading...\"></div>",
                        "A 40px circle that continuously rotates clockwise.\nLight blue background ring, dark blue top segment.\nRotates 360 degrees every 0.8 seconds, forever.\nariaLabel announces 'Loading' to screen readers."),
                    new Concept.ConceptExample("Page entrance animations",
                        "Animate content fading and sliding in when the page loads.",
                        "/* CSS */\n@keyframes fadeSlideUp {\n  from {\n    opacity: 0;\n    transform: translateY(24px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n\n.hero-title {\n  animation: fadeSlideUp 0.6s ease forwards;\n}\n\n.hero-subtitle {\n  opacity: 0; /* hidden before animation starts */\n  animation: fadeSlideUp 0.6s ease 0.2s forwards; /* 0.2s delay */\n}\n\n.hero-btn {\n  opacity: 0;\n  animation: fadeSlideUp 0.6s ease 0.4s forwards; /* 0.4s delay */\n}\n\n/* HTML */\n<h1 class=\"hero-title\">Welcome to Arise</h1>\n<p class=\"hero-subtitle\">Your learning journey starts here</p>\n<button class=\"hero-btn\">Start Learning</button>",
                        "On page load:\n0.0s: Title fades and slides up\n0.2s: Subtitle fades and slides up\n0.4s: Button fades and slides up\n\nStaggered effect makes the page feel polished"),
                    new Concept.ConceptExample("Pulse effect for notifications",
                        "Draw attention to elements with a gentle pulse animation.",
                        "/* CSS */\n@keyframes pulse {\n  0%, 100% {\n    transform: scale(1);\n    box-shadow: 0 0 0 0 rgba(38,78,228,0.4);\n  }\n  50% {\n    transform: scale(1.05);\n    box-shadow: 0 0 0 8px rgba(38,78,228,0);\n  }\n}\n\n.notification-badge {\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  width: 24px;\n  height: 24px;\n  background: #EF4444;\n  color: white;\n  border-radius: 50%;\n  font-size: 0.75rem;\n  font-weight: bold;\n  animation: pulse 1.5s ease-in-out infinite;\n}\n\n.notification-dot {\n  width: 10px;\n  height: 10px;\n  background: #EF4444;\n  border-radius: 50%;\n  animation: pulse 1.5s ease-in-out infinite;\n}\n\n/* HTML */\n<span class=\"notification-badge\">3</span>\n<span class=\"notification-dot\"></span>",
                        "Red circle with '3' that gently pulses larger and back.\nA ripple ring expands outward and fades.\nDraws the eye without being intrusive.")
                ),
                List.of(
                    "Use @keyframes for animations that run automatically; use transitions for animations triggered by state changes",
                    "animation-fill-mode: forwards holds the end state — without it, the element snaps back to start after animation",
                    "animation-iteration-count: infinite loops forever; 3 plays exactly 3 times",
                    "Stagger animations with animation-delay to create sequential entrance effects",
                    "Always add @media (prefers-reduced-motion: reduce) to respect users who experience motion sickness"
                ),
                "Always animate `transform` and `opacity` — never animate `width`, `height`, `margin`, `padding`, `top`, or `left`. Transform and opacity changes are handled by the GPU compositor and do not cause layout recalculations. Animating layout properties like width causes the browser to recalculate the entire page layout on every frame — this causes jank and dropped frames.",
                List.of(
                    "Forgetting animation-fill-mode: forwards — the element snaps back to its initial state when animation ends",
                    "Animating layout properties (width, height, margin) instead of transform — causes jank and poor performance",
                    "No prefers-reduced-motion media query — users with vestibular disorders or epilepsy may be harmed by excessive animation",
                    "Using animation for UI state changes (hover, active) — transitions are cleaner and more appropriate for state changes"
                ),
                25, 11, "B"),

            // ── 12. Flexbox ───────────────────────────────────────────────
            conceptRich(css, "Flexbox",
                "Flexbox is a one-dimensional layout system that arranges items in a row or column with powerful alignment and spacing control.",
                "Imagine seats in a cinema. Without Flexbox, placing seats is like manually measuring and marking every seat position. With Flexbox, you tell the cinema manager: 'put all seats in a row, space them equally, center them vertically'. The manager handles all the math. Flexbox has two roles: the container (the cinema) controls how items are arranged, and each item (the seat) can optionally control its own size and order. It's like having a smart manager who handles alignment automatically.",
                "**Flex container** (`display: flex`): `flex-direction` (row/row-reverse/column/column-reverse), `flex-wrap` (nowrap/wrap/wrap-reverse), `justify-content` (main axis: flex-start/flex-end/center/space-between/space-around/space-evenly), `align-items` (cross axis: stretch/flex-start/flex-end/center/baseline), `align-content` (multi-line cross axis), `gap` (spacing between items). **Flex items**: `flex-grow` (how much to grow), `flex-shrink` (how much to shrink), `flex-basis` (initial size), `flex: grow shrink basis` (shorthand), `align-self` (override container's align-items), `order` (reorder without changing HTML). `flex: 1` = `flex: 1 1 0` = grow to fill space equally.",
                "/* Container */\n.flex-container {\n  display: flex;\n\n  /* Direction */\n  flex-direction: row;     /* default: left to right */\n  flex-direction: column;  /* top to bottom */\n\n  /* Wrapping */\n  flex-wrap: nowrap;  /* default: all on one line */\n  flex-wrap: wrap;    /* wrap to next line if needed */\n\n  /* Main axis alignment (horizontal in row) */\n  justify-content: flex-start;   /* default: packed left */\n  justify-content: center;       /* centered */\n  justify-content: space-between; /* first/last at edges */\n  justify-content: space-evenly;  /* equal space everywhere */\n\n  /* Cross axis alignment (vertical in row) */\n  align-items: stretch;   /* default: fill height */\n  align-items: center;    /* vertically centered */\n  align-items: flex-start; /* aligned to top */\n\n  /* Gap between items */\n  gap: 16px;           /* row and column gap */\n  gap: 16px 24px;      /* row-gap column-gap */\n}\n\n/* Items */\n.item {\n  flex: 1;           /* grow equally */\n  flex: 0 0 200px;   /* fixed 200px, no grow/shrink */\n  align-self: center; /* override container alignment */\n  order: -1;         /* move to front */\n}",
                List.of(
                    new Concept.ConceptExample("Navbar with flexbox",
                        "The most common flexbox use case — horizontal navbar with logo left, links right.",
                        "/* CSS */\n.navbar {\n  display: flex;\n  align-items: center;        /* vertical center */\n  justify-content: space-between; /* logo left, links right */\n  padding: 0 24px;\n  height: 60px;\n  background: #1a1a2e;\n}\n\n.navbar-logo {\n  color: white;\n  font-size: 1.25rem;\n  font-weight: bold;\n  text-decoration: none;\n}\n\n.navbar-links {\n  display: flex;\n  gap: 24px;      /* space between links */\n  list-style: none;\n  margin: 0;\n  padding: 0;\n}\n\n.navbar-links a {\n  color: #ccc;\n  text-decoration: none;\n  transition: color 0.2s;\n}\n\n.navbar-links a:hover { color: white; }\n\n/* HTML */\n<nav class=\"navbar\">\n  <a href=\"/\" class=\"navbar-logo\">Arise</a>\n  <ul class=\"navbar-links\">\n    <li><a href=\"/skills\">Skills Arena</a></li>\n    <li><a href=\"/resume\">Resume</a></li>\n    <li><a href=\"/jobs\">Jobs</a></li>\n  </ul>\n</nav>",
                        "Arise [logo]          Skills Arena  Resume  Jobs\n←─────────────────────────────────────────────→\nLogo on far left, links on far right, all vertically centered"),
                    new Concept.ConceptExample("Centering with flexbox",
                        "The easiest way to center content both horizontally and vertically.",
                        "/* Perfect centering — the most common flexbox use */\n.center-container {\n  display: flex;\n  justify-content: center; /* horizontal center */\n  align-items: center;     /* vertical center */\n  height: 100vh;           /* full viewport height */\n  background: #f0f4ff;\n}\n\n/* Card to be centered */\n.login-card {\n  background: white;\n  padding: 40px;\n  border-radius: 16px;\n  width: 380px;\n  box-shadow: 0 8px 32px rgba(0,0,0,0.1);\n}\n\n/* HTML */\n<div class=\"center-container\">\n  <div class=\"login-card\">\n    <h2>Login</h2>\n    <form>...</form>\n  </div>\n</div>",
                        "Login card appears perfectly centered in the middle of the screen.\nWorks for any screen size.\nThis 3-line technique (display:flex + justify:center + align:center) replaced decades of centering hacks."),
                    new Concept.ConceptExample("Responsive card grid with flex-wrap",
                        "Cards that wrap to new lines and fill available space.",
                        "/* CSS */\n.cards-container {\n  display: flex;\n  flex-wrap: wrap;  /* wrap to next line */\n  gap: 20px;\n  padding: 20px;\n}\n\n.card {\n  flex: 1 1 280px;  /* grow, shrink, min 280px */\n  /* flex: 1 1 280px means:\n     - grow to fill space\n     - shrink if needed\n     - base size 280px */\n  background: white;\n  border: 1px solid #e5e7eb;\n  border-radius: 12px;\n  padding: 20px;\n}\n\n/* HTML */\n<div class=\"cards-container\">\n  <div class=\"card\">Course 1</div>\n  <div class=\"card\">Course 2</div>\n  <div class=\"card\">Course 3</div>\n  <div class=\"card\">Course 4</div>\n  <div class=\"card\">Course 5</div>\n</div>",
                        "Wide screen: 3 cards per row (each ~280px min, grows to fill)\nTablet: 2 cards per row\nMobile: 1 card per row\n\nNo media queries needed — flex-wrap handles it automatically")
                ),
                List.of(
                    "justify-content aligns along the MAIN axis (horizontal in row, vertical in column)",
                    "align-items aligns along the CROSS axis (vertical in row, horizontal in column)",
                    "flex: 1 makes an item grow to fill available space — multiple flex:1 items share space equally",
                    "gap replaces margin hacks for spacing between flex items — use it instead of margin on children",
                    "flex-wrap: wrap + flex: 1 1 minWidth creates a responsive layout without media queries"
                ),
                "Use `gap` for spacing between flex items instead of margin. With margin, you have to handle the first/last item specially (no margin on the edges). With `gap`, spacing only appears BETWEEN items, never on the outside — exactly what you usually want.",
                List.of(
                    "Confusing justify-content and align-items — justify is main axis, align is cross axis. When direction:column, they flip",
                    "Forgetting display:flex on the container — flexbox only works when the parent has display:flex",
                    "Using flex:1 on everything without understanding flex-basis — flex:1 sets basis to 0, meaning items share ALL available space equally",
                    "Not using flex-wrap:wrap for responsive layouts — without it, items shrink indefinitely or overflow on small screens"
                ),
                30, 12, "A"),

            // ── 13. CSS Grid ──────────────────────────────────────────────
            conceptRich(css, "CSS Grid",
                "CSS Grid is a two-dimensional layout system — it controls both rows AND columns simultaneously, making complex page layouts simple.",
                "Flexbox is like organizing a single shelf — you arrange items in a line. Grid is like organizing an entire warehouse with aisles and shelves — you control both the horizontal lanes (columns) and vertical lanes (rows) at the same time. You draw a grid on the floor by saying 'I want 3 columns and 4 rows'. Then you tell each item 'you sit in column 1-2, row 2-3'. Grid makes page layouts that used to take hundreds of CSS lines achievable in 10 lines.",
                "**Grid container** (`display: grid`): `grid-template-columns` (define columns), `grid-template-rows` (define rows), `gap`/`row-gap`/`column-gap`, `grid-template-areas` (named areas). **Sizing**: `fr` (fraction of available space), `repeat(3, 1fr)`, `minmax(200px, 1fr)`, `auto`, `auto-fill`/`auto-fit`. **Grid items**: `grid-column: 1 / 3` (span from line 1 to 3), `grid-row: 1 / 2`, `grid-area: header`, `justify-self`, `align-self`. **Auto placement**: `grid-auto-flow: row/column/dense`. `grid-template-areas` creates named regions for readable layout code.",
                "/* ── Basic grid ── */\n.container {\n  display: grid;\n\n  /* 3 equal columns */\n  grid-template-columns: 1fr 1fr 1fr;\n  /* Same: */\n  grid-template-columns: repeat(3, 1fr);\n\n  /* Fixed + flexible + fixed */\n  grid-template-columns: 250px 1fr 250px;\n\n  /* Responsive: fit as many as possible, min 200px */\n  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));\n\n  /* Rows */\n  grid-template-rows: auto 1fr auto;\n\n  /* Gap between all cells */\n  gap: 20px;\n  row-gap: 16px; column-gap: 24px;\n}\n\n/* ── Place items ── */\n.item {\n  grid-column: 1 / 3;    /* span columns 1-2 */\n  grid-column: span 2;   /* span 2 columns from current position */\n  grid-row: 1 / 3;       /* span rows 1-2 */\n}\n\n/* ── Named areas ── */\n.layout {\n  display: grid;\n  grid-template-areas:\n    'header  header '\n    'sidebar main   '\n    'footer  footer ';\n  grid-template-columns: 250px 1fr;\n  grid-template-rows: 60px 1fr 60px;\n  min-height: 100vh;\n  gap: 0;\n}\n\n.header  { grid-area: header; }\n.sidebar { grid-area: sidebar; }\n.main    { grid-area: main; }\n.footer  { grid-area: footer; }",
                List.of(
                    new Concept.ConceptExample("Classic page layout with grid-template-areas",
                        "Build a header-sidebar-main-footer layout with named areas.",
                        "/* CSS */\n.page {\n  display: grid;\n  grid-template-areas:\n    'nav    nav    nav   '\n    'aside  main   main  '\n    'footer footer footer';\n  grid-template-columns: 260px 1fr;\n  grid-template-rows: 60px 1fr 50px;\n  min-height: 100vh;\n  gap: 0;\n}\n\nnav    { grid-area: nav;    background: #1a1a2e; color: white; }\naside  { grid-area: aside;  background: #f8fafc; padding: 20px; }\nmain   { grid-area: main;   padding: 24px; }\nfooter { grid-area: footer; background: #1a1a2e; color: white; }\n\n/* HTML */\n<div class=\"page\">\n  <nav>Navigation</nav>\n  <aside>Sidebar</aside>\n  <main>Main Content</main>\n  <footer>Footer</footer>\n</div>",
                        "┌─────────────────────────────┐\n│        NAVIGATION           │\n├──────────┬──────────────────┤\n│          │                  │\n│ SIDEBAR  │   MAIN CONTENT   │\n│          │                  │\n├──────────┴──────────────────┤\n│            FOOTER           │\n└─────────────────────────────┘"),
                    new Concept.ConceptExample("Responsive card grid without media queries",
                        "auto-fit + minmax creates a grid that automatically adjusts columns.",
                        "/* CSS */\n.courses-grid {\n  display: grid;\n  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));\n  gap: 24px;\n  padding: 24px;\n}\n\n.course-card {\n  background: white;\n  border-radius: 12px;\n  overflow: hidden;\n  border: 1px solid #e5e7eb;\n  box-shadow: 0 2px 8px rgba(0,0,0,0.06);\n}\n\n.course-thumb {\n  width: 100%;\n  height: 160px;\n  object-fit: cover;\n}\n\n.course-body { padding: 16px; }\n\n/* HTML */\n<div class=\"courses-grid\">\n  <div class=\"course-card\">...</div>\n  <div class=\"course-card\">...</div>\n  <div class=\"course-card\">...</div>\n  <div class=\"course-card\">...</div>\n</div>",
                        "Wide screen (1200px): 4 columns\nMedium screen (800px): 2-3 columns\nMobile (400px): 1 column\n\nAll automatic — no media queries needed.\nauto-fit fills columns, auto-fill preserves empty columns."),
                    new Concept.ConceptExample("Spanning items across grid lines",
                        "Make specific items span multiple columns or rows.",
                        "/* CSS */\n.gallery {\n  display: grid;\n  grid-template-columns: repeat(3, 1fr);\n  grid-auto-rows: 200px;\n  gap: 12px;\n}\n\n.featured {\n  grid-column: span 2; /* takes 2 columns */\n  grid-row: span 2;    /* takes 2 rows */\n}\n\n/* HTML */\n<div class=\"gallery\">\n  <div class=\"featured\">Featured</div>\n  <div>Item 2</div>\n  <div>Item 3</div>\n  <div>Item 4</div>\n  <div>Item 5</div>\n  <div>Item 6</div>\n</div>",
                        "┌───────────────┬───────┐\n│               │   2   │\n│   FEATURED    ├───────┤\n│   (2×2)       │   3   │\n├───────┬───────┴───────┤\n│   4   │   5   │   6   │\n└───────┴───────┴───────┘")
                ),
                List.of(
                    "Grid is two-dimensional (rows AND columns); Flexbox is one-dimensional (row OR column)",
                    "fr unit distributes available space: repeat(3, 1fr) = 3 equal columns sharing all space",
                    "repeat(auto-fit, minmax(250px, 1fr)) creates a responsive grid without any media queries",
                    "grid-template-areas with named regions makes complex layouts readable — you can see the layout in the CSS",
                    "Use Grid for page layout and component layout; use Flexbox for aligning items within a component"
                ),
                "Use `repeat(auto-fit, minmax(250px, 1fr))` for any card grid. This magic one-liner creates as many columns as fit at 250px minimum, and they grow equally to fill the row. On mobile they stack to one column. On desktop they spread out. No media queries. No JavaScript. One line.",
                List.of(
                    "Confusing fr units with percentages — 1fr takes available space after fixed widths; 33% takes 33% of total, including gap",
                    "Using grid for one-directional layouts — if you only need a row or column, flexbox is simpler",
                    "Forgetting that grid-template-areas values must be quoted strings with matching column counts on every row",
                    "Not understanding the difference between auto-fit (collapses empty columns) and auto-fill (keeps empty columns)"
                ),
                30, 13, "A"),

            // ── 14. Responsive Design ─────────────────────────────────────
            conceptRich(css, "Responsive Design",
                "Responsive design makes one website look and work correctly on every device — phones, tablets, and desktops — using CSS media queries.",
                "Imagine a newspaper. On a desktop it has 6 columns. On a tablet, maybe 3. On a phone, just 1 column with bigger text so it's readable. The content is the same — the layout adapts to the reading environment. Responsive design is CSS's ability to say: 'on small screens, do this. On medium screens, do that. On large screens, do something else.' Media queries are the if-statements that check the screen size and apply different CSS rules.",
                "**Mobile-first approach**: write base CSS for mobile, then use `min-width` media queries to add complexity for larger screens. `@media (min-width: 768px) { }` — desktop enhancement. Viewport meta tag is required: `<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">`. Common breakpoints: 480px (large mobile), 768px (tablet), 1024px (small desktop), 1280px (desktop), 1536px (wide). **Media features**: `width`, `min-width`, `max-width`, `height`, `orientation`, `prefers-color-scheme`, `prefers-reduced-motion`, `hover`. CSS techniques: fluid widths (%), max-width with auto margin, responsive images (max-width:100%, object-fit), CSS Grid auto-fit, Flexbox wrap, `clamp()` for fluid typography.",
                "/* ── Viewport meta (required in HTML) ── */\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n\n/* ── Mobile-first approach ── */\n\n/* BASE: mobile styles (no media query needed) */\n.container {\n  width: 100%;\n  padding: 0 16px;\n}\n\n.nav-links { display: none; } /* hidden on mobile */\n.hamburger { display: block; } /* shown on mobile */\n\n/* TABLET: screens 768px and wider */\n@media (min-width: 768px) {\n  .container {\n    max-width: 720px;\n    margin: 0 auto;\n    padding: 0 24px;\n  }\n}\n\n/* DESKTOP: screens 1024px and wider */\n@media (min-width: 1024px) {\n  .container {\n    max-width: 1200px;\n    padding: 0 32px;\n  }\n\n  .nav-links  { display: flex; }  /* shown on desktop */\n  .hamburger  { display: none; }  /* hidden on desktop */\n\n  .grid-layout {\n    grid-template-columns: 260px 1fr;\n  }\n}\n\n/* Dark mode */\n@media (prefers-color-scheme: dark) {\n  body { background: #1a1a2e; color: white; }\n}",
                List.of(
                    new Concept.ConceptExample("Mobile-first responsive navbar",
                        "Hamburger menu on mobile, full nav on desktop.",
                        "/* CSS — Mobile first */\n.navbar {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  padding: 0 16px;\n  height: 56px;\n  background: #1a1a2e;\n}\n\n/* Mobile: links hidden, hamburger visible */\n.nav-links { display: none; }\n.hamburger { display: block; color: white; font-size: 1.5rem; cursor: pointer; }\n\n/* Mobile: when hamburger clicked, show vertical links */\n.nav-links.open {\n  display: flex;\n  flex-direction: column;\n  position: absolute;\n  top: 56px; left: 0; right: 0;\n  background: #1a1a2e;\n  padding: 16px;\n}\n\n/* Desktop: always show horizontal links */\n@media (min-width: 768px) {\n  .nav-links {\n    display: flex !important;\n    flex-direction: row;\n    gap: 24px;\n    position: static;\n    padding: 0;\n  }\n  .hamburger { display: none; }\n}\n\n.nav-links a { color: #ccc; text-decoration: none; padding: 8px 0; }",
                        "Mobile (<768px):\n☰ Arise  [hamburger icon]\nClick hamburger → vertical dropdown menu\n\nDesktop (≥768px):\nArise    Home  About  Skills  Contact"),
                    new Concept.ConceptExample("Responsive typography with clamp",
                        "Font sizes that smoothly scale between mobile and desktop.",
                        "/* Without clamp: fixed sizes at breakpoints */\nh1 { font-size: 1.75rem; }\n@media (min-width: 768px) { h1 { font-size: 2.5rem; } }\n@media (min-width: 1024px) { h1 { font-size: 3.5rem; } }\n\n/* With clamp: smooth scaling, one line */\nh1 {\n  /* clamp(minimum, preferred, maximum) */\n  font-size: clamp(1.75rem, 5vw, 3.5rem);\n  /* min: 28px | scales with 5vw | max: 56px */\n}\n\nh2 { font-size: clamp(1.25rem, 3.5vw, 2.5rem); }\np  { font-size: clamp(0.9rem, 2vw, 1.1rem); }\n\n/* Body text readable on all sizes */\n.container {\n  width: min(100% - 32px, 1200px);\n  margin-inline: auto;\n}\n/* = max-width:1200px, centered, with 16px padding each side */",
                        "Mobile 375px: h1 = 28px (clamp minimum)\n768px: h1 = 38.4px (5vw × 768px)\nDesktop 1440px: h1 = 56px (clamp maximum)\n\nSmoothly scales — no media queries for typography"),
                    new Concept.ConceptExample("Responsive image techniques",
                        "Images that scale correctly on all devices.",
                        "/* Responsive image basics */\nimg {\n  display: block;    /* remove inline gap */\n  max-width: 100%;   /* never wider than container */\n  height: auto;      /* maintain aspect ratio */\n}\n\n/* Responsive image with object-fit */\n.card-image {\n  width: 100%;\n  height: 200px;          /* fixed height */\n  object-fit: cover;      /* crop to fill, no distortion */\n  object-position: center;\n}\n\n/* Responsive background image */\n.hero {\n  background-image: url('hero.jpg');\n  background-size: cover;\n  background-position: center;\n  height: 60vh;\n  min-height: 300px;\n}\n\n/* Different image for mobile vs desktop */\n@media (max-width: 767px) {\n  .hero { background-image: url('hero-mobile.jpg'); }\n}\n\n/* HTML: srcset for responsive images */\n<!-- <img\n  src=\"img-400.jpg\"\n  srcset=\"img-400.jpg 400w, img-800.jpg 800w, img-1200.jpg 1200w\"\n  sizes=\"(max-width:600px) 400px, (max-width:900px) 800px, 1200px\"\n  alt=\"Hero\"\n/> -->",
                        "img max-width:100%: image fills container, shrinks on mobile\nobject-fit:cover: image fills the 200px height, crops centered\nhero: background covers the section, scales with viewport\nsrcset: browser picks the right image size for the device")
                ),
                List.of(
                    "Mobile-first: write base styles for mobile, use min-width media queries to add larger screen styles",
                    "The viewport meta tag is mandatory — without it, mobile browsers render at desktop width",
                    "Common breakpoints: 480px (mobile), 768px (tablet), 1024px (desktop) — use min-width for mobile-first",
                    "Use clamp() for fluid typography that scales smoothly between breakpoints without multiple media queries",
                    "max-width: 100% on images is the minimum responsive image technique — always apply it"
                ),
                "Always design mobile-first — start with the mobile layout as your base CSS, then add `@media (min-width: 768px)` for tablets and larger. This is opposite to the common beginner approach of designing desktop-first. Mobile-first results in smaller CSS file sizes and better performance on slow mobile connections.",
                List.of(
                    "Forgetting the viewport meta tag — the single most common reason responsive design doesn't work on mobile",
                    "Desktop-first approach with max-width queries — results in bloated CSS where mobile has to override desktop styles",
                    "Not testing on real devices — browser DevTools device simulation doesn't perfectly replicate real touch behavior",
                    "Fixed pixel widths on containers — use max-width with 100% and let elements be fluid"
                ),
                30, 14, "A"),

            // ── 15. CSS Variables ─────────────────────────────────────────
            conceptRich(css, "CSS Variables",
                "CSS Custom Properties (variables) store reusable values — change one variable and every property using it updates across the entire site.",
                "CSS variables are like settings in your phone. You set your wallpaper once and it appears on every screen — lock screen, home screen, everywhere. If you change the wallpaper, all screens update at once. CSS variables work the same way: define your brand color once as `--primary-color: #264DE4`. Every button, link, and heading that uses `var(--primary-color)` will be that color. Want to rebrand? Change one line — the entire site updates instantly. No find-and-replace, no missing occurrences.",
                "CSS Custom Properties: declared with `--property-name: value` inside a selector. Read with `var(--property-name, fallback)`. Global scope: declare in `:root` (equivalent to `html`). **Cascade and inheritance**: CSS variables cascade and inherit like regular CSS properties. Child elements can override parent variables. Variables can reference other variables. They update in real-time when changed via JavaScript: `element.style.setProperty('--color', 'red')`. Unlike preprocessor variables (Sass/Less), CSS variables are live in the browser and work with JavaScript. **Fallback values**: `var(--color, blue)` — uses `blue` if `--color` is undefined. Variables can store any value: colors, sizes, transforms, shadows, even partial values.",
                "/* ── Declare in :root for global scope ── */\n:root {\n  /* Colors */\n  --color-primary:    #264DE4;\n  --color-primary-dark: #1a3ab8;\n  --color-secondary:  #9B6ED4;\n  --color-success:    #22C55E;\n  --color-danger:     #EF4444;\n  --color-text:       #1a1a2e;\n  --color-text-muted: #6b7280;\n  --color-bg:         #ffffff;\n  --color-surface:    #f8fafc;\n  --color-border:     #e5e7eb;\n\n  /* Typography */\n  --font-sans: 'Segoe UI', Arial, sans-serif;\n  --font-mono: 'JetBrains Mono', monospace;\n  --text-sm:  0.875rem;\n  --text-base: 1rem;\n  --text-lg:  1.125rem;\n  --text-xl:  1.25rem;\n  --text-2xl: 1.5rem;\n\n  /* Spacing */\n  --space-1: 0.25rem;\n  --space-2: 0.5rem;\n  --space-4: 1rem;\n  --space-6: 1.5rem;\n  --space-8: 2rem;\n\n  /* Borders */\n  --radius-sm:  4px;\n  --radius-md:  8px;\n  --radius-lg:  12px;\n  --radius-full: 9999px;\n\n  /* Shadows */\n  --shadow-sm: 0 1px 3px rgba(0,0,0,0.08);\n  --shadow-md: 0 4px 12px rgba(0,0,0,0.1);\n  --shadow-lg: 0 8px 32px rgba(0,0,0,0.15);\n\n  /* Transitions */\n  --transition: all 0.2s ease;\n}\n\n/* ── Use them ── */\n.btn {\n  background: var(--color-primary);\n  color: white;\n  padding: var(--space-2) var(--space-4);\n  border-radius: var(--radius-md);\n  font-family: var(--font-sans);\n  transition: var(--transition);\n}\n\n.btn:hover { background: var(--color-primary-dark); }",
                List.of(
                    new Concept.ConceptExample("Design token system",
                        "Build a complete design system with CSS variables.",
                        "/* Define ALL design decisions as variables */\n:root {\n  --primary: #264DE4;\n  --primary-hover: #1a3ab8;\n  --success: #22C55E;\n  --danger: #EF4444;\n  --text: #1a1a2e;\n  --text-muted: #9ca3af;\n  --bg: #ffffff;\n  --bg-surface: #f8fafc;\n  --border: #e5e7eb;\n  --radius: 8px;\n  --shadow: 0 2px 8px rgba(0,0,0,0.08);\n  --transition: 0.2s ease;\n}\n\n/* Every component uses variables — no hardcoded values */\n.card {\n  background: var(--bg);\n  border: 1px solid var(--border);\n  border-radius: var(--radius);\n  box-shadow: var(--shadow);\n  padding: 24px;\n  transition: box-shadow var(--transition);\n}\n\n.btn-primary { background: var(--primary); color: white; }\n.btn-primary:hover { background: var(--primary-hover); }\n\n.text-muted { color: var(--text-muted); }\n.text-success { color: var(--success); }\n.text-danger  { color: var(--danger); }",
                        "Every color, size, and style comes from one place.\nChange --primary from blue to purple = entire site rebrands.\nNew team member? Read :root to understand the entire design language."),
                    new Concept.ConceptExample("Dark mode with CSS variables",
                        "Implement dark mode by overriding variables — not rewriting CSS.",
                        "/* Light mode variables (default) */\n:root {\n  --bg:          #ffffff;\n  --bg-surface:  #f8fafc;\n  --text:        #1a1a2e;\n  --text-muted:  #6b7280;\n  --border:      #e5e7eb;\n  --shadow:      0 2px 8px rgba(0,0,0,0.08);\n}\n\n/* Dark mode: override ONLY the variables */\n[data-theme='dark'] {\n  --bg:          #0f172a;\n  --bg-surface:  #1e293b;\n  --text:        #e2e8f0;\n  --text-muted:  #94a3b8;\n  --border:      #334155;\n  --shadow:      0 2px 8px rgba(0,0,0,0.4);\n}\n\n/* Auto dark mode from OS preference */\n@media (prefers-color-scheme: dark) {\n  :root {\n    --bg: #0f172a;\n    --text: #e2e8f0;\n    /* etc */\n  }\n}\n\n/* Components use variables — they automatically update */\nbody { background: var(--bg); color: var(--text); }\n.card { background: var(--bg-surface); border-color: var(--border); }\n\n/* Toggle dark mode with JavaScript */\n/* document.documentElement.setAttribute('data-theme', 'dark') */",
                        "Light mode: white backgrounds, dark text\nSet data-theme='dark' on <html>:\n  All --bg variables switch to dark colors\n  All --text variables switch to light colors\n  Every component updates instantly — no extra CSS written"),
                    new Concept.ConceptExample("JavaScript + CSS variables for themes",
                        "Change CSS variables with JavaScript for real-time theming.",
                        "/* CSS */\n:root {\n  --accent: #264DE4;\n  --accent-glow: rgba(38, 78, 228, 0.3);\n}\n\n.themed-btn {\n  background: var(--accent);\n  box-shadow: 0 0 16px var(--accent-glow);\n  color: white;\n  padding: 12px 24px;\n  border: none;\n  border-radius: 8px;\n  cursor: pointer;\n  transition: all 0.3s ease;\n}\n\n/* JavaScript color theme switcher */\nconst themes = {\n  blue:   { '--accent': '#264DE4', '--accent-glow': 'rgba(38,78,228,0.3)' },\n  purple: { '--accent': '#9B6ED4', '--accent-glow': 'rgba(155,110,212,0.3)' },\n  green:  { '--accent': '#22C55E', '--accent-glow': 'rgba(34,197,94,0.3)' },\n};\n\nfunction setTheme(name) {\n  const vars = themes[name];\n  Object.entries(vars).forEach(([k, v]) => {\n    document.documentElement.style.setProperty(k, v);\n  });\n}",
                        "Clicking 'Blue': all themed elements turn blue with blue glow\nClicking 'Purple': instantly switch to purple\nClicking 'Green': instantly switch to green\n\nOne JavaScript function changes the entire site's color scheme.")
                ),
                List.of(
                    "Declare global variables in :root — they are accessible to every element on the page",
                    "var(--variable, fallback) — the second argument is a fallback if the variable is undefined",
                    "CSS variables cascade and inherit like regular properties — child elements can override parent variables",
                    "JavaScript can read/write CSS variables: getComputedStyle(el).getPropertyValue('--color') and el.style.setProperty('--color', 'red')",
                    "CSS variables update in real-time — perfect for themes, dark mode, and user customization"
                ),
                "Name your CSS variables semantically, not by value. `--color-primary: #264DE4` is good — if you change the primary color from blue to purple, the variable name still makes sense. `--blue: #264DE4` is bad — when you change it to purple, `--blue` becomes a lie that confuses everyone reading the code.",
                List.of(
                    "Declaring variables on a specific selector instead of :root — they won't be available outside that element",
                    "No fallback value when variable might be undefined — var(--color) with no fallback renders as invalid (transparent/initial)",
                    "Naming variables after their value (--blue, --large) instead of their purpose (--color-primary, --text-heading)",
                    "Forgetting that CSS variables are case-sensitive — --Color and --color are two different variables"
                ),
                25, 15, "B")

        );

        conceptRepository.saveAll(concepts);
        css.setTotalConcepts(concepts.size());
        subjectRepository.save(css);
        System.out.println("✅ CSS Fundamentals seeded — 15 concepts");
    }

    // ─── QUESTIONS ───────────────────────────────────────────────────────────

    private Question q(Concept c, String text, String a, String b, String s, String d, int correct, String explanation) {
        Question q = new Question();
        q.setConceptId(c.getId());
        q.setSubjectId(c.getSubjectId());
        q.setText(text);
        q.setOptions(List.of(a, b, s, d));
        q.setCorrectIndex(correct);
        q.setExplanation(explanation);
        q.setDifficulty("MEDIUM");
        return q;
    }

    private void seedQuestions() {
        List<Subject> subjects = subjectRepository.findAll();
        Subject html = subjects.stream().filter(s -> "HTML Fundamentals".equals(s.getTitle())).findFirst().orElse(null);
        Subject css  = subjects.stream().filter(s -> "CSS Fundamentals".equals(s.getTitle())).findFirst().orElse(null);
        if (html != null) seedHtmlQuestions(html);
        if (css  != null) seedCssQuestions(css);
        System.out.println("✅ Questions seeded");
    }

    private void seedHtmlQuestions(Subject html) {
        List<Concept> c = conceptRepository.findBySubjectIdOrderByOrderIndex(html.getId());
        if (c.size() < 12) return;
        List<Question> qs = new java.util.ArrayList<>();

        // ── C1: HTML Structure ──
        Concept c1 = c.get(0);
        qs.addAll(List.of(
            q(c1,"What must be the very first line of an HTML5 document?","<!DOCTYPE html>","<html>","<head>","<!-- comment -->",0,"<!DOCTYPE html> switches the browser into standards mode."),
            q(c1,"Which element is the root of every HTML document?","<body>","<html>","<head>","<main>",1,"<html> is the root element that wraps all other elements."),
            q(c1,"Where does visible page content go?","<head>","<meta>","<body>","<title>",2,"<body> contains all rendered, visible content."),
            q(c1,"What does the lang attribute on <html> do?","Sets the page language for browsers and screen readers","Changes the font","Adds a translation widget","Sets the encoding",0,"lang='en' tells browsers and screen readers the page language."),
            q(c1,"Which tag holds the page title shown in browser tabs?","<header>","<h1>","<title>","<caption>",2,"<title> inside <head> sets the browser tab label."),
            q(c1,"What does <meta charset='UTF-8'> do?","Sets the page color","Defines character encoding so special chars display correctly","Links a stylesheet","Sets the viewport",1,"UTF-8 encoding allows all Unicode characters to render correctly."),
            q(c1,"Which element provides responsive-design viewport settings?","<viewport>","<meta name='viewport'>","<responsive>","<view>",1,"<meta name='viewport' content='width=device-width, initial-scale=1'> enables responsive layout."),
            q(c1,"What is inside <head> rendered as?","Normal text","Bold text","Nothing — it is metadata not displayed","A sidebar",2,"<head> content is metadata; none of it appears as visible page content."),
            q(c1,"Which attribute should every <html> tag include for accessibility?","class","id","lang","dir",2,"lang tells screen readers and search engines the language of the document."),
            q(c1,"What happens if you omit <!DOCTYPE html>?","The page crashes","Browser enters quirks mode and renders inconsistently","Images stop loading","JavaScript breaks",1,"Without DOCTYPE the browser uses quirks mode, causing cross-browser layout differences."),
            q(c1,"Which of these belongs inside <head>?","<p>","<div>","<link rel='stylesheet' href='style.css'>","<h1>",2,"Stylesheet links belong in <head>; visible content belongs in <body>."),
            q(c1,"What is the correct nesting order?","<html><body><head>","<head><html><body>","<html><head><body>","<body><html><head>",2,"Correct order: html wraps head then body."),
            q(c1,"<meta name='description'> primarily helps with what?","Page layout","SEO — it appears as the snippet in search results","Font loading","Animations",1,"The description meta tag is shown as the search result snippet by Google."),
            q(c1,"Which statement about <body> is true?","Only one body allowed per page","Multiple body tags are fine","Body can go inside head","Body is optional in HTML5",0,"There must be exactly one <body> element per HTML document."),
            q(c1,"What does the self-closing <meta /> tag mean in HTML5?","It crashes old browsers","It is optional — HTML5 void elements don't need the slash","It is required","It links to external files",1,"In HTML5 void elements like <meta> and <link> don't need a closing slash but it is harmless."),
            q(c1,"Where should external JavaScript files be linked for best performance?","In <head>","Before </body>","Inside <meta>","Inside <title>",1,"Placing <script> before </body> lets the page render before JS is parsed."),
            q(c1,"Which tag links an external CSS file?","<style href=''>","<css>","<link rel='stylesheet' href=''>","<import>",2,"<link rel='stylesheet'> in <head> links an external CSS file."),
            q(c1,"What is the minimum valid HTML5 document?","Only <html></html>","<!DOCTYPE html><html><head></head><body></body></html>","<body></body>","<!DOCTYPE> alone",1,"A minimal valid document needs DOCTYPE, html, head, and body elements."),
            q(c1,"The <html lang='fr'> attribute tells search engines what?","The page is French","The page has a French theme","The page uses a French font","Nothing",0,"lang='fr' signals to search engines and screen readers the page is in French."),
            q(c1,"Which is NOT a valid location for <title>?","Inside <head>","At the start of <head>","At the end of <head>","Inside <body>",3,"<title> must be inside <head>, never in <body>.")
        ));

        // ── C2: Headings Paragraphs Text Tags ──
        Concept c2 = c.get(1);
        qs.addAll(List.of(
            q(c2,"How many heading levels does HTML provide?","3","4","6","8",2,"HTML has h1 through h6 — six heading levels."),
            q(c2,"Which heading is the most important?","h6","h3","h1","h2",2,"h1 is the top-level heading; there should be one per page."),
            q(c2,"What does <strong> convey semantically?","Bold styling only","Strong importance — screen readers emphasize it","Underline","Italic",1,"<strong> has semantic weight; screen readers announce it with emphasis."),
            q(c2,"What is the difference between <b> and <strong>?","None","<b> is bold with no semantic meaning; <strong> signals importance","<strong> is deprecated","<b> is only for links",1,"<b> is purely visual; <strong> carries semantic importance."),
            q(c2,"Which tag marks text with stress emphasis (semantic italic)?","<i>","<em>","<italic>","<u>",1,"<em> is semantic emphasis; <i> is presentational italic."),
            q(c2,"What does <br> do?","Creates a new paragraph","Inserts a horizontal line","Forces a line break within content","Bold text",2,"<br> inserts a line break without starting a new block element."),
            q(c2,"What does <hr> represent?","A heading rule","A thematic break between sections","Hard return","Hyperlink reference",1,"<hr> represents a thematic break — a shift in topic."),
            q(c2,"Which tag preserves whitespace and uses monospace font?","<code>","<kbd>","<pre>","<samp>",2,"<pre> renders text exactly as typed, preserving all spaces and newlines."),
            q(c2,"How many <h1> elements should a page have?","As many as needed","Two max","Exactly one","Zero",2,"One h1 per page is best practice for SEO and accessibility."),
            q(c2,"Which tag shows deleted/struck-through text?","<strike>","<del>","<s>","<remove>",1,"<del> semantically marks deleted content; <s> is presentational."),
            q(c2,"What does <mark> do?","Makes text bold","Highlights text with a background color","Creates a bookmark","Underlines text",1,"<mark> highlights text, typically shown with a yellow background."),
            q(c2,"Which tag shows smaller, fine-print text?","<tiny>","<sub>","<small>","<mini>",2,"<small> renders text at a smaller font size, often used for disclaimers."),
            q(c2,"<sup> creates which type of text?","Subscript","Superscript","Strikethrough","Underline",1,"<sup> raises text above the baseline — used for footnotes, powers."),
            q(c2,"What is the correct use of <br>?","Spacing between sections","A line break inside a poem or address","Replacing paragraph spacing","Creating lists",1,"<br> is for intentional line breaks within content like poems or addresses."),
            q(c2,"Why should headings not be used just to make text large?","They increase page size","They have semantic meaning used by screen readers and SEO","They don't work in CSS","They are case-sensitive",1,"Screen readers and search engines use heading hierarchy to understand page structure."),
            q(c2,"Which tag inserts underlined / inserted text?","<u>","<ins>","<under>","<add>",1,"<ins> semantically marks inserted content; typically rendered underlined."),
            q(c2,"What does the <abbr> tag do?","Creates an abbreviation with a tooltip explanation","Makes text abbreviated size","Links abbreviations","Underlines words",0,"<abbr title='HyperText Markup Language'>HTML</abbr> provides tooltip on hover."),
            q(c2,"Skipping heading levels (h1 then h3) hurts what?","Page speed","SEO and accessibility — breaks document outline","CSS styling","Image loading",1,"Heading hierarchy must be sequential for proper document outline."),
            q(c2,"Which tag is used for keyboard input display?","<key>","<input>","<kbd>","<code>",2,"<kbd> represents keyboard input, typically rendered in monospace."),
            q(c2,"What does <q> do?","Creates a block quotation","Creates an inline quotation with auto-added quotation marks","Queries the database","Makes text quiet",1,"<q> wraps short inline quotes; browsers add quotation marks automatically.")
        ));

        // ── C3: Links ──
        Concept c3 = c.get(2);
        qs.addAll(List.of(
            q(c3,"Which attribute specifies the link destination in <a>?","src","href","link","url",1,"href holds the URL the link points to."),
            q(c3,"What does target='_blank' do?","Opens in same tab","Opens in a new tab","Downloads the file","Opens in an iframe",1,"target='_blank' opens the linked page in a new browser tab."),
            q(c3,"Which security attribute should accompany target='_blank'?","nofollow","rel='noopener noreferrer'","sandbox","defer",1,"rel='noopener noreferrer' prevents the new tab from accessing window.opener."),
            q(c3,"How do you link to a section on the same page?","href='page.html'","href='#section-id'","href='./section'","href='same:section'",1,"href='#id' creates an anchor link that scrolls to the element with that id."),
            q(c3,"Which href value opens an email client?","link:email","email://address","mailto:address@example.com","mail:address",2,"mailto: protocol opens the default mail client with the To field prefilled."),
            q(c3,"What is a relative URL?","A URL starting with https://","A path relative to the current file location","A URL with a hash","A URL starting with //",1,"Relative URLs like 'about.html' resolve from the current page's directory."),
            q(c3,"What does the download attribute on <a> do?","Changes the link color","Forces the browser to download the linked file","Disables the link","Adds an icon",1,"The download attribute prompts the browser to save the file instead of navigating."),
            q(c3,"Which of these is an absolute URL?","about.html","../images/photo.jpg","https://example.com/page","#section",2,"Absolute URLs include the full protocol and domain."),
            q(c3,"What makes a link accessible for screen readers?","Using id","Using descriptive link text that explains the destination","Using target='_blank'","Using blue color",1,"Descriptive text like 'View pricing plans' is announced by screen readers; 'click here' is not."),
            q(c3,"What does href='tel:+1234567890' do?","Opens a map","Dials the number on supported devices","Sends an SMS","Does nothing",1,"The tel: protocol initiates a phone call on mobile and VoIP devices."),
            q(c3,"An <a> without href is...","Invalid HTML","A placeholder link — no navigation occurs, styled as text","A button","An image link",1,"<a> without href is not interactive; it renders as plain text with no navigation."),
            q(c3,"How do you make an image a clickable link?","<img href=''>","Wrap <img> inside <a href=''>","<link src=''>","<clickable>",1,"Wrapping <img> inside <a> makes the image clickable."),
            q(c3,"What is the purpose of rel='nofollow'?","Opens new tab","Tells search engines not to follow this link","Downloads the file","Prevents caching",1,"nofollow instructs search engine crawlers not to follow or pass rank to the linked page."),
            q(c3,"Which link opens the user's default phone dialer?","href='phone:123'","href='tel:123'","href='call:123'","href='dial:123'",1,"tel: is the correct protocol for phone links."),
            q(c3,"What does href='#' do by default?","Nothing","Scrolls to the top of the page","Opens a dialog","Reloads the page",1,"href='#' creates a link that scrolls the page back to the top."),
            q(c3,"Which attribute value makes a link open in the same tab?","_blank","_self","_parent","_top",1,"_self is the default — opens in the same browsing context."),
            q(c3,"What is tab-napping?","A browser tab bug","A security exploit where a new tab can hijack the opener","A CSS animation","A keyboard shortcut",1,"tab-napping is prevented by rel='noopener' when using target='_blank'."),
            q(c3,"Can block-level elements like <div> be placed inside <a>?","No — only inline elements","Yes — in HTML5 <a> can wrap block elements","Only in tables","Only with a class",1,"HTML5 allows <a> to wrap block-level elements, making large areas clickable."),
            q(c3,"How do you create an anchor target for jump links?","<a name='section'>","Add id='section' to any element","Both work","Neither works",2,"Both methods work but id on any element is the modern preferred approach."),
            q(c3,"Which of these link texts is best practice?","Click here","Read more","Download our pricing guide PDF","This link",2,"Descriptive text tells both users and screen readers exactly what the link does.")
        ));

        // ── C4: Images ──
        Concept c4 = c.get(3);
        qs.addAll(List.of(
            q(c4,"Which attribute is mandatory on every <img> tag?","src only","src and alt","width and height","title",1,"Both src (image source) and alt (text alternative) are required."),
            q(c4,"What does alt='' (empty alt) tell screen readers?","Skip this image — it is decorative","This image is important","The image is broken","The image has no source",0,"An empty alt tells assistive technology to skip the image — it adds no information."),
            q(c4,"What does loading='lazy' do?","Loads the image immediately","Defers loading until the image is near the viewport","Compresses the image","Disables the image",1,"Lazy loading improves initial page performance by only loading visible images first."),
            q(c4,"Why should you set explicit width and height on <img>?","For styling only","To prevent Cumulative Layout Shift while the image loads","To make it responsive","To add borders",1,"Explicit dimensions let the browser reserve space before the image loads, preventing layout shift."),
            q(c4,"What does the <figure> element wrap?","Only images","Self-contained media with an optional caption","All block elements","Navigation",1,"<figure> wraps media (images, diagrams, code) along with a <figcaption>."),
            q(c4,"What does <figcaption> provide?","A tooltip","A visible caption associated with the figure","An alt attribute","A border",1,"<figcaption> adds a caption to the parent <figure> element."),
            q(c4,"Which is the correct way to make a clickable image link?","<img link='url'>","<a href='url'><img src='...' alt='...'></a>","<img href='url'>","<link><img></link>",1,"Wrap <img> inside <a href='url'> to create a clickable image link."),
            q(c4,"What does srcset allow?","Multiple image formats","Providing different images for different screen sizes and densities","Lazy loading","Captions",1,"srcset lets the browser choose the best image for the device's resolution."),
            q(c4,"What should alt text describe?","The file name","The image format","The content and purpose of the image","The image size",2,"Alt text should describe what's shown in the image and why it's there."),
            q(c4,"Which image format is best for photographs?","PNG","GIF","JPEG/JPG","SVG",2,"JPEG uses lossy compression ideal for photographs with many colors."),
            q(c4,"What does loading='eager' mean?","Lazy load","Load only on click","Load immediately (default browser behavior)","Load in background",2,"eager forces immediate loading — useful for above-the-fold hero images."),
            q(c4,"What happens when an image fails to load?","Nothing","The alt text is displayed in its place","The page crashes","A broken icon only",1,"When an image fails to load, the browser displays the alt text."),
            q(c4,"Why should you not use images to display important text?","Images load slowly","If the image fails or a screen reader is used, the text is lost","Images are too large","Text images are illegal",1,"Text in images cannot be read by screen readers or search engines."),
            q(c4,"Which format supports animation?","JPEG","PNG","GIF","WebP only",2,"GIF supports simple frame-by-frame animation."),
            q(c4,"What is the best format for logos and icons with transparency?","JPEG","BMP","PNG or SVG","GIF",2,"PNG supports lossless compression with transparency; SVG is scalable."),
            q(c4,"What does the title attribute on <img> do?","Sets the alt text","Shows a tooltip on hover","Changes the image size","Makes the image a link",1,"The title attribute provides a tooltip visible on mouse hover."),
            q(c4,"Which image format is vector-based and scales perfectly?","PNG","JPEG","SVG","WebP",2,"SVG (Scalable Vector Graphics) is XML-based and renders crisp at any size."),
            q(c4,"What does object-fit: cover do in CSS with images?","Stretches the image","Crops the image to fill the container while keeping aspect ratio","Shrinks the image","Adds a border",1,"object-fit: cover fills the container, cropping the image as needed."),
            q(c4,"How do you make an image fill its container width?","width='100'","style='width:100%'","size='full'","stretch='yes'",1,"width: 100% in CSS makes the image fill its parent's width."),
            q(c4,"Which attribute provides an accessible long description for complex images?","longdesc","aria-describedby pointing to a description element","describe","detail",1,"aria-describedby references an element containing the long description.")
        ));

        // ── C5: Lists ──
        Concept c5 = c.get(4);
        qs.addAll(List.of(
            q(c5,"Which tag creates an unordered (bulleted) list?","<ol>","<ul>","<list>","<dl>",1,"<ul> creates an unordered list with bullet points."),
            q(c5,"Which tag creates an ordered (numbered) list?","<ul>","<nl>","<ol>","<list>",2,"<ol> creates a numbered list."),
            q(c5,"What is the correct child element of <ul> and <ol>?","<item>","<li>","<dt>","<p>",1,"<li> (list item) is the only valid direct child of <ul> and <ol>."),
            q(c5,"Which tag creates a description list?","<ul>","<ol>","<dl>","<list>",2,"<dl> creates a description list with term/definition pairs."),
            q(c5,"In a <dl>, which tag marks the term?","<li>","<dd>","<dt>","<term>",2,"<dt> (description term) marks the term; <dd> marks the definition."),
            q(c5,"What does the type attribute on <ol> change?","Color","List style — 1, A, a, I, i","Size","Order direction",1,"type='A' uses letters; type='I' uses Roman numerals."),
            q(c5,"What does the start attribute on <ol> do?","Sorts the list","Sets the starting number of the list","Changes bullet style","Adds a header",1,"start='5' makes the list begin at 5."),
            q(c5,"Can a <li> contain another list?","No","Yes — lists can nest to any depth","Only if it has a class","Only in <ol>",1,"Any <li> can contain a nested <ul> or <ol>."),
            q(c5,"What is the semantic use of <ul> for navigation menus?","Navigation should use <nav> only","Navigation links are semantically a list — use <ul> inside <nav>","Use <menu>","Use <div>",1,"Nav links are a list of items; <nav><ul> is the correct semantic pattern."),
            q(c5,"What CSS property removes list bullets?","display: none","list-style-type: none","bullet: hidden","text-decoration: none",1,"list-style-type: none removes the bullet or number from list items."),
            q(c5,"Which list type is best for a glossary?","ul","ol","dl","Both ul and ol",2,"<dl> with <dt> and <dd> pairs is ideal for glossaries and FAQs."),
            q(c5,"Can <p> be a direct child of <ul>?","Yes","No — only <li> is valid directly inside <ul>","In HTML5 yes","In <ol> only",1,"Placing <p> directly inside <ul> is invalid; content must go inside <li>."),
            q(c5,"What is the reversed attribute on <ol>?","Makes the list go backwards (descending)","Reverses the bullet style","Sorts alphabetically","Removes the list",0,"reversed makes the list count down instead of up."),
            q(c5,"What is the best tag for step-by-step instructions?","<ul>","<ol>","<dl>","<steps>",1,"<ol> conveys that order matters — perfect for instructions and recipes."),
            q(c5,"Which of these is valid HTML?","<ul><p>text</p></ul>","<ul><li>text</li></ul>","<ol><div>text</div></ol>","<ul><span>text</span></ul>",1,"Only <li> is valid directly inside <ul> or <ol>."),
            q(c5,"How many levels deep can you nest lists?","2","3","Unlimited","10",2,"Lists can be nested to any depth in HTML."),
            q(c5,"What does <menu> do in HTML5?","Creates a dropdown","Is a semantic alias for <ul> for interactive menus","Creates a context menu","Adds a toolbar",1,"<menu> is a semantic <ul> for interactive menus and toolbars."),
            q(c5,"Why use lists for navigation instead of divs?","Lists load faster","Screen readers announce the number of items and let users jump between items","Divs don't support links","Lists are required by spec",1,"Screen readers announce 'navigation, list of 4 items' giving keyboard users context."),
            q(c5,"What class of elements are <ul>, <ol>, <dl>?","Inline","Block","Void","Table",1,"All list container elements are block-level by default."),
            q(c5,"Which tag pair creates a key-value style description list item?","<li>text</li>","<dt>term</dt><dd>definition</dd>","<item>","<key><value>",1,"<dt> (term) followed by <dd> (definition) is the correct dl pair.")
        ));

        // ── C6: Tables ──
        Concept c6 = c.get(5);
        qs.addAll(List.of(
            q(c6,"Which element wraps an entire HTML table?","<tr>","<tbody>","<table>","<grid>",2,"<table> is the container for all table elements."),
            q(c6,"What does <tr> represent?","Table reference","Table row","Table right","Text row",1,"<tr> creates a horizontal row in a table."),
            q(c6,"What is the difference between <th> and <td>?","None","<th> is a header cell (bold, semantic); <td> is a data cell","<th> is for numbers only","<td> is deprecated",1,"<th> has semantic header meaning; <td> is a regular data cell."),
            q(c6,"Which section of a table holds column headers?","<tbody>","<tfoot>","<thead>","<headers>",2,"<thead> groups header rows at the top of the table."),
            q(c6,"Which section holds the main table data?","<tdata>","<tbody>","<main>","<tgroup>",1,"<tbody> wraps the body rows of the table."),
            q(c6,"What does colspan='3' on a cell do?","Merges 3 rows","Makes the cell span 3 columns","Spans 3 tables","Limits the cell to 3 characters",1,"colspan merges a cell across the specified number of columns."),
            q(c6,"What does rowspan='2' do?","Cell spans 2 columns","Cell spans 2 rows vertically","Creates 2 tables","Repeats the row",1,"rowspan merges a cell across the specified number of rows."),
            q(c6,"When should you use tables in HTML?","For page layout","For tabular data only — rows and columns of related data","For navigation menus","For centering content",1,"Tables are semantic for tabular data; CSS Grid/Flexbox handles layout."),
            q(c6,"Which attribute on <th> improves table accessibility?","class","scope='col' or scope='row'","title","data-header",1,"scope tells screen readers which cells a header applies to."),
            q(c6,"What does <caption> add to a table?","Styling","A visible title/label for the table","A footer","A tooltip",1,"<caption> provides a visible label describing the table's content."),
            q(c6,"Which element belongs in <tfoot>?","Column headers","Summary rows like totals","Navigation","Meta info",1,"<tfoot> holds summary, totals, or footer rows."),
            q(c6,"What was wrong with using tables for page layout?","Tables are slow","They destroy accessibility and semantic meaning","Tables don't support CSS","Tables are deprecated",1,"Table-based layouts break screen reader navigation and semantic HTML."),
            q(c6,"Can a <td> span both rows and columns simultaneously?","No","Yes — use both rowspan and colspan together","Only in <thead>","Only with CSS",1,"Both rowspan and colspan can be applied to the same cell."),
            q(c6,"What does border='1' on <table> do?","Applies a CSS border","Adds a simple 1-pixel border (HTML attribute, use CSS instead)","Sets border-radius","Creates a 1px padding",1,"The border attribute adds a basic border but CSS is preferred."),
            q(c6,"Where should <caption> appear in a table?","Last child of table","First child of table","Inside thead","Inside tfoot",1,"<caption> must be the first child of <table>."),
            q(c6,"Which CSS property controls spacing between table cells?","cell-gap","border-collapse","padding","margin",1,"border-collapse: collapse removes spacing between cells; border-collapse: separate keeps gaps."),
            q(c6,"What element groups multiple columns for shared styling?","<colgroup> and <col>","<columns>","<group>","<header>",0,"<colgroup> and <col> apply styles to entire columns without repeating on each cell."),
            q(c6,"A table with 3 rows and 4 columns has how many cells maximum?","7","12","10","3",1,"3 rows × 4 columns = 12 cells."),
            q(c6,"What accessibility role does <th scope='col'> create?","Row header","Column header","Caption","None",1,"scope='col' declares the <th> as a column header for that column."),
            q(c6,"Can tables be responsive?","No","Yes — with CSS overflow-x: auto on a wrapper div","Only with JavaScript","Only with Bootstrap",1,"Wrapping a table in overflow-x: auto container makes it horizontally scrollable on small screens.")
        ));

        // ── C7: Forms ──
        Concept c7 = c.get(6);
        qs.addAll(List.of(
            q(c7,"Which attribute on <form> specifies where to send data?","method","href","action","target",0,"action sets the URL the form data is submitted to."),
            q(c7,"Which HTTP method should be used for sensitive form data?","GET","PUT","POST","DELETE",2,"POST sends data in the request body — not visible in the URL."),
            q(c7,"What does the name attribute on an input do?","Adds a label","Sets the key sent to the server on form submission","Styles the field","Creates an id",1,"Without name, the input value is not included in the form submission."),
            q(c7,"How do you associate a <label> with an <input>?","Use the same class","Set label for='id' matching input id","Use CSS","They auto-connect",1,"for='fieldId' on label matches id='fieldId' on input."),
            q(c7,"Which input type hides characters as the user types?","hidden","text","password","secret",2,"type='password' masks typed characters for security."),
            q(c7,"What does the required attribute do?","Marks the field visually","Prevents form submission if the field is empty","Adds a red border","Makes the field read-only",1,"required is a boolean attribute that triggers browser validation before submit."),
            q(c7,"What is the difference between GET and POST in forms?","No difference","GET puts data in URL (visible); POST puts data in request body","POST is faster","GET is more secure",1,"GET is bookmarkable/cacheable for searches; POST is for sensitive/large data."),
            q(c7,"Which input type shows a calendar date picker?","time","datetime","date","calendar",2,"type='date' renders a browser-native date picker."),
            q(c7,"Which element creates a multi-line text field?","<input type='multiline'>","<textarea>","<input type='text' rows='4'>","<textbox>",1,"<textarea rows='4'> creates a resizable multi-line text input."),
            q(c7,"What attribute limits the maximum number of characters in an input?","max","size","maxlength","limit",2,"maxlength='100' prevents the user typing more than 100 characters."),
            q(c7,"How do you group related form fields together with a label?","<div>","<fieldset> and <legend>","<section>","<group>",1,"<fieldset> groups fields; <legend> provides the group's label."),
            q(c7,"Which input type creates a slider?","slider","bar","range","scale",2,"type='range' renders a drag slider between min and max values."),
            q(c7,"What makes radio buttons belong to the same group?","Same id","Same class","Same name attribute","Same value",2,"Radio buttons with the same name form a mutually exclusive group."),
            q(c7,"What does <input type='hidden'> do?","Hides the label","Submits a value not shown to the user","Makes the form invisible","Disables the field",1,"Hidden inputs carry data silently in form submissions (e.g. CSRF tokens)."),
            q(c7,"Which attribute provides HTML5 client-side pattern validation?","validate","pattern","regex","match",1,"pattern='[A-Za-z]+' validates input against a regular expression."),
            q(c7,"What does autocomplete='off' do on a field?","Disables spellcheck","Prevents the browser from suggesting saved values","Clears the field","Makes the field required",1,"autocomplete='off' stops the browser from auto-filling the input."),
            q(c7,"What type creates a file upload button?","<input type='upload'>","<input type='file'>","<file>","<input type='attach'>",1,"type='file' opens the operating system's file picker."),
            q(c7,"Why is placeholder text not a substitute for labels?","Placeholder disappears when typing and is not read reliably by screen readers","It works fine","Placeholder is more accessible","Labels are optional",0,"Labels persist and are always announced by screen readers; placeholders vanish on input."),
            q(c7,"What does <button type='reset'> do?","Submits the form","Clears all form fields to their default values","Deletes the form","Disables all inputs",1,"type='reset' reverts all inputs to their initial default values."),
            q(c7,"Which attribute makes an input non-editable but still submitted?","disabled","readonly","locked","static",1,"readonly prevents editing but the value IS submitted; disabled prevents both editing and submission.")
        ));

        // ── C8: iFrames ──
        Concept c8 = c.get(7);
        qs.addAll(List.of(
            q(c8,"What does <iframe> stand for?","Internal Frame","Inline Frame","Independent Frame","Internet Frame",1,"iframe = Inline Frame — it embeds another document inside the current one."),
            q(c8,"Which attribute is mandatory on <iframe> for accessibility?","src","width","title","loading",2,"title describes the iframe content to screen reader users."),
            q(c8,"What does the sandbox attribute on <iframe> do?","Adds a border","Restricts capabilities — scripts, forms, popups blocked by default","Loads faster","Makes it full screen",1,"sandbox isolates the iframe, preventing scripts and other capabilities unless explicitly allowed."),
            q(c8,"What does allowfullscreen permit?","The page to go fullscreen","The iframe content (e.g. video) to enter fullscreen","Popups","Screen sharing",1,"allowfullscreen enables the fullscreen button on embedded videos."),
            q(c8,"Which value to loading attribute defers iframe loading?","eager","auto","lazy","defer",2,"loading='lazy' delays loading until the iframe is near the viewport."),
            q(c8,"What security risk does target='_blank' share with iframes?","None","Tab-napping — embedded content can access window.opener","CORS issue","Cookie theft",1,"Both target='_blank' links and iframes can expose window.opener without rel='noopener'."),
            q(c8,"How do you allow only scripts inside a sandboxed iframe?","sandbox='scripts'","sandbox='allow-scripts'","sandbox='js'","Remove sandbox",1,"sandbox='allow-scripts' grants only script execution within the restricted iframe."),
            q(c8,"What happens when you embed an HTTP page inside an HTTPS page?","Works fine","Browser blocks it as mixed content","Shows a warning only","Always loads",1,"Browsers block HTTP resources (including iframes) on HTTPS pages as mixed content."),
            q(c8,"What does frameborder='0' do?","Hides the iframe","Removes the default border around the iframe","Locks the frame","Disables scrolling",1,"frameborder='0' removes the default border; use CSS border:none in modern code."),
            q(c8,"How do you make an iframe responsive (full width)?","width='100%' attribute","Only with JavaScript","Use CSS width: 100% on the iframe","Iframes can't be responsive",2,"Setting CSS width: 100% on the iframe makes it fill its container."),
            q(c8,"What is an iframe's browsing context?","Same as the parent","Completely separate — its own DOM, history, and JS scope","Shared with the parent","Only separate for security",1,"Each iframe has an independent browsing context with its own Document."),
            q(c8,"Which attribute prevents iframe content from navigating the parent page?","sandbox='allow-same-origin'","sandbox (with no allow-top-navigation)","noscroll","block-navigation",1,"By default, sandbox prevents navigation to the top-level browsing context."),
            q(c8,"What does referrerpolicy='no-referrer-when-downgrade' do?","Blocks all referrers","Sends referrer on HTTPS requests, omits on HTTP downgrade","Logs the referrer","Hides the iframe",1,"This policy is the default and controls what referrer information is sent with requests."),
            q(c8,"Can an iframe load any external site?","Yes always","No — sites can set X-Frame-Options to block being embedded","Only HTTP sites","Only same-domain",1,"X-Frame-Options: DENY or SAMEORIGIN prevents sites from being framed by others."),
            q(c8,"What is the correct embed code source for YouTube?","youtube.com/watch?v=ID","youtube.com/embed/ID","youtu.be/ID","youtube.com/video/ID",1,"YouTube's embed URL format is youtube.com/embed/VIDEO_ID."),
            q(c8,"Why should you use loading='lazy' on most iframes?","To make them invisible","Each iframe loads a full separate webpage — deferring saves bandwidth","It makes them faster to interact with","Lazy is required by W3C",1,"Iframes are expensive — each is essentially a full page load."),
            q(c8,"What does allow='camera; microphone' on an iframe do?","Grants those hardware permissions to the embedded page","Records the user","Streams video","Enables WebRTC",0,"The allow attribute implements Permissions Policy, granting specific browser APIs to the iframe."),
            q(c8,"Which attribute makes the iframe blend with the page background?","transparent='true'","style='background: transparent'","framebg='none'","allowtransparency='true'",3,"allowtransparency='true' lets the iframe background be transparent."),
            q(c8,"Can you communicate between a parent page and an iframe?","No","Yes — using window.postMessage()","Only same-origin","Only with cookies",1,"postMessage() provides secure cross-origin communication between parent and iframe."),
            q(c8,"Why is the title attribute important on iframes?","SEO","Screen readers announce it so users know what they're entering","Styling","Performance",1,"Without a title, screen readers just say 'frame' with no context for keyboard users.")
        ));

        // ── C9: Audio & Video ──
        Concept c9 = c.get(8);
        qs.addAll(List.of(
            q(c9,"Which attribute shows browser-native player controls for <video>?","autoplay","controls","player","show",1,"The controls attribute renders the browser's built-in play, pause, volume, and seek UI."),
            q(c9,"Why does autoplay often not work in modern browsers?","It is deprecated","Browsers block autoplay with sound to protect users","Only works on Chrome","Requires JavaScript",1,"Browsers block audio autoplay by default; use autoplay with muted for silent videos."),
            q(c9,"What does the muted attribute do?","Silences the microphone","Starts the media with audio muted","Removes the audio track permanently","Disables the volume slider",1,"muted starts playback without sound — required for autoplay to work in most browsers."),
            q(c9,"Which element adds subtitles or captions to a video?","<sub>","<caption>","<track>","<text>",2,"<track kind='subtitles'> links a WebVTT subtitle file to the video."),
            q(c9,"What file format does <track> use?","SRT","VTT (WebVTT)","TXT","XML",1,"WebVTT (.vtt) is the standard caption format for HTML5 media elements."),
            q(c9,"What is the poster attribute on <video>?","A CSS class","An image shown before the video plays","The video title","A background color",1,"poster='thumbnail.jpg' displays an image as the video placeholder before play."),
            q(c9,"Why should you provide multiple <source> formats?","For decoration","Not all browsers support every format — the browser picks the first it supports","To increase quality","For accessibility",1,"Providing MP4 + WebM ensures the widest browser compatibility."),
            q(c9,"What does loop do on <audio> or <video>?","Plays once","Plays the media on repeat continuously","Reverses playback","Plays in slow motion",1,"loop restarts the media automatically when it reaches the end."),
            q(c9,"Which preload value loads only duration and dimensions?","none","auto","metadata","full",2,"preload='metadata' downloads just enough to show duration and dimensions without loading the full file."),
            q(c9,"What does playsinline do on <video>?","Pauses inline","Plays video inline on iOS instead of fullscreen","Changes aspect ratio","Enables captions",1,"Without playsinline, iOS Safari opens videos in fullscreen by default."),
            q(c9,"What is the video format with the widest browser support?","WebM","OGG","MP4 (H.264)","AVI",2,"MP4/H.264 has the broadest support across all major browsers and devices."),
            q(c9,"What should you put between <video> and </video> as fallback?","Nothing","A message or download link for browsers that don't support the element","Another video","An image",1,"Fallback content is shown only in very old browsers that don't support <video>."),
            q(c9,"Which audio format has the widest browser support?","OGG","WAV","MP3 (MPEG)","FLAC",2,"MP3 is supported in all major browsers."),
            q(c9,"What does preload='none' tell the browser?","Load everything","Don't download the media until the user presses play","Only load metadata","Cache aggressively",1,"preload='none' saves bandwidth for users who may never play the media."),
            q(c9,"Why add <track> subtitles to every video?","For SEO only","Deaf and hard-of-hearing users cannot access video content without them","They improve performance","They are required by browsers",1,"Captions are legally required in many countries for public-facing websites."),
            q(c9,"Which attribute auto-starts playback with no sound?","autoplay muted","autoplay only","muted only","playsinline",0,"autoplay AND muted together allow silent autoplay in modern browsers."),
            q(c9,"What kind='captions' vs kind='subtitles' means?","Same thing","Captions include sound descriptions; subtitles are for non-native speakers","Subtitles are better","Captions are deprecated",1,"captions describe sounds for deaf users; subtitles translate speech for non-native speakers."),
            q(c9,"What does <source src='video.webm' type='video/webm'> do?","Adds an image","Provides an alternative video format for browsers preferring WebM","Embeds a website","Creates a fallback",1,"Multiple <source> elements give the browser format options — it picks the first it supports."),
            q(c9,"Why host videos on YouTube/Vimeo instead of your own server?","For better SEO","Avoid bandwidth costs and slow load times — CDN-delivered streaming","They look better","No technical reason",1,"Self-hosted videos consume significant bandwidth and may buffer; CDNs optimize delivery."),
            q(c9,"What is the purpose of the volume attribute on <video>?","Disables audio","Sets the initial volume level (0.0 to 1.0)","Mutes the video","Controls bass",1,"volume='0.5' sets playback to 50% volume on load.")
        ));

        // ── C10: Semantic HTML ──
        Concept c10 = c.get(9);
        qs.addAll(List.of(
            q(c10,"What is semantic HTML?","HTML that looks good","Using tags that describe the meaning and purpose of content","Using only HTML5 tags","Using classes",1,"Semantic tags convey meaning to browsers, screen readers, and search engines."),
            q(c10,"Which tag marks the primary navigation links?","<header>","<nav>","<links>","<menu>",1,"<nav> wraps the main navigation links of a page."),
            q(c10,"How many <main> elements should a page have?","As many as needed","Two — one per column","Exactly one","Zero",2,"One <main> per page marks the primary unique content."),
            q(c10,"Which element wraps a self-contained piece of content (like a blog post)?","<section>","<div>","<article>","<aside>",2,"<article> is for independently distributable content like posts or product cards."),
            q(c10,"What does <aside> represent?","An error message","Content tangentially related to the main content — like sidebars","Navigation","Advertisements only",1,"<aside> contains related but non-essential content like a sidebar or callout."),
            q(c10,"Which element wraps closing page content?","<end>","<footer>","<bottom>","<closing>",1,"<footer> contains closing content like copyright, links, and contact info."),
            q(c10,"What is the difference between <section> and <div>?","None","<section> has semantic meaning and needs a heading; <div> is a generic container","<div> is deprecated","<section> is for layout",1,"<section> groups related content with a heading; <div> is purely structural."),
            q(c10,"What ARIA landmark role does <main> map to?","navigation","banner","main","complementary",2,"<main> has implicit ARIA role='main' — a landmark screen readers can jump to."),
            q(c10,"What ARIA role does <header> at the page level map to?","main","contentinfo","complementary","banner",3,"The page-level <header> has implicit role='banner'."),
            q(c10,"What ARIA role does <footer> map to?","banner","contentinfo","navigation","aside",1,"<footer> has implicit role='contentinfo' for page-level footers."),
            q(c10,"Which element wraps media with an optional caption?","<image>","<media>","<figure>","<content>",2,"<figure> wraps self-contained media like images or code with an optional <figcaption>."),
            q(c10,"What does <time datetime='2026-06-01'> provide?","Visual formatting","A machine-readable date that search engines and calendars understand","A calendar picker","A tooltip",1,"The datetime attribute provides an unambiguous machine-readable date/time."),
            q(c10,"Which tag is for contact information?","<contact>","<address>","<info>","<tel>",1,"<address> semantically marks contact information like email, phone, or location."),
            q(c10,"What does the <details> element create?","A detail page","A native disclosure widget — collapsed by default","A tooltip","An aside",1,"<details> creates a native expand/collapse widget without JavaScript."),
            q(c10,"What is <summary> used for inside <details>?","The expanded content","The clickable heading/label of the disclosure widget","A tooltip","A description",1,"<summary> is the visible heading that toggles the <details> open/closed."),
            q(c10,"When should you use <div> over semantic elements?","Always","Only when no semantic element fits — purely for CSS or JS hooks","For all containers","For images",1,"Use semantic elements where meaning exists; use <div> only as a styling/scripting hook."),
            q(c10,"Which element is best for a group of related articles?","<div class='articles'>","<section> with a heading","<group>","<container>",1,"<section> with a heading is the semantic wrapper for a related group."),
            q(c10,"What does screen reader landmark navigation do?","Makes the page look better","Lets keyboard users jump directly to named regions (nav, main, footer)","Reads all text","Changes font size",1,"Landmark elements let screen reader users skip to sections without reading everything."),
            q(c10,"Can <header> appear inside <article>?","No — one per page only","Yes — <article> can have its own <header>","Only in <section>","Only with a class",1,"Sectioning elements like <article> can each have their own <header> and <footer>."),
            q(c10,"What is wrong with <div id='nav'>...</div> instead of <nav>?","Nothing","div has no semantic meaning so screen readers and search engines don't know it's navigation","div is slower","div doesn't support links",1,"A <div> with an id gives no semantic meaning; <nav> does.")
        ));

        // ── C11: HTML Attributes ──
        Concept c11 = c.get(10);
        qs.addAll(List.of(
            q(c11,"What is true about HTML attribute values?","They must always be numbers","They must always be quoted","They are case-sensitive","They cannot be empty",1,"Attribute values should always be quoted using double or single quotes."),
            q(c11,"What does the id attribute do?","Groups elements","Uniquely identifies one element per page","Adds styling","Adds functionality",1,"id must be unique on the entire page — used for CSS, JS, and anchor links."),
            q(c11,"How many id values can an element have?","Unlimited","Two max","Exactly one","Zero",2,"An element can have only one id attribute value."),
            q(c11,"What does the class attribute do?","Uniquely identifies one element","Adds reusable styling hooks — multiple elements can share a class","Sets the element type","Defines behavior",1,"class is reusable; multiple elements can have the same class for shared styling."),
            q(c11,"How do you add multiple classes to one element?","class='btn,primary'","class='btn primary'","class=['btn','primary']","multi-class='btn primary'",1,"Multiple classes are space-separated in the class attribute value."),
            q(c11,"What are boolean attributes?","Attributes with true/false string values","Attributes whose presence means true — no value needed (required, disabled, checked)","Attributes set to 1 or 0","Attributes that start with is-",1,"Boolean attributes like required and disabled are true when present, false when absent."),
            q(c11,"What is the purpose of data-* attributes?","Apply styles","Store custom data on elements for JavaScript to read via dataset","Create tooltips","Link files",1,"data-user-id='42' is accessed in JS as element.dataset.userId."),
            q(c11,"What does the title attribute do?","Sets the page title","Shows a tooltip on hover","Creates a heading","Links a file",1,"title='explanation' shows a tooltip when the user hovers over the element."),
            q(c11,"What does tabindex='0' do?","Removes the element from tab order","Adds the element to the natural tab order","Makes it first in tab order","Makes it last",1,"tabindex='0' adds the element to tab focus order following DOM sequence."),
            q(c11,"What does tabindex='-1' do?","Adds to tab order","Removes from tab order but allows programmatic focus","Makes first","Makes last",1,"tabindex='-1' lets JavaScript call .focus() on the element but removes it from normal tab sequence."),
            q(c11,"What does the hidden attribute do?","Fades the element","Completely hides the element from all users including screen readers","Hides visually only","Disables the element",1,"hidden removes the element from the page — equivalent to display: none."),
            q(c11,"What does aria-label do?","Adds visible text","Provides an accessible name for screen readers when no visible text exists","Hides the element","Styles the element",1,"aria-label='Close menu' is read by screen readers when the element has no visible label."),
            q(c11,"What does aria-hidden='true' do?","Makes element invisible visually","Hides element from screen readers while keeping it visible","Shows the element","Disables it",1,"aria-hidden='true' removes the element from the accessibility tree only."),
            q(c11,"What is wrong with aria-hidden='true' on a <button>?","Nothing","It makes the button completely inaccessible to screen reader users","It adds styling","It prevents clicks",1,"Interactive elements must not be hidden from screen readers — they become unusable."),
            q(c11,"Which attribute connects a form field to its label?","class","name","id matching label's for attribute","title",2,"<label for='email'> and <input id='email'> are linked by matching for/id values."),
            q(c11,"What does the disabled attribute do?","Makes read-only","Prevents interaction AND excludes from form submission","Hides the field","Makes it required",1,"disabled prevents both interaction and submission; readonly only prevents editing."),
            q(c11,"What does aria-expanded='false' communicate?","A collapsed/closed state","An open state","An error state","A loading state",0,"aria-expanded tells screen readers whether a collapsible element (menu, accordion) is open or closed."),
            q(c11,"Which attribute provides a long description for complex visuals?","longdesc","alt","aria-describedby pointing to a description element","title",2,"aria-describedby references a detailed description element for complex charts or diagrams."),
            q(c11,"What is the contenteditable attribute?","Makes element copyable","Allows the user to edit the element's content directly in the browser","Makes bold","Links to a CMS",1,"contenteditable='true' turns any element into an editable region."),
            q(c11,"What does spellcheck='false' do?","Disables the input","Turns off browser spell checking on the element","Makes text bold","Removes validation",1,"spellcheck='false' disables the red squiggly underlines for coding editors and specific fields.")
        ));

        // ── C12: Meta Tags & SEO ──
        Concept c12 = c.get(11);
        qs.addAll(List.of(
            q(c12,"Where do meta tags belong?","<body>","<footer>","<head>","Anywhere",2,"All meta tags belong inside <head> — they are not rendered as visible content."),
            q(c12,"What does <meta charset='UTF-8'> define?","Page color","Character encoding so all Unicode characters display correctly","Viewport","Language",1,"UTF-8 is the universal character encoding that supports all languages and symbols."),
            q(c12,"Which meta tag enables responsive mobile layout?","<meta name='mobile'>","<meta name='viewport' content='width=device-width, initial-scale=1'>","<responsive>","<meta name='screen'>",1,"The viewport meta tag tells the browser to render at device width, not zoomed-out desktop width."),
            q(c12,"What is the maximum recommended length for a meta description?","50 chars","100 chars","155 chars","300 chars",2,"Google typically truncates meta descriptions beyond 155–160 characters."),
            q(c12,"Where does the meta description appear?","In the page body","As the snippet text under the page title in search results","In browser tooltips","In the title bar",1,"The description meta is shown as the snippet in Google search results."),
            q(c12,"What is the most important SEO element in HTML?","meta description","meta keywords","<title> tag","<h1>",2,"The page <title> tag is the most important on-page SEO element."),
            q(c12,"What does og:image do?","Sets the page background","Controls the image shown when the page is shared on social media","Adds a favicon","Creates a header image",1,"Open Graph og:image defines the preview image on Facebook, LinkedIn, and WhatsApp shares."),
            q(c12,"What is the purpose of the canonical link?","Adds a stylesheet","Prevents duplicate content SEO penalties by specifying the preferred URL","Creates a shortcut","Links to a map",1,"<link rel='canonical' href='...'> tells search engines which URL is the authoritative version."),
            q(c12,"What does <meta name='robots' content='noindex'> do?","Speeds up crawling","Tells search engines not to include the page in results","Blocks all visitors","Disables JavaScript",1,"noindex hides the page from search engine results — used for login, admin, and thank-you pages."),
            q(c12,"What are Open Graph (og:) tags used for?","Styling","Controlling how pages look when shared on social media platforms","Performance","Security",1,"OG tags define title, image, and description for social media link previews."),
            q(c12,"What does the Twitter card meta tag control?","Twitter username linking","How pages look when shared on Twitter/X","Twitter login","Ad targeting",1,"Twitter card tags define the card type and preview content for Twitter shares."),
            q(c12,"What does <meta http-equiv='refresh' content='5; url=page.html'> do?","Adds caching","Redirects the page after 5 seconds","Refreshes every 5 minutes","Loads a stylesheet",1,"http-equiv='refresh' triggers an automatic redirect or page reload after a set time."),
            q(c12,"What does user-scalable=no in the viewport meta do?","Helps accessibility","Prevents users from pinching to zoom — harms accessibility and is discouraged","Enables GPU rendering","Fixes layout on old phones",1,"Preventing user zoom is an accessibility anti-pattern; visually impaired users need to zoom."),
            q(c12,"What is the purpose of <link rel='icon' href='favicon.ico'>?","Adds a background image","Sets the small icon shown in the browser tab","Links CSS","Creates a shortcut",1,"The favicon is the small icon visible in browser tabs and bookmark lists."),
            q(c12,"Which meta name controls how search engines index and follow links?","description","keywords","robots","author",2,"<meta name='robots' content='index, follow'> controls crawl behavior."),
            q(c12,"What does theme-color meta tag do?","Changes CSS variables","Sets the browser UI color on mobile Chrome and Edge","Changes font color","Sets background",1,"theme-color changes the address bar/browser chrome color on mobile devices."),
            q(c12,"How many characters should a <title> tag be to avoid truncation in Google?","20-30","50-60","100+","No limit",1,"Google typically shows about 50-60 characters of the title in search results."),
            q(c12,"What does <meta name='author' content='Name'> do?","Adds a byline visually","Identifies the page author for search engines and browsers","Creates an account","Locks editing",1,"The author meta tag credits the content creator, used by some search engines and tools."),
            q(c12,"What is OG type='article' used for?","Blog posts and articles to get richer social sharing","Making the page bold","Changing fonts","Reducing page size",0,"og:type='article' signals to social platforms that the content is a news/blog article for richer previews."),
            q(c12,"Why are duplicate <title> and <meta description> on every page bad for SEO?","They are fine","Search engines penalise duplicate metadata and can't distinguish pages","They slow the site","They cause layout issues",1,"Unique title and description per page help search engines understand and rank each page correctly.")
        ));

        questionRepository.saveAll(qs);
        System.out.println("✅ HTML questions seeded — " + qs.size() + " questions across 12 concepts");
    }

    private void seedCssQuestions(Subject css) {
        // CSS questions will be added in next session
        System.out.println("ℹ️ CSS questions pending");
    }
}