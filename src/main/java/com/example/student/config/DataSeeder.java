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
        if (subjectRepository.findAll().stream().noneMatch(s -> "Python OOP".equals(s.getTitle()))) {
            seedPythonOOP();
        }
        if (subjectRepository.findAll().stream().noneMatch(s -> "Python Advanced".equals(s.getTitle()))) {
            seedPythonAdvanced();
        }
        if (subjectRepository.findAll().stream().noneMatch(s -> "Python Professional".equals(s.getTitle()))) {
            seedPythonProfessional();
        }
        if (questionRepository.countByConceptId("6a2533030fc2fb031334a40f") == 0) {
            seedPythonFundamentalsC1Questions();
        }
        if (questionRepository.countByConceptId("6a2533030fc2fb031334a410") == 0) {
            seedPythonFundamentalsRemainingQuestions();
        }
        if (questionRepository.countByConceptId("6a256d6c91a13f5f2780109e") == 0) {
            seedPythonBasicsQuestions();
        }
        if (subjectRepository.findAll().stream().noneMatch(s -> "HTML Fundamentals".equals(s.getTitle()))) {
            seedHTMLFundamentals();
        }
        if (subjectRepository.findAll().stream().noneMatch(s -> "CSS Fundamentals".equals(s.getTitle()))) {
            seedCSSFundamentals();
        }
        if (subjectRepository.findAll().stream().noneMatch(s -> "JavaScript Basics".equals(s.getTitle()))) {
            seedJavaScriptBasics();
        }
        if (subjectRepository.findAll().stream().noneMatch(s -> "JavaScript Advanced".equals(s.getTitle()))) {
            seedJavaScriptAdvanced();
        }
        reconcile();
        repairConceptProgress();
        backfillUserStats();
    }

    // ─── PUBLIC API (called by AdminController) ───────────────────────────────
    public void reconcileRichContent() {
        reconcile();
    }

    // ─── REPAIR ──────────────────────────────────────────────────────────────
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
                admin.setPassword(passwordEncoder.encode("***REMOVED***"));
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

    // ─── HTML FUNDAMENTALS ───────────────────────────────────────────────────
    private void seedHTMLFundamentals() {
        Subject html = subjectRepository.save(sub(
            "HTML Fundamentals",
            "Learn the structure of the web — HTML tags, attributes, forms, semantic elements, accessibility and SEO basics",
            "🌐", "#E44D26", "A"
        ));
        html.setOverview("HTML is the skeleton of every web page. Every website you have ever visited is built on HTML. This subject covers everything from the basic document structure to semantic elements, forms, media, and accessibility — giving you a complete foundation for web development.");
        html.setWhyLearn("HTML is the starting point for all web development. Frontend developers, full-stack developers and UI engineers all write HTML daily. Every browser renders HTML — it is the universal language of the web.");
        html.setForWho("Complete beginners with no prior web development experience. This is the first subject in the web development path.");
        html.setPrerequisites(List.of("A modern browser (Chrome recommended)", "VS Code or any text editor"));
        html.setOutcomes(List.of(
            "Create a valid HTML5 document from scratch",
            "Use headings, paragraphs, links, images and lists correctly",
            "Understand block vs inline elements",
            "Build tables and forms with proper attributes",
            "Use semantic HTML5 elements correctly",
            "Write accessible HTML with ARIA and alt text"
        ));
        html.setWhatYouWillBuild(List.of(
            "A personal profile page with headings, images and links",
            "A contact form with validation attributes",
            "A semantic blog post layout"
        ));
        html.setToolsRequired(List.of("VS Code", "Chrome browser", "Live Server VS Code extension"));
        html.setDifficulty("Beginner");
        html.setEstimatedHours(8);
        html.setCareerUse("HTML is required for every frontend, full-stack and web developer role. It is the foundation for React, Vue, Angular and every other web framework.");
        subjectRepository.save(html);

        List<Concept> concepts = List.of(

            conceptRich(html, "HTML Structure and Document Setup",
                "Every HTML page follows a standard structure: DOCTYPE, html, head and body. Understanding this skeleton is the first step to building any web page.",
                "Think of an HTML document like a letter.\n\nEvery letter has a standard format:\n- An envelope with the recipient's address (DOCTYPE and meta info in head)\n- The letter itself (body — visible content)\n\nEvery HTML page you write will start with this same structure:\n```\n<!DOCTYPE html>\n<html lang=\"en\">\n  <head>...</head>\n  <body>...</body>\n</html>\n```\n\nThe head contains information ABOUT the page (title, charset, styles). The body contains what the user actually SEES.",
                "1. DOCTYPE declaration:\n- <!DOCTYPE html> tells the browser this is HTML5\n- Must be the very first line — before anything else\n- Not a tag — it is a declaration\n\n2. html element:\n- Root element — wraps everything\n- lang attribute: specifies page language for screen readers and SEO\n\n3. head element:\n- Contains metadata — information about the page, not visible content\n- <meta charset=\"UTF-8\">: character encoding — always include\n- <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">: mobile scaling\n- <title>: text shown in browser tab\n- <link>: link to CSS files\n- <script>: link to JS files (often at bottom of body)\n\n4. body element:\n- Contains all visible content\n- Everything the user sees on the page goes here\n\n5. HTML comments:\n- <!-- this is a comment --> — not visible to user\n- Useful for notes and temporarily disabling code",
                "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <title>My First Web Page</title>\n  <link rel=\"stylesheet\" href=\"styles.css\">\n</head>\n<body>\n\n  <!-- Visible content goes here -->\n  <h1>Hello, World!</h1>\n  <p>This is my first web page.</p>\n\n  <script src=\"app.js\"></script>\n</body>\n</html>",
                List.of(
                    new Concept.ConceptExample("Minimal valid HTML5 document",
                        "The smallest correct HTML5 document you can write.",
                        "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <title>Page Title</title>\n</head>\n<body>\n  <p>Hello!</p>\n</body>\n</html>",
                        "Browser shows: Hello!"),
                    new Concept.ConceptExample("head vs body — what goes where",
                        "Understand which elements belong in head and which in body.",
                        "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <!-- NOT visible to user -->\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <meta name=\"description\" content=\"A demo page\">\n  <title>Demo Page</title>\n  <link rel=\"stylesheet\" href=\"styles.css\">\n</head>\n<body>\n  <!-- Visible to user -->\n  <h1>Welcome</h1>\n  <p>This text is visible on the page.</p>\n  <!-- Script at bottom so HTML loads first -->\n  <script src=\"app.js\"></script>\n</body>\n</html>",
                        "Browser renders: Welcome heading and paragraph text")
                ),
                List.of(
                    "<!DOCTYPE html> must be the very first line — no spaces or lines before it",
                    "Always include charset UTF-8 and viewport meta tags in every page",
                    "The title tag controls what appears in the browser tab and bookmark",
                    "Scripts are placed at the bottom of body so the page content loads before JavaScript runs",
                    "The lang attribute on html helps screen readers pronounce content correctly"
                ),
                "Use the VS Code shortcut: type ! and press Tab in an empty .html file. It generates the complete HTML5 boilerplate instantly — you never need to type it from scratch.",
                List.of(
                    "Missing DOCTYPE — without it the browser enters quirks mode and renders inconsistently",
                    "Missing charset meta tag — special characters like é, ü, ₹ may display incorrectly",
                    "Missing viewport meta — page will not scale correctly on mobile devices"
                ),
                15, 1, "E"),

            conceptRich(html, "HTML Attributes",
                "Attributes provide additional information about HTML elements. They are written inside the opening tag as name-value pairs and control element behaviour, appearance and meaning.",
                "Tags alone do not tell the whole story.\n\nAn image tag without a source would show nothing. A link without a destination goes nowhere. An input without a type defaults to text.\n\nAttributes are the extra details that make tags complete:\n- src tells an image where to find the file\n- href tells a link where to go\n- class gives an element a name for CSS to target\n- id gives an element a unique identifier\n\nThink of an HTML element like a person. The tag is their job title (teacher, engineer). The attributes are their specific details (name, location, department).",
                "1. Attribute syntax:\n- name=\"value\" inside the opening tag\n- Some attributes are boolean — just the name, no value: disabled, checked, required\n- Attribute names are case-insensitive (lowercase is standard)\n- Values go in double quotes (single quotes also work)\n\n2. Global attributes (work on any element):\n- id: unique identifier for the element — must be unique per page\n- class: one or more class names for CSS/JS targeting\n- style: inline CSS (avoid in favour of external CSS)\n- title: tooltip text shown on hover\n- lang: language override for a section\n- hidden: hides element (like display:none)\n- data-*: custom data attributes for storing extra info\n- tabindex: controls keyboard tab order\n\n3. Element-specific attributes:\n- href (on a): destination URL\n- src (on img, script): source file path\n- alt (on img): alternative text\n- type (on input): input type\n- name (on input): form field name\n- value (on input): preset value\n- placeholder (on input): hint text\n- disabled: disables form elements\n- required: makes field mandatory",
                "<!-- id and class -->\n<h1 id=\"main-title\" class=\"hero-heading large\">Welcome</h1>\n\n<!-- href and target -->\n<a href=\"https://example.com\" target=\"_blank\" rel=\"noopener\">Visit</a>\n\n<!-- src and alt -->\n<img src=\"photo.jpg\" alt=\"A mountain landscape\" width=\"400\" height=\"300\">\n\n<!-- Boolean attributes -->\n<input type=\"checkbox\" checked>\n<input type=\"text\" disabled placeholder=\"Cannot type here\">\n<input type=\"email\" required placeholder=\"Enter email\">\n\n<!-- data-* custom attributes -->\n<button data-user-id=\"42\" data-action=\"delete\">Delete</button>\n\n<!-- Access data attributes in JS -->\n<script>\n  const btn = document.querySelector('button');\n  console.log(btn.dataset.userId);   // '42'\n  console.log(btn.dataset.action);   // 'delete'\n</script>\n\n<!-- style (inline -- avoid) -->\n<p style=\"color: red; font-size: 18px;\">Red text</p>\n\n<!-- title tooltip -->\n<abbr title=\"HyperText Markup Language\">HTML</abbr>",
                List.of(
                    new Concept.ConceptExample("class vs id",
                        "id is unique per page; class can be shared by many elements.",
                        "<!-- id: one element only -->\n<header id=\"main-header\">...</header>\n\n<!-- class: many elements can share -->\n<p class=\"highlight\">First highlighted paragraph</p>\n<p class=\"highlight\">Second highlighted paragraph</p>\n<span class=\"highlight\">Highlighted span</span>\n\n<!-- Multiple classes on one element -->\n<button class=\"btn btn-primary large\">Click Me</button>\n\n<!-- CSS targets -->\n<style>\n  #main-header { background: #333; }\n  .highlight { background: yellow; }\n  .btn.btn-primary { background: blue; color: white; }\n</style>",
                        "Header has dark background, paragraphs and span have yellow highlight, button is blue")
                ),
                List.of(
                    "id must be unique on the page — using the same id twice breaks CSS and JavaScript",
                    "class can be used by many elements — it is the primary way to target elements in CSS",
                    "Boolean attributes like disabled, checked, required need no value — just the attribute name",
                    "data-* attributes store custom data: data-id, data-user, data-color are all valid",
                    "Always use rel=\"noopener\" with target=\"_blank\" links — prevents security vulnerability"
                ),
                "Use data-* attributes to store information on elements that JavaScript needs. Instead of generating complex IDs or using hidden inputs, add data-product-id=\"123\" on a button and read it with button.dataset.productId in JS.",
                List.of(
                    "Using the same id on multiple elements — id must be unique",
                    "Forgetting quotes around attribute values — some values work without quotes but it is bad practice",
                    "Using target=\"_blank\" without rel=\"noopener\" — allows the new tab to access your page via window.opener"
                ),
                15, 2, "E"),

            conceptRich(html, "Headings, Paragraphs, and Text Tags",
                "Headings (h1-h6) create a document outline. Paragraphs (p) hold blocks of text. Inline text tags like strong, em, span add meaning and styling to text content.",
                "Text is the backbone of most web pages.\n\nHeadings create hierarchy — h1 is the main title, h2 is a section heading, h3 is a sub-section. Search engines use this hierarchy to understand your page.\n\nParagraphs group related sentences together with automatic spacing above and below.\n\nInline text tags wrap specific words within a paragraph to add meaning:\n- strong: important text (bold)\n- em: emphasised text (italic)\n- mark: highlighted text\n- code: inline code snippet\n- small: small print",
                "1. Headings h1-h6:\n- h1: page title — only one per page (SEO)\n- h2: major section headings\n- h3: sub-sections under h2\n- h4-h6: rarely needed, for deep nesting\n- Use headings for structure, not for making text big\n- Screen readers use headings to navigate\n\n2. Paragraph:\n- <p>: block of text with automatic margins\n- Browsers collapse multiple spaces/newlines to one\n- Use &nbsp; for non-breaking space if needed\n\n3. Line break and rule:\n- <br>: single line break (self-closing)\n- <hr>: horizontal rule / thematic break\n\n4. Inline text tags:\n- <strong>: important — bold by default\n- <em>: emphasis — italic by default\n- <mark>: highlighted text\n- <small>: smaller text — legal, captions\n- <del>: deleted text (strikethrough)\n- <ins>: inserted text (underline)\n- <sup>: superscript — footnotes, powers\n- <sub>: subscript — chemical formulas\n- <code>: inline code\n- <pre>: preformatted text (preserves spaces/newlines)\n- <abbr>: abbreviation with title tooltip\n- <span>: generic inline container for CSS/JS",
                "<!-- Headings -->\n<h1>Main Page Title</h1>\n<h2>Section Heading</h2>\n<h3>Sub-section</h3>\n\n<!-- Paragraphs -->\n<p>This is a paragraph with <strong>important text</strong> and <em>emphasised text</em>.</p>\n\n<p>HTML was created by <abbr title=\"Tim Berners-Lee\">TBL</abbr> in 1991.</p>\n\n<!-- Line break -->\n<p>Line one.<br>Line two on next line.</p>\n\n<!-- Horizontal rule -->\n<hr>\n\n<!-- Various text tags -->\n<p>Price: <del>Rs. 2000</del> <ins>Rs. 1500</ins></p>\n<p>Water formula: H<sub>2</sub>O</p>\n<p>Area = r<sup>2</sup></p>\n<p><mark>This is highlighted</mark></p>\n<p><small>Terms and conditions apply</small></p>\n\n<!-- Code -->\n<p>Use <code>console.log()</code> to debug.</p>\n\n<!-- Preformatted -->\n<pre>\n  function hello() {\n    return 'world';\n  }\n</pre>",
                List.of(
                    new Concept.ConceptExample("Correct heading hierarchy",
                        "Use headings to create a logical document outline.",
                        "<article>\n  <h1>Introduction to Python</h1>  <!-- one h1 per page -->\n\n  <h2>What is Python?</h2>\n  <p>Python is a high-level programming language...</p>\n\n  <h2>Getting Started</h2>\n  <h3>Installation</h3>\n  <p>Download Python from python.org...</p>\n\n  <h3>First Program</h3>\n  <p>Open a file and type:</p>\n  <pre><code>print('Hello, World!')</code></pre>\n\n  <h2>Core Concepts</h2>\n  <h3>Variables</h3>\n  <p>Variables store data...</p>\n</article>",
                        "Renders a structured document with logical heading levels")
                ),
                List.of(
                    "Use only one h1 per page — it is the main title and important for SEO",
                    "Do not skip heading levels: h1 → h3 without h2 breaks document structure",
                    "Use headings for structure, not for making text big — use CSS for sizing",
                    "strong means important; b means bold with no semantic meaning — prefer strong",
                    "em means emphasis; i means italic with no semantic meaning — prefer em"
                ),
                "Never use headings just to make text bigger or bolder. If you want bigger text, use CSS. Headings carry semantic meaning — search engines and screen readers use them to understand your content structure.",
                List.of(
                    "Using multiple h1 tags — one per page is the standard for SEO",
                    "Using <br> tags repeatedly to add vertical space — use CSS margin/padding instead",
                    "Using <b> and <i> instead of <strong> and <em> — strong and em carry semantic meaning"
                ),
                15, 3, "E"),

            conceptRich(html, "Block vs Inline Elements",
                "Block elements take up the full width and start on a new line. Inline elements only take up as much space as their content and flow within text.",
                "Every HTML element behaves as either block or inline by default.\n\nBlock elements:\n- Always start on a new line\n- Take up the full width available\n- Can contain other block and inline elements\n- Examples: div, p, h1-h6, ul, ol, table, form, header, section\n\nInline elements:\n- Flow within the surrounding text\n- Only as wide as their content\n- Cannot contain block elements\n- Examples: span, a, img, strong, em, code, button, input\n\nThis matters because you cannot put a block element inside an inline element — browsers will display it incorrectly.",
                "1. Block elements:\n- Start on their own line\n- Width: 100% of parent by default\n- Height: determined by content\n- Can have width, height, margin, padding on all sides\n- Common: div, p, h1-h6, ul, ol, li, table, form, blockquote, pre, header, main, section, article, footer\n\n2. Inline elements:\n- Flow in line with text\n- Width: fits content\n- Cannot set width/height directly (use display:block or inline-block)\n- Top/bottom margin and padding have limited effect\n- Common: span, a, img, strong, em, b, i, code, label, button, input, select\n\n3. Inline-block:\n- CSS display: inline-block\n- Flows inline but accepts width/height/margins\n- Used for buttons, navigation items\n\n4. CSS display property overrides:\n- Any element can be made block or inline with CSS\n- display: block, inline, inline-block, flex, grid, none\n\n5. Nesting rules:\n- Block inside block: OK\n- Inline inside block: OK\n- Block inside inline: NOT valid HTML",
                "<!-- Block elements — each on its own line -->\n<div>I am a block div</div>\n<p>I am a block paragraph</p>\n<h2>I am a block heading</h2>\n\n<!-- Inline elements — flow within text -->\n<p>\n  This is a paragraph with\n  <strong>bold text</strong>,\n  <em>italic text</em>, and\n  <a href=\"#\">a link</a>\n  all flowing inline.\n</p>\n\n<!-- div vs span -->\n<div class=\"card\">     <!-- block container -->\n  <h3>Product</h3>\n  <p>Price: <span class=\"price\">Rs. 999</span></p>\n</div>\n\n<!-- Invalid: block inside inline -->\n<!-- <span><div>Wrong!</div></span> -->\n\n<!-- Valid: inline inside block -->\n<p><span>Correct</span></p>",
                List.of(
                    new Concept.ConceptExample("div vs span — container elements",
                        "div is the generic block container; span is the generic inline container.",
                        "<div class=\"user-card\">\n  <img src=\"avatar.jpg\" alt=\"User avatar\">\n  <h3>Ravi Kumar</h3>\n  <p>Role: <span class=\"role admin\">Admin</span></p>\n  <p>Status: <span class=\"status active\">Active</span></p>\n</div>\n\n<!-- div groups block-level content -->\n<!-- span targets specific inline text for styling -->",
                        "User card block with inline-styled role and status badges")
                ),
                List.of(
                    "Block elements start on new line and fill width; inline elements flow within text",
                    "div is a generic block container; span is a generic inline container",
                    "Cannot nest a block element inside an inline element",
                    "img behaves as inline by default — use display:block to centre it with margin:auto",
                    "CSS display property can change any element's layout behaviour"
                ),
                "When you want to target a specific word or phrase for styling, use span. When you want to group multiple block-level elements together, use div. These two are your go-to containers for everything CSS-related.",
                List.of(
                    "Putting a p or div inside a span — invalid nesting, browser tries to fix it unpredictably",
                    "Trying to set width/height on inline elements — use display:inline-block first"
                ),
                12, 4, "E"),

            conceptRich(html, "Links and Anchor Tags",
                "The anchor tag <a> creates hyperlinks — the foundation of the web. Links can go to other pages, sections of the same page, files, emails or phone numbers.",
                "Links are what make the web a web.\n\nWithout links, every website would be an isolated island. Links connect pages to each other, let users navigate between sections, and allow browsers to download files.\n\nThe anchor tag has one essential attribute:\nhref (hypertext reference) — where the link goes\n\nLinks can point to:\n- External pages: https://example.com\n- Internal pages: /about or about.html\n- Page sections: #section-id\n- Email: mailto:email@example.com\n- Phone: tel:+919876543210\n- Files: /downloads/report.pdf",
                "1. Basic anchor tag:\n- <a href=\"url\">Link text</a>\n- href: the destination\n- Link text: visible, clickable text (descriptive, not 'click here')\n\n2. Types of links:\n- Absolute: full URL including https://\n- Relative: path from current file — /about, ../images/photo.jpg\n- Fragment: #id jumps to element with that id on same page\n- mailto: opens email client — mailto:name@email.com\n- tel: initiates phone call on mobile — tel:+919876543210\n\n3. target attribute:\n- _blank: opens in new tab\n- _self: same tab (default)\n- Always add rel=\"noopener noreferrer\" with _blank\n\n4. Download attribute:\n- <a href=\"file.pdf\" download>Download PDF</a>\n- Forces download instead of navigation\n\n5. States:\n- :link — unvisited\n- :visited — already visited\n- :hover — mouse over\n- :active — being clicked\n- Styled with CSS pseudo-classes",
                "<!-- External link -->\n<a href=\"https://www.google.com\" target=\"_blank\" rel=\"noopener noreferrer\">\n  Visit Google\n</a>\n\n<!-- Internal link (relative path) -->\n<a href=\"/about\">About Us</a>\n<a href=\"../contact.html\">Contact</a>\n\n<!-- Jump to section on same page -->\n<a href=\"#features\">See Features</a>\n\n<!-- The target section -->\n<section id=\"features\">\n  <h2>Features</h2>\n</section>\n\n<!-- Email link -->\n<a href=\"mailto:hello@example.com\">Email Us</a>\n\n<!-- Phone link -->\n<a href=\"tel:+919876543210\">Call Us</a>\n\n<!-- Download link -->\n<a href=\"/files/brochure.pdf\" download=\"brochure\">Download Brochure</a>\n\n<!-- Link wrapping an image -->\n<a href=\"/product\">\n  <img src=\"product.jpg\" alt=\"View product\">\n</a>",
                List.of(
                    new Concept.ConceptExample("Navigation menu with links",
                        "Build a simple navigation bar using anchor tags.",
                        "<nav>\n  <ul>\n    <li><a href=\"/\">Home</a></li>\n    <li><a href=\"/courses\">Courses</a></li>\n    <li><a href=\"/about\">About</a></li>\n    <li><a href=\"/contact\">Contact</a></li>\n  </ul>\n</nav>\n\n<!-- In-page navigation -->\n<nav>\n  <a href=\"#intro\">Introduction</a>\n  <a href=\"#features\">Features</a>\n  <a href=\"#pricing\">Pricing</a>\n</nav>",
                        "Navigation bar with links to pages and page sections")
                ),
                List.of(
                    "href is required for links to work — missing href makes the element unclickable",
                    "Use descriptive link text: 'Read our privacy policy' not 'click here'",
                    "Always add rel=\"noopener noreferrer\" with target=\"_blank\" external links",
                    "#id links jump to the element with that id on the page — used for in-page navigation",
                    "Relative paths: /page is from site root, page.html is from current folder, ../page is one level up"
                ),
                "Use descriptive, meaningful link text. Screen readers announce links by their text content. 'Click here' or 'read more' tells users nothing. 'Download the Python Basics PDF' or 'Visit our GitHub' is much better.",
                List.of(
                    "Using target=\"_blank\" without rel=\"noopener\" — security vulnerability",
                    "Using vague link text like 'click here' or 'more' — not accessible",
                    "Missing the # in fragment links: href=\"section\" goes to a file, not a page section"
                ),
                15, 5, "E"),

            conceptRich(html, "Images",
                "The <img> tag embeds images. It requires src (source) and alt (alternative text) attributes. Understanding image formats, sizes and loading strategies is essential for performance.",
                "Images make web pages visual and engaging.\n\nThe img tag is self-closing — it has no content between tags. Two attributes are always required:\n- src: where to find the image file\n- alt: text description of the image\n\nThe alt attribute is critical for:\n- Screen readers (visually impaired users)\n- When the image fails to load\n- Search engine indexing\n\nChoosing the right image format affects page loading speed significantly.",
                "1. Basic img tag:\n- <img src=\"path\" alt=\"description\">\n- Self-closing — no closing tag needed\n- src: relative path or full URL\n- alt: always required — describes the image\n\n2. Size attributes:\n- width and height: in pixels or percentage\n- Always specify to prevent layout shift while loading\n- CSS is preferred for responsive sizing\n\n3. Image formats:\n- JPEG/JPG: photos — good compression, no transparency\n- PNG: graphics with transparency, logos\n- WebP: modern format, better compression than JPEG and PNG\n- SVG: vector graphics — scales infinitely, ideal for icons and logos\n- GIF: animations (avoid — WebP is better)\n- AVIF: newest, best compression (limited browser support)\n\n4. loading attribute:\n- loading=\"lazy\": load image only when near viewport — improves performance\n- loading=\"eager\": load immediately (default)\n\n5. srcset and sizes (responsive images):\n- <img srcset=\"img-400.jpg 400w, img-800.jpg 800w\">\n- Browser picks the right size for screen width\n\n6. figure and figcaption:\n- <figure> wraps image with caption\n- <figcaption> provides visible caption",
                "<-- Basic image -->\n<img src=\"photo.jpg\" alt=\"A sunset over the mountains\">\n\n<!-- With dimensions -->\n<img src=\"logo.png\" alt=\"Company logo\" width=\"200\" height=\"60\">\n\n<!-- External image -->\n<img src=\"https://example.com/image.jpg\" alt=\"Example image\">\n\n<!-- Lazy loading -->\n<img src=\"below-fold.jpg\" alt=\"Content image\" loading=\"lazy\">\n\n<!-- Responsive with srcset -->\n<img\n  src=\"photo-800.jpg\"\n  srcset=\"photo-400.jpg 400w, photo-800.jpg 800w, photo-1200.jpg 1200w\"\n  sizes=\"(max-width: 600px) 400px, 800px\"\n  alt=\"Responsive photo\"\n>\n\n<!-- Figure with caption -->\n<figure>\n  <img src=\"chart.png\" alt=\"Sales growth chart showing 40% increase\">\n  <figcaption>Sales growth Q1 2024</figcaption>\n</figure>\n\n<!-- Decorative image — empty alt -->\n<img src=\"decoration.png\" alt=\"\" role=\"presentation\">",
                List.of(
                    new Concept.ConceptExample("Image with fallback and lazy load",
                        "Best practices for production image implementation.",
                        "<section class=\"gallery\">\n  <!-- Always: src + meaningful alt + dimensions -->\n  <figure>\n    <img\n      src=\"python-course.webp\"\n      alt=\"Student learning Python programming on laptop\"\n      width=\"400\"\n      height=\"300\"\n      loading=\"lazy\"\n    >\n    <figcaption>Python Fundamentals — 12 concepts</figcaption>\n  </figure>\n\n  <!-- Decorative: alt must be empty string -->\n  <img src=\"divider.svg\" alt=\"\" width=\"100\" height=\"4\">\n</section>",
                        "Gallery section with captioned course image and decorative divider")
                ),
                List.of(
                    "alt is required for accessibility — describe what the image shows, not 'image of'",
                    "Decorative images should have alt=\"\" — empty string tells screen readers to ignore it",
                    "Always specify width and height to prevent layout shift (CLS) during page load",
                    "Use loading=\"lazy\" for all images below the fold — improves page load time",
                    "WebP format gives better quality at smaller file size than JPEG and PNG"
                ),
                "Add loading=\"lazy\" to every image below the fold. This is one of the easiest performance improvements — images are not downloaded until the user scrolls near them, making the initial page load much faster.",
                List.of(
                    "Missing alt attribute — fails accessibility standards and SEO",
                    "Not specifying width and height — causes layout shift as images load",
                    "Using enormous images without compression — slows page load significantly"
                ),
                15, 6, "D"),

            conceptRich(html, "Lists",
                "HTML has three types of lists: unordered (ul), ordered (ol) and description (dl). Lists are used for navigation menus, steps, features, terms and any grouped content.",
                "Lists are everywhere on the web:\n- Navigation menus are ul lists\n- Step-by-step instructions are ol lists\n- FAQ pages use dl lists\n- Feature lists on landing pages are ul with icons\n\nLists provide semantic meaning — they tell browsers and screen readers that these items are related and grouped together. A nav menu built with ul is more accessible and SEO-friendly than a series of separate links.",
                "1. Unordered list (ul):\n- Items with bullet points (default)\n- Order does not matter — feature lists, navigation\n- <ul> contains <li> items\n- Bullets styled with CSS list-style-type\n\n2. Ordered list (ol):\n- Items with numbers (default)\n- Order matters — steps, rankings, instructions\n- start attribute: start numbering from a different number\n- reversed attribute: count down\n- type attribute: 1, A, a, I, i (number style)\n\n3. Description list (dl):\n- Term and description pairs\n- <dl> contains <dt> (term) and <dd> (description)\n- Used for glossaries, FAQs, metadata\n\n4. Nested lists:\n- List inside a list — sub-menu items\n- <li> can contain another <ul> or <ol>\n\n5. Removing default styling:\n- list-style: none to remove bullets/numbers\n- margin: 0; padding: 0 to remove indentation\n- Used for navigation menus",
                "<!-- Unordered list -->\n<ul>\n  <li>HTML</li>\n  <li>CSS</li>\n  <li>JavaScript</li>\n</ul>\n\n<!-- Ordered list -->\n<ol>\n  <li>Install Python</li>\n  <li>Write your first script</li>\n  <li>Run the file</li>\n</ol>\n\n<!-- Ordered — start from 5 -->\n<ol start=\"5\">\n  <li>Step 5</li>\n  <li>Step 6</li>\n</ol>\n\n<!-- Description list -->\n<dl>\n  <dt>HTML</dt>\n  <dd>HyperText Markup Language — structure of web pages</dd>\n  <dt>CSS</dt>\n  <dd>Cascading Style Sheets — styling and layout</dd>\n</dl>\n\n<!-- Nested list -->\n<ul>\n  <li>Frontend\n    <ul>\n      <li>HTML</li>\n      <li>CSS</li>\n      <li>JavaScript</li>\n    </ul>\n  </li>\n  <li>Backend\n    <ul>\n      <li>Python</li>\n      <li>Java</li>\n    </ul>\n  </li>\n</ul>",
                List.of(
                    new Concept.ConceptExample("Navigation menu using a list",
                        "The correct semantic HTML for a navigation bar.",
                        "<nav aria-label=\"Main navigation\">\n  <ul>\n    <li><a href=\"/\">Home</a></li>\n    <li><a href=\"/courses\">Courses</a></li>\n    <li><a href=\"/about\">About</a></li>\n    <li><a href=\"/contact\">Contact</a></li>\n  </ul>\n</nav>\n\n<!-- CSS removes bullets and makes horizontal -->\n<style>\n  nav ul { list-style: none; display: flex; gap: 1rem; padding: 0; margin: 0; }\n  nav a { text-decoration: none; }\n</style>",
                        "Horizontal navigation bar with no bullets")
                ),
                List.of(
                    "Use ul for items where order does not matter; ol for steps and rankings",
                    "Only li elements should be direct children of ul or ol",
                    "Navigation menus should be ul lists for semantic correctness and accessibility",
                    "dl is ideal for key-value content: glossaries, FAQs, product specifications"
                ),
                "Always wrap navigation links in a ul list inside a nav element. This is the semantic HTML standard — screen readers announce 'navigation' when they encounter nav, and the list structure helps users skip through menu items efficiently.",
                List.of(
                    "Putting non-li elements directly inside ul or ol — invalid HTML",
                    "Using ol when order does not matter — use ul for unordered content"
                ),
                12, 7, "E"),

            conceptRich(html, "HTML Entities and Special Characters",
                "HTML entities represent special characters that have meaning in HTML syntax or are not on a standard keyboard. They start with & and end with ; like &lt; for < and &amp; for &.",
                "Some characters cannot be used directly in HTML because they are part of the language syntax.\n\nIf you type < in your HTML, the browser thinks it is the start of a tag. If you want to display a literal < character, you must use the entity &lt;.\n\nEntities also cover:\n- Characters not on a standard keyboard: ©, ®, ™, €, £, ₹\n- Typographic characters: em dash —, curly quotes \"\", non-breaking space\n- Mathematical symbols: ×, ÷, ±, ∞",
                "1. Why entities exist:\n- <, >, &, \" are reserved in HTML syntax\n- Must be escaped to display as text content\n\n2. Essential entities:\n- &lt; → < (less than)\n- &gt; → > (greater than)\n- &amp; → & (ampersand)\n- &quot; → \" (double quote)\n- &apos; → ' (single quote)\n- &nbsp; → non-breaking space\n\n3. Common symbol entities:\n- &copy; → © (copyright)\n- &reg; → ® (registered trademark)\n- &trade; → ™ (trademark)\n- &euro; → € (euro)\n- &pound; → £ (pound)\n- &mdash; → — (em dash)\n- &ndash; → – (en dash)\n- &hellip; → … (ellipsis)\n- &times; → × (multiplication)\n- &divide; → ÷ (division)\n\n4. Numeric entities:\n- &#60; → < (decimal)\n- &#x3C; → < (hexadecimal)\n- Used when named entity is unknown\n\n5. Non-breaking space (&nbsp;):\n- Prevents line break at that point\n- Keeps words together: 10&nbsp;kg stays on same line\n- Avoid using for layout spacing — use CSS",
                "<!-- Reserved characters -->\n<p>5 &lt; 10 and 10 &gt; 5</p>\n<p>JavaScript uses &amp;&amp; for AND</p>\n<p>She said &quot;Hello&quot;</p>\n\n<!-- Symbols -->\n<p>&copy; 2024 LearnToEarn. All rights reserved.</p>\n<p>Price: &pound;99 or &euro;119 or &#x20B9;9,999</p>\n<p>React&trade; is developed by Meta&reg;</p>\n\n<!-- Em dash for punctuation -->\n<p>JavaScript &mdash; the language of the web.</p>\n\n<!-- Non-breaking space -->\n<p>Version&nbsp;2.0 will not break across lines.</p>\n\n<!-- Showing code in HTML -->\n<p>Use <code>&lt;img&gt;</code> to embed images.</p>\n<p>Use <code>&lt;a href=&quot;url&quot;&gt;</code> for links.</p>",
                List.of(
                    new Concept.ConceptExample("Displaying code snippets with entities",
                        "Use entities when showing HTML code inside HTML content.",
                        "<article>\n  <h2>How to create a link</h2>\n  <p>Use the anchor tag with an href attribute:</p>\n  <pre><code>&lt;a href=&quot;https://example.com&quot;&gt;Click here&lt;/a&gt;</code></pre>\n  <p>The <code>&amp;lt;</code> entity displays as &lt;</p>\n  <p>The <code>&amp;gt;</code> entity displays as &gt;</p>\n</article>",
                        "Article showing HTML code with angle brackets displayed correctly")
                ),
                List.of(
                    "&lt; and &gt; are required when displaying < and > as text content",
                    "&amp; is required when displaying & as text — without it browsers may misread it",
                    "&nbsp; creates a non-breaking space — prevents line breaks at that point",
                    "Use &copy; for copyright symbol in footers — &copy; 2024 Your Company",
                    "Numeric entities (&#60;) work everywhere; named entities (&lt;) are more readable"
                ),
                "When writing documentation or tutorials that show HTML code, always escape < as &lt; and > as &gt;. Otherwise the browser will try to parse your example code as real HTML tags.",
                List.of(
                    "Using & directly in URLs inside href without encoding — use &amp; in HTML attributes",
                    "Using &nbsp; repeatedly to create spacing — use CSS padding/margin instead"
                ),
                12, 8, "E"),

            conceptRich(html, "Semantic HTML",
                "Semantic HTML uses elements that describe their meaning and purpose. Instead of generic divs, semantic elements like header, nav, main, article, section and footer tell browsers and screen readers what each part of the page does.",
                "Before HTML5, web developers built pages entirely with div and span. Every section was a nameless box.\n\nHTML5 introduced semantic elements — tags that describe their role:\n- header: top of page or section\n- nav: navigation links\n- main: primary page content\n- article: self-contained content (blog post, news)\n- section: grouped related content\n- aside: sidebar, related links\n- footer: bottom of page or section\n- figure/figcaption: image with caption\n- time: date and time values\n\nSemantic HTML improves:\n- Accessibility: screen readers navigate by landmarks\n- SEO: search engines understand page structure\n- Readability: developers understand code faster",
                "1. Page-level semantic elements:\n- <header>: site header or section header — logo, nav, page title\n- <nav>: navigation links — main menu, breadcrumbs, pagination\n- <main>: primary content — only ONE per page\n- <footer>: site footer or section footer — copyright, links\n- <aside>: sidebar content — ads, related articles, author bio\n\n2. Content semantic elements:\n- <article>: self-contained content that makes sense standalone — blog post, product card, news article\n- <section>: thematic grouping of related content — use with a heading\n- <figure>: image, code, chart with optional caption\n- <figcaption>: caption for figure\n- <time datetime=\"2024-01-15\">: machine-readable date\n\n3. Text semantic elements (beyond formatting):\n- <address>: contact information\n- <blockquote cite=\"url\">: extended quotation\n- <cite>: title of cited work\n- <details>/<summary>: disclosure widget (accordion)\n- <mark>: highlighted/relevant text\n\n4. div vs section vs article:\n- div: generic, no semantic meaning — use when no semantic element fits\n- section: group of related content — should have a heading\n- article: standalone content — could be syndicated separately",
                "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <title>Blog Post</title>\n</head>\n<body>\n\n  <header>\n    <a href=\"/\"><img src=\"logo.svg\" alt=\"LearnToEarn\"></a>\n    <nav>\n      <ul>\n        <li><a href=\"/\">Home</a></li>\n        <li><a href=\"/courses\">Courses</a></li>\n      </ul>\n    </nav>\n  </header>\n\n  <main>\n    <article>\n      <header>\n        <h1>Getting Started with Python</h1>\n        <p>By <address>Ravi Kumar</address> on\n           <time datetime=\"2024-01-15\">January 15, 2024</time>\n        </p>\n      </header>\n\n      <section>\n        <h2>Introduction</h2>\n        <p>Python is easy to learn...</p>\n      </section>\n\n      <section>\n        <h2>Installation</h2>\n        <figure>\n          <img src=\"install.png\" alt=\"Python installer screenshot\">\n          <figcaption>Python 3.12 installer on Windows</figcaption>\n        </figure>\n      </section>\n    </article>\n\n    <aside>\n      <h2>Related Articles</h2>\n      <ul>\n        <li><a href=\"/python-basics\">Python Basics</a></li>\n      </ul>\n    </aside>\n  </main>\n\n  <footer>\n    <p>&copy; 2024 LearnToEarn</p>\n  </footer>\n\n</body>\n</html>",
                List.of(
                    new Concept.ConceptExample("div soup vs semantic HTML",
                        "Compare non-semantic and semantic versions of the same layout.",
                        "<!-- ❌ Div soup — no meaning -->\n<div class=\"header\">\n  <div class=\"nav\">...</div>\n</div>\n<div class=\"content\">\n  <div class=\"post\">...</div>\n  <div class=\"sidebar\">...</div>\n</div>\n<div class=\"footer\">...</div>\n\n<!-- ✅ Semantic HTML — meaningful structure -->\n<header>\n  <nav>...</nav>\n</header>\n<main>\n  <article>...</article>\n  <aside>...</aside>\n</main>\n<footer>...</footer>",
                        "Both render identically but semantic version is accessible and SEO-friendly")
                ),
                List.of(
                    "Use semantic elements instead of divs wherever possible — they add meaning for free",
                    "Only one <main> per page — it contains the primary unique content",
                    "header and footer can be used inside article and section, not just at page level",
                    "article is for standalone content; section is for grouped content with a heading",
                    "Screen readers use landmarks (header, nav, main, footer) to jump between sections"
                ),
                "Replace divs with semantic elements as a default. If you are writing <div class=\"header\">, use <header> instead. If you are writing <div class=\"nav\">, use <nav>. The CSS works the same and your HTML gains meaning.",
                List.of(
                    "Using section as a generic div — section should have a heading and group related content",
                    "Using multiple main elements — only one main is valid per page",
                    "Nesting article inside section when it should be the other way for blog posts"
                ),
                18, 9, "D"),

            conceptRich(html, "Tables",
                "HTML tables display tabular data in rows and columns. They should be used for data that has a natural row-and-column relationship, not for page layout.",
                "Tables are for data — think spreadsheets.\n\nComparison tables, pricing tables, schedules, results, statistics — these are all appropriate for HTML tables.\n\nTables should NEVER be used for page layout (creating columns and rows for the overall page design). CSS Grid and Flexbox do that job. Using tables for layout was a practice from the 1990s that causes serious accessibility problems.\n\nA proper table uses semantic elements: thead, tbody, tfoot, th, td — these tell screen readers which cells are headers and which are data.",
                "1. Basic table structure:\n- <table>: the table container\n- <tr>: table row\n- <th>: table header cell — bold, centred by default\n- <td>: table data cell\n\n2. Semantic grouping:\n- <thead>: wraps header rows\n- <tbody>: wraps data rows\n- <tfoot>: wraps footer rows (totals, summaries)\n\n3. Spanning cells:\n- colspan=\"2\": cell spans 2 columns\n- rowspan=\"2\": cell spans 2 rows\n\n4. Accessibility attributes:\n- scope=\"col\" on th: this header is for the column\n- scope=\"row\" on th: this header is for the row\n- <caption>: table title — read first by screen readers\n\n5. Styling:\n- border-collapse: collapse — removes double borders\n- nth-child for zebra stripes\n- Responsive tables: overflow-x: auto on container",
                "<table>\n  <caption>Student Results — January 2024</caption>\n\n  <thead>\n    <tr>\n      <th scope=\"col\">Name</th>\n      <th scope=\"col\">Subject</th>\n      <th scope=\"col\">Score</th>\n      <th scope=\"col\">Grade</th>\n    </tr>\n  </thead>\n\n  <tbody>\n    <tr>\n      <td>Ravi Kumar</td>\n      <td>Python</td>\n      <td>85</td>\n      <td>B</td>\n    </tr>\n    <tr>\n      <td>Priya Sharma</td>\n      <td>Python</td>\n      <td>92</td>\n      <td>A</td>\n    </tr>\n  </tbody>\n\n  <tfoot>\n    <tr>\n      <td colspan=\"2\">Class Average</td>\n      <td>88.5</td>\n      <td>B+</td>\n    </tr>\n  </tfoot>\n</table>",
                List.of(
                    new Concept.ConceptExample("Table with colspan and rowspan",
                        "Merge cells across columns and rows.",
                        "<table border=\"1\">\n  <thead>\n    <tr>\n      <th rowspan=\"2\">Name</th>  <!-- spans 2 rows -->\n      <th colspan=\"2\">Scores</th>  <!-- spans 2 columns -->\n    </tr>\n    <tr>\n      <th>Mid Term</th>\n      <th>Final</th>\n    </tr>\n  </thead>\n  <tbody>\n    <tr>\n      <td>Ravi</td>\n      <td>78</td>\n      <td>85</td>\n    </tr>\n    <tr>\n      <td>Priya</td>\n      <td>88</td>\n      <td>92</td>\n    </tr>\n  </tbody>\n</table>",
                        "Table with merged Name header cell and Scores spanning two sub-columns")
                ),
                List.of(
                    "Use tables for tabular data only — never for page layout",
                    "Always include thead, tbody for proper structure and accessibility",
                    "Use th for header cells with scope attribute to help screen readers",
                    "caption provides a table title — always add for data tables",
                    "Use colspan and rowspan to merge cells when data naturally spans multiple columns/rows"
                ),
                "Add border-collapse: collapse to every table in your CSS. The default table has double borders between cells which looks unprofessional. border-collapse merges them into single lines.",
                List.of(
                    "Using tables for layout — use CSS Grid or Flexbox instead",
                    "Missing thead/tbody — tables without semantic grouping are harder to style and read",
                    "Mismatched colspan/rowspan causing missing cells — browser renders unpredictably"
                ),
                15, 10, "D"),

            conceptRich(html, "Forms and Inputs",
                "HTML forms collect user input. The form element groups inputs, the action and method attributes control where data is sent, and input types, labels and validation attributes create a complete user experience.",
                "Forms are how users interact with web applications.\n\nEvery login page, search bar, registration form, checkout page and contact form is an HTML form.\n\nA form has two essential parts:\n1. The <form> element — defines where data goes and how\n2. Input elements — the actual fields users fill in\n\nEvery input needs a <label> — this connects the text description to the field, making it accessible and clickable.",
                "1. Form element:\n- action: URL where form data is sent\n- method: GET (visible in URL) or POST (in request body)\n- GET: search forms, safe/idempotent actions\n- POST: login, registration, any data modification\n\n2. Input types:\n- text: single-line text\n- email: email validation built in\n- password: masked input\n- number: numeric with up/down arrows\n- tel: telephone number\n- url: URL validation\n- checkbox: on/off toggle\n- radio: one choice from a group\n- file: file upload\n- date, time, datetime-local: date/time pickers\n- range: slider\n- color: colour picker\n- search: search field with clear button\n- hidden: invisible, submits with form\n- submit, reset, button: form actions\n\n3. Label:\n- <label for=\"id\">: connects to input via id\n- Clicking label focuses the input\n- Always required for accessibility\n\n4. Validation attributes:\n- required: field must be filled\n- minlength, maxlength: text length limits\n- min, max: number range\n- pattern: regex pattern validation\n- type validation: email, url enforce format\n\n5. Other form elements:\n- <textarea>: multi-line text\n- <select>/<option>: dropdown\n- <fieldset>/<legend>: group related inputs\n- <datalist>: suggestions for text input",
                "<form action=\"/register\" method=\"POST\">\n\n  <fieldset>\n    <legend>Personal Details</legend>\n\n    <div>\n      <label for=\"name\">Full Name *</label>\n      <input type=\"text\" id=\"name\" name=\"name\"\n             placeholder=\"Ravi Kumar\" required minlength=\"2\">\n    </div>\n\n    <div>\n      <label for=\"email\">Email *</label>\n      <input type=\"email\" id=\"email\" name=\"email\"\n             placeholder=\"ravi@example.com\" required>\n    </div>\n\n    <div>\n      <label for=\"password\">Password *</label>\n      <input type=\"password\" id=\"password\" name=\"password\"\n             required minlength=\"8\">\n    </div>\n\n    <div>\n      <label for=\"age\">Age</label>\n      <input type=\"number\" id=\"age\" name=\"age\" min=\"18\" max=\"100\">\n    </div>\n  </fieldset>\n\n  <fieldset>\n    <legend>Preferences</legend>\n\n    <div>\n      <label for=\"course\">Course</label>\n      <select id=\"course\" name=\"course\">\n        <option value=\"\">Select a course</option>\n        <option value=\"python\">Python</option>\n        <option value=\"js\">JavaScript</option>\n        <option value=\"css\">CSS</option>\n      </select>\n    </div>\n\n    <div>\n      <label>\n        <input type=\"checkbox\" name=\"newsletter\" value=\"yes\">\n        Subscribe to newsletter\n      </label>\n    </div>\n  </fieldset>\n\n  <div>\n    <label for=\"bio\">About You</label>\n    <textarea id=\"bio\" name=\"bio\" rows=\"4\" placeholder=\"Tell us about yourself\"></textarea>\n  </div>\n\n  <button type=\"submit\">Register</button>\n</form>",
                List.of(
                    new Concept.ConceptExample("Radio buttons and checkboxes",
                        "Group radio buttons by name; checkboxes are independent.",
                        "<form>\n  <!-- Radio: name groups them — only one can be selected -->\n  <fieldset>\n    <legend>Preferred Language</legend>\n    <label><input type=\"radio\" name=\"lang\" value=\"python\"> Python</label>\n    <label><input type=\"radio\" name=\"lang\" value=\"js\"> JavaScript</label>\n    <label><input type=\"radio\" name=\"lang\" value=\"java\"> Java</label>\n  </fieldset>\n\n  <!-- Checkboxes: independent — multiple can be selected -->\n  <fieldset>\n    <legend>Interests (select all that apply)</legend>\n    <label><input type=\"checkbox\" name=\"int\" value=\"web\"> Web Dev</label>\n    <label><input type=\"checkbox\" name=\"int\" value=\"data\"> Data Science</label>\n    <label><input type=\"checkbox\" name=\"int\" value=\"ai\"> AI/ML</label>\n  </fieldset>\n\n  <button type=\"submit\">Submit</button>\n</form>",
                        "Form with radio group and independent checkboxes")
                ),
                List.of(
                    "Every input must have a corresponding label — connect them with matching for and id",
                    "Use POST for forms that change data (login, register, submit); GET for searches",
                    "required, email, min, max attributes provide built-in browser validation for free",
                    "Radio buttons sharing the same name means only one can be selected",
                    "name attribute is required for form data to be submitted — without it the field is ignored"
                ),
                "Use the correct input type for each field. type=\"email\" gives mobile users the @ keyboard, validates format, and prevents invalid submissions. type=\"number\" gives a numeric keyboard on mobile. These free improvements require zero JavaScript.",
                List.of(
                    "Missing label elements — inputs without labels are inaccessible",
                    "Missing name attribute on inputs — data not included in form submission",
                    "Using GET for login forms — passwords appear in the URL and browser history"
                ),
                20, 11, "D"),

            conceptRich(html, "Audio and Video Tags",
                "HTML5 provides native <audio> and <video> elements to embed media directly in web pages without plugins. Multiple source formats ensure cross-browser compatibility.",
                "Before HTML5, embedding video or audio required Flash or Java plugins.\n\nHTML5 changed this with native media elements:\n- <video> for video files\n- <audio> for audio files\n\nBoth support multiple source files — the browser picks the first format it supports. Both have a controls attribute that shows play/pause/volume/fullscreen buttons.\n\nFor production sites, most developers use hosted video services (YouTube, Vimeo) via <iframe> for video, but understanding the native elements is essential.",
                "1. Video element:\n- <video src=\"file.mp4\" controls>\n- controls: shows player controls\n- autoplay: plays automatically (muted required in most browsers)\n- muted: starts muted\n- loop: plays on repeat\n- poster: thumbnail image shown before play\n- width, height: dimensions\n- preload: none, metadata, auto\n\n2. Audio element:\n- <audio src=\"file.mp3\" controls>\n- Same attributes: controls, autoplay, muted, loop, preload\n\n3. Multiple sources (cross-browser):\n- <source src=\"video.webm\" type=\"video/webm\">\n- <source src=\"video.mp4\" type=\"video/mp4\">\n- Browser uses first supported format\n- Fallback text for unsupported browsers\n\n4. Video formats:\n- MP4 (H.264): most widely supported\n- WebM: open format, good compression\n- OGG: older, less common\n\n5. Audio formats:\n- MP3: most widely supported\n- OGG: open format\n- WAV: uncompressed, large files\n\n6. Track element:\n- <track kind=\"subtitles\" src=\"subs.vtt\" srclang=\"en\">\n- Adds subtitles/captions for accessibility",
                "<!-- Video with multiple sources and poster -->\n<video\n  width=\"640\"\n  height=\"360\"\n  controls\n  poster=\"thumbnail.jpg\"\n  preload=\"metadata\"\n>\n  <source src=\"video.webm\" type=\"video/webm\">\n  <source src=\"video.mp4\" type=\"video/mp4\">\n  <track kind=\"subtitles\" src=\"subtitles-en.vtt\" srclang=\"en\" label=\"English\">\n  Your browser does not support the video element.\n</video>\n\n<!-- Autoplay muted (background video) -->\n<video autoplay muted loop playsinline>\n  <source src=\"background.mp4\" type=\"video/mp4\">\n</video>\n\n<!-- Audio player -->\n<audio controls>\n  <source src=\"podcast.mp3\" type=\"audio/mpeg\">\n  <source src=\"podcast.ogg\" type=\"audio/ogg\">\n  Your browser does not support audio.\n</audio>\n\n<!-- YouTube embed via iframe -->\n<iframe\n  width=\"560\" height=\"315\"\n  src=\"https://www.youtube.com/embed/VIDEO_ID\"\n  allowfullscreen\n  title=\"Course introduction video\"\n></iframe>",
                List.of(
                    new Concept.ConceptExample("Accessible video with captions",
                        "Add captions to video for hearing-impaired users.",
                        "<figure>\n  <video controls width=\"100%\" poster=\"course-thumb.jpg\">\n    <source src=\"lesson-1.mp4\" type=\"video/mp4\">\n    <track\n      kind=\"subtitles\"\n      src=\"lesson-1-en.vtt\"\n      srclang=\"en\"\n      label=\"English\"\n      default\n    >\n    <p>Your browser doesn't support HTML5 video.\n      <a href=\"lesson-1.mp4\">Download the video</a>.\n    </p>\n  </video>\n  <figcaption>Lesson 1: Introduction to Python</figcaption>\n</figure>",
                        "Video player with English subtitle track and fallback download link")
                ),
                List.of(
                    "Always provide multiple source formats — MP4 for widest compatibility",
                    "autoplay requires muted in most browsers — unmuted autoplay is blocked",
                    "Add poster attribute to show a thumbnail before the video plays",
                    "Add fallback text between video tags for browsers that do not support the element",
                    "Add track element with subtitles for accessibility compliance"
                ),
                "Add a WebM source before MP4. WebM is smaller and loads faster in Chrome and Firefox. Include MP4 as fallback for Safari. This simple change reduces video file size by 20-30% for most users.",
                List.of(
                    "Using autoplay without muted — browsers block it for user experience reasons",
                    "Only providing one video format — some browsers may not support it"
                ),
                15, 12, "D"),

            conceptRich(html, "iFrames",
                "The <iframe> element embeds another web page or resource inside the current page. Used for maps, videos, social media widgets and third-party content.",
                "An iframe (inline frame) is a window inside a window.\n\nIt creates a completely separate browsing context embedded within the current page. The embedded content has its own HTML, CSS and JavaScript — isolated from the parent page.\n\nCommon uses:\n- Embedding YouTube or Vimeo videos\n- Google Maps\n- Payment widgets (Stripe, PayPal)\n- Social media share buttons\n- Sandboxed third-party content\n\nSecurity is the primary concern with iframes — the sandbox attribute controls what the embedded content can do.",
                "1. Basic iframe:\n- <iframe src=\"url\" title=\"description\">...</iframe>\n- title attribute is required for accessibility\n- width, height: dimensions (or use CSS)\n\n2. Security attributes:\n- sandbox: restricts iframe capabilities\n  - sandbox=\"\": all restrictions on\n  - sandbox=\"allow-scripts\": allow JavaScript\n  - sandbox=\"allow-forms\": allow form submission\n  - sandbox=\"allow-same-origin\": allow same-origin access\n- allow: permission policy (camera, microphone, etc.)\n\n3. Common iframe uses:\n- YouTube embed: src=\"https://youtube.com/embed/ID\"\n- Google Maps: from Maps share > Embed a map\n- allowfullscreen: allow fullscreen mode\n\n4. srcdoc attribute:\n- Inline HTML content instead of external URL\n- <iframe srcdoc=\"<p>Hello</p>\">\n\n5. Security concerns:\n- Clickjacking: malicious pages can iframe your site\n- Prevent with X-Frame-Options: DENY header\n- Or Content-Security-Policy: frame-ancestors 'none'\n\n6. Accessibility:\n- Always add title attribute\n- Provide fallback content between iframe tags",
                "<!-- YouTube video embed -->\n<iframe\n  width=\"560\"\n  height=\"315\"\n  src=\"https://www.youtube.com/embed/dQw4w9WgXcQ\"\n  title=\"Introduction to Python Programming\"\n  frameborder=\"0\"\n  allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture\"\n  allowfullscreen\n></iframe>\n\n<!-- Google Maps embed -->\n<iframe\n  src=\"https://www.google.com/maps/embed?pb=...\"\n  width=\"600\"\n  height=\"450\"\n  style=\"border:0;\"\n  allowfullscreen\n  loading=\"lazy\"\n  title=\"Office location on Google Maps\"\n></iframe>\n\n<!-- Sandboxed iframe -->\n<iframe\n  src=\"https://example.com/widget\"\n  sandbox=\"allow-scripts allow-same-origin\"\n  title=\"Interactive widget\"\n  width=\"400\"\n  height=\"200\"\n></iframe>",
                List.of(
                    new Concept.ConceptExample("Sandboxed iframe for user content",
                        "Use sandbox to safely embed untrusted content.",
                        "<!-- Dangerous: no sandbox -->\n<iframe src=\"untrusted-content.html\"></iframe>\n\n<!-- Safe: restricted sandbox -->\n<iframe\n  src=\"user-preview.html\"\n  sandbox=\"allow-scripts\"\n  title=\"User code preview\"\n  width=\"100%\"\n  height=\"300\"\n></iframe>\n\n<!-- sandbox=\"\" (empty): no scripts, no forms, isolated origin -->\n<iframe\n  srcdoc=\"<h1>Hello!</h1><p>This is sandboxed HTML.</p>\"\n  sandbox=\"\"\n  title=\"Sandboxed content demo\"\n  width=\"400\"\n  height=\"200\"\n></iframe>",
                        "Sandboxed iframe showing safe isolated HTML content")
                ),
                List.of(
                    "Always add title attribute to iframes — required for screen readers",
                    "Use sandbox attribute to restrict what embedded content can do",
                    "add loading=\"lazy\" to iframes below the fold for performance",
                    "iframes create a separate browsing context — CSS from parent does not affect content inside",
                    "Prevent your own site from being iframed by adding X-Frame-Options: SAMEORIGIN header"
                ),
                "Always add sandbox to third-party iframes you do not control. At minimum, sandbox=\"allow-scripts allow-same-origin\" prevents the embedded page from navigating your page, accessing localStorage, or opening popups.",
                List.of(
                    "Missing title attribute — iframes without titles are inaccessible",
                    "Embedding untrusted content without sandbox — security risk"
                ),
                15, 13, "D"),

            conceptRich(html, "Meta Tags and SEO Basics",
                "Meta tags in the <head> provide information about the page to browsers, search engines and social media platforms. They control how the page appears in search results and when shared.",
                "Meta tags are invisible to users but critical for:\n- Search Engine Optimization (SEO): title and description in search results\n- Social media sharing: Open Graph tags control preview cards\n- Browser behaviour: charset, viewport, theme-color\n- Page indexing: robots meta tells search engines what to crawl\n\nThe title and meta description are the most important — they are what users see in Google search results before clicking.",
                "1. Essential meta tags:\n- <meta charset=\"UTF-8\">: character encoding — always first\n- <meta name=\"viewport\">: mobile scaling — always include\n- <title>: shown in browser tab and search results (50-60 chars)\n- <meta name=\"description\">: search result snippet (150-160 chars)\n\n2. SEO meta tags:\n- robots: index/noindex, follow/nofollow\n- canonical: preferred URL to avoid duplicate content\n- author: content author\n- keywords: largely ignored by Google now\n\n3. Open Graph (social sharing):\n- og:title: title when shared on Facebook/LinkedIn\n- og:description: description when shared\n- og:image: preview image when shared\n- og:url: canonical URL\n- og:type: website, article, product\n\n4. Twitter Card:\n- twitter:card: summary, summary_large_image\n- twitter:title, twitter:description, twitter:image\n\n5. Other important tags:\n- <link rel=\"icon\" href=\"favicon.ico\">: browser tab icon\n- <link rel=\"canonical\">: avoid duplicate content\n- <meta name=\"theme-color\">: browser UI colour on mobile\n- <meta http-equiv=\"refresh\" content=\"5;url=/new\">: redirect",
                "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <!-- Essential -->\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n\n  <!-- SEO -->\n  <title>Learn Python Online | LearnToEarn</title>\n  <meta name=\"description\" content=\"Master Python from beginner to expert with 60 structured concepts, quizzes and hands-on projects. Start free today.\">\n  <meta name=\"robots\" content=\"index, follow\">\n  <link rel=\"canonical\" href=\"https://learntoearn.com/python\">\n\n  <!-- Open Graph for social sharing -->\n  <meta property=\"og:title\" content=\"Learn Python Online | LearnToEarn\">\n  <meta property=\"og:description\" content=\"Master Python from beginner to expert.\">\n  <meta property=\"og:image\" content=\"https://learntoearn.com/og-python.jpg\">\n  <meta property=\"og:url\" content=\"https://learntoearn.com/python\">\n  <meta property=\"og:type\" content=\"website\">\n\n  <!-- Twitter Card -->\n  <meta name=\"twitter:card\" content=\"summary_large_image\">\n  <meta name=\"twitter:title\" content=\"Learn Python Online | LearnToEarn\">\n  <meta name=\"twitter:image\" content=\"https://learntoearn.com/og-python.jpg\">\n\n  <!-- Favicon -->\n  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/favicon.svg\">\n  <link rel=\"icon\" type=\"image/png\" href=\"/favicon.png\">\n\n  <!-- Theme colour for mobile browsers -->\n  <meta name=\"theme-color\" content=\"#9B6ED4\">\n</head>",
                List.of(
                    new Concept.ConceptExample("Complete SEO meta setup",
                        "All essential meta tags for a production web page.",
                        "<!-- Minimal production meta setup -->\n<head>\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <title>Python Basics Course | LearnToEarn</title>  <!-- 55 chars -->\n  <meta name=\"description\" content=\"Learn Python basics in 12 structured concepts with examples and quizzes. Free for students.\">  <!-- 155 chars -->\n  <link rel=\"canonical\" href=\"https://learntoearn.com/python-basics\">\n  <meta property=\"og:title\" content=\"Python Basics Course\">\n  <meta property=\"og:description\" content=\"Learn Python in 12 structured concepts.\">\n  <meta property=\"og:image\" content=\"https://learntoearn.com/python-og.jpg\">\n  <meta property=\"og:type\" content=\"website\">\n  <link rel=\"icon\" href=\"/favicon.ico\">\n</head>",
                        "Page with complete SEO and social sharing setup")
                ),
                List.of(
                    "Title should be 50-60 characters — longer gets cut in search results",
                    "Description should be 150-160 characters — this is your search result snippet",
                    "og:image should be at least 1200×630 pixels for best social media previews",
                    "canonical link prevents duplicate content penalties when same content exists at multiple URLs",
                    "meta keywords are ignored by Google — focus on title, description and content quality"
                ),
                "Test your Open Graph tags with Facebook's Sharing Debugger or LinkedIn's Post Inspector. These tools show exactly how your page will appear when shared — they reveal missing images or truncated descriptions before real users see them.",
                List.of(
                    "Missing viewport meta — page does not scale on mobile, Google penalises this",
                    "Duplicate title tags across pages — every page needs a unique, descriptive title",
                    "Missing og:image — social media shares show no preview image"
                ),
                18, 14, "D"),

            conceptRich(html, "HTML Accessibility (ARIA & alt text)",
                "Accessible HTML ensures web content works for users with disabilities — including screen reader users, keyboard-only users and those with visual impairments. ARIA attributes and semantic HTML are the primary tools.",
                "Accessibility means building for everyone.\n\nAbout 15% of the global population has some form of disability. Screen reader users navigate the web using only a keyboard and audio. Users with motor impairments may not use a mouse. Colour-blind users may miss information conveyed only by colour.\n\nGood news: most accessibility comes from using HTML correctly:\n- Semantic elements (header, nav, main, button) are accessible by default\n- Proper labels on form inputs\n- Meaningful alt text on images\n- Good heading structure\n- Sufficient colour contrast\n\nARIA (Accessible Rich Internet Applications) is for cases where HTML alone is not enough.",
                "1. Semantic HTML is the foundation:\n- Use button for buttons, not div with onclick\n- Use a for links, not span with onclick\n- Use label for form inputs\n- Use heading hierarchy h1→h2→h3\n- Use nav, main, header, footer as landmarks\n\n2. Alt text for images:\n- Meaningful: describe what the image conveys\n- Decorative: alt=\"\" (empty string)\n- Functional (button/link): describe the action\n- Charts: describe the data trend\n\n3. ARIA attributes:\n- aria-label=\"description\": label for element with no visible text\n- aria-labelledby=\"id\": reference another element as label\n- aria-describedby=\"id\": reference description element\n- aria-hidden=\"true\": hide from screen readers\n- aria-expanded=\"true/false\": for accordions/dropdowns\n- aria-required=\"true\": mark required fields\n- role: landmark roles (banner, navigation, main)\n\n4. Keyboard accessibility:\n- All interactive elements must be focusable\n- Tab navigates forward, Shift+Tab backward\n- Enter/Space activates buttons and links\n- tabindex=\"0\": add to tab order\n- tabindex=\"-1\": focusable by JS but not tab\n\n5. Focus management:\n- :focus styles must be visible — never do outline:none without replacement\n- Skip navigation links for keyboard users",
                "<!-- ❌ Inaccessible -->\n<div onclick=\"handleClick()\">Submit</div>\n<img src=\"chart.png\">\n<div class=\"nav\">...</div>\n\n<!-- ✅ Accessible -->\n<button type=\"submit\" onclick=\"handleClick()\">Submit</button>\n<img src=\"sales-chart.png\" alt=\"Bar chart showing 40% sales increase in Q2 2024\">\n<nav aria-label=\"Main navigation\">...</nav>\n\n<!-- Icon button — needs aria-label -->\n<button aria-label=\"Close dialog\">\n  <svg aria-hidden=\"true\" focusable=\"false\">...</svg>\n</button>\n\n<!-- Form with full accessibility -->\n<form>\n  <div>\n    <label for=\"email\">Email address</label>\n    <input\n      type=\"email\"\n      id=\"email\"\n      name=\"email\"\n      aria-required=\"true\"\n      aria-describedby=\"email-hint\"\n    >\n    <span id=\"email-hint\">We will never share your email</span>\n  </div>\n</form>\n\n<!-- Skip navigation -->\n<a href=\"#main-content\" class=\"skip-link\">Skip to main content</a>\n\n<!-- Accordion with ARIA -->\n<button\n  aria-expanded=\"false\"\n  aria-controls=\"faq-1\"\n  onclick=\"toggleFAQ(this)\"\n>\n  What is Python?\n</button>\n<div id=\"faq-1\" hidden>\n  <p>Python is a high-level programming language...</p>\n</div>",
                List.of(
                    new Concept.ConceptExample("Accessible modal dialog",
                        "A modal that manages focus and uses correct ARIA roles.",
                        "<button id=\"open-btn\" onclick=\"openModal()\">Open Modal</button>\n\n<div\n  id=\"modal\"\n  role=\"dialog\"\n  aria-modal=\"true\"\n  aria-labelledby=\"modal-title\"\n  hidden\n>\n  <h2 id=\"modal-title\">Confirm Action</h2>\n  <p>Are you sure you want to delete this item?</p>\n\n  <button onclick=\"closeModal()\">Cancel</button>\n  <button onclick=\"confirmDelete()\">Delete</button>\n</div>\n\n<script>\nfunction openModal() {\n  const modal = document.getElementById('modal');\n  modal.hidden = false;\n  modal.querySelector('button').focus(); // move focus inside\n}\nfunction closeModal() {\n  document.getElementById('modal').hidden = true;\n  document.getElementById('open-btn').focus(); // return focus\n}\n</script>",
                        "Modal dialog with proper focus management and ARIA roles")
                ),
                List.of(
                    "Use semantic HTML first — button, a, input are accessible by default",
                    "Never use div or span for interactive elements — screen readers cannot activate them",
                    "Every image needs alt — empty alt=\"\" for decorative, description for informative",
                    "Never use outline:none without providing a visible alternative focus style",
                    "aria-label is for elements with no visible text; aria-labelledby points to existing text"
                ),
                "Test your site with a screen reader. On Windows, NVDA is free. On Mac, VoiceOver is built in (Cmd+F5). Navigating your own site with eyes closed reveals accessibility issues that are invisible in visual testing.",
                List.of(
                    "Using div/span for buttons and links — not keyboard focusable or screen reader announced",
                    "Removing focus outlines with outline:none without replacement — keyboard users cannot see where they are",
                    "Using aria-label on elements that already have visible text — creates confusing duplicate announcements"
                ),
                18, 15, "C")
        );

        conceptRepository.saveAll(concepts);
        html.setTotalConcepts(concepts.size());
        subjectRepository.save(html);
        System.out.println("✅ HTML Fundamentals seeded — " + concepts.size() + " concepts");
    }

    // ─── CSS FUNDAMENTALS ────────────────────────────────────────────────────
    private void seedCSSFundamentals() {
        Subject css = subjectRepository.save(sub(
            "CSS Fundamentals",
            "Style web pages with CSS — selectors, colors, typography, box model, flexbox, grid, animations and responsive design",
            "🎨", "#264DE4", "A"
        ));
        css.setOverview("CSS turns plain HTML into visually polished web pages. This subject covers everything from selectors and the box model to modern layout systems like Flexbox and Grid, plus animations, responsive design, and CSS custom properties. The order is carefully structured so each concept builds on the previous one.");
        css.setWhyLearn("CSS is required for every frontend and full-stack role. Every web interface you see is styled with CSS. Employers test Flexbox, Grid and responsive design in frontend interviews. These skills directly translate to building real products.");
        css.setForWho("Students who have completed HTML Fundamentals. You should understand HTML tags, attributes and page structure before styling them.");
        css.setPrerequisites(List.of("HTML Fundamentals completed", "VS Code with Live Server extension"));
        css.setOutcomes(List.of(
            "Select HTML elements using any CSS selector",
            "Apply colours, backgrounds and typography styling",
            "Use the box model to control spacing and sizing",
            "Build layouts with Flexbox and CSS Grid",
            "Create responsive designs that work on all screen sizes",
            "Add transitions, transforms and animations",
            "Use CSS custom properties (variables)"
        ));
        css.setWhatYouWillBuild(List.of(
            "A styled personal profile card using box model and flexbox",
            "A responsive navigation bar",
            "A landing page with grid layout and animations"
        ));
        css.setToolsRequired(List.of("VS Code", "Live Server extension", "Chrome DevTools"));
        css.setDifficulty("Beginner");
        css.setEstimatedHours(14);
        css.setCareerUse("CSS is required for frontend developer, UI engineer and full-stack roles. Flexbox, Grid and responsive design are tested in every frontend interview. CSS animations and transitions are used in every modern web product.");
        subjectRepository.save(css);

        List<Concept> concepts = List.of(

            conceptRich(css, "How CSS Works",
                "CSS connects to HTML in three ways — inline, internal and external. The browser builds a render tree combining DOM and CSSOM to paint the page.",
                "Think of HTML as the skeleton of a person and CSS as their clothing and appearance.\n\nHTML gives you the structure. CSS decides how it looks — colours, sizes, fonts, spacing, layout.\n\nThere are three ways to add CSS to HTML:\n- Inline: style attribute directly on an element — messy, hard to maintain\n- Internal: style tag in the head — for single pages\n- External: separate .css file linked with link tag — professional, reusable\n\nExternal stylesheets are always the correct approach for real projects.",
                "1. Three ways to add CSS:\n- Inline: <p style=\"color:red\">Text</p> — highest specificity, not reusable\n- Internal: <style> p { color: red; } </style> inside <head>\n- External: <link rel=\"stylesheet\" href=\"styles.css\"> — best practice\n\n2. CSS rule structure:\n- selector { property: value; }\n- Selector: targets the HTML element\n- Property: what to change (color, font-size, margin)\n- Value: what to set it to\n- Declaration = property + value\n- Multiple declarations in one rule block\n\n3. CSS comments:\n- /* this is a comment */ — ignored by browser\n\n4. How the browser renders CSS:\n- Parse HTML → build DOM\n- Parse CSS → build CSSOM\n- Combine → Render Tree\n- Layout → Paint → Composite\n\n5. Cascade:\n- Multiple rules can apply to same element\n- Browser resolves conflicts using specificity and order\n- Later rules override earlier rules (same specificity)",
                "/* External stylesheet — styles.css */\n\n/* Basic rule syntax */\np {\n  color: #333;\n  font-size: 16px;\n  line-height: 1.6;\n}\n\nh1 {\n  color: #264DE4;\n  font-size: 2rem;\n  font-weight: 700;\n}\n\n/* Multiple selectors — same styles */\nh1, h2, h3 {\n  font-family: 'Arial', sans-serif;\n  margin-bottom: 1rem;\n}\n\n/* In HTML: -->\n<!-- <link rel=\"stylesheet\" href=\"styles.css\"> -->\n\n/* Inline (avoid) */\n/* <p style=\"color: red; font-size: 18px;\">Text</p> */",
                List.of(
                    new Concept.ConceptExample("External stylesheet — the correct approach",
                        "Create and link an external CSS file to HTML.",
                        "<!-- index.html -->\n<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <title>CSS Demo</title>\n  <link rel=\"stylesheet\" href=\"styles.css\">\n</head>\n<body>\n  <h1>Hello CSS!</h1>\n  <p>This paragraph is styled.</p>\n</body>\n</html>\n\n/* styles.css */\nbody {\n  font-family: Arial, sans-serif;\n  margin: 0;\n  padding: 20px;\n  background-color: #f5f5f5;\n}\n\nh1 {\n  color: #264DE4;\n}\n\np {\n  color: #555;\n  line-height: 1.6;\n}",
                        "Page with blue heading and grey paragraph on light background")
                ),
                List.of(
                    "Always use external stylesheets — one file styles all pages, and changes apply everywhere",
                    "CSS rule: selector { property: value; } — the semicolon after each declaration is required",
                    "Later rules override earlier rules when specificity is equal",
                    "The link tag goes inside head, not body",
                    "CSS comments use /* */ not // — double slash does not work in CSS"
                ),
                "Open Chrome DevTools (F12) → Elements tab → Styles panel while building. You can edit CSS live in the browser, see all applied styles, and instantly preview changes without saving files. This speeds up CSS learning dramatically.",
                List.of(
                    "Using // for CSS comments — only /* */ is valid in CSS",
                    "Putting link tag in body — always in head",
                    "Missing semicolons after property values — browser may ignore the whole rule"
                ),
                15, 1, "D"),

            conceptRich(css, "CSS Selectors",
                "CSS selectors target HTML elements to apply styles. From basic tag and class selectors to advanced combinators and attribute selectors, the right selector keeps your CSS clean and specific.",
                "Selectors answer the question: which elements should these styles apply to?\n\nCSS provides many ways to target elements:\n- By tag: p, h1, div — all elements of that type\n- By class: .card, .highlight — elements with that class\n- By id: #header — one specific element\n- By relationship: ul li, .nav > a — elements inside others\n- By attribute: input[type=\"email\"] — elements with specific attributes\n- By state: a:hover, input:focus — elements in a specific state\n\nChoosing the right selector makes your CSS maintainable. Over-specific selectors create the cascade problems you will learn about in the next concept.",
                "1. Basic selectors:\n- Element: p, h1, div — all matching elements\n- Class: .class-name — elements with that class\n- ID: #id-name — one specific element\n- Universal: * — all elements\n\n2. Grouping:\n- h1, h2, h3 {} — same styles for multiple selectors\n\n3. Combinator selectors:\n- Descendant: div p — p anywhere inside div\n- Child: div > p — p that is direct child of div\n- Adjacent sibling: h1 + p — p immediately after h1\n- General sibling: h1 ~ p — all p after h1 in same parent\n\n4. Attribute selectors:\n- [attr] — has attribute\n- [attr=\"val\"] — exact value\n- [attr^=\"val\"] — starts with\n- [attr$=\"val\"] — ends with\n- [attr*=\"val\"] — contains\n\n5. Pseudo-class selectors:\n- :hover, :focus, :active, :visited\n- :first-child, :last-child, :nth-child(n)\n- :not(.class)\n\n6. Pseudo-element selectors:\n- ::before, ::after — insert content\n- ::first-letter, ::first-line, ::placeholder",
                "/* Element selector */\np { color: #333; }\n\n/* Class selector */\n.card { background: white; border-radius: 8px; }\n\n/* ID selector */\n#main-title { font-size: 2.5rem; }\n\n/* Descendant */\n.nav a { text-decoration: none; }\n\n/* Direct child */\n.menu > li { display: inline-block; }\n\n/* Adjacent sibling */\nh2 + p { margin-top: 0; }\n\n/* Attribute selectors */\ninput[type=\"email\"] { border-color: blue; }\na[target=\"_blank\"]::after { content: \" ↗\"; }\n[data-role=\"admin\"] { background: gold; }\n\n/* Pseudo-classes */\na:hover { color: #264DE4; }\ninput:focus { outline: 2px solid blue; }\nli:first-child { font-weight: bold; }\nli:nth-child(2n) { background: #f5f5f5; } /* even rows */\n\n/* Pseudo-elements */\n.card::before {\n  content: '';\n  display: block;\n  height: 4px;\n  background: #264DE4;\n}",
                List.of(
                    new Concept.ConceptExample("Targeting specific elements cleanly",
                        "Use the right selector for each use case.",
                        "/* Target all navigation links */\nnav a { color: #333; text-decoration: none; }\nnav a:hover { color: #264DE4; }\n\n/* Target first item differently */\n.features-list li:first-child { font-weight: bold; color: #264DE4; }\n\n/* Zebra striping for table */\ntr:nth-child(even) { background-color: #f9f9f9; }\n\n/* Style required inputs */\ninput:required { border-left: 3px solid red; }\n\n/* Style valid inputs */\ninput:valid { border-color: green; }\n\n/* Add arrow after external links */\na[href^=\"https\"]::after { content: ' ↗'; font-size: 0.8em; }",
                        "Navigation links with hover effect, zebra table, styled form inputs")
                ),
                List.of(
                    "Class selectors (.name) are the most commonly used — prefer over ID selectors for styling",
                    "IDs (#name) should be used sparingly — mostly for JavaScript anchors and labels",
                    "Descendant (space): matches any level deep. Child (>): matches direct children only",
                    ":nth-child(2n) selects even elements, :nth-child(2n+1) selects odd",
                    "Attribute selectors are powerful for styling form inputs by type"
                ),
                "Build the habit of using classes for everything you style. Avoid using ID selectors in CSS — they are too specific and hard to override. Keep selector specificity low and consistent throughout your stylesheet.",
                List.of(
                    "Using IDs for styling — high specificity makes overriding difficult later",
                    "Overusing descendant selectors — .nav .menu .item .link is fragile and slow"
                ),
                18, 2, "D"),

            conceptRich(css, "CSS Specificity and Cascade",
                "When multiple CSS rules target the same element, specificity determines which rule wins. The cascade is the algorithm browsers use to resolve conflicts.",
                "CSS stands for Cascading Style Sheets. The cascade is the 'C' in CSS.\n\nWhen two rules conflict — both trying to set the same property on the same element — the browser must choose one. It does this using three factors in order:\n1. Importance (!important — avoid using)\n2. Specificity — how targeted the selector is\n3. Source order — later rules win ties\n\nSpecificity is calculated as a score:\n- Inline styles: 1000\n- ID selectors: 100\n- Class, attribute, pseudo-class: 10\n- Element, pseudo-element: 1",
                "1. Cascade order (highest to lowest priority):\n- !important declarations\n- Inline styles (style=\"\")\n- ID selectors (#id)\n- Class, attribute, pseudo-class selectors (.class, [attr], :hover)\n- Element selectors (div, p)\n- Universal selector (*)\n\n2. Specificity calculation:\n- Count IDs: x\n- Count classes/attributes/pseudo-classes: y\n- Count elements/pseudo-elements: z\n- Score: x,y,z — compare left to right\n- #header .nav a: 1,1,1 = 111 points\n- .nav a: 0,1,1 = 11 points\n- a: 0,0,1 = 1 point\n\n3. !important:\n- Overrides all specificity\n- Should only be used for utility classes and debugging\n- If you use it everywhere, you have a specificity problem\n\n4. Inheritance:\n- Some properties inherit from parent: color, font-family, font-size\n- Some do not: margin, padding, border, background\n- inherit keyword: force inheritance\n- initial keyword: reset to default\n- unset keyword: inherit if inheritable, else initial\n\n5. Source order:\n- When specificity is equal, the last rule wins\n- Order of stylesheets matters",
                "/* Specificity: 0,0,1 = 1 */\np { color: black; }\n\n/* Specificity: 0,1,0 = 10 — wins */\n.text { color: blue; }\n\n/* Specificity: 0,1,1 = 11 — wins */\np.text { color: green; }\n\n/* Specificity: 1,0,0 = 100 — wins */\n#main p { color: red; }\n\n/* !important — wins over all */\np { color: purple !important; }\n\n/* Example conflict */\n.btn { background: blue; }   /* 0,1,0 = 10 */\ndiv .btn { background: red; }  /* 0,1,1 = 11 — wins */\n\n/* Inheritance */\nbody {\n  font-family: Arial, sans-serif; /* inherited by all children */\n  color: #333;                     /* inherited */\n}\n\np {\n  /* No need to re-declare font-family — inherited from body */\n  margin: 0; /* NOT inherited — each element must set its own */\n}",
                List.of(
                    new Concept.ConceptExample("Debugging specificity conflicts",
                        "Understand why your CSS rule is not applying.",
                        "/* If this is not working: */\n.card p { color: grey; }  /* specificity: 0,1,1 */\n\n/* It might be overridden by: */\n#sidebar .card p { color: black; }  /* specificity: 1,1,1 — wins! */\n\n/* Solutions: */\n\n/* 1. Increase specificity */\n#sidebar .card p.description { color: grey; }\n\n/* 2. Use !important (sparingly) */\n.card p { color: grey !important; }\n\n/* 3. Better: avoid the conflict by restructuring CSS */\n.sidebar-card-text { color: grey; }  /* flat, low specificity */",
                        "Demonstrates specificity conflict and three ways to resolve it")
                ),
                List.of(
                    "Specificity score: IDs=100, Classes/attributes/pseudo-classes=10, Elements=1",
                    "Higher specificity always wins regardless of order — order only breaks ties",
                    "!important overrides everything — use it only for utilities, never for general styling",
                    "Inherited properties (color, font) flow down; non-inherited (margin, border) do not",
                    "Keep selectors as simple as possible — low specificity is easier to override and maintain"
                ),
                "Use Chrome DevTools to debug specificity. Click any element, go to Styles panel — rules are listed in specificity order. Crossed-out rules have been overridden. Hovering shows the exact specificity score.",
                List.of(
                    "Overusing !important to fix cascade issues — address the root specificity problem instead",
                    "Wondering why a style is not applying without checking specificity — always check DevTools"
                ),
                18, 3, "C"),

            conceptRich(css, "CSS Colors and Backgrounds",
                "CSS provides multiple color formats — hex, RGB, HSL and named colors. Background properties control background color, images, gradients, size and position.",
                "Color and background are the first visual impact of any web page.\n\nCSS supports several color formats:\n- Named: red, blue, tomato, cornflowerblue\n- Hex: #264DE4, #fff, #333\n- RGB: rgb(38, 77, 228)\n- RGBA: rgba(38, 77, 228, 0.5) — with transparency\n- HSL: hsl(228, 71%, 52%) — hue, saturation, lightness\n- HSLA: hsla(228, 71%, 52%, 0.5)\n\nHex is the most common in codebases. HSL is the most intuitive for creating colour variations.",
                "1. Color formats:\n- Named: 140 standard names — limited but readable\n- Hex: #RRGGBB (00-FF per channel) or shorthand #RGB\n- RGB: rgb(0-255, 0-255, 0-255)\n- RGBA: rgba(r, g, b, 0-1) — alpha is opacity\n- HSL: hsl(0-360deg, 0-100%, 0-100%)\n- Modern: rgb() supports decimals and /alpha: rgb(255 0 0 / 50%)\n\n2. Color properties:\n- color: text color\n- background-color: background fill\n- border-color: border color\n- outline-color: outline color\n- opacity: 0-1 — affects entire element including children\n\n3. Background shorthand:\n- background: color image repeat position/size\n- background-color\n- background-image: url('img.jpg') or gradient\n- background-repeat: repeat, no-repeat, repeat-x, repeat-y\n- background-size: cover, contain, auto, px or %\n- background-position: center, top left, 50% 50%\n- background-attachment: scroll, fixed (parallax)\n\n4. Gradients:\n- linear-gradient(direction, color1, color2)\n- radial-gradient(shape, color1, color2)\n- conic-gradient()\n- Gradients are treated as images\n\n5. currentColor:\n- Uses the element's color value\n- Useful for SVG and border to match text colour",
                "/* Color formats */\n.text-blue { color: #264DE4; }\n.text-red { color: rgb(239, 68, 68); }\n.text-muted { color: rgba(0, 0, 0, 0.5); }\n.text-purple { color: hsl(263, 54%, 62%); }\n\n/* Backgrounds */\n.card { background-color: #f9fafb; }\n.hero {\n  background-image: url('hero.jpg');\n  background-size: cover;\n  background-position: center;\n  background-repeat: no-repeat;\n}\n\n/* Gradient backgrounds */\n.gradient-btn {\n  background: linear-gradient(135deg, #264DE4, #9B6ED4);\n  color: white;\n}\n.radial {\n  background: radial-gradient(circle, #fff, #264DE4);\n}\n\n/* Multiple backgrounds */\n.layered {\n  background:\n    linear-gradient(rgba(0,0,0,0.4), rgba(0,0,0,0.4)),\n    url('photo.jpg') center/cover no-repeat;\n}\n\n/* CSS color variables for consistent palette */\n:root {\n  --primary: #264DE4;\n  --secondary: #9B6ED4;\n  --text: #1a1a1a;\n  --muted: #6b7280;\n}",
                List.of(
                    new Concept.ConceptExample("Background image with text overlay",
                        "Create a hero section with image background and dark overlay.",
                        ".hero {\n  background-image:\n    linear-gradient(rgba(0, 0, 0, 0.6), rgba(0, 0, 0, 0.6)),\n    url('mountain.jpg');\n  background-size: cover;\n  background-position: center;\n  height: 400px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  text-align: center;\n  color: white;\n}\n\n.hero h1 {\n  font-size: 3rem;\n  margin: 0;\n}\n\n.hero p {\n  font-size: 1.2rem;\n  opacity: 0.9;\n}",
                        "Hero section: dark overlay on background image with centered white text")
                ),
                List.of(
                    "Hex #264DE4 = R:38, G:77, B:228 — use a colour picker to convert",
                    "rgba() adds transparency to any colour without affecting child elements (unlike opacity)",
                    "background-size: cover fills the container, contain fits inside without cropping",
                    "Layer a dark gradient over an image background to make text readable on top",
                    "HSL is best for creating colour variations — change lightness to get tints and shades"
                ),
                "Use CSS custom properties for your colour palette. Define --primary, --secondary, --text in :root and use var(--primary) everywhere. When the brand colour changes, you update one line instead of hundreds.",
                List.of(
                    "Using opacity on a coloured background when you want transparent background only — use rgba() instead",
                    "Forgetting background-size: cover — background image tiles by default"
                ),
                15, 4, "D"),

            conceptRich(css, "CSS Typography",
                "Typography CSS controls fonts, sizes, weights, spacing and text alignment. Choosing the right font and setting readable text properties makes a huge difference to user experience.",
                "Typography is the art of arranging text so it is readable and beautiful.\n\nOn the web, typography involves:\n- font-family: which font to use\n- font-size: how big\n- font-weight: how bold\n- line-height: spacing between lines\n- letter-spacing: spacing between characters\n- text-align: left, center, right, justify\n- color: text colour\n\nGoogle Fonts provides hundreds of free web fonts. You can use them by adding a link in your HTML head.\n\nTypographic scale — a consistent set of sizes — makes text hierarchy feel polished.",
                "1. Font family:\n- font-family: 'Roboto', Arial, sans-serif\n- Always include fallback fonts\n- Generic families: serif, sans-serif, monospace, cursive\n- Web safe fonts: Arial, Georgia, Times New Roman, Courier New\n- Custom fonts via @font-face or Google Fonts link\n\n2. Font size:\n- px: fixed pixels\n- rem: relative to root (html) font size — best for accessibility\n- em: relative to parent font size\n- %: relative to parent\n- viewport units: vw, vh — responsive text\n\n3. Font weight:\n- font-weight: 100-900 or named (bold=700, normal=400)\n\n4. Line height and letter spacing:\n- line-height: 1.5 — unitless ratio (recommended)\n- letter-spacing: 0.05em — space between letters\n- word-spacing: space between words\n\n5. Text properties:\n- text-align: left, center, right, justify\n- text-transform: uppercase, lowercase, capitalize\n- text-decoration: none, underline, line-through\n- text-shadow: offsetX offsetY blur color\n- white-space: nowrap, pre, pre-line\n- overflow: hidden; text-overflow: ellipsis — truncate\n\n6. Google Fonts:\n- Add <link> from fonts.google.com in HTML head\n- Then use in font-family CSS",
                "/* Google Font import */\n/* In HTML: <link href=\"https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap\" rel=\"stylesheet\"> */\n\nbody {\n  font-family: 'Roboto', Arial, sans-serif;\n  font-size: 16px;     /* base size */\n  line-height: 1.6;    /* comfortable reading */\n  color: #333;\n}\n\nh1 {\n  font-size: 2.5rem;   /* 40px if base is 16px */\n  font-weight: 700;\n  line-height: 1.2;\n  letter-spacing: -0.02em;\n}\n\nh2 { font-size: 2rem; font-weight: 600; }\nh3 { font-size: 1.5rem; font-weight: 600; }\n\n.caption {\n  font-size: 0.875rem;\n  color: #6b7280;\n  text-transform: uppercase;\n  letter-spacing: 0.08em;\n}\n\n.truncate {\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  max-width: 200px;\n}\n\n.heading-gradient {\n  background: linear-gradient(135deg, #264DE4, #9B6ED4);\n  -webkit-background-clip: text;\n  -webkit-text-fill-color: transparent;\n  background-clip: text;\n}",
                List.of(
                    new Concept.ConceptExample("Typographic scale and hierarchy",
                        "Create a consistent type system for a web page.",
                        "/* Typographic scale using rem */\n:root { font-size: 16px; }\n\nh1 { font-size: 3rem;    /* 48px */ }\nh2 { font-size: 2rem;    /* 32px */ }\nh3 { font-size: 1.5rem;  /* 24px */ }\nh4 { font-size: 1.25rem; /* 20px */ }\nbody { font-size: 1rem;  /* 16px */ }\nsmall { font-size: 0.875rem; /* 14px */ }\n\n/* Readable body text */\np {\n  font-size: 1rem;\n  line-height: 1.7;\n  max-width: 65ch;  /* optimal reading width */\n  color: #374151;\n}\n\n/* Heading with eyebrow label */\n.eyebrow {\n  font-size: 0.75rem;\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: 0.1em;\n  color: #264DE4;\n}",
                        "Page with clear typographic hierarchy from large h1 to small body text")
                ),
                List.of(
                    "Use rem for font sizes — they scale with the user's browser font preference",
                    "Line-height of 1.5-1.7 is optimal for body text readability",
                    "Use unitless line-height (1.5 not 1.5px) — it scales with font size",
                    "Limit body text width with max-width: 65ch for optimal reading line length",
                    "Always include font fallbacks: 'Roboto', Arial, sans-serif"
                ),
                "Set a base font-size on the html or body element and use rem for all other sizes. When a user increases their browser font size for accessibility, rem-based sizes scale correctly. px sizes stay fixed and override the user's preference.",
                List.of(
                    "Using px for all font sizes — breaks user's accessibility font size preferences",
                    "Line-height less than 1.2 — text lines are too close together to read comfortably"
                ),
                15, 5, "D"),

            conceptRich(css, "CSS Units",
                "CSS units define the size of elements and values. Absolute units like px are fixed; relative units like rem, em, % and vw scale with context. Choosing the right unit matters for responsive and accessible design.",
                "CSS values need units — 16 what? 16 pixels? 16% of the parent? 16 times the font size?\n\nDifferent units are appropriate for different properties:\n- Font sizes: rem for accessibility\n- Padding/margins: rem or px\n- Widths: % or vw for responsive layouts\n- Heights: avoid fixed heights — let content determine height\n- Borders: px\n- Line height: unitless number",
                "1. Absolute units:\n- px: pixels — most common, fixed size\n- pt: points (print only)\n- cm, mm, in: physical measurements (print only)\n\n2. Relative to font size:\n- em: relative to the element's own font size\n  - 2em on an element with font-size:16px = 32px\n  - Compounds when nested — can cause unexpected sizing\n- rem: relative to the ROOT font size (html element)\n  - Same root reference always — no compounding\n  - Best for font sizes and consistent spacing\n\n3. Relative to viewport:\n- vw: 1% of viewport width — 100vw = full width\n- vh: 1% of viewport height — 100vh = full height\n- vmin: 1% of smaller viewport dimension\n- vmax: 1% of larger viewport dimension\n- dvh: dynamic viewport height (mobile-safe, fixes 100vh on mobile)\n\n4. Percentage:\n- % of the parent element's size\n- For width: % of parent width\n- For padding/margin: % of parent WIDTH (even vertical!)\n\n5. Intrinsic sizing keywords:\n- auto: browser calculates\n- min-content: smallest the content can be\n- max-content: largest without wrapping\n- fit-content: as big as content, capped at parent\n\n6. Best practices:\n- Font sizes: rem\n- Spacing (padding/margin): rem or px\n- Layout widths: % or vw\n- Media queries: em or px\n- Borders: px\n- Line-height: unitless",
                "/* Font sizing */\nhtml { font-size: 16px; }  /* 1rem = 16px */\n\nh1 { font-size: 2.5rem; }  /* 40px */\np  { font-size: 1rem; }    /* 16px */\nsmall { font-size: 0.875rem; } /* 14px */\n\n/* Spacing with rem */\n.card {\n  padding: 1.5rem;    /* 24px */\n  margin-bottom: 2rem; /* 32px */\n  border-radius: 0.5rem; /* 8px */\n}\n\n/* Responsive widths */\n.container {\n  width: 90%;            /* relative to parent */\n  max-width: 1200px;\n  margin: 0 auto;\n}\n\n/* Full viewport height */\n.hero {\n  height: 100vh;         /* full browser height */\n  min-height: 100dvh;    /* dynamic — mobile safe */\n}\n\n/* em compounds — watch out */\n.parent { font-size: 1.25em; }  /* 20px if root is 16 */\n.parent .child { font-size: 1.25em; }  /* 25px! */\n\n/* rem does not compound */\n.parent { font-size: 1.25rem; }  /* 20px */\n.parent .child { font-size: 1.25rem; }  /* still 20px */",
                List.of(
                    new Concept.ConceptExample("Responsive layout with mixed units",
                        "Use appropriate units for each property.",
                        ".page {\n  /* Container: percentage width */\n  max-width: 1200px;\n  width: 90%;\n  margin: 0 auto;\n  padding: 0 1rem;\n}\n\n.hero {\n  /* Viewport: full screen height */\n  min-height: 100vh;\n  padding: 4rem 2rem;  /* rem: consistent spacing */\n}\n\n.hero h1 {\n  font-size: clamp(2rem, 5vw, 4rem); /* fluid between 32-64px */\n}\n\n.card {\n  padding: 1.5rem;     /* rem: scales with user prefs */\n  border: 1px solid #e5e7eb; /* px: always 1 pixel */\n  border-radius: 8px;  /* px: fixed corner radius */\n}",
                        "Responsive page layout with mixed appropriate units")
                ),
                List.of(
                    "Use rem for font sizes — scales with user's browser font preference",
                    "Use % or vw for layout widths — responsive to container and viewport",
                    "100vh on mobile can be taller than visible area — use 100dvh for mobile layouts",
                    "em compounds when nested — prefer rem for consistent results",
                    "Unitless line-height (1.5) scales with font size; line-height in px is fixed"
                ),
                "Avoid setting fixed heights with px or vh on content containers. Content amounts change and fixed heights cause overflow. Use min-height instead of height, and let the container grow naturally with its content.",
                List.of(
                    "Using px for font sizes exclusively — breaks accessibility font scaling",
                    "Setting height: 100vh and forgetting mobile address bar adds to viewport height — use dvh"
                ),
                15, 6, "D"),

            conceptRich(css, "Box Model",
                "Every HTML element is a rectangular box. The CSS box model defines how width, height, padding, border and margin combine to determine the element's size and spacing.",
                "The box model is the most fundamental concept in CSS layout.\n\nEvery element on the page is a box. That box has four layers from inside to outside:\n1. Content: the actual text or image\n2. Padding: space between content and border\n3. Border: the line around the element\n4. Margin: space outside the border, between elements\n\nThe critical question: what does width and height refer to?\n\nBy default (content-box): width = content only. Padding and border add to the total.\nWith box-sizing: border-box: width = content + padding + border. Total size stays predictable.",
                "1. Box model layers:\n- Content: width × height\n- Padding: inner space — background colour extends here\n- Border: line drawn around padding\n- Margin: outer space — transparent, does not show background\n\n2. box-sizing:\n- content-box (default): width = content only, total = width + padding + border\n- border-box: width = content + padding + border (most intuitive)\n- Always set: *, *::before, *::after { box-sizing: border-box; }\n\n3. Shorthand for padding and margin:\n- 1 value: all sides\n- 2 values: top/bottom left/right\n- 3 values: top left/right bottom\n- 4 values: top right bottom left (clockwise)\n\n4. Margin collapse:\n- Vertical margins between block elements collapse to the larger value\n- If parent has no padding/border, child margin bleeds out\n\n5. Border:\n- border: width style color — e.g. border: 1px solid #ccc\n- Individual sides: border-top, border-right, border-bottom, border-left\n- border-radius: rounds corners — 50% makes a circle\n\n6. outline:\n- Does NOT take up space — does not affect layout\n- Used for focus indicators",
                "/* Reset to border-box — always include */\n*, *::before, *::after {\n  box-sizing: border-box;\n}\n\n.card {\n  width: 300px;\n  padding: 24px;         /* all sides */\n  border: 1px solid #e5e7eb;\n  margin: 16px 0;        /* top/bottom 16px, left/right 0 */\n  border-radius: 8px;\n  background: white;\n}\n/* Total width = 300px (content + padding + border included) */\n\n/* Shorthand values */\n.box {\n  padding: 10px;               /* all 4 sides */\n  padding: 10px 20px;          /* top/bottom left/right */\n  padding: 10px 20px 15px;     /* top left/right bottom */\n  padding: 10px 20px 15px 5px; /* top right bottom left */\n}\n\n/* Circle using border-radius */\n.avatar {\n  width: 60px;\n  height: 60px;\n  border-radius: 50%;  /* circle */\n  object-fit: cover;\n}\n\n/* Margin auto — center block elements */\n.container {\n  max-width: 1200px;\n  margin: 0 auto;  /* 0 top/bottom, auto left/right = centred */\n}",
                List.of(
                    new Concept.ConceptExample("Box model in DevTools",
                        "See the box model layers visualised in Chrome DevTools.",
                        ".profile-card {\n  box-sizing: border-box;\n  width: 320px;\n  padding: 24px;\n  border: 2px solid #264DE4;\n  margin: 20px auto;\n  border-radius: 12px;\n  background: white;\n  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);\n}\n\n/* With box-sizing: border-box:\n   Total width = 320px (content shrinks to fit padding and border)\n\n   Without box-sizing: border-box:\n   Total width = 320 + 24+24 + 2+2 = 372px */",
                        "Card with blue border showing box model — inspect in DevTools to see layers")
                ),
                List.of(
                    "Always add box-sizing: border-box to * at the top of every CSS file",
                    "margin: 0 auto centres block elements horizontally — the element must have a width",
                    "Margin collapse: vertical margins between siblings merge to the larger value",
                    "Padding uses the background colour; margin is always transparent",
                    "border-radius: 50% on equal width/height element creates a perfect circle"
                ),
                "Open Chrome DevTools → click any element → scroll to the bottom of the Styles panel. You will see a visual box model diagram showing exact content, padding, border and margin values. Use this constantly while learning CSS layout.",
                List.of(
                    "Not adding box-sizing: border-box — adding padding makes elements wider than expected",
                    "Using margin: auto on inline elements — auto margin only works on block elements with a width"
                ),
                18, 7, "D"),

            conceptRich(css, "CSS Overflow",
                "The overflow property controls what happens when content is larger than its container. Options include hiding content, adding scrollbars, or letting content spill out.",
                "Overflow happens when content is bigger than its container.\n\nA paragraph of text inside a 100px tall div. An image wider than its frame. A list with more items than the container can show.\n\nThe overflow property controls what happens:\n- visible: content spills out (default)\n- hidden: overflow is clipped — invisible\n- scroll: always shows scrollbars\n- auto: scrollbars only when needed\n\nUnderstanding overflow is essential for fixed-height containers, sticky elements, modal dialogs and text truncation.",
                "1. overflow values:\n- visible: default — content spills outside box\n- hidden: clips content at box edge — no scrollbar\n- scroll: always shows scrollbars (even if not needed)\n- auto: scrollbars only when content overflows — preferred\n- clip: clips without creating scroll context (newer)\n\n2. overflow-x and overflow-y:\n- overflow-x: horizontal scrolling/clipping\n- overflow-y: vertical scrolling/clipping\n- Set independently for each axis\n\n3. Common use cases:\n- overflow: hidden on parent to contain floats\n- overflow: auto for scrollable boxes\n- overflow-x: auto for wide tables on mobile\n- overflow: hidden + border-radius to clip images\n\n4. Text overflow (with overflow: hidden):\n- white-space: nowrap — prevent text wrapping\n- overflow: hidden — clip overflowing text\n- text-overflow: ellipsis — show ... at cutoff\n- All three required together for ellipsis\n\n5. Creating scroll containers:\n- overflow: auto on container + fixed height/max-height\n- Useful for sidebars, chat lists, modal content",
                "/* Basic overflow */\n.box {\n  width: 200px;\n  height: 100px;\n  overflow: hidden;    /* clip content */\n}\n\n.scrollable {\n  height: 300px;\n  overflow-y: auto;    /* scroll when needed */\n  overflow-x: hidden;  /* no horizontal scroll */\n}\n\n/* Text truncation with ellipsis */\n.card-title {\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  max-width: 200px;\n}\n\n/* Multi-line truncation */\n.card-description {\n  display: -webkit-box;\n  -webkit-line-clamp: 3;    /* show max 3 lines */\n  -webkit-box-orient: vertical;\n  overflow: hidden;\n}\n\n/* Overflow hidden clips border-radius on images */\n.avatar-wrapper {\n  width: 80px;\n  height: 80px;\n  border-radius: 50%;\n  overflow: hidden;    /* clips the img to the circle */\n}\n.avatar-wrapper img {\n  width: 100%;\n  height: 100%;\n  object-fit: cover;\n}\n\n/* Responsive table */\n.table-container {\n  overflow-x: auto;    /* scroll table on small screens */\n}\n.table-container table {\n  min-width: 600px;    /* table keeps full width, container scrolls */\n}",
                List.of(
                    new Concept.ConceptExample("Chat list with scrollable container",
                        "Fixed height sidebar that scrolls when messages overflow.",
                        ".chat-sidebar {\n  width: 280px;\n  height: 500px;\n  border: 1px solid #e5e7eb;\n  border-radius: 8px;\n  display: flex;\n  flex-direction: column;\n}\n\n.chat-messages {\n  flex: 1;\n  overflow-y: auto;    /* scroll when needed */\n  padding: 1rem;\n}\n\n.chat-input {\n  padding: 0.75rem;\n  border-top: 1px solid #e5e7eb;\n  flex-shrink: 0;      /* never shrinks, always visible */\n}\n\n.message {\n  padding: 0.5rem;\n  margin-bottom: 0.5rem;\n  background: #f3f4f6;\n  border-radius: 6px;\n}",
                        "Chat sidebar: messages scroll, input stays fixed at bottom")
                ),
                List.of(
                    "overflow: auto adds scrollbars only when needed — prefer over scroll",
                    "Ellipsis requires three properties together: white-space:nowrap + overflow:hidden + text-overflow:ellipsis",
                    "overflow: hidden on a parent clips child content — useful for image masks and border-radius",
                    "Wrap wide tables in a div with overflow-x: auto for mobile responsiveness",
                    "overflow:hidden creates a new block formatting context — useful for clearing floats"
                ),
                "Use overflow-x: auto on a wrapper div around tables to make them scroll on mobile instead of breaking the layout. This is one of the most common and easiest responsive design fixes.",
                List.of(
                    "Using overflow: scroll — always shows scrollbars even when unnecessary, looks bad on desktop",
                    "Forgetting white-space: nowrap when trying to add text-overflow: ellipsis"
                ),
                15, 8, "C"),

            conceptRich(css, "Display Properties",
                "The display property controls how an element participates in the layout. Block, inline, inline-block, flex, grid and none are the most important values.",
                "The display property is one of the most important in CSS.\n\nIt controls two things:\n1. How the element itself is placed in the flow\n2. How its children are laid out\n\nblock: full width, new line — div, p, h1 default\ninline: flows with text, no width/height — span, a default\ninline-block: flows with text but accepts width/height — like a button\nflex: turns children into flex items (see Flexbox concept)\ngrid: turns children into grid items (see Grid concept)\nnone: completely removes element from layout and screen\n\nThese are not just visual changes — they affect how elements flow and interact with each other.",
                "1. display: block:\n- Takes up full width of parent\n- Starts on new line\n- Accepts width, height, margin, padding on all sides\n- Examples: div, p, h1-h6, ul, li, header, section\n\n2. display: inline:\n- Flows with text, does not start new line\n- Width and height have no effect\n- Vertical margin/padding has limited effect\n- Examples: span, a, strong, em\n\n3. display: inline-block:\n- Flows with text (inline) but accepts width/height (block)\n- Buttons, image thumbnails, nav items\n\n4. display: none:\n- Element removed from layout entirely — takes no space\n- Different from visibility: hidden which hides but keeps space\n\n5. display: flex:\n- Turns children into flex items\n- Powerful 1D layout (rows OR columns)\n- Covered in depth in the Flexbox concept\n\n6. display: grid:\n- Turns children into grid items\n- Powerful 2D layout (rows AND columns)\n- Covered in depth in the CSS Grid concept\n\n7. display: contents:\n- Element itself becomes invisible but children remain\n- Useful for accessibility wrappers",
                "/* Block — default for div, p, h1 */\n.block-demo {\n  display: block;\n  width: 300px;     /* works */\n  height: 100px;    /* works */\n  margin: 20px 0;   /* works on all sides */\n}\n\n/* Inline — default for span, a */\n.inline-demo {\n  display: inline;\n  /* width: 300px; — NO EFFECT */\n  /* height: 100px; — NO EFFECT */\n}\n\n/* Inline-block — buttons, tags */\n.badge {\n  display: inline-block;\n  padding: 4px 12px;   /* works */\n  background: #264DE4;\n  color: white;\n  border-radius: 20px;\n  font-size: 0.875rem;\n}\n\n/* None — hide completely */\n.hidden { display: none; }          /* removed from layout */\n.invisible { visibility: hidden; }  /* hidden but keeps space */\n\n/* Toggle visibility with JS */\n/* element.style.display = 'none'; */\n/* element.style.display = 'block'; */",
                List.of(
                    new Concept.ConceptExample("display: none vs visibility: hidden",
                        "The difference between completely hiding and making invisible.",
                        ".container {\n  display: flex;\n  gap: 1rem;\n  padding: 1rem;\n  background: #f3f4f6;\n}\n\n.box {\n  padding: 1rem;\n  background: #264DE4;\n  color: white;\n  border-radius: 4px;\n}\n\n.gone {\n  display: none;       /* removed — other boxes fill in */\n}\n\n.invisible {\n  visibility: hidden;  /* space preserved — gap remains */\n}",
                        "Box1 | [gap where hidden box was] | Box3  — for display:none\nBox1 | [invisible space]          | Box3  — for visibility:hidden")
                ),
                List.of(
                    "Block elements fill full width; inline elements fit their content",
                    "inline-block flows inline but accepts width, height, and all margin/padding",
                    "display:none removes element from layout entirely — no space preserved",
                    "visibility:hidden hides visually but keeps the space the element occupies",
                    "Changing display to flex or grid changes how direct children are laid out"
                ),
                "Use display: none to toggle elements on and off with JavaScript. Store the original display value if you need to restore it — or better yet, use CSS classes: .hidden { display: none } and toggle the class with classList.toggle('hidden').",
                List.of(
                    "Setting width/height on inline elements — they are ignored. Use inline-block first",
                    "Confusing display:none (removes from layout) with visibility:hidden (keeps space)"
                ),
                15, 9, "C"),

            conceptRich(css, "CSS Positioning",
                "The position property controls how elements are placed on the page. Static, relative, absolute, fixed and sticky each behave differently and are used for different layout patterns.",
                "By default, HTML elements flow naturally — one after another. CSS positioning lets you take elements out of that flow and place them precisely.\n\nFive position values:\n- static: default — normal document flow\n- relative: stays in flow, but can be nudged with offset\n- absolute: removed from flow, positioned relative to nearest positioned ancestor\n- fixed: removed from flow, positioned relative to viewport — stays when scrolling\n- sticky: hybrid — in flow until it hits a scroll position, then becomes fixed\n\nAbsolute positioning is used for tooltips, dropdowns, badges on images. Fixed is for sticky headers and modals. Sticky is for table headers and navigation.",
                "1. position: static (default):\n- Normal document flow\n- top, right, bottom, left have no effect\n\n2. position: relative:\n- Stays in document flow (space is still reserved)\n- top/right/bottom/left nudge from its normal position\n- Creates a positioning context for absolutely positioned children\n\n3. position: absolute:\n- Removed from document flow (no space reserved)\n- Positioned relative to nearest ancestor with position other than static\n- If no positioned ancestor found, positioned relative to html element\n\n4. position: fixed:\n- Removed from flow\n- Positioned relative to viewport\n- Does not move when page scrolls\n- Use for: fixed headers, cookie banners, floating buttons, modals\n\n5. position: sticky:\n- Behaves as relative until it hits the specified threshold\n- Then behaves as fixed within its parent container\n- Requires a threshold: top: 0, top: 60px etc.\n- Use for: sticky table headers, sticky sidebars, sticky nav\n\n6. z-index:\n- Controls stacking order for positioned elements\n- Higher z-index appears on top\n- Only works on positioned elements (not static)",
                "/* Relative — nudge from normal position */\n.badge {\n  position: relative;\n  top: -2px;  /* moves up 2px without affecting layout */\n}\n\n/* Absolute — positioned badge on a card */\n.card {\n  position: relative;  /* creates positioning context */\n}\n.card .new-badge {\n  position: absolute;\n  top: 12px;\n  right: 12px;\n  background: red;\n  color: white;\n  padding: 4px 8px;\n  border-radius: 4px;\n}\n\n/* Fixed — stays on screen when scrolling */\n.navbar {\n  position: fixed;\n  top: 0;\n  left: 0;\n  right: 0;\n  height: 60px;\n  background: white;\n  z-index: 100;\n  box-shadow: 0 2px 4px rgba(0,0,0,0.1);\n}\nbody { padding-top: 60px; }  /* offset for fixed navbar */\n\n/* Sticky — sticks when scrolled to position */\n.section-header {\n  position: sticky;\n  top: 60px;  /* sticks 60px from top (below fixed navbar) */\n  background: white;\n  z-index: 50;\n  padding: 0.5rem 0;\n}",
                List.of(
                    new Concept.ConceptExample("Notification badge on an avatar",
                        "Use absolute positioning to overlay a badge on an element.",
                        ".user-avatar {\n  position: relative;   /* positioning context */\n  display: inline-block;\n  width: 48px;\n  height: 48px;\n}\n\n.user-avatar img {\n  width: 100%;\n  height: 100%;\n  border-radius: 50%;\n  object-fit: cover;\n}\n\n.notification-dot {\n  position: absolute;   /* relative to .user-avatar */\n  bottom: 0;\n  right: 0;\n  width: 14px;\n  height: 14px;\n  background: #22c55e;  /* green = online */\n  border-radius: 50%;\n  border: 2px solid white;\n}",
                        "Avatar image with small green online indicator dot at bottom right")
                ),
                List.of(
                    "absolute positioning looks for the nearest ancestor with position != static",
                    "Always add position: relative to the parent when using absolute children",
                    "Fixed position is relative to viewport — it stays visible while scrolling",
                    "Sticky requires a threshold (top: 0) to work — without it nothing happens",
                    "position takes elements out of normal flow (except relative) — use carefully"
                ),
                "When absolute-positioning a child inside a parent, always add position: relative to the parent. Without it, the child will fly up to the nearest positioned ancestor which is often the entire page — causing confusing misplacement.",
                List.of(
                    "Forgetting position: relative on parent — absolute child positions relative to wrong ancestor",
                    "Using position: fixed for a sticky header without accounting for the space it removes — add padding-top to body"
                ),
                18, 10, "C"),

            conceptRich(css, "z-index and Stacking Context",
                "z-index controls the visual stacking order of overlapping elements. A stacking context is a 3D layer created by certain CSS properties, and z-index only competes within the same context.",
                "When elements overlap, which one appears on top?\n\nz-index answers this. Higher values appear on top.\n\nBut z-index only works on positioned elements (relative, absolute, fixed, sticky) — it has no effect on static elements.\n\nThe tricky part: stacking contexts. Certain CSS properties create a new stacking context — an isolated layer. z-index values inside a context only compete within that context.\n\nThis is why sometimes a z-index:9999 element still appears behind another — it is in a different stacking context.",
                "1. How z-index works:\n- Only affects positioned elements (non-static)\n- Higher value = appears on top\n- Default z-index is auto (same as 0)\n- Can be negative\n\n2. Stacking order (without z-index, bottom to top):\n1. Background and borders of root element\n2. Block elements in normal flow\n3. Floating elements\n4. Inline elements\n5. Positioned elements\n\n3. Stacking context is created by:\n- position + z-index other than auto\n- opacity less than 1\n- transform other than none\n- filter other than none\n- will-change: transform\n- isolation: isolate\n\n4. Key rule:\n- z-index only competes within the same stacking context\n- z-index: 9999 inside a context with z-index: 1 loses to z-index: 2 outside\n\n5. isolation: isolate:\n- Forces a new stacking context without changing visuals\n- Isolates children's z-index from outside",
                "/* Basic z-index */\n.dropdown {\n  position: relative;\n  z-index: 100;\n}\n\n.modal-overlay {\n  position: fixed;\n  inset: 0;\n  background: rgba(0,0,0,0.5);\n  z-index: 200;\n}\n\n.modal {\n  position: fixed;\n  top: 50%;\n  left: 50%;\n  transform: translate(-50%, -50%);\n  z-index: 201;  /* above overlay */\n  background: white;\n  padding: 2rem;\n  border-radius: 8px;\n}\n\n/* Stacking context trap */\n.card {\n  position: relative;\n  opacity: 0.9;  /* creates stacking context! */\n}\n.card .tooltip {\n  z-index: 9999;  /* only competes within .card's context */\n}\n\n/* Fix: use isolation */\n.card {\n  position: relative;\n  isolation: isolate;  /* new context without opacity side effects */\n}\n\n/* Z-index scale (consistent system) */\n:root {\n  --z-dropdown: 100;\n  --z-sticky: 200;\n  --z-overlay: 300;\n  --z-modal: 400;\n  --z-toast: 500;\n}",
                List.of(
                    new Concept.ConceptExample("Modal layering system",
                        "Use a z-index system for reliable modal stacking.",
                        "/* Systematic z-index values */\n:root {\n  --z-base: 1;\n  --z-dropdown: 100;\n  --z-sticky: 200;\n  --z-modal-backdrop: 300;\n  --z-modal: 301;\n  --z-toast: 400;\n}\n\n.sticky-header {\n  position: sticky;\n  top: 0;\n  z-index: var(--z-sticky);\n}\n\n.modal-backdrop {\n  position: fixed;\n  inset: 0;\n  z-index: var(--z-modal-backdrop);\n}\n\n.modal {\n  position: fixed;\n  z-index: var(--z-modal);\n}\n\n.toast {\n  position: fixed;\n  bottom: 1rem;\n  right: 1rem;\n  z-index: var(--z-toast);  /* always on top */\n}",
                        "Predictable stacking: sticky under dropdown, modal over everything, toast always on top")
                ),
                List.of(
                    "z-index only works on positioned elements — add position: relative if nothing is happening",
                    "opacity < 1, transform, and filter all create stacking contexts — children's z-index is isolated",
                    "Use CSS variables for a z-index system: --z-modal: 400, --z-toast: 500",
                    "z-index: -1 sends element behind its parent's background",
                    "Stacking contexts are the most common cause of z-index not working as expected"
                ),
                "Create a z-index scale with CSS variables in :root. This prevents the z-index arms race where developers keep increasing values. A clear hierarchy (base:1, dropdown:100, modal:300, toast:500) makes layering predictable across the whole project.",
                List.of(
                    "Using z-index on a static element — has no effect, add position: relative",
                    "Using arbitrary high numbers like 9999 — use a defined scale instead"
                ),
                15, 11, "B"),

            conceptRich(css, "CSS Pseudo-classes and Pseudo-elements",
                "Pseudo-classes select elements in specific states (:hover, :focus, :nth-child). Pseudo-elements create virtual elements (::before, ::after) that can be styled without adding HTML.",
                "Pseudo-classes let you style elements based on their STATE or POSITION:\n- :hover — user's mouse is over it\n- :focus — element has keyboard/mouse focus\n- :nth-child(n) — element at position n in parent\n- :not(.class) — elements that don't match\n\nPseudo-elements let you insert VIRTUAL content:\n- ::before — insert content before the element's content\n- ::after — insert content after\n- These are commonly used for decorative elements, icons, and CSS-only tricks\n\nThe colon convention: :single for pseudo-classes, ::double for pseudo-elements.",
                "1. Common pseudo-classes:\n- :hover: mouse over element\n- :focus: element has focus (keyboard/click)\n- :focus-visible: focus from keyboard only (not click)\n- :active: being clicked\n- :visited: visited link\n- :disabled: disabled form element\n- :checked: checked checkbox or radio\n- :placeholder-shown: input showing placeholder\n- :empty: element with no children\n- :not(selector): elements not matching selector\n\n2. Structural pseudo-classes:\n- :first-child, :last-child\n- :nth-child(n): nth child counting from start\n- :nth-last-child(n): nth from end\n- :nth-child(2n): every even\n- :nth-child(2n+1): every odd\n- :only-child: only child of parent\n- :first-of-type, :last-of-type, :nth-of-type(n)\n\n3. Pseudo-elements:\n- ::before: virtual first child\n- ::after: virtual last child\n- Must have content property (can be empty: content: '')\n- Can be absolutely positioned within parent\n- Used for: decorative lines, icons, counters, overlays",
                "/* Hover and focus states */\n.btn {\n  background: #264DE4;\n  color: white;\n  padding: 0.75rem 1.5rem;\n  border: none;\n  border-radius: 6px;\n  transition: background 0.2s;\n}\n.btn:hover { background: #1a3ab8; }\n.btn:focus-visible { outline: 3px solid #264DE4; outline-offset: 2px; }\n.btn:active { transform: scale(0.98); }\n\n/* nth-child — zebra table */\ntr:nth-child(even) { background: #f9fafb; }\n\n/* Form states */\ninput:focus { border-color: #264DE4; box-shadow: 0 0 0 3px rgba(38,77,228,0.2); }\ninput:disabled { opacity: 0.5; cursor: not-allowed; }\ninput:invalid { border-color: #ef4444; }\n\n/* ::before and ::after */\n.card {\n  position: relative;\n  padding: 1.5rem;\n  border-radius: 8px;\n}\n.card::before {\n  content: '';\n  position: absolute;\n  top: 0; left: 0; right: 0;\n  height: 4px;\n  background: linear-gradient(90deg, #264DE4, #9B6ED4);\n  border-radius: 8px 8px 0 0;\n}\n\n/* Required field indicator */\n.required::after {\n  content: ' *';\n  color: #ef4444;\n}",
                List.of(
                    new Concept.ConceptExample("Custom checkbox with pseudo-elements",
                        "Style checkboxes using ::before and :checked state.",
                        ".custom-checkbox {\n  display: flex;\n  align-items: center;\n  gap: 0.5rem;\n  cursor: pointer;\n}\n\n.custom-checkbox input[type=\"checkbox\"] {\n  display: none;\n}\n\n.custom-checkbox .box {\n  width: 20px;\n  height: 20px;\n  border: 2px solid #d1d5db;\n  border-radius: 4px;\n  position: relative;\n  transition: all 0.2s;\n}\n\n.custom-checkbox input:checked + .box {\n  background: #264DE4;\n  border-color: #264DE4;\n}\n\n.custom-checkbox input:checked + .box::after {\n  content: '';\n  position: absolute;\n  left: 5px; top: 2px;\n  width: 6px; height: 11px;\n  border: 2px solid white;\n  border-top: none; border-left: none;\n  transform: rotate(45deg);\n}",
                        "Custom styled checkbox with blue background and white checkmark when checked")
                ),
                List.of(
                    ":hover, :focus, :active style interaction states — always style :focus for accessibility",
                    "::before and ::after insert virtual content — must have content: '' at minimum",
                    ":nth-child(2n) targets even elements; :nth-child(2n+1) targets odd",
                    "Use :not() to exclude elements: li:not(:last-child) adds border to all but last",
                    "Use :focus-visible instead of :focus to only show focus ring for keyboard users"
                ),
                "Always style :focus-visible on interactive elements. This shows a focus ring for keyboard users (accessibility) without showing it when users click with a mouse. Never remove focus styles entirely — keyboard users rely on them to navigate.",
                List.of(
                    "Using outline:none on :focus without a replacement — breaks keyboard navigation",
                    "Forgetting content: '' on ::before or ::after — pseudo-element won't appear"
                ),
                18, 12, "B"),

            conceptRich(css, "Flexbox",
                "Flexbox is a one-dimensional layout system for arranging items in a row or column. It handles alignment, spacing, wrapping and ordering with simple properties.",
                "Before Flexbox, creating centred content required hacks. Before Flexbox, making equal-height columns was complicated. Before Flexbox, distributing space between items required complex calculations.\n\nFlexbox solved all of this.\n\nThe concept:\n- Apply display: flex to a container\n- Direct children become flex items\n- Container controls how items are arranged\n\nFlexbox is one-dimensional — it works in one direction at a time: either a row or a column. For two-dimensional layouts (rows AND columns simultaneously), use CSS Grid.",
                "1. Container properties (parent):\n- display: flex — activates flexbox\n- flex-direction: row (default) | column | row-reverse | column-reverse\n- justify-content: alignment on main axis\n  - flex-start, flex-end, center, space-between, space-around, space-evenly\n- align-items: alignment on cross axis\n  - stretch (default), flex-start, flex-end, center, baseline\n- flex-wrap: nowrap (default) | wrap | wrap-reverse\n- gap: space between items (row-gap, column-gap)\n- align-content: multi-line alignment (when wrap is on)\n\n2. Item properties (children):\n- flex-grow: how much item grows relative to siblings\n- flex-shrink: how much item shrinks\n- flex-basis: initial size before grow/shrink\n- flex: shorthand — flex: 1 = grow:1 shrink:1 basis:0\n- align-self: override container's align-items for one item\n- order: change visual order (default 0)\n\n3. Common patterns:\n- Center anything: display:flex + justify-content:center + align-items:center\n- Space between nav items: justify-content: space-between\n- Responsive wrap: flex-wrap: wrap + flex: 1 1 min(300px, 100%)",
                "/* Center anything — most common Flexbox use */\n.centered {\n  display: flex;\n  justify-content: center;\n  align-items: center;\n  height: 100vh;\n}\n\n/* Navigation bar */\n.navbar {\n  display: flex;\n  justify-content: space-between;\n  align-items: center;\n  padding: 0 2rem;\n  height: 60px;\n}\n.navbar-links {\n  display: flex;\n  gap: 2rem;\n  list-style: none;\n}\n\n/* Card grid with wrapping */\n.card-grid {\n  display: flex;\n  flex-wrap: wrap;\n  gap: 1.5rem;\n}\n.card {\n  flex: 1 1 300px;   /* grow, shrink, minimum 300px */\n  max-width: 400px;\n}\n\n/* Sidebar layout */\n.layout {\n  display: flex;\n  gap: 2rem;\n  min-height: 100vh;\n}\n.sidebar { flex: 0 0 260px; }  /* fixed width, no grow/shrink */\n.main { flex: 1; }               /* takes remaining space */",
                List.of(
                    new Concept.ConceptExample("Flex card grid that wraps responsively",
                        "Cards that sit in a row and wrap to new lines on small screens.",
                        ".courses {\n  display: flex;\n  flex-wrap: wrap;\n  gap: 1.5rem;\n  padding: 2rem;\n}\n\n.course-card {\n  flex: 1 1 280px;    /* min 280px, grows to fill */\n  max-width: 360px;\n  background: white;\n  border-radius: 8px;\n  border: 1px solid #e5e7eb;\n  padding: 1.5rem;\n  display: flex;\n  flex-direction: column;\n}\n\n.course-card h3 { margin: 0 0 0.5rem; }\n.course-card p { flex: 1; color: #6b7280; } /* pushes button down */\n.course-card .btn { margin-top: auto; align-self: flex-start; }",
                        "Row of course cards that wraps to a new line when the screen is too narrow")
                ),
                List.of(
                    "justify-content aligns along main axis (row=horizontal, column=vertical)",
                    "align-items aligns along cross axis (row=vertical, column=horizontal)",
                    "flex: 1 is shorthand for flex: 1 1 0 — item grows to fill available space",
                    "gap replaces margin hacks between flex items — use it always",
                    "flex-wrap: wrap allows items to move to new rows when they do not fit"
                ),
                "Memorise the centering pattern: display:flex + justify-content:center + align-items:center. This centres content both horizontally and vertically in the container. It works for single items, multiple items, and entire page layouts.",
                List.of(
                    "Confusing justify-content (main axis) and align-items (cross axis) — draw the axes first",
                    "Using margin: auto on flex items instead of gap on the container"
                ),
                20, 13, "B"),

            conceptRich(css, "CSS Grid",
                "CSS Grid is a two-dimensional layout system. It creates rows AND columns simultaneously, making it ideal for page layouts, card grids and complex designs.",
                "Flexbox handles one direction at a time — row or column.\nCSS Grid handles both directions simultaneously — rows AND columns.\n\nGrid is for:\n- Overall page layout (header/sidebar/main/footer)\n- Card grids with equal-sized cells\n- Complex dashboard layouts\n- Any layout where you need precise control over both axes\n\nThe concept:\n- Apply display: grid to the container\n- Define columns with grid-template-columns\n- Define rows with grid-template-rows\n- Items automatically fill the cells — or you can place them explicitly",
                "1. Container properties:\n- display: grid\n- grid-template-columns: defines column widths\n  - fixed: 200px 200px 200px\n  - fraction: 1fr 2fr 1fr (proportional)\n  - repeat: repeat(3, 1fr) = three equal columns\n  - auto-fill: repeat(auto-fill, minmax(250px, 1fr))\n  - auto-fit: like auto-fill but collapses empty tracks\n- grid-template-rows: defines row heights\n- gap: space between cells\n- grid-template-areas: named layout areas\n- justify-items: horizontal alignment within cells\n- align-items: vertical alignment within cells\n\n2. Item properties:\n- grid-column: col-start / col-end\n- grid-row: row-start / row-end\n- grid-column: 1 / 3 — span two columns\n- grid-column: span 2 — span shorthand\n- grid-area: name when using template-areas\n\n3. The fr unit:\n- Fraction of available space\n- 1fr 2fr 1fr: middle column is twice as wide\n- After fixed columns are placed, fr divides the rest\n\n4. minmax() function:\n- minmax(min, max) — column can be between min and max\n- minmax(250px, 1fr): at least 250px, can grow\n\n5. auto-fill vs auto-fit:\n- auto-fill: creates as many columns as fit, keeps empty\n- auto-fit: creates as many columns as fit, collapses empty",
                "/* Basic 3-column grid */\n.grid-3 {\n  display: grid;\n  grid-template-columns: repeat(3, 1fr);\n  gap: 1.5rem;\n}\n\n/* Responsive grid — columns adjust automatically */\n.card-grid {\n  display: grid;\n  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));\n  gap: 1.5rem;\n}\n\n/* Page layout with named areas */\n.page {\n  display: grid;\n  grid-template-columns: 260px 1fr;\n  grid-template-rows: 60px 1fr auto;\n  grid-template-areas:\n    'header  header'\n    'sidebar main'\n    'footer  footer';\n  min-height: 100vh;\n}\n.page header  { grid-area: header; }\n.page .sidebar { grid-area: sidebar; }\n.page main    { grid-area: main; }\n.page footer  { grid-area: footer; }\n\n/* Item spanning multiple columns */\n.featured {\n  grid-column: 1 / 3;  /* spans columns 1 and 2 */\n}",
                List.of(
                    new Concept.ConceptExample("Responsive card grid with auto-fill",
                        "Cards automatically reflow from 3 columns to 2 to 1 as screen narrows.",
                        ".courses-grid {\n  display: grid;\n  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));\n  gap: 1.5rem;\n  padding: 2rem;\n}\n\n.course-card {\n  background: white;\n  border-radius: 8px;\n  border: 1px solid #e5e7eb;\n  overflow: hidden;\n}\n\n.course-card img {\n  width: 100%;\n  aspect-ratio: 16/9;\n  object-fit: cover;\n}\n\n.course-card-content {\n  padding: 1.25rem;\n}\n\n/* No media queries needed —\n   columns auto-adjust based on available space */",
                        "3 columns on desktop, 2 on tablet, 1 on mobile — no media queries needed")
                ),
                List.of(
                    "fr is the fraction unit — 1fr 2fr 1fr means middle column is twice the width",
                    "repeat(auto-fill, minmax(250px, 1fr)) creates a responsive grid without media queries",
                    "grid-template-areas with named zones creates the most readable layouts",
                    "grid-column: 1 / -1 spans the full width of the grid (from first to last line)",
                    "Use Grid for 2D layouts (rows AND columns); use Flexbox for 1D (row OR column)"
                ),
                "Use repeat(auto-fill, minmax(250px, 1fr)) for any card grid. It creates as many columns as fit, each at least 250px wide. No media queries needed — the grid automatically adjusts from 4 columns on desktop to 1 on mobile.",
                List.of(
                    "Using Grid when Flexbox would be simpler — use Grid for 2D, Flexbox for 1D",
                    "Forgetting the fr unit — using px for all columns prevents responsive behaviour"
                ),
                20, 14, "A"),

            conceptRich(css, "Responsive Design",
                "Responsive design makes web pages look good on all screen sizes. Media queries apply different styles at different viewport widths. Mobile-first design starts with mobile styles and adds complexity for larger screens.",
                "In 2024, over 60% of web traffic is on mobile devices.\n\nA page that looks great on desktop but is unusable on mobile loses the majority of its potential users.\n\nResponsive design means:\n- Flexible layouts that adapt to any screen size\n- Readable text without zooming\n- Tap targets large enough for fingers\n- Images that scale correctly\n\nThe primary tool: media queries. They apply CSS rules only at certain viewport widths.\n\nMobile-first approach: write styles for mobile first, then add @media (min-width) to enhance for larger screens. This forces you to keep mobile layouts simple and add complexity progressively.",
                "1. Media query syntax:\n- @media (max-width: 768px) { ... }\n- @media (min-width: 768px) { ... }\n- @media (min-width: 768px) and (max-width: 1024px) { ... }\n- Can target: width, height, orientation, resolution, hover ability\n\n2. Mobile-first approach:\n- Write base styles for mobile (smallest screen)\n- Use min-width media queries to add styles for larger screens\n- Default styles apply to mobile, media queries override for desktop\n\n3. Desktop-first approach:\n- Write for desktop, use max-width to adjust for smaller screens\n- Avoid — tends to create bloated mobile experience\n\n4. Common breakpoints:\n- 480px: small phones\n- 768px: tablets\n- 1024px: laptops\n- 1280px: desktops\n- 1536px: large screens\n\n5. Responsive techniques without media queries:\n- Flexbox with flex-wrap: wrap\n- Grid with auto-fill and minmax()\n- clamp() for fluid typography\n- % and vw widths\n\n6. Viewport meta tag:\n- <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n- Required for responsive behaviour on mobile",
                "/* Mobile-first approach */\n\n/* Base: mobile styles */\n.container {\n  width: 100%;\n  padding: 0 1rem;\n}\n\n.nav-links {\n  display: none;  /* hidden on mobile */\n}\n\n.card-grid {\n  display: grid;\n  grid-template-columns: 1fr;  /* 1 column on mobile */\n  gap: 1rem;\n}\n\n.hero h1 {\n  font-size: 1.75rem;\n}\n\n/* Tablet: 768px and up */\n@media (min-width: 768px) {\n  .container { max-width: 768px; margin: 0 auto; }\n  .card-grid { grid-template-columns: repeat(2, 1fr); }\n  .hero h1 { font-size: 2.5rem; }\n}\n\n/* Desktop: 1024px and up */\n@media (min-width: 1024px) {\n  .container { max-width: 1280px; }\n  .nav-links { display: flex; gap: 2rem; }\n  .card-grid { grid-template-columns: repeat(3, 1fr); }\n  .hero h1 { font-size: 3.5rem; }\n}\n\n/* Feature queries */\n@media (hover: hover) {\n  .btn:hover { background: darken(10%); }\n}",
                List.of(
                    new Concept.ConceptExample("Responsive navigation",
                        "Nav that shows a hamburger on mobile and full links on desktop.",
                        "/* Mobile: hamburger visible, links hidden */\n.nav-links {\n  display: none;\n  flex-direction: column;\n  gap: 1rem;\n}\n\n.nav-links.open { display: flex; }\n\n.hamburger { display: block; cursor: pointer; }\n\n/* Desktop: hamburger hidden, links visible */\n@media (min-width: 768px) {\n  .nav-links {\n    display: flex;\n    flex-direction: row;\n    gap: 2rem;\n  }\n  .hamburger { display: none; }\n}",
                        "Mobile: hamburger button. Desktop: horizontal nav links")
                ),
                List.of(
                    "Always include the viewport meta tag — without it responsive CSS has no effect on mobile",
                    "Mobile-first: write mobile styles first, use min-width to progressively enhance",
                    "Prefer layout techniques (Flexbox wrap, Grid auto-fill) over many media queries",
                    "Touch targets (buttons, links) should be at least 44×44px on mobile",
                    "Test in Chrome DevTools device mode — Ctrl+Shift+M to toggle"
                ),
                "Start every project mobile-first. Open Chrome DevTools, toggle device mode (Ctrl+Shift+M), and start with the iPhone SE view. Design and CSS for the smallest screen first. This prevents the common mistake of having a beautiful desktop site that is broken on mobile.",
                List.of(
                    "Missing viewport meta tag in HTML head — responsive CSS does nothing without it",
                    "Writing desktop-first CSS with max-width queries — creates overrides that are hard to maintain"
                ),
                20, 15, "A"),

            conceptRich(css, "CSS Variables (Custom Properties)",
                "CSS custom properties (variables) store values that can be reused throughout a stylesheet. They enable consistent design systems, easy theme changes and dynamic updates via JavaScript.",
                "CSS variables solve the problem of repetition.\n\nWithout variables, if your brand blue is #264DE4, you type that hex code hundreds of times. When the brand colour changes, you update hundreds of places.\n\nWith CSS variables:\n--primary: #264DE4;\n\nUse it anywhere: color: var(--primary);\nChange the brand colour? Update one line.\n\nCSS variables are also:\n- Dynamic — can be changed with JavaScript at runtime\n- Inheritable — child elements see parent's variables\n- Overridable — redefine in a specific scope\n- Theme-friendly — define light/dark themes in :root",
                "1. Declaring variables:\n- Inside a selector: --variable-name: value;\n- Conventionally in :root for global scope\n- --primary-color, --spacing-lg, --radius-md\n\n2. Using variables:\n- var(--variable-name) — use the value\n- var(--variable-name, fallback) — with fallback\n\n3. Scope:\n- :root variables are globally available\n- Variables are inherited by child elements\n- A variable on a child only affects that child and its descendants\n\n4. JavaScript access:\n- Read: getComputedStyle(element).getPropertyValue('--name')\n- Write: element.style.setProperty('--name', value)\n- This enables dynamic themes, animations and interactive styling\n\n5. Common uses:\n- Colour palette: --primary, --secondary, --text, --border\n- Spacing scale: --spacing-xs, --sm, --md, --lg, --xl\n- Typography: --font-body, --font-heading, --text-base\n- Shadows, borders, transitions durations",
                "/* Define variables in :root for global access */\n:root {\n  /* Colours */\n  --primary: #264DE4;\n  --primary-dark: #1a3ab8;\n  --secondary: #9B6ED4;\n  --success: #22c55e;\n  --danger: #ef4444;\n  --text: #1a1a1a;\n  --text-muted: #6b7280;\n  --bg: #ffffff;\n  --border: #e5e7eb;\n\n  /* Spacing */\n  --spacing-xs: 0.25rem;\n  --spacing-sm: 0.5rem;\n  --spacing-md: 1rem;\n  --spacing-lg: 1.5rem;\n  --spacing-xl: 2rem;\n\n  /* Typography */\n  --font-body: 'Roboto', Arial, sans-serif;\n  --text-sm: 0.875rem;\n  --text-base: 1rem;\n  --text-lg: 1.25rem;\n\n  /* Misc */\n  --radius: 8px;\n  --shadow: 0 4px 6px rgba(0,0,0,0.1);\n  --transition: 0.2s ease;\n}\n\n/* Use variables */\n.btn-primary {\n  background: var(--primary);\n  color: var(--bg);\n  padding: var(--spacing-sm) var(--spacing-lg);\n  border-radius: var(--radius);\n  transition: background var(--transition);\n}\n.btn-primary:hover { background: var(--primary-dark); }\n\n/* Dark theme */\n[data-theme=\"dark\"] {\n  --text: #f9fafb;\n  --bg: #0f172a;\n  --border: #1e293b;\n}",
                List.of(
                    new Concept.ConceptExample("Dynamic theme switching with JS",
                        "Toggle between light and dark theme by updating CSS variables.",
                        "/* CSS */\n:root {\n  --bg: #ffffff;\n  --text: #1a1a1a;\n  --card-bg: #f9fafb;\n}\n\n[data-theme=\"dark\"] {\n  --bg: #0f172a;\n  --text: #f1f5f9;\n  --card-bg: #1e293b;\n}\n\nbody {\n  background: var(--bg);\n  color: var(--text);\n  transition: background 0.3s, color 0.3s;\n}\n\n.card { background: var(--card-bg); }\n\n/* JavaScript */",
                        "// Toggle theme\nconst html = document.documentElement;\nconst current = html.getAttribute('data-theme');\nhtml.setAttribute('data-theme', current === 'dark' ? 'light' : 'dark');\nlocalStorage.setItem('theme', html.getAttribute('data-theme'));")
                ),
                List.of(
                    "Define all colours, spacing and typography in :root for a consistent design system",
                    "var(--name, fallback) — fallback used if variable is not defined",
                    "CSS variables are inherited — redefine on a child element to scope a change",
                    "JavaScript can read and write CSS variables — enables dynamic theming",
                    "Unlike SASS/LESS variables, CSS variables are live and can change at runtime"
                ),
                "Build a colour system with CSS variables at the start of every project. --primary, --text, --bg, --border covers 80% of your needs. When designs change (and they always do), you update the :root values and the whole site updates instantly.",
                List.of(
                    "Using hex codes directly throughout CSS — makes global changes painful",
                    "Forgetting the -- prefix — CSS variable names must start with two dashes"
                ),
                15, 16, "B"),

            conceptRich(css, "CSS Transforms",
                "CSS transforms move, rotate, scale and skew elements without affecting document flow. Combined with transitions and animations, they create smooth, performant visual effects.",
                "CSS transforms change how an element looks and where it appears — but without affecting document layout.\n\nUnlike changing margin or width, a transformed element does not push other elements around. The transform is applied visually only — the element's space in the document stays exactly where it was.\n\nFour main transform functions:\n- translate: move horizontally and/or vertically\n- rotate: spin around a point\n- scale: resize\n- skew: slant\n\nTransforms are hardware-accelerated — they run on the GPU, making them smooth and performant. This is why animations using transform are preferred over animations using top/left/margin.",
                "1. transform functions:\n- translateX(n), translateY(n), translate(x, y): move element\n- translateZ(n), translate3d(x,y,z): 3D movement\n- rotate(deg): rotate clockwise\n- rotateX, rotateY, rotateZ: 3D rotations\n- scaleX(n), scaleY(n), scale(n): resize (1=normal, 2=double, 0.5=half)\n- skewX(deg), skewY(deg): shear/slant\n- matrix(a,b,c,d,e,f): raw 2D matrix\n\n2. Multiple transforms:\n- Chained: transform: rotate(45deg) scale(1.2)\n- Order matters — transforms are applied right to left\n\n3. transform-origin:\n- Default: 50% 50% (center)\n- Changes the pivot point for rotation/scale\n- transform-origin: top left — rotates around top-left corner\n\n4. Performance:\n- transform and opacity are GPU-composited — smooth animation\n- Avoid animating: width, height, top, left, margin — they trigger layout\n- Use transform: translate() instead of changing top/left\n\n5. will-change:\n- will-change: transform hints browser to prepare GPU layer\n- Use sparingly — creates memory overhead",
                "/* Move */\n.shifted { transform: translate(20px, -10px); }\n.centered-modal {\n  position: fixed;\n  top: 50%;\n  left: 50%;\n  transform: translate(-50%, -50%);  /* perfect centering trick */\n}\n\n/* Rotate */\n.rotated { transform: rotate(45deg); }\n.icon-arrow.open { transform: rotate(180deg); }  /* flip arrow */\n\n/* Scale */\n.card:hover { transform: scale(1.05); }  /* slight zoom on hover */\n\n/* Skew */\n.diagonal { transform: skewY(-3deg); }  /* angled section */\n\n/* Multiple transforms */\n.spin-grow:hover {\n  transform: rotate(360deg) scale(1.2);\n}\n\n/* Performant centering (instead of margin: auto) */\n.overlay {\n  position: absolute;\n  top: 50%;\n  left: 50%;\n  transform: translate(-50%, -50%);\n}\n\n/* Card flip effect */\n.card-inner {\n  transform-style: preserve-3d;\n  transition: transform 0.6s;\n}\n.card:hover .card-inner {\n  transform: rotateY(180deg);\n}",
                List.of(
                    new Concept.ConceptExample("Hover lift effect with transform",
                        "Cards that lift slightly on hover using transform and box-shadow.",
                        ".product-card {\n  background: white;\n  border-radius: 12px;\n  padding: 1.5rem;\n  box-shadow: 0 2px 4px rgba(0,0,0,0.1);\n  transition: transform 0.2s ease, box-shadow 0.2s ease;\n}\n\n.product-card:hover {\n  transform: translateY(-4px);\n  box-shadow: 0 8px 24px rgba(0,0,0,0.15);\n}\n\n/* Performance: transform is GPU-composited */\n/* Do NOT use: margin-top: -4px on hover -- causes layout reflow */",
                        "Card lifts 4px on hover with deeper shadow — smooth and performant")
                ),
                List.of(
                    "transform does not affect document flow — other elements do not move when you transform",
                    "translate(-50%, -50%) with position:absolute/fixed is the cleanest centering method",
                    "Use transform for hover effects — it is GPU-accelerated and does not cause layout recalculation",
                    "transform-origin changes the pivot point for rotation and scale",
                    "Multiple transforms are applied right to left: rotate(45deg) scale(2) rotates first then scales"
                ),
                "Use transform: translateY(-4px) instead of changing margin-top on hover. Transform runs on the GPU and does not trigger layout recalculation — it is dramatically smoother than changing any layout property like margin, padding or top.",
                List.of(
                    "Animating top/left/margin instead of transform — causes layout reflow every frame, janky animation",
                    "Forgetting that multiple transforms apply right to left"
                ),
                15, 17, "B"),

            conceptRich(css, "CSS Transitions",
                "CSS transitions animate property changes smoothly over a specified duration. They create the visual feedback that makes interfaces feel polished and responsive.",
                "Without transitions, CSS property changes happen instantly.\nWith transitions, they animate smoothly.\n\nA button that instantly changes colour on hover feels abrupt.\nA button that smoothly transitions its colour over 200ms feels polished.\n\ntransition is how you add that smoothness.\n\nYou define:\n- Which property to animate\n- How long the animation lasts\n- The timing function (ease, linear, ease-in-out)\n- An optional delay before starting",
                "1. transition shorthand:\n- transition: property duration timing-function delay\n- transition: background 0.2s ease\n- transition: all 0.3s ease-in-out\n- transition: background 0.2s, transform 0.3s\n\n2. Individual properties:\n- transition-property: which property to animate\n- transition-duration: 0.2s, 300ms\n- transition-timing-function: easing curve\n- transition-delay: wait before starting\n\n3. Timing functions:\n- ease: slow start, fast middle, slow end (default)\n- linear: constant speed throughout\n- ease-in: slow start, fast end\n- ease-out: fast start, slow end\n- ease-in-out: slow start and end\n- cubic-bezier(x1, y1, x2, y2): custom curve\n- steps(n): stepped animation (for sprite sheets)\n\n4. What can be transitioned:\n- Most numeric properties: opacity, width, height, color, transform\n- display: none cannot be transitioned directly\n- Transitions run automatically when a CSS value changes\n\n5. Performance:\n- Best properties for transition: opacity, transform\n- Avoid: width, height, top, left (cause layout)\n- transition: all is convenient but can hurt performance",
                "/* Button with smooth hover */\n.btn {\n  background: #264DE4;\n  color: white;\n  padding: 0.75rem 1.5rem;\n  border: none;\n  border-radius: 6px;\n  cursor: pointer;\n  transition: background 0.2s ease, transform 0.1s ease, box-shadow 0.2s ease;\n}\n\n.btn:hover {\n  background: #1a3ab8;\n  transform: translateY(-1px);\n  box-shadow: 0 4px 12px rgba(38, 77, 228, 0.3);\n}\n\n.btn:active {\n  transform: translateY(0);\n  box-shadow: none;\n}\n\n/* Smooth show/hide with opacity */\n.tooltip {\n  opacity: 0;\n  transition: opacity 0.2s ease;\n  pointer-events: none;\n}\n.tooltip.visible {\n  opacity: 1;\n  pointer-events: auto;\n}\n\n/* Sliding drawer */\n.drawer {\n  transform: translateX(-100%);\n  transition: transform 0.3s ease-in-out;\n}\n.drawer.open {\n  transform: translateX(0);\n}\n\n/* Multiple transitions */\n.card {\n  transition:\n    box-shadow 0.3s ease,\n    transform 0.2s ease;\n}",
                List.of(
                    new Concept.ConceptExample("Smooth page interaction transitions",
                        "Apply transitions consistently for a polished feel.",
                        "/* Global interactive element transitions */\na,\nbutton,\n.btn,\n.link,\n.card {\n  transition:\n    color 0.15s ease,\n    background-color 0.15s ease,\n    border-color 0.15s ease,\n    transform 0.15s ease,\n    box-shadow 0.15s ease,\n    opacity 0.15s ease;\n}\n\n/* Smooth focus ring */\n:focus-visible {\n  outline: 3px solid #264DE4;\n  outline-offset: 2px;\n  transition: outline-offset 0.1s ease;\n}\n\n/* Fade in elements on load */\n.fade-in {\n  opacity: 0;\n  transform: translateY(10px);\n  transition: opacity 0.4s ease, transform 0.4s ease;\n}\n.fade-in.visible {\n  opacity: 1;\n  transform: translateY(0);\n}",
                        "All interactive elements have smooth state transitions — polished feel")
                ),
                List.of(
                    "Transition runs when a CSS value changes — usually triggered by :hover, :focus, class toggle",
                    "ease-out feels most natural for UI interactions — fast response, smooth settle",
                    "Transition only animates from one value to another — both values must be numeric/color",
                    "transition: all is convenient but can accidentally transition properties you do not want",
                    "Use opacity + pointer-events to show/hide elements with transition (display:none cannot transition)"
                ),
                "Keep transitions short — 150-250ms for interactions, 300-400ms for larger movements. Transitions longer than 500ms feel slow and frustrating for users. Shorter transitions feel snappy and responsive.",
                List.of(
                    "Using display:none and expecting a transition — use opacity:0 + pointer-events:none instead",
                    "Forgetting transition on the base element — only adding it on :hover means transition only plays one direction"
                ),
                15, 18, "B"),

            conceptRich(css, "CSS Keyframes and Animations",
                "CSS animations use @keyframes to define multi-step sequences. Unlike transitions, animations can run automatically, loop, and have complex multi-step paths without user interaction.",
                "Transitions animate between two states triggered by user interaction.\nAnimations can:\n- Run automatically when the page loads\n- Loop indefinitely\n- Have multiple steps (not just start and end)\n- Play forward, reverse, or alternate\n\nCommon uses:\n- Loading spinners\n- Pulsing attention indicators\n- Page entrance effects\n- Skeleton loading screens\n- Logo animations\n\n@keyframes defines the steps. animation applies it to an element.",
                "1. @keyframes syntax:\n- @keyframes name { from {} to {} }\n- @keyframes name { 0% {} 50% {} 100% {} }\n- Percentages: any number of steps\n\n2. animation shorthand:\n- animation: name duration timing-function delay iteration-count direction fill-mode\n- animation: spin 1s linear infinite\n\n3. Individual properties:\n- animation-name: keyframe name\n- animation-duration: 0.3s, 1s\n- animation-timing-function: ease, linear, steps(n)\n- animation-delay: wait before starting\n- animation-iteration-count: 1, 3, infinite\n- animation-direction: normal, reverse, alternate, alternate-reverse\n- animation-fill-mode:\n  - none: reverts to original after animation\n  - forwards: keeps end state\n  - backwards: applies start state during delay\n  - both: both forwards and backwards\n- animation-play-state: running, paused\n\n4. Multiple animations:\n- animation: spin 1s linear infinite, pulse 2s ease infinite\n\n5. Performance:\n- Only animate transform and opacity for smooth 60fps\n- will-change: transform on element being animated",
                "/* Loading spinner */\n@keyframes spin {\n  from { transform: rotate(0deg); }\n  to   { transform: rotate(360deg); }\n}\n\n.spinner {\n  width: 40px;\n  height: 40px;\n  border: 3px solid #e5e7eb;\n  border-top-color: #264DE4;\n  border-radius: 50%;\n  animation: spin 0.8s linear infinite;\n}\n\n/* Pulse effect */\n@keyframes pulse {\n  0%, 100% { opacity: 1; }\n  50%       { opacity: 0.5; }\n}\n.loading-text { animation: pulse 1.5s ease-in-out infinite; }\n\n/* Entrance animation */\n@keyframes fadeInUp {\n  from {\n    opacity: 0;\n    transform: translateY(20px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n\n.hero-title {\n  animation: fadeInUp 0.6s ease-out forwards;\n}\n.hero-subtitle {\n  animation: fadeInUp 0.6s ease-out 0.2s forwards;  /* 0.2s delay */\n  opacity: 0;  /* start hidden during delay */\n}\n\n/* Attention shake */\n@keyframes shake {\n  0%, 100% { transform: translateX(0); }\n  25%       { transform: translateX(-8px); }\n  75%       { transform: translateX(8px); }\n}\n.error { animation: shake 0.4s ease-in-out; }",
                List.of(
                    new Concept.ConceptExample("Skeleton loading screen",
                        "Animated placeholder shown while content loads.",
                        "@keyframes shimmer {\n  0%   { background-position: -1000px 0; }\n  100% { background-position: 1000px 0; }\n}\n\n.skeleton {\n  background: linear-gradient(\n    90deg,\n    #f0f0f0 25%,\n    #e0e0e0 50%,\n    #f0f0f0 75%\n  );\n  background-size: 2000px 100%;\n  animation: shimmer 2s infinite linear;\n  border-radius: 4px;\n}\n\n.skeleton-title {\n  height: 24px;\n  width: 60%;\n  margin-bottom: 12px;\n}\n\n.skeleton-text {\n  height: 16px;\n  width: 100%;\n  margin-bottom: 8px;\n}\n\n.skeleton-text:last-child { width: 80%; }",
                        "Shimmering grey placeholder blocks that animate while content loads")
                ),
                List.of(
                    "@keyframes defines the steps; animation applies it to an element",
                    "animation-fill-mode: forwards keeps the end state after animation completes",
                    "Use animation-delay for staggered entrance effects on multiple elements",
                    "animation-iteration-count: infinite makes animations loop forever",
                    "Only animate transform and opacity for 60fps smooth animations — avoid width/height"
                ),
                "Prefer prefers-reduced-motion media query to respect users with vestibular disorders: @media (prefers-reduced-motion: reduce) { * { animation-duration: 0.001ms !important; } }. This disables animations for users who have requested reduced motion in their OS settings.",
                List.of(
                    "Animating layout properties like width/height — causes jank. Use transform/opacity",
                    "Forgetting animation-fill-mode: forwards when you want the final state to persist"
                ),
                18, 19, "A"),

            conceptRich(css, "CSS Functions — calc(), clamp(), min(), max()",
                "CSS functions perform calculations and comparisons directly in CSS. calc() enables mixed-unit arithmetic, clamp() creates fluid values within bounds, and min()/max() pick from multiple values.",
                "CSS functions bring mathematical logic to your stylesheets.\n\ncalc() — compute a value using mixed units:\n- width: calc(100% - 240px)\n- Subtract a fixed sidebar from full width without JavaScript\n\nclamp(minimum, ideal, maximum) — fluid value with bounds:\n- font-size: clamp(1rem, 4vw, 2.5rem)\n- Text that is always at least 1rem, ideally 4% of viewport, never larger than 2.5rem\n- Responsive without media queries\n\nmin(a, b) — picks the smaller value\nmax(a, b) — picks the larger value\n- width: min(90%, 1200px) — either 90% of screen or 1200px, whichever is smaller",
                "1. calc():\n- calc(expression) — supports +, -, *, /\n- Mix units: calc(100% - 24px)\n- Must have spaces around + and - operators\n- Use for: responsive widths, positioning, spacing\n\n2. clamp(min, ideal, max):\n- Returns ideal value, clamped between min and max\n- clamp(minimum, preferred, maximum)\n- Used for: fluid typography, responsive spacing\n- Replaces multiple media queries for gradual scaling\n\n3. min(value1, value2):\n- Returns the smallest of the values\n- min(90%, 1200px) — responsive container with max-width\n- Equivalent to: width: 90%; max-width: 1200px;\n\n4. max(value1, value2):\n- Returns the largest of the values\n- max(1rem, 2vw) — size is at least 1rem, grows with viewport\n- Good for minimum touch target sizes\n\n5. Nesting:\n- Functions can be nested: min(100%, max(200px, 50vw))\n- calc inside clamp: clamp(1rem, calc(1vw + 0.5rem), 2rem)\n\n6. Other CSS functions:\n- env(): CSS environment variables (safe area insets for notch phones)\n- var(): custom property value\n- rgb(), hsl(), oklch(): colour functions",
                "/* calc — mixed unit arithmetic */\n.sidebar-layout {\n  display: grid;\n  grid-template-columns: 260px calc(100% - 260px - 2rem);\n  gap: 2rem;\n}\n\n.full-minus-header {\n  height: calc(100vh - 60px);  /* full height minus navbar */\n}\n\n/* clamp — fluid typography */\nh1 { font-size: clamp(1.75rem, 4vw, 3.5rem); }\nh2 { font-size: clamp(1.5rem, 3vw, 2.5rem); }\np  { font-size: clamp(1rem, 1.5vw, 1.125rem); }\n\n/* clamp — fluid spacing */\n.section { padding: clamp(2rem, 5vw, 6rem) clamp(1rem, 5vw, 4rem); }\n\n/* min — responsive container */\n.container {\n  width: min(90%, 1280px);  /* 90% on small, 1280px max on large */\n  margin: 0 auto;\n}\n\n/* max — ensure minimum size */\n.icon {\n  width: max(44px, 3vw);  /* at least 44px (tap target), scales up */\n}\n\n/* Combining functions */\n.fluid-card {\n  width: clamp(280px, calc(33% - 2rem), 400px);\n  padding: max(1rem, 2vw);\n}",
                List.of(
                    new Concept.ConceptExample("Fluid typography scale with clamp",
                        "Type that scales smoothly between breakpoints without media queries.",
                        ":root {\n  /* Fluid typography — no media queries needed */\n  --text-xs:   clamp(0.75rem,  0.7vw + 0.5rem, 0.875rem);\n  --text-sm:   clamp(0.875rem, 0.8vw + 0.6rem, 1rem);\n  --text-base: clamp(1rem,     1vw  + 0.75rem, 1.125rem);\n  --text-lg:   clamp(1.125rem, 1.5vw + 0.8rem, 1.5rem);\n  --text-xl:   clamp(1.25rem,  2vw  + 0.9rem, 2rem);\n  --text-2xl:  clamp(1.5rem,   3vw  + 1rem,   2.5rem);\n  --text-3xl:  clamp(2rem,     5vw  + 1rem,   3.5rem);\n}\n\nbody { font-size: var(--text-base); }\nh1   { font-size: var(--text-3xl); }\nh2   { font-size: var(--text-2xl); }\np    { font-size: var(--text-base); line-height: 1.7; }",
                        "Type that fluidly scales across all screen sizes with no media query breakpoints")
                ),
                List.of(
                    "calc() supports mixed units: calc(100% - 240px) subtracts fixed px from relative %",
                    "clamp(min, ideal, max) replaces min-width media queries for gradual scaling",
                    "min(90%, 1200px) is the most concise responsive container — one line replaces width+max-width",
                    "Spaces are required around + and - in calc(): calc(100% - 60px) not calc(100%-60px)",
                    "Use clamp() for fluid typography — text scales proportionally without breakpoints"
                ),
                "Replace width: 90%; max-width: 1200px; with width: min(90%, 1200px);. It is more concise and equally readable. Use min(), max() and clamp() whenever you find yourself writing multiple properties to express a single constraint.",
                List.of(
                    "Missing spaces around + and - in calc() — browser ignores the whole value",
                    "Using vw for font-size without clamp — text can become unreadably small or large"
                ),
                18, 20, "A")
        );

        conceptRepository.saveAll(concepts);
        css.setTotalConcepts(concepts.size());
        subjectRepository.save(css);
        System.out.println("✅ CSS Fundamentals seeded — " + concepts.size() + " concepts");
    }

    // ─── JAVASCRIPT ADVANCED ─────────────────────────────────────────────────
    private void seedJavaScriptAdvanced() {
        Subject js = subjectRepository.save(sub(
            "JavaScript Advanced",
            "Master closures, prototypes, classes, async patterns, the event loop, Fetch API, ES modules and browser storage",
            "⚡", "#F0A500", "A"
        ));
        js.setOverview("JavaScript Advanced covers the concepts that separate intermediate developers from senior ones. Closures, prototypal inheritance, the event loop, Promises, async/await and ES Modules are tested in every senior JavaScript interview and used in every real-world JavaScript codebase.");
        js.setWhyLearn("Senior frontend developer, full-stack and Node.js roles require deep JavaScript knowledge. Closures, the event loop, async/await and Fetch are used in every modern JS application. These topics are the most common advanced interview questions.");
        js.setForWho("Students who have completed JavaScript Basics. You should be comfortable with functions, scope, objects, DOM and events.");
        js.setPrerequisites(List.of("JavaScript Basics completed", "Comfortable with functions, scope, and callbacks"));
        js.setOutcomes(List.of(
            "Explain and use closures for data encapsulation",
            "Understand JavaScript's prototype chain",
            "Use ES6 classes with inheritance",
            "Apply call, apply and bind to control this",
            "Work with Map, Set, WeakMap and WeakSet",
            "Explain the event loop and call stack",
            "Write Promise chains and async/await code",
            "Make HTTP requests using the Fetch API",
            "Use ES Modules with import and export"
        ));
        js.setWhatYouWillBuild(List.of(
            "A module-based app using ES Modules and Fetch API",
            "A memoization utility using closures",
            "An async data fetching layer with error handling"
        ));
        js.setToolsRequired(List.of("Modern browser (Chrome)", "VS Code", "Node.js (for ES Modules)", "Browser DevTools Network tab"));
        js.setDifficulty("Advanced");
        js.setEstimatedHours(14);
        js.setCareerUse("Required for senior frontend, full-stack, React/Vue/Angular developer and Node.js roles. Closures, the event loop and async/await appear in virtually every advanced JavaScript interview.");
        subjectRepository.save(js);

        List<Concept> concepts = List.of(

            // ── 1. Closures ───────────────────────────────────────────────────
            conceptRich(js, "Closures",
                "A closure is a function that remembers the variables from its outer scope even after that outer function has finished executing.",
                "Imagine you hired a personal assistant and gave them a secret code.\n\nEven after you leave the room, the assistant still remembers the code. That is a closure — the inner function (assistant) keeps access to the outer function's variables (the code) even after the outer function has returned.\n\nClosures happen automatically in JavaScript whenever a function is created inside another function. The inner function carries a reference to its outer scope — not a copy, but a live reference.\n\nClosures are used for:\n- Data privacy and encapsulation\n- Factory functions\n- Memoization\n- Event handlers that remember state",
                "1. How closures work:\n- Every function in JS creates a new scope\n- Inner functions have access to outer function's variables\n- When outer function returns, its scope normally gets destroyed\n- But if an inner function still references outer variables, those variables are kept alive\n- The inner function + the preserved outer scope = a closure\n\n2. Practical uses:\n- Counter with private state: inner function increments a variable the outside cannot touch\n- Factory functions: return customised functions with baked-in data\n- Module pattern: expose only what you want, hide the rest\n- Memoization: cache results inside a closure\n\n3. Closure in loops — classic interview question:\n- With var: all closures share the same variable (bug)\n- With let: each iteration creates a new binding (correct)\n\n4. Memory:\n- Closures keep outer variables in memory as long as the inner function exists\n- Can cause memory leaks if closures are held unnecessarily",
                "// Basic closure\nfunction makeCounter() {\n  let count = 0;  // private — cannot be accessed from outside\n\n  return {\n    increment() { count++; },\n    decrement() { count--; },\n    getCount() { return count; }\n  };\n}\n\nconst counter = makeCounter();\ncounter.increment();\ncounter.increment();\ncounter.increment();\ncounter.decrement();\nconsole.log(counter.getCount());  // 2\n// console.log(count);  // ReferenceError — count is private\n\n// Factory function using closure\nfunction makeMultiplier(factor) {\n  return (number) => number * factor;  // closes over factor\n}\n\nconst double = makeMultiplier(2);\nconst triple = makeMultiplier(3);\nconsole.log(double(5));   // 10\nconsole.log(triple(5));   // 15\n\n// Memoization using closure\nfunction memoize(fn) {\n  const cache = {};  // private cache\n  return function(n) {\n    if (n in cache) {\n      console.log('From cache:', n);\n      return cache[n];\n    }\n    cache[n] = fn(n);\n    return cache[n];\n  };\n}\n\nconst factorial = memoize(function f(n) {\n  return n <= 1 ? 1 : n * f(n - 1);\n});\nconsole.log(factorial(5));  // computed\nconsole.log(factorial(5));  // from cache",
                List.of(
                    new Concept.ConceptExample("Private state with closures",
                        "Use a closure to create an object with truly private data.",
                        "function createBankAccount(initialBalance) {\n  let balance = initialBalance;  // private\n  const transactions = [];        // private\n\n  return {\n    deposit(amount) {\n      balance += amount;\n      transactions.push(`+${amount}`);\n    },\n    withdraw(amount) {\n      if (amount > balance) {\n        console.log('Insufficient funds');\n        return;\n      }\n      balance -= amount;\n      transactions.push(`-${amount}`);\n    },\n    getBalance() { return balance; },\n    getHistory() { return [...transactions]; }  // copy, not reference\n  };\n}\n\nconst acc = createBankAccount(1000);\nacc.deposit(500);\nacc.withdraw(200);\nconsole.log(acc.getBalance());  // 1300\nconsole.log(acc.getHistory()); // ['+500', '-200']\n// acc.balance — undefined, truly private",
                        "1300\n['+500', '-200']"),
                    new Concept.ConceptExample("Closure in a loop — the classic interview question",
                        "Why var in loops breaks closures, and how let fixes it.",
                        "// Bug: all share the same i\nconst fnsVar = [];\nfor (var i = 0; i < 3; i++) {\n  fnsVar.push(() => i);\n}\nconsole.log(fnsVar.map(f => f()));  // [3, 3, 3] — all same!\n\n// Fixed with let — each iteration has its own binding\nconst fnsLet = [];\nfor (let i = 0; i < 3; i++) {\n  fnsLet.push(() => i);\n}\nconsole.log(fnsLet.map(f => f()));  // [0, 1, 2] — correct",
                        "[3, 3, 3]\n[0, 1, 2]")
                ),
                List.of(
                    "A closure is created every time a function is defined inside another function",
                    "Closures keep outer variables alive as long as the inner function exists",
                    "Use closures to create private variables that cannot be modified from outside",
                    "var in loops shares one binding across all closures — use let instead",
                    "Memoization, factory functions and the module pattern all rely on closures"
                ),
                "Closures are the foundation of many JavaScript patterns. When you see a function returning another function, think closure — the returned function carries its outer scope with it. This is how private state, factories, and memoization all work.",
                List.of(
                    "Using var in loops with closures — all closures share the same variable",
                    "Accidentally holding references to large objects in closures — prevents garbage collection",
                    "Thinking closures are only for complex patterns — every callback that references outer variables is a closure"
                ),
                22, 1, "B"),

            // ── 2. Prototypal Inheritance ─────────────────────────────────────
            conceptRich(js, "Prototypal Inheritance",
                "Every JavaScript object has a prototype — another object it inherits properties and methods from. This is how shared methods work in JavaScript without copying them to every instance.",
                "JavaScript does not use classical inheritance like Java or Python.\nIt uses prototype-based inheritance.\n\nEvery object has a hidden [[Prototype]] property pointing to another object. When you access a property that does not exist on the object, JavaScript looks at its prototype, then the prototype's prototype, and so on — this is the prototype chain.\n\nThis is why all arrays have push() and pop() — they are defined once on Array.prototype and every array inherits them.\n\nES6 classes are syntactic sugar over prototypes — under the hood, everything is still prototype-based.",
                "1. Prototype chain:\n- Every object has [[Prototype]] (accessible via __proto__ or Object.getPrototypeOf)\n- Property lookup: own properties first, then prototype, then prototype's prototype\n- Chain ends at Object.prototype (its prototype is null)\n\n2. Constructor functions (pre-ES6):\n- function Person(name) { this.name = name; }\n- Person.prototype.greet = function() { ... }\n- new Person('Ravi') creates an object with Person.prototype as its prototype\n\n3. Object.create():\n- Creates a new object with specified prototype\n- Object.create(proto) — cleanest way to set up prototype chain\n\n4. hasOwnProperty vs in:\n- obj.hasOwnProperty('key'): checks only own properties\n- 'key' in obj: checks own AND prototype chain\n\n5. instanceof operator:\n- obj instanceof Constructor: checks prototype chain\n\n6. ES6 class vs prototype:\n- class syntax compiles to prototype-based code\n- Same behaviour, cleaner syntax",
                "// Prototype chain\nconst animal = {\n  breathe() { return `${this.name} breathes`; }\n};\n\nconst dog = Object.create(animal);\ndog.name = 'Tommy';\ndog.bark = function() { return `${this.name} barks`; };\n\nconsole.log(dog.bark());     // Tommy barks (own method)\nconsole.log(dog.breathe());  // Tommy breathes (from prototype)\nconsole.log(Object.getPrototypeOf(dog) === animal);  // true\n\n// Constructor function + prototype\nfunction Person(name, age) {\n  this.name = name;\n  this.age = age;\n}\nPerson.prototype.greet = function() {\n  return `Hi, I'm ${this.name}`;\n};\n\nconst p1 = new Person('Ravi', 21);\nconst p2 = new Person('Priya', 22);\nconsole.log(p1.greet());\nconsole.log(p1.greet === p2.greet);  // true — shared method!\n\n// hasOwnProperty\nconsole.log(p1.hasOwnProperty('name'));  // true\nconsole.log(p1.hasOwnProperty('greet')); // false — on prototype\nconsole.log('greet' in p1);              // true — found in chain\n\n// instanceof\nconsole.log(p1 instanceof Person);  // true\nconsole.log(p1 instanceof Object);  // true — all objects inherit Object",
                List.of(
                    new Concept.ConceptExample("Prototype chain in action",
                        "Trace a property lookup through the prototype chain.",
                        "const base = { type: 'base', describe() { return this.type; } };\nconst mid = Object.create(base);\nmid.type = 'mid';\nconst top = Object.create(mid);\ntop.name = 'Top';\n\nconsole.log(top.name);      // 'Top' — own property\nconsole.log(top.type);      // 'mid' — from mid\nconsole.log(top.describe());// 'mid' — method from base, this is top\n\n// Chain visualization\nconsole.log(Object.getPrototypeOf(top) === mid);   // true\nconsole.log(Object.getPrototypeOf(mid) === base);  // true\nconsole.log(Object.getPrototypeOf(base) === Object.prototype); // true\nconsole.log(Object.getPrototypeOf(Object.prototype)); // null — end of chain",
                        "Top\nmid\nmid\ntrue\ntrue\ntrue\nnull")
                ),
                List.of(
                    "Every object has [[Prototype]] — property lookup traverses this chain",
                    "Methods on Constructor.prototype are shared across all instances — not copied",
                    "Object.create(proto) creates an object with proto as its prototype",
                    "hasOwnProperty checks only own properties; in checks the entire chain",
                    "ES6 class syntax is syntactic sugar — it compiles to prototype-based code"
                ),
                "Define shared methods on the prototype, not inside the constructor. If you put methods inside the constructor with this.greet = function(){}, each instance gets its own copy. Prototype methods are shared — one copy for all instances.",
                List.of(
                    "Modifying built-in prototypes (Array.prototype, String.prototype) — breaks libraries and future code",
                    "Confusing instance properties (set in constructor) with prototype properties (shared)"
                ),
                18, 2, "B"),

            // ── 3. Classes (ES6) ─────────────────────────────────────────────
            conceptRich(js, "Classes (ES6)",
                "ES6 classes are syntactic sugar over JavaScript's prototype system. They provide a clean, readable way to define objects with constructors, methods, inheritance, getters, setters and static members.",
                "Classes in JavaScript look like Java or Python classes but work differently under the hood. They are still prototype-based — class syntax just makes it cleaner to write.\n\nkey class features:\n- constructor(): runs on new ClassName()\n- Instance methods: defined in class body\n- Inheritance: class Dog extends Animal\n- super(): calls parent constructor or method\n- static: belongs to the class, not instances\n- getter/setter: controlled property access\n- Private fields (#): truly private (ES2022)",
                "1. Class syntax:\n- class ClassName { constructor() {} method() {} }\n- constructor runs when you call new ClassName()\n- Methods are on the prototype — shared across instances\n\n2. Inheritance:\n- class Child extends Parent\n- super() in constructor: calls Parent's constructor — must come before this\n- super.method(): calls parent's version of a method\n\n3. Static members:\n- static method() {} — called on the class, not instances\n- static property = value (ES2022)\n- Used for utility/factory methods\n\n4. Getters and setters:\n- get propName() {} — runs on property access\n- set propName(value) {} — runs on property assignment\n- No parentheses when calling: obj.propName not obj.propName()\n\n5. Private fields (ES2022):\n- #fieldName — truly private, only accessible inside the class\n- Declared at class level before constructor\n- SyntaxError if accessed outside class\n\n6. instanceof works with classes:\n- dog instanceof Dog and dog instanceof Animal both true",
                "class Animal {\n  #sound;  // private field\n\n  constructor(name, sound) {\n    this.name = name;\n    this.#sound = sound;\n  }\n\n  speak() {\n    return `${this.name} says ${this.#sound}`;\n  }\n\n  get info() {\n    return `${this.name} (${this.constructor.name})`;\n  }\n\n  static create(name, sound) {\n    return new Animal(name, sound);\n  }\n}\n\nclass Dog extends Animal {\n  #tricks = [];  // private field with default\n\n  constructor(name) {\n    super(name, 'Woof');\n  }\n\n  learn(trick) {\n    this.#tricks.push(trick);\n    return this;  // enable chaining\n  }\n\n  get tricks() { return [...this.#tricks]; }  // read-only copy\n\n  speak() {\n    return super.speak() + '!';\n  }\n}\n\nconst dog = new Dog('Tommy');\ndog.learn('sit').learn('fetch');\nconsole.log(dog.speak());\nconsole.log(dog.tricks);\nconsole.log(dog.info);\nconsole.log(dog instanceof Animal);  // true\n\nconst cat = Animal.create('Whiskers', 'Meow');\nconsole.log(cat.speak());",
                List.of(
                    new Concept.ConceptExample("Class with validation using private fields",
                        "Private fields and setters enforce data integrity.",
                        "class BankAccount {\n  #balance;\n  #owner;\n\n  constructor(owner, initialBalance = 0) {\n    this.#owner = owner;\n    this.#balance = 0;\n    this.deposit(initialBalance);\n  }\n\n  deposit(amount) {\n    if (amount <= 0) throw new Error('Amount must be positive');\n    this.#balance += amount;\n    return this;\n  }\n\n  withdraw(amount) {\n    if (amount > this.#balance) throw new Error('Insufficient funds');\n    this.#balance -= amount;\n    return this;\n  }\n\n  get balance() { return this.#balance; }\n  get owner() { return this.#owner; }\n\n  toString() {\n    return `${this.#owner}: Rs.${this.#balance}`;\n  }\n}\n\nconst acc = new BankAccount('Ravi', 1000);\nacc.deposit(500).withdraw(200);\nconsole.log(acc.balance);\nconsole.log(acc.toString());",
                        "1300\nRavi: Rs.1300")
                ),
                List.of(
                    "class is syntactic sugar — under the hood it uses prototypes",
                    "super() must be called in child constructor before using this",
                    "static methods are called on the class: Animal.create(), not instance",
                    "Private fields (#name) are truly private — SyntaxError if accessed outside class",
                    "Getters are accessed as properties: obj.balance not obj.balance()"
                ),
                "Use private fields (#) for all internal state that should not be accessible from outside. This is better than the convention of using underscore (_name) which only signals intent but does not enforce privacy.",
                List.of(
                    "Forgetting super() in child constructor — ReferenceError: must call super before this",
                    "Calling static methods on instances — Animal.create() not new Animal().create()"
                ),
                18, 3, "B"),

            // ── 4. Call, Apply, Bind ──────────────────────────────────────────
            conceptRich(js, "Call, Apply, Bind",
                "call, apply and bind are methods on every function that let you explicitly set what 'this' refers to when the function runs.",
                "Every JavaScript function has three methods inherited from Function.prototype: call, apply and bind.\n\nThey all solve the same problem: controlling what this is inside a function.\n\nNormal function call: this is determined by how the function is called.\ncall/apply/bind: you decide what this is, regardless of how it is called.\n\n- call: invoke immediately with explicit this\n- apply: same as call but arguments passed as an array\n- bind: return a new function with this permanently fixed",
                "1. call(thisArg, ...args):\n- Calls function immediately\n- First argument is the value for this\n- Remaining arguments are passed to the function\n- fn.call(obj, arg1, arg2)\n\n2. apply(thisArg, [argsArray]):\n- Same as call but arguments passed as an array\n- fn.apply(obj, [arg1, arg2])\n- Useful when you already have arguments in an array\n\n3. bind(thisArg, ...args):\n- Does NOT call the function immediately\n- Returns a NEW function with this permanently fixed\n- Partial application: pre-fill some arguments\n- Common for event handlers and callbacks\n\n4. When to use which:\n- call: when you need to borrow a method with specific args\n- apply: when args are already in an array — or Math.max.apply\n- bind: when passing a method as callback and need to preserve this\n\n5. Arrow functions:\n- call/apply/bind do not work on arrow functions — they have no own this",
                "// call\nfunction introduce(city, country) {\n  return `${this.name} from ${city}, ${country}`;\n}\n\nconst user1 = { name: 'Ravi' };\nconst user2 = { name: 'Priya' };\n\nconsole.log(introduce.call(user1, 'Hyderabad', 'India'));\nconsole.log(introduce.call(user2, 'Mumbai', 'India'));\n\n// apply — same but args as array\nconst args = ['Chennai', 'India'];\nconsole.log(introduce.apply(user1, args));\n\n// apply with Math.max\nconst nums = [5, 3, 8, 1, 9, 4];\nconsole.log(Math.max.apply(null, nums));  // 9\nconsole.log(Math.max(...nums));           // 9 (modern way)\n\n// bind — returns new function\nconst boundIntro = introduce.bind(user1);\nconsole.log(boundIntro('Bangalore', 'India'));\n\n// Partial application with bind\nconst fromHyd = introduce.bind(user1, 'Hyderabad');\nconsole.log(fromHyd('India'));\n\n// bind for event handler — fix this\nclass Timer {\n  constructor() { this.seconds = 0; }\n  tick() { this.seconds++; console.log(this.seconds); }\n  start() {\n    // Without bind, this.tick's 'this' would be wrong\n    setInterval(this.tick.bind(this), 1000);\n  }\n}",
                List.of(
                    new Concept.ConceptExample("Method borrowing with call",
                        "Borrow an array method for an array-like object.",
                        "// arguments object is array-like but not a real array\nfunction showArgs() {\n  // arguments.forEach is not a function!\n  // Borrow Array's forEach with call\n  Array.prototype.forEach.call(arguments, (arg, i) => {\n    console.log(`Arg ${i}: ${arg}`);\n  });\n\n  // Modern way: Array.from(arguments)\n  const arr = Array.from(arguments);\n  console.log('As array:', arr);\n}\n\nshowArgs('a', 'b', 'c');",
                        "Arg 0: a\nArg 1: b\nArg 2: c\nAs array: ['a', 'b', 'c']")
                ),
                List.of(
                    "call invokes immediately with explicit this; apply same but args in array; bind returns new function",
                    "Use bind when passing a method as callback to preserve its this",
                    "Partial application: bind(thisArg, arg1) pre-fills arg1 for every future call",
                    "call/apply/bind do nothing to arrow functions — arrow functions have no own this",
                    "Math.max.apply(null, array) is the old way to find max of array — use spread (...) now"
                ),
                "Use bind when you pass an object method as a callback. Without it, this becomes undefined or window. class Timer { start() { setInterval(this.tick.bind(this), 1000); } } is the classic pattern.",
                List.of(
                    "Trying to use bind/call/apply on arrow functions to change this — arrow functions ignore them",
                    "Forgetting bind returns a new function — you must use the returned function"
                ),
                15, 4, "B"),

            // ── 5. Function Currying ──────────────────────────────────────────
            conceptRich(js, "Function Currying",
                "Currying transforms a function that takes multiple arguments into a sequence of functions that each take one argument. It enables partial application and reusable specialised functions.",
                "Currying is named after mathematician Haskell Curry.\n\nA curried function instead of taking all arguments at once, takes them one at a time:\n\nNormal: add(2, 3) → 5\nCurried: add(2)(3) → 5\n\nWhy is this useful?\n- Create specialised versions of a function by pre-filling some arguments\n- Build reusable pipelines\n- Delay execution until all arguments are available\n\nCurrying + closures is what makes this possible — each partial application returns a closure that remembers the earlier arguments.",
                "1. Manual currying:\n- Return a function from a function\n- Each returned function takes the next argument\n- When all arguments collected, compute result\n\n2. Generic curry utility:\n- function curry(fn) that wraps any function\n- Collects arguments until fn.length is reached\n- Then calls the original function\n\n3. Partial application vs currying:\n- Partial application: pre-fill SOME arguments (using bind or curry)\n- Currying: transform into series of one-argument functions\n- Related but different concepts\n\n4. Real-world uses:\n- Configuration functions\n- Middleware pipelines\n- Event handlers with pre-filled context\n- Functional composition",
                "// Manual curried function\nfunction multiply(a) {\n  return function(b) {\n    return a * b;\n  };\n}\n\nconst double = multiply(2);\nconst triple = multiply(3);\nconsole.log(double(5));   // 10\nconsole.log(triple(5));   // 15\n\n// Arrow function version — cleaner\nconst add = a => b => a + b;\nconst add10 = add(10);\nconsole.log(add10(5));    // 15\nconsole.log(add10(20));   // 30\n\n// Generic curry utility\nfunction curry(fn) {\n  return function curried(...args) {\n    if (args.length >= fn.length) {\n      return fn(...args);\n    }\n    return function(...moreArgs) {\n      return curried(...args, ...moreArgs);\n    };\n  };\n}\n\nconst curriedAdd = curry((a, b, c) => a + b + c);\nconsole.log(curriedAdd(1)(2)(3));   // 6\nconsole.log(curriedAdd(1, 2)(3));   // 6\nconsole.log(curriedAdd(1)(2, 3));   // 6\nconsole.log(curriedAdd(1, 2, 3));   // 6\n\n// Real-world: filter builder\nconst filter = key => value => item => item[key] === value;\nconst isAdmin = filter('role')('admin');\n\nconst users = [\n  { name: 'Ravi', role: 'admin' },\n  { name: 'Priya', role: 'student' },\n  { name: 'Arjun', role: 'admin' }\n];\nconsole.log(users.filter(isAdmin).map(u => u.name));",
                List.of(
                    new Concept.ConceptExample("URL builder using currying",
                        "Build specialised URL functions using curried functions.",
                        "const buildUrl = base => path => params => {\n  const query = params\n    ? '?' + Object.entries(params).map(([k,v]) => `${k}=${v}`).join('&')\n    : '';\n  return `${base}${path}${query}`;\n};\n\nconst apiUrl = buildUrl('https://api.example.com');\nconst usersUrl = apiUrl('/users');\nconst productsUrl = apiUrl('/products');\n\nconsole.log(usersUrl(null));\nconsole.log(usersUrl({ page: 1, limit: 10 }));\nconsole.log(productsUrl({ category: 'books' }));",
                        "https://api.example.com/users\nhttps://api.example.com/users?page=1&limit=10\nhttps://api.example.com/products?category=books")
                ),
                List.of(
                    "Currying transforms fn(a, b, c) into fn(a)(b)(c)",
                    "Each partial application returns a closure that remembers previous arguments",
                    "Currying enables creating specialised functions from general ones",
                    "Arrow function currying: const add = a => b => a + b is compact and clear"
                ),
                "Use currying when you have a general function and need several specialised versions of it. Instead of calling filter(items, 'role', 'admin') every time, curry it to create an isAdmin filter you can reuse everywhere.",
                List.of(
                    "Confusing currying with partial application — currying always creates single-argument functions",
                    "Forgetting that fn.length does not count rest parameters or default parameters"
                ),
                15, 5, "B"),

            // ── 6. Map, Set, WeakMap, WeakSet ─────────────────────────────────
            conceptRich(js, "Map, Set, WeakMap, WeakSet",
                "Map is a key-value store where keys can be any type. Set is a collection of unique values. WeakMap and WeakSet use weak references and allow garbage collection of their keys.",
                "JavaScript objects and arrays cover most data structure needs, but they have limitations:\n\nObject keys must be strings or symbols. Map allows any type as key — even other objects.\n\nArrays allow duplicates and need indexOf for membership checks. Set only stores unique values and has an O(1) has() method.\n\nWeakMap and WeakSet are special versions that hold weak references — they allow the garbage collector to remove their entries when no other references exist. Useful for caching without memory leaks.",
                "1. Map:\n- new Map()\n- map.set(key, value): add entry\n- map.get(key): retrieve value\n- map.has(key): boolean\n- map.delete(key): remove\n- map.size: number of entries\n- Keys can be any type: objects, functions, primitives\n- Maintains insertion order\n- Iterable: for...of, forEach, spread\n\n2. Set:\n- new Set([iterable])\n- set.add(value): add (duplicate ignored)\n- set.has(value): O(1) membership check\n- set.delete(value)\n- set.size\n- Maintains insertion order\n- Used for unique values and fast lookups\n\n3. Map vs Object:\n- Map allows any key type; Object only string/symbol\n- Map maintains insertion order guaranteed\n- Map has .size; Object needs Object.keys().length\n- Map is better for frequent add/remove\n\n4. WeakMap:\n- Keys must be objects\n- Weak reference — key can be garbage collected\n- Not iterable, no size property\n- Use case: caching DOM node data without memory leaks\n\n5. WeakSet:\n- Only objects as members\n- Weak references — members can be garbage collected\n- Not iterable, no size\n- Use case: tracking which objects have been processed",
                "// Map — any key type\nconst map = new Map();\nmap.set('name', 'Ravi');\nmap.set(42, 'the answer');\nmap.set(true, 'boolean key');\n\nconst objKey = { id: 1 };\nmap.set(objKey, 'object as key');\n\nconsole.log(map.get('name'));    // Ravi\nconsole.log(map.get(42));        // the answer\nconsole.log(map.get(objKey));    // object as key\nconsole.log(map.size);           // 4\n\n// Iterate Map\nfor (const [key, value] of map) {\n  console.log(key, '->', value);\n}\n\n// Set — unique values\nconst set = new Set([1, 2, 2, 3, 3, 3, 4]);\nconsole.log(set);          // Set {1, 2, 3, 4}\nconsole.log(set.size);     // 4\nconsole.log(set.has(3));   // true — O(1)\n\n// Remove duplicates from array\nconst arr = [1, 2, 2, 3, 3, 4];\nconst unique = [...new Set(arr)];\nconsole.log(unique);       // [1, 2, 3, 4]\n\n// Set operations\nconst a = new Set([1, 2, 3, 4]);\nconst b = new Set([3, 4, 5, 6]);\nconst union = new Set([...a, ...b]);\nconst intersection = new Set([...a].filter(x => b.has(x)));\nconsole.log([...union]);        // [1,2,3,4,5,6]\nconsole.log([...intersection]); // [3,4]",
                List.of(
                    new Concept.ConceptExample("WeakMap for DOM element metadata",
                        "Use WeakMap to attach data to DOM elements without memory leaks.",
                        "// WeakMap: keys are objects, values can be anything\n// When the DOM element is removed, its entry is automatically cleaned up\n\nconst elementData = new WeakMap();\n\nfunction trackElement(element, data) {\n  elementData.set(element, data);\n}\n\nfunction getElementData(element) {\n  return elementData.get(element);\n}\n\n// Simulate DOM elements as plain objects\nconst btn1 = { id: 'submit', type: 'button' };\nconst btn2 = { id: 'cancel', type: 'button' };\n\ntrackElement(btn1, { clickCount: 0, lastClicked: null });\ntrackElement(btn2, { clickCount: 0, lastClicked: null });\n\nconst data = getElementData(btn1);\ndata.clickCount++;\nconsole.log(getElementData(btn1));\n\n// When btn1 is set to null and GC runs,\n// its WeakMap entry is automatically removed\n// btn1 = null;",
                        "{ clickCount: 1, lastClicked: null }")
                ),
                List.of(
                    "Map allows any type as key; Object only allows string or Symbol keys",
                    "Set automatically removes duplicates; [...new Set(array)] is the fastest dedup",
                    "Set.has() is O(1) — much faster than Array.includes() for large datasets",
                    "WeakMap keys must be objects and are weakly referenced — no memory leaks",
                    "WeakMap and WeakSet are not iterable — you cannot loop over them"
                ),
                "Use a Set instead of an array when you need fast membership checks (has()) and don't want duplicates. For large datasets, Set.has() is dramatically faster than Array.includes() because Set uses a hash table internally.",
                List.of(
                    "Using a regular Map or Set where WeakMap/WeakSet would prevent memory leaks",
                    "Trying to iterate WeakMap or WeakSet — they are not iterable"
                ),
                18, 6, "C"),

            // ── 7. Error Handling ─────────────────────────────────────────────
            conceptRich(js, "Error Handling (try, catch, finally)",
                "JavaScript uses try/catch/finally to handle runtime errors. Custom error classes, async error handling and global error handlers are all essential for production code.",
                "Every program encounters errors — invalid input, network failures, missing data.\n\nWithout error handling, your program crashes with an ugly stack trace.\nWith error handling, you catch the error, respond appropriately, and keep running.\n\ntry: put code that might fail here\ncatch: handle the error here\nfinally: always runs — cleanup code\n\nIn production JavaScript you also need to handle async errors in Promises and async/await, and catch unhandled errors globally.",
                "1. try/catch/finally:\n- try block runs normally\n- If error thrown, jumps to catch(error)\n- finally always runs — even if return in try or catch\n\n2. The Error object:\n- error.message: human-readable description\n- error.name: error type ('TypeError', 'ReferenceError', etc.)\n- error.stack: full stack trace string\n\n3. Built-in error types:\n- TypeError: wrong type operation\n- ReferenceError: accessing undefined variable\n- SyntaxError: invalid code syntax\n- RangeError: value out of valid range\n- URIError, EvalError\n\n4. Custom errors:\n- class ValidationError extends Error { constructor(msg) { super(msg); this.name = 'ValidationError'; } }\n\n5. Async error handling:\n- Promise: .catch(err => ...)\n- async/await: try/catch block\n- Unhandled rejection: process.on or window.addEventListener\n\n6. throw:\n- throw new Error('message') — throws any value\n- Best practice: always throw Error objects, not strings",
                "// Basic try/catch/finally\nfunction parseJSON(str) {\n  try {\n    return JSON.parse(str);\n  } catch (err) {\n    console.log('Parse error:', err.message);\n    return null;\n  } finally {\n    console.log('Parse attempt complete');\n  }\n}\n\nconsole.log(parseJSON('{\"name\": \"Ravi\"}'));\nconsole.log(parseJSON('invalid json'));\n\n// Custom error class\nclass ValidationError extends Error {\n  constructor(field, message) {\n    super(message);\n    this.name = 'ValidationError';\n    this.field = field;\n  }\n}\n\nfunction validateAge(age) {\n  if (typeof age !== 'number') throw new TypeError('Age must be a number');\n  if (age < 0 || age > 150) throw new ValidationError('age', 'Age must be 0-150');\n  return true;\n}\n\ntry {\n  validateAge(-5);\n} catch (err) {\n  if (err instanceof ValidationError) {\n    console.log(`Validation failed on field '${err.field}': ${err.message}`);\n  } else {\n    console.log('Unexpected error:', err.message);\n  }\n}",
                List.of(
                    new Concept.ConceptExample("Error handling in async code",
                        "Handle errors in both Promise chains and async/await.",
                        "// Promise chain error handling\nfunction fetchData(url) {\n  return fetch(url)\n    .then(res => {\n      if (!res.ok) throw new Error(`HTTP ${res.status}`);\n      return res.json();\n    })\n    .catch(err => {\n      console.log('Fetch failed:', err.message);\n      return null;\n    });\n}\n\n// async/await error handling\nasync function getData(url) {\n  try {\n    const res = await fetch(url);\n    if (!res.ok) throw new Error(`HTTP error: ${res.status}`);\n    return await res.json();\n  } catch (err) {\n    console.error('Error fetching data:', err.message);\n    return null;\n  }\n}\n\n// Simulate for demo\nasync function demo() {\n  try {\n    throw new Error('Network timeout');\n  } catch (err) {\n    console.log('Caught async error:', err.message);\n  } finally {\n    console.log('Cleanup done');\n  }\n}\ndemo();",
                        "Caught async error: Network timeout\nCleanup done")
                ),
                List.of(
                    "catch receives the Error object — use err.message and err.name",
                    "finally always runs — use it to release resources regardless of success or failure",
                    "Create custom error classes by extending Error — set this.name in constructor",
                    "Use instanceof to distinguish different error types in catch",
                    "Always throw Error objects, not plain strings — Error objects include stack traces"
                ),
                "Use custom error classes to distinguish different failure modes. When you catch errors, checking instanceof ValidationError vs instanceof NetworkError tells you exactly what went wrong and how to respond — without parsing error messages.",
                List.of(
                    "Catching all errors and swallowing them silently — always log or re-throw",
                    "Throwing a string instead of an Error object — you lose the stack trace"
                ),
                15, 7, "C"),

            // ── 8. JavaScript Runtime Environment ────────────────────────────
            conceptRich(js, "JavaScript Runtime Environment",
                "The JavaScript runtime includes the engine, call stack, memory heap, Web APIs, callback queue and event loop. Understanding this explains how JavaScript handles asynchronous operations despite being single-threaded.",
                "JavaScript is single-threaded — it can only do one thing at a time. Yet it handles multiple network requests, timers and user events simultaneously. How?\n\nThe runtime environment has several components working together:\n\n- JS Engine (V8, SpiderMonkey): parses and executes JavaScript\n- Call Stack: tracks what function is currently running\n- Memory Heap: where objects and closures are stored\n- Web APIs (browser) or C++ APIs (Node.js): handle async operations like setTimeout, fetch, DOM events\n- Callback Queue (Task Queue): stores callbacks ready to run\n- Microtask Queue: stores Promise callbacks (higher priority)\n- Event Loop: moves callbacks from queues to call stack when stack is empty",
                "1. Call Stack:\n- LIFO data structure (Last In, First Out)\n- When function is called, pushed onto stack\n- When function returns, popped off stack\n- Stack overflow: infinite recursion fills the stack\n\n2. Memory Heap:\n- Unstructured memory where objects/closures are stored\n- Garbage collector frees unused memory\n\n3. Web APIs:\n- Browser-provided: setTimeout, fetch, addEventListener, DOM\n- These run outside the JS engine (in browser's C++ code)\n- JavaScript does not block waiting for them\n\n4. Callback Queue (Task Queue / Macro Queue):\n- Holds callbacks from setTimeout, setInterval, I/O\n- Processed after each complete task\n\n5. Microtask Queue:\n- Holds Promise callbacks (.then, .catch), queueMicrotask\n- HIGHER PRIORITY than callback queue\n- Drained completely before any task from callback queue runs\n\n6. Event Loop:\n- Continuously checks: is call stack empty?\n- If yes: first drain microtask queue, then pick one task from callback queue\n- Repeat forever",
                "// Single-threaded but non-blocking\nconsole.log('1. Start');\n\nsetTimeout(() => console.log('4. setTimeout'), 0);\n\nPromise.resolve().then(() => console.log('3. Promise microtask'));\n\nconsole.log('2. End');\n\n// Output order: 1, 2, 3, 4\n// Why? synchronous runs first (1, 2)\n// Then microtask queue (3 — Promise)\n// Then callback queue (4 — setTimeout)\n\n// Stack overflow — infinite recursion\nfunction infinite() {\n  return infinite();\n}\n// try { infinite(); } catch(e) { console.log(e.message); }  // Maximum call stack size exceeded\n\n// Long task blocks everything\nfunction blockFor(ms) {\n  const start = Date.now();\n  while (Date.now() - start < ms) {}  // busy-wait\n}\n// blockFor(3000) — blocks UI for 3 seconds!\n// This is why heavy computation should be in Web Workers",
                List.of(
                    new Concept.ConceptExample("Microtask vs macrotask order",
                        "Demonstrate the exact execution order of sync, microtask and macrotask.",
                        "console.log('sync 1');\n\nsetTimeout(() => console.log('setTimeout 1'), 0);\nsetTimeout(() => console.log('setTimeout 2'), 0);\n\nPromise.resolve()\n  .then(() => console.log('promise 1'))\n  .then(() => console.log('promise 2'));\n\nqueueMicrotask(() => console.log('microtask'));\n\nconsole.log('sync 2');\n\n// Order: sync 1, sync 2, promise 1, promise 2, microtask, setTimeout 1, setTimeout 2",
                        "sync 1\nsync 2\npromise 1\npromise 2\nmicrotask\nsetTimeout 1\nsetTimeout 2")
                ),
                List.of(
                    "JS is single-threaded — only one piece of code runs at a time",
                    "Call stack tracks execution; heap stores objects; Web APIs handle async work",
                    "Microtask queue (Promises) runs before callback queue (setTimeout) — every time",
                    "Event loop only picks up callbacks when the call stack is empty",
                    "Never block the call stack with long synchronous operations — use async patterns"
                ),
                "Remember: microtasks always run before macrotasks. When a Promise resolves, its .then callback goes to the microtask queue and runs before any setTimeout callback, even a setTimeout(0). This order matters for debugging async code.",
                List.of(
                    "Expecting setTimeout(fn, 0) to run immediately — it runs after current execution and microtasks",
                    "Running CPU-intensive code synchronously — blocks the UI/event loop"
                ),
                18, 8, "B"),

            // ── 9. The Event Loop (Advanced) ──────────────────────────────────
            conceptRich(js, "The Event Loop (Advanced Deep Dive)",
                "The event loop coordinates the call stack, microtask queue and callback queue to enable non-blocking asynchronous behaviour in JavaScript's single-threaded environment.",
                "Building on the Runtime Environment concept, let us go deeper into how the event loop actually works in practice.\n\nThe event loop is not just a concept — it directly affects:\n- Order of console.log output in async code\n- When Promise callbacks run relative to setTimeout\n- Why UI feels sluggish during heavy computation\n- How Node.js handles thousands of concurrent requests\n\nMastering this helps you write correct async code and debug complex ordering issues.",
                "1. Detailed event loop algorithm:\n- Step 1: Run all synchronous code (call stack)\n- Step 2: Run ALL microtasks (Promise.then, queueMicrotask) until queue empty\n- Step 3: Pick ONE task from callback queue (setTimeout, setInterval, I/O)\n- Step 4: Render if needed (browser only)\n- Step 5: Repeat from Step 2\n\n2. Microtask queue starvation:\n- If microtasks keep adding more microtasks, callback queue never runs\n- queueMicrotask in a loop can freeze the page\n\n3. Tasks vs Microtasks:\n- Macrotasks (callback queue): setTimeout, setInterval, requestAnimationFrame, I/O, UI events\n- Microtasks: Promise.then/catch/finally, queueMicrotask, MutationObserver\n\n4. requestAnimationFrame:\n- Runs before next paint — between macrotasks\n- Use for animations — synced with display refresh (60fps)\n\n5. Node.js event loop phases:\n- timers → pending callbacks → idle → poll → check (setImmediate) → close\n- process.nextTick runs before any other microtask\n\n6. Practical implications:\n- Batch DOM updates — many DOM changes in one task is faster than spread across tasks\n- Long tasks → break into chunks using setTimeout(fn, 0)",
                "// Advanced ordering\nasync function example() {\n  console.log('1 async start');\n\n  await Promise.resolve();\n  console.log('3 after first await');\n\n  await Promise.resolve();\n  console.log('5 after second await');\n}\n\nconsole.log('0 sync start');\nexample();\nconsole.log('2 sync end');\nsetTimeout(() => console.log('6 setTimeout'), 0);\n\n// Output: 0, 1, 2, 3, 5, 6\n// await splits the function — everything after await is a microtask\n\n// Breaking long task into chunks\nfunction processLargeArray(arr, onProgress) {\n  let index = 0;\n  function processChunk() {\n    const chunkEnd = Math.min(index + 100, arr.length);\n    while (index < chunkEnd) {\n      // process arr[index]\n      index++;\n    }\n    onProgress(index / arr.length);\n    if (index < arr.length) {\n      setTimeout(processChunk, 0);  // yield to event loop\n    }\n  }\n  setTimeout(processChunk, 0);\n}\n\n// This allows UI to remain responsive during processing",
                List.of(
                    new Concept.ConceptExample("Promise vs setTimeout execution order",
                        "Trace exact output order through the event loop.",
                        "console.log('A');\n\nsetTimeout(() => {\n  console.log('B setTimeout');\n  Promise.resolve().then(() => console.log('C promise inside timeout'));\n}, 0);\n\nPromise.resolve()\n  .then(() => {\n    console.log('D promise 1');\n    return Promise.resolve();\n  })\n  .then(() => console.log('E promise 2'));\n\nconsole.log('F');\n\n// A (sync)\n// F (sync)\n// D (microtask)\n// E (microtask from D's return)\n// B (macrotask)\n// C (microtask — runs before next macrotask)",
                        "A\nF\nD promise 1\nE promise 2\nB setTimeout\nC promise inside timeout")
                ),
                List.of(
                    "Microtasks drain completely before any macrotask — even newly added microtasks",
                    "await effectively splits the async function — everything after is a microtask",
                    "A single long synchronous task blocks all rendering and other tasks",
                    "Break large computations into chunks using setTimeout(fn, 0) to stay responsive",
                    "process.nextTick (Node.js) runs before other microtasks"
                ),
                "If your UI freezes during a computation, break the work into smaller chunks and schedule them with setTimeout(fn, 0). Each chunk runs as a separate macrotask, giving the event loop time to process user interactions and re-render between chunks.",
                List.of(
                    "Assuming microtasks and macrotasks run in creation order — microtasks always run first",
                    "Creating infinite microtask chains — can starve the macrotask queue"
                ),
                18, 9, "B"),

            // ── 10. Promises ──────────────────────────────────────────────────
            conceptRich(js, "Promises",
                "A Promise represents a value that will be available in the future. It solves callback hell by providing a cleaner way to handle asynchronous operations.",
                "A Promise is an object that represents the eventual result of an async operation.\n\nA Promise is in one of three states:\n- Pending: operation not yet complete\n- Fulfilled: operation succeeded, value available\n- Rejected: operation failed, error available\n\nOnce settled (fulfilled or rejected), a Promise never changes state.\n\nPromises solve callback hell by allowing you to chain async operations in a flat, readable structure rather than nesting callbacks inside callbacks.",
                "1. Creating a Promise:\n- new Promise((resolve, reject) => { ... })\n- Call resolve(value) on success\n- Call reject(error) on failure\n\n2. Consuming a Promise:\n- promise.then(value => ...) — handle success\n- promise.catch(err => ...) — handle failure\n- promise.finally(() => ...) — always runs\n\n3. Promise chaining:\n- .then returns a new Promise\n- Return a value: next .then receives it\n- Return a Promise: next .then waits for it\n- throw in .then: goes to .catch\n\n4. Static Promise methods:\n- Promise.resolve(value): immediately resolved\n- Promise.reject(error): immediately rejected\n- Promise.all([p1, p2]): wait for all, fail if any fails\n- Promise.allSettled([p1, p2]): wait for all regardless\n- Promise.race([p1, p2]): first settled wins\n- Promise.any([p1, p2]): first fulfilled wins\n\n5. Promise vs callback:\n- Promise: flat chain, single error handler\n- Callback: nested pyramid, error handling per callback",
                "// Creating a Promise\nfunction delay(ms) {\n  return new Promise(resolve => setTimeout(resolve, ms));\n}\n\nfunction fetchUser(id) {\n  return new Promise((resolve, reject) => {\n    setTimeout(() => {\n      if (id > 0) resolve({ id, name: 'Ravi', role: 'student' });\n      else reject(new Error('Invalid user ID'));\n    }, 100);\n  });\n}\n\n// Consuming\nfetchUser(1)\n  .then(user => {\n    console.log('User:', user.name);\n    return user.role;\n  })\n  .then(role => console.log('Role:', role))\n  .catch(err => console.log('Error:', err.message))\n  .finally(() => console.log('Request complete'));\n\n// Promise.all — run in parallel, wait for all\nPromise.all([fetchUser(1), fetchUser(2), delay(50)])\n  .then(([u1, u2]) => console.log('Both users:', u1.name, u2.name))\n  .catch(err => console.log('One failed:', err.message));\n\n// Promise.allSettled — don't fail if one rejects\nPromise.allSettled([fetchUser(1), fetchUser(-1)])\n  .then(results => {\n    results.forEach(r => {\n      if (r.status === 'fulfilled') console.log('OK:', r.value.name);\n      else console.log('Failed:', r.reason.message);\n    });\n  });",
                List.of(
                    new Concept.ConceptExample("Promise.all vs Promise.allSettled",
                        "Use Promise.all when all must succeed; allSettled when you want all results regardless.",
                        "const fetchScore = id => new Promise((res, rej) =>\n  setTimeout(() => id % 2 === 0 ? res({ id, score: 85 }) : rej(new Error(`No score for ${id}`)), 100)\n);\n\n// Promise.all — fails fast\nPromise.all([fetchScore(2), fetchScore(4), fetchScore(6)])\n  .then(scores => console.log('All scores:', scores.map(s => s.score)))\n  .catch(err => console.log('Promise.all failed:', err.message));\n\n// Promise.allSettled — all results\nPromise.allSettled([fetchScore(1), fetchScore(2), fetchScore(3)])\n  .then(results => {\n    const passed = results.filter(r => r.status === 'fulfilled').map(r => r.value.score);\n    const failed = results.filter(r => r.status === 'rejected').length;\n    console.log(`Scores: ${passed}, Failed: ${failed}`);\n  });",
                        "All scores: [85, 85, 85]\nScores: [85], Failed: 2")
                ),
                List.of(
                    "A Promise is either pending, fulfilled or rejected — once settled it never changes",
                    ".then returns a new Promise — you can chain as many as needed",
                    "One .catch at the end of a chain handles errors from any .then above it",
                    "Promise.all fails immediately if any Promise rejects — use allSettled for all results",
                    "throw inside .then sends control to .catch — just like throw in synchronous code"
                ),
                "Put a single .catch at the end of your Promise chain to handle all errors. You do not need a .catch after every .then — errors bubble down the chain to the first .catch, just like try/catch.",
                List.of(
                    "Forgetting to return a Promise inside .then — breaks the chain",
                    "Not adding .catch — unhandled rejections crash Node.js and give warnings in browsers"
                ),
                18, 10, "B"),

            // ── 11. Promise Chaining ──────────────────────────────────────────
            conceptRich(js, "Promise Chaining",
                "Promise chaining links multiple async operations sequentially. Each .then receives the result of the previous one and can return a value or a new Promise.",
                "Promise chaining is how you sequence multiple async operations without nesting.\n\nInstead of:\nfetchUser(id, (user) => {\n  fetchPosts(user.id, (posts) => {\n    fetchComments(posts[0], (comments) => {\n      ...\n    });\n  });\n});\n\nYou write:\nfetchUser(id)\n  .then(user => fetchPosts(user.id))\n  .then(posts => fetchComments(posts[0]))\n  .then(comments => display(comments))\n  .catch(err => handleError(err));\n\nFlat, readable, one error handler for everything.",
                "1. Chaining rules:\n- Return a value from .then → next .then gets that value\n- Return a Promise from .then → next .then waits for that Promise\n- Throw an error → goes to .catch\n- Return from .catch → chain continues as resolved\n\n2. Common patterns:\n- Sequential fetch: fetch user, then fetch their posts, then display\n- Transform: get raw data, parse, validate, format — each step in a .then\n- Error recovery: .catch returns a fallback value so chain continues\n\n3. Promise.all in chains:\n- Within a chain, use Promise.all to do parallel work then continue\n\n4. Anti-patterns:\n- Nesting .then inside .then (callback hell with Promises)\n- Forgetting to return the Promise from .then\n- Creating new Promise inside .then unnecessarily (Promise constructor anti-pattern)",
                "// Sequential chain\nfunction getUser(id) {\n  return Promise.resolve({ id, name: 'Ravi', postIds: [1, 2] });\n}\nfunction getPosts(ids) {\n  return Promise.resolve(ids.map(id => ({ id, title: `Post ${id}` })));\n}\nfunction formatPosts(posts) {\n  return posts.map(p => p.title.toUpperCase());\n}\n\ngetUser(1)\n  .then(user => {\n    console.log('Got user:', user.name);\n    return getPosts(user.postIds);  // return Promise\n  })\n  .then(posts => {\n    console.log('Got posts:', posts.length);\n    return formatPosts(posts);  // return value\n  })\n  .then(formatted => {\n    console.log('Formatted:', formatted);\n  })\n  .catch(err => console.log('Error:', err.message));\n\n// Mix of parallel and sequential\ngetUser(1)\n  .then(user => Promise.all([\n    getPosts(user.postIds),\n    Promise.resolve({ email: `${user.name}@mail.com` })\n  ]))\n  .then(([posts, contact]) => {\n    console.log('Posts:', posts.length);\n    console.log('Contact:', contact.email);\n  });",
                List.of(
                    new Concept.ConceptExample("Error recovery in a chain",
                        "Use .catch in the middle of a chain to recover and continue.",
                        "function riskyFetch(shouldFail) {\n  return shouldFail\n    ? Promise.reject(new Error('Server error'))\n    : Promise.resolve({ data: 'success' });\n}\n\nriskyFetch(true)\n  .then(result => result.data)\n  .catch(err => {\n    console.log('Caught, using fallback:', err.message);\n    return { data: 'fallback data' };  // recovery — chain continues\n  })\n  .then(result => console.log('Result:', result.data))\n  .finally(() => console.log('Done'));",
                        "Caught, using fallback: Server error\nResult: fallback data\nDone")
                ),
                List.of(
                    "Return a Promise from .then to make the next .then wait for it",
                    "Return a plain value from .then to pass it directly to the next .then",
                    "A .catch in the middle can recover — returning a value makes the chain continue as resolved",
                    "Never nest .then inside .then — that recreates callback hell",
                    "One .catch at the end handles all errors from the entire chain"
                ),
                "When you have a series of async steps that depend on each other, chain them with .then. When you need multiple async operations in parallel within a step, use Promise.all inside a .then. This combination covers almost all async patterns.",
                List.of(
                    "Forgetting to return from .then — the next .then receives undefined",
                    "Nesting .then callbacks — flattens the benefit of Promise chaining"
                ),
                15, 11, "B"),

            // ── 12. Async & Await ─────────────────────────────────────────────
            conceptRich(js, "Async & Await",
                "async/await is syntactic sugar over Promises. async functions always return a Promise. await pauses execution until the Promise resolves, making async code look and behave like synchronous code.",
                "async/await makes asynchronous code readable.\n\nInstead of:\nfetchUser(1)\n  .then(user => fetchPosts(user.id))\n  .then(posts => display(posts))\n  .catch(err => handleError(err));\n\nYou write:\ntry {\n  const user = await fetchUser(1);\n  const posts = await fetchPosts(user.id);\n  display(posts);\n} catch (err) {\n  handleError(err);\n}\n\nThe code reads top-to-bottom like synchronous code. Same behaviour, much clearer.",
                "1. async function:\n- async keyword before function declaration or expression\n- Always returns a Promise\n- Return value is wrapped in Promise.resolve()\n- Thrown error is wrapped in Promise.reject()\n\n2. await:\n- Can only be used inside async function (or top-level module)\n- Pauses the async function until the Promise resolves\n- Resumes with the resolved value\n- If rejected, throws an error (caught by try/catch)\n\n3. Error handling:\n- Wrap await calls in try/catch\n- Or use .catch() on the async function call\n\n4. Parallel execution with await:\n- Two awaits sequentially: runs one after the other (slower)\n- await Promise.all([p1, p2]): runs in parallel (faster)\n\n5. Async iteration:\n- for await...of: iterate over async iterables\n\n6. async/await vs Promise chain:\n- Both use Promises under the hood\n- async/await is easier to read, especially for complex flows\n- Promise chains can be more readable for simple transforms",
                "// Basic async/await\nasync function fetchUser(id) {\n  return { id, name: 'Ravi', posts: [1, 2, 3] };\n}\n\nasync function fetchPost(id) {\n  return { id, title: `Post ${id}` };\n}\n\nasync function main() {\n  try {\n    const user = await fetchUser(1);\n    console.log('User:', user.name);\n\n    // Sequential — one after the other\n    const post1 = await fetchPost(user.posts[0]);\n    const post2 = await fetchPost(user.posts[1]);\n    console.log('Sequential:', post1.title, post2.title);\n\n    // Parallel — both at the same time (faster)\n    const [p1, p2] = await Promise.all([\n      fetchPost(user.posts[0]),\n      fetchPost(user.posts[1])\n    ]);\n    console.log('Parallel:', p1.title, p2.title);\n\n  } catch (err) {\n    console.log('Error:', err.message);\n  }\n}\n\nmain();\n\n// Async error propagation\nasync function risky() {\n  throw new Error('Something went wrong');\n}\n\nrisky()\n  .then(v => console.log('Value:', v))\n  .catch(err => console.log('Caught:', err.message));",
                List.of(
                    new Concept.ConceptExample("Sequential vs parallel await",
                        "See the difference in execution time between sequential and parallel awaits.",
                        "function delay(ms, value) {\n  return new Promise(resolve => setTimeout(() => resolve(value), ms));\n}\n\nasync function sequential() {\n  console.time('sequential');\n  const a = await delay(300, 'A');\n  const b = await delay(300, 'B');\n  const c = await delay(300, 'C');\n  console.timeEnd('sequential');  // ~900ms\n  console.log(a, b, c);\n}\n\nasync function parallel() {\n  console.time('parallel');\n  const [a, b, c] = await Promise.all([\n    delay(300, 'A'),\n    delay(300, 'B'),\n    delay(300, 'C')\n  ]);\n  console.timeEnd('parallel');  // ~300ms\n  console.log(a, b, c);\n}\n\nsequential().then(() => parallel());",
                        "sequential: ~900ms\nA B C\nparallel: ~300ms\nA B C")
                ),
                List.of(
                    "async function always returns a Promise — even if you return a plain value",
                    "await can only be used inside an async function",
                    "Multiple independent awaits should use Promise.all — sequential awaits are slower",
                    "try/catch with await is the clean way to handle async errors",
                    "Forgetting await is a common bug — the code continues without waiting for the result"
                ),
                "When you have multiple independent async operations, always use Promise.all instead of separate awaits. Three separate await calls that each take 300ms run in 900ms. Promise.all runs them in parallel in 300ms.",
                List.of(
                    "Forgetting await — the code continues with a Promise object, not the resolved value",
                    "Using sequential awaits for independent operations — use Promise.all for parallel execution"
                ),
                18, 12, "B"),

            // ── 13. Fetch API ─────────────────────────────────────────────────
            conceptRich(js, "Fetch API (GET, POST, PUT, DELETE)",
                "The Fetch API is the modern browser API for making HTTP requests. It returns Promises and replaces the older XMLHttpRequest.",
                "Fetch is how JavaScript communicates with servers.\n\nEvery time you see a web page that loads data without refreshing — a live search, an infinite scroll, a real-time update — that is JavaScript using Fetch (or a similar API) to talk to a server in the background.\n\nFetch returns a Promise. The first Promise resolves when the response headers arrive (not the body). You need a second await for response.json() to get the actual data.",
                "1. Basic GET request:\n- fetch(url) returns a Promise<Response>\n- response.ok: boolean — true for 2xx status codes\n- response.json(): returns Promise<parsed body>\n- response.text(): returns Promise<string>\n\n2. Response status check:\n- fetch does not reject on HTTP errors (404, 500)\n- Only rejects on network failure\n- Always check response.ok manually\n\n3. POST/PUT/DELETE requests:\n- Pass options object: { method, headers, body }\n- body must be a string — use JSON.stringify() for objects\n- Content-Type header tells server what format the body is\n\n4. Request options:\n- method: 'GET', 'POST', 'PUT', 'DELETE', 'PATCH'\n- headers: object of request headers\n- body: request body (string, FormData, Blob)\n- credentials: 'include' to send cookies\n- signal: AbortController signal for cancellation\n\n5. Cancelling requests:\n- AbortController + AbortSignal\n- controller.abort() cancels the request\n\n6. Error handling:\n- Network error: fetch rejects\n- HTTP error (4xx, 5xx): check response.ok, throw manually",
                "const BASE_URL = 'https://jsonplaceholder.typicode.com';\n\n// GET\nasync function getUser(id) {\n  const response = await fetch(`${BASE_URL}/users/${id}`);\n  if (!response.ok) throw new Error(`HTTP ${response.status}`);\n  return response.json();\n}\n\n// POST\nasync function createPost(data) {\n  const response = await fetch(`${BASE_URL}/posts`, {\n    method: 'POST',\n    headers: { 'Content-Type': 'application/json' },\n    body: JSON.stringify(data)\n  });\n  if (!response.ok) throw new Error(`HTTP ${response.status}`);\n  return response.json();\n}\n\n// PUT\nasync function updatePost(id, data) {\n  const response = await fetch(`${BASE_URL}/posts/${id}`, {\n    method: 'PUT',\n    headers: { 'Content-Type': 'application/json' },\n    body: JSON.stringify(data)\n  });\n  if (!response.ok) throw new Error(`HTTP ${response.status}`);\n  return response.json();\n}\n\n// DELETE\nasync function deletePost(id) {\n  const response = await fetch(`${BASE_URL}/posts/${id}`, {\n    method: 'DELETE'\n  });\n  return response.ok;\n}\n\n// Usage\nasync function demo() {\n  try {\n    const user = await getUser(1);\n    console.log('User:', user.name);\n\n    const post = await createPost({ title: 'Test', userId: 1 });\n    console.log('Created post id:', post.id);\n  } catch (err) {\n    console.log('Error:', err.message);\n  }\n}\ndemo();",
                List.of(
                    new Concept.ConceptExample("Fetch with AbortController — cancel a request",
                        "Cancel a slow request when the user navigates away or times out.",
                        "async function fetchWithTimeout(url, timeoutMs) {\n  const controller = new AbortController();\n  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);\n\n  try {\n    const response = await fetch(url, { signal: controller.signal });\n    clearTimeout(timeoutId);\n    if (!response.ok) throw new Error(`HTTP ${response.status}`);\n    return await response.json();\n  } catch (err) {\n    if (err.name === 'AbortError') {\n      throw new Error(`Request timed out after ${timeoutMs}ms`);\n    }\n    throw err;\n  }\n}\n\n// Usage\nfetchWithTimeout('https://jsonplaceholder.typicode.com/users/1', 5000)\n  .then(user => console.log('Got user:', user.name))\n  .catch(err => console.log('Failed:', err.message));",
                        "Got user: Leanne Graham")
                ),
                List.of(
                    "fetch only rejects on network failure — HTTP 404/500 still resolve, check response.ok",
                    "response.json() returns a Promise — you need a second await",
                    "Always set Content-Type: application/json when sending JSON body",
                    "Use AbortController to cancel requests on timeout or component unmount",
                    "Use Promise.all with multiple fetch calls to run them in parallel"
                ),
                "Always check response.ok after fetch. This is the most common Fetch API mistake — developers assume that if fetch did not throw, the request succeeded. But fetch only throws on network errors, not HTTP errors like 404 or 500.",
                List.of(
                    "Not checking response.ok — HTTP 404 does not throw, you get a 404 response",
                    "Forgetting JSON.stringify for POST body — sends [object Object] as string",
                    "Not setting Content-Type header for POST — server may not parse body correctly"
                ),
                20, 13, "B"),

            // ── 14. Debouncing and Throttling ─────────────────────────────────
            conceptRich(js, "Debouncing & Throttling",
                "Debouncing delays a function call until a pause in input. Throttling limits how often a function can run. Both control the rate of execution for performance-sensitive operations.",
                "Some events fire very frequently:\n- keyup: fires on every keystroke\n- scroll: fires hundreds of times per scroll\n- resize: fires constantly while resizing\n- mousemove: fires on every pixel of mouse movement\n\nRunning an API call, DOM update or heavy calculation on every single event would kill performance.\n\nDebouncing: wait until events stop, then run once.\nExample: search input — wait until user stops typing, then search.\n\nThrottling: run at most once every N milliseconds regardless of how many times triggered.\nExample: scroll position — update UI at most 10 times per second.",
                "1. Debounce:\n- When event fires, start a timer\n- If event fires again before timer ends, reset timer\n- Execute function only when timer completes\n- Delay: how long to wait after last event\n- Use case: search input, form validation, window resize handler\n\n2. Throttle:\n- Execute function immediately\n- Ignore all subsequent calls within the throttle period\n- After period ends, allow next call\n- Use case: scroll events, mousemove, button rapid clicks, rate limiting API calls\n\n3. Debounce vs Throttle:\n- Debounce: fires AFTER events stop (trailing edge)\n- Throttle: fires at regular intervals DURING events\n- Leading edge: fire on first call, then wait (throttle variant)\n\n4. Libraries:\n- Lodash: _.debounce, _.throttle (most used in production)\n- Both support options: leading, trailing, maxWait",
                "// Debounce implementation\nfunction debounce(fn, delay) {\n  let timerId;\n  return function(...args) {\n    clearTimeout(timerId);\n    timerId = setTimeout(() => fn.apply(this, args), delay);\n  };\n}\n\n// Throttle implementation\nfunction throttle(fn, limit) {\n  let lastRun = 0;\n  return function(...args) {\n    const now = Date.now();\n    if (now - lastRun >= limit) {\n      lastRun = now;\n      return fn.apply(this, args);\n    }\n  };\n}\n\n// Usage — search input debounce\nconst searchInput = document.querySelector('#search');\n\nconst search = debounce((query) => {\n  console.log('Searching for:', query);\n  // fetch(`/api/search?q=${query}`)\n}, 300);\n\n// searchInput.addEventListener('input', e => search(e.target.value));\n// Only fires 300ms after user stops typing\n\n// Usage — scroll throttle\nconst handleScroll = throttle(() => {\n  console.log('Scroll position:', window.scrollY);\n}, 100);\n// window.addEventListener('scroll', handleScroll);\n// Fires at most once every 100ms",
                List.of(
                    new Concept.ConceptExample("Debounce for live search",
                        "Implement debounced search that waits for the user to stop typing.",
                        "function debounce(fn, delay) {\n  let timer;\n  return (...args) => {\n    clearTimeout(timer);\n    timer = setTimeout(() => fn(...args), delay);\n  };\n}\n\nlet apiCallCount = 0;\n\nfunction searchAPI(query) {\n  apiCallCount++;\n  console.log(`API call #${apiCallCount}: searching '${query}'`);\n}\n\nconst debouncedSearch = debounce(searchAPI, 300);\n\n// Simulate rapid typing: 'r', 'ra', 'rav', 'ravi'\n// Without debounce: 4 API calls\n// With debounce: only 1 call (after typing stops)\n['r', 'ra', 'rav', 'ravi'].forEach((text, i) => {\n  setTimeout(() => debouncedSearch(text), i * 50);\n});\n// 50ms between each — all within 300ms window\n// Only last call 'ravi' fires after 300ms",
                        "API call #1: searching 'ravi'")
                ),
                List.of(
                    "Debounce: waits for events to stop, then fires once — ideal for search and validation",
                    "Throttle: fires at most once per interval — ideal for scroll, resize, mousemove",
                    "Both return a new function — store it and reuse (do not call debounce inside an event handler)",
                    "Lodash debounce/throttle are production-ready — use them instead of writing your own"
                ),
                "Always store the debounced/throttled function outside the event handler setup. If you call debounce() inside addEventListener, you create a new debounce function on every event — the timer resets every time and debounce never actually delays.",
                List.of(
                    "Creating a new debounce/throttle function inside the event handler — defeats the purpose",
                    "Using debounce for scroll — use throttle instead, debounce fires only after scrolling stops"
                ),
                18, 14, "C"),

            // ── 15. ES Modules ────────────────────────────────────────────────
            conceptRich(js, "ES Modules (import / export)",
                "ES Modules are the official JavaScript module system. export makes values available to other files. import brings them in. Modules enable clean code organisation and dependency management.",
                "As your JavaScript grows beyond one file, you need a way to organise code into separate files and share code between them.\n\nES Modules (ESM) are the official standard:\n- export: expose values from a file\n- import: bring values in from another file\n\nEvery modern JavaScript project — React, Vue, Node.js, Deno — uses ES Modules. They replaced the older CommonJS (require/module.exports) system and the informal IIFE pattern.\n\nModules are loaded once and cached — importing the same module twice does not run it twice.",
                "1. Named exports:\n- export const name = value; or export function fn() {}\n- Or export { name1, name2 } at bottom of file\n- Import with same name: import { name1 } from './file.js'\n- Import all: import * as module from './file.js'\n\n2. Default export:\n- export default value — one per file\n- Import with any name: import anything from './file.js'\n- Common for classes and main component\n\n3. Re-exports:\n- export { name } from './other.js' — pass-through\n- export * from './other.js' — re-export all\n- Used in index.js barrel files\n\n4. Module features:\n- Strict mode by default\n- Top-level this is undefined\n- Loaded asynchronously (deferred)\n- Cached — same module only executes once\n- In HTML: <script type=\"module\" src=\"app.js\">\n\n5. Dynamic imports:\n- import('./module.js').then(module => ...)\n- Lazy loading — load module only when needed\n- Returns a Promise",
                "// math.js — named exports\nexport const PI = 3.14159;\n\nexport function add(a, b) { return a + b; }\nexport function multiply(a, b) { return a * b; }\n\n// utils.js — default export\nexport default class Logger {\n  log(msg) { console.log(`[LOG] ${msg}`); }\n}\n\n// user.js — mixed exports\nexport const roles = ['admin', 'student', 'guest'];\n\nclass User {\n  constructor(name, role) {\n    this.name = name;\n    this.role = role;\n  }\n}\nexport default User;\n\n// app.js — importing\nimport Logger from './utils.js';          // default\nimport { PI, add, multiply } from './math.js'; // named\nimport { roles } from './user.js';\nimport User from './user.js';              // default\nimport * as math from './math.js';         // namespace\n\nconst logger = new Logger();\nlogger.log(add(2, 3));         // [LOG] 5\nlogger.log(math.multiply(4, 5)); // [LOG] 20\n\n// Dynamic import — lazy loading\nconst loadChart = async () => {\n  const { Chart } = await import('./chart.js');\n  new Chart();\n};",
                List.of(
                    new Concept.ConceptExample("Barrel file pattern with re-exports",
                        "Use an index.js to create a clean public API for a folder.",
                        "// services/auth.js\nexport function login(user, pass) {\n  return Promise.resolve({ token: 'abc123' });\n}\nexport function logout() {\n  return Promise.resolve();\n}\n\n// services/user.js\nexport function getProfile(id) {\n  return Promise.resolve({ id, name: 'Ravi' });\n}\n\n// services/index.js — barrel file\nexport { login, logout } from './auth.js';\nexport { getProfile } from './user.js';\n\n// app.js — clean single import\nimport { login, getProfile } from './services/index.js';\n\nasync function main() {\n  const { token } = await login('ravi', 'pass');\n  console.log('Token:', token);\n  const profile = await getProfile(1);\n  console.log('Profile:', profile.name);\n}\nmain();",
                        "Token: abc123\nProfile: Ravi")
                ),
                List.of(
                    "Named exports use { }, default exports do not — import { fn } vs import fn",
                    "One file can have many named exports but only ONE default export",
                    "Add type=\"module\" to script tag in HTML: <script type=\"module\" src=\"app.js\">",
                    "Modules are strict mode by default — no accidental globals",
                    "Dynamic import() loads a module lazily and returns a Promise"
                ),
                "Use barrel files (index.js) to create a clean API for folders. Instead of importing from deep paths everywhere, import everything from the index: import { login, getProfile } from './services'. This makes refactoring much easier.",
                List.of(
                    "Forgetting type=\"module\" on the script tag — modules will not work",
                    "Trying to use import outside a module context — use .mjs extension or type=\"module\"",
                    "Circular imports — A imports B imports A — can cause undefined values at runtime"
                ),
                18, 15, "B"),

            // ── 16. Optional Chaining and Nullish Coalescing ──────────────────
            conceptRich(js, "Optional Chaining (?.) and Nullish Coalescing (??)",
                "Optional chaining (?.) safely accesses nested properties without throwing if an intermediate value is null/undefined. Nullish coalescing (??) provides a default only when the value is null or undefined.",
                "Accessing deeply nested data is a constant task in JavaScript.\n\nWithout optional chaining:\nconst city = user && user.address && user.address.city;\n\nWith optional chaining:\nconst city = user?.address?.city;\n\nIf any part of the chain is null or undefined, the expression returns undefined instead of throwing TypeError.\n\nNullish coalescing (??) provides a default value only when the value is specifically null or undefined — unlike || which also triggers on 0, false, or empty string.",
                "1. Optional chaining (?.):\n- obj?.prop: access property safely\n- obj?.method(): call method safely\n- arr?.[index]: access array index safely\n- Returns undefined if anything in chain is null/undefined\n- Does not mask actual errors — only handles null/undefined\n\n2. Nullish coalescing (??):\n- value ?? defaultValue\n- Returns defaultValue only if value is null or undefined\n- Unlike ||: 0 ?? 'default' → 0 (not 'default')\n- Unlike ||: '' ?? 'default' → '' (not 'default')\n- Unlike ||: false ?? 'default' → false (not 'default')\n\n3. Combined usage:\n- user?.profile?.score ?? 0\n- Falls through chain safely, provides default at end\n\n4. Nullish assignment (??=):\n- x ??= value: assign value only if x is null/undefined\n\n5. Optional chaining with functions:\n- callback?.() — call only if callback is defined\n- Useful for optional event handlers and callbacks",
                "const user = {\n  name: 'Ravi',\n  address: {\n    city: 'Hyderabad',\n    zip: '500001'\n  },\n  getScore: () => 85\n};\n\n// Optional chaining\nconsole.log(user?.name);             // 'Ravi'\nconsole.log(user?.address?.city);    // 'Hyderabad'\nconsole.log(user?.phone?.number);    // undefined (no error!)\nconsole.log(user?.getScore?.());     // 85\nconsole.log(user?.getEmail?.());     // undefined\n\n// Optional chaining with arrays\nconst data = { items: ['a', 'b', 'c'] };\nconsole.log(data?.items?.[0]);   // 'a'\nconsole.log(data?.tags?.[0]);    // undefined\n\n// Nullish coalescing\nconsole.log(null ?? 'default');   // 'default'\nconsole.log(undefined ?? 'default'); // 'default'\nconsole.log(0 ?? 'default');      // 0 (not 'default'!)\nconsole.log('' ?? 'default');     // ''\nconsole.log(false ?? 'default');  // false\n\n// Combined\nconst score = user?.stats?.score ?? 0;\nconsole.log(score);  // 0 (safe default)\n\n// Nullish assignment\nlet config = {};\nconfig.theme ??= 'dark';   // assign only if null/undefined\nconsole.log(config.theme); // 'dark'",
                List.of(
                    new Concept.ConceptExample("Safe API response processing",
                        "Use ?. and ?? to safely process deeply nested API data.",
                        "// API response might have missing fields\nconst response = {\n  user: {\n    name: 'Ravi',\n    subscription: null,  // no subscription\n    preferences: {}\n  }\n};\n\nconst name = response?.user?.name ?? 'Anonymous';\nconst plan = response?.user?.subscription?.plan ?? 'Free';\nconst theme = response?.user?.preferences?.theme ?? 'dark';\nconst score = response?.user?.stats?.score ?? 0;\n\nconsole.log(`Name: ${name}`);\nconsole.log(`Plan: ${plan}`);\nconsole.log(`Theme: ${theme}`);\nconsole.log(`Score: ${score}`);",
                        "Name: Ravi\nPlan: Free\nTheme: dark\nScore: 0")
                ),
                List.of(
                    "?. returns undefined if chain breaks — does not throw TypeError",
                    "?? provides default only for null/undefined — 0, '', false are NOT replaced",
                    "|| provides default for any falsy value — 0, '', false, null, undefined",
                    "Combine: obj?.deeply?.nested?.value ?? 'default' — safe access with fallback",
                    "callback?.() safely calls a function only if it exists"
                ),
                "Prefer ?? over || when providing default values for numeric or boolean fields. score || 0 would incorrectly treat score=0 as missing. score ?? 0 correctly handles it — only providing 0 when score is specifically null or undefined.",
                List.of(
                    "Using || instead of ?? for numeric defaults — 0 || 'N/A' gives 'N/A' which is wrong",
                    "Using ?. to mask bugs — optional chaining is for genuinely optional values, not to hide errors"
                ),
                15, 16, "C"),

            // ── 17. Web Storage API ───────────────────────────────────────────
            conceptRich(js, "Web Storage API (localStorage & sessionStorage)",
                "localStorage stores data in the browser permanently. sessionStorage stores data for one browser session. Both store key-value pairs as strings and are accessible only in the browser.",
                "Web Storage lets JavaScript save data in the browser without a server.\n\nlocalStorage:\n- Persists until explicitly cleared\n- Survives page refresh, tab close, browser restart\n- Shared across all tabs of the same origin\n- Common uses: user preferences, theme, language, remember me\n\nsessionStorage:\n- Cleared when the browser tab is closed\n- Not shared between tabs\n- Common uses: form state, one-session data, multi-step flow\n\nBoth have the same API and only store strings — use JSON.stringify/parse for objects and arrays.",
                "1. localStorage API:\n- localStorage.setItem(key, value)\n- localStorage.getItem(key) — returns string or null\n- localStorage.removeItem(key)\n- localStorage.clear() — remove all\n- localStorage.length, localStorage.key(index)\n\n2. sessionStorage:\n- Same API as localStorage\n- Data cleared when tab closes\n\n3. Storing objects:\n- Must stringify: localStorage.setItem('user', JSON.stringify(user))\n- Must parse: JSON.parse(localStorage.getItem('user'))\n\n4. Storage limits:\n- Typically 5-10MB per origin\n- Throws QuotaExceededError if limit reached\n\n5. Security considerations:\n- Never store sensitive data (passwords, tokens) in localStorage — XSS attacks can read it\n- sessionStorage is slightly safer but still readable by JS\n- Use HttpOnly cookies for authentication tokens\n\n6. storage event:\n- Fires in OTHER tabs when localStorage changes\n- Use for cross-tab communication",
                "// localStorage — persists between sessions\nlocalStorage.setItem('theme', 'dark');\nlocalStorage.setItem('lang', 'en');\n\nconsole.log(localStorage.getItem('theme'));  // 'dark'\nconsole.log(localStorage.getItem('missing')); // null\n\n// Storing objects\nconst user = { name: 'Ravi', role: 'student', score: 850 };\nlocalStorage.setItem('user', JSON.stringify(user));\n\nconst saved = JSON.parse(localStorage.getItem('user'));\nconsole.log(saved?.name);  // 'Ravi'\n\n// Safe getter helper\nfunction getStorageItem(key, defaultValue = null) {\n  try {\n    const item = localStorage.getItem(key);\n    return item ? JSON.parse(item) : defaultValue;\n  } catch {\n    return defaultValue;\n  }\n}\n\n// sessionStorage — cleared on tab close\nsessionStorage.setItem('step', '2');\nsessionStorage.setItem('formData', JSON.stringify({ name: 'Ravi' }));\n\nconsole.log(sessionStorage.getItem('step'));  // '2'\n\n// Remove and clear\nlocalStorage.removeItem('lang');\n// localStorage.clear();  // removes everything",
                List.of(
                    new Concept.ConceptExample("Theme preference with localStorage",
                        "Save and restore user's theme preference across visits.",
                        "// Save theme preference\nfunction setTheme(theme) {\n  document.documentElement.setAttribute('data-theme', theme);\n  localStorage.setItem('theme', theme);\n  console.log('Theme set to:', theme);\n}\n\n// Restore on page load\nfunction initTheme() {\n  const saved = localStorage.getItem('theme') || 'dark';\n  document.documentElement.setAttribute('data-theme', saved);\n  console.log('Restored theme:', saved);\n}\n\ninitTheme();\n\n// Simulate toggle\nconst current = localStorage.getItem('theme') || 'dark';\nsetTheme(current === 'dark' ? 'light' : 'dark');\n\nconsole.log('Stored:', localStorage.getItem('theme'));",
                        "Restored theme: dark\nTheme set to: light\nStored: light")
                ),
                List.of(
                    "localStorage persists forever; sessionStorage clears when the tab closes",
                    "Both only store strings — always JSON.stringify before storing objects",
                    "JSON.parse returns null gracefully if stored value is null or invalid JSON",
                    "Never store passwords or auth tokens in localStorage — XSS can steal them",
                    "5MB limit per origin — do not store large data, use IndexedDB for that"
                ),
                "Wrap localStorage access in a try/catch. Private browsing in some browsers throws an error even for getItem. A safe helper function that catches errors and returns a default value prevents crashes in these environments.",
                List.of(
                    "Storing objects without JSON.stringify — stores '[object Object]'",
                    "Not checking for null from getItem before JSON.parse — JSON.parse(null) throws",
                    "Storing sensitive data like JWT tokens in localStorage — use HttpOnly cookies instead"
                ),
                15, 17, "C"),

            // ── 18. Cookies ───────────────────────────────────────────────────
            conceptRich(js, "Cookies (HTTP & Client-Side)",
                "Cookies store small pieces of data in the browser. They are sent automatically with every HTTP request to the server. HttpOnly cookies cannot be read by JavaScript — making them safer for authentication.",
                "Cookies have been part of the web since 1994. Unlike localStorage, cookies are:\n- Sent automatically with every HTTP request to the matching domain\n- Can have an expiry date\n- Can be scoped to a path or domain\n- Can be marked HttpOnly (no JS access) for security\n\nThis makes cookies the standard choice for session tokens and authentication:\n- Server sets a cookie with the auth token\n- Browser automatically sends it with every request\n- Server validates it without JavaScript doing anything\n\nJavaScript can also set and read cookies, but HttpOnly cookies (set by the server) are invisible to JavaScript.",
                "1. Reading cookies (JavaScript):\n- document.cookie: string of all accessible cookies\n- Format: 'key1=value1; key2=value2'\n- Must parse manually — there is no getCookie() built-in\n\n2. Setting cookies (JavaScript):\n- document.cookie = 'key=value; expires=date; path=/'\n- Each assignment adds or updates ONE cookie\n- Assigning to document.cookie does not replace all cookies\n\n3. Cookie attributes:\n- expires: Date string — when cookie expires\n- max-age: seconds until expiry\n- path: which paths send the cookie (default: current path)\n- domain: which domains receive the cookie\n- secure: HTTPS only\n- HttpOnly: cannot be read by JavaScript (server-set only)\n- SameSite: Strict/Lax/None — CSRF protection\n\n4. HttpOnly cookies:\n- Set by server in Set-Cookie response header\n- Invisible to document.cookie\n- Safe from XSS attacks\n- Best practice for auth tokens\n\n5. Cookie vs localStorage:\n- Cookie: sent with requests, server can read, expiry, HttpOnly possible\n- localStorage: JS only, larger storage, not sent to server",
                "// Reading cookies\nconsole.log(document.cookie);  // 'theme=dark; lang=en; ...'\n\n// Helper to get a specific cookie\nfunction getCookie(name) {\n  const cookies = document.cookie.split('; ');\n  const found = cookies.find(c => c.startsWith(name + '='));\n  return found ? decodeURIComponent(found.split('=')[1]) : null;\n}\n\n// Setting a cookie\nfunction setCookie(name, value, days) {\n  const expires = new Date();\n  expires.setDate(expires.getDate() + days);\n  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires.toUTCString()}; path=/; SameSite=Lax`;\n}\n\n// Deleting a cookie (set expiry in past)\nfunction deleteCookie(name) {\n  document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;\n}\n\n// Usage\nsetCookie('theme', 'dark', 30);   // expires in 30 days\nsetCookie('lang', 'en', 365);\nconsole.log(getCookie('theme'));  // 'dark'\ndeleteCookie('lang');\nconsole.log(getCookie('lang'));   // null",
                List.of(
                    new Concept.ConceptExample("Cookie vs localStorage comparison",
                        "Understand when to use each storage mechanism.",
                        "// Use localStorage for:\n// - User preferences (theme, language)\n// - Non-sensitive UI state\n// - Data that should not go to server\n\nlocalStorage.setItem('theme', 'dark');\nlocalStorage.setItem('sidebar-open', 'true');\n\n// Use cookies for:\n// - Authentication tokens (HttpOnly)\n// - Session identifiers\n// - Data that server needs to read\n\n// Server would set HttpOnly cookie like:\n// Set-Cookie: session=abc123; HttpOnly; Secure; SameSite=Strict\n\n// Client-side cookie for non-sensitive preference\ndocument.cookie = 'currency=INR; path=/; SameSite=Lax; max-age=86400';\n\n// Cookie is sent with every request to server\n// localStorage is never sent automatically\nconsole.log('localStorage theme:', localStorage.getItem('theme'));\nconsole.log('Cookies accessible:', document.cookie);",
                        "localStorage theme: dark\nCookies accessible: currency=INR")
                ),
                List.of(
                    "Cookies are sent automatically with every HTTP request — localStorage is not",
                    "HttpOnly cookies are invisible to JavaScript — they cannot be stolen by XSS",
                    "Always set SameSite=Lax or Strict to protect against CSRF attacks",
                    "document.cookie assignments add/update one cookie — they do not replace all cookies",
                    "Use cookies for auth tokens; use localStorage for UI preferences"
                ),
                "Never store authentication tokens in localStorage. If your site has any XSS vulnerability, an attacker can read all localStorage data. Use HttpOnly cookies for auth — they are inaccessible to JavaScript even if an XSS attack runs.",
                List.of(
                    "Storing JWT tokens in localStorage — use HttpOnly cookies instead",
                    "Thinking document.cookie = 'a=1' replaces all cookies — it only sets/updates one"
                ),
                15, 18, "C"),

            // ── 19. Polyfills & Transpilation ─────────────────────────────────
            conceptRich(js, "Polyfills & Transpilation",
                "Polyfills add missing JavaScript features to older browsers. Transpilation converts modern JavaScript syntax into an older syntax that all browsers understand. Together they allow you to write modern JS that works everywhere.",
                "Modern JavaScript features like optional chaining, nullish coalescing, async/await and arrow functions are not supported in very old browsers.\n\nTwo solutions:\n\nPolyfill: JavaScript code that implements a missing feature.\n- Example: adding Array.prototype.includes to browsers that do not have it\n- The code runs and adds the method before your app code runs\n\nTranspilation: converting new syntax to old syntax.\n- Babel converts arrow functions to regular functions\n- It converts async/await to Promise chains\n- It converts optional chaining to if/else checks\n- The output code is equivalent but uses older syntax\n\nBabel + bundlers (webpack, Vite) handle this automatically in most modern projects.",
                "1. Why needed:\n- Internet Explorer 11 does not support most ES6+\n- Some mobile browsers lag behind\n- Corporate environments often run old browsers\n\n2. Polyfills:\n- core-js: most comprehensive polyfill library\n- Polyfills for: Promise, fetch, Array methods, Object methods, etc.\n- Can target specific browsers with browserslist\n\n3. Babel:\n- Transpiler: converts modern JS to ES5\n- @babel/preset-env: automatically decides what to transpile based on target browsers\n- Integrates with webpack, Vite, Rollup\n- babel.config.js or .babelrc configuration\n\n4. TypeScript:\n- Also transpiles — TypeScript compiler outputs JavaScript\n- Adds type checking on top of transpilation\n\n5. Bundlers:\n- Webpack, Vite, Rollup, Parcel\n- Bundle multiple files into one\n- Run transpilation and polyfills\n- Tree shaking: remove unused code\n\n6. Modern approach:\n- Most frameworks (React, Vue, Next.js) set up Babel automatically\n- Vite uses esbuild for fast transpilation\n- browserslist in package.json targets which browsers to support",
                "// Modern JavaScript code you write\nconst getUser = async (id) => {\n  const user = await fetch(`/api/users/${id}`).then(r => r.json());\n  return user?.name ?? 'Unknown';\n};\n\n// What Babel might output for older browsers\n// (simplified)\n\"use strict\";\nfunction _asyncToGenerator(fn) { /* ... */ }\nvar getUser = function() {\n  return _asyncToGenerator(function* (id) {\n    var user = yield fetch('/api/users/' + id).then(function(r) { return r.json(); });\n    return (user === null || user === void 0 ? void 0 : user.name) !== null &&\n           (user === null || user === void 0 ? void 0 : user.name) !== undefined\n           ? user.name : 'Unknown';\n  });\n}();\n\n// package.json — target browsers\n// { \"browserslist\": [\"last 2 versions\", \"not dead\"] }\n\n// Check if a feature is available\nconst hasOptionalChaining = (() => {\n  try {\n    eval('null?.x');\n    return true;\n  } catch { return false; }\n})();\nconsole.log('Optional chaining supported:', hasOptionalChaining);\n\n// Manual polyfill example\nif (!Array.prototype.at) {\n  Array.prototype.at = function(index) {\n    const i = index < 0 ? this.length + index : index;\n    return this[i];\n  };\n}\nconsole.log([1,2,3].at(-1));  // 3",
                List.of(
                    new Concept.ConceptExample("Feature detection before polyfilling",
                        "Check if a feature exists before adding a polyfill.",
                        "// Feature detection pattern\nconst features = {\n  promise: typeof Promise !== 'undefined',\n  fetch: typeof fetch !== 'undefined',\n  arrayAt: typeof Array.prototype.at === 'function',\n  optionalChaining: (() => { try { eval('null?.x'); return true; } catch { return false; } })()\n};\n\nObject.entries(features).forEach(([feature, supported]) => {\n  console.log(`${feature}: ${supported ? '✓ supported' : '✗ needs polyfill'}`);\n});\n\n// Polyfill only if needed\nif (!Array.prototype.at) {\n  console.log('Adding Array.at polyfill');\n  Array.prototype.at = function(n) {\n    return this[n < 0 ? this.length + n : n];\n  };\n}",
                        "promise: ✓ supported\nfetch: ✓ supported\narrayAt: ✓ supported\noptionalChaining: ✓ supported")
                ),
                List.of(
                    "Polyfills add missing API features in JavaScript; transpilation converts new syntax to old",
                    "Babel transpiles syntax (arrow functions, async/await); polyfills add methods (Promise, fetch)",
                    "core-js is the standard polyfill library — includes polyfills for almost all ES6+ features",
                    "Modern frameworks (React, Vue) configure Babel automatically — you rarely set it up manually",
                    "browserslist in package.json defines which browsers to target — determines what gets transpiled"
                ),
                "In modern projects, you rarely need to manually configure polyfills or Babel — frameworks like Create React App, Next.js and Vite handle it. Focus on understanding WHAT transpilation does conceptually, and use framework defaults for configuration.",
                List.of(
                    "Manually polyfilling features that are already in your bundle's polyfill library — double polyfilling",
                    "Targeting too many old browsers — increases bundle size significantly"
                ),
                15, 19, "C")
        );

        conceptRepository.saveAll(concepts);
        js.setTotalConcepts(concepts.size());
        subjectRepository.save(js);
        System.out.println("✅ JavaScript Advanced seeded — " + concepts.size() + " concepts");
    }

    // ─── JAVASCRIPT BASICS ───────────────────────────────────────────────────
    private void seedJavaScriptBasics() {
        Subject js = subjectRepository.save(sub(
            "JavaScript Basics",
            "Master variables, operators, loops, functions, scope, arrays, objects, DOM and events — the complete JavaScript foundation",
            "🟨", "#F7DF1E", "B"
        ));
        js.setOverview("JavaScript Basics covers everything you need to write real, interactive web programs. Starting from variables and data types, you will learn operators, loops, functions, scope, hoisting, arrays, objects, DOM manipulation and events. This subject is carefully ordered so each concept builds on the previous one.");
        js.setWhyLearn("JavaScript is the only programming language that runs in the browser. Every interactive website — forms, animations, popups, real-time updates — is powered by JavaScript. It is the most used language on GitHub and is required for every frontend and full-stack developer role.");
        js.setForWho("Students who understand basic programming concepts (variables, conditions, loops in any language) or have completed HTML and CSS fundamentals.");
        js.setPrerequisites(List.of("HTML Fundamentals (recommended)", "CSS Fundamentals (recommended)", "Any basic programming knowledge"));
        js.setOutcomes(List.of(
            "Declare variables correctly using var, let and const",
            "Use all JavaScript operators including the ternary operator",
            "Understand type coercion and the difference between == and ===",
            "Write for, while and for...of loops",
            "Define and call functions including arrow functions",
            "Understand scope, hoisting and the temporal dead zone",
            "Manipulate arrays and strings using built-in methods",
            "Work with objects and their methods",
            "Select and modify DOM elements",
            "Handle user events with addEventListener"
        ));
        js.setWhatYouWillBuild(List.of(
            "An interactive quiz app using DOM and events",
            "A to-do list with add, delete and filter using arrays and DOM",
            "A form validator using string methods and events"
        ));
        js.setToolsRequired(List.of("Any modern browser (Chrome recommended)", "VS Code", "Browser DevTools console"));
        js.setDifficulty("Beginner");
        js.setEstimatedHours(14);
        js.setCareerUse("Required for every frontend developer, full-stack developer and web developer role. JavaScript fundamentals are tested in every frontend interview — arrays, scope, hoisting, closures and DOM manipulation are the most common topics.");
        subjectRepository.save(js);

        List<Concept> concepts = List.of(

            // ── 1. Variables and Data Types ───────────────────────────────────
            conceptRich(js, "Variables and Data Types",
                "Variables store data in JavaScript. var, let and const are the three ways to declare them. JavaScript has 8 data types: string, number, boolean, null, undefined, symbol, bigint and object.",
                "A variable is a named box that holds a value.\n\nIn JavaScript you have three ways to create a variable:\n- var: the old way — has quirky scoping rules, mostly avoided today\n- let: the modern way for values that will change\n- const: for values that will not be reassigned\n\nJavaScript has 7 primitive types and 1 complex type:\n- Primitive: string, number, boolean, null, undefined, symbol, bigint\n- Complex: object (includes arrays, functions, objects)\n\nThe typeof operator tells you what type a value is.",
                "1. var, let, const:\n- var: function-scoped, hoisted, can be re-declared — avoid\n- let: block-scoped, not re-declarable, can be reassigned\n- const: block-scoped, not re-declarable, not reassignable\n- const does not mean immutable — object/array contents can still change\n\n2. Primitive types:\n- string: text in quotes — 'hello', \"world\", `template`\n- number: integers and floats — 42, 3.14, NaN, Infinity\n- boolean: true or false only\n- null: intentional absence of value — assigned by developer\n- undefined: variable declared but not assigned\n- symbol: unique identifier (ES6)\n- bigint: integers larger than Number.MAX_SAFE_INTEGER\n\n3. Object type:\n- object: key-value pairs — {name: 'Ravi'}\n- array: ordered list — [1, 2, 3] (typeof array is 'object')\n- function: callable object (typeof function is 'function')\n\n4. typeof operator:\n- typeof 42 → 'number'\n- typeof null → 'object' (historical bug in JS)\n- typeof undefined → 'undefined'",
                "// var, let, const\nvar name = 'Ravi';       // old way\nlet age = 21;            // can be reassigned\nconst PI = 3.14159;      // cannot be reassigned\n\nage = 22;     // OK\n// PI = 3;    // TypeError\n\n// Data types\nlet str    = 'Hello JavaScript';\nlet num    = 42;\nlet float  = 3.14;\nlet bool   = true;\nlet empty  = null;\nlet undef  = undefined;\nlet big    = 9007199254740993n;  // bigint\n\n// typeof\nconsole.log(typeof str);    // string\nconsole.log(typeof num);    // number\nconsole.log(typeof bool);   // boolean\nconsole.log(typeof null);   // object (JS bug!)\nconsole.log(typeof undef);  // undefined\nconsole.log(typeof {});     // object\nconsole.log(typeof []);     // object\nconsole.log(typeof function(){}); // function\n\n// const with objects — contents can change\nconst user = { name: 'Ravi' };\nuser.name = 'Priya';   // OK — object contents changed\n// user = {};           // TypeError — cannot reassign const",
                List.of(
                    new Concept.ConceptExample("var vs let — the difference",
                        "var is function-scoped, let is block-scoped — a critical difference.",
                        "// var leaks out of blocks\nif (true) {\n  var x = 10;\n  let y = 20;\n}\nconsole.log(x);  // 10 — var leaked out!\n// console.log(y);  // ReferenceError — let stays inside block\n\n// var allows re-declaration, let does not\nvar a = 1;\nvar a = 2;  // no error\n\nlet b = 1;\n// let b = 2;  // SyntaxError: already declared",
                        "10\nReferenceError: y is not defined"),
                    new Concept.ConceptExample("Checking types with typeof",
                        "Use typeof to inspect the type of any value.",
                        "const values = [42, 'hello', true, null, undefined, {}, [], function(){}];\n\nvalues.forEach(v => {\n  console.log(`${String(v).padEnd(15)} -> ${typeof v}`);\n});",
                        "42              -> number\nhello           -> string\ntrue            -> boolean\nnull            -> object\nundefined       -> undefined\n[object Object] -> object\n                -> object\nfunction(){}    -> function")
                ),
                List.of(
                    "Use const by default, let when you need to reassign, never var",
                    "null is intentionally empty (you set it), undefined means not yet assigned",
                    "typeof null returns 'object' — this is a known JavaScript bug, not a feature",
                    "Arrays and functions are objects in JavaScript — typeof [] is 'object'",
                    "const prevents reassignment, not mutation — const arrays and objects can still be changed"
                ),
                "Default to const for every variable. Switch to let only when you know the value needs to change. This prevents accidental reassignment bugs and makes your code intent clear.",
                List.of(
                    "Using var — it has function scope and hoisting behaviour that causes confusing bugs",
                    "Confusing null (intentional empty) with undefined (not assigned)",
                    "Thinking const makes objects immutable — const only prevents reassignment of the variable"
                ),
                18, 1, "D"),

            // ── 2. Operators ──────────────────────────────────────────────────
            conceptRich(js, "Operators",
                "Operators perform actions on values. JavaScript has arithmetic, comparison, logical, assignment, and ternary operators.",
                "Operators are the tools that let you work with values.\n\nJavaScript operators:\n- Arithmetic: +, -, *, /, %, ** — do maths\n- Comparison: ==, ===, !=, !==, <, >, <=, >= — compare values, return boolean\n- Logical: &&, ||, ! — combine or invert conditions\n- Assignment: =, +=, -=, *=, /= — store values\n- Ternary: condition ? ifTrue : ifFalse — shorthand if/else\n- Nullish coalescing: ?? — use right side only if left is null/undefined\n- Optional chaining: ?. — safely access nested properties",
                "1. Arithmetic operators:\n- +, -, *, /: basic maths\n- %: modulo (remainder) — useful for even/odd checks\n- **: exponentiation — 2**10 is 1024\n- + with strings: concatenation — 'hello' + ' world'\n- + with number and string: coercion — 5 + '3' is '53'\n\n2. Comparison operators:\n- == checks value with type coercion: '5' == 5 is true\n- === checks value AND type: '5' === 5 is false (strict)\n- Always prefer === over ==\n\n3. Logical operators:\n- && (AND): returns first falsy value or last value\n- || (OR): returns first truthy value or last value\n- ! (NOT): flips boolean\n- Short-circuit: a && b — if a is falsy, b is never evaluated\n\n4. Ternary operator:\n- condition ? valueIfTrue : valueIfFalse\n- One-line if/else for simple cases\n\n5. Nullish coalescing (??):\n- a ?? b — returns b only if a is null or undefined\n- Unlike ||, it does not trigger on 0, '', or false",
                "// Arithmetic\nconsole.log(10 + 3);   // 13\nconsole.log(10 % 3);   // 1 (remainder)\nconsole.log(2 ** 8);   // 256\nconsole.log('5' + 3);  // '53' (string concat!)\nconsole.log('5' - 3);  // 2 (coercion to number)\n\n// Comparison\nconsole.log(5 == '5');   // true  (loose)\nconsole.log(5 === '5');  // false (strict)\nconsole.log(5 !== '5');  // true\n\n// Logical\nconsole.log(true && false);  // false\nconsole.log(true || false);  // true\nconsole.log(!true);          // false\n\n// Short-circuit\nconst user = null;\nconst name = user && user.name;  // null (safe)\nconst display = name || 'Guest'; // 'Guest'\n\n// Ternary\nconst age = 20;\nconst status = age >= 18 ? 'Adult' : 'Minor';\nconsole.log(status);  // Adult\n\n// Nullish coalescing\nconst score = 0;\nconsole.log(score || 'No score');  // 'No score' (wrong!)\nconsole.log(score ?? 'No score');  // 0 (correct)",
                List.of(
                    new Concept.ConceptExample("Practical operator use",
                        "Using operators in real scenarios.",
                        "// Calculate discount\nconst price = 1000;\nconst discount = 15;\nconst final = price - (price * discount / 100);\nconsole.log('Final price:', final);\n\n// Check even/odd\nconst num = 17;\nconsole.log(num % 2 === 0 ? 'Even' : 'Odd');\n\n// Default value with ??\nfunction greet(name) {\n  const displayName = name ?? 'Guest';\n  return `Hello, ${displayName}!`;\n}\nconsole.log(greet('Ravi'));\nconsole.log(greet(null));\nconsole.log(greet(undefined));",
                        "Final price: 850\nOdd\nHello, Ravi!\nHello, Guest!\nHello, Guest!")
                ),
                List.of(
                    "Always use === and !== instead of == and != to avoid type coercion surprises",
                    "'5' + 3 is '53' (string concatenation), '5' - 3 is 2 (numeric subtraction)",
                    "&& returns the first falsy value, || returns the first truthy value",
                    "?? is safer than || for default values — it only triggers on null/undefined, not 0 or ''",
                    "Ternary is for simple one-line conditions — use if/else for complex logic"
                ),
                "Use ?? instead of || when providing default values for numeric or boolean fields. score || 0 would incorrectly treat score=0 as missing, but score ?? 0 handles it correctly.",
                List.of(
                    "Using == instead of === — causes unexpected bugs with type coercion",
                    "Using || for default values when 0 or false are valid values — use ?? instead",
                    "Forgetting operator precedence: && evaluates before ||"
                ),
                15, 2, "D"),

            // ── 3. Type Coercion ─────────────────────────────────────────────
            conceptRich(js, "Type Coercion",
                "Type coercion is JavaScript's automatic conversion of values from one type to another. Understanding it explains many surprising JavaScript behaviours.",
                "JavaScript is loosely typed — it automatically converts types when needed. This is called type coercion.\n\nSometimes this is helpful:\n- '5' - 3 gives 2 (string coerced to number)\n\nSometimes it causes bugs:\n- '5' + 3 gives '53' (number coerced to string)\n- [] + {} gives '[object Object]'\n\nTruthy and falsy values are how JavaScript evaluates non-boolean values in conditions. Knowing which values are falsy is essential for writing correct conditions.",
                "1. Implicit type coercion:\n- Happens automatically in operations between different types\n- + with a string: converts everything to string\n- -, *, /, %: converts strings to numbers\n- Comparison with ==: coerces types to match\n\n2. Explicit type conversion:\n- Number('42') → 42, Number('hello') → NaN\n- String(42) → '42'\n- Boolean(0) → false, Boolean('hello') → true\n- parseInt('42px') → 42, parseFloat('3.14em') → 3.14\n\n3. Falsy values (6 total):\n- false, 0, '' (empty string), null, undefined, NaN\n- Everything else is truthy — including '0', [], {}, -1\n\n4. == vs === coercion:\n- null == undefined → true\n- null === undefined → false\n- 0 == false → true\n- 0 === false → false\n- '' == false → true\n\n5. NaN:\n- Not a Number — result of invalid number operations\n- NaN === NaN → false (NaN is not equal to itself!)\n- Use Number.isNaN() to check for NaN",
                "// Implicit coercion\nconsole.log('5' + 3);      // '53' (string)\nconsole.log('5' - 3);      // 2 (number)\nconsole.log('5' * '2');    // 10\nconsole.log(true + 1);     // 2\nconsole.log(false + 1);    // 1\nconsole.log(null + 1);     // 1\nconsole.log(undefined + 1); // NaN\n\n// Explicit conversion\nconsole.log(Number('42'));    // 42\nconsole.log(Number(''));      // 0\nconsole.log(Number('abc'));   // NaN\nconsole.log(Number(true));    // 1\nconsole.log(Number(null));    // 0\nconsole.log(Number(undefined)); // NaN\n\n// Falsy values\nconst falsyValues = [false, 0, '', null, undefined, NaN];\nfalsyValues.forEach(v => console.log(`${String(v)} is falsy: ${!v}`));\n\n// Truthy surprises\nconsole.log(Boolean('0'));   // true — non-empty string\nconsole.log(Boolean([]));    // true — empty array!\nconsole.log(Boolean({}));    // true — empty object!\n\n// NaN check\nconst result = Number('hello');\nconsole.log(result === NaN);      // false — NaN != NaN\nconsole.log(Number.isNaN(result)); // true — correct way",
                List.of(
                    new Concept.ConceptExample("Coercion bugs in conditions",
                        "Common bugs caused by not understanding truthy/falsy.",
                        "// Bug: checking for empty array\nconst items = [];\nif (items) {\n  console.log('Has items');   // THIS RUNS — [] is truthy!\n}\n\n// Fix:\nif (items.length > 0) {\n  console.log('Has items');\n} else {\n  console.log('Empty');       // correct\n}\n\n// Bug: number zero treated as falsy\nfunction getScore(score) {\n  return score || 'No score';\n}\nconsole.log(getScore(0));    // 'No score' — wrong!\nconsole.log(getScore(0) ?? 'No score');   // wait, this won't work either\n// Fix:\nfunction getScoreFixed(score) {\n  return score !== null && score !== undefined ? score : 'No score';\n}\nconsole.log(getScoreFixed(0));   // 0 — correct",
                        "Has items\nEmpty\nNo score\n0")
                ),
                List.of(
                    "Six falsy values: false, 0, '', null, undefined, NaN — everything else is truthy",
                    "'0', [], {} are all TRUTHY — empty array and empty object are truthy",
                    "Use === to compare without coercion — always preferred",
                    "NaN is not equal to itself: NaN === NaN is false. Use Number.isNaN() to check",
                    "Number(null) is 0, Number(undefined) is NaN — they behave differently"
                ),
                "Before writing if (value), always ask: could value be 0, empty string, or false legitimately? If yes, check explicitly with value !== null && value !== undefined, or use the ?? operator.",
                List.of(
                    "Using [] or {} in a falsy check — they are truthy, always. Check .length for arrays",
                    "Using == for comparison — coercion makes null == undefined and 0 == false",
                    "Checking NaN with === NaN — always use Number.isNaN()"
                ),
                15, 3, "D"),

            // ── 4. Conditional Statements ─────────────────────────────────────
            conceptRich(js, "Conditional Statements",
                "Conditional statements execute different code based on whether a condition is true or false. JavaScript has if/else, else if chains, switch statements and the ternary operator.",
                "Programs need to make decisions.\n\nIs the user logged in? Show the dashboard. Otherwise show the login page.\nIs the score above 90? Grade A. Above 75? Grade B. Otherwise keep checking.\n\nJavaScript gives you three ways to write conditional logic:\n- if/else: most flexible, works with any condition\n- switch: cleaner for checking one value against many cases\n- ternary: one-line shorthand for simple if/else",
                "1. if / else if / else:\n- if (condition) runs the block when condition is truthy\n- else if adds another condition check\n- else runs when no condition matched\n- Only the first matching block runs\n\n2. switch statement:\n- switch(value) compares value to each case\n- break required to stop fall-through\n- default runs if no case matched\n- Uses === comparison internally\n\n3. Ternary operator:\n- condition ? valueIfTrue : valueIfFalse\n- Returns a value — can be used in assignments\n\n4. Short-circuit as conditionals:\n- condition && doSomething() — runs doSomething only if condition is truthy\n- value || defaultValue — gives default if value is falsy\n\n5. Nullish coalescing for conditionals:\n- value ?? defaultValue — default only if null/undefined",
                "// if / else if / else\nfunction getGrade(score) {\n  if (score >= 90) return 'A';\n  else if (score >= 75) return 'B';\n  else if (score >= 60) return 'C';\n  else if (score >= 40) return 'D';\n  else return 'F';\n}\nconsole.log(getGrade(85));  // B\nconsole.log(getGrade(35));  // F\n\n// switch\nfunction getDayType(day) {\n  switch (day.toLowerCase()) {\n    case 'saturday':\n    case 'sunday':\n      return 'Weekend';\n    case 'monday':\n    case 'friday':\n      return 'Start/End of week';\n    default:\n      return 'Weekday';\n  }\n}\nconsole.log(getDayType('Sunday'));   // Weekend\nconsole.log(getDayType('Tuesday'));  // Weekday\n\n// Ternary\nconst age = 20;\nconst access = age >= 18 ? 'Allowed' : 'Denied';\nconsole.log(access);\n\n// Short-circuit\nconst user = { name: 'Ravi', isAdmin: true };\nuser.isAdmin && console.log('Admin panel visible');",
                List.of(
                    new Concept.ConceptExample("Grade calculator",
                        "Use if/else chain to assign grades.",
                        "const students = [\n  { name: 'Ravi', score: 92 },\n  { name: 'Priya', score: 78 },\n  { name: 'Arjun', score: 55 },\n  { name: 'Sneha', score: 35 }\n];\n\nstudents.forEach(s => {\n  let grade;\n  if (s.score >= 90) grade = 'A';\n  else if (s.score >= 75) grade = 'B';\n  else if (s.score >= 60) grade = 'C';\n  else if (s.score >= 40) grade = 'D';\n  else grade = 'F';\n  console.log(`${s.name}: ${s.score} -> ${grade}`);\n});",
                        "Ravi: 92 -> A\nPriya: 78 -> B\nArjun: 55 -> C\nSneha: 35 -> F")
                ),
                List.of(
                    "Only the first matching if/else if block runs — order matters",
                    "switch uses === comparison — '1' and 1 are different cases",
                    "Always add break in switch cases unless you intentionally want fall-through",
                    "Ternary is for simple value decisions — use if/else for logic with multiple statements"
                ),
                "Use early returns in functions to avoid deep if/else nesting. Instead of if (valid) { ... many lines ... }, write if (!valid) return; and put the main logic at the top level.",
                List.of(
                    "Forgetting break in switch — causes fall-through to the next case",
                    "Using switch for range checks — switch uses === so case score >= 90 does not work"
                ),
                15, 4, "D"),

            // ── 5. Loops ─────────────────────────────────────────────────────
            conceptRich(js, "Loops",
                "Loops repeat a block of code. JavaScript has for, while, do...while, for...of and for...in loops, plus array methods like forEach.",
                "Loops let you repeat code without writing it multiple times.\n\nProcess 100 students? One loop.\nDisplay all items in a cart? One loop.\nKeep asking for valid input? One loop.\n\nJavaScript has several types of loops:\n- for: when you know how many times to loop\n- while: when you loop until a condition changes\n- do...while: loop at least once, then check condition\n- for...of: loop through values of an iterable (array, string)\n- for...in: loop through keys of an object\n- forEach: array method for looping — cleaner for arrays",
                "1. for loop:\n- for (init; condition; update)\n- Most flexible — full control over counter\n- Use when index matters or you need to break early\n\n2. while loop:\n- Runs while condition is true\n- Must update condition inside loop or infinite loop\n\n3. do...while:\n- Runs the block FIRST, then checks condition\n- Guarantees at least one execution\n\n4. for...of:\n- for (const item of iterable)\n- Works on arrays, strings, Maps, Sets\n- Gives values, not indices\n- Clean and readable for iterating arrays\n\n5. for...in:\n- for (const key in object)\n- Loops over enumerable property keys of an object\n- Not recommended for arrays — use for...of instead\n\n6. forEach:\n- array.forEach((item, index) => {})\n- Cleaner than for for arrays\n- Cannot break out early — use for...of if you need break\n\n7. break and continue:\n- break: exits loop immediately\n- continue: skips current iteration",
                "// for loop\nfor (let i = 0; i < 5; i++) {\n  console.log(i);\n}\n\n// while loop\nlet count = 0;\nwhile (count < 3) {\n  console.log('count:', count);\n  count++;\n}\n\n// for...of — arrays\nconst fruits = ['apple', 'mango', 'banana'];\nfor (const fruit of fruits) {\n  console.log(fruit);\n}\n\n// for...of — string\nfor (const char of 'JS') {\n  console.log(char);\n}\n\n// for...in — object keys\nconst user = { name: 'Ravi', age: 21, city: 'Hyderabad' };\nfor (const key in user) {\n  console.log(`${key}: ${user[key]}`);\n}\n\n// forEach\nconst scores = [85, 92, 78];\nscores.forEach((score, index) => {\n  console.log(`Student ${index + 1}: ${score}`);\n});\n\n// break and continue\nfor (let i = 0; i < 10; i++) {\n  if (i === 3) continue;  // skip 3\n  if (i === 6) break;     // stop at 6\n  console.log(i);         // 0 1 2 4 5\n}",
                List.of(
                    new Concept.ConceptExample("Loop through and filter an array",
                        "Find all passing scores and calculate the average.",
                        "const scores = [85, 32, 91, 45, 28, 76, 60, 15, 88];\nconst passing = [];\n\nfor (const score of scores) {\n  if (score >= 40) {\n    passing.push(score);\n  }\n}\n\nconst total = passing.reduce((sum, s) => sum + s, 0);\nconst avg = total / passing.length;\n\nconsole.log('Passing scores:', passing);\nconsole.log('Count:', passing.length);\nconsole.log('Average:', avg.toFixed(1));",
                        "Passing scores: [85, 91, 45, 76, 60, 88]\nCount: 6\nAverage: 74.2"),
                    new Concept.ConceptExample("Nested loops — multiplication table",
                        "Use nested for loops to generate a multiplication table.",
                        "for (let i = 1; i <= 3; i++) {\n  let row = '';\n  for (let j = 1; j <= 3; j++) {\n    row += (i * j).toString().padStart(4);\n  }\n  console.log(row);\n}",
                        "   1   2   3\n   2   4   6\n   3   6   9")
                ),
                List.of(
                    "for...of gives values, for...in gives keys — use for...of for arrays",
                    "forEach cannot be stopped with break — use for...of if you need to exit early",
                    "Always update the loop variable in while — forgetting causes an infinite loop",
                    "do...while runs at least once — useful for menus and input validation",
                    "for...in on arrays also iterates prototype properties — avoid it for arrays"
                ),
                "Prefer for...of over a classic for loop when you don't need the index. It is cleaner and less error-prone — no off-by-one mistakes. Use forEach when calling a function for each item. Use classic for when you need the index or want to break early.",
                List.of(
                    "Using for...in on arrays — it iterates keys (indices as strings) and inherited properties",
                    "Forgetting to increment in while loop — causes infinite loop",
                    "Trying to break out of forEach — it does not support break, use for...of instead"
                ),
                18, 5, "D"),

            // ── 6. Functions and Types ────────────────────────────────────────
            conceptRich(js, "Functions and Types",
                "Functions are reusable blocks of code. JavaScript has function declarations, function expressions, arrow functions, and IIFEs. Each has different behaviour with scope and 'this'.",
                "A function is a reusable block of code with a name.\n\nJavaScript has several ways to define functions, and the differences matter:\n\nFunction declaration: hoisted, named, traditional\nFunction expression: assigned to variable, not hoisted\nArrow function: short syntax, no own 'this', no arguments object\nIIFE: runs immediately after definition\n\nAll functions are first-class citizens in JavaScript — they can be passed as arguments, returned from other functions, and stored in variables.",
                "1. Function declaration:\n- function name() {} — traditional syntax\n- Hoisted: can be called before it appears in code\n- Has its own this binding\n\n2. Function expression:\n- const fn = function() {} — assigned to variable\n- Not hoisted — cannot be called before declaration\n- Can be anonymous or named\n\n3. Arrow function (ES6):\n- const fn = () => {} or const fn = () => value\n- Shorter syntax\n- No own this — inherits from surrounding scope\n- No arguments object\n- Cannot be used as constructor\n\n4. Parameters and arguments:\n- Default parameters: function greet(name = 'Guest')\n- Rest parameters: function sum(...nums) — collects remaining args into array\n\n5. Return value:\n- return sends value back to caller\n- Without return, function returns undefined\n- Arrow functions with single expression: implicit return\n\n6. IIFE (Immediately Invoked Function Expression):\n- (function() { })() — runs immediately\n- Creates private scope",
                "// Function declaration — hoisted\nconsole.log(add(3, 5));  // 8 — works before declaration\nfunction add(a, b) {\n  return a + b;\n}\n\n// Function expression — not hoisted\nconst multiply = function(a, b) {\n  return a * b;\n};\n\n// Arrow function\nconst square = (n) => n * n;\nconst greet = name => `Hello, ${name}!`;\nconst getUser = () => ({ name: 'Ravi', age: 21 });  // return object\n\nconsole.log(square(5));       // 25\nconsole.log(greet('Priya')); // Hello, Priya!\n\n// Default parameters\nfunction createUser(name, role = 'student', active = true) {\n  return { name, role, active };\n}\nconsole.log(createUser('Ravi'));\nconsole.log(createUser('Admin', 'admin'));\n\n// Rest parameters\nfunction sumAll(...numbers) {\n  return numbers.reduce((total, n) => total + n, 0);\n}\nconsole.log(sumAll(1, 2, 3, 4, 5));  // 15\n\n// IIFE\n(function() {\n  const secret = 'private';\n  console.log('IIFE runs immediately:', secret);\n})();\n// secret not accessible here",
                List.of(
                    new Concept.ConceptExample("Functions as first-class citizens",
                        "Pass functions as arguments and return them from other functions.",
                        "// Function passed as argument\nfunction applyTwice(fn, value) {\n  return fn(fn(value));\n}\n\nconst double = n => n * 2;\nconsole.log(applyTwice(double, 3));   // 12\n\n// Function returned from function\nfunction multiplier(factor) {\n  return (number) => number * factor;\n}\n\nconst triple = multiplier(3);\nconst quadruple = multiplier(4);\nconsole.log(triple(5));     // 15\nconsole.log(quadruple(5));  // 20",
                        "12\n15\n20"),
                    new Concept.ConceptExample("Arrow function vs regular function — this",
                        "Arrow functions do not have their own this.",
                        "const timer = {\n  seconds: 0,\n  // Regular function — 'this' is wrong in callback\n  startWrong: function() {\n    // setTimeout(function() { this.seconds++; }, 100); // this is window!\n  },\n  // Arrow function — 'this' from surrounding scope\n  start: function() {\n    const tick = () => {\n      this.seconds++;\n      console.log('Seconds:', this.seconds);\n    };\n    tick();\n    tick();\n  }\n};\ntimer.start();",
                        "Seconds: 1\nSeconds: 2")
                ),
                List.of(
                    "Function declarations are hoisted — they can be called before they appear in code",
                    "Arrow functions have no own 'this' — they use the this from the surrounding scope",
                    "Arrow functions with a single expression do not need return or braces",
                    "Default parameters are used when the argument is undefined",
                    "Rest parameters (...args) collect all remaining arguments into an array"
                ),
                "Use arrow functions for callbacks and short functions. Use regular function declarations for named, reusable functions at the module level. This keeps your code consistent and avoids 'this' surprises.",
                List.of(
                    "Using arrow functions as methods — they do not have their own this",
                    "Forgetting () => ({}) when returning an object literal from an arrow function — the {} is read as a block",
                    "Confusing rest parameters (...args) with the spread operator — same syntax, different context"
                ),
                20, 6, "C"),

            // ── 7. Scope ──────────────────────────────────────────────────────
            conceptRich(js, "Scope",
                "Scope determines where variables are accessible. JavaScript has global scope, function scope and block scope. Understanding scope prevents variable collision and unexpected bugs.",
                "Scope is the context in which a variable exists and can be accessed.\n\nThink of scope like rooms in a building:\n- Global scope: the lobby — everyone can access it\n- Function scope: a private office — only code inside can access it\n- Block scope: a locked cabinet inside the office — only code in that block\n\nvar is function-scoped.\nlet and const are block-scoped.\n\nScope also determines variable shadowing — when an inner variable has the same name as an outer one, the inner one takes priority in its scope.",
                "1. Global scope:\n- Variables declared outside any function or block\n- Accessible everywhere in the script\n- In browsers, global variables become properties of window\n- Avoid polluting global scope — use modules or IIFEs\n\n2. Function scope:\n- Variables declared inside a function with var, let or const\n- Only accessible inside that function\n- Each function call creates a new scope\n\n3. Block scope:\n- Variables declared with let or const inside {} blocks\n- if, for, while blocks create block scope\n- var ignores block scope — it leaks out of blocks\n\n4. Lexical scope:\n- Inner functions can access outer function variables\n- Scope is determined at write time, not run time\n- Foundation for closures (covered in JS Advanced)\n\n5. Variable shadowing:\n- Inner scope variable with same name hides outer variable\n- The outer variable is unchanged\n\n6. Scope chain:\n- When a variable is not found in current scope, JS looks in outer scope\n- Continues until global scope — ReferenceError if not found",
                "// Global scope\nconst globalName = 'Ravi';\n\nfunction greet() {\n  // Function scope\n  const message = 'Hello';  // only inside greet\n  console.log(message + ' ' + globalName);  // can access global\n}\ngreet();\n// console.log(message);  // ReferenceError\n\n// Block scope — let vs var\nif (true) {\n  let blockLet = 'block';    // block scope\n  var blockVar = 'function'; // function scope (leaks!)\n}\n// console.log(blockLet);   // ReferenceError\nconsole.log(blockVar);       // 'function' — leaked out\n\n// Lexical scope — inner can see outer\nfunction outer() {\n  const x = 10;\n  function inner() {\n    const y = 20;\n    console.log(x + y);  // 30 — inner sees outer's x\n  }\n  inner();\n  // console.log(y);  // ReferenceError\n}\nouter();\n\n// Variable shadowing\nconst color = 'red';\nfunction paintRoom() {\n  const color = 'blue';  // shadows outer color\n  console.log(color);   // 'blue'\n}\npaintRoom();\nconsole.log(color);     // 'red' — outer unchanged",
                List.of(
                    new Concept.ConceptExample("Scope in loops — var vs let",
                        "The classic loop scope bug with var — a must-know interview question.",
                        "// Bug with var — all callbacks see the same i\nconst funcsVar = [];\nfor (var i = 0; i < 3; i++) {\n  funcsVar.push(() => console.log('var:', i));\n}\nfuncsVar.forEach(f => f());  // 3 3 3 — all same!\n\n// Fixed with let — each iteration gets its own i\nconst funcsLet = [];\nfor (let i = 0; i < 3; i++) {\n  funcsLet.push(() => console.log('let:', i));\n}\nfuncsLet.forEach(f => f());  // 0 1 2 — correct",
                        "var: 3\nvar: 3\nvar: 3\nlet: 0\nlet: 1\nlet: 2")
                ),
                List.of(
                    "var is function-scoped — it leaks out of if/for blocks",
                    "let and const are block-scoped — confined to the {} they are declared in",
                    "Inner functions can access outer scope variables (lexical scope)",
                    "A variable shadows an outer variable when declared with the same name in inner scope",
                    "Scope chain: JS looks in current scope, then outer, then global for any variable"
                ),
                "Avoid using global variables. Every variable should be declared in the smallest scope that makes sense — function scope or block scope. This prevents accidental modification from other parts of the code.",
                List.of(
                    "Using var in loops — creates a single shared variable, not one per iteration",
                    "Declaring variables without let/const/var — creates accidental global variables",
                    "Thinking variables in one function are accessible in another — each function has its own scope"
                ),
                18, 7, "C"),

            // ── 8. Hoisting ───────────────────────────────────────────────────
            conceptRich(js, "Hoisting",
                "Hoisting moves variable and function declarations to the top of their scope before code runs. Function declarations are fully hoisted; var is hoisted but not initialised; let and const are hoisted but not accessible before declaration.",
                "Before executing any code, JavaScript scans the file and moves all declarations to the top of their scope. This is called hoisting.\n\nThis is why you can call a function before it appears in the file — the declaration was already moved up.\n\nHoisting behaves differently for different declaration types:\n- Function declarations: fully hoisted — can call before declaration\n- var: hoisted but set to undefined — accessing before declaration gives undefined, not an error\n- let and const: hoisted but placed in the Temporal Dead Zone — accessing before declaration gives ReferenceError",
                "1. Function declaration hoisting:\n- Entire function is moved to top of scope\n- Can call the function anywhere in the file\n- This is why function declarations work before they appear\n\n2. var hoisting:\n- Declaration is hoisted, initialisation is not\n- var x = 5 is split into: var x (hoisted to top) + x = 5 (stays in place)\n- Accessing var before assignment gives undefined, not ReferenceError\n\n3. let and const hoisting:\n- Declaration is technically hoisted\n- But placed in the Temporal Dead Zone (TDZ)\n- Accessing before declaration gives ReferenceError\n- TDZ ends when the declaration line is reached\n\n4. Class hoisting:\n- Class declarations are NOT hoisted like functions\n- Must define class before using it\n\n5. Practical implication:\n- Always declare variables at the top of their scope\n- Use let/const — they give clear errors instead of silent undefined bugs",
                "// Function declarations are fully hoisted\nconsole.log(add(2, 3));  // 5 — works before declaration!\nfunction add(a, b) { return a + b; }\n\n// var is hoisted as undefined\nconsole.log(x);  // undefined — no error\nvar x = 5;\nconsole.log(x);  // 5\n\n// let/const are NOT accessible before declaration\n// console.log(y);  // ReferenceError: Cannot access before init\nlet y = 10;\n\n// What JavaScript sees after hoisting:\n// (Conceptual — not actual transformation)\n// var x;\n// function add(a, b) { return a + b; }\n// console.log(add(2, 3));\n// console.log(x);\n// x = 5;\n// console.log(x);\n\n// Function expression — NOT hoisted like declaration\n// console.log(greet('Ravi'));  // TypeError: greet is not a function\nvar greet = function(name) { return `Hi ${name}`; };\nconsole.log(greet('Ravi'));  // Hi Ravi",
                List.of(
                    new Concept.ConceptExample("Hoisting bug with var in function",
                        "A classic hoisting bug — var declaration hoisted but value not yet assigned.",
                        "function processOrder() {\n  console.log('Status:', status);  // undefined — not an error!\n\n  if (true) {\n    var status = 'processing';\n  }\n\n  console.log('Status:', status);  // 'processing'\n}\nprocessOrder();\n\n// With let — clear error instead of silent bug\nfunction processOrderFixed() {\n  // console.log(status); // ReferenceError: Cannot access before init\n  let status = 'processing';\n  console.log('Status:', status);\n}\nprocessOrderFixed();",
                        "Status: undefined\nStatus: processing\nStatus: processing")
                ),
                List.of(
                    "Function declarations are fully hoisted — they can be called before they appear",
                    "var declarations are hoisted but initialised to undefined, not their value",
                    "let and const are NOT accessible before their declaration line — they are in the TDZ",
                    "Function expressions (const fn = function(){}) are not hoisted like declarations",
                    "Hoisting is why var causes silent bugs — always use let/const"
                ),
                "Always declare your variables at the top of their function or block, even with let and const. This makes hoisting behaviour irrelevant and makes your code easier to read — you always know what is declared before it is used.",
                List.of(
                    "Relying on var hoisting — the value is undefined before the assignment line",
                    "Expecting function expressions to be hoisted like declarations — they are not",
                    "Thinking let/const are not hoisted at all — they are, but in the TDZ"
                ),
                15, 8, "C"),

            // ── 9. Temporal Dead Zone ─────────────────────────────────────────
            conceptRich(js, "Temporal Dead Zone (TDZ)",
                "The Temporal Dead Zone is the period between entering a block scope and the point where a let or const variable is declared. Accessing a variable in TDZ throws a ReferenceError.",
                "When JavaScript enters a block, it knows about all the let and const declarations in that block (due to hoisting). But it marks them as uninitialised.\n\nThe Temporal Dead Zone is the gap between:\n- Entering the block (where JS knows the variable exists)\n- Reaching the actual declaration line (where it becomes accessible)\n\nThis is why let and const are safer than var — they give you a clear ReferenceError instead of silently giving undefined.",
                "1. Why TDZ exists:\n- Prevents accessing variables before they are meaningfully defined\n- Encourages declaring variables before use\n- Makes bugs obvious rather than silent\n\n2. TDZ behaviour:\n- Starts when the block scope is entered\n- Ends when the let/const declaration line is executed\n- Any access in between throws ReferenceError: Cannot access before initialization\n\n3. TDZ with functions in class:\n- Class methods cannot access class fields before the constructor runs\n\n4. TDZ with default parameters:\n- Parameters are evaluated left to right\n- A parameter cannot reference a later parameter: (a, b = a) is OK, (a = b, b) is TDZ error\n\n5. typeof in TDZ:\n- typeof undeclaredVar → 'undefined' (no error)\n- typeof tdz_variable → ReferenceError (in TDZ)",
                "// TDZ example\n{\n  // TDZ for 'name' starts here\n  // console.log(name); // ReferenceError!\n\n  let name = 'Ravi';  // TDZ ends here\n  console.log(name);  // Ravi\n}\n\n// var has no TDZ — gives undefined instead of error\n{\n  console.log(age);  // undefined (no error)\n  var age = 21;\n  console.log(age);  // 21\n}\n\n// TDZ in functions\nfunction greet() {\n  // console.log(msg); // ReferenceError — msg in TDZ\n  const msg = 'Hello';\n  console.log(msg);\n}\ngreet();\n\n// typeof with TDZ — still throws!\nfunction test() {\n  // console.log(typeof x); // ReferenceError — x is in TDZ\n  let x = 5;\n}\n\n// Default parameter TDZ\nfunction demo(a, b = a) {  // b = a is OK\n  return a + b;\n}\nconsole.log(demo(5));  // 10",
                List.of(
                    new Concept.ConceptExample("TDZ vs var — the difference",
                        "Compare the behaviour of let in TDZ vs var hoisting.",
                        "// This is why var causes silent bugs\nfunction withVar() {\n  console.log(x);  // undefined — no error\n  var x = 10;\n  console.log(x);  // 10\n}\n\n// This is why let is safer\nfunction withLet() {\n  try {\n    console.log(x);  // ReferenceError caught\n  } catch (e) {\n    console.log('Error:', e.message);\n  }\n  let x = 10;\n  console.log(x);  // 10\n}\n\nwithVar();\nwithLet();",
                        "undefined\n10\nError: Cannot access 'x' before initialization\n10")
                ),
                List.of(
                    "TDZ is the gap between entering a scope and reaching the let/const declaration",
                    "Accessing a variable in TDZ throws ReferenceError — not undefined like var",
                    "TDZ makes let/const safer than var — errors are explicit, not silent",
                    "typeof on a TDZ variable still throws ReferenceError — unlike undeclared variables"
                ),
                "The TDZ is a feature, not a limitation. It forces you to declare variables before using them, which makes code more predictable. If you see 'Cannot access before initialization', check if you are using let/const and accessing it before its declaration line.",
                List.of(
                    "Trying to access let/const above its declaration — always declare before use",
                    "Thinking typeof is safe for TDZ variables — it still throws ReferenceError"
                ),
                12, 9, "C"),

            // ── 10. Arrays and Array Methods ──────────────────────────────────
            conceptRich(js, "Arrays and Array Methods",
                "Arrays are ordered lists. JavaScript arrays have powerful built-in methods for adding, removing, searching, transforming and iterating over data.",
                "An array is an ordered collection of values.\n\nJavaScript arrays are dynamic — they grow and shrink automatically. They can hold mixed types (numbers, strings, objects, other arrays).\n\nThe real power of arrays comes from their methods:\n- Mutating methods: push, pop, shift, unshift, splice, sort, reverse\n- Non-mutating methods: map, filter, reduce, find, findIndex, some, every, slice, concat\n- Iteration: forEach, for...of\n\nKnowing map, filter and reduce is essential for modern JavaScript and interviews.",
                "1. Creating and accessing arrays:\n- const arr = [1, 2, 3]\n- Zero-indexed: arr[0] is first element\n- arr.length gives number of elements\n- Negative indexing: arr.at(-1) is last element (ES2022)\n\n2. Mutating methods (modify original):\n- push(item): add to end\n- pop(): remove from end, returns removed item\n- unshift(item): add to start\n- shift(): remove from start\n- splice(start, deleteCount, ...items): remove/insert\n- sort(): sorts in place (lexicographic by default)\n- reverse(): reverses in place\n\n3. Non-mutating methods (return new array):\n- slice(start, end): extract portion\n- concat(...arrays): merge arrays\n- map(fn): transform each element\n- filter(fn): keep elements matching condition\n- reduce(fn, initial): accumulate to single value\n\n4. Search methods:\n- find(fn): first matching element or undefined\n- findIndex(fn): index of first match or -1\n- indexOf(value): index of value or -1\n- includes(value): boolean\n- some(fn): true if any element matches\n- every(fn): true if all elements match\n\n5. Utility:\n- Array.from(iterable): convert to array\n- Array.isArray(value): check if array\n- flat(depth): flatten nested arrays\n- flatMap(fn): map then flatten",
                "const nums = [3, 1, 4, 1, 5, 9, 2, 6];\n\n// Add/remove\nnums.push(7);          // add to end\nnums.unshift(0);       // add to start\nconst last = nums.pop(); // remove from end\n\n// slice — non-mutating\nconst first3 = nums.slice(0, 3);\nconsole.log(first3, nums); // original unchanged\n\n// map — transform\nconst doubled = nums.map(n => n * 2);\nconsole.log(doubled);\n\n// filter — select\nconst evens = nums.filter(n => n % 2 === 0);\nconsole.log(evens);\n\n// reduce — accumulate\nconst total = nums.reduce((sum, n) => sum + n, 0);\nconsole.log('Sum:', total);\n\n// find\nconst bigNum = nums.find(n => n > 5);\nconsole.log('First > 5:', bigNum);\n\n// some / every\nconsole.log(nums.some(n => n > 8));   // true\nconsole.log(nums.every(n => n > 0));  // true\n\n// sort numbers correctly\nconst sorted = [...nums].sort((a, b) => a - b);\nconsole.log(sorted);",
                List.of(
                    new Concept.ConceptExample("map, filter, reduce pipeline",
                        "Chain array methods to process student data.",
                        "const students = [\n  { name: 'Ravi', score: 85 },\n  { name: 'Priya', score: 32 },\n  { name: 'Arjun', score: 91 },\n  { name: 'Sneha', score: 47 },\n  { name: 'Kiran', score: 28 }\n];\n\nconst passAverage = students\n  .filter(s => s.score >= 40)\n  .map(s => s.score)\n  .reduce((sum, s, _, arr) => sum + s / arr.length, 0);\n\nconsole.log('Pass average:', passAverage.toFixed(1));\n\nconst topStudents = students\n  .filter(s => s.score >= 80)\n  .map(s => `${s.name} (${s.score})`)\n  .join(', ');\nconsole.log('Top students:', topStudents);",
                        "Pass average: 74.3\nTop students: Ravi (85), Arjun (91)"),
                    new Concept.ConceptExample("sort — numbers and objects",
                        "Sort arrays of numbers and objects correctly.",
                        "// Sort numbers — must provide comparator\nconst nums = [10, 2, 21, 1, 9];\nconsole.log([...nums].sort());           // [1, 10, 2, 21, 9] — wrong!\nconsole.log([...nums].sort((a, b) => a - b)); // [1, 2, 9, 10, 21] — correct\n\n// Sort objects by property\nconst people = [\n  { name: 'Priya', age: 25 },\n  { name: 'Ravi', age: 21 },\n  { name: 'Arjun', age: 28 }\n];\n\nconst byAge = [...people].sort((a, b) => a.age - b.age);\nbyAge.forEach(p => console.log(`${p.name}: ${p.age}`));",
                        "[1, 10, 2, 21, 9]\n[1, 2, 9, 10, 21]\nRavi: 21\nPriya: 25\nArjun: 28")
                ),
                List.of(
                    "map returns a new array, forEach does not — use map when you need the result",
                    "filter returns a new array of matching elements",
                    "reduce accumulates all elements into a single value — sum, count, object, etc.",
                    "sort() without a comparator sorts lexicographically — always pass (a,b) => a-b for numbers",
                    "Use [...arr] to copy before mutating sort/reverse so original is unchanged",
                    "find returns the first matching element, filter returns all matches"
                ),
                "Learn map, filter and reduce deeply — they appear in almost every JavaScript interview. Practice replacing for loops with these three methods. For example: building an array from another array = map, keeping some items = filter, computing one value from many = reduce.",
                List.of(
                    "Sorting numbers without a comparator — sort() converts to strings and sorts lexicographically",
                    "Mutating the original array when you intended to keep it — use [...arr] to clone first",
                    "Using map when you want forEach — map creates a new array, forEach returns undefined"
                ),
                22, 10, "C"),

            // ── 11. Strings and String Methods ────────────────────────────────
            conceptRich(js, "Strings and String Methods",
                "Strings are immutable sequences of characters. JavaScript provides built-in methods for searching, slicing, replacing, splitting and formatting strings. Template literals enable clean string interpolation.",
                "A string is a sequence of characters in quotes.\n\nJavaScript strings are immutable — methods always return a new string, they never modify the original.\n\nTemplate literals (backticks) are the modern way to work with strings:\n- Embed expressions: `Hello ${name}`\n- Multi-line strings without \\n\n- Tagged templates for advanced use\n\nString methods cover all common text processing needs — searching, slicing, trimming, replacing, splitting and more.",
                "1. String creation:\n- Single quotes: 'hello'\n- Double quotes: \"hello\"\n- Template literals: `hello ${name}`\n- Strings are zero-indexed like arrays\n\n2. Common properties and methods:\n- length: number of characters\n- [index] or charAt(i): character at index\n- indexOf(sub): index of first occurrence, -1 if not found\n- lastIndexOf(sub): last occurrence\n- includes(sub): boolean\n- startsWith(prefix), endsWith(suffix): boolean\n\n3. Extracting:\n- slice(start, end): extract portion (negative index works)\n- substring(start, end): like slice but no negative index\n- at(index): access by index (supports negative)\n\n4. Transforming:\n- toUpperCase(), toLowerCase()\n- trim(), trimStart(), trimEnd(): remove whitespace\n- replace(search, replacement): first match\n- replaceAll(search, replacement): all matches\n- padStart(length, char), padEnd(length, char): pad to length\n- repeat(n): repeat string n times\n\n5. Splitting and joining:\n- split(separator): string to array\n- Array.join(sep) is the reverse\n\n6. Template literals:\n- `text ${expression} text`\n- Multi-line without \\n\n- Can contain any JS expression",
                "const str = '  Hello, JavaScript World!  ';\n\n// Basic properties\nconsole.log(str.length);           // 29\nconsole.log(str.trim());           // 'Hello, JavaScript World!'\nconsole.log(str.toUpperCase());    // '  HELLO, JAVASCRIPT WORLD!  '\n\n// Search\nconst clean = str.trim();\nconsole.log(clean.includes('JavaScript')); // true\nconsole.log(clean.startsWith('Hello'));    // true\nconsole.log(clean.indexOf('JavaScript')); // 7\n\n// Extract\nconsole.log(clean.slice(7, 17));   // 'JavaScript'\nconsole.log(clean.slice(-6));      // 'World!'\n\n// Replace\nconsole.log(clean.replace('JavaScript', 'JS'));\nconsole.log('a-b-c-d'.replaceAll('-', '_'));\n\n// Split and join\nconst words = clean.split(' ');\nconsole.log(words);\nconsole.log(words.join('-'));\n\n// Template literals\nconst name = 'Ravi';\nconst score = 92;\nconsole.log(`Student: ${name}, Score: ${score}, Grade: ${score >= 90 ? 'A' : 'B'}`);\n\n// Multi-line\nconst html = `\n  <div>\n    <h1>${name}</h1>\n  </div>\n`;",
                List.of(
                    new Concept.ConceptExample("Parse and format data from a string",
                        "Extract and reformat structured data from a CSV-like string.",
                        "const record = 'Ravi Kumar,CSE,2021,8.7 CGPA';\nconst parts = record.split(',');\n\nconst name = parts[0].trim();\nconst dept = parts[1].trim();\nconst year = parts[2].trim();\nconst cgpa = parseFloat(parts[3]);\n\nconsole.log(`Name:  ${name}`);\nconsole.log(`Dept:  ${dept}`);\nconsole.log(`Year:  ${year}`);\nconsole.log(`CGPA:  ${cgpa}`);\nconsole.log(`ID:    ${dept}-${year}-${name.split(' ')[0].toUpperCase()}`);",
                        "Name:  Ravi Kumar\nDept:  CSE\nYear:  2021\nCGPA:  8.7\nID:    CSE-2021-RAVI")
                ),
                List.of(
                    "Strings are immutable — all methods return new strings, the original never changes",
                    "Template literals use backticks and ${} for interpolation — prefer over concatenation",
                    "slice() supports negative indices, substring() does not",
                    "split() converts a string to an array, join() converts an array to a string",
                    "indexOf() returns -1 if not found — check with !== -1, or prefer includes() for boolean"
                ),
                "Use template literals instead of string concatenation. 'Hello ' + name + ', you scored ' + score is harder to read and edit than `Hello ${name}, you scored ${score}`.",
                List.of(
                    "Mutating a string directly — str[0] = 'X' silently fails, strings are immutable",
                    "Using == for string comparison — always use === for strings"
                ),
                18, 11, "C"),

            // ── 12. Objects and Object Methods ────────────────────────────────
            conceptRich(js, "Objects and Object Methods",
                "Objects store data as key-value pairs. JavaScript provides built-in Object methods to inspect, copy, merge and work with objects. Destructuring and shorthand syntax make objects easier to work with.",
                "An object is a collection of related data and functionality stored as key-value pairs.\n\nAlmost everything in JavaScript is an object — arrays, functions, dates, and regular expression all inherit from Object.\n\nObjects are the backbone of JavaScript programs:\n- A user profile is an object\n- An API response is an object\n- A DOM element is an object\n\nModern JavaScript gives you cleaner syntax for working with objects: shorthand properties, computed keys, destructuring, and spread operator.",
                "1. Creating objects:\n- Object literal: const obj = { key: value }\n- new Object() — avoid this\n- Object.create(proto) — prototype-based creation\n\n2. Accessing properties:\n- Dot notation: obj.name (preferred when key is known)\n- Bracket notation: obj['name'] (when key is dynamic or has spaces)\n\n3. Adding, updating, deleting:\n- obj.newKey = value — add or update\n- delete obj.key — remove property\n\n4. Shorthand property syntax (ES6):\n- const name = 'Ravi'; const obj = { name } same as { name: name }\n\n5. Computed property names:\n- const key = 'role'; const obj = { [key]: 'admin' }\n\n6. Object methods:\n- Object.keys(obj): array of keys\n- Object.values(obj): array of values\n- Object.entries(obj): array of [key, value] pairs\n- Object.assign(target, source): copy properties\n- Object.freeze(obj): prevent all modifications\n- Object.hasOwn(obj, key): check own property\n\n7. Destructuring:\n- const { name, age } = user\n- const { name: fullName } = user — rename\n- const { name = 'Guest' } = user — default value\n\n8. Spread operator:\n- const copy = { ...original } — shallow clone\n- const merged = { ...obj1, ...obj2 } — merge",
                "const user = {\n  name: 'Ravi',\n  age: 21,\n  city: 'Hyderabad',\n  greet() {\n    return `Hi, I'm ${this.name}`;\n  }\n};\n\n// Access\nconsole.log(user.name);\nconsole.log(user['city']);\n\n// Dynamic key\nconst field = 'age';\nconsole.log(user[field]);  // 21\n\n// Object methods\nconsole.log(Object.keys(user));\nconsole.log(Object.values(user));\nconsole.log(Object.entries(user));\n\n// Destructuring\nconst { name, age, city = 'Unknown' } = user;\nconsole.log(name, age, city);\n\n// Rename in destructuring\nconst { name: fullName } = user;\nconsole.log(fullName);\n\n// Spread — clone and merge\nconst updated = { ...user, age: 22, email: 'ravi@email.com' };\nconsole.log(updated);\n\n// Object.assign\nconst defaults = { theme: 'dark', lang: 'en' };\nconst config = Object.assign({}, defaults, { lang: 'hi' });\nconsole.log(config);",
                List.of(
                    new Concept.ConceptExample("Transform objects with entries and fromEntries",
                        "Convert object to array, transform, convert back.",
                        "const prices = { apple: 50, mango: 80, banana: 30, orange: 60 };\n\n// Apply 10% discount to all prices\nconst discounted = Object.fromEntries(\n  Object.entries(prices).map(([item, price]) => [item, price * 0.9])\n);\nconsole.log(discounted);\n\n// Filter expensive items\nconst expensive = Object.fromEntries(\n  Object.entries(prices).filter(([_, price]) => price >= 60)\n);\nconsole.log(expensive);",
                        "{ apple: 45, mango: 72, banana: 27, orange: 54 }\n{ mango: 80, orange: 60 }")
                ),
                List.of(
                    "Use dot notation for known keys, bracket notation for dynamic keys",
                    "Object.keys/values/entries return arrays — you can chain array methods on them",
                    "Spread {...obj} creates a shallow copy — nested objects are still shared",
                    "Destructuring with default values: const { name = 'Guest' } = obj",
                    "Object methods like greet() can use this to access other properties"
                ),
                "Use destructuring at function parameters to make the function signature self-documenting. function createUser({ name, email, role = 'student' }) is clearer than function createUser(options).",
                List.of(
                    "Using Object.assign or spread for deep clone — both are shallow, nested objects are shared",
                    "Accessing a missing property — returns undefined, not an error. Use optional chaining for nested: obj?.nested?.prop"
                ),
                20, 12, "C"),

            // ── 13. The 'this' Keyword ────────────────────────────────────────
            conceptRich(js, "The 'this' Keyword",
                "this refers to the object that is executing the current function. Its value depends on how the function is called, not where it is defined.",
                "this is one of JavaScript's most confusing concepts.\n\nIn most languages, this always refers to the current object. In JavaScript, this is determined at runtime — it depends on HOW the function is called, not WHERE it is defined.\n\nRules for what this is:\n- In global scope: window (browser) or global (Node)\n- In a method: the object before the dot\n- In a regular function: window in non-strict, undefined in strict mode\n- In an arrow function: this from the surrounding scope\n- With call/apply/bind: the explicitly provided object\n- In an event handler: the element that fired the event",
                "1. Global context:\n- In browser: this === window\n- In Node module: this === module.exports\n\n2. Method context:\n- obj.method() — this is obj\n- this is determined by what is left of the dot at call time\n\n3. Function context:\n- Regular function called without dot: this is window (or undefined in strict)\n- This is why extracting a method loses its this\n\n4. Arrow function context:\n- Arrow functions have no own this\n- They inherit this from the enclosing lexical scope\n- Useful for callbacks inside methods\n\n5. Explicit binding:\n- call(thisArg, ...args): call with explicit this\n- apply(thisArg, [args]): same but args as array\n- bind(thisArg): returns new function with fixed this\n\n6. new keyword:\n- new Constructor() — this is the newly created object",
                "// Method — this is the object\nconst user = {\n  name: 'Ravi',\n  greet() {\n    console.log(`Hi, I'm ${this.name}`);\n  }\n};\nuser.greet();  // Hi, I'm Ravi\n\n// Losing this — common bug\nconst greet = user.greet;\n// greet();  // Hi, I'm undefined — this lost!\n\n// Arrow function — inherits outer this\nconst timer = {\n  count: 0,\n  start() {\n    const tick = () => {\n      this.count++;  // this is timer — arrow inherits\n      console.log(this.count);\n    };\n    tick();\n    tick();\n  }\n};\ntimer.start();\n\n// bind — fix this permanently\nconst boundGreet = user.greet.bind(user);\nboundGreet();  // Hi, I'm Ravi — this is always user\n\n// call and apply\nfunction introduce(city, country) {\n  console.log(`${this.name} from ${city}, ${country}`);\n}\nintroduce.call(user, 'Hyderabad', 'India');\nintroduce.apply(user, ['Hyderabad', 'India']);",
                List.of(
                    new Concept.ConceptExample("this in event handler vs arrow function",
                        "Why arrow functions should not be used as event handlers.",
                        "// Simulating DOM-like behaviour\nconst button = {\n  text: 'Click me',\n  // Regular function — this is the button\n  handleClick: function() {\n    console.log('Clicked:', this.text);\n  },\n  // Arrow function — this is outer scope, not button\n  handleClickArrow: () => {\n    console.log('Clicked:', this?.text);  // undefined!\n  }\n};\n\nbutton.handleClick();       // Clicked: Click me\nbutton.handleClickArrow();  // Clicked: undefined",
                        "Clicked: Click me\nClicked: undefined")
                ),
                List.of(
                    "this in a method is the object before the dot at call time",
                    "Extracting a method loses its this — use bind to fix it",
                    "Arrow functions have no own this — they inherit from lexical scope",
                    "Use arrow functions for callbacks inside methods to preserve this",
                    "Do not use arrow functions as object methods — they will not have the object as this"
                ),
                "When you pass an object method as a callback, bind it first: element.addEventListener('click', this.handleClick.bind(this)). Otherwise this inside the method will be the element, not your object.",
                List.of(
                    "Using arrow functions as object methods — arrow functions capture outer this, not the object",
                    "Passing unbound methods as callbacks — this becomes undefined or window"
                ),
                18, 13, "C"),

            // ── 14. Rest and Spread Operators ─────────────────────────────────
            conceptRich(js, "Rest and Spread Operators",
                "The spread operator (...) expands an iterable into individual elements. The rest operator (...) collects multiple elements into an array. Same syntax, opposite purposes.",
                "The three dots (...) do two opposite things depending on context:\n\nSpread: takes one thing and expands it into many\n- [...array1, ...array2] — combine arrays\n- {...obj1, ...obj2} — merge objects\n- Math.max(...numbers) — pass array as individual args\n\nRest: takes many things and collects them into one\n- function sum(...numbers) — collect all args into array\n- const [first, ...rest] = array — collect remaining elements\n- const { name, ...others } = obj — collect remaining properties",
                "1. Spread in arrays:\n- [...arr1, ...arr2]: concatenate arrays\n- [...arr]: shallow clone an array\n- [...str]: spread string into characters\n\n2. Spread in objects:\n- {...obj1, ...obj2}: merge objects (later keys win)\n- {...obj}: shallow clone object\n- {...obj, key: newValue}: clone with override\n\n3. Spread in function calls:\n- fn(...arr): pass array elements as separate arguments\n- Math.max(...numbers) instead of Math.max.apply(null, numbers)\n\n4. Rest in function parameters:\n- function fn(first, second, ...rest)\n- rest collects ALL remaining arguments as an array\n- Must be the last parameter\n\n5. Rest in destructuring:\n- const [first, second, ...remaining] = array\n- const { name, ...otherProps } = obj\n- Useful for omitting specific properties",
                "// Spread — arrays\nconst a = [1, 2, 3];\nconst b = [4, 5, 6];\nconst combined = [...a, ...b];\nconsole.log(combined);  // [1, 2, 3, 4, 5, 6]\n\nconst copy = [...a];    // shallow clone\ncopy.push(99);\nconsole.log(a);   // [1, 2, 3] — original unchanged\n\n// Spread in function call\nconst nums = [5, 3, 8, 1, 9];\nconsole.log(Math.max(...nums));  // 9\n\n// Spread — objects\nconst defaults = { theme: 'dark', lang: 'en', size: 14 };\nconst userPrefs = { theme: 'light', size: 16 };\nconst final = { ...defaults, ...userPrefs };\nconsole.log(final);  // later keys override\n\n// Rest — parameters\nfunction logAll(label, ...items) {\n  console.log(label + ':', items.join(', '));\n}\nlogAll('Numbers', 1, 2, 3, 4);  // Numbers: 1, 2, 3, 4\n\n// Rest — destructuring\nconst [first, second, ...rest] = [10, 20, 30, 40, 50];\nconsole.log(first, second, rest);\n\nconst { name, age, ...profile } = { name: 'Ravi', age: 21, city: 'HYD', role: 'dev' };\nconsole.log(name, profile);",
                List.of(
                    new Concept.ConceptExample("Omit a property using rest destructuring",
                        "Use rest destructuring to create an object without a specific key.",
                        "// Remove 'password' before sending user to client\nconst userFromDB = {\n  id: 1,\n  name: 'Ravi',\n  email: 'ravi@example.com',\n  password: 'hashed_secret',\n  role: 'admin'\n};\n\nconst { password, ...safeUser } = userFromDB;\nconsole.log('Safe to send:', safeUser);\nconsole.log('Password omitted:', !safeUser.password);",
                        "Safe to send: { id: 1, name: 'Ravi', email: 'ravi@example.com', role: 'admin' }\nPassword omitted: true")
                ),
                List.of(
                    "Spread (...) expands — rest (...) collects. Same syntax, context determines which",
                    "Object spread is shallow — nested objects are still shared references",
                    "Rest parameter must be last: (a, b, ...rest) — not (a, ...rest, b)",
                    "Spread creates array/object clones — safer than direct assignment for mutation",
                    "Math.max(...arr) is the clean way to find max of an array"
                ),
                "Use rest destructuring to omit properties from an object cleanly. const { password, ...safeUser } = user gives you the user without the password — a common pattern when sending data to the client.",
                List.of(
                    "Using rest parameter in the middle of parameters — it must be last",
                    "Thinking spread does a deep clone — it only copies one level deep"
                ),
                15, 14, "C"),

            // ── 15. ES6 Features ──────────────────────────────────────────────
            conceptRich(js, "ES6 Features",
                "ES6 (ES2015) introduced major improvements: destructuring, template literals, arrow functions, classes, default parameters, shorthand syntax, computed keys, Symbol and more.",
                "ES6 (released 2015) was the biggest update to JavaScript since its creation. It introduced syntax that is now considered standard in all modern JavaScript code.\n\nYou have already seen several ES6 features — arrow functions, let/const, template literals, destructuring, spread/rest. This concept covers the remaining essential ES6 features, particularly classes and symbols.\n\nES6+ features are now universally supported in all modern browsers and are standard in every professional JavaScript project.",
                "1. Classes (ES6):\n- Cleaner syntax for prototype-based OOP\n- class ClassName { constructor() {} method() {} }\n- extends for inheritance, super() to call parent\n- static methods, getter/setter\n\n2. Destructuring:\n- Array: const [a, b] = [1, 2]\n- Object: const { name, age } = user\n- In parameters: function fn({ name, age }) {}\n- Nested: const { address: { city } } = user\n\n3. Enhanced object literals:\n- Shorthand: { name } instead of { name: name }\n- Method shorthand: { greet() {} } instead of { greet: function() {} }\n- Computed keys: { [dynamicKey]: value }\n\n4. Symbol:\n- const id = Symbol('description')\n- Every Symbol is unique: Symbol('a') !== Symbol('a')\n- Used as unique object keys to avoid collisions\n\n5. Iterators and generators (preview):\n- for...of works on any iterable object\n- Objects with [Symbol.iterator] are iterable\n\n6. Modules (preview — covered in detail in JS Advanced):\n- export and import keywords\n- Named exports and default exports",
                "// Classes\nclass Animal {\n  constructor(name, sound) {\n    this.name = name;\n    this.sound = sound;\n  }\n  speak() {\n    return `${this.name} says ${this.sound}`;\n  }\n  static create(name, sound) {\n    return new Animal(name, sound);\n  }\n}\n\nclass Dog extends Animal {\n  constructor(name) {\n    super(name, 'Woof');\n    this.tricks = [];\n  }\n  learn(trick) {\n    this.tricks.push(trick);\n    return this;\n  }\n}\n\nconst d = new Dog('Tommy');\nd.learn('sit').learn('fetch');\nconsole.log(d.speak());\nconsole.log(d.tricks);\n\n// Destructuring in function params\nfunction displayUser({ name, age, role = 'student' }) {\n  console.log(`${name} (${age}) - ${role}`);\n}\ndisplayUser({ name: 'Ravi', age: 21 });\n\n// Symbol — unique keys\nconst ID = Symbol('id');\nconst user = { [ID]: 123, name: 'Ravi' };\nconsole.log(user[ID]);    // 123\nconsole.log(user.name);   // Ravi\n// Symbol key does not appear in for...in or Object.keys",
                List.of(
                    new Concept.ConceptExample("Class with getter and setter",
                        "Use getter and setter to add validation to class properties.",
                        "class Temperature {\n  #celsius;  // private field (ES2022)\n\n  constructor(celsius) {\n    this.celsius = celsius;\n  }\n\n  get celsius() { return this.#celsius; }\n  set celsius(value) {\n    if (value < -273.15) throw new Error('Below absolute zero');\n    this.#celsius = value;\n  }\n\n  get fahrenheit() {\n    return this.#celsius * 9/5 + 32;\n  }\n}\n\nconst t = new Temperature(100);\nconsole.log(t.celsius);     // 100\nconsole.log(t.fahrenheit);  // 212\n\nt.celsius = 0;\nconsole.log(t.fahrenheit);  // 32",
                        "100\n212\n32")
                ),
                List.of(
                    "class syntax is syntactic sugar over JavaScript's prototype system",
                    "extends and super() enable inheritance — super() must be called in child constructor before this",
                    "static methods belong to the class, not instances — call with ClassName.method()",
                    "Symbol() always creates a unique value — two Symbols with same description are never equal",
                    "Symbol keys are hidden from Object.keys, for...in, and JSON.stringify"
                ),
                "Use class syntax for all object-oriented code in modern JavaScript. It is cleaner than prototype-based syntax and easier to read. Remember: under the hood it is still prototype-based — class is just cleaner syntax.",
                List.of(
                    "Forgetting super() in a child class constructor — throws ReferenceError before this is usable",
                    "Calling a static method on an instance — static methods are on the class, not instances"
                ),
                18, 15, "C"),

            // ── 16. Callbacks and Higher Order Functions ──────────────────────
            conceptRich(js, "Callbacks and Higher Order Functions",
                "A callback is a function passed as an argument to another function. A higher order function takes a function as input or returns a function. These are the foundation of asynchronous JavaScript.",
                "Functions are first-class citizens in JavaScript — they can be passed around like any other value.\n\nA callback is a function you hand to another function to call later:\n- setTimeout(callback, 1000) — call callback after 1 second\n- arr.map(callback) — call callback for each element\n- btn.addEventListener('click', callback) — call on click\n\nA higher order function (HOF) takes a function as argument or returns a function:\n- map, filter, reduce, forEach are all HOFs\n- setTimeout is a HOF\n- Decorators are HOFs\n\nUnderstanding callbacks is essential before learning Promises and async/await.",
                "1. Callbacks:\n- A function passed as an argument\n- Called by the receiving function at the right time\n- Can be named or anonymous\n- Synchronous callbacks: map, filter, forEach — called immediately\n- Asynchronous callbacks: setTimeout, event listeners, file reads — called later\n\n2. Higher order functions:\n- Takes a function as parameter OR returns a function\n- Array methods (map, filter, reduce) are all HOFs\n- Functions that return functions enable currying and closures\n\n3. Callback hell:\n- Deeply nested callbacks for async operations\n- Hard to read and maintain\n- Solved by Promises and async/await (in Advanced)\n\n4. Synchronous vs asynchronous callbacks:\n- Sync: runs immediately in the call stack\n- Async: scheduled to run later via the event queue\n\n5. Error-first callbacks (Node.js convention):\n- callback(error, data)\n- Check error first: if (err) return handleError(err)",
                "// Basic callback\nfunction greet(name, callback) {\n  const message = `Hello, ${name}!`;\n  callback(message);\n}\n\ngreet('Ravi', msg => console.log(msg));\n\n// Callbacks in array methods\nconst nums = [1, 2, 3, 4, 5];\nconst doubled = nums.map(function(n) { return n * 2; });\nconst evens = nums.filter(n => n % 2 === 0);\nconst sum = nums.reduce((acc, n) => acc + n, 0);\n\n// setTimeout — async callback\nconsole.log('Start');\nsetTimeout(() => console.log('After 1 second'), 1000);\nconsole.log('End');  // prints before the setTimeout callback!\n\n// Higher order function\nfunction repeat(fn, times) {\n  for (let i = 0; i < times; i++) fn(i);\n}\nrepeat(i => console.log('Repeat:', i), 3);\n\n// Function returning function\nfunction makeMultiplier(factor) {\n  return number => number * factor;\n}\nconst triple = makeMultiplier(3);\nconsole.log(triple(5));   // 15\nconsole.log(triple(10));  // 30",
                List.of(
                    new Concept.ConceptExample("Callback hell and why it happens",
                        "See how nested async callbacks become hard to read.",
                        "// Simulated async operation\nfunction fetchUser(id, callback) {\n  setTimeout(() => callback(null, { id, name: 'Ravi' }), 100);\n}\nfunction fetchPosts(userId, callback) {\n  setTimeout(() => callback(null, ['Post 1', 'Post 2']), 100);\n}\nfunction fetchComments(postId, callback) {\n  setTimeout(() => callback(null, ['Comment 1']), 100);\n}\n\n// Callback hell\nfetchUser(1, (err, user) => {\n  if (err) return console.error(err);\n  fetchPosts(user.id, (err, posts) => {\n    if (err) return console.error(err);\n    fetchComments(posts[0], (err, comments) => {\n      if (err) return console.error(err);\n      console.log(user.name, posts, comments);\n    });\n  });\n});\n// This pyramid of doom is solved by Promises in Advanced",
                        "Ravi ['Post 1', 'Post 2'] ['Comment 1']")
                ),
                List.of(
                    "Callbacks are functions passed to other functions to be called later",
                    "Synchronous callbacks run immediately (map, filter), async callbacks run later (setTimeout)",
                    "Higher order functions take or return functions — map, filter, reduce are all HOFs",
                    "Async callbacks do not block — setTimeout callback runs after current code finishes",
                    "Callback hell is deeply nested callbacks — solved by Promises in the Advanced subject"
                ),
                "When writing a function that does async work, always include an error parameter in the callback: callback(error, result). This is the standard Node.js convention and makes error handling consistent.",
                List.of(
                    "Expecting setTimeout(fn, 0) to run before the current code — it always runs after",
                    "Forgetting to handle the error parameter in callbacks"
                ),
                18, 16, "C"),

            // ── 17. DOM and DOM Methods ───────────────────────────────────────
            conceptRich(js, "DOM and DOM Methods",
                "The DOM (Document Object Model) is a tree of objects representing the HTML page. JavaScript can select, create, modify and delete DOM elements to make pages interactive.",
                "When a browser loads an HTML page, it builds a tree of JavaScript objects representing every element. This tree is the DOM.\n\nJavaScript can:\n- Select elements: querySelector, getElementById\n- Read and change content: textContent, innerHTML\n- Change styles: element.style, classList\n- Create new elements: createElement, appendChild\n- Remove elements: element.remove()\n- Read and set attributes: getAttribute, setAttribute\n\nDOM manipulation is how JavaScript makes web pages interactive — updating content, showing/hiding elements, changing styles based on user actions.",
                "1. Selecting elements:\n- document.getElementById('id'): by ID (fastest)\n- document.querySelector('selector'): first match of CSS selector\n- document.querySelectorAll('selector'): NodeList of all matches\n- document.getElementsByClassName('class'): HTMLCollection\n- element.children, element.parentElement, element.nextElementSibling\n\n2. Reading and modifying content:\n- element.textContent: plain text (safe, no HTML parsing)\n- element.innerHTML: HTML string (XSS risk with user input!)\n- element.value: for input elements\n\n3. Modifying styles:\n- element.style.property: inline style (camelCase: backgroundColor)\n- element.classList.add/remove/toggle/contains\n- element.className: full class string\n\n4. Creating and inserting elements:\n- document.createElement('tag'): create new element\n- parent.appendChild(child): add to end\n- parent.prepend(child): add to start\n- element.insertAdjacentHTML('position', html): flexible insert\n- parent.removeChild(child) or element.remove()\n\n5. Attributes:\n- element.getAttribute('attr')\n- element.setAttribute('attr', 'value')\n- element.removeAttribute('attr')\n- element.hasAttribute('attr')",
                "// Selecting\nconst title = document.getElementById('title');\nconst btn = document.querySelector('.submit-btn');\nconst items = document.querySelectorAll('li');\n\n// Reading and modifying content\ntitle.textContent = 'New Title';          // safe\ntitle.innerHTML = '<strong>Bold</strong>'; // renders HTML\n\n// Styles\nbtn.style.backgroundColor = '#4CAF50';\nbtn.style.color = 'white';\nbtn.style.padding = '10px 20px';\n\n// classList\ntitle.classList.add('active');\ntitle.classList.remove('hidden');\ntitle.classList.toggle('highlight');\nconsole.log(title.classList.contains('active')); // true\n\n// Create and insert\nconst newItem = document.createElement('li');\nnewItem.textContent = 'New Item';\nnewItem.classList.add('item');\ndocument.querySelector('ul').appendChild(newItem);\n\n// Remove\nconst oldItem = document.querySelector('.old');\nif (oldItem) oldItem.remove();\n\n// Attributes\nconst link = document.querySelector('a');\nlink.setAttribute('href', 'https://example.com');\nlink.setAttribute('target', '_blank');\nconsole.log(link.getAttribute('href'));",
                List.of(
                    new Concept.ConceptExample("Build a todo list item dynamically",
                        "Create DOM elements programmatically and append them to a list.",
                        "function createTodoItem(text) {\n  const li = document.createElement('li');\n  li.style.cssText = 'display:flex; gap:10px; padding:8px';\n\n  const span = document.createElement('span');\n  span.textContent = text;\n  span.style.flex = '1';\n\n  const deleteBtn = document.createElement('button');\n  deleteBtn.textContent = 'Delete';\n  deleteBtn.onclick = () => li.remove();\n\n  const doneBtn = document.createElement('button');\n  doneBtn.textContent = 'Done';\n  doneBtn.onclick = () => span.style.textDecoration = 'line-through';\n\n  li.append(span, doneBtn, deleteBtn);\n  return li;\n}\n\n// Usage (in browser):\n// const list = document.querySelector('#todo-list');\n// list.appendChild(createTodoItem('Learn JavaScript'));",
                        "DOM element created with text, done button and delete button")
                ),
                List.of(
                    "querySelector returns the first match, querySelectorAll returns all matches as NodeList",
                    "textContent is safe for user-generated content, innerHTML parses HTML — avoid with user input",
                    "classList.toggle adds the class if absent, removes if present",
                    "Always check if an element exists before calling methods — querySelector returns null if not found",
                    "style.property uses camelCase: backgroundColor not background-color"
                ),
                "Prefer classList over direct style manipulation. Adding/removing CSS classes is more maintainable — your styles stay in CSS, your logic stays in JavaScript.",
                List.of(
                    "Using innerHTML with user input — major XSS vulnerability. Always use textContent for user content",
                    "Forgetting to check if querySelector returned null before accessing properties"
                ),
                20, 17, "C"),

            // ── 18. Events and Event Listeners ────────────────────────────────
            conceptRich(js, "Events and Event Listeners",
                "Events are user or browser actions — clicks, key presses, form submissions, page loads. addEventListener registers a function to run when an event occurs.",
                "Events are the mechanism that makes web pages interactive.\n\nWhen a user clicks a button, types in an input, submits a form, scrolls the page — these are all events. JavaScript listens for these events and runs code in response.\n\nelement.addEventListener('event', handler) is the modern way to handle events. It is better than inline HTML event attributes (onclick=) or setting element.onclick because it allows multiple listeners and can be removed.",
                "1. addEventListener:\n- element.addEventListener('eventType', handler)\n- handler receives an Event object\n- Multiple listeners for same event are all called\n\n2. Common event types:\n- Mouse: click, dblclick, mouseenter, mouseleave, mousemove\n- Keyboard: keydown, keyup, keypress (deprecated)\n- Form: submit, input, change, focus, blur\n- Document: DOMContentLoaded, load\n- Window: resize, scroll\n\n3. Event object:\n- event.target: the element that triggered the event\n- event.currentTarget: the element the listener is on\n- event.type: event type string\n- event.preventDefault(): prevent default behaviour (form submit, link follow)\n- event.stopPropagation(): stop event bubbling up\n\n4. Event bubbling:\n- Events bubble up from target to document\n- A click on a child also triggers listeners on parent elements\n- stopPropagation() stops this\n\n5. Event delegation:\n- Add one listener on a parent instead of many on children\n- Check event.target to determine which child was clicked\n- More efficient for dynamic lists\n\n6. removeEventListener:\n- Must use the exact same function reference\n- Cannot remove anonymous functions",
                "// Basic event listener\nconst btn = document.querySelector('#myBtn');\nbtn.addEventListener('click', function(event) {\n  console.log('Clicked!', event.target);\n});\n\n// Arrow function handler\nbtn.addEventListener('mouseover', (e) => {\n  e.target.style.backgroundColor = 'lightblue';\n});\n\n// Form submit — prevent default\nconst form = document.querySelector('#myForm');\nform.addEventListener('submit', (e) => {\n  e.preventDefault();  // stop page refresh\n  const input = document.querySelector('#name');\n  console.log('Submitted:', input.value);\n});\n\n// Keyboard events\ndocument.addEventListener('keydown', (e) => {\n  console.log('Key:', e.key, 'Code:', e.code);\n  if (e.key === 'Escape') console.log('Close modal');\n});\n\n// Event delegation — one listener for all list items\nconst ul = document.querySelector('ul');\nul.addEventListener('click', (e) => {\n  if (e.target.tagName === 'LI') {\n    e.target.classList.toggle('completed');\n    console.log('Toggled:', e.target.textContent);\n  }\n});\n\n// Remove listener — must use named function\nfunction handleClick() { console.log('Click'); }\nbtn.addEventListener('click', handleClick);\nbtn.removeEventListener('click', handleClick);  // removed",
                List.of(
                    new Concept.ConceptExample("Event delegation for a dynamic list",
                        "Handle clicks on dynamically added items using one parent listener.",
                        "// HTML structure:\n// <ul id=\"list\">\n//   <li data-id=\"1\">Item 1 <button class=\"del\">X</button></li>\n// </ul>\n\nconst list = document.querySelector('#list') || document.createElement('ul');\n\nlist.addEventListener('click', (e) => {\n  // Handle delete button click\n  if (e.target.classList.contains('del')) {\n    const li = e.target.closest('li');\n    console.log('Deleting item:', li?.dataset.id);\n    li?.remove();\n    return;\n  }\n\n  // Handle item click\n  if (e.target.tagName === 'LI' || e.target.closest('li')) {\n    const li = e.target.closest('li');\n    li.classList.toggle('done');\n    console.log('Toggled:', li.dataset.id);\n  }\n});\nconsole.log('Event delegation set up on parent');",
                        "Event delegation set up on parent")
                ),
                List.of(
                    "addEventListener is preferred over onclick property — allows multiple handlers",
                    "event.target is the element that was clicked; event.currentTarget is where the listener is",
                    "event.preventDefault() stops default actions: form submit, link navigation, checkbox toggle",
                    "Event bubbling means child events propagate up to parents — use stopPropagation() to stop",
                    "Event delegation: add one listener on parent, check event.target — much more efficient for lists"
                ),
                "Use event delegation whenever you have a list of items that can be added or removed dynamically. Adding individual listeners to each item means you have to add a listener every time a new item is created. One parent listener handles everything, including future items.",
                List.of(
                    "Adding listeners inside loops — creates many listeners, use event delegation instead",
                    "Trying to removeEventListener with an anonymous function — always use a named function reference",
                    "Forgetting event.preventDefault() on form submit — page reloads and you lose the data"
                ),
                20, 18, "C"),

            // ── 19. BOM and BOM Methods ───────────────────────────────────────
            conceptRich(js, "BOM and BOM Methods",
                "The BOM (Browser Object Model) provides objects to interact with the browser window: navigation, location, history, screen, timers and dialogs.",
                "While the DOM gives you access to the page content, the BOM gives you access to the browser itself.\n\nThe BOM includes:\n- window: the global object — all global variables and functions are properties of window\n- navigator: information about the browser and device\n- location: current URL — read and redirect\n- history: browser history navigation\n- screen: screen dimensions\n- Timers: setTimeout, setInterval, clearTimeout, clearInterval\n- Dialogs: alert, confirm, prompt (avoid in production)",
                "1. window object:\n- Global object in browsers — window.alert is the same as alert\n- window.innerWidth, window.innerHeight: viewport dimensions\n- window.scrollX, window.scrollY: current scroll position\n- window.scrollTo(x, y) or scrollTo({ top: y, behavior: 'smooth' })\n- window.open(url): open new tab\n\n2. navigator:\n- navigator.userAgent: browser identification string\n- navigator.language: user's language\n- navigator.onLine: whether browser has network\n- navigator.geolocation: get GPS coordinates\n\n3. location:\n- location.href: full URL — set to navigate\n- location.hostname, pathname, search, hash\n- location.reload(): refresh page\n- location.replace(url): navigate without history entry\n\n4. history:\n- history.back(): go back\n- history.forward(): go forward\n- history.go(n): go n steps\n- history.pushState(state, title, url): add history entry without navigation\n\n5. Timers:\n- setTimeout(fn, ms): run once after delay\n- setInterval(fn, ms): run repeatedly\n- clearTimeout(id), clearInterval(id): cancel timers\n- Always store the timer ID to cancel it\n\n6. Storage (preview — full detail in Advanced):\n- localStorage: persists forever\n- sessionStorage: persists for browser session",
                "// window\nconsole.log(window.innerWidth, window.innerHeight);\n\n// Smooth scroll\nwindow.scrollTo({ top: 0, behavior: 'smooth' });\n\n// navigator\nconsole.log(navigator.language);   // e.g. 'en-US'\nconsole.log(navigator.onLine);     // true/false\n\n// location\nconsole.log(location.href);        // full URL\nconsole.log(location.pathname);    // e.g. '/dashboard'\nconsole.log(location.search);      // e.g. '?id=123'\n\n// Navigate\n// location.href = 'https://example.com';\n\n// setTimeout — run once\nconst timerId = setTimeout(() => {\n  console.log('Runs after 2 seconds');\n}, 2000);\n\n// Cancel before it fires\nclearTimeout(timerId);\n\n// setInterval — run repeatedly\nlet count = 0;\nconst intervalId = setInterval(() => {\n  count++;\n  console.log('Tick', count);\n  if (count >= 3) {\n    clearInterval(intervalId);  // stop after 3\n    console.log('Stopped');\n  }\n}, 1000);\n\n// history\nhistory.pushState({ page: 1 }, '', '/page1');\nconsole.log(location.pathname);  // /page1 (no actual navigation)",
                List.of(
                    new Concept.ConceptExample("Countdown timer using setInterval",
                        "Build a countdown that stops when it reaches zero.",
                        "function countdown(seconds) {\n  let remaining = seconds;\n\n  console.log(`Starting countdown from ${seconds}...`);\n\n  const intervalId = setInterval(() => {\n    console.log(remaining);\n    remaining--;\n\n    if (remaining < 0) {\n      clearInterval(intervalId);\n      console.log('Time up!');\n    }\n  }, 1000);\n\n  return intervalId; // caller can cancel if needed\n}\n\ncountdown(3);",
                        "Starting countdown from 3...\n3\n2\n1\n0\nTime up!")
                ),
                List.of(
                    "Always store timer IDs from setTimeout/setInterval so you can cancel them",
                    "clearInterval must be called with the ID returned by setInterval",
                    "location.replace(url) does not add to history — user cannot go back with browser back button",
                    "history.pushState changes the URL without a page reload — used in single page apps",
                    "navigator.onLine can be false even with internet if behind certain firewalls — verify with a real request"
                ),
                "Always clear intervals when they are no longer needed — especially when a component is removed or a page changes in a single page app. Forgetting to clear setInterval causes memory leaks and unexpected behaviour after navigation.",
                List.of(
                    "Forgetting to store the interval ID — then you cannot clear it",
                    "Using alert/confirm/prompt in production — they block the browser thread and look unprofessional"
                ),
                18, 19, "C")
        );

        conceptRepository.saveAll(concepts);
        js.setTotalConcepts(concepts.size());
        subjectRepository.save(js);
        System.out.println("✅ JavaScript Basics seeded — " + concepts.size() + " concepts");
    }

    // ─── PYTHON OOP ───────────────────────────────────────────────────────────
    private void seedPythonOOP() {
        Subject py = subjectRepository.save(sub(
            "Python OOP",
            "Learn Object Oriented Programming — classes, inheritance, encapsulation, abstract classes, and magic methods",
            "🏗️", "#10B981", "C"
        ));
        py.setOverview("Object Oriented Programming organises code into classes and objects. Instead of writing functions that operate on separate data, OOP bundles data and behaviour together. Python's OOP includes classes, inheritance, encapsulation, polymorphism, abstract classes, and special dunder methods. This subject builds directly on Python Basics.");
        py.setWhyLearn("Every Python framework uses OOP — Django models, Flask views, SQLAlchemy, PyTest. Backend developer and software engineer roles expect you to design and write class-based code. Interviews regularly include OOP design questions.");
        py.setForWho("Students who have completed Python Basics. You should be comfortable with functions, loops, and data structures before starting OOP.");
        py.setPrerequisites(List.of("Python Basics completed", "Comfortable with functions and data structures"));
        py.setOutcomes(List.of(
            "Define classes and create objects with attributes and methods",
            "Use inheritance and super() to reuse and extend code",
            "Apply encapsulation with private and protected members",
            "Use @property for controlled attribute access",
            "Define abstract classes using abc module",
            "Use dunder methods to make objects behave like built-in types"
        ));
        py.setWhatYouWillBuild(List.of(
            "A BankAccount class with deposit, withdraw and balance methods",
            "An inheritance hierarchy: Animal -> Dog, Cat with overridden methods",
            "A Shape abstract class with Circle and Rectangle implementations"
        ));
        py.setToolsRequired(List.of("Python 3.x", "VS Code with Python extension"));
        py.setDifficulty("Intermediate");
        py.setEstimatedHours(10);
        py.setCareerUse("OOP is required for backend development, Django/Flask projects, and almost every software engineering role. Design patterns, system design interviews, and framework usage all require strong OOP knowledge.");
        subjectRepository.save(py);

        List<Concept> concepts = List.of(

            conceptRich(py, "Classes and Objects",
                "A class is a blueprint for creating objects. An object is an instance of a class with its own data and behaviour.",
                "Think of a class as a cookie cutter and objects as the cookies.\n\nThe cookie cutter defines the shape — every cookie made from it has the same shape. But each cookie can have different toppings (data).\n\nIn Python:\n- A class defines what attributes (data) and methods (behaviour) an object will have\n- An object is a specific instance created from that class\n- You can create many objects from one class, each with their own data\n\nFor example, a Student class defines that every student has a name, roll number, and marks. Each actual student is an object with their own specific name, roll number, and marks.",
                "1. Defining a class:\n- Use the class keyword followed by the class name in PascalCase\n- The class body contains attributes and methods\n\n2. Creating an object:\n- Call the class like a function: student1 = Student()\n- This creates a new instance of the class\n\n3. Attributes:\n- Variables that belong to an object\n- Set using dot notation: student1.name = 'Ravi'\n- Or defined in __init__ (covered in next concept)\n\n4. Methods:\n- Functions defined inside a class\n- First parameter is always self (refers to the current object)\n\n5. The self parameter:\n- self refers to the specific object calling the method\n- Python passes it automatically when you call obj.method()",
                "# Define a class\nclass Student:\n    def greet(self):\n        print('Hello, I am a student')\n\n    def study(self, subject):\n        print(f'Studying {subject}')\n\n# Create objects\nstudent1 = Student()\nstudent2 = Student()\n\n# Call methods\nstudent1.greet()\nstudent2.study('Python')\n\n# Set attributes directly\nstudent1.name = 'Ravi'\nstudent1.marks = 85\nprint(student1.name, student1.marks)\n\n# Check type\nprint(type(student1))           # <class '__main__.Student'>\nprint(isinstance(student1, Student))  # True",
                List.of(
                    new Concept.ConceptExample("BankAccount class",
                        "A simple class with attributes and methods.",
                        "class BankAccount:\n    def deposit(self, amount):\n        self.balance += amount\n        print(f'Deposited {amount}. Balance: {self.balance}')\n\n    def withdraw(self, amount):\n        if amount <= self.balance:\n            self.balance -= amount\n            print(f'Withdrew {amount}. Balance: {self.balance}')\n        else:\n            print('Insufficient funds')\n\nacc = BankAccount()\nacc.balance = 1000\nacc.deposit(500)\nacc.withdraw(200)\nacc.withdraw(2000)",
                        "Deposited 500. Balance: 1500\nWithdrew 200. Balance: 1300\nInsufficient funds"),
                    new Concept.ConceptExample("Multiple objects from one class",
                        "Each object is independent with its own data.",
                        "class Car:\n    def info(self):\n        print(f'{self.brand} {self.model} - {self.year}')\n\ncar1 = Car()\ncar1.brand = 'Toyota'\ncar1.model = 'Innova'\ncar1.year = 2022\n\ncar2 = Car()\ncar2.brand = 'Honda'\ncar2.model = 'City'\ncar2.year = 2023\n\ncar1.info()\ncar2.info()",
                        "Toyota Innova - 2022\nHonda City - 2023")
                ),
                List.of(
                    "Class names use PascalCase: Student, BankAccount, UserProfile",
                    "self is the first parameter of every method — it refers to the object calling the method",
                    "Each object is independent — changing one object does not affect other objects of the same class",
                    "isinstance(obj, ClassName) checks if an object is an instance of a class"
                ),
                "Always use PascalCase for class names and snake_case for method names. This is PEP 8 convention and every Python developer follows it.",
                List.of(
                    "Forgetting self as the first parameter of a method — Python raises TypeError when calling it",
                    "Using lowercase for class names — not an error but breaks Python convention",
                    "Confusing the class itself with an object — Student is the class, Student() creates an object"
                ),
                20, 1, "D"),

            conceptRich(py, "__init__ and self",
                "__init__ is the constructor method that runs automatically when an object is created. self refers to the current object instance.",
                "When you create a Student object, you usually want to set their name, roll number, and marks right away — not as separate steps after creation.\n\n__init__ is Python's constructor. It runs automatically the moment you create an object and lets you set up initial data.\n\nself is how the object refers to itself. When you write self.name = name inside __init__, you are saying: this specific object's name attribute should be set to the value passed in.\n\nWithout __init__, you would have to manually set every attribute after creating each object. With __init__, the object is ready to use immediately.",
                "1. __init__ method:\n- Special method called automatically when object is created\n- Used to initialise the object's attributes\n- Parameters after self are passed when creating the object\n\n2. self:\n- Refers to the current instance of the class\n- Must be the first parameter of every method\n- Python passes it automatically — you do not pass it manually\n- Allows each object to store its own data\n\n3. Instance attributes:\n- self.name, self.age etc. are instance attributes\n- Each object has its own copy of these\n\n4. __init__ with default values:\n- Parameters can have defaults: def __init__(self, name, role='Student')\n\n5. __str__ preview:\n- Define __str__ to control how object prints (covered in dunder methods)",
                "class Student:\n    def __init__(self, name, roll_no, marks):\n        self.name = name\n        self.roll_no = roll_no\n        self.marks = marks\n\n    def get_grade(self):\n        if self.marks >= 90:\n            return 'A'\n        elif self.marks >= 75:\n            return 'B'\n        elif self.marks >= 60:\n            return 'C'\n        else:\n            return 'D'\n\n    def info(self):\n        print(f'{self.name} ({self.roll_no}) - Marks: {self.marks}, Grade: {self.get_grade()}')\n\n# Create objects — __init__ runs automatically\ns1 = Student('Ravi', 'CS001', 85)\ns2 = Student('Priya', 'CS002', 92)\ns3 = Student('Arjun', 'CS003', 58)\n\ns1.info()\ns2.info()\ns3.info()\n\n# Access attributes\nprint(s1.name)\nprint(s2.marks)",
                List.of(
                    new Concept.ConceptExample("BankAccount with __init__",
                        "Use __init__ to set up an account with owner and initial balance.",
                        "class BankAccount:\n    def __init__(self, owner, balance=0):\n        self.owner = owner\n        self.balance = balance\n        self.transactions = []\n\n    def deposit(self, amount):\n        self.balance += amount\n        self.transactions.append(f'+ {amount}')\n\n    def withdraw(self, amount):\n        if amount <= self.balance:\n            self.balance -= amount\n            self.transactions.append(f'- {amount}')\n        else:\n            print('Insufficient funds')\n\n    def statement(self):\n        print(f'Account: {self.owner}')\n        for t in self.transactions:\n            print(f'  {t}')\n        print(f'Balance: {self.balance}')\n\nacc = BankAccount('Ravi', 5000)\nacc.deposit(2000)\nacc.withdraw(1500)\nacc.statement()",
                        "Account: Ravi\n  + 2000\n  - 1500\nBalance: 5500"),
                    new Concept.ConceptExample("Default parameters in __init__",
                        "Provide default values so some arguments are optional.",
                        "class Employee:\n    def __init__(self, name, department, salary=25000):\n        self.name = name\n        self.department = department\n        self.salary = salary\n\n    def details(self):\n        print(f'{self.name} | {self.department} | Rs.{self.salary}')\n\ne1 = Employee('Ravi', 'Engineering', 45000)\ne2 = Employee('Priya', 'Design')   # uses default salary\n\ne1.details()\ne2.details()",
                        "Ravi | Engineering | Rs.45000\nPriya | Design | Rs.25000")
                ),
                List.of(
                    "__init__ runs automatically when you create an object — you never call it manually",
                    "self.attribute creates an instance attribute — each object has its own copy",
                    "self is passed automatically by Python — do not pass it when calling methods",
                    "Default parameter values in __init__ make some arguments optional"
                ),
                "Name your __init__ parameters the same as your attributes: def __init__(self, name, age) with self.name = name, self.age = age. This makes the code clear and consistent.",
                List.of(
                    "Forgetting self in method definition — causes TypeError when called",
                    "Calling __init__ manually — never do this, Python calls it automatically",
                    "Using a mutable default like [] in __init__ parameter — all objects share the same list"
                ),
                15, 2, "D"),

            conceptRich(py, "Instance vs Class Variables",
                "Instance variables belong to each individual object. Class variables are shared across all objects of the class.",
                "Imagine a school where every student has their own marks (different for each student) but all students share the same school name.\n\nIn Python:\n- Instance variables are unique to each object — defined with self.variable inside methods\n- Class variables are shared by all objects — defined directly in the class body\n\nIf you change a class variable, it changes for all objects. If you change an instance variable, it only changes for that one object.",
                "1. Class variables:\n- Defined at class level, outside any method\n- Shared across all instances\n- Accessed as ClassName.variable or self.variable\n- Useful for: counters, constants, shared configuration\n\n2. Instance variables:\n- Defined inside methods using self.variable\n- Each object has its own copy\n- Defined in __init__ or any method\n\n3. Name conflict:\n- If an instance variable has the same name as a class variable, the instance variable takes priority for that object\n- The class variable is not changed\n\n4. Checking with __dict__:\n- obj.__dict__ shows all instance variables of that object\n- ClassName.__dict__ shows class-level attributes",
                "class Student:\n    school_name = 'Python Academy'   # class variable\n    count = 0                         # class variable\n\n    def __init__(self, name, marks):\n        self.name = name              # instance variable\n        self.marks = marks            # instance variable\n        Student.count += 1\n\n# Create objects\ns1 = Student('Ravi', 85)\ns2 = Student('Priya', 92)\ns3 = Student('Arjun', 78)\n\n# Class variable — same for all\nprint(s1.school_name)   # Python Academy\nprint(s2.school_name)   # Python Academy\nprint(Student.count)    # 3\n\n# Instance variables — unique per object\nprint(s1.name, s1.marks)   # Ravi 85\nprint(s2.name, s2.marks)   # Priya 92\n\n# Changing class variable\nStudent.school_name = 'Code Academy'\nprint(s1.school_name)   # Code Academy (all affected)\n\n# Instance __dict__\nprint(s1.__dict__)",
                List.of(
                    new Concept.ConceptExample("Object counter using class variable",
                        "Track how many objects have been created using a class variable.",
                        "class Connection:\n    active_count = 0\n\n    def __init__(self, host):\n        self.host = host\n        Connection.active_count += 1\n\n    def close(self):\n        Connection.active_count -= 1\n        print(f'Connection to {self.host} closed')\n\nc1 = Connection('db-server-1')\nc2 = Connection('db-server-2')\nc3 = Connection('cache-server')\n\nprint('Active connections:', Connection.active_count)\n\nc2.close()\nprint('Active connections:', Connection.active_count)",
                        "Active connections: 3\nConnection to db-server-2 closed\nActive connections: 2")
                ),
                List.of(
                    "Class variables are shared — changing via ClassName.var affects all objects",
                    "Instance variables are unique — defined with self.var inside methods",
                    "If an object has an instance variable with the same name as a class variable, the instance variable wins for that object",
                    "Use class variables for counters, constants, and data shared across all instances"
                ),
                "Use class variables for things that are the same for every object of the class — like a counter, a configuration value, or a constant. Use instance variables for data that differs per object.",
                List.of(
                    "Modifying a mutable class variable (like a list) via an instance — this changes it for all instances",
                    "Confusing ClassName.var (class variable) with self.var (instance variable)"
                ),
                15, 3, "D"),

            conceptRich(py, "Instance Methods",
                "Instance methods are functions defined inside a class that operate on an object's data using self.",
                "Methods are what make objects useful — they define what an object can DO.\n\nAn instance method:\n- Is defined inside the class\n- Takes self as the first parameter\n- Can read and modify the object's attributes\n- Is called on an object: obj.method()\n\nMethods can call other methods using self.other_method(). This lets you build complex behaviour from simple building blocks.",
                "1. Defining instance methods:\n- def method_name(self, params):\n- self gives access to the object's attributes and other methods\n\n2. Calling methods:\n- obj.method() — Python automatically passes the object as self\n- Inside the class: self.method()\n\n3. Getter and setter methods (before @property):\n- get_name(self): returns self.name\n- set_name(self, name): validates and sets self.name\n\n4. Methods calling methods:\n- self.other_method() inside a method calls another method of the same object\n\n5. Method chaining (returning self):\n- return self from a method to allow chaining: obj.method1().method2()",
                "class BankAccount:\n    def __init__(self, owner, balance=0):\n        self.owner = owner\n        self.balance = balance\n\n    def deposit(self, amount):\n        if amount > 0:\n            self.balance += amount\n            return True\n        return False\n\n    def withdraw(self, amount):\n        if 0 < amount <= self.balance:\n            self.balance -= amount\n            return True\n        return False\n\n    def get_balance(self):\n        return self.balance\n\n    def transfer(self, other_account, amount):\n        if self.withdraw(amount):\n            other_account.deposit(amount)\n            print(f'Transferred {amount} from {self.owner} to {other_account.owner}')\n        else:\n            print('Transfer failed — insufficient funds')\n\nacc1 = BankAccount('Ravi', 5000)\nacc2 = BankAccount('Priya', 2000)\n\nacc1.deposit(1000)\nacc1.transfer(acc2, 2000)\nprint(f'Ravi: {acc1.get_balance()}')\nprint(f'Priya: {acc2.get_balance()}')",
                List.of(
                    new Concept.ConceptExample("Methods calling other methods",
                        "A method can call other methods of the same object using self.",
                        "class ShoppingCart:\n    def __init__(self):\n        self.items = []\n\n    def add_item(self, name, price):\n        self.items.append({'name': name, 'price': price})\n\n    def get_total(self):\n        return sum(item['price'] for item in self.items)\n\n    def apply_discount(self, percent):\n        total = self.get_total()   # calls another method\n        discount = total * percent / 100\n        return total - discount\n\n    def summary(self):\n        for item in self.items:\n            print(f\"  {item['name']}: Rs.{item['price']}\")\n        print(f'Total: Rs.{self.get_total()}')\n\ncart = ShoppingCart()\ncart.add_item('Book', 350)\ncart.add_item('Pen', 50)\ncart.add_item('Bag', 800)\ncart.summary()\nprint(f'After 10% discount: Rs.{cart.apply_discount(10)}')",
                        "  Book: Rs.350\n  Pen: Rs.50\n  Bag: Rs.800\nTotal: Rs.1200\nAfter 10% discount: Rs.1080.0")
                ),
                List.of(
                    "self must be the first parameter of every instance method",
                    "Methods can access and modify instance variables using self.variable",
                    "Methods can call other methods of the same class using self.method()",
                    "Always validate input inside methods before modifying attributes"
                ),
                "Keep methods small and focused on one job. A method named deposit should only handle depositing. Logic that belongs together can be pulled into helper methods called via self.helper().",
                List.of(
                    "Forgetting self in the method call inside the class — write self.method() not just method()",
                    "Making methods too long with multiple responsibilities — split into smaller methods"
                ),
                15, 4, "D"),

            conceptRich(py, "Inheritance and super()",
                "Inheritance lets a child class reuse and extend the behaviour of a parent class. super() calls the parent class methods from the child.",
                "Inheritance is like a family tree.\n\nA parent class defines common attributes and methods. A child class inherits everything from the parent and can add new things or change existing ones.\n\nFor example:\n- Animal class has: name, sound(), eat()\n- Dog inherits from Animal and gets name, eat() for free\n- Dog can also have its own fetch() method\n- Dog can change how sound() works\n\nsuper() lets the child class call the parent's version of a method. Most commonly used in __init__ to initialise the parent's attributes before adding child-specific ones.",
                "1. Defining inheritance:\n- class Child(Parent): inherits everything from Parent\n\n2. What the child gets:\n- All attributes and methods from the parent\n- Can override (replace) any method\n- Can add new methods\n\n3. super():\n- super().__init__() calls the parent's __init__\n- Always call super().__init__() first in child __init__ when parent has attributes\n- super().method() calls parent version of any method\n\n4. isinstance() with inheritance:\n- isinstance(dog, Dog) is True\n- isinstance(dog, Animal) is also True — dog is both a Dog and an Animal\n\n5. Method Resolution Order (MRO):\n- Python looks for methods: child first, then parent, then grandparent\n- ClassName.__mro__ shows the lookup order",
                "class Animal:\n    def __init__(self, name, species):\n        self.name = name\n        self.species = species\n\n    def eat(self):\n        print(f'{self.name} is eating')\n\n    def sound(self):\n        print(f'{self.name} makes a sound')\n\nclass Dog(Animal):\n    def __init__(self, name, breed):\n        super().__init__(name, 'Canis lupus')  # call parent __init__\n        self.breed = breed\n\n    def sound(self):           # override parent method\n        print(f'{self.name} barks')\n\n    def fetch(self):           # new method\n        print(f'{self.name} fetches the ball')\n\nclass Cat(Animal):\n    def __init__(self, name):\n        super().__init__(name, 'Felis catus')\n\n    def sound(self):\n        print(f'{self.name} meows')\n\nd = Dog('Tommy', 'Labrador')\nc = Cat('Whiskers')\n\nd.eat()     # inherited from Animal\nd.sound()   # overridden in Dog\nd.fetch()   # Dog-specific\nc.sound()   # overridden in Cat\n\nprint(isinstance(d, Dog))     # True\nprint(isinstance(d, Animal))  # True",
                List.of(
                    new Concept.ConceptExample("Employee hierarchy",
                        "A Manager inherits from Employee and adds extra behaviour.",
                        "class Employee:\n    def __init__(self, name, salary):\n        self.name = name\n        self.salary = salary\n\n    def details(self):\n        print(f'{self.name} - Rs.{self.salary}/month')\n\n    def work(self):\n        print(f'{self.name} is working')\n\nclass Manager(Employee):\n    def __init__(self, name, salary, team_size):\n        super().__init__(name, salary)\n        self.team_size = team_size\n\n    def details(self):\n        super().details()   # call parent method\n        print(f'  Manages {self.team_size} people')\n\n    def conduct_meeting(self):\n        print(f'{self.name} is conducting a team meeting')\n\ne = Employee('Ravi', 30000)\nm = Manager('Priya', 60000, 8)\n\ne.details()\nprint()\nm.details()\nm.conduct_meeting()",
                        "Ravi - Rs.30000/month\n\nPriya - Rs.60000/month\n  Manages 8 people\nPriya is conducting a team meeting")
                ),
                List.of(
                    "Call super().__init__() in child __init__ to initialise parent attributes",
                    "Child class gets all parent methods automatically — only override what needs to change",
                    "isinstance(obj, Parent) returns True for child objects too",
                    "super() always refers to the parent in the MRO — not necessarily the direct parent"
                ),
                "Always call super().__init__() as the first line in a child class __init__. This ensures the parent object is properly set up before you add child-specific attributes.",
                List.of(
                    "Forgetting to call super().__init__() — parent attributes are not initialised",
                    "Re-defining all parent methods in child — only override what needs to change",
                    "Confusing inheritance with composition — not every relationship should be parent-child"
                ),
                20, 5, "C"),

            conceptRich(py, "Multiple Inheritance and MRO",
                "Python supports multiple inheritance — a class can inherit from more than one parent. MRO (Method Resolution Order) defines which parent's method is called when names conflict.",
                "Sometimes a class needs behaviour from two different parents.\n\nFor example, a FlyingCar is both a Car and a Plane. It should have drive() from Car and fly() from Plane.\n\nPython allows this with multiple inheritance: class FlyingCar(Car, Plane).\n\nBut when both parents have a method with the same name, Python needs a rule to decide which one to use. This rule is called MRO — Method Resolution Order. Python always follows the C3 linearisation algorithm — child first, then left parent, then right parent, then their parents.",
                "1. Multiple inheritance syntax:\n- class Child(Parent1, Parent2): inherits from both\n\n2. MRO — Method Resolution Order:\n- Python searches: Child → Parent1 → Parent2 → object\n- Always left to right, child before parent\n- View with ClassName.__mro__ or ClassName.mro()\n\n3. super() in multiple inheritance:\n- super() follows the MRO — not just the direct parent\n- Ensures every class in the hierarchy gets initialised once\n\n4. Diamond problem:\n- When two parents share a grandparent, Python's MRO prevents calling grandparent's __init__ twice\n\n5. Mixin pattern:\n- A Mixin is a class designed to add specific behaviour via multiple inheritance\n- LogMixin, SerializeMixin, ValidateMixin are common patterns\n- Mixins have no __init__ and no standalone use",
                "class Flyable:\n    def fly(self):\n        print(f'{self.name} is flying')\n\nclass Swimmable:\n    def swim(self):\n        print(f'{self.name} is swimming')\n\nclass Duck(Flyable, Swimmable):\n    def __init__(self, name):\n        self.name = name\n\n    def quack(self):\n        print(f'{self.name} says quack')\n\nd = Duck('Donald')\nd.fly()\nd.swim()\nd.quack()\n\n# MRO\nprint(Duck.__mro__)\n\n# Mixin pattern\nclass LogMixin:\n    def log(self, message):\n        print(f'[LOG] {self.__class__.__name__}: {message}')\n\nclass UserService(LogMixin):\n    def create_user(self, name):\n        self.log(f'Creating user: {name}')\n        print(f'User {name} created')\n\nservice = UserService()\nservice.create_user('Ravi')",
                List.of(
                    new Concept.ConceptExample("Mixin for reusable behaviour",
                        "Add logging and validation to any class using Mixins.",
                        "class ValidateMixin:\n    def validate_positive(self, value, field):\n        if value < 0:\n            raise ValueError(f'{field} cannot be negative')\n        return True\n\nclass LogMixin:\n    def log(self, msg):\n        print(f'[{self.__class__.__name__}] {msg}')\n\nclass BankAccount(ValidateMixin, LogMixin):\n    def __init__(self, owner, balance=0):\n        self.validate_positive(balance, 'balance')\n        self.owner = owner\n        self.balance = balance\n\n    def deposit(self, amount):\n        self.validate_positive(amount, 'amount')\n        self.balance += amount\n        self.log(f'Deposited {amount}')\n\nacc = BankAccount('Ravi', 1000)\nacc.deposit(500)\nprint('Balance:', acc.balance)",
                        "[BankAccount] Deposited 500\nBalance: 1500")
                ),
                List.of(
                    "MRO order: child first, then left parent, then right parent (left to right, depth first)",
                    "View MRO with ClassName.__mro__ or ClassName.mro()",
                    "Mixins add reusable behaviour without representing a real-world entity",
                    "super() in multiple inheritance follows the MRO — not just the immediate parent"
                ),
                "Prefer Mixins over complex multiple inheritance. A Mixin adds a specific feature (logging, serialisation, validation) without being a full parent class. Keep Mixins focused on one capability.",
                List.of(
                    "Assuming super() always calls the direct parent — it follows MRO which may skip a class",
                    "Creating deep multiple inheritance chains — they become hard to debug"
                ),
                15, 6, "C"),

            conceptRich(py, "Method Overriding and Polymorphism",
                "Method overriding lets a child class replace a parent method with its own version. Polymorphism means different objects respond to the same method call in different ways.",
                "Polymorphism means many forms.\n\nImagine you call sound() on different animals — a Dog barks, a Cat meows, a Cow moos. The method name is the same but each animal responds differently.\n\nThis is polymorphism. You can write code that calls sound() on any animal without knowing or caring what type it is. Each object handles it in its own way.\n\nMethod overriding is how you implement this — the child class defines its own version of the parent's method.",
                "1. Method overriding:\n- Define a method in the child class with the same name as the parent\n- Child's version completely replaces the parent's version for that class\n- Call parent version with super().method() if needed\n\n2. Polymorphism:\n- Different objects respond to the same method call differently\n- Works because Python looks up methods on the actual object type at runtime\n- You can loop through a list of different objects and call the same method on each\n\n3. Duck typing:\n- Python does not require objects to share a parent class to be used polymorphically\n- If an object has the method, it works — regardless of its type\n- 'If it quacks like a duck, it is a duck'\n\n4. Abstract methods enforce overriding:\n- Covered in the Abstract Classes concept",
                "class Shape:\n    def area(self):\n        return 0\n\n    def describe(self):\n        print(f'I am a {self.__class__.__name__} with area {self.area():.2f}')\n\nclass Circle(Shape):\n    def __init__(self, radius):\n        self.radius = radius\n\n    def area(self):     # override\n        return 3.14159 * self.radius ** 2\n\nclass Rectangle(Shape):\n    def __init__(self, width, height):\n        self.width = width\n        self.height = height\n\n    def area(self):     # override\n        return self.width * self.height\n\nclass Triangle(Shape):\n    def __init__(self, base, height):\n        self.base = base\n        self.height = height\n\n    def area(self):     # override\n        return 0.5 * self.base * self.height\n\n# Polymorphism — same call, different results\nshapes = [Circle(5), Rectangle(4, 6), Triangle(3, 8)]\nfor shape in shapes:\n    shape.describe()    # each calls its own area()",
                List.of(
                    new Concept.ConceptExample("Payment system with polymorphism",
                        "Different payment methods respond to the same process() call.",
                        "class Payment:\n    def process(self, amount):\n        raise NotImplementedError\n\nclass CreditCard(Payment):\n    def process(self, amount):\n        print(f'Charged Rs.{amount} to credit card')\n\nclass UPI(Payment):\n    def __init__(self, upi_id):\n        self.upi_id = upi_id\n\n    def process(self, amount):\n        print(f'Paid Rs.{amount} via UPI: {self.upi_id}')\n\nclass NetBanking(Payment):\n    def process(self, amount):\n        print(f'Rs.{amount} debited via net banking')\n\n# Process all payments the same way\npayments = [CreditCard(), UPI('ravi@upi'), NetBanking()]\nfor p in payments:\n    p.process(1000)",
                        "Charged Rs.1000 to credit card\nPaid Rs.1000 via UPI: ravi@upi\nRs.1000 debited via net banking")
                ),
                List.of(
                    "Method overriding: same method name in child class replaces parent version",
                    "Polymorphism: loop through different objects, call same method — each responds differently",
                    "Python uses duck typing — if the object has the method, it works regardless of type",
                    "Use super().method() inside the override if you want to run the parent's logic too"
                ),
                "Polymorphism is what makes code extensible. When you add a new class later, it just needs to implement the same method — all existing code that calls that method automatically works with the new class.",
                List.of(
                    "Changing the method signature when overriding — parameter names and count should match the parent"
                ),
                15, 7, "C"),

            conceptRich(py, "Encapsulation — Private and Protected",
                "Encapsulation hides an object's internal data from outside access. Python uses naming conventions: _protected (single underscore) and __private (double underscore).",
                "Encapsulation is about protecting data.\n\nImagine a bank account. The balance is private — you cannot directly reach in and change it to any number you want. You can only change it through official methods like deposit() and withdraw(), which validate the amount first.\n\nThis is encapsulation — hiding internal data and controlling access through methods.\n\nPython uses naming conventions:\n- _name (single underscore): protected — convention says 'internal use, be careful'\n- __name (double underscore): private — Python mangles the name to make it harder to access from outside",
                "1. Public attributes (default):\n- self.name — accessible from anywhere\n- No underscore prefix\n\n2. Protected attributes:\n- self._name — single underscore prefix\n- Convention: 'intended for internal use or subclasses'\n- Still technically accessible from outside but signals 'do not access directly'\n\n3. Private attributes:\n- self.__name — double underscore prefix\n- Python performs name mangling: __name becomes _ClassName__name\n- Harder to access from outside, prevents accidental override in subclasses\n\n4. Getters and setters:\n- Public methods to read (get) and write (set) private attributes\n- Allows validation before setting values\n- Modern Python uses @property instead (next concept)\n\n5. Name mangling:\n- obj.__balance raises AttributeError from outside\n- obj._BankAccount__balance still works but should not be used",
                "class BankAccount:\n    def __init__(self, owner, balance):\n        self.owner = owner           # public\n        self._bank_code = 'PYBANK'   # protected\n        self.__balance = balance     # private\n\n    def deposit(self, amount):\n        if amount > 0:\n            self.__balance += amount\n\n    def withdraw(self, amount):\n        if 0 < amount <= self.__balance:\n            self.__balance -= amount\n            return True\n        print('Invalid amount or insufficient funds')\n        return False\n\n    def get_balance(self):\n        return self.__balance\n\nacc = BankAccount('Ravi', 5000)\nacc.deposit(1000)\nacc.withdraw(500)\n\nprint(acc.owner)           # works — public\nprint(acc._bank_code)      # works — protected (but not recommended)\nprint(acc.get_balance())   # 5500 — correct way\n\n# print(acc.__balance)     # AttributeError\nprint(acc._BankAccount__balance)  # 5500 — name mangling (avoid this)",
                List.of(
                    new Concept.ConceptExample("Validated setter using private attribute",
                        "Use private attribute with a setter method to validate before storing.",
                        "class Student:\n    def __init__(self, name, age):\n        self.name = name\n        self.__age = None\n        self.set_age(age)   # use setter for validation\n\n    def set_age(self, age):\n        if isinstance(age, int) and 5 <= age <= 100:\n            self.__age = age\n        else:\n            raise ValueError(f'Invalid age: {age}')\n\n    def get_age(self):\n        return self.__age\n\ns = Student('Ravi', 21)\nprint(s.get_age())   # 21\n\ntry:\n    s.set_age(-5)\nexcept ValueError as e:\n    print(e)",
                        "21\nInvalid age: -5")
                ),
                List.of(
                    "Single underscore _name is a convention for protected — Python does not enforce it",
                    "Double underscore __name triggers name mangling to _ClassName__name",
                    "Use getter/setter methods or @property to provide controlled access to private data",
                    "Encapsulation prevents external code from putting objects into invalid states"
                ),
                "Use private attributes (__name) for data that must be validated before being set. Add a getter method to read it and a setter with validation to write it. This pattern prevents bugs caused by setting invalid values directly.",
                List.of(
                    "Thinking __name is completely inaccessible — it is just name-mangled, still reachable",
                    "Making everything private — only hide what genuinely needs protection"
                ),
                15, 8, "C"),

            conceptRich(py, "@property, @setter, @deleter",
                "@property turns a method into an attribute. It gives you getter, setter and deleter behaviour with clean attribute-style access while still allowing validation.",
                "Without @property, you need explicit get_name() and set_name() methods. This works but feels unnatural — you call obj.get_name() instead of obj.name.\n\n@property solves this. You write a method named like an attribute and decorate it with @property. Now callers write obj.name but Python runs your method behind the scenes.\n\nThis lets you:\n- Add validation when setting a value\n- Compute a value on the fly\n- Make an attribute read-only\n...all with normal attribute syntax that callers never need to change.",
                "1. @property (getter):\n- @property decorator on a method makes it accessible as an attribute\n- No parentheses needed when accessing: obj.name not obj.name()\n\n2. @name.setter:\n- Define a method with the same name decorated with @property_name.setter\n- Runs when you do obj.name = value\n- Add validation here\n\n3. @name.deleter:\n- Runs when you do del obj.name\n- Use for cleanup logic\n\n4. Read-only property:\n- Define only @property without a setter\n- Attempting obj.name = value raises AttributeError\n\n5. Computed property:\n- Return a calculated value rather than a stored attribute\n- The calculation runs fresh each time the property is accessed",
                "class Student:\n    def __init__(self, name, marks):\n        self._name = name\n        self._marks = None\n        self.marks = marks    # uses setter\n\n    @property\n    def name(self):\n        return self._name\n\n    @property\n    def marks(self):\n        return self._marks\n\n    @marks.setter\n    def marks(self, value):\n        if not isinstance(value, (int, float)):\n            raise TypeError('Marks must be a number')\n        if not 0 <= value <= 100:\n            raise ValueError('Marks must be between 0 and 100')\n        self._marks = value\n\n    @property\n    def grade(self):    # computed property — no setter\n        if self._marks >= 90: return 'A'\n        if self._marks >= 75: return 'B'\n        if self._marks >= 60: return 'C'\n        return 'D'\n\ns = Student('Ravi', 85)\nprint(s.name)    # Ravi\nprint(s.marks)   # 85\nprint(s.grade)   # B\n\ns.marks = 92\nprint(s.grade)   # A\n\ntry:\n    s.marks = 150\nexcept ValueError as e:\n    print(e)",
                List.of(
                    new Concept.ConceptExample("Read-only computed property",
                        "A property that computes a value without storing it.",
                        "class Circle:\n    def __init__(self, radius):\n        self._radius = radius\n\n    @property\n    def radius(self):\n        return self._radius\n\n    @radius.setter\n    def radius(self, value):\n        if value <= 0:\n            raise ValueError('Radius must be positive')\n        self._radius = value\n\n    @property\n    def area(self):      # computed — no setter\n        return 3.14159 * self._radius ** 2\n\n    @property\n    def circumference(self):   # computed — no setter\n        return 2 * 3.14159 * self._radius\n\nc = Circle(5)\nprint(f'Radius: {c.radius}')\nprint(f'Area: {c.area:.2f}')\nprint(f'Circumference: {c.circumference:.2f}')\n\nc.radius = 10\nprint(f'New area: {c.area:.2f}')",
                        "Radius: 5\nArea: 78.54\nCircumference: 31.42\nNew area: 314.16")
                ),
                List.of(
                    "@property makes a method accessible as an attribute — no parentheses needed",
                    "@property_name.setter runs when you assign a value: obj.name = value",
                    "A property without a setter is read-only — assignment raises AttributeError",
                    "Use properties to add validation without changing how callers access attributes"
                ),
                "Use @property when you need to add validation or computation to what looks like a simple attribute. The caller's code never changes — they still write obj.name = value — but your setter validates it first.",
                List.of(
                    "Forgetting the @property_name.setter decorator — assignment silently creates a new instance attribute instead",
                    "Naming the setter method differently from the property — both must have the same name"
                ),
                15, 9, "C"),

            conceptRich(py, "Abstract Classes — abc, @abstractmethod",
                "An abstract class defines a template that child classes must follow. Abstract methods have no body — subclasses are forced to implement them.",
                "An abstract class is like a job description.\n\nThe job description says 'this role requires: write reports, attend meetings, manage budget'. It does not do these things itself — it defines what a person in this role must be able to do.\n\nIn Python, an abstract class:\n- Cannot be instantiated directly\n- Defines abstract methods that subclasses must implement\n- Ensures all subclasses follow the same interface\n\nThis is useful when you have a common structure (like Shape) but each specific type (Circle, Rectangle) must provide its own implementation of methods like area().",
                "1. Creating an abstract class:\n- Import ABC and abstractmethod from abc module\n- Inherit from ABC: class Shape(ABC)\n- Mark methods with @abstractmethod decorator\n\n2. Abstract method:\n- Has no body (just pass or docstring)\n- Any subclass that does not implement it cannot be instantiated\n\n3. Rules:\n- Cannot create an instance of an abstract class directly\n- Subclass must implement all abstract methods\n- Abstract class can also have concrete (non-abstract) methods\n\n4. Why use abstract classes:\n- Enforces a contract — guarantees all subclasses have required methods\n- Prevents incomplete classes from being used\n- Defines a clear interface for a group of related classes",
                "from abc import ABC, abstractmethod\n\nclass Shape(ABC):\n    @abstractmethod\n    def area(self):\n        pass\n\n    @abstractmethod\n    def perimeter(self):\n        pass\n\n    def describe(self):   # concrete method\n        print(f'{self.__class__.__name__}: area={self.area():.2f}, perimeter={self.perimeter():.2f}')\n\nclass Circle(Shape):\n    def __init__(self, radius):\n        self.radius = radius\n\n    def area(self):\n        return 3.14159 * self.radius ** 2\n\n    def perimeter(self):\n        return 2 * 3.14159 * self.radius\n\nclass Rectangle(Shape):\n    def __init__(self, w, h):\n        self.w = w\n        self.h = h\n\n    def area(self):\n        return self.w * self.h\n\n    def perimeter(self):\n        return 2 * (self.w + self.h)\n\n# Shape()   # TypeError: Cannot instantiate abstract class\n\nshapes = [Circle(5), Rectangle(4, 6)]\nfor s in shapes:\n    s.describe()",
                List.of(
                    new Concept.ConceptExample("Abstract Payment class",
                        "Force all payment methods to implement process() and refund().",
                        "from abc import ABC, abstractmethod\n\nclass Payment(ABC):\n    @abstractmethod\n    def process(self, amount):\n        pass\n\n    @abstractmethod\n    def refund(self, amount):\n        pass\n\n    def receipt(self, amount):   # concrete method\n        print(f'Receipt: Rs.{amount} via {self.__class__.__name__}')\n\nclass UPI(Payment):\n    def process(self, amount):\n        print(f'UPI payment: Rs.{amount}')\n        self.receipt(amount)\n\n    def refund(self, amount):\n        print(f'UPI refund: Rs.{amount}')\n\nclass Card(Payment):\n    def process(self, amount):\n        print(f'Card payment: Rs.{amount}')\n        self.receipt(amount)\n\n    def refund(self, amount):\n        print(f'Card refund initiated: Rs.{amount}')\n\nfor p in [UPI(), Card()]:\n    p.process(500)\n    p.refund(100)\n    print()",
                        "UPI payment: Rs.500\nReceipt: Rs.500 via UPI\nUPI refund: Rs.100\n\nCard payment: Rs.500\nReceipt: Rs.500 via Card\nCard refund initiated: Rs.100\n")
                ),
                List.of(
                    "Import ABC and abstractmethod from the abc module",
                    "A class is abstract by inheriting from ABC",
                    "Abstract methods have no implementation — just pass or a docstring",
                    "Trying to instantiate an abstract class raises TypeError",
                    "Subclasses must implement ALL abstract methods or they are also abstract"
                ),
                "Use abstract classes when you have multiple related classes that must all support the same operations. The abstract class acts as the contract. Anyone writing a new subclass knows exactly which methods they must implement.",
                List.of(
                    "Forgetting to import ABC and abstractmethod from abc",
                    "Inheriting from ABC but forgetting @abstractmethod — the method is concrete, not abstract",
                    "Trying to create an instance of the abstract class directly"
                ),
                20, 10, "C"),

            conceptRich(py, "Magic / Dunder Methods",
                "Dunder methods (double underscore) let your objects work with Python's built-in operations like +, len(), str(), comparison operators, and iteration.",
                "Dunder methods — also called magic methods — are special methods with double underscores on both sides: __init__, __str__, __len__, __add__.\n\nWhen you write len(my_list), Python internally calls my_list.__len__(). When you write a + b, Python calls a.__add__(b).\n\nBy defining these methods in your class, you make your objects behave like built-in Python types. Your objects can support + operator, work with len(), print nicely with print(), be compared with ==, and even be iterable.",
                "1. Common dunder methods:\n- __init__: constructor, called on object creation\n- __str__: string representation for print() and str()\n- __repr__: detailed representation for debugging\n- __len__: called by len(obj)\n- __eq__: called by == operator\n- __lt__, __gt__, __le__, __ge__: comparison operators\n- __add__: called by + operator\n- __contains__: called by in operator\n- __getitem__: called by obj[index]\n- __iter__ and __next__: make object iterable\n\n2. __str__ vs __repr__:\n- __str__: human-readable, for print()\n- __repr__: developer-friendly, for debugging. Should ideally return string that recreates the object\n\n3. Using @functools.total_ordering:\n- Define __eq__ and one of __lt__/__gt__ and get all comparisons automatically",
                "class Vector:\n    def __init__(self, x, y):\n        self.x = x\n        self.y = y\n\n    def __str__(self):\n        return f'Vector({self.x}, {self.y})'\n\n    def __repr__(self):\n        return f'Vector(x={self.x}, y={self.y})'\n\n    def __add__(self, other):\n        return Vector(self.x + other.x, self.y + other.y)\n\n    def __eq__(self, other):\n        return self.x == other.x and self.y == other.y\n\n    def __len__(self):\n        return int((self.x**2 + self.y**2) ** 0.5)\n\nv1 = Vector(3, 4)\nv2 = Vector(1, 2)\n\nprint(v1)            # Vector(3, 4) — calls __str__\nprint(repr(v1))      # Vector(x=3, y=4) — calls __repr__\nprint(v1 + v2)       # Vector(4, 6) — calls __add__\nprint(v1 == v2)      # False — calls __eq__\nprint(len(v1))       # 5 — calls __len__",
                List.of(
                    new Concept.ConceptExample("Shopping cart with dunder methods",
                        "Make a cart object support len(), in operator, and print nicely.",
                        "class Cart:\n    def __init__(self):\n        self.items = []\n\n    def add(self, item):\n        self.items.append(item)\n\n    def __len__(self):\n        return len(self.items)\n\n    def __contains__(self, item):\n        return item in self.items\n\n    def __str__(self):\n        return f'Cart with {len(self)} items: {\", \".join(self.items)}'\n\ncart = Cart()\ncart.add('Book')\ncart.add('Pen')\ncart.add('Bag')\n\nprint(cart)\nprint(len(cart))\nprint('Pen' in cart)\nprint('Phone' in cart)",
                        "Cart with 3 items: Book, Pen, Bag\n3\nTrue\nFalse")
                ),
                List.of(
                    "__str__ is for print() and str() — make it human-readable",
                    "__repr__ is for debugging — make it show how to recreate the object",
                    "__add__ enables the + operator between two objects of your class",
                    "__eq__ enables == comparison between objects",
                    "__len__ enables len() on your object",
                    "Python automatically calls dunder methods for built-in operations"
                ),
                "Always define __repr__ in your classes. When debugging, if an object prints as <__main__.Student object at 0x...> it is useless. A good __repr__ like Student(name='Ravi', marks=85) tells you exactly what the object contains.",
                List.of(
                    "Defining __eq__ without __hash__ — Python sets __hash__ to None which breaks using objects in sets or as dict keys",
                    "Returning a non-string from __str__ or __repr__ — must always return a string"
                ),
                20, 11, "C"),

            conceptRich(py, "@classmethod and @staticmethod",
                "@classmethod receives the class as first argument and can create objects or access class data. @staticmethod is a plain function inside a class with no access to class or instance.",
                "Not every method in a class needs to work on an object's data.\n\n@classmethod is for methods that work with the class itself — like alternative constructors or factory methods. Instead of self, it receives cls (the class).\n\n@staticmethod is for utility functions that logically belong to a class but do not need access to class or instance data. It is just a regular function placed inside the class for organisation.",
                "1. Instance method (default):\n- First param: self (the object)\n- Can access and modify instance attributes\n\n2. @classmethod:\n- Decorated with @classmethod\n- First param: cls (the class itself)\n- Can access class variables\n- Common use: alternative constructors (create objects from different input formats)\n\n3. @staticmethod:\n- Decorated with @staticmethod\n- No self or cls parameter\n- Cannot access instance or class data\n- Used for utility functions that belong logically to the class\n\n4. When to use which:\n- Instance method: needs to read/write object data\n- Class method: needs to create objects or access class-level data\n- Static method: utility function that belongs to the class concept but needs no data from it",
                "class Student:\n    school = 'Python Academy'\n    count = 0\n\n    def __init__(self, name, marks):\n        self.name = name\n        self.marks = marks\n        Student.count += 1\n\n    # Instance method\n    def grade(self):\n        return 'Pass' if self.marks >= 40 else 'Fail'\n\n    # Class method — alternative constructor\n    @classmethod\n    def from_string(cls, data_string):\n        name, marks = data_string.split(',')\n        return cls(name.strip(), int(marks.strip()))\n\n    @classmethod\n    def get_count(cls):\n        return cls.count\n\n    # Static method — utility\n    @staticmethod\n    def is_valid_marks(marks):\n        return isinstance(marks, (int, float)) and 0 <= marks <= 100\n\n# Using instance method\ns1 = Student('Ravi', 85)\nprint(s1.grade())\n\n# Using class method — create from string\ns2 = Student.from_string('Priya, 92')\nprint(s2.name, s2.marks)\n\n# Using static method\nprint(Student.is_valid_marks(85))    # True\nprint(Student.is_valid_marks(150))   # False\nprint(Student.get_count())",
                List.of(
                    new Concept.ConceptExample("Factory methods using @classmethod",
                        "Create objects from different data sources using class methods.",
                        "class Date:\n    def __init__(self, day, month, year):\n        self.day = day\n        self.month = month\n        self.year = year\n\n    @classmethod\n    def from_string(cls, date_str):\n        day, month, year = map(int, date_str.split('-'))\n        return cls(day, month, year)\n\n    @classmethod\n    def today(cls):\n        import datetime\n        d = datetime.date.today()\n        return cls(d.day, d.month, d.year)\n\n    @staticmethod\n    def is_leap_year(year):\n        return year % 4 == 0 and (year % 100 != 0 or year % 400 == 0)\n\n    def __str__(self):\n        return f'{self.day:02d}-{self.month:02d}-{self.year}'\n\nd1 = Date.from_string('15-08-2024')\nd2 = Date.today()\nprint(d1)\nprint(Date.is_leap_year(2024))\nprint(Date.is_leap_year(2023))",
                        "15-08-2024\nTrue\nFalse")
                ),
                List.of(
                    "@classmethod receives cls (the class) as first argument, not self",
                    "@staticmethod receives no automatic first argument — no self or cls",
                    "Class methods are commonly used as alternative constructors (from_string, from_file, today)",
                    "Static methods are utility functions that logically belong to the class"
                ),
                "Use @classmethod as a factory method when you want to create objects from different input formats. For example, Date.from_string('15-08-2024') is cleaner than asking the caller to parse the string themselves.",
                List.of(
                    "Using self inside a @classmethod — the first parameter is cls, not an instance",
                    "Using @staticmethod when you need to access class data — use @classmethod instead"
                ),
                15, 12, "C")
        );

        conceptRepository.saveAll(concepts);
        py.setTotalConcepts(concepts.size());
        subjectRepository.save(py);
        System.out.println("✅ Python OOP seeded — " + concepts.size() + " concepts");
    }

    // ─── PYTHON ADVANCED ──────────────────────────────────────────────────────
    private void seedPythonAdvanced() {
        Subject py = subjectRepository.save(sub(
            "Python Advanced",
            "Master comprehensions, decorators, generators, iterators, context managers, regex, file I/O and modules",
            "⚡", "#EF4444", "B"
        ));
        py.setOverview("Python Advanced covers the patterns and tools that separate beginner Python from professional Python. Comprehensions make data transformation concise, decorators add behaviour to functions, generators handle large data efficiently, and context managers manage resources safely. These patterns appear in every real-world Python codebase.");
        py.setWhyLearn("Senior Python developers and code reviewers expect Pythonic code. Comprehensions, decorators, and generators are used daily in Django, FastAPI, data pipelines, and automation scripts. These topics appear in Python technical interviews at every level.");
        py.setForWho("Students who have completed Python Basics and Python OOP. You should be comfortable with functions, classes, and exception handling.");
        py.setPrerequisites(List.of("Python Basics completed", "Python OOP completed", "Comfortable with functions and classes"));
        py.setOutcomes(List.of(
            "Write list, dict and set comprehensions to replace verbose loops",
            "Use lambda with map(), filter() and zip() for functional-style operations",
            "Write and apply decorators to add behaviour to functions",
            "Create generators with yield for memory-efficient iteration",
            "Implement custom iterators using __iter__ and __next__",
            "Use context managers for safe resource management",
            "Use regex for pattern matching and text processing",
            "Read and write files using Python's file I/O"
        ));
        py.setWhatYouWillBuild(List.of(
            "A data processing pipeline using comprehensions and generators",
            "A timing decorator and a retry decorator",
            "A log file parser using regex and file I/O"
        ));
        py.setToolsRequired(List.of("Python 3.x", "VS Code", "re module (built-in)", "os module (built-in)"));
        py.setDifficulty("Intermediate");
        py.setEstimatedHours(12);
        py.setCareerUse("These patterns are used in Django views, FastAPI endpoints, data engineering pipelines, automation scripts, and any production Python code. Interviewers test comprehensions, decorators, and generators as indicators of Python fluency.");
        subjectRepository.save(py);

        List<Concept> concepts = List.of(

            conceptRich(py, "List Comprehensions",
                "List comprehensions create a new list by applying an expression to each item in an iterable, in a single readable line.",
                "A list comprehension is a shorter, cleaner way to build a list from another list or range.\n\nInstead of:\n```\nresult = []\nfor x in numbers:\n    result.append(x * 2)\n```\n\nYou write:\n```\nresult = [x * 2 for x in numbers]\n```\n\nSame result, one line, easier to read.\n\nYou can also add a condition to filter items:\n```\nresult = [x * 2 for x in numbers if x > 0]\n```\n\nList comprehensions are faster than equivalent for loops because Python optimises them internally.",
                "1. Basic syntax:\n- [expression for item in iterable]\n- Creates a new list by evaluating expression for each item\n\n2. With condition (filter):\n- [expression for item in iterable if condition]\n- Only includes items where condition is True\n\n3. Nested comprehension:\n- [expression for outer in iterable for inner in outer]\n- Replaces nested for loops\n\n4. With function call:\n- [func(x) for x in items]\n- Applies a function to each item\n\n5. Performance:\n- Faster than equivalent for loop + append\n- More memory-efficient than map() in most cases\n- Do not use for complex logic — keep comprehensions readable",
                "numbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]\n\n# Basic comprehension\nsquares = [x**2 for x in numbers]\nprint(squares)\n\n# With condition\nevens = [x for x in numbers if x % 2 == 0]\nprint(evens)\n\n# Transform and filter\nsquares_of_evens = [x**2 for x in numbers if x % 2 == 0]\nprint(squares_of_evens)\n\n# String operations\nnames = ['  ravi  ', '  priya  ', '  arjun  ']\ncleaned = [name.strip().title() for name in names]\nprint(cleaned)\n\n# Nested comprehension — flatten 2D list\nmatrix = [[1, 2, 3], [4, 5, 6], [7, 8, 9]]\nflat = [num for row in matrix for num in row]\nprint(flat)\n\n# Conditional expression (ternary) in comprehension\nresult = ['Pass' if m >= 40 else 'Fail' for m in [85, 30, 72, 45, 15]]\nprint(result)",
                List.of(
                    new Concept.ConceptExample("Process student marks",
                        "Use comprehensions to transform and filter student data.",
                        "marks = [85, 32, 91, 45, 28, 76, 60, 15, 88]\n\npassed = [m for m in marks if m >= 40]\nfailed = [m for m in marks if m < 40]\ngrades = ['A' if m >= 75 else 'B' if m >= 60 else 'C' if m >= 40 else 'F' for m in marks]\n\nprint('Passed:', passed)\nprint('Failed:', failed)\nprint('Grades:', grades)\nprint(f'Pass rate: {len(passed)/len(marks)*100:.0f}%')",
                        "Passed: [85, 91, 45, 76, 60, 88]\nFailed: [32, 28, 15]\nGrades: ['A', 'F', 'A', 'C', 'F', 'B', 'C', 'F', 'A']\nPass rate: 67%"),
                    new Concept.ConceptExample("Generate combinations",
                        "Use nested comprehension to generate all pairs.",
                        "colours = ['Red', 'Blue', 'Green']\nsizes = ['S', 'M', 'L']\n\nvariants = [(c, s) for c in colours for s in sizes]\nfor v in variants:\n    print(v)",
                        "('Red', 'S')\n('Red', 'M')\n('Red', 'L')\n('Blue', 'S')\n('Blue', 'M')\n('Blue', 'L')\n('Green', 'S')\n('Green', 'M')\n('Green', 'L')")
                ),
                List.of(
                    "[expr for x in iterable] — basic form",
                    "[expr for x in iterable if condition] — with filter",
                    "Comprehensions are faster than for loop + append",
                    "Keep comprehensions on one line if readable — use a for loop for complex logic",
                    "Nested comprehension: [expr for outer in iterable for inner in outer]"
                ),
                "If your comprehension is hard to read in one line, use a regular for loop instead. Comprehensions are for clarity — if they reduce clarity, they defeat the purpose.",
                List.of(
                    "Putting complex logic inside a comprehension — use a function or regular loop instead",
                    "Forgetting that the condition goes at the end: [x for x in list if x > 0], not [x if x > 0 for x in list]"
                ),
                15, 1, "C"),

            conceptRich(py, "Dict and Set Comprehensions",
                "Dict comprehensions create dictionaries from iterables. Set comprehensions create sets. Both follow the same pattern as list comprehensions.",
                "Just like list comprehensions build lists, dict and set comprehensions build dicts and sets in one line.\n\nDict comprehension:\n{key: value for item in iterable}\n\nSet comprehension:\n{expression for item in iterable}\n\nThe key difference from list comprehensions:\n- Dict: uses curly braces with key: value pairs\n- Set: uses curly braces without pairs (like a list comprehension but with {})\n\nBoth support filtering with if conditions.",
                "1. Dict comprehension syntax:\n- {key_expr: value_expr for item in iterable}\n- {key_expr: value_expr for item in iterable if condition}\n\n2. Set comprehension syntax:\n- {expression for item in iterable}\n- Automatically removes duplicates\n\n3. Common uses:\n- Swap keys and values: {v: k for k, v in original.items()}\n- Filter a dictionary: {k: v for k, v in d.items() if v > 0}\n- Transform values: {k: v.upper() for k, v in d.items()}\n- Build dict from two lists: dict(zip(keys, values)) or {k: v for k, v in zip(keys, values)}\n\n4. Difference from set literal:\n- {} is an empty dict, not an empty set\n- Use set() for empty set",
                "students = ['Ravi', 'Priya', 'Arjun', 'Sneha']\nmarks = [85, 92, 78, 88]\n\n# Dict comprehension — pair two lists\ngrades = {name: mark for name, mark in zip(students, marks)}\nprint(grades)\n\n# Filter dict — only passed\npassed = {name: m for name, m in grades.items() if m >= 80}\nprint(passed)\n\n# Transform values\nupper_grades = {name: str(m) + '%' for name, m in grades.items()}\nprint(upper_grades)\n\n# Invert dict (swap keys and values)\noriginal = {'a': 1, 'b': 2, 'c': 3}\ninverted = {v: k for k, v in original.items()}\nprint(inverted)\n\n# Set comprehension — unique first letters\nwords = ['apple', 'ant', 'bear', 'avocado', 'banana', 'bee']\nfirst_letters = {word[0] for word in words}\nprint(first_letters)\n\n# Set comprehension with condition\nlong_words = {word for word in words if len(word) > 4}\nprint(long_words)",
                List.of(
                    new Concept.ConceptExample("Build lookup table from a list",
                        "Create a dict mapping each student to their grade using a comprehension.",
                        "def get_grade(m):\n    if m >= 90: return 'A'\n    if m >= 75: return 'B'\n    if m >= 60: return 'C'\n    return 'D'\n\nmarks_data = [('Ravi', 85), ('Priya', 92), ('Arjun', 58), ('Sneha', 76)]\n\n# Dict comprehension\ngrade_map = {name: get_grade(m) for name, m in marks_data}\nprint(grade_map)\n\n# Filter to only A and B grades\ntop_students = {name: g for name, g in grade_map.items() if g in ('A', 'B')}\nprint(top_students)",
                        "{'Ravi': 'B', 'Priya': 'A', 'Arjun': 'D', 'Sneha': 'B'}\n{'Ravi': 'B', 'Priya': 'A', 'Sneha': 'B'}")
                ),
                List.of(
                    "{k: v for ...} creates a dict, {v for ...} creates a set",
                    "Dict comprehensions are great for transforming or filtering existing dicts",
                    "Set comprehensions automatically deduplicate",
                    "zip(list1, list2) pairs elements for building dicts from two lists"
                ),
                "Use dict comprehension to filter or transform an existing dictionary. {k: v for k, v in d.items() if condition} is cleaner than building a new dict with a for loop.",
                List.of(
                    "Using {} for an empty set — that creates an empty dict. Use set() for empty set",
                    "Confusing set comprehension {x for x in list} with dict comprehension {k: v for ...}"
                ),
                15, 2, "C"),

            conceptRich(py, "Lambda, map(), filter(), zip()",
                "Lambda creates small anonymous functions. map() applies a function to every item. filter() selects items matching a condition. zip() combines multiple iterables together.",
                "Sometimes you need a small, one-use function. Instead of defining a full def function, Python lets you write a lambda — a tiny inline function.\n\nlambda x: x * 2 is the same as:\ndef double(x):\n    return x * 2\n\nmap(), filter(), and zip() are built-in functions that work well with lambda:\n- map(func, iterable): apply func to every item\n- filter(func, iterable): keep only items where func returns True\n- zip(a, b): pair items from two iterables together\n\nIn modern Python, list comprehensions are often preferred over map() and filter(), but you will see all of these in real codebases.",
                "1. lambda syntax:\n- lambda parameters: expression\n- Can have multiple parameters: lambda x, y: x + y\n- Can only contain a single expression — not statements\n- Returns the expression result automatically\n\n2. map(function, iterable):\n- Applies function to every element of iterable\n- Returns a lazy map object — wrap with list() to see results\n- map(lambda x: x**2, [1,2,3]) → [1, 4, 9]\n\n3. filter(function, iterable):\n- Keeps only elements where function returns True\n- Returns a lazy filter object — wrap with list()\n- filter(lambda x: x > 0, [-1, 2, -3, 4]) → [2, 4]\n\n4. zip(*iterables):\n- Pairs elements from multiple iterables together\n- Returns tuples: zip([1,2], ['a','b']) → [(1,'a'), (2,'b')]\n- Stops at the shortest iterable\n- dict(zip(keys, values)) builds a dictionary from two lists\n\n5. When to use what:\n- lambda: quick one-liner key functions for sorted(), max(), min()\n- map/filter: functional style — often list comprehensions are clearer\n- zip: pairing two parallel lists together",
                "# Lambda\ndouble = lambda x: x * 2\nadd = lambda x, y: x + y\nprint(double(5))     # 10\nprint(add(3, 4))     # 7\n\n# Lambda with sorted() — sort by key\nstudents = [('Ravi', 85), ('Priya', 92), ('Arjun', 78)]\nsorted_by_marks = sorted(students, key=lambda s: s[1], reverse=True)\nprint(sorted_by_marks)\n\n# map() — apply function to all items\nnumbers = [1, 2, 3, 4, 5]\nsquares = list(map(lambda x: x**2, numbers))\nprint(squares)\n\n# filter() — keep items matching condition\nevens = list(filter(lambda x: x % 2 == 0, numbers))\nprint(evens)\n\n# zip() — combine two lists\nnames = ['Ravi', 'Priya', 'Arjun']\nmarks = [85, 92, 78]\n\ncombined = list(zip(names, marks))\nprint(combined)\n\nfor name, mark in zip(names, marks):\n    print(f'{name}: {mark}')",
                List.of(
                    new Concept.ConceptExample("Sort list of dicts using lambda",
                        "Sort a list of employee records by salary using a lambda key.",
                        "employees = [\n    {'name': 'Ravi', 'salary': 45000},\n    {'name': 'Priya', 'salary': 60000},\n    {'name': 'Arjun', 'salary': 38000},\n    {'name': 'Sneha', 'salary': 52000}\n]\n\nby_salary = sorted(employees, key=lambda e: e['salary'], reverse=True)\nfor e in by_salary:\n    print(f\"{e['name']}: Rs.{e['salary']}\")",
                        "Priya: Rs.60000\nSneha: Rs.52000\nRavi: Rs.45000\nArjun: Rs.38000"),
                    new Concept.ConceptExample("Zip to create dict from two lists",
                        "Use zip to pair keys and values from separate lists.",
                        "subjects = ['Maths', 'Physics', 'Chemistry', 'Python']\nmarks = [88, 75, 82, 95]\n\nresult = dict(zip(subjects, marks))\nprint(result)\n\ntotal = sum(result.values())\nprint(f'Total: {total}')\nprint(f'Average: {total/len(result):.1f}')",
                        "{'Maths': 88, 'Physics': 75, 'Chemistry': 82, 'Python': 95}\nTotal: 340\nAverage: 85.0")
                ),
                List.of(
                    "lambda x: expr creates an anonymous function — use for short, one-time functions",
                    "map(func, iterable) returns a map object — wrap with list() to see results",
                    "filter(func, iterable) returns a filter object — wrap with list() to see results",
                    "zip() stops at the shortest iterable",
                    "sorted(list, key=lambda x: x.attr) is the most common lambda use case"
                ),
                "Use lambda mainly with sorted(), max(), min() as the key argument. For anything more complex than a single expression, define a proper function with def — it is more readable.",
                List.of(
                    "Forgetting to wrap map() and filter() with list() — they return lazy iterators",
                    "Writing complex logic in lambda — use def instead for readability"
                ),
                15, 3, "C"),

            conceptRich(py, "*args and **kwargs",
                "*args lets a function accept any number of positional arguments. **kwargs lets it accept any number of keyword arguments.",
                "Sometimes you do not know how many arguments a function will receive.\n\nA print() function can take 1 value or 10 values. A calculator function might take 2 or 20 numbers to add.\n\n*args collects any number of positional arguments into a tuple.\n**kwargs collects any number of keyword arguments into a dictionary.\n\nThe * and ** are the actual syntax. args and kwargs are just conventions — you could write *numbers or **options, but args and kwargs are universally understood.",
                "1. *args:\n- Collects extra positional arguments into a tuple\n- def func(*args): — args is a tuple inside the function\n- Call: func(1, 2, 3, 4) — all go into args\n\n2. **kwargs:\n- Collects extra keyword arguments into a dictionary\n- def func(**kwargs): — kwargs is a dict inside the function\n- Call: func(name='Ravi', age=21) — all go into kwargs\n\n3. Combining all types:\n- def func(pos1, pos2, *args, **kwargs):\n- Order must be: positional, *args, keyword-only, **kwargs\n\n4. Unpacking with * and **:\n- *list unpacks a list as positional arguments: func(*my_list)\n- **dict unpacks a dict as keyword arguments: func(**my_dict)\n\n5. Common real-world uses:\n- Wrapper functions that pass all arguments to another function\n- Flexible APIs that accept optional configuration",
                "# *args\ndef add_all(*args):\n    return sum(args)\n\nprint(add_all(1, 2))           # 3\nprint(add_all(1, 2, 3, 4, 5)) # 15\n\n# **kwargs\ndef create_profile(**kwargs):\n    for key, value in kwargs.items():\n        print(f'{key}: {value}')\n\ncreate_profile(name='Ravi', age=21, city='Hyderabad')\n\n# Combined\ndef log(level, *messages, **context):\n    print(f'[{level}]', ' '.join(messages))\n    if context:\n        print('  Context:', context)\n\nlog('INFO', 'User logged in')\nlog('ERROR', 'Payment failed', 'Retry attempted', user='Ravi', amount=500)\n\n# Unpacking with * and **\nnumbers = [1, 2, 3, 4, 5]\nprint(add_all(*numbers))   # unpacks list as args\n\nstudent = {'name': 'Priya', 'age': 22, 'city': 'Chennai'}\ncreate_profile(**student)  # unpacks dict as kwargs",
                List.of(
                    new Concept.ConceptExample("Flexible function with *args and **kwargs",
                        "A function that accepts any combination of arguments.",
                        "def send_email(to, subject, *cc_list, **options):\n    print(f'To: {to}')\n    print(f'Subject: {subject}')\n    if cc_list:\n        print(f'CC: {\", \".join(cc_list)}')\n    for key, value in options.items():\n        print(f'{key}: {value}')\n\nsend_email('ravi@example.com', 'Hello')\nprint()\nsend_email(\n    'priya@example.com',\n    'Meeting',\n    'hr@example.com',\n    'manager@example.com',\n    priority='high',\n    read_receipt=True\n)",
                        "To: ravi@example.com\nSubject: Hello\n\nTo: priya@example.com\nSubject: Meeting\nCC: hr@example.com, manager@example.com\npriority: high\nread_receipt: True")
                ),
                List.of(
                    "*args collects positional arguments into a tuple inside the function",
                    "**kwargs collects keyword arguments into a dictionary inside the function",
                    "Order in signature: regular params, *args, keyword-only params, **kwargs",
                    "*list unpacks a list as positional args when calling a function",
                    "**dict unpacks a dict as keyword args when calling a function"
                ),
                "Use *args when writing wrapper functions or utilities that need to forward all arguments to another function. The pattern def wrapper(*args, **kwargs): ... inner(*args, **kwargs) is extremely common in decorators.",
                List.of(
                    "Putting *args before positional parameters in the signature",
                    "Trying to unpack a non-iterable with *",
                    "Using **kwargs when you know exactly which keys to expect — use explicit parameters instead"
                ),
                15, 4, "C"),

            conceptRich(py, "Decorators",
                "A decorator is a function that wraps another function to add behaviour before or after it runs, without modifying the original function's code.",
                "A decorator is like a wrapper around a gift.\n\nThe gift inside is your original function. The wrapper adds something extra — maybe logging, maybe timing, maybe checking if the user is logged in — without touching what is inside.\n\nYou apply a decorator using the @ symbol above the function definition:\n@my_decorator\ndef my_function():\n    ...\n\nThis is exactly the same as:\nmy_function = my_decorator(my_function)\n\nDecorators are used everywhere in Python frameworks — @app.route in Flask, @login_required in Django, @pytest.fixture in pytest.",
                "1. How decorators work:\n- A decorator is a function that takes a function as input\n- It returns a new function (the wrapper) that adds behaviour\n- Applied with @decorator_name above the function\n\n2. Basic structure:\n```\ndef my_decorator(func):\n    def wrapper(*args, **kwargs):\n        # before\n        result = func(*args, **kwargs)\n        # after\n        return result\n    return wrapper\n```\n\n3. functools.wraps:\n- Preserves the original function's name and docstring\n- Always use @functools.wraps(func) inside the wrapper\n\n4. Decorator with arguments:\n- Add one more layer of nesting\n- @repeat(3) — the outer function takes the argument, returns a decorator\n\n5. Stacking decorators:\n- Multiple decorators applied bottom-up: @d2 then @d1 means d1(d2(func))",
                "import functools\nimport time\n\n# Basic decorator\ndef timer(func):\n    @functools.wraps(func)\n    def wrapper(*args, **kwargs):\n        start = time.time()\n        result = func(*args, **kwargs)\n        end = time.time()\n        print(f'{func.__name__} took {end - start:.4f}s')\n        return result\n    return wrapper\n\n@timer\ndef slow_operation():\n    time.sleep(0.1)\n    return 'done'\n\nresult = slow_operation()\nprint(result)\n\n# Decorator for logging\ndef log_call(func):\n    @functools.wraps(func)\n    def wrapper(*args, **kwargs):\n        print(f'Calling {func.__name__} with args={args} kwargs={kwargs}')\n        result = func(*args, **kwargs)\n        print(f'{func.__name__} returned {result}')\n        return result\n    return wrapper\n\n@log_call\ndef add(a, b):\n    return a + b\n\nadd(3, 5)",
                List.of(
                    new Concept.ConceptExample("Authentication decorator",
                        "Check if user is logged in before running the function.",
                        "import functools\n\ndef require_login(func):\n    @functools.wraps(func)\n    def wrapper(*args, **kwargs):\n        user = kwargs.get('user') or (args[0] if args else None)\n        if not user:\n            print('Error: Login required')\n            return None\n        return func(*args, **kwargs)\n    return wrapper\n\n@require_login\ndef view_dashboard(user):\n    print(f'Welcome to dashboard, {user}!')\n    return 'dashboard content'\n\nview_dashboard('Ravi')\nview_dashboard(None)",
                        "Welcome to dashboard, Ravi!\nError: Login required"),
                    new Concept.ConceptExample("Decorator with arguments — retry",
                        "A decorator that retries a function N times on failure.",
                        "import functools\n\ndef retry(times):\n    def decorator(func):\n        @functools.wraps(func)\n        def wrapper(*args, **kwargs):\n            for attempt in range(1, times + 1):\n                try:\n                    return func(*args, **kwargs)\n                except Exception as e:\n                    print(f'Attempt {attempt} failed: {e}')\n                    if attempt == times:\n                        raise\n        return wrapper\n    return decorator\n\nimport random\n\n@retry(3)\ndef unstable_api_call():\n    if random.random() < 0.7:\n        raise ConnectionError('Network timeout')\n    return 'Success'\n\ntry:\n    result = unstable_api_call()\n    print(result)\nexcept ConnectionError:\n    print('All attempts failed')",
                        "Attempt 1 failed: Network timeout\nAttempt 2 failed: Network timeout\nSuccess")
                ),
                List.of(
                    "A decorator is a function that takes a function and returns a function",
                    "Always use @functools.wraps(func) to preserve the original function's metadata",
                    "Use *args, **kwargs in the wrapper to pass all arguments through",
                    "@decorator is syntactic sugar for func = decorator(func)",
                    "Decorators stack bottom-up: @d1 @d2 means d1(d2(func))"
                ),
                "Always add @functools.wraps(func) inside your decorator's wrapper function. Without it, the wrapped function loses its __name__ and __doc__, which breaks debugging and documentation tools.",
                List.of(
                    "Forgetting to return the wrapper function from the decorator",
                    "Forgetting to return the result of func() inside the wrapper",
                    "Not using @functools.wraps — wrapped functions lose their identity"
                ),
                20, 5, "B"),

            conceptRich(py, "Generators and yield",
                "Generators produce values one at a time using yield, pausing and resuming between calls. They are memory-efficient for large or infinite sequences.",
                "Imagine you need the first million even numbers. If you build them all as a list, you use a lot of memory.\n\nA generator is different. It produces one value at a time, on demand. It pauses after each yield, remembers where it was, and resumes from that point when asked for the next value.\n\nA function with yield instead of return becomes a generator function. Calling it returns a generator object. You iterate over it with for or next().\n\nGenerators are lazy — they compute values only when needed. This makes them perfect for large datasets, file reading, and infinite sequences.",
                "1. Generator function:\n- Use yield instead of return\n- Calling the function returns a generator object, does not run the body\n- Values produced one at a time when iterated\n\n2. How it works:\n- First call to next() runs until the first yield, pauses\n- Second call resumes from where it paused until the next yield\n- StopIteration raised when function body ends\n\n3. Generator expression:\n- Like list comprehension but with () instead of []\n- (x**2 for x in range(10)) — lazy, no list created\n\n4. Benefits:\n- Memory efficient — only one value in memory at a time\n- Can represent infinite sequences\n- Fast to start — no need to compute all values upfront\n\n5. yield from:\n- Delegates to another generator or iterable",
                "# Generator function\ndef count_up(start, end):\n    current = start\n    while current <= end:\n        yield current\n        current += 1\n\n# Doesn't run yet — returns generator object\ncounter = count_up(1, 5)\n\nprint(next(counter))   # 1\nprint(next(counter))   # 2\n\nfor num in count_up(1, 5):\n    print(num, end=' ')   # 1 2 3 4 5\nprint()\n\n# Generator for large data — memory efficient\ndef read_large_file_lines(n):\n    for i in range(n):\n        yield f'Line {i}: some data'\n\n# Only one line in memory at a time\nfor line in read_large_file_lines(1000000):\n    if 'Line 5' in line:\n        print(line)\n        break\n\n# Generator expression\nsquares_gen = (x**2 for x in range(10))\nprint(list(squares_gen))\n\n# Compare memory\nimport sys\nlist_mem = sys.getsizeof([x**2 for x in range(1000)])\ngen_mem = sys.getsizeof((x**2 for x in range(1000)))\nprint(f'List: {list_mem} bytes, Generator: {gen_mem} bytes')",
                List.of(
                    new Concept.ConceptExample("Infinite sequence generator",
                        "Generate an infinite sequence — only compute values when needed.",
                        "def fibonacci():\n    a, b = 0, 1\n    while True:\n        yield a\n        a, b = b, a + b\n\nfib = fibonacci()\nfirst_10 = [next(fib) for _ in range(10)]\nprint(first_10)",
                        "[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]"),
                    new Concept.ConceptExample("Pipeline with generators",
                        "Chain generators for memory-efficient data processing.",
                        "def read_numbers(data):\n    for n in data:\n        yield n\n\ndef filter_even(numbers):\n    for n in numbers:\n        if n % 2 == 0:\n            yield n\n\ndef square(numbers):\n    for n in numbers:\n        yield n ** 2\n\ndata = range(1, 11)\npipeline = square(filter_even(read_numbers(data)))\nprint(list(pipeline))",
                        "[4, 16, 36, 64, 100]")
                ),
                List.of(
                    "A function with yield is a generator function — calling it returns a generator object",
                    "yield pauses the function and returns a value — next() resumes it",
                    "Generators are lazy — values computed only when requested",
                    "Generator expression: (expr for x in iterable) — like list comprehension but lazy",
                    "Use generators for large datasets or infinite sequences to save memory"
                ),
                "Use a generator when you are processing data you do not need all at once — reading a large file, processing database rows, or streaming API results. A generator uses constant memory regardless of how many items there are.",
                List.of(
                    "Trying to index a generator like a list — generators have no index, use list() to convert",
                    "Using a generator twice — once exhausted, it produces no more values"
                ),
                20, 6, "B"),

            conceptRich(py, "Iterators and Iterables",
                "An iterable is anything you can loop over. An iterator is an object that produces values one at a time using __iter__ and __next__.",
                "Every time you use a for loop, Python is using an iterator behind the scenes.\n\nWhen you write for item in my_list, Python:\n1. Calls iter(my_list) to get an iterator\n2. Calls next() on the iterator repeatedly\n3. Stops when StopIteration is raised\n\nAn iterable is any object with __iter__ (lists, strings, dicts, generators).\nAn iterator is an object with both __iter__ and __next__.\n\nYou can create your own iterable objects by implementing these two methods, making your custom classes work naturally in for loops.",
                "1. Iterable vs Iterator:\n- Iterable: has __iter__, returns an iterator\n- Iterator: has __iter__ AND __next__\n- All iterators are iterables, not all iterables are iterators\n- Lists are iterable but not iterators — iter(list) creates an iterator\n\n2. iter() and next():\n- iter(iterable) returns an iterator\n- next(iterator) returns next value or raises StopIteration\n\n3. Creating a custom iterator:\n- Implement __iter__(self): return self\n- Implement __next__(self): return next value or raise StopIteration\n\n4. for loop internals:\n- for x in obj is equivalent to:\n  it = iter(obj)\n  while True:\n    try: x = next(it)\n    except StopIteration: break",
                "# Built-in iterables\nmy_list = [1, 2, 3]\nit = iter(my_list)      # get iterator\nprint(next(it))         # 1\nprint(next(it))         # 2\nprint(next(it))         # 3\n# next(it)              # StopIteration\n\n# Custom iterator class\nclass Countdown:\n    def __init__(self, start):\n        self.current = start\n\n    def __iter__(self):\n        return self\n\n    def __next__(self):\n        if self.current <= 0:\n            raise StopIteration\n        value = self.current\n        self.current -= 1\n        return value\n\nfor num in Countdown(5):\n    print(num, end=' ')   # 5 4 3 2 1\nprint()\n\n# Iterable class (returns a fresh iterator each time)\nclass EvenNumbers:\n    def __init__(self, limit):\n        self.limit = limit\n\n    def __iter__(self):\n        return EvenIterator(self.limit)\n\nclass EvenIterator:\n    def __init__(self, limit):\n        self.current = 0\n        self.limit = limit\n\n    def __iter__(self): return self\n\n    def __next__(self):\n        if self.current > self.limit:\n            raise StopIteration\n        val = self.current\n        self.current += 2\n        return val\n\nfor n in EvenNumbers(10):\n    print(n, end=' ')   # 0 2 4 6 8 10",
                List.of(
                    new Concept.ConceptExample("Custom range-like iterator",
                        "Build a class that behaves like range() in a for loop.",
                        "class MyRange:\n    def __init__(self, start, stop, step=1):\n        self.start = start\n        self.stop = stop\n        self.step = step\n        self.current = start\n\n    def __iter__(self):\n        self.current = self.start\n        return self\n\n    def __next__(self):\n        if self.current >= self.stop:\n            raise StopIteration\n        val = self.current\n        self.current += self.step\n        return val\n\nfor n in MyRange(0, 10, 2):\n    print(n, end=' ')",
                        "0 2 4 6 8 ")
                ),
                List.of(
                    "Iterable: has __iter__ | Iterator: has __iter__ AND __next__",
                    "iter(iterable) gets an iterator, next(iterator) gets the next value",
                    "Raise StopIteration in __next__ when values are exhausted",
                    "Generators are iterators — they implement __iter__ and __next__ automatically",
                    "A for loop internally calls iter() then next() repeatedly"
                ),
                "If you just need a simple custom iterable, use a generator function — it handles __iter__ and __next__ automatically. Only implement a full iterator class when you need extra state or the ability to restart iteration.",
                List.of(
                    "Forgetting to raise StopIteration in __next__ — causes infinite loop",
                    "Confusing iterable (has __iter__) with iterator (also has __next__)"
                ),
                15, 7, "C"),

            conceptRich(py, "Context Managers — with statement",
                "Context managers handle setup and cleanup automatically. The with statement ensures resources like files and connections are always properly closed, even if an error occurs.",
                "Every time you open a file, you should close it when done. Every time you acquire a lock, you should release it. Every time you start a database transaction, you should commit or rollback.\n\nForgetting to do these things causes resource leaks.\n\nContext managers solve this. The with statement guarantees that cleanup code runs automatically when the block ends — whether it ends normally or with an error.\n\nwith open('file.txt') as f:\n    data = f.read()\n# file is automatically closed here — no matter what\n\nYou can create your own context managers using __enter__ and __exit__ methods, or the @contextmanager decorator.",
                "1. with statement:\n- with expression as variable: ...\n- expression must be a context manager (has __enter__ and __exit__)\n- __enter__ runs at the start, returns the resource\n- __exit__ runs at the end, handles cleanup\n\n2. File handling with with:\n- open() returns a context manager\n- File is closed automatically when block ends\n- Works even if an exception occurs inside the block\n\n3. Custom context manager (class-based):\n- __enter__(self): setup, return resource\n- __exit__(self, exc_type, exc_val, exc_tb): cleanup, return True to suppress exception\n\n4. @contextmanager decorator:\n- Use with yield in a generator function\n- Code before yield is __enter__, code after is __exit__\n- Simpler than writing a full class\n\n5. Common built-in context managers:\n- open() for files\n- threading.Lock() for thread safety\n- decimal.localcontext() for decimal precision",
                "# File handling — automatic close\nwith open('example.txt', 'w') as f:\n    f.write('Hello, World!')\n    f.write('\\nSecond line')\n\nwith open('example.txt', 'r') as f:\n    content = f.read()\n    print(content)\n\n# Multiple context managers\nwith open('source.txt', 'r') as src, open('dest.txt', 'w') as dst:\n    dst.write(src.read())\n\n# Custom context manager — class based\nclass Timer:\n    import time\n    def __enter__(self):\n        import time\n        self.start = time.time()\n        return self\n\n    def __exit__(self, *args):\n        import time\n        self.elapsed = time.time() - self.start\n        print(f'Elapsed: {self.elapsed:.4f}s')\n\nwith Timer() as t:\n    total = sum(range(1000000))\nprint('Sum done')\n\n# Using contextmanager decorator\nfrom contextlib import contextmanager\n\n@contextmanager\ndef managed_resource(name):\n    print(f'Acquiring {name}')\n    try:\n        yield name\n    finally:\n        print(f'Releasing {name}')\n\nwith managed_resource('database connection') as res:\n    print(f'Using {res}')",
                List.of(
                    new Concept.ConceptExample("Database connection context manager",
                        "Simulate a database context manager that commits or rolls back.",
                        "from contextlib import contextmanager\n\n@contextmanager\ndef db_transaction(connection_name):\n    print(f'BEGIN transaction on {connection_name}')\n    try:\n        yield connection_name\n        print('COMMIT')\n    except Exception as e:\n        print(f'ROLLBACK due to: {e}')\n        raise\n\nwith db_transaction('main_db') as conn:\n    print(f'Inserting data via {conn}')\n    print('Updating records')\n\nprint()\n\ntry:\n    with db_transaction('main_db') as conn:\n        print('Inserting data')\n        raise ValueError('Constraint violation')\nexcept ValueError:\n    print('Transaction failed')",
                        "BEGIN transaction on main_db\nInserting data via main_db\nUpdating records\nCOMMIT\n\nBEGIN transaction on main_db\nInserting data\nROLLBACK due to: Constraint violation\nTransaction failed")
                ),
                List.of(
                    "with statement guarantees __exit__ runs even if an exception occurs",
                    "Always use with open() for files — never manually call f.close()",
                    "__enter__ returns the resource, __exit__ handles cleanup",
                    "Return True from __exit__ to suppress an exception",
                    "@contextmanager with yield is simpler than a full class for most cases"
                ),
                "Always use with open() when working with files. It is not just cleaner — it guarantees the file is closed even if your code raises an exception midway through. A file left open can cause data corruption or resource leaks.",
                List.of(
                    "Not using with for file operations — file may not be closed if exception occurs",
                    "Forgetting yield in a @contextmanager function — raises RuntimeError"
                ),
                15, 8, "C"),

            conceptRich(py, "Regular Expressions — re module",
                "Regular expressions (regex) are patterns for matching, searching, and extracting text. Python's re module provides regex support.",
                "Regular expressions are a powerful language for describing text patterns.\n\nInstead of writing: 'check if this string starts with a digit, followed by two letters, followed by exactly four digits'\n\nYou write a pattern: r'\\d[A-Z]{2}\\d{4}'\n\nRegex is used in:\n- Validating input (email, phone number, password)\n- Extracting data from text (scraping, log parsing)\n- Search and replace operations\n- Splitting text on complex patterns\n\nThe r prefix (raw string) before the pattern prevents Python from interpreting backslashes — always use r'pattern'.",
                "1. Import and basic functions:\n- import re\n- re.match(pattern, string): match at start of string\n- re.search(pattern, string): find anywhere in string\n- re.findall(pattern, string): return list of all matches\n- re.sub(pattern, replace, string): replace matches\n- re.split(pattern, string): split on pattern\n\n2. Common pattern elements:\n- . any character except newline\n- \\d digit (0-9)\n- \\D non-digit\n- \\w word character (letters, digits, underscore)\n- \\W non-word character\n- \\s whitespace\n- ^ start of string\n- $ end of string\n- * zero or more\n- + one or more\n- ? zero or one\n- {n} exactly n times\n- {n,m} between n and m times\n- [abc] any of a, b, c\n- [^abc] any except a, b, c\n- (group) capturing group\n\n3. Flags:\n- re.IGNORECASE or re.I: case insensitive\n- re.MULTILINE or re.M: ^ and $ match each line",
                "import re\n\n# re.search — find anywhere\ntext = 'Phone: 9876543210, Email: ravi@gmail.com'\n\nphone = re.search(r'\\d{10}', text)\nif phone:\n    print('Phone:', phone.group())\n\n# re.findall — all matches\nemails = re.findall(r'[\\w.]+@[\\w.]+\\.\\w+', text)\nprint('Emails:', emails)\n\n# re.match — only at start\nif re.match(r'Phone', text):\n    print('Starts with Phone')\n\n# re.sub — replace\ncleaned = re.sub(r'\\s+', ' ', 'too   many    spaces')\nprint(cleaned)\n\n# Capturing groups\ndate_text = 'Date: 15-08-2024'\nmatch = re.search(r'(\\d{2})-(\\d{2})-(\\d{4})', date_text)\nif match:\n    print(f'Day: {match.group(1)}, Month: {match.group(2)}, Year: {match.group(3)}')\n\n# Email validation\ndef is_valid_email(email):\n    pattern = r'^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$'\n    return bool(re.match(pattern, email))\n\nprint(is_valid_email('ravi@gmail.com'))    # True\nprint(is_valid_email('invalid-email'))     # False",
                List.of(
                    new Concept.ConceptExample("Extract data from log file",
                        "Use regex to extract timestamps, log levels and messages.",
                        "import re\n\nlogs = [\n    '2024-01-15 10:30:45 ERROR Database connection failed',\n    '2024-01-15 10:31:02 INFO User Ravi logged in',\n    '2024-01-15 10:32:18 WARNING High memory usage: 85%'\n]\n\npattern = r'(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2}) (\\w+) (.+)'\n\nfor log in logs:\n    m = re.match(pattern, log)\n    if m:\n        date, time, level, message = m.groups()\n        print(f'[{level}] {time} - {message}')",
                        "[ERROR] 10:30:45 - Database connection failed\n[INFO] 10:31:02 - User Ravi logged in\n[WARNING] 10:32:18 - High memory usage: 85%"),
                    new Concept.ConceptExample("Validate phone and email",
                        "Use regex patterns to validate common input formats.",
                        "import re\n\ndef validate(label, value, pattern):\n    if re.match(pattern, value):\n        print(f'{label} \"{value}\" is valid')\n    else:\n        print(f'{label} \"{value}\" is invalid')\n\nvalidate('Phone', '9876543210', r'^[6-9]\\d{9}$')\nvalidate('Phone', '12345', r'^[6-9]\\d{9}$')\nvalidate('Email', 'ravi@gmail.com', r'^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$')\nvalidate('Email', 'notanemail', r'^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$')",
                        "Phone \"9876543210\" is valid\nPhone \"12345\" is invalid\nEmail \"ravi@gmail.com\" is valid\nEmail \"notanemail\" is invalid")
                ),
                List.of(
                    "Always use raw strings r'pattern' to avoid backslash issues",
                    "re.search finds anywhere in string, re.match only matches at the start",
                    "re.findall returns a list of all matches",
                    "Use () for capturing groups — access with match.group(1), match.group(2)",
                    "re.sub replaces all matches with the replacement string"
                ),
                "Compile regex patterns that you use multiple times: pattern = re.compile(r'...') then pattern.findall(text). This is faster because the pattern is only compiled once instead of on every call.",
                List.of(
                    "Forgetting the r prefix — r'\\d' vs '\\d' behaves differently",
                    "Using re.match expecting to match anywhere — it only matches at the start of string"
                ),
                20, 9, "C"),

            conceptRich(py, "File I/O",
                "Python's built-in open() function reads from and writes to files. Use the with statement to ensure files are always closed properly.",
                "Files are how programs save data permanently.\n\nPython makes file operations straightforward:\n- open(filename, mode) opens a file\n- read(), readlines() read content\n- write(), writelines() write content\n- with statement ensures the file closes automatically\n\nModes:\n- 'r': read (default)\n- 'w': write (creates or overwrites)\n- 'a': append (adds to end)\n- 'rb', 'wb': binary read/write",
                "1. Opening files:\n- open(filename, mode, encoding='utf-8')\n- Always use encoding='utf-8' for text files\n- Always use with open() — automatic close guaranteed\n\n2. Reading:\n- f.read(): entire file as one string\n- f.readline(): one line at a time\n- f.readlines(): list of all lines\n- for line in f: iterate line by line (memory efficient)\n\n3. Writing:\n- f.write(string): write a string\n- f.writelines(list): write a list of strings\n- Mode 'w' overwrites existing content\n- Mode 'a' appends to existing content\n\n4. File existence and paths:\n- import os\n- os.path.exists(path): check if file exists\n- os.path.join(dir, filename): build paths correctly\n- os.listdir(dir): list files in directory\n\n5. Working with JSON:\n- import json\n- json.dump(data, f): write Python object as JSON\n- json.load(f): read JSON file as Python object",
                "import os\nimport json\n\n# Write to a file\nwith open('students.txt', 'w', encoding='utf-8') as f:\n    f.write('Ravi,85\\n')\n    f.write('Priya,92\\n')\n    f.write('Arjun,78\\n')\n\n# Read entire file\nwith open('students.txt', 'r', encoding='utf-8') as f:\n    content = f.read()\n    print(content)\n\n# Read line by line\nwith open('students.txt', 'r', encoding='utf-8') as f:\n    for line in f:\n        name, marks = line.strip().split(',')\n        print(f'{name}: {marks}')\n\n# Append to file\nwith open('students.txt', 'a', encoding='utf-8') as f:\n    f.write('Sneha,88\\n')\n\n# File exists check\nprint(os.path.exists('students.txt'))   # True\n\n# JSON read/write\ndata = {'name': 'Ravi', 'marks': 85, 'subjects': ['Python', 'Maths']}\n\nwith open('data.json', 'w') as f:\n    json.dump(data, f, indent=2)\n\nwith open('data.json', 'r') as f:\n    loaded = json.load(f)\n    print(loaded)",
                List.of(
                    new Concept.ConceptExample("Read and process a CSV-like file",
                        "Read student data from a file and compute statistics.",
                        "# First create the file\nwith open('marks.txt', 'w') as f:\n    f.write('Ravi,85,90,78\\n')\n    f.write('Priya,92,88,95\\n')\n    f.write('Arjun,70,75,68\\n')\n\n# Now read and process\nwith open('marks.txt', 'r') as f:\n    for line in f:\n        parts = line.strip().split(',')\n        name = parts[0]\n        marks = list(map(int, parts[1:]))\n        avg = sum(marks) / len(marks)\n        print(f'{name}: avg = {avg:.1f}')",
                        "Ravi: avg = 84.3\nPriya: avg = 91.7\nArjun: avg = 71.0"),
                    new Concept.ConceptExample("Store and load config as JSON",
                        "Save application settings to JSON and load them back.",
                        "import json\n\nconfig = {\n    'app_name': 'LearnToEarn',\n    'version': '2.0',\n    'max_users': 1000,\n    'features': ['quiz', 'roadmap', 'badges']\n}\n\n# Save config\nwith open('config.json', 'w') as f:\n    json.dump(config, f, indent=2)\nprint('Config saved')\n\n# Load config\nwith open('config.json', 'r') as f:\n    loaded = json.load(f)\n\nprint(loaded['app_name'])\nprint(loaded['features'])",
                        "Config saved\nLearnToEarn\n['quiz', 'roadmap', 'badges']")
                ),
                List.of(
                    "Always use with open() — it guarantees the file is closed even if an error occurs",
                    "Always specify encoding='utf-8' for text files to handle special characters",
                    "Mode 'w' overwrites, mode 'a' appends — choose carefully",
                    "for line in f: is memory-efficient — reads one line at a time",
                    "Use json.dump/json.load for structured data storage"
                ),
                "When reading a text file line by line, always call line.strip() to remove the trailing newline character. Without it, lines will have a hidden \\n at the end that causes subtle bugs when comparing or processing the text.",
                List.of(
                    "Forgetting with and manually handling open/close — file may not close on error",
                    "Using mode 'w' when you meant 'a' — overwrites existing content",
                    "Not stripping newlines when reading lines — line.strip() is almost always needed"
                ),
                20, 10, "C"),

            conceptRich(py, "Custom Exceptions",
                "Custom exceptions let you define application-specific error types that make error handling more precise and your code more readable.",
                "Python has many built-in exceptions — ValueError, TypeError, FileNotFoundError. But sometimes you need errors that are specific to your application.\n\nFor example:\n- InsufficientFundsError for a banking app\n- InvalidAgeError for a registration system\n- AuthenticationError for a login system\n\nCustom exceptions:\n- Make your error messages clear and specific\n- Allow callers to catch your specific errors separately from generic ones\n- Carry extra information about what went wrong",
                "1. Creating a custom exception:\n- Inherit from Exception or a more specific built-in exception\n- class MyError(Exception): pass\n\n2. Adding custom attributes:\n- Define __init__ to store extra context\n- super().__init__(message) calls Exception's init\n\n3. Exception hierarchy:\n- Inherit from ValueError if it is a value-related error\n- Inherit from RuntimeError for general runtime errors\n- Inherit from Exception for application errors\n- Create a base exception for your application then specific ones under it\n\n4. Raising custom exceptions:\n- raise MyError('message') or raise MyError(value, message)\n\n5. Catching specific vs general:\n- except InsufficientFundsError: catches only your exception\n- except Exception: catches everything",
                "# Basic custom exception\nclass ValidationError(Exception):\n    pass\n\nclass InsufficientFundsError(Exception):\n    def __init__(self, balance, amount):\n        self.balance = balance\n        self.amount = amount\n        super().__init__(f'Cannot withdraw {amount}. Balance is only {balance}')\n\n# Application exception hierarchy\nclass AppError(Exception):\n    pass\n\nclass AuthError(AppError):\n    pass\n\nclass NotFoundError(AppError):\n    pass\n\n# Using custom exceptions\nclass BankAccount:\n    def __init__(self, balance):\n        self.balance = balance\n\n    def withdraw(self, amount):\n        if amount <= 0:\n            raise ValidationError('Amount must be positive')\n        if amount > self.balance:\n            raise InsufficientFundsError(self.balance, amount)\n        self.balance -= amount\n        return amount\n\nacc = BankAccount(1000)\n\ntry:\n    acc.withdraw(1500)\nexcept InsufficientFundsError as e:\n    print(e)\n    print(f'Short by: {e.amount - e.balance}')\n\ntry:\n    acc.withdraw(-50)\nexcept ValidationError as e:\n    print(e)",
                List.of(
                    new Concept.ConceptExample("Exception hierarchy for an API",
                        "Create a hierarchy of custom exceptions for an application.",
                        "class APIError(Exception):\n    def __init__(self, message, status_code=500):\n        super().__init__(message)\n        self.status_code = status_code\n\nclass NotFoundError(APIError):\n    def __init__(self, resource):\n        super().__init__(f'{resource} not found', 404)\n\nclass UnauthorizedError(APIError):\n    def __init__(self):\n        super().__init__('Authentication required', 401)\n\ndef get_user(user_id, logged_in):\n    if not logged_in:\n        raise UnauthorizedError()\n    if user_id not in [1, 2, 3]:\n        raise NotFoundError(f'User {user_id}')\n    return {'id': user_id, 'name': 'Ravi'}\n\nfor uid, auth in [(1, True), (5, True), (1, False)]:\n    try:\n        user = get_user(uid, auth)\n        print('Found:', user)\n    except APIError as e:\n        print(f'Error {e.status_code}: {e}')",
                        "Found: {'id': 1, 'name': 'Ravi'}\nError 404: User 5 not found\nError 401: Authentication required")
                ),
                List.of(
                    "Inherit from Exception (or a subclass) to create custom exceptions",
                    "Use super().__init__(message) to set the error message",
                    "Add custom attributes in __init__ to carry extra context about the error",
                    "Create a base exception for your app then specific ones — allows catching all app errors with one except",
                    "Catch specific custom exceptions before general ones"
                ),
                "Create a base exception for your application: class AppError(Exception). Then inherit specific exceptions from it. This lets you catch all application errors with except AppError while still being able to catch specific ones with except NotFoundError.",
                List.of(
                    "Not calling super().__init__(message) — the error message will not display properly",
                    "Making every exception custom — only create custom exceptions for domain-specific errors"
                ),
                15, 11, "C"),

            conceptRich(py, "Modules and Packages",
                "A module is a Python file you can import. A package is a directory of modules. Modules let you organise code into reusable, separate files.",
                "As your programs grow, keeping everything in one file becomes unmanageable.\n\nModules let you split code into separate files:\n- math_utils.py with number functions\n- string_utils.py with text functions\n- database.py with DB operations\n\nYou import what you need:\nimport math_utils\nfrom string_utils import clean_text\n\nA package is a folder of modules with an __init__.py file. Python's standard library is a large collection of packages — os, json, re, datetime, collections, itertools.\n\nYou can install third-party packages using pip.",
                "1. Creating a module:\n- Any .py file is a module\n- Import with: import module_name\n- Use: module_name.function()\n\n2. Import styles:\n- import math: imports the module\n- from math import sqrt: imports specific name\n- from math import sqrt, pi: import multiple\n- from math import *: import all (avoid this)\n- import numpy as np: import with alias\n\n3. __name__ == '__main__':\n- When a file is run directly, __name__ is '__main__'\n- When imported, __name__ is the module name\n- Wrap runnable code in if __name__ == '__main__': to prevent it running on import\n\n4. Packages:\n- A directory with __init__.py is a package\n- Import: from package.module import function\n\n5. Useful standard library modules:\n- os: file system, environment variables\n- sys: Python interpreter info\n- datetime: dates and times\n- json: JSON encode/decode\n- random: random numbers\n- collections: Counter, defaultdict, deque\n- itertools: efficient iterators",
                "# Using standard library modules\nimport os\nimport sys\nimport random\nfrom datetime import datetime\nfrom collections import Counter\n\n# os — file system\nprint(os.getcwd())              # current directory\nprint(os.path.exists('test.txt'))\n\n# sys — interpreter info\nprint(sys.version)\nprint(sys.platform)\n\n# random\nprint(random.randint(1, 100))\nprint(random.choice(['Ravi', 'Priya', 'Arjun']))\n\n# datetime\nnow = datetime.now()\nprint(now.strftime('%d-%m-%Y %H:%M'))\n\n# Counter\nwords = ['python', 'is', 'great', 'python', 'is', 'python']\ncount = Counter(words)\nprint(count)\nprint(count.most_common(2))\n\n# Creating your own module\n# In file: utils.py\n# def greet(name):\n#     return f'Hello, {name}!'\n#\n# if __name__ == '__main__':\n#     print(greet('World'))   # only runs when executed directly\n#\n# In another file:\n# from utils import greet\n# print(greet('Ravi'))",
                List.of(
                    new Concept.ConceptExample("Using datetime and os modules",
                        "Combine standard library modules for a practical task.",
                        "import os\nfrom datetime import datetime\n\ndef create_log_entry(message):\n    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')\n    log_dir = 'logs'\n\n    if not os.path.exists(log_dir):\n        os.makedirs(log_dir)\n\n    log_file = os.path.join(log_dir, 'app.log')\n    with open(log_file, 'a') as f:\n        f.write(f'[{timestamp}] {message}\\n')\n    print(f'Logged: {message}')\n\ncreate_log_entry('Application started')\ncreate_log_entry('User Ravi logged in')\ncreate_log_entry('Database connected')",
                        "Logged: Application started\nLogged: User Ravi logged in\nLogged: Database connected")
                ),
                List.of(
                    "Any .py file is a module — import it by filename without the .py extension",
                    "Use if __name__ == '__main__': to prevent code from running when the module is imported",
                    "from module import name imports a specific name into the current namespace",
                    "import module as alias creates a shorter name: import numpy as np",
                    "Standard library modules (os, json, datetime, random) need no installation"
                ),
                "Always include if __name__ == '__main__': in scripts that have runnable code. This makes the file both importable as a module and runnable as a script without side effects.",
                List.of(
                    "Naming your file the same as a standard library module — import math imports your file instead",
                    "Using from module import * — pollutes the namespace and makes it unclear where names come from"
                ),
                15, 12, "C")
        );

        conceptRepository.saveAll(concepts);
        py.setTotalConcepts(concepts.size());
        subjectRepository.save(py);
        System.out.println("✅ Python Advanced seeded — " + concepts.size() + " concepts");
    }

    // ─── PYTHON PROFESSIONAL ─────────────────────────────────────────────────
    private void seedPythonProfessional() {
        Subject py = subjectRepository.save(sub(
            "Python Professional",
            "Type hints, dataclasses, functools, itertools, collections, async/await, threading, testing, logging, and packaging",
            "🎯", "#F59E0B", "A"
        ));
        py.setOverview("Python Professional covers the tools and practices that senior Python developers use every day. Type hints make code self-documenting, dataclasses eliminate boilerplate, functools and itertools enable elegant functional patterns, async/await handles concurrent I/O, and testing ensures reliability. This is what separates professional Python from beginner Python.");
        py.setWhyLearn("Senior roles, code reviews, and production codebases expect these skills. Type hints are now standard in all modern Python projects. async/await is required for APIs and microservices. Testing is mandatory in every professional team. These topics appear in senior-level interviews.");
        py.setForWho("Students who have completed Python Advanced. You should be comfortable with OOP, decorators, generators, and file I/O.");
        py.setPrerequisites(List.of("Python Advanced completed", "Comfortable with OOP and decorators", "Comfortable with functions and modules"));
        py.setOutcomes(List.of(
            "Add type hints to functions and variables for better code documentation",
            "Use @dataclass to create data classes without boilerplate",
            "Use functools and itertools for elegant functional programming",
            "Use collections.Counter, defaultdict and namedtuple for common data problems",
            "Write async functions with async/await for concurrent I/O operations",
            "Write unit tests with unittest and pytest",
            "Use the logging module instead of print for production code",
            "Structure a Python project with proper packaging"
        ));
        py.setWhatYouWillBuild(List.of(
            "A fully type-hinted data processing module",
            "A test suite using pytest for a class-based application",
            "An async web scraper using asyncio"
        ));
        py.setToolsRequired(List.of("Python 3.10+", "pytest (pip install pytest)", "VS Code with Pylance extension"));
        py.setDifficulty("Advanced");
        py.setEstimatedHours(12);
        py.setCareerUse("Required for senior Python developer, backend engineer, data engineer, and ML engineer roles. Type hints and testing are mandatory in every production Python codebase. async/await is essential for modern API development.");
        subjectRepository.save(py);

        List<Concept> concepts = List.of(

            conceptRich(py, "Type Hints and typing module",
                "Type hints annotate variables and function parameters with expected types. They don't enforce types at runtime but improve readability, IDE support, and catch bugs early with tools like mypy.",
                "Python is dynamically typed — you never have to declare types. But as projects grow, this becomes a problem. You call a function and wonder: what type does this parameter expect? What does this function return?\n\nType hints solve this. You add type annotations as documentation:\n\ndef greet(name: str) -> str:\n    return f'Hello {name}'\n\nNow the function signature tells you exactly what goes in and what comes out. IDEs use this for autocomplete and error highlighting. mypy checks these statically before running.\n\nType hints are optional and not enforced at runtime — but modern Python teams use them everywhere.",
                "1. Basic type hints:\n- Variable: name: str = 'Ravi'\n- Function parameter: def func(x: int, y: float) -> str:\n- Return type: -> str, -> None, -> bool\n\n2. typing module (Python 3.9+ uses built-in generics):\n- List[int]: list of integers (older: from typing import List)\n- Dict[str, int]: dict with str keys and int values\n- Optional[str]: either str or None\n- Union[int, str]: either int or str\n- Tuple[int, str]: tuple with specific types\n- Callable[[int], str]: function taking int, returning str\n- Any: any type\n\n3. Python 3.10+ modern syntax:\n- int | str instead of Union[int, str]\n- str | None instead of Optional[str]\n- list[int] instead of List[int] (no import needed)\n\n4. Type checking:\n- mypy: pip install mypy, run: mypy yourfile.py\n- pyright: VS Code's built-in checker with Pylance\n\n5. TypedDict and dataclasses:\n- TypedDict for dict with specific key types\n- Covered in next concept",
                "from typing import Optional, Union\n\n# Basic annotations\ndef add(a: int, b: int) -> int:\n    return a + b\n\ndef greet(name: str, title: Optional[str] = None) -> str:\n    if title:\n        return f'Hello, {title} {name}'\n    return f'Hello, {name}'\n\n# Python 3.10+ union syntax\ndef process(value: int | str) -> str:\n    return str(value).upper()\n\n# Complex types\nfrom typing import List, Dict, Tuple\n\ndef get_top_students(marks: Dict[str, int], n: int) -> List[Tuple[str, int]]:\n    sorted_marks = sorted(marks.items(), key=lambda x: x[1], reverse=True)\n    return sorted_marks[:n]\n\n# Variable annotations\nstudent_name: str = 'Ravi'\nmarks: list[int] = [85, 92, 78]\ngrade_map: dict[str, str] = {}\n\nresult = get_top_students({'Ravi': 85, 'Priya': 92, 'Arjun': 78}, 2)\nprint(result)",
                List.of(
                    new Concept.ConceptExample("Type hinted class methods",
                        "Add type hints to a class to document all attributes and methods.",
                        "from typing import Optional\n\nclass Student:\n    def __init__(self, name: str, roll_no: str, marks: int) -> None:\n        self.name: str = name\n        self.roll_no: str = roll_no\n        self.marks: int = marks\n\n    def get_grade(self) -> str:\n        if self.marks >= 90: return 'A'\n        if self.marks >= 75: return 'B'\n        if self.marks >= 60: return 'C'\n        return 'D'\n\n    @classmethod\n    def from_dict(cls, data: dict[str, str | int]) -> 'Student':\n        return cls(\n            name=str(data['name']),\n            roll_no=str(data['roll_no']),\n            marks=int(data['marks'])\n        )\n\ns = Student.from_dict({'name': 'Ravi', 'roll_no': 'CS001', 'marks': 85})\nprint(s.name, s.get_grade())",
                        "Ravi B")
                ),
                List.of(
                    "Type hints are for documentation and tooling — Python does not enforce them at runtime",
                    "Use -> None for functions that return nothing",
                    "Optional[str] means the value can be str or None",
                    "Python 3.10+: use int | str instead of Union[int, str]",
                    "IDEs use type hints for autocomplete and error highlighting"
                ),
                "Add type hints to all function signatures. You don't need to annotate every local variable — focus on public function parameters and return types. This gives you and your IDE the most value for the least effort.",
                List.of(
                    "Thinking type hints enforce types — they are just annotations, Python ignores them at runtime",
                    "Importing List, Dict from typing in Python 3.9+ — use built-in list, dict directly"
                ),
                15, 1, "C"),

            conceptRich(py, "Dataclasses — @dataclass",
                "@dataclass automatically generates __init__, __repr__, and __eq__ for classes that mainly store data. It eliminates boilerplate code.",
                "If you have a class that mainly holds data — a Student with name, marks, grade — you write a lot of boilerplate:\n- __init__ to accept and store all fields\n- __repr__ to print nicely\n- __eq__ to compare two students\n\n@dataclass generates all of this automatically from the field declarations.\n\nInstead of 20 lines of boilerplate, you write:\n@dataclass\nclass Student:\n    name: str\n    marks: int\n    grade: str = 'E'\n\nPython generates __init__(name, marks, grade='E'), a nice __repr__, and == comparison for you.",
                "1. Basic @dataclass:\n- from dataclasses import dataclass\n- @dataclass decorator on a class\n- Fields declared as class-level annotations with types\n- __init__, __repr__, __eq__ generated automatically\n\n2. Default values:\n- grade: str = 'E' — simple default\n- field(default_factory=list) for mutable defaults like lists\n\n3. Options:\n- @dataclass(frozen=True): immutable — raises FrozenInstanceError on assignment\n- @dataclass(order=True): generates __lt__, __gt__ etc. for sorting\n- @dataclass(eq=False): skip generating __eq__\n\n4. Post-init:\n- def __post_init__(self): runs after __init__\n- Use for validation or computed fields\n\n5. Comparison with NamedTuple:\n- dataclass is mutable by default, NamedTuple is always immutable\n- dataclass supports inheritance, methods, and post_init",
                "from dataclasses import dataclass, field\n\n@dataclass\nclass Student:\n    name: str\n    roll_no: str\n    marks: int\n    subjects: list = field(default_factory=list)\n\n    def __post_init__(self):\n        if not 0 <= self.marks <= 100:\n            raise ValueError(f'Invalid marks: {self.marks}')\n\n    @property\n    def grade(self) -> str:\n        if self.marks >= 90: return 'A'\n        if self.marks >= 75: return 'B'\n        if self.marks >= 60: return 'C'\n        return 'D'\n\n# __init__ is auto-generated\ns1 = Student('Ravi', 'CS001', 85, ['Python', 'Maths'])\ns2 = Student('Priya', 'CS002', 92)\n\nprint(s1)          # __repr__ auto-generated\nprint(s2)\nprint(s1 == s2)    # __eq__ auto-generated\nprint(s1.grade)\n\n# Frozen dataclass — immutable\n@dataclass(frozen=True)\nclass Point:\n    x: float\n    y: float\n\np = Point(3.0, 4.0)\nprint(p)\n# p.x = 5.0   # FrozenInstanceError",
                List.of(
                    new Concept.ConceptExample("Dataclass with ordering",
                        "Use order=True to enable sorting of dataclass instances.",
                        "from dataclasses import dataclass\n\n@dataclass(order=True)\nclass Product:\n    price: float\n    name: str\n\nproducts = [\n    Product(499.0, 'Pen'),\n    Product(1299.0, 'Book'),\n    Product(299.0, 'Pencil'),\n    Product(899.0, 'Bag')\n]\n\nproducts.sort()\nfor p in products:\n    print(f'{p.name}: Rs.{p.price}')",
                        "Pencil: Rs.299.0\nPen: Rs.499.0\nBag: Rs.899.0\nBook: Rs.1299.0")
                ),
                List.of(
                    "@dataclass auto-generates __init__, __repr__, and __eq__",
                    "Use field(default_factory=list) for mutable default values like lists or dicts",
                    "frozen=True makes the dataclass immutable — good for hash keys",
                    "order=True enables comparison operators for sorting",
                    "__post_init__ runs after the generated __init__ — use for validation"
                ),
                "Use @dataclass whenever you are creating a class mainly to hold data. It eliminates boilerplate and makes the class easier to read. Use frozen=True for data that should not change after creation.",
                List.of(
                    "Using a mutable default like [] directly — use field(default_factory=list) instead",
                    "Expecting @dataclass to generate __hash__ automatically when eq=True — it sets __hash__ to None"
                ),
                15, 2, "C"),

            conceptRich(py, "functools — partial, lru_cache, wraps",
                "functools provides higher-order functions. partial creates new functions with pre-filled arguments. lru_cache caches function results for speed. wraps preserves function metadata in decorators.",
                "functools is a standard library module with tools for working with functions.\n\npartial: Create a specialised version of a function with some arguments already filled in.\n- multiply = lambda x, y: x * y\n- double = partial(multiply, 2)\n- double(5) returns 10\n\nlru_cache: Cache (remember) the results of expensive function calls.\n- If the function is called with the same arguments again, return the cached result immediately instead of recomputing.\n\nwraps: Used inside decorators to preserve the original function's name and docstring.",
                "1. functools.partial:\n- Creates a new function with some arguments pre-filled\n- partial(func, arg1, arg2) returns a new callable\n- Useful for creating specialised functions from general ones\n\n2. functools.lru_cache:\n- @lru_cache(maxsize=128) caches up to 128 results\n- @lru_cache(maxsize=None) for unlimited cache\n- @cache (Python 3.9+) — simpler alias for unlimited cache\n- Works only with hashable (immutable) arguments\n- cache_info() shows hits, misses, current size\n- cache_clear() clears the cache\n\n3. functools.wraps:\n- Used in decorators: @functools.wraps(func)\n- Copies __name__, __doc__, __module__ from original function to wrapper\n- Without it, all decorated functions look the same to debuggers\n\n4. functools.reduce:\n- reduce(func, iterable): applies function cumulatively\n- reduce(lambda a, b: a + b, [1,2,3,4]) → 10",
                "import functools\n\n# partial — pre-fill arguments\ndef power(base, exponent):\n    return base ** exponent\n\nsquare = functools.partial(power, exponent=2)\ncube = functools.partial(power, exponent=3)\n\nprint(square(4))   # 16\nprint(cube(3))     # 27\n\n# lru_cache — cache expensive results\n@functools.lru_cache(maxsize=128)\ndef fibonacci(n):\n    if n < 2:\n        return n\n    return fibonacci(n-1) + fibonacci(n-2)\n\nimport time\nstart = time.time()\nprint(fibonacci(35))\nprint(f'First call: {time.time() - start:.4f}s')\n\nstart = time.time()\nprint(fibonacci(35))   # instant from cache\nprint(f'Cached call: {time.time() - start:.6f}s')\n\nprint(fibonacci.cache_info())\n\n# functools.reduce\nfrom functools import reduce\nnumbers = [1, 2, 3, 4, 5]\ntotal = reduce(lambda a, b: a + b, numbers)\nprint(total)   # 15",
                List.of(
                    new Concept.ConceptExample("Cache database-like lookups",
                        "Use lru_cache to avoid repeated expensive lookups.",
                        "import functools\nimport time\n\n# Simulate expensive DB query\n@functools.lru_cache(maxsize=100)\ndef get_user_by_id(user_id: int) -> dict:\n    time.sleep(0.1)   # simulate DB delay\n    users = {\n        1: {'name': 'Ravi', 'role': 'admin'},\n        2: {'name': 'Priya', 'role': 'student'}\n    }\n    return users.get(user_id)\n\n# First call — hits DB\nstart = time.time()\nprint(get_user_by_id(1))\nprint(f'First: {time.time()-start:.3f}s')\n\n# Second call — from cache\nstart = time.time()\nprint(get_user_by_id(1))\nprint(f'Cached: {time.time()-start:.4f}s')\n\nprint(get_user_by_id.cache_info())",
                        "{'name': 'Ravi', 'role': 'admin'}\nFirst: 0.101s\n{'name': 'Ravi', 'role': 'admin'}\nCached: 0.0001s\nCacheInfo(hits=1, misses=1, maxsize=100, currsize=1)")
                ),
                List.of(
                    "partial(func, arg) creates a new function with arg pre-filled",
                    "@lru_cache caches function results — same arguments = instant return",
                    "lru_cache only works with hashable arguments (no lists or dicts as args)",
                    "@functools.wraps(func) in decorators preserves function name and docstring",
                    "cache_clear() resets the lru_cache, cache_info() shows cache statistics"
                ),
                "Use @lru_cache on any pure function that is called repeatedly with the same arguments — fibonacci, factorial, expensive computations. It trades memory for speed and can make dramatic performance improvements.",
                List.of(
                    "Using lru_cache with mutable arguments like lists — raises TypeError",
                    "Forgetting @functools.wraps in decorators — wrapped functions lose their identity"
                ),
                20, 3, "B"),

            conceptRich(py, "itertools — chain, product, groupby",
                "itertools provides memory-efficient iterators for combinatorics, chaining, grouping, and slicing sequences.",
                "itertools is a standard library module of building blocks for working with sequences efficiently.\n\nAll itertools functions return lazy iterators — they produce values one at a time without creating full lists in memory.\n\nMost useful functions:\n- chain(): combine multiple iterables as one\n- product(): all combinations (like nested loops)\n- groupby(): group consecutive items by a key\n- islice(): slice an iterator without a list\n- combinations(): all k-combinations\n- permutations(): all arrangements",
                "1. itertools.chain(*iterables):\n- Chains multiple iterables together as one\n- chain([1,2], [3,4], [5]) → 1 2 3 4 5\n\n2. itertools.product(*iterables, repeat=1):\n- Cartesian product — like nested for loops\n- product('AB', '12') → A1 A2 B1 B2\n\n3. itertools.groupby(iterable, key=None):\n- Groups consecutive items with the same key value\n- Input must be sorted by the key first\n- Returns (key, group_iterator) pairs\n\n4. itertools.islice(iterable, stop) or islice(iterable, start, stop, step):\n- Slices an iterator — like list slicing but lazy\n\n5. itertools.combinations(iterable, r):\n- All r-length combinations without repetition\n\n6. itertools.permutations(iterable, r):\n- All r-length ordered arrangements",
                "import itertools\n\n# chain — combine iterables\nlist1 = [1, 2, 3]\nlist2 = [4, 5, 6]\nlist3 = [7, 8, 9]\n\ncombined = list(itertools.chain(list1, list2, list3))\nprint(combined)\n\n# product — cartesian product\nsizes = ['S', 'M', 'L']\ncolours = ['Red', 'Blue']\nvariants = list(itertools.product(sizes, colours))\nprint(variants)\n\n# groupby — group sorted data\nstudents = [\n    {'name': 'Ravi', 'grade': 'A'},\n    {'name': 'Priya', 'grade': 'A'},\n    {'name': 'Arjun', 'grade': 'B'},\n    {'name': 'Sneha', 'grade': 'B'},\n    {'name': 'Kiran', 'grade': 'C'},\n]\n\nfor grade, group in itertools.groupby(students, key=lambda s: s['grade']):\n    names = [s['name'] for s in group]\n    print(f\"Grade {grade}: {', '.join(names)}\")\n\n# combinations\nteam = ['Ravi', 'Priya', 'Arjun', 'Sneha']\npairs = list(itertools.combinations(team, 2))\nprint(f'{len(pairs)} pairs:', pairs[:3], '...')\n\n# islice — take first N from generator\ndef infinite_counter():\n    n = 0\n    while True:\n        yield n\n        n += 1\n\nfirst_10 = list(itertools.islice(infinite_counter(), 10))\nprint(first_10)",
                List.of(
                    new Concept.ConceptExample("Generate test data combinations",
                        "Use product() to generate all test input combinations.",
                        "import itertools\n\nusers = ['admin', 'student', 'guest']\nactions = ['view', 'edit', 'delete']\nstatus = ['active', 'inactive']\n\ntest_cases = list(itertools.product(users, actions, status))\nprint(f'Total test cases: {len(test_cases)}')\nfor case in test_cases[:5]:\n    print(case)",
                        "Total test cases: 18\n('admin', 'view', 'active')\n('admin', 'view', 'inactive')\n('admin', 'edit', 'active')\n('admin', 'edit', 'inactive')\n('admin', 'delete', 'active')")
                ),
                List.of(
                    "All itertools functions return lazy iterators — wrap with list() to see all values",
                    "groupby requires data sorted by the same key — otherwise groups are not merged",
                    "chain() merges multiple iterables without creating a new list",
                    "product() is equivalent to nested for loops",
                    "islice() lets you take N items from any iterator without converting to a list"
                ),
                "Remember that groupby only groups consecutive items with the same key. Always sort your data by the grouping key first — otherwise you will get multiple groups for the same key.",
                List.of(
                    "Using groupby without sorting first — results in multiple separate groups for same key",
                    "Forgetting to wrap itertools results with list() when you need to print or index"
                ),
                20, 4, "B"),

            conceptRich(py, "collections — Counter, defaultdict, namedtuple",
                "The collections module provides specialised data structures: Counter for counting, defaultdict to avoid KeyError, namedtuple for readable tuples.",
                "The collections module has data structures that solve common problems more elegantly than plain dicts and lists.\n\nCounter: count occurrences of items automatically.\nInstead of manually building {word: count, ...} with a loop.\n\ndefaultdict: a dict that provides a default value when a key is missing.\nInstead of checking if key exists before appending.\n\nnamedtuple: a tuple where fields have names.\nInstead of point[0] and point[1], you write point.x and point.y.",
                "1. Counter:\n- Counter(iterable) counts occurrences\n- Access counts: counter['word']\n- Most common: counter.most_common(n)\n- Supports + - operations between counters\n\n2. defaultdict:\n- defaultdict(default_factory)\n- default_factory is called when key is missing\n- defaultdict(int): missing key → 0\n- defaultdict(list): missing key → []\n- defaultdict(set): missing key → set()\n- No more KeyError for missing keys\n\n3. namedtuple:\n- namedtuple('TypeName', ['field1', 'field2'])\n- Creates a tuple subclass with named fields\n- Immutable like a tuple\n- Lighter than a class — no __dict__\n- Access by name: point.x or by index: point[0]\n\n4. deque (double-ended queue):\n- Fast append and pop from both ends\n- appendleft(), popleft() are O(1) unlike list\n- Useful for queues, sliding windows",
                "from collections import Counter, defaultdict, namedtuple, deque\n\n# Counter\nwords = 'the quick brown fox jumps over the lazy fox the'.split()\ncount = Counter(words)\nprint(count)\nprint(count.most_common(3))\nprint(count['the'])   # 3\n\n# defaultdict(list) — group items\ngrades = [('Ravi', 'A'), ('Priya', 'A'), ('Arjun', 'B'), ('Sneha', 'B'), ('Kiran', 'C')]\n\nby_grade = defaultdict(list)\nfor name, grade in grades:\n    by_grade[grade].append(name)\nprint(dict(by_grade))\n\n# defaultdict(int) — word frequency\nfrequency = defaultdict(int)\nfor word in words:\n    frequency[word] += 1   # no KeyError even on first access\nprint(dict(frequency))\n\n# namedtuple\nPoint = namedtuple('Point', ['x', 'y'])\nStudent = namedtuple('Student', ['name', 'roll', 'marks'])\n\np = Point(3.0, 4.0)\nprint(p.x, p.y)\nprint(p[0], p[1])   # also works by index\n\ns = Student('Ravi', 'CS001', 85)\nprint(s.name, s.marks)\nprint(s._asdict())   # convert to dict",
                List.of(
                    new Concept.ConceptExample("Analyse word frequency in text",
                        "Use Counter to find most common words in a text.",
                        "from collections import Counter\n\ntext = '''\nPython is easy to learn. Python is powerful.\nPython is used in web development and data science.\nData science uses Python extensively.\n'''\n\nwords = text.lower().split()\n# Remove punctuation\nwords = [w.strip('.,!?') for w in words]\n\ncount = Counter(words)\nprint('Top 5 words:')\nfor word, freq in count.most_common(5):\n    print(f'  {word}: {freq}')",
                        "Top 5 words:\n  python: 4\n  is: 3\n  data: 2\n  science: 2\n  in: 1"),
                    new Concept.ConceptExample("Group data with defaultdict",
                        "Group students by department using defaultdict.",
                        "from collections import defaultdict\n\nstudents = [\n    ('Ravi', 'CSE'), ('Priya', 'ECE'),\n    ('Arjun', 'CSE'), ('Sneha', 'MECH'),\n    ('Kiran', 'ECE'), ('Divya', 'CSE')\n]\n\nby_dept = defaultdict(list)\nfor name, dept in students:\n    by_dept[dept].append(name)\n\nfor dept, members in sorted(by_dept.items()):\n    print(f'{dept}: {members}')",
                        "CSE: ['Ravi', 'Arjun', 'Divya']\nECE: ['Priya', 'Kiran']\nMECH: ['Sneha']")
                ),
                List.of(
                    "Counter(iterable) counts occurrences, most_common(n) gives top n",
                    "defaultdict(list) prevents KeyError — missing key returns [] automatically",
                    "namedtuple creates a tuple with named fields — more readable than index-based access",
                    "deque is faster than list for appending/popping from the left",
                    "Use defaultdict(int) for frequency counting instead of checking if key exists"
                ),
                "Use defaultdict(list) whenever you are grouping items into lists by a key. It saves you the pattern of if key not in d: d[key] = [] before appending.",
                List.of(
                    "Passing a value to defaultdict instead of a factory: defaultdict(0) is wrong, use defaultdict(int)",
                    "Forgetting that namedtuple is immutable — you cannot change field values after creation"
                ),
                20, 5, "C"),

            conceptRich(py, "async/await and asyncio",
                "async/await enables concurrent I/O operations without threads. An async function pauses at await points, letting other tasks run instead of blocking.",
                "Normal Python code is synchronous — one thing at a time. If your function waits for a network response, the entire program waits.\n\nasync/await changes this. An async function can pause at an await expression, letting Python run other async tasks while waiting.\n\nThis is not multithreading. It is cooperative concurrency — tasks voluntarily yield control at await points. This makes it perfect for I/O-heavy work: web requests, database queries, file operations.\n\nFor CPU-heavy work (calculations, ML), use multiprocessing instead.",
                "1. async def:\n- Defines a coroutine function\n- Calling it returns a coroutine object — it does not run immediately\n- Must be awaited or run with asyncio.run()\n\n2. await:\n- Pauses the current coroutine until the awaited operation completes\n- Can only be used inside async def functions\n- Other coroutines can run during the pause\n\n3. asyncio.run(coro):\n- Runs the event loop with the given coroutine\n- Entry point for async programs\n\n4. asyncio.gather(*coros):\n- Runs multiple coroutines concurrently\n- Returns results when all complete\n\n5. asyncio.sleep(seconds):\n- Async version of time.sleep()\n- Pauses current coroutine, lets others run\n\n6. When to use async:\n- Network requests, database queries, file I/O\n- NOT for CPU-heavy computation (use multiprocessing)",
                "import asyncio\nimport time\n\n# Sync version — sequential\ndef fetch_sync(url):\n    time.sleep(1)   # simulate network delay\n    return f'Data from {url}'\n\n# Async version — concurrent\nasync def fetch_async(url):\n    await asyncio.sleep(1)   # yields control\n    return f'Data from {url}'\n\nasync def fetch_all():\n    urls = ['api/users', 'api/products', 'api/orders']\n\n    # Sequential — 3 seconds total\n    start = time.time()\n    results = []\n    for url in urls:\n        r = await fetch_async(url)\n        results.append(r)\n    print(f'Sequential: {time.time()-start:.1f}s')\n\n    # Concurrent — ~1 second total\n    start = time.time()\n    results = await asyncio.gather(*[fetch_async(url) for url in urls])\n    print(f'Concurrent: {time.time()-start:.1f}s')\n    return results\n\nresults = asyncio.run(fetch_all())\nfor r in results:\n    print(r)",
                List.of(
                    new Concept.ConceptExample("Async task pipeline",
                        "Run multiple independent async tasks concurrently with gather.",
                        "import asyncio\n\nasync def process_user(user_id: int) -> dict:\n    await asyncio.sleep(0.5)   # simulate DB query\n    return {'id': user_id, 'name': f'User{user_id}', 'active': True}\n\nasync def main():\n    user_ids = [1, 2, 3, 4, 5]\n\n    import time\n    start = time.time()\n\n    # Process all users concurrently\n    users = await asyncio.gather(*[process_user(uid) for uid in user_ids])\n\n    print(f'Fetched {len(users)} users in {time.time()-start:.2f}s')\n    for u in users:\n        print(f\"  {u['id']}: {u['name']}\")\n\nasyncio.run(main())",
                        "Fetched 5 users in 0.50s\n  1: User1\n  2: User2\n  3: User3\n  4: User4\n  5: User5")
                ),
                List.of(
                    "async def defines a coroutine — it must be awaited to run",
                    "await can only be used inside async def functions",
                    "asyncio.gather() runs multiple coroutines concurrently",
                    "asyncio.run() is the entry point — starts the event loop",
                    "Use async for I/O-bound tasks, multiprocessing for CPU-bound tasks"
                ),
                "Use asyncio.gather() to run multiple independent async operations concurrently. Fetching 10 URLs one by one takes 10x the time. gather() runs them all at once, taking only as long as the slowest single request.",
                List.of(
                    "Calling an async function without await — returns a coroutine object, not the result",
                    "Using time.sleep() in async code — blocks the entire event loop. Use asyncio.sleep()",
                    "Using async for CPU-heavy tasks — async only helps with I/O wait"
                ),
                20, 6, "B"),

            conceptRich(py, "Threading vs Multiprocessing",
                "Threading runs multiple threads in the same process for I/O-bound tasks. Multiprocessing creates separate processes for CPU-bound tasks to bypass Python's GIL.",
                "Python has a Global Interpreter Lock (GIL) — only one thread can execute Python code at a time.\n\nThis means:\n- Threading: useful for I/O-bound tasks (network, file, database). While one thread waits for I/O, other threads run.\n- Multiprocessing: useful for CPU-bound tasks (calculations, image processing). Each process has its own GIL so they run truly in parallel.\n\nFor modern async I/O, prefer asyncio over threading. But threading is still useful for running background tasks alongside a main program.",
                "1. threading module:\n- threading.Thread(target=func, args=(...)) creates a thread\n- thread.start() starts it\n- thread.join() waits for it to finish\n- threading.Lock() prevents race conditions\n\n2. multiprocessing module:\n- multiprocessing.Process(target=func, args=(...)) creates a process\n- Same API as threading\n- ProcessPoolExecutor for pool of workers\n\n3. concurrent.futures (modern, recommended):\n- ThreadPoolExecutor: pool of threads for I/O-bound\n- ProcessPoolExecutor: pool of processes for CPU-bound\n- executor.map(func, iterable): apply function concurrently\n- executor.submit(func, *args): submit one task\n\n4. When to use which:\n- asyncio: async I/O with await\n- threading/ThreadPoolExecutor: I/O-bound, library code that blocks\n- ProcessPoolExecutor: CPU-bound (heavy computation)\n\n5. Race condition:\n- When multiple threads modify shared data simultaneously\n- Use threading.Lock() to prevent",
                "import threading\nimport multiprocessing\nfrom concurrent.futures import ThreadPoolExecutor, ProcessPoolExecutor\nimport time\n\n# Threading — I/O-bound simulation\ndef download(url):\n    time.sleep(1)   # simulate download\n    return f'Downloaded {url}'\n\nurls = ['url1', 'url2', 'url3', 'url4']\n\n# Sequential — 4 seconds\nstart = time.time()\nresults = [download(url) for url in urls]\nprint(f'Sequential: {time.time()-start:.1f}s')\n\n# ThreadPoolExecutor — ~1 second\nstart = time.time()\nwith ThreadPoolExecutor(max_workers=4) as executor:\n    results = list(executor.map(download, urls))\nprint(f'Threaded: {time.time()-start:.1f}s')\nprint(results)\n\n# ProcessPoolExecutor — CPU-bound\ndef cpu_task(n):\n    return sum(i**2 for i in range(n))\n\nnumbers = [100000, 200000, 300000, 400000]\n\nstart = time.time()\nwith ProcessPoolExecutor() as executor:\n    results = list(executor.map(cpu_task, numbers))\nprint(f'Multiprocess: {time.time()-start:.2f}s')",
                List.of(
                    new Concept.ConceptExample("Thread-safe counter with Lock",
                        "Prevent race condition when multiple threads update shared data.",
                        "import threading\n\ncounter = 0\nlock = threading.Lock()\n\ndef increment(n):\n    global counter\n    for _ in range(n):\n        with lock:   # acquire and release automatically\n            counter += 1\n\nthreads = [threading.Thread(target=increment, args=(1000,)) for _ in range(5)]\nfor t in threads: t.start()\nfor t in threads: t.join()\n\nprint(f'Final counter: {counter}')   # should be 5000",
                        "Final counter: 5000")
                ),
                List.of(
                    "GIL: only one thread executes Python code at a time — threading helps I/O, not CPU",
                    "Use ThreadPoolExecutor for I/O-bound tasks (downloads, DB queries)",
                    "Use ProcessPoolExecutor for CPU-bound tasks (calculations, data processing)",
                    "Use Lock to protect shared data from race conditions",
                    "For modern async I/O, asyncio is usually preferred over threading"
                ),
                "Prefer concurrent.futures over manually managing threads and processes. ThreadPoolExecutor and ProcessPoolExecutor handle the complexity of creating, managing, and shutting down workers automatically.",
                List.of(
                    "Using threading for CPU-bound tasks — GIL prevents real parallelism",
                    "Not using a Lock when multiple threads modify shared variables — causes race conditions"
                ),
                20, 7, "B"),

            conceptRich(py, "Virtual Environments and pip",
                "Virtual environments isolate project dependencies so different projects can use different package versions without conflict. pip installs and manages packages.",
                "Imagine two projects:\n- Project A needs Django 3.2\n- Project B needs Django 4.2\n\nWithout virtual environments, installing Django 4.2 globally breaks Project A.\n\nA virtual environment is an isolated Python installation for one project. Each project has its own packages, versions, and even Python version.\n\npip is Python's package manager. pip install package downloads and installs packages from PyPI (Python Package Index) — the official repository of Python libraries.",
                "1. Creating a virtual environment:\n- python -m venv venv — creates a folder called venv\n- python -m venv .venv — common naming convention\n\n2. Activating:\n- Windows: venv\\Scripts\\activate\n- Mac/Linux: source venv/bin/activate\n- Prompt changes to show (venv)\n\n3. pip commands:\n- pip install package: install latest version\n- pip install package==2.0: install specific version\n- pip install package>=2.0: install 2.0 or newer\n- pip uninstall package: remove\n- pip list: show installed packages\n- pip freeze: show installed packages in requirements format\n- pip install -r requirements.txt: install from file\n\n4. requirements.txt:\n- Text file listing all project dependencies\n- Generate: pip freeze > requirements.txt\n- Install: pip install -r requirements.txt\n- Share with team so everyone has same packages\n\n5. Modern tools:\n- pipenv: manages venv and packages together\n- poetry: full project manager with dependency resolution",
                "# Terminal commands (not Python code)\n\n# Create virtual environment\n# python -m venv venv\n\n# Activate (Windows)\n# venv\\Scripts\\activate\n\n# Activate (Mac/Linux)\n# source venv/bin/activate\n\n# Install packages\n# pip install requests\n# pip install django==4.2\n# pip install pytest\n\n# See installed packages\n# pip list\n# pip show requests\n\n# Save requirements\n# pip freeze > requirements.txt\n\n# Install from requirements\n# pip install -r requirements.txt\n\n# Deactivate\n# deactivate\n\n# Python code to check package version\nimport importlib.metadata\nprint(importlib.metadata.version('pip'))",
                List.of(
                    new Concept.ConceptExample("requirements.txt workflow",
                        "Create and use requirements.txt for a project.",
                        "# requirements.txt example content:\n# requests==2.31.0\n# flask==3.0.0\n# pytest==7.4.0\n# python-dotenv==1.0.0\n\n# Reading requirements.txt in Python\nwith open('requirements.txt', 'w') as f:\n    f.write('requests==2.31.0\\n')\n    f.write('pytest==7.4.0\\n')\n\nwith open('requirements.txt', 'r') as f:\n    packages = [line.strip() for line in f if line.strip()]\n\nprint('Required packages:')\nfor pkg in packages:\n    print(f'  {pkg}')",
                        "Required packages:\n  requests==2.31.0\n  pytest==7.4.0")
                ),
                List.of(
                    "Always create a virtual environment for every project — never install packages globally",
                    "Activate the venv before installing packages — check prompt shows (venv)",
                    "pip freeze > requirements.txt saves current packages for sharing",
                    "pip install -r requirements.txt installs all project dependencies at once",
                    "Add venv/ folder to .gitignore — never commit it"
                ),
                "Create a virtual environment as the first step of every new Python project. Name it venv or .venv, activate it, then install your packages. Always commit requirements.txt so teammates can install the same dependencies.",
                List.of(
                    "Installing packages without activating venv — installs globally and pollutes system Python",
                    "Committing the venv folder — it is large and machine-specific"
                ),
                15, 8, "D"),

            conceptRich(py, "Testing with unittest",
                "unittest is Python's built-in testing framework. Tests verify your code works correctly and prevent regressions when you make changes.",
                "Testing is writing code that checks your code.\n\nWhy test?\n- When you change one function, tests tell you if you accidentally broke something else\n- Tests document expected behaviour\n- Tests give you confidence to refactor\n\nunittest is Python's built-in framework:\n- Create a class inheriting from unittest.TestCase\n- Write test methods starting with test_\n- Use self.assertEqual(), self.assertTrue() etc. to check results\n- Run: python -m unittest filename.py",
                "1. Test structure:\n- Class inherits from unittest.TestCase\n- Each test is a method starting with test_\n- setUp() runs before each test — set up shared state\n- tearDown() runs after each test — cleanup\n\n2. Assertion methods:\n- assertEqual(a, b): check a == b\n- assertNotEqual(a, b): check a != b\n- assertTrue(expr): check expr is True\n- assertFalse(expr): check expr is False\n- assertRaises(Error): check exception is raised\n- assertIn(item, container)\n- assertIsNone(x)\n\n3. Running tests:\n- python -m unittest test_file.py\n- python -m unittest discover: finds all test_*.py files\n- verbose: python -m unittest -v test_file.py\n\n4. Test naming conventions:\n- Test files: test_module.py\n- Test class: TestClassName\n- Test methods: test_what_it_does",
                "import unittest\n\n# Code to test\ndef add(a, b):\n    return a + b\n\ndef divide(a, b):\n    if b == 0:\n        raise ValueError('Cannot divide by zero')\n    return a / b\n\ndef get_grade(marks):\n    if marks >= 90: return 'A'\n    if marks >= 75: return 'B'\n    if marks >= 60: return 'C'\n    if marks >= 40: return 'D'\n    return 'F'\n\n# Tests\nclass TestMathFunctions(unittest.TestCase):\n    def test_add_positive(self):\n        self.assertEqual(add(3, 5), 8)\n\n    def test_add_negative(self):\n        self.assertEqual(add(-1, -2), -3)\n\n    def test_divide_normal(self):\n        self.assertEqual(divide(10, 2), 5.0)\n\n    def test_divide_by_zero(self):\n        with self.assertRaises(ValueError):\n            divide(10, 0)\n\nclass TestGrades(unittest.TestCase):\n    def test_grade_a(self):\n        self.assertEqual(get_grade(95), 'A')\n\n    def test_grade_f(self):\n        self.assertEqual(get_grade(30), 'F')\n\n    def test_boundary(self):\n        self.assertEqual(get_grade(90), 'A')\n        self.assertEqual(get_grade(89), 'B')\n\nif __name__ == '__main__':\n    unittest.main()",
                List.of(
                    new Concept.ConceptExample("Test a BankAccount class",
                        "Write tests for a class with setUp to initialise shared state.",
                        "import unittest\n\nclass BankAccount:\n    def __init__(self, balance=0):\n        self.balance = balance\n    def deposit(self, amount):\n        if amount <= 0: raise ValueError('Positive amount required')\n        self.balance += amount\n    def withdraw(self, amount):\n        if amount > self.balance: raise ValueError('Insufficient funds')\n        self.balance -= amount\n\nclass TestBankAccount(unittest.TestCase):\n    def setUp(self):\n        self.account = BankAccount(1000)\n\n    def test_initial_balance(self):\n        self.assertEqual(self.account.balance, 1000)\n\n    def test_deposit(self):\n        self.account.deposit(500)\n        self.assertEqual(self.account.balance, 1500)\n\n    def test_invalid_deposit(self):\n        with self.assertRaises(ValueError):\n            self.account.deposit(-100)\n\n    def test_withdraw(self):\n        self.account.withdraw(300)\n        self.assertEqual(self.account.balance, 700)\n\n    def test_overdraft(self):\n        with self.assertRaises(ValueError):\n            self.account.withdraw(2000)\n\nif __name__ == '__main__':\n    unittest.main(verbosity=2)",
                        "test_deposit ... ok\ntest_initial_balance ... ok\ntest_invalid_deposit ... ok\ntest_overdraft ... ok\ntest_withdraw ... ok\n------\nRan 5 tests in 0.001s\nOK")
                ),
                List.of(
                    "Test methods must start with test_ — unittest discovers them automatically",
                    "setUp() runs before each test — use it to create fresh objects for each test",
                    "assertRaises(Error) checks that an exception is raised",
                    "Each test should test ONE thing — small, focused tests",
                    "Run with python -m unittest -v for verbose output showing each test name"
                ),
                "Write tests as you write code — not after. Testing each function as you build it takes minutes. Debugging mysterious failures weeks later takes hours.",
                List.of(
                    "Forgetting to start test methods with test_ — they won't be discovered",
                    "Writing one huge test that checks many things — split into small focused tests"
                ),
                20, 9, "C"),

            conceptRich(py, "Testing with pytest",
                "pytest is the industry-standard Python testing framework. It is simpler than unittest, has better output, and supports powerful features like fixtures and parametrize.",
                "pytest is what most professional Python projects use for testing.\n\nCompared to unittest:\n- No class required — just functions starting with test_\n- Use plain assert statements — no self.assertEqual()\n- Better error messages when tests fail\n- Fixtures replace setUp/tearDown\n- @pytest.mark.parametrize runs one test with multiple inputs\n- Huge ecosystem of plugins\n\nInstall: pip install pytest\nRun: pytest or pytest -v for verbose",
                "1. Basic pytest tests:\n- Functions starting with test_\n- Use plain assert: assert result == expected\n- File named test_*.py or *_test.py\n\n2. Fixtures:\n- @pytest.fixture decorator\n- Function that returns shared state\n- Test function receives fixture by parameter name\n- Replaces setUp()/tearDown()\n- scope: 'function' (default), 'module', 'session'\n\n3. parametrize:\n- @pytest.mark.parametrize('input,expected', [...] )\n- Runs same test with multiple inputs\n- Reduces code duplication in tests\n\n4. Running pytest:\n- pytest: runs all tests\n- pytest -v: verbose\n- pytest test_file.py: specific file\n- pytest test_file.py::test_function: specific test\n- pytest -k 'keyword': tests matching keyword\n\n5. conftest.py:\n- Shared fixtures used across multiple test files",
                "# Install: pip install pytest\n# Run: pytest -v\n\nimport pytest\n\n# Code to test\ndef get_grade(marks):\n    if marks >= 90: return 'A'\n    if marks >= 75: return 'B'\n    if marks >= 60: return 'C'\n    if marks >= 40: return 'D'\n    return 'F'\n\ndef divide(a, b):\n    if b == 0:\n        raise ValueError('Cannot divide by zero')\n    return a / b\n\n# Simple tests\ndef test_grade_a():\n    assert get_grade(95) == 'A'\n\ndef test_grade_f():\n    assert get_grade(30) == 'F'\n\n# Parametrize — test with multiple inputs\n@pytest.mark.parametrize('marks,expected', [\n    (95, 'A'),\n    (80, 'B'),\n    (65, 'C'),\n    (45, 'D'),\n    (30, 'F'),\n    (90, 'A'),\n    (89, 'B'),\n])\ndef test_grade_boundaries(marks, expected):\n    assert get_grade(marks) == expected\n\n# Test exception\ndef test_divide_by_zero():\n    with pytest.raises(ValueError, match='Cannot divide by zero'):\n        divide(10, 0)\n\n# Fixture\n@pytest.fixture\ndef sample_marks():\n    return [85, 92, 78, 65, 45, 30]\n\ndef test_pass_count(sample_marks):\n    passed = [m for m in sample_marks if m >= 40]\n    assert len(passed) == 5",
                List.of(
                    new Concept.ConceptExample("Fixture for shared test state",
                        "Use a fixture to provide a fresh BankAccount for each test.",
                        "import pytest\n\nclass BankAccount:\n    def __init__(self, balance=0):\n        self.balance = balance\n    def deposit(self, amount):\n        if amount <= 0: raise ValueError('Positive amount required')\n        self.balance += amount\n    def withdraw(self, amount):\n        if amount > self.balance: raise ValueError('Insufficient funds')\n        self.balance -= amount\n\n@pytest.fixture\ndef account():\n    return BankAccount(1000)\n\ndef test_deposit(account):\n    account.deposit(500)\n    assert account.balance == 1500\n\ndef test_withdraw(account):\n    account.withdraw(300)\n    assert account.balance == 700\n\ndef test_overdraft(account):\n    with pytest.raises(ValueError):\n        account.withdraw(5000)\n\n@pytest.mark.parametrize('amount,expected', [\n    (100, 1100), (500, 1500), (1000, 2000)\n])\ndef test_deposit_amounts(account, amount, expected):\n    account.deposit(amount)\n    assert account.balance == expected",
                        "test_deposit PASSED\ntest_withdraw PASSED\ntest_overdraft PASSED\ntest_deposit_amounts[100-1100] PASSED\ntest_deposit_amounts[500-1500] PASSED\ntest_deposit_amounts[1000-2000] PASSED")
                ),
                List.of(
                    "pytest requires no class — just functions starting with test_",
                    "Use plain assert instead of assertEqual — pytest shows clear diffs on failure",
                    "@pytest.fixture provides shared setup, injected by parameter name",
                    "@pytest.mark.parametrize runs one test with many input combinations",
                    "Run pytest -v for verbose output showing each test name and result"
                ),
                "Use @pytest.mark.parametrize to test boundary conditions and edge cases without writing separate test functions for each. Testing get_grade() with 7 boundary cases takes 2 lines instead of 7 functions.",
                List.of(
                    "Forgetting to prefix test functions with test_ — pytest will not discover them",
                    "Using self.assertEqual in pytest — just use plain assert"
                ),
                20, 10, "C"),

            conceptRich(py, "Logging module",
                "The logging module provides a proper logging system for Python applications. Use it instead of print() for production code.",
                "print() is fine for learning, but production code should use logging.\n\nWhy?\n- You can turn off debug messages in production without changing code\n- Logs include timestamps, file names, and line numbers automatically\n- You can write logs to files, send to remote servers, or format as JSON\n- Different severity levels: DEBUG, INFO, WARNING, ERROR, CRITICAL\n\nThe logging hierarchy:\nDEBUG < INFO < WARNING < ERROR < CRITICAL\n\nSet the level to WARNING in production — only WARNING and above are shown. Set to DEBUG in development — everything is shown.",
                "1. Basic logging:\n- import logging\n- logging.basicConfig(level=logging.DEBUG)\n- logging.debug(), info(), warning(), error(), critical()\n\n2. Log levels:\n- DEBUG (10): detailed diagnostic information\n- INFO (20): confirmation that things work as expected\n- WARNING (30): something unexpected but not an error\n- ERROR (40): serious problem, function could not perform\n- CRITICAL (50): very serious error, program may crash\n\n3. Logger objects (recommended):\n- logger = logging.getLogger(__name__)\n- Each module gets its own logger\n- Parent-child hierarchy: 'myapp', 'myapp.db', 'myapp.api'\n\n4. Handlers:\n- StreamHandler: output to console\n- FileHandler: output to file\n- RotatingFileHandler: file with size limit\n\n5. Formatters:\n- Control log message format\n- Include: time, level, module, line number, message",
                "import logging\n\n# Basic config\nlogging.basicConfig(\n    level=logging.DEBUG,\n    format='%(asctime)s - %(levelname)s - %(message)s',\n    datefmt='%Y-%m-%d %H:%M:%S'\n)\n\nlogging.debug('Debug message — detailed info')\nlogging.info('Info message — normal operation')\nlogging.warning('Warning — something to watch')\nlogging.error('Error — something failed')\nlogging.critical('Critical — system failure')\n\n# Logger per module (recommended)\nlogger = logging.getLogger(__name__)\n\n# Log to file AND console\nfile_handler = logging.FileHandler('app.log')\nconsole_handler = logging.StreamHandler()\n\nformatter = logging.Formatter('%(asctime)s [%(name)s] %(levelname)s: %(message)s')\nfile_handler.setFormatter(formatter)\nconsole_handler.setFormatter(formatter)\n\nlogger.addHandler(file_handler)\nlogger.addHandler(console_handler)\nlogger.setLevel(logging.INFO)\n\nlogger.info('Application started')\nlogger.warning('High memory usage: 85%')\n\ntry:\n    result = 10 / 0\nexcept ZeroDivisionError:\n    logger.error('Division by zero', exc_info=True)",
                List.of(
                    new Concept.ConceptExample("Logging in a real function",
                        "Replace print statements with proper logging in a data processing function.",
                        "import logging\n\nlogging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')\nlogger = logging.getLogger(__name__)\n\ndef process_marks(students: list) -> dict:\n    logger.info(f'Processing {len(students)} students')\n\n    results = {}\n    for name, marks in students:\n        if not 0 <= marks <= 100:\n            logger.warning(f'Invalid marks for {name}: {marks}')\n            continue\n        grade = 'Pass' if marks >= 40 else 'Fail'\n        results[name] = grade\n        logger.debug(f'{name}: {marks} -> {grade}')\n\n    logger.info(f'Processed {len(results)} valid students')\n    return results\n\ndata = [('Ravi', 85), ('Priya', 150), ('Arjun', 72), ('Sneha', 35)]\nresults = process_marks(data)\nprint(results)",
                        "INFO: Processing 4 students\nWARNING: Invalid marks for Priya: 150\nINFO: Processed 3 valid students\n{'Ravi': 'Pass', 'Arjun': 'Pass', 'Sneha': 'Fail'}")
                ),
                List.of(
                    "Five log levels: DEBUG < INFO < WARNING < ERROR < CRITICAL",
                    "Set level to WARNING in production, DEBUG in development",
                    "Use getLogger(__name__) in each module for a named logger",
                    "exc_info=True in logger.error() includes the full traceback",
                    "Use logging instead of print() in any code that runs in production"
                ),
                "Replace all print() statements with logger.debug() or logger.info() as you finish developing a feature. This lets you keep the useful messages and just change the log level to silence them in production without deleting your debugging output.",
                List.of(
                    "Using print() for error messages in production — use logger.error() instead",
                    "Setting level too low in production — WARNING or ERROR is appropriate, not DEBUG"
                ),
                15, 11, "C"),

            conceptRich(py, "Python Packaging and Project Structure",
                "A well-structured Python project is easy to navigate, test, and package. Good structure separates concerns, makes imports clean, and prepares code for distribution.",
                "As projects grow beyond a single file, structure matters.\n\nA professional Python project has:\n- Source code in a package directory\n- Tests in a separate tests/ directory\n- Configuration in pyproject.toml or setup.py\n- Dependencies in requirements.txt\n- README and .gitignore\n\nA package is a directory with an __init__.py file. Python can import from it like any module.\n\nPyPI (Python Package Index) is where packages like requests, numpy, and django live. You can publish your own package there so others can pip install it.",
                "1. Standard project structure:\n```\nmy_project/\n    src/\n        my_package/\n            __init__.py\n            core.py\n            utils.py\n    tests/\n        test_core.py\n        test_utils.py\n    requirements.txt\n    pyproject.toml\n    README.md\n    .gitignore\n```\n\n2. __init__.py:\n- Makes a directory a package\n- Can be empty or import from submodules\n- Controls what from package import * exports\n\n3. Relative imports:\n- from . import module (same package)\n- from .. import module (parent package)\n- Prefer absolute imports when possible\n\n4. pyproject.toml (modern standard):\n- Project name, version, dependencies\n- Build system configuration\n- Tool configuration (pytest, black, mypy)\n\n5. Publishing to PyPI:\n- pip install build twine\n- python -m build (creates dist/ folder)\n- twine upload dist/*",
                "# Example package structure\n# my_project/\n#     src/\n#         calculator/\n#             __init__.py\n#             operations.py\n#             validators.py\n#     tests/\n#         test_operations.py\n#     requirements.txt\n#     pyproject.toml\n\n# operations.py\ndef add(a: float, b: float) -> float:\n    return a + b\n\ndef divide(a: float, b: float) -> float:\n    if b == 0:\n        raise ValueError('Division by zero')\n    return a / b\n\n# __init__.py\n# from .operations import add, divide\n# from .validators import validate_number\n\n# pyproject.toml content example:\nconfig = '''\n[build-system]\nrequires = ['setuptools']\nbuild-backend = 'setuptools.backends.legacy:build'\n\n[project]\nname = 'my-calculator'\nversion = '1.0.0'\ndescription = 'A simple calculator'\nrequires-python = '>=3.9'\n\n[tool.pytest.ini_options]\ntestpaths = ['tests']\n\n[tool.mypy]\npython_version = '3.11'\nstrict = true\n'''\nprint('pyproject.toml example shown above')",
                List.of(
                    new Concept.ConceptExample("Creating and importing a package",
                        "Create a simple package with multiple modules.",
                        "import os\n\n# Simulate package creation\nos.makedirs('myutils', exist_ok=True)\n\nwith open('myutils/__init__.py', 'w') as f:\n    f.write('from .strings import clean\\nfrom .numbers import clamp\\n')\n\nwith open('myutils/strings.py', 'w') as f:\n    f.write('def clean(s: str) -> str:\\n    return s.strip().lower()\\n')\n\nwith open('myutils/numbers.py', 'w') as f:\n    f.write('def clamp(n, lo, hi):\\n    return max(lo, min(hi, n))\\n')\n\n# Now import from the package\nimport importlib\nspec = importlib.util.spec_from_file_location('myutils', 'myutils/__init__.py')\nmod = importlib.util.module_from_spec(spec)\nspec.loader.exec_module(mod)\n\nprint(mod.clean('  HELLO WORLD  '))\nprint(mod.clamp(150, 0, 100))",
                        "hello world\n100")
                ),
                List.of(
                    "__init__.py makes a directory a Python package",
                    "Separate source code (src/), tests (tests/), and config files at root level",
                    "Use absolute imports (from mypackage.module import func) over relative when possible",
                    "requirements.txt lists dependencies for recreating the environment",
                    "pyproject.toml is the modern standard for project configuration"
                ),
                "Create a consistent project structure from day one, even for small projects. Moving files and fixing imports later is painful. The src/ layout and separate tests/ directory is the standard professional structure.",
                List.of(
                    "Forgetting __init__.py — the directory will not be recognised as a package",
                    "Putting all code in one file as the project grows — split into modules early"
                ),
                15, 12, "C")
        );

        conceptRepository.saveAll(concepts);
        py.setTotalConcepts(concepts.size());
        subjectRepository.save(py);
        System.out.println("✅ Python Professional seeded — " + concepts.size() + " concepts");
    }

    // ─── PYTHON BASICS QUESTIONS ─────────────────────────────────────────────
    private void seedPythonBasicsQuestions() {
        final String subjectId = "6a256d6c91a13f5f2780109d";
        List<Question> all = new java.util.ArrayList<>();

        java.util.function.Function<String[], Question> make = arr -> {
            Question q = new Question();
            q.setConceptId(arr[0]); q.setSubjectId(subjectId);
            q.setText(arr[1]);
            q.setOptions(List.of(arr[2], arr[3], arr[4], arr[5]));
            q.setCorrectIndex(Integer.parseInt(arr[6]));
            q.setExplanation(arr[7]);
            q.setDifficulty(arr[8]);
            return q;
        };

        // ── Concept 1: Operators ───────────────────────────────────────────────
        String c1 = "6a256d6c91a13f5f2780109e";
        all.add(make.apply(new String[]{c1,
            "What does the ** operator do in Python?",
            "Integer division","Modulo (remainder)","Exponentiation (power)","Bitwise XOR",
            "2","** is the exponentiation operator. 2**8 gives 256. It raises the left operand to the power of the right operand.",
            "EASY"}));
        all.add(make.apply(new String[]{c1,
            "What is the result of 10 % 3?",
            "3","3.333...","0","1",
            "3","% is the modulo operator — it returns the remainder after division. 10 divided by 3 is 3 remainder 1, so 10 % 3 == 1.",
            "EASY"}));
        all.add(make.apply(new String[]{c1,
            "Which operator should you use to check if score is between 40 and 100 (inclusive)?",
            "score > 40 or score < 100","score >= 40 and score <= 100","score = 40 and score = 100","score between 40 and 100",
            "1","Use and to combine two comparison conditions. score >= 40 and score <= 100 is True only when both conditions are True simultaneously.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c1,
            "What is the difference between / and // in Python?",
            "/ and // are identical","/ always returns float; // returns integer (floor division)","// is for comments","/ is for strings; // is for numbers",
            "1","/ performs true division and always returns a float: 10/2 gives 5.0. // performs floor division and returns an integer (or float if either operand is float): 10//3 gives 3.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c1,
            "What does x += 5 mean?",
            "x equals 5","Assign 5 to a new variable","x is assigned x + 5 (add 5 to current value)","Compare x with 5",
            "2","x += 5 is shorthand for x = x + 5. It adds 5 to the current value of x and stores the result back in x. This is an augmented assignment operator.",
            "EASY"}));
        all.add(make.apply(new String[]{c1,
            "What does the not operator do?",
            "Inverts a boolean value: not True gives False","Multiplies by -1","Checks if two values are not equal","Subtracts 1",
            "0","not is the logical NOT operator. It inverts the boolean value: not True is False, not False is True. It has lower precedence than comparison operators.",
            "EASY"}));
        all.add(make.apply(new String[]{c1,
            "What is the result of '5' + 3 in Python?",
            "8","'53'","53","TypeError",
            "3","'5' + 3 raises a TypeError because you cannot concatenate a string and an integer with +. Python does not implicitly convert types for + unlike JavaScript. Use int('5') + 3 or f'{5}' + '3'.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c1,
            "score = 0. What does score ?? 'No score' evaluate to in Python?",
            "Python does not have a ?? operator — use score if score is not None else 'No score'","'No score'","0","None",
            "0","Python does not have the ?? (nullish coalescing) operator — that is JavaScript. In Python use: score if score is not None else 'No score'. The or operator (score or 'No score') would treat 0 as falsy.",
            "HARD"}));
        all.add(make.apply(new String[]{c1,
            "Which comparison operator checks value AND type equality?",
            "=","==","!=","is",
            "1","== checks value equality with possible type coercion in Python (though Python's == is stricter than JavaScript's ==). is checks object identity. For value comparison use ==; for identity (same object) use is.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c1,
            "What is the result of 17 % 2?",
            "8","8.5","0","1",
            "3","17 % 2 gives 1 because 17 divided by 2 is 8 remainder 1. The modulo operator is commonly used to check if a number is even (n % 2 == 0) or odd (n % 2 == 1).",
            "EASY"}));

        // ── Concept 2: Lists and Tuples ────────────────────────────────────────
        String c2 = "6a256d6c91a13f5f2780109f";
        all.add(make.apply(new String[]{c2,
            "What is the key difference between a list and a tuple in Python?",
            "Lists hold numbers; tuples hold strings","Lists are unordered; tuples are ordered","Lists are mutable (can be changed); tuples are immutable (cannot be changed)","There is no difference",
            "2","Lists are mutable — you can add, remove and change elements after creation. Tuples are immutable — once created they cannot be modified. This makes tuples suitable for fixed data like coordinates.",
            "EASY"}));
        all.add(make.apply(new String[]{c2,
            "What does list[-1] return?",
            "An error","The first element","The last element","None",
            "2","Negative indexing counts from the end. list[-1] is the last element, list[-2] is the second to last, and so on. This is a clean way to access elements from the end without knowing the length.",
            "EASY"}));
        all.add(make.apply(new String[]{c2,
            "Which method adds an element to the END of a list?",
            "list.add()","list.push()","list.append()","list.insert()",
            "2","list.append(item) adds the item to the end of the list. list.insert(index, item) adds at a specific position. Python lists have no add() or push() methods.",
            "EASY"}));
        all.add(make.apply(new String[]{c2,
            "What does list.pop() do by default?",
            "Removes the first element","Removes and returns the last element","Removes all elements","Returns the last element without removing it",
            "1","list.pop() with no arguments removes and returns the last element. list.pop(0) removes the first. list.pop(i) removes the element at index i.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c2,
            "nums = [3,1,4,1,5]. What does sorted(nums) return?",
            "Sorts nums in place and returns None","Returns [1,1,3,4,5] and leaves nums unchanged","Raises an error","Returns nums reversed",
            "1","sorted(iterable) returns a NEW sorted list without modifying the original. list.sort() sorts in place and returns None. Always use sorted() when you need to keep the original.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c2,
            "How do you create a tuple with one element?",
            "(42)","[42]","(42,)","tuple{42}",
            "2","(42) is just parentheses around 42 — it is an integer, not a tuple. A single-element tuple requires a trailing comma: (42,). Without the comma Python sees it as a parenthesised expression.",
            "HARD"}));
        all.add(make.apply(new String[]{c2,
            "What is tuple unpacking?",
            "Removing items from a tuple","Converting a tuple to a list","Assigning tuple values to multiple variables: a, b, c = (1, 2, 3)","Sorting a tuple",
            "2","Tuple unpacking assigns each element to a variable: a, b, c = (1, 2, 3) gives a=1, b=2, c=3. This is also used for swapping variables: a, b = b, a.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c2,
            "What does len([1, 2, 3, 4]) return?",
            "3","4","5","[1,2,3,4]",
            "1","len() returns the number of elements in a sequence. len([1,2,3,4]) is 4. It works on lists, tuples, strings, dictionaries and sets.",
            "EASY"}));
        all.add(make.apply(new String[]{c2,
            "Why would you use a tuple instead of a list?",
            "Tuples are always faster for all operations","Tuples use less memory and signal that data should not be modified — e.g. coordinates, RGB colours","Tuples support more methods than lists","Tuples can hold more elements than lists",
            "1","Tuples are slightly faster and use less memory than lists. More importantly, using a tuple communicates intent — this data is fixed and should not change. Tuples can also be used as dictionary keys; lists cannot.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c2,
            "What does list.remove('apple') do?",
            "Removes the element at the index named 'apple'","Removes the first occurrence of 'apple' from the list","Removes all occurrences of 'apple'","Returns the index of 'apple'",
            "1","list.remove(value) removes the FIRST occurrence of the value. If the value is not found it raises a ValueError. To remove all occurrences you need a loop or list comprehension.",
            "MEDIUM"}));

        // ── Concept 3: Dictionaries ────────────────────────────────────────────
        String c3 = "6a256d6c91a13f5f278010a0";
        all.add(make.apply(new String[]{c3,
            "How do you access the value for key 'name' in a dictionary d?",
            "d.name","d(name)","d['name'] or d.get('name')","d{name}",
            "2","You access dictionary values with d['name'] (raises KeyError if missing) or d.get('name') (returns None if missing). d.name is not valid for regular dicts.",
            "EASY"}));
        all.add(make.apply(new String[]{c3,
            "What is the difference between d['key'] and d.get('key')?",
            "They are identical","d['key'] raises KeyError if key is missing; d.get('key') returns None (or a default) if missing","d.get() is faster","d['key'] only works for strings",
            "1","d['key'] raises KeyError if the key does not exist. d.get('key') returns None by default, or a specified default: d.get('key', 'default'). Use get() when you are not sure the key exists.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c3,
            "What do dict.keys(), dict.values() and dict.items() return?",
            "Lists of keys, values and (key,value) tuples respectively","Strings","Booleans","None",
            "0","dict.keys() returns a view of all keys. dict.values() returns a view of all values. dict.items() returns a view of all (key, value) pairs. Wrap with list() to get a regular list.",
            "EASY"}));
        all.add(make.apply(new String[]{c3,
            "Are Python dictionaries ordered?",
            "No — dictionaries have no defined order","Yes — from Python 3.7+ dictionaries maintain insertion order","Only if you use OrderedDict","Order depends on the key types",
            "1","From Python 3.7+, regular dictionaries maintain insertion order as a language guarantee. The order in which you add key-value pairs is preserved when iterating.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c3,
            "How do you add or update a key-value pair in a dictionary?",
            "d.add('key', value)","d.append('key', value)","d['key'] = value","d.set('key', value)",
            "2","d['key'] = value adds the key if it does not exist, or updates its value if it does. There is no separate add() or set() method for dictionaries.",
            "EASY"}));
        all.add(make.apply(new String[]{c3,
            "What type must dictionary keys be?",
            "Strings only","Numbers only","Any immutable type: string, number, or tuple","Any type including lists",
            "2","Dictionary keys must be hashable (immutable): strings, integers, floats, tuples. Lists and other mutable objects cannot be keys because they can change, which would break the dictionary's hash table.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c3,
            "How do you check if a key exists in a dictionary?",
            "d.hasKey('key')","'key' in d","d.contains('key')","d.exists('key')",
            "1","Use the in operator: 'key' in d returns True if the key exists. This is more efficient than d.get('key') is not None and cleaner than using try/except.",
            "EASY"}));
        all.add(make.apply(new String[]{c3,
            "What does dict.pop('key') do?",
            "Returns the value without removing it","Removes and returns the value for 'key'; raises KeyError if missing","Removes all items","Adds a new key",
            "1","dict.pop('key') removes the key and returns its value. If the key does not exist it raises KeyError. Use dict.pop('key', default) to provide a fallback instead of raising.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c3,
            "How do you merge two dictionaries d1 and d2 in Python 3.9+?",
            "d1.merge(d2)","d1 + d2","d1 | d2 or {**d1, **d2} in earlier versions","d1.update(d2) returns new dict",
            "2","Python 3.9+ supports the | operator: d1 | d2 creates a new merged dict. For earlier versions use {**d1, **d2}. Note: d1.update(d2) modifies d1 in place and returns None.",
            "HARD"}));
        all.add(make.apply(new String[]{c3,
            "You loop with: for key, value in d.items(). What does this give you?",
            "Only keys","Only values","Both key and value on each iteration","The index and the key",
            "2","d.items() returns (key, value) pairs. The for key, value in d.items() pattern unpacks each pair — you get both key and value in each iteration. This is the standard way to iterate over a dictionary.",
            "EASY"}));

        // ── Concept 4: Sets ────────────────────────────────────────────────────
        String c4 = "6a256d6c91a13f5f278010a1";
        all.add(make.apply(new String[]{c4,
            "What makes sets different from lists?",
            "Sets hold only numbers","Sets are ordered and allow duplicates","Sets are unordered and automatically remove duplicates","Sets are immutable",
            "2","Sets are unordered (no guaranteed element order) and only store unique values — duplicates are silently removed. set([1,2,2,3]) gives {1,2,3}.",
            "EASY"}));
        all.add(make.apply(new String[]{c4,
            "How do you create an EMPTY set in Python?",
            "{}","set()","[]","empty_set()",
            "1","set() creates an empty set. {} creates an empty DICTIONARY, not a set. This is a very common mistake.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c4,
            "What is the fastest way to remove duplicates from a list?",
            "Use a loop with if item not in result","list(set(original_list))","list.unique()","sorted(list)",
            "1","Converting to a set removes duplicates (sets cannot have duplicates), then converting back to a list gives a deduplicated list. list(set(my_list)) is the standard one-liner.",
            "EASY"}));
        all.add(make.apply(new String[]{c4,
            "What does the & operator do with two sets?",
            "Union — all elements from both","Intersection — elements present in BOTH sets","Difference — elements in first but not second","Symmetric difference",
            "1","A & B (or A.intersection(B)) returns elements present in both A and B. A | B is union, A - B is difference, A ^ B is symmetric difference.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c4,
            "Can you access set elements by index like set[0]?",
            "Yes — sets support indexing like lists","No — sets are unordered and do not support indexing","Only for numeric elements","Yes using set.get(0)",
            "1","Sets are unordered — elements have no position. You cannot access elements by index. To check membership use the in operator: 'apple' in my_set.",
            "EASY"}));
        all.add(make.apply(new String[]{c4,
            "What is the difference between set.remove() and set.discard()?",
            "They are identical","remove() raises KeyError if element missing; discard() does nothing if element missing","discard() removes all occurrences; remove() removes only one","remove() returns the element; discard() does not",
            "1","set.remove(x) raises KeyError if x is not in the set. set.discard(x) silently does nothing if x is not present. Use discard() when you are not sure the element exists.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c4,
            "What does A | B produce when A and B are sets?",
            "Elements in A but not B","Elements in B but not A","All unique elements from both A and B (union)","Elements in both A and B",
            "2","A | B is the union operator — it returns a new set containing all unique elements from both sets combined. A & B is intersection, A - B is difference.",
            "EASY"}));
        all.add(make.apply(new String[]{c4,
            "set1 = {1,2,3}; set2 = {3,4,5}. What is set1 - set2?",
            "{1,2,3,4,5}","{3}","{1,2}","{1,2,4,5}",
            "2","A - B is the set difference — elements in A that are NOT in B. {1,2,3} - {3,4,5} gives {1,2} because 3 is removed (it is in B). Use ^ for symmetric difference (elements in either but not both).",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c4,
            "Which set method checks if one set is a subset of another?",
            "set.contains()","set.issubset()","set.subset()","set.within()",
            "1","A.issubset(B) returns True if every element in A is also in B. You can also use A <= B. A.issuperset(B) (or A >= B) checks the reverse.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c4,
            "Why can sets only contain immutable (hashable) elements?",
            "It is an arbitrary Python limitation","Sets use a hash table internally — mutable objects have no stable hash","Sets are slower with mutable elements","It is just a convention",
            "1","Sets are implemented as hash tables. Every element must have a stable hash value. Mutable objects (lists, dicts) can change, so their hash would change, breaking the set. Strings, numbers, tuples are hashable and can be set elements.",
            "HARD"}));

        // ── Concept 5: String Methods and Formatting ───────────────────────────
        String c5 = "6a256d6c91a13f5f278010a2";
        all.add(make.apply(new String[]{c5,
            "What does 'Hello World'.lower() return?",
            "'HELLO WORLD'","'hello world'","'Hello World'","'hello World'",
            "1","str.lower() returns a new string with all characters converted to lowercase. Strings are immutable — lower() does not modify the original string.",
            "EASY"}));
        all.add(make.apply(new String[]{c5,
            "What does '  hello  '.strip() return?",
            "'  hello  '","'hello'","'hello  '","'  hello'",
            "1","strip() removes leading and trailing whitespace (spaces, tabs, newlines). lstrip() removes from the left only; rstrip() from the right only.",
            "EASY"}));
        all.add(make.apply(new String[]{c5,
            "What does 'a,b,c'.split(',') return?",
            "'abc'","['a', 'b', 'c']","('a', 'b', 'c')","{'a', 'b', 'c'}",
            "1","split(separator) splits a string into a list of substrings divided by the separator. 'a,b,c'.split(',') returns ['a', 'b', 'c']. split() with no argument splits on any whitespace.",
            "EASY"}));
        all.add(make.apply(new String[]{c5,
            "What is the modern way to embed a variable in a string in Python 3?",
            "'Hello ' + name + '!'","'Hello %s!' % name","f'Hello {name}!'","'Hello {}'.format(name)",
            "2","f-strings (f'Hello {name}!') are the modern, preferred way. They are cleaner, faster and support expressions directly inside {}. Available from Python 3.6+.",
            "EASY"}));
        all.add(make.apply(new String[]{c5,
            "What does '-'.join(['a', 'b', 'c']) return?",
            "['a-b-c']","'a-b-c'","'abc'","'a','b','c'",
            "1","join(iterable) concatenates an iterable of strings using the string as separator. '-'.join(['a','b','c']) gives 'a-b-c'. It is the reverse of split().",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c5,
            "What does 'python'.find('thon') return?",
            "True","2","'thon'","-1",
            "1","find(substring) returns the index of the first occurrence. 'python'.find('thon') returns 2 (position where 'thon' starts). Returns -1 if not found (unlike index() which raises ValueError).",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c5,
            "What does f'{3.14159:.2f}' produce?",
            "'3.14159'","'3.14'","3.14","'3'",
            "1",":.2f in an f-string format specifier means: format as float with 2 decimal places. f'{3.14159:.2f}' gives '3.14'. The result is always a string.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c5,
            "Are Python strings mutable?",
            "Yes — you can change individual characters","No — strings are immutable; all methods return new strings","Yes but only inside functions","Only ASCII strings are immutable",
            "1","Strings are immutable in Python. You cannot do text[0] = 'H'. Every string method (upper(), replace(), strip()) returns a NEW string. The original is unchanged.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c5,
            "What does 'Hello World'.replace('World', 'Python') return?",
            "'Hello World'","'Hello Python'","Modifies the original string","None",
            "1","replace(old, new) returns a new string with all occurrences of old replaced by new. It does not modify the original string.",
            "EASY"}));
        all.add(make.apply(new String[]{c5,
            "What does 'python'.startswith('py') return?",
            "2","'py'","True","False",
            "2","startswith(prefix) returns True if the string starts with the given prefix. 'python'.startswith('py') is True. endswith(suffix) checks the end.",
            "EASY"}));

        // ── Concept 6: Mutable vs Immutable ───────────────────────────────────
        String c6 = "6a256d6c91a13f5f278010a3";
        all.add(make.apply(new String[]{c6,
            "Which of these is a MUTABLE data type in Python?",
            "int","str","tuple","list",
            "3","Mutable types can be changed after creation: list, dict, set. Immutable types cannot: int, float, str, tuple, bool, frozenset.",
            "EASY"}));
        all.add(make.apply(new String[]{c6,
            "a = [1,2,3]; b = a; b.append(4). What is the value of a?",
            "[1,2,3]","[1,2,3,4]","[1,2,3,[4]]","Error",
            "1","b = a does NOT copy the list — both b and a point to the same list object. Modifying b also modifies a. Use b = a.copy() to create an independent copy.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c6,
            "Which of these creates a SHALLOW COPY of a list?",
            "b = a","b = a.copy() or b = a[:]","b = copy.deepcopy(a)","b = list.clone(a)",
            "1","a.copy() and a[:] both create a shallow copy — a new list with the same elements. For a list of simple values this is effectively independent. For nested lists, use copy.deepcopy() for a fully independent copy.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c6,
            "Why can't you use a list as a dictionary key?",
            "Lists are too slow","Lists are mutable so their hash can change — dictionary keys must be hashable (immutable)","Lists don't support the == operator","Lists can only be values, not keys — this is arbitrary",
            "1","Dictionary keys must be hashable. Mutable objects like lists have no stable hash because their contents can change. Use a tuple instead of a list as a dictionary key.",
            "HARD"}));
        all.add(make.apply(new String[]{c6,
            "x = 5; y = x; x = 10. What is y?",
            "10","5","None","Error",
            "1","Integers are immutable. y = x makes y point to the same int object (5). x = 10 makes x point to a NEW int object (10). y still points to 5 — it was NOT affected. This is different from mutable objects.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c6,
            "Which Python types are IMMUTABLE?",
            "list, dict, set","int, str, tuple","list, str, tuple","dict, set, int",
            "1","Immutable types: int, float, str, tuple, bool, frozenset, bytes. Mutable types: list, dict, set, bytearray. Immutable types cannot be changed after creation.",
            "EASY"}));
        all.add(make.apply(new String[]{c6,
            "What is the difference between a shallow copy and a deep copy?",
            "They are identical","Shallow copy copies outer container only; deep copy copies all nested objects recursively","Deep copy is always faster","Shallow copy works for tuples; deep copy for lists",
            "1","A shallow copy (list.copy()) creates a new list but nested objects are shared. A deep copy (copy.deepcopy()) creates fully independent copies of all nested objects too.",
            "HARD"}));
        all.add(make.apply(new String[]{c6,
            "If you pass a list to a function and modify it inside, does the original list change?",
            "No — Python always passes by value","Yes — lists are mutable and passed by reference (object reference)","Only if you use the global keyword","Depends on the Python version",
            "1","Python passes object references. For mutable objects like lists, the function receives a reference to the same object. Modifications inside the function affect the original. Pass a copy (list.copy()) to avoid this.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c6,
            "text = 'hello'; text[0] = 'H'. What happens?",
            "text becomes 'Hello'","TypeError: strings are immutable and do not support item assignment","The assignment is silently ignored","text becomes 'H'",
            "1","Strings are immutable. You cannot modify individual characters. text[0] = 'H' raises TypeError: 'str' object does not support item assignment. Create a new string: text = 'H' + text[1:].",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c6,
            "What does 'immutable' mean for Python's tuple type?",
            "Tuples cannot be printed","Once a tuple is created, its elements cannot be added, removed or changed","Tuples are slower than lists","Tuples can only contain numbers",
            "1","Immutable means the object cannot be modified after creation. For tuples: no append, no remove, no item assignment. t[0] = 5 on a tuple raises TypeError.",
            "EASY"}));

        // ── Concept 7: Slicing ────────────────────────────────────────────────
        String c7 = "6a256d6c91a13f5f278010a4";
        all.add(make.apply(new String[]{c7,
            "What does s[2:5] return for s = 'Python'?",
            "'Pyt'","'tho'","'thon'","'ytho'",
            "1","s[2:5] extracts characters at indices 2, 3, 4 (stop is exclusive). 'Python'[2] = 't', [3] = 'h', [4] = 'o'. So s[2:5] = 'tho'.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c7,
            "What does s[::-1] do?",
            "Returns the first character","Returns an empty string","Reverses the sequence","Sorts the sequence",
            "2","s[::-1] uses a step of -1 to iterate backwards through the entire sequence. It is the standard Python idiom for reversing a string, list or tuple.",
            "EASY"}));
        all.add(make.apply(new String[]{c7,
            "For list = [0,1,2,3,4,5], what does list[-3:] return?",
            "[0,1,2]","[3,4,5]","[2,3,4]","[4,5]",
            "1","Negative start index counts from the end. list[-3:] starts at the 3rd from last (index 3) and goes to the end. [0,1,2,3,4,5][-3:] = [3,4,5].",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c7,
            "What does the step parameter in slicing do?",
            "Sets the maximum number of elements to return","Controls how many positions to skip between each selected element","Sets the starting index","Sets the ending index",
            "1","In s[start:stop:step], step controls the increment between selected indices. s[::2] takes every second element. s[::-1] steps backwards by 1 (reversing). Default step is 1.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c7,
            "Does slicing ever raise an IndexError?",
            "Yes — if start or stop is out of range","No — slicing handles out-of-range indices gracefully by clamping to valid range","Yes — if step is 0","Only for negative indices",
            "1","Slicing never raises IndexError even if indices are out of range. s[0:100] on a 5-element list simply returns all 5 elements. This is different from direct indexing s[100] which would raise IndexError.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c7,
            "nums = [0,1,2,3,4]. What does nums[1:4] return?",
            "[0,1,2,3]","[1,2,3]","[1,2,3,4]","[2,3,4]",
            "1","nums[1:4] returns elements at indices 1, 2, 3 (stop index 4 is EXCLUDED). nums[1]=1, nums[2]=2, nums[3]=3. Result: [1,2,3].",
            "EASY"}));
        all.add(make.apply(new String[]{c7,
            "Does slicing a list modify the original?",
            "Yes — slicing removes the extracted elements from the original","No — slicing returns a new list; the original is unchanged","Only if you use a step","Only with negative indices",
            "1","Slicing always creates a new object. The original list, string or tuple is unchanged. This is why my_list[:] is used to make a shallow copy of a list.",
            "EASY"}));
        all.add(make.apply(new String[]{c7,
            "'racecar' == 'racecar'[::-1]. What does this evaluate to?",
            "False","'racecar'","True","Error",
            "2","'racecar'[::-1] reverses the string giving 'racecar'. 'racecar' == 'racecar' is True. This is how you check for palindromes in Python.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c7,
            "What does items[::2] do for items = [10,20,30,40,50]?",
            "Returns every second element starting from index 0: [10,30,50]","Returns [20,40]","Returns the first 2 elements","Returns the last 2 elements",
            "0","items[::2] uses step=2, starting from index 0: picks indices 0,2,4 giving [10,30,50]. items[1::2] would give [20,40].",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c7,
            "How do you use slicing to make a copy of a list?",
            "list.slice()","list[:]","list[0:-1]","list.copy() only — slicing does not work",
            "1","list[:] creates a shallow copy by slicing the entire list. It is equivalent to list.copy(). Both return a new list with the same elements.",
            "MEDIUM"}));

        // ── Concept 8: if, elif, else ──────────────────────────────────────────
        String c8 = "6a256d6c91a13f5f278010a5";
        all.add(make.apply(new String[]{c8,
            "In an if-elif-else chain, how many blocks execute?",
            "All blocks that have True conditions","Every block always executes","Only the first block whose condition is True","The last block always executes",
            "2","Python evaluates conditions top to bottom and executes ONLY the first block whose condition is True. Once a match is found, the rest of the chain is skipped.",
            "EASY"}));
        all.add(make.apply(new String[]{c8,
            "What is a falsy value in Python?",
            "Only the boolean False","False, 0, '', None, [], {} and other empty/zero values","Only None and False","Only 0 and False",
            "1","Falsy values are: False, 0, 0.0, '' (empty string), None, [] (empty list), {} (empty dict/set), set(). All other values are truthy.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c8,
            "What is the ternary expression syntax in Python?",
            "condition ? true_value : false_value","if condition then true_value else false_value","true_value if condition else false_value","(condition, true_value, false_value)",
            "2","Python's ternary (conditional) expression is: value_if_true if condition else value_if_false. Example: status = 'Pass' if score >= 40 else 'Fail'.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c8,
            "if [] — does the if block execute?",
            "Yes — empty list is truthy","No — empty list is falsy","Error — you must use len([]) == 0","Depends on what is inside the list",
            "1","Empty collections ([], {}, set(), '') are falsy in Python. if [] evaluates to False so the block does NOT execute. Check non-empty with if my_list: which is cleaner than if len(my_list) > 0:.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c8,
            "Which keyword introduces an additional condition in an if-else chain?",
            "else if","elseif","elif","or if",
            "2","Python uses elif (short for 'else if'). It checks another condition if the previous conditions were all False. You can have as many elif blocks as needed between if and else.",
            "EASY"}));
        all.add(make.apply(new String[]{c8,
            "Does the else block require a condition?",
            "Yes — else requires a boolean condition","No — else has no condition; it runs when all previous conditions are False","Yes — else requires not condition","Depends on what comes before it",
            "1","else has no condition. It is a catch-all that runs when none of the preceding if or elif conditions were True. Putting a condition on else is a SyntaxError.",
            "EASY"}));
        all.add(make.apply(new String[]{c8,
            "What does 'if x:' check when x is a string?",
            "If x is the string 'True'","If x is non-empty (truthy)","If x equals 1","If x is a valid variable name",
            "1","if x: where x is a string checks if x is truthy. Empty string '' is falsy; any non-empty string like 'hello' or '0' is truthy.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c8,
            "Can you use 'and' and 'or' to combine conditions in an if statement?",
            "No — use separate if statements","Yes: if condition1 and condition2: checks both must be True","No — Python uses && and ||","Yes but only with two conditions",
            "1","Python uses and, or, not for logical operators (not && and ||). if age >= 18 and has_id: is valid. You can chain multiple conditions.",
            "EASY"}));
        all.add(make.apply(new String[]{c8,
            "What is 'short-circuit evaluation' with 'and'?",
            "and always evaluates both sides","If the left side is False, the right side is NOT evaluated — the result is already False","and is faster than or","and only works with boolean values",
            "1","Short-circuit: with and, if the first condition is False, Python skips evaluating the second — the result must be False. With or, if the first is True, the second is skipped. This prevents errors like: if x is not None and x > 0.",
            "HARD"}));
        all.add(make.apply(new String[]{c8,
            "What is the correct way to check if a number is between 1 and 10 inclusive?",
            "if 1 <= x <= 10:","if x > 1 and x < 10:","if x between 1 and 10:","if 1 < x < 10:",
            "0","Python supports chained comparisons: 1 <= x <= 10 is equivalent to x >= 1 and x <= 10. This is clean Pythonic syntax. 1 < x < 10 would exclude the endpoints.",
            "MEDIUM"}));

        // ── Concept 9: for and while Loops ────────────────────────────────────
        String c9 = "6a256d6c91a13f5f278010a6";
        all.add(make.apply(new String[]{c9,
            "What does range(5) produce?",
            "1, 2, 3, 4, 5","0, 1, 2, 3, 4, 5","0, 1, 2, 3, 4","[0, 1, 2, 3, 4]",
            "2","range(5) generates integers from 0 to 4 (stop value is exclusive). To include 5 use range(6). range() returns a lazy iterator, not a list.",
            "EASY"}));
        all.add(make.apply(new String[]{c9,
            "What is the risk of a while loop without updating the condition variable?",
            "The loop runs exactly once","The loop raises an error","The loop runs forever (infinite loop)","The loop is skipped",
            "2","If the condition variable is never changed inside the while loop, the condition stays True and the loop runs forever. Always ensure the loop variable or condition changes on each iteration.",
            "EASY"}));
        all.add(make.apply(new String[]{c9,
            "What does enumerate(items) give you in a for loop?",
            "Only the indices","Only the values","Both the index and the value as a tuple","The length of items",
            "2","enumerate(iterable) yields (index, value) pairs. Use: for i, item in enumerate(items): to get both. Prefer this over for i in range(len(items)): items[i].",
            "EASY"}));
        all.add(make.apply(new String[]{c9,
            "for i in range(1, 10, 2). What values does i take?",
            "1, 3, 5, 7, 9","1, 2, 3, 4, 5, 6, 7, 8, 9","2, 4, 6, 8","1, 3, 5, 7",
            "0","range(1, 10, 2) starts at 1, stops before 10, with step 2: 1, 3, 5, 7, 9. The last value is 9 because the next would be 11 which exceeds the stop value.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c9,
            "What is the main difference between for and while loops?",
            "for is faster than while","for iterates over a sequence; while loops while a condition is True","while is for numbers; for is for strings","They are completely identical",
            "1","Use for when you know what to iterate over (a list, range, string). Use while when you loop until a condition changes — like waiting for valid input or processing until a target is reached.",
            "EASY"}));
        all.add(make.apply(new String[]{c9,
            "Can a for loop iterate directly over a string?",
            "No — strings must be converted to a list first","Yes — for char in 'hello': gives each character","Only for strings shorter than 10 characters","No — use range(len(string))",
            "1","Strings are iterable in Python. for char in 'hello': gives 'h', 'e', 'l', 'l', 'o' one by one. This works for lists, tuples, sets, dicts, files, and any iterable.",
            "EASY"}));
        all.add(make.apply(new String[]{c9,
            "What does zip([1,2,3], ['a','b','c']) give?",
            "[[1,'a'],[2,'b'],[3,'c']]","(1,'a'), (2,'b'), (3,'c') as pairs","[1,2,3,'a','b','c']","Error",
            "1","zip() pairs elements from multiple iterables together. zip([1,2,3], ['a','b','c']) yields (1,'a'), (2,'b'), (3,'c'). Great for iterating two related lists simultaneously.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c9,
            "What does a for loop's else clause do?",
            "Runs when the loop raises an error","Runs every time the loop body executes","Runs only if the loop completed without a break statement","Runs when the loop variable is None",
            "2","for...else: the else block runs only if the loop finished normally without hitting break. If break was hit, else is skipped. Useful for search operations: 'if break was never hit, item was not found'.",
            "HARD"}));
        all.add(make.apply(new String[]{c9,
            "You want to loop 5 times with a counter starting from 1. Which is correct?",
            "for i in range(5):","for i in range(1, 6):","for i in range(0, 5):","for i in range(1, 5):",
            "1","range(1, 6) generates 1, 2, 3, 4, 5. range(5) generates 0-4. range(1, 5) generates 1-4. When you need 1 to n inclusive, use range(1, n+1).",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c9,
            "What is the output of: for i in range(3): print(i)?",
            "1 2 3","0 1 2 3","0 1 2","1 2",
            "2","range(3) generates 0, 1, 2. print(i) prints each value on a new line. Output: 0 (newline) 1 (newline) 2.",
            "EASY"}));

        // ── Concept 10: break, continue, pass ─────────────────────────────────
        String c10 = "6a256d6c91a13f5f278010a7";
        all.add(make.apply(new String[]{c10,
            "What does break do inside a loop?",
            "Skips the current iteration and continues to the next","Exits the entire loop immediately","Pauses the loop for 1 second","Restarts the loop from the beginning",
            "1","break exits the loop entirely. Execution continues with the first statement after the loop. continue skips only the current iteration.",
            "EASY"}));
        all.add(make.apply(new String[]{c10,
            "What does continue do inside a loop?",
            "Exits the entire loop","Skips the rest of the current iteration and moves to the next iteration","Pauses execution","Restarts the loop",
            "1","continue skips remaining code in the current iteration and jumps to the next. The loop itself does not stop — only the current pass through the loop body is cut short.",
            "EASY"}));
        all.add(make.apply(new String[]{c10,
            "What is the purpose of pass in Python?",
            "Exit the current function","Skip the current loop iteration","A no-operation placeholder that satisfies Python's requirement for a non-empty code block","End the program",
            "2","pass does nothing — it is a no-op. It is used when Python requires at least one statement in a block (if, for, def, class) but you have nothing to write yet. It is a placeholder for future code.",
            "EASY"}));
        all.add(make.apply(new String[]{c10,
            "In a nested loop, what does break exit?",
            "All loops simultaneously","Only the innermost loop it is in","The outermost loop only","The entire program",
            "1","break only exits the INNERMOST loop it is directly inside. To break out of multiple loops you need additional flags or a different structure.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c10,
            "What is the for...else pattern useful for?",
            "Running code after every iteration","Running code only when the loop found no match (break was never hit)","Running code when an exception occurs","Providing a default value for the loop variable",
            "1","for...else: the else runs if the loop completed without break. Pattern: search through a list; if found hit break; else (not found) handle the miss. Cleaner than using a 'found' flag variable.",
            "HARD"}));
        all.add(make.apply(new String[]{c10,
            "You have a loop that searches a list for a target. Using break, how do you know if the target was found?",
            "break returns True if the target was found","Use a found = True flag before break; check the flag after the loop","The loop automatically stores the result","Check if the loop variable equals the target after the loop",
            "1","Common pattern: found = False before loop. Inside: if item == target: found = True; break. After: if found: ... Alternatively use for...else.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c10,
            "What is the difference between pass and continue?",
            "They are identical","pass does nothing and allows the iteration to complete; continue skips to the next iteration","continue does nothing; pass skips the iteration","pass is for functions; continue is for loops",
            "1","pass does nothing — execution continues normally after pass. continue jumps to the next loop iteration, skipping any remaining code in the current iteration.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c10,
            "Where can break and continue be used?",
            "Anywhere in a Python file","Only inside functions","Only inside for and while loops","Only inside while loops",
            "2","break and continue are only valid inside for and while loops. Using them outside a loop raises a SyntaxError.",
            "EASY"}));
        all.add(make.apply(new String[]{c10,
            "What does pass allow you to do that empty code blocks don't allow?",
            "Run faster","Write syntactically valid empty code blocks — Python requires at least one statement in any block","Skip indentation requirements","Define multiple return values",
            "1","Python raises a SyntaxError if any block is completely empty. pass satisfies the requirement for at least one statement without doing anything. Common in stub functions and empty class definitions.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c10,
            "In a while True: loop, how do you exit it?",
            "The loop exits automatically after 1000 iterations","You must use a break statement inside the loop","You cannot — it runs forever","Use while True: False",
            "1","while True: creates an intentional infinite loop. The only way to exit is with a break statement inside the loop body (typically inside an if condition). Common for menu loops and input validation.",
            "EASY"}));

        // ── Concept 11: Functions ──────────────────────────────────────────────
        String c11 = "6a256d6c91a13f5f278010a8";
        all.add(make.apply(new String[]{c11,
            "What is a function in Python?",
            "A variable that holds a number","A reusable named block of code that can accept inputs and return outputs","A special type of loop","A way to import modules",
            "1","A function is a reusable block of code defined with def. It can accept parameters as input and return a value with return. Functions prevent code repetition.",
            "EASY"}));
        all.add(make.apply(new String[]{c11,
            "What is the difference between a parameter and an argument?",
            "They are identical terms","Parameter is the variable in the function definition; argument is the actual value passed when calling","Argument is in the definition; parameter is the value passed","Parameters are only for built-in functions",
            "1","Parameter: the variable name in def greet(name) — name is the parameter. Argument: the actual value passed in the call greet('Ravi') — 'Ravi' is the argument.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c11,
            "What does a function return if it has no return statement?",
            "0","False","''","None",
            "3","A function without a return statement automatically returns None. This is the same as writing return None or return by itself.",
            "EASY"}));
        all.add(make.apply(new String[]{c11,
            "def greet(name='Guest'): — what happens if you call greet() with no arguments?",
            "Error — name is required","name gets None","name gets the default value 'Guest'","Error — default parameters are not allowed",
            "2","Default parameter values are used when the argument is not provided. greet() uses name='Guest'. greet('Ravi') overrides the default with 'Ravi'.",
            "EASY"}));
        all.add(make.apply(new String[]{c11,
            "Can a Python function return multiple values?",
            "No — only one return value allowed","Yes — return a, b returns a tuple that can be unpacked: x, y = func()","Yes but only if they are the same type","No — use a list instead",
            "1","Python functions can return multiple values separated by commas. They are returned as a tuple. Use x, y = func() to unpack them. return a, b is shorthand for return (a, b).",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c11,
            "What is a local variable?",
            "A variable defined outside any function","A variable defined inside a function — only accessible within that function","A variable starting with underscore","A global variable",
            "1","Local variables are created inside a function and only exist for the duration of that function call. They are not accessible outside the function and are destroyed when the function returns.",
            "EASY"}));
        all.add(make.apply(new String[]{c11,
            "Can a function call another function?",
            "No — functions are isolated","Yes — functions can call any accessible function including themselves (recursion)","Only built-in functions","Only if they are in the same class",
            "1","Functions can call other functions freely. This is how modular programming works — small focused functions composed together. A function can also call itself (recursion).",
            "EASY"}));
        all.add(make.apply(new String[]{c11,
            "What is the difference between print() and return in a function?",
            "They are identical","print() displays output to the screen; return sends a value back to the caller for further use","return displays output; print() sends value back","print() is faster",
            "1","print() displays output but the function still returns None. return sends a value back to the caller that can be stored, passed to other functions, or used in expressions. print() output cannot be used programmatically.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c11,
            "Are Python functions first-class citizens?",
            "No — functions are special and cannot be stored in variables","Yes — functions can be stored in variables, passed as arguments and returned from other functions","Only lambda functions","Only built-in functions",
            "1","In Python, functions are first-class objects. You can assign them to variables (fn = print), pass them as arguments (map(str, nums)), and return them from other functions (decorators and factories).",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c11,
            "What happens when Python reaches a return statement?",
            "The function pauses and can be resumed","The function immediately exits and sends the value back to the caller","All variables in the function are printed","Python continues to the next function in the file",
            "1","When Python reaches a return statement, the function immediately exits and the return value is sent back to wherever the function was called from. Any code after return in the same block is never executed.",
            "EASY"}));

        // ── Concept 12: Exception Handling ────────────────────────────────────
        String c12 = "6a256d6c91a13f5f278010a9";
        all.add(make.apply(new String[]{c12,
            "What is the purpose of exception handling in Python?",
            "To make code run faster","To prevent any errors from occurring","To catch and respond to runtime errors so the program can continue or exit gracefully","To automatically fix bugs",
            "2","Exception handling catches runtime errors and lets your program respond appropriately — show a user-friendly message, use a fallback value, or exit cleanly — instead of crashing with a traceback.",
            "EASY"}));
        all.add(make.apply(new String[]{c12,
            "What goes inside the try block?",
            "Error handling code","Code that runs after the error","Code that might raise an exception","Code that always runs",
            "2","The try block contains code that might fail. If an exception occurs inside try, Python immediately jumps to the matching except block. Code after the error line in try is skipped.",
            "EASY"}));
        all.add(make.apply(new String[]{c12,
            "What is the difference between a specific except and a bare except:?",
            "They are identical","except ValueError: catches only ValueError; bare except: catches everything including system exits — bare except is discouraged","Bare except is faster","Specific except only works for built-in errors",
            "1","except ValueError: catches only that exception type, making error handling precise. Bare except: catches everything including KeyboardInterrupt and SystemExit, hiding bugs. Always catch specific exceptions.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c12,
            "What does the finally block do?",
            "Runs only when an exception occurs","Runs only when no exception occurs","Always runs — whether an exception occurred or not — used for cleanup","Catches all remaining exceptions",
            "2","finally always executes regardless of whether an exception occurred. It is used to ensure cleanup code runs: closing files, releasing database connections, freeing resources.",
            "EASY"}));
        all.add(make.apply(new String[]{c12,
            "int('hello') raises which exception?",
            "TypeError","SyntaxError","ValueError","NameError",
            "2","int('hello') raises ValueError: invalid literal for int() with base 10: 'hello'. ValueError means the value is in the wrong format. TypeError would be raised by something like int([1,2,3]) where the type itself is wrong.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c12,
            "What does the else block in try...except...else do?",
            "Runs when an exception occurs","Runs only when NO exception occurred in the try block","Always runs like finally","Catches unhandled exceptions",
            "1","The else block runs ONLY when the try block completed without raising any exception. It keeps the 'success path' code separate from the error handling code, making logic clearer.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c12,
            "How do you access the exception message in an except block?",
            "except Error.message:","except Exception get e:","except Exception as e: — then use str(e) or e.args","exception.getMessage()",
            "2","The as keyword binds the exception to a variable: except ValueError as e:. Access the message with str(e). Access the type with type(e).__name__. Access arguments with e.args.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c12,
            "Should you put as much code as possible inside a try block?",
            "Yes — the larger the try block the better","No — only wrap the specific line(s) that might fail to keep error handling precise","Yes — this catches all possible errors","It makes no difference",
            "1","Keep try blocks small and targeted. Wrapping too much code in try makes it hard to know which line caused the error and may catch unintended exceptions. Only wrap the specific operation that might fail.",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c12,
            "What is the correct way to handle multiple exception types separately?",
            "except (ValueError, TypeError): catches both but handles them the same","Multiple except blocks: except ValueError: ... except TypeError: ...","Using nested try blocks only","Python cannot handle multiple exception types",
            "1","You can have multiple except blocks to handle different exceptions differently. except ValueError: handles one type, except TypeError: handles another. You can also catch multiple in one block: except (ValueError, TypeError):",
            "MEDIUM"}));
        all.add(make.apply(new String[]{c12,
            "What is the 'safe input' pattern using try/except for user input?",
            "Always use input() without any handling",
            "Validate input before the try block",
            "Wrap int(input()) in try/except ValueError with a loop to keep asking until valid input is given",
            "Use a global exception handler",
            "2","while True: try: value = int(input('Enter: ')); break except ValueError: print('Invalid, try again'). This loops until the user enters valid input, catching ValueError when non-numeric input is given.",
            "HARD"}));

        questionRepository.saveAll(all);
        System.out.println("✅ Python Basics questions seeded — " + all.size());
    }

    // ─── PYTHON FUNDAMENTALS — CONCEPTS 2-12 QUESTIONS ──────────────────────
    private void seedPythonFundamentalsRemainingQuestions() {
        final String subjectId = "6a2533020fc2fb031334a40e";
        List<Question> all = new java.util.ArrayList<>();

        // helper lambda
        java.util.function.Function<String[], Question> make = arr -> {
            Question q = new Question();
            q.setConceptId(arr[0]); q.setSubjectId(subjectId);
            q.setText(arr[1]);
            q.setOptions(List.of(arr[2], arr[3], arr[4], arr[5]));
            q.setCorrectIndex(Integer.parseInt(arr[6]));
            q.setExplanation(arr[7]);
            q.setDifficulty(arr[8]);
            return q;
        };

        // ── Concept 2: Python 2 vs Python 3 ──────────────────────────────────
        String c2 = "6a2533030fc2fb031334a410";
        all.add(make.apply(new String[]{c2,
            "When did Python 2 officially reach end-of-life?",
            "January 1, 2022", "January 1, 2020", "January 1, 2018", "December 31, 2023",
            "1",
            "Python 2 reached end-of-life on January 1, 2020. After this date, no security patches, bug fixes or new features are released for Python 2. All modern projects use Python 3.",
            "EASY"}));

        all.add(make.apply(new String[]{c2,
            "What is the correct way to print 'Hello' in Python 3?",
            "print 'Hello'", "echo 'Hello'", "print('Hello')", "console.log('Hello')",
            "2",
            "In Python 3, print is a function and must be called with parentheses: print('Hello'). The Python 2 syntax print 'Hello' without parentheses is a SyntaxError in Python 3.",
            "EASY"}));

        all.add(make.apply(new String[]{c2,
            "What does 10 / 3 evaluate to in Python 3?",
            "3", "3.0", "4", "3.3333333333333335",
            "3",
            "In Python 3, the / operator always performs true (float) division. 10 / 3 gives 3.3333333333333335. In Python 2 it would give 3 (integer division). Use // for integer division in Python 3.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c2,
            "Which operator gives integer (floor) division in Python 3?",
            "//", "/", "%", "**",
            "0",
            "The // operator performs floor division (integer division) in Python 3. 10 // 3 gives 3. This explicitly requests integer division, unlike / which always gives a float.",
            "EASY"}));

        all.add(make.apply(new String[]{c2,
            "In Python 3, what does the input() function always return?",
            "int", "str", "float", "bool",
            "1",
            "input() in Python 3 always returns a string (str), regardless of what the user types. You must explicitly cast it: int(input()) or float(input()) to get a number.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c2,
            "Which Python version should you learn today?",
            "Python 1.x", "Python 2.x", "Any version — they are identical", "Python 3.x",
            "3",
            "You should learn Python 3. Python 2 reached end-of-life in 2020. All modern libraries (NumPy, Django, TensorFlow, FastAPI) require Python 3. There is no reason to learn Python 2.",
            "EASY"}));

        all.add(make.apply(new String[]{c2,
            "What does range() return in Python 3 compared to Python 2?",
            "A list in both versions",
            "A lazy iterator in Python 3 (not a list)",
            "A tuple in Python 3",
            "A set in Python 3",
            "1",
            "In Python 3, range() returns a lazy iterator that generates values on demand — it does not create the full list in memory. In Python 2, range() returned a list. Python 3's approach is more memory-efficient for large ranges.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c2,
            "How do you check your Python version from the terminal?",
            "python -check", "python --info", "python --version", "python -v",
            "2",
            "The command python --version (or python3 --version) prints the installed Python version. You should see Python 3.x.x. If you see Python 2.x.x, use python3 command instead.",
            "EASY"}));

        all.add(make.apply(new String[]{c2,
            "In Python 3, strings are Unicode by default. What was the default string type in Python 2?",
            "Unicode strings", "Bytes (ASCII) strings", "UTF-32 strings", "Integer arrays",
            "1",
            "In Python 2, regular strings (str) were byte strings (ASCII). Unicode strings required a u prefix: u'hello'. In Python 3, all strings are Unicode by default, making internationalisation much simpler.",
            "HARD"}));

        all.add(make.apply(new String[]{c2,
            "You see a Python tutorial with the code: print 'Welcome'. What can you conclude?",
            "This is valid Python 3 code",
            "This is Python 2 code and will not work in Python 3",
            "This uses a special print module",
            "This works in all Python versions",
            "1",
            "print 'Welcome' without parentheses is Python 2 syntax. In Python 3 this raises a SyntaxError. Always check the Python version of tutorials — anything using print without () is Python 2 and should be avoided.",
            "MEDIUM"}));

        // ── Concept 3: Interpreted vs Compiled Language ────────────────────────
        String c3 = "6a2533030fc2fb031334a411";
        all.add(make.apply(new String[]{c3,
            "What does it mean that Python is an 'interpreted' language?",
            "Python code must be manually compiled before running",
            "Python translates source code to machine code once and stores it permanently",
            "Python executes code line by line through an interpreter without a manual compilation step",
            "Python runs exclusively in a web browser",
            "2",
            "An interpreted language executes code directly line by line. In Python, you run python file.py and the interpreter reads and executes each line. There is no separate compile step that the developer needs to perform.",
            "EASY"}));

        all.add(make.apply(new String[]{c3,
            "What is the name of the default Python interpreter written in C?",
            "PyPy", "CPython", "Jython", "IronPython",
            "1",
            "CPython is the reference implementation and default Python interpreter. It is written in C. PyPy is a JIT-compiled alternative, Jython runs on the JVM, and IronPython runs on .NET.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c3,
            "In which folder does Python store compiled bytecode files?",
            "__bytecode__", "__cache__", "__compiled__", "__pycache__",
            "3",
            "Python automatically compiles source code to bytecode and stores it in a __pycache__ folder as .pyc files. This speeds up subsequent runs. You never need to manage these files manually.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c3,
            "What happens if there is an error on line 5 of a Python script that has 10 lines?",
            "The entire script fails before running any line",
            "Python runs lines 1-4 successfully, then raises an error on line 5",
            "Python skips line 5 and continues from line 6",
            "Python runs all 10 lines but marks line 5 as invalid",
            "1",
            "Python is interpreted line by line. If line 5 has a runtime error, lines 1-4 have already executed successfully. The program stops at line 5 and raises an exception. This is different from compiled languages where errors are caught before any code runs.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c3,
            "What is the Python REPL?",
            "A file editor for Python scripts",
            "A Python package manager",
            "An interactive mode where Python executes one line at a time and shows the result",
            "A Python testing framework",
            "2",
            "REPL stands for Read-Eval-Print Loop. Open it by typing python in your terminal. It reads your input, evaluates it, prints the result, and loops. It is ideal for testing small ideas without creating a file.",
            "EASY"}));

        all.add(make.apply(new String[]{c3,
            "Compared to compiled languages like C++, what is the main trade-off with Python being interpreted?",
            "Python code is harder to read",
            "Python cannot handle numbers",
            "Python does not support object-oriented programming",
            "Python is generally slower in execution speed than compiled languages",
            "3",
            "Interpreted languages have overhead because code is executed line by line at runtime rather than pre-compiled to optimised machine code. Python is slower than C/C++ but this rarely matters for web, data and scripting work where development speed is the priority.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c3,
            "Which Python implementation uses Just-In-Time (JIT) compilation for faster execution?",
            "CPython", "PyPy", "Jython", "MicroPython",
            "1",
            "PyPy is an alternative Python implementation that uses JIT compilation to significantly speed up Python code. CPython is the standard interpreter. Jython runs on the JVM. MicroPython targets microcontrollers.",
            "HARD"}));

        all.add(make.apply(new String[]{c3,
            "What is bytecode in the context of Python?",
            "Binary machine code that runs directly on the CPU",
            "A lower-level representation of Python code that the Python Virtual Machine executes",
            "A Python file with a .pyc extension that you write manually",
            "HTML code embedded in Python",
            "1",
            "Python internally compiles source code (.py) to bytecode (.pyc) which is then executed by the Python Virtual Machine (PVM). Bytecode is not machine code — it still needs the PVM to run. This process is automatic.",
            "HARD"}));

        all.add(make.apply(new String[]{c3,
            "In a compiled language like C++, when are errors typically detected?",
            "During runtime, when the specific line executes",
            "Only when the program is deployed to a server",
            "During the compilation step, before the program runs",
            "Errors are never detected automatically",
            "2",
            "Compiled languages detect many errors at compile time — before the program runs at all. Python only catches runtime errors when the specific line with the error executes. This is a key difference between interpreted and compiled approaches.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c3,
            "What command opens the Python interactive REPL in the terminal?",
            "python --interactive", "python3 --repl", "python", "run python",
            "2",
            "Simply typing python (or python3 on some systems) in the terminal opens the interactive REPL. You will see the >>> prompt. Type exit() or press Ctrl+D to quit.",
            "EASY"}));

        // ── Concept 4: PEP 8 Style Guide ──────────────────────────────────────
        String c4 = "6a2533030fc2fb031334a412";
        all.add(make.apply(new String[]{c4,
            "What does PEP stand for in PEP 8?",
            "Python Execution Protocol", "Python Enhancement Proposal", "Python Error Prevention", "Python Environment Package",
            "1",
            "PEP stands for Python Enhancement Proposal. PEP 8 is the style guide for Python code. It defines conventions for naming, indentation, line length and formatting that all Python developers are expected to follow.",
            "EASY"}));

        all.add(make.apply(new String[]{c4,
            "According to PEP 8, which naming convention should be used for variable and function names?",
            "camelCase like userName", "PascalCase like UserName", "snake_case like user_name", "UPPER_CASE like USERNAME",
            "2",
            "PEP 8 specifies snake_case for variable and function names: user_name, calculate_score, get_user_by_id. camelCase is a JavaScript convention. PascalCase is for class names. UPPER_SNAKE_CASE is for constants.",
            "EASY"}));

        all.add(make.apply(new String[]{c4,
            "According to PEP 8, which naming convention should be used for class names?",
            "snake_case", "PascalCase (CapWords)", "UPPER_SNAKE_CASE", "camelCase",
            "1",
            "PEP 8 specifies PascalCase (also called CapWords) for class names: HunterProfile, BankAccount, UserService. Each word starts with a capital letter. This distinguishes classes from functions and variables.",
            "EASY"}));

        all.add(make.apply(new String[]{c4,
            "What naming convention does PEP 8 recommend for constants?",
            "snake_case", "PascalCase", "camelCase", "UPPER_SNAKE_CASE",
            "3",
            "Constants that should not change use UPPER_SNAKE_CASE: MAX_CONNECTIONS = 100, DEFAULT_TIMEOUT = 30, API_BASE_URL = '...'. This visually distinguishes constants from regular variables.",
            "EASY"}));

        all.add(make.apply(new String[]{c4,
            "According to PEP 8, how many spaces should be used per indentation level?",
            "2 spaces", "4 spaces", "8 spaces", "1 tab",
            "1",
            "PEP 8 specifies 4 spaces per indentation level. Never use tabs (or mix tabs and spaces). Configure your editor to insert 4 spaces when you press Tab.",
            "EASY"}));

        all.add(make.apply(new String[]{c4,
            "PEP 8 recommends a maximum line length of how many characters?",
            "100 characters", "120 characters", "60 characters", "79 characters",
            "3",
            "PEP 8 recommends a maximum of 79 characters per line. This keeps code readable in side-by-side views and on terminals. Many teams extend this to 99 or 119, but 79 is the official PEP 8 standard.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c4,
            "Which tool automatically formats Python code to follow PEP 8?",
            "pylint", "mypy", "black", "pytest",
            "2",
            "Black is an opinionated Python code formatter. Running black myfile.py reformats the entire file to comply with PEP 8 style. pylint checks for errors and style, mypy checks types, pytest runs tests.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c4,
            "Which of these variable names violates PEP 8 conventions?",
            "student_name", "get_user_rank", "HunterProfile", "calculate_xp",
            "2",
            "HunterProfile uses PascalCase which is the convention for class names, not variable names. Variables should use snake_case: hunter_profile. student_name, get_user_rank and calculate_xp all correctly use snake_case.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c4,
            "According to PEP 8, how many blank lines should separate top-level function and class definitions?",
            "One blank line", "Three blank lines", "No blank lines", "Two blank lines",
            "3",
            "PEP 8 specifies two blank lines between top-level function and class definitions. Inside a class, methods are separated by one blank line. This visual spacing helps distinguish logical sections of code.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c4,
            "Which of the following correctly follows PEP 8 import style?",
            "import os, sys, math",
            "import os\nimport sys\nimport math",
            "from os import *",
            "IMPORT os; IMPORT sys",
            "1",
            "PEP 8 says one import per line. import os, sys on one line is valid Python but violates PEP 8 style. from module import * (wildcard import) is also discouraged as it pollutes the namespace.",
            "MEDIUM"}));

        // ── Concept 5: Indentation Rules ──────────────────────────────────────
        String c5 = "6a2533030fc2fb031334a413";
        all.add(make.apply(new String[]{c5,
            "In Python, what defines a code block (e.g. the body of an if statement)?",
            "Curly braces { }",
            "Parentheses ( )",
            "Square brackets [ ]",
            "Consistent indentation (spaces or tabs)",
            "3",
            "Python uses indentation to define code blocks. Unlike C, Java or JavaScript which use curly braces, Python uses the amount of whitespace at the beginning of each line. The colon (:) at the end of a statement introduces an indented block.",
            "EASY"}));

        all.add(make.apply(new String[]{c5,
            "How many spaces per indentation level does PEP 8 recommend?",
            "2 spaces", "3 spaces", "4 spaces", "8 spaces",
            "2",
            "PEP 8 recommends 4 spaces per indentation level. Configure your editor to convert Tab key presses to 4 spaces. Never mix tabs and spaces in the same file.",
            "EASY"}));

        all.add(make.apply(new String[]{c5,
            "What error does Python raise when a colon (:) is followed by a line with no indentation?",
            "SyntaxError", "IndentationError: expected an indented block", "NameError", "TypeError",
            "1",
            "If you write if True: followed by code at the same indentation level (not indented), Python raises IndentationError: expected an indented block. Every block statement ending with : must have at least one indented line below it.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c5,
            "What happens in Python 3 if you mix tabs and spaces for indentation?",
            "Python converts tabs to 4 spaces automatically",
            "Python raises a TabError",
            "Python ignores the inconsistency",
            "The code runs but produces unexpected output",
            "1",
            "Python 3 strictly disallows mixing tabs and spaces for indentation and raises TabError: inconsistent use of tabs and spaces in indentation. Always use spaces (4 per level) and configure your editor to insert spaces for Tab.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c5,
            "In Python, what does dedenting (reducing indentation) signify?",
            "The start of a new function", "A syntax error", "The end of the current code block", "A comment line",
            "2",
            "When you reduce the indentation level, Python knows the current code block has ended. For example, going from 8 spaces back to 4 spaces exits the inner block, and going to 0 spaces exits the outer block.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c5,
            "How many indentation levels are used in a loop nested inside an if block?",
            "0 spaces (no indentation needed)",
            "4 spaces for both",
            "8 spaces for the loop body (2 levels)",
            "The outer block uses 4 spaces, inner block also uses 4 spaces from the outer level total 8",
            "3",
            "Each nesting level adds 4 more spaces. The if block body is at 4 spaces. A for loop inside the if is also at 4 spaces (relative to if). The for loop's body is at 8 spaces total (4 for if + 4 for for). Each level is 4 spaces further than the previous.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c5,
            "Which of the following would cause an IndentationError in Python?",
            "Using 4 spaces consistently throughout the file",
            "Having an empty function body with pass",
            "Mixing 3 spaces and 4 spaces in the same block",
            "Using 8 spaces for doubly-nested code",
            "2",
            "Indentation must be consistent within a block. Mixing 3 and 4 spaces (or any inconsistent amount) in the same block raises IndentationError. Python does not require exactly 4 spaces — it requires whatever amount you started with to be consistent.",
            "HARD"}));

        all.add(make.apply(new String[]{c5,
            "What does the pass statement do in relation to indentation requirements?",
            "It removes the need for any indentation",
            "It satisfies Python's requirement for at least one statement in a block",
            "It marks the end of an indented block",
            "It adds extra indentation automatically",
            "1",
            "Python requires at least one statement inside every block (if, for, def, class). pass is a no-operation statement that satisfies this requirement. It is commonly used for empty functions, classes, or placeholder if blocks.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c5,
            "What does Python's use of indentation as syntax (rather than curly braces) enforce?",
            "Code runs twice as fast",
            "Variables must be declared before use",
            "Code that is visually structured is also logically structured — they cannot disagree",
            "All functions must return a value",
            "2",
            "In languages with curly braces, code can be visually indented one way but logically structured differently. Python's indentation-as-syntax guarantees that the visual structure IS the logical structure — unreadable indentation is also syntactically invalid.",
            "HARD"}));

        all.add(make.apply(new String[]{c5,
            "Which of these correctly uses indentation for a function with an if/else inside it?",
            "def greet():\nif True:\nprint('yes')\nelse:\nprint('no')",
            "def greet():\n    if True:\n        print('yes')\n    else:\n        print('no')",
            "def greet(): { if True: print('yes') else: print('no') }",
            "def greet() =>\n  if True => print('yes')\n  else => print('no')",
            "1",
            "The function body is indented 4 spaces. The if/else are inside the function (4 spaces). The print statements inside if/else are indented 8 spaces (4 for function + 4 for if/else). This is the correct Python indentation pattern.",
            "MEDIUM"}));

        // ── Concept 6: Comments and Docstrings ────────────────────────────────
        String c6 = "6a2533030fc2fb031334a414";
        all.add(make.apply(new String[]{c6,
            "Which character starts a single-line comment in Python?",
            "//", "/*", "#", "--",
            "2",
            "The # character starts a single-line comment in Python. Everything after # on that line is ignored by the interpreter. // is used for comments in JavaScript and Java. Python has no multi-line comment syntax — use multiple # lines.",
            "EASY"}));

        all.add(make.apply(new String[]{c6,
            "Where must a docstring be placed to document a function?",
            "Anywhere inside the function",
            "As the very first statement inside the function, immediately after def",
            "After all the code in the function",
            "Outside the function, above the def line",
            "1",
            "A docstring must be the very first statement in a function (or class or module) body, placed immediately after the def line. It must be a string literal (usually triple-quoted). Python stores it in function.__doc__.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c6,
            "How do you access a function's docstring programmatically?",
            "function.comment", "function.doc", "function.__doc__", "function.help",
            "2",
            "The docstring is stored in the function's __doc__ attribute. Access it with function.__doc__ or use help(function) which displays it with additional formatting.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{
        	    c6,
        	    "Which of the following is the correct way to write a docstring?",
        	    "// A function that adds two numbers",
        	    "# \"A function that adds two numbers\"",
        	    "\"\"\"A function that adds two numbers.\"\"\"",
        	    "/* A function that adds two numbers */",
        	    "2",
        	    "Docstrings use triple quotes: \"\"\"description\"\"\" or '''description'''. They are placed immediately after the def, class or module statement. // and /* */ are not valid Python comment syntax.",
        	    "EASY"
        	}));

        all.add(make.apply(new String[]{c6,
            "According to best practices, what should comments explain?",
            "What the code does (restating the logic)",
            "The exact same thing as the variable names",
            "Why the code does something — the reasoning, not the mechanics",
            "Every single line of code",
            "2",
            "Good comments explain WHY — the reasoning, constraints, or context that is not obvious from the code itself. The code already shows WHAT it does. A comment like # add x and y before x + y adds no value.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c6,
            "What is a TODO comment used for?",
            "Marking lines that should be deleted", "Triggering a runtime warning", "Marking incomplete or planned work for future attention", "Disabling code from running",
            "2",
            "# TODO: description is a convention for marking work that needs to be done. Many editors highlight TODO comments specially. It is not a Python keyword — just a widely understood convention.",
            "EASY"}));

        all.add(make.apply(new String[]{c6,
            "Which Python function displays a function's docstring with formatted output?",
            "print(function)", "str(function)", "type(function)", "help(function)",
            "3",
            "help(function) displays the docstring with additional formatting — the function signature, class name and docstring content. It is Python's built-in documentation system. You can also see the raw string with function.__doc__.",
            "EASY"}));

        all.add(make.apply(new String[]{c6,
            "Does Python have a built-in multi-line comment syntax like /* */ in other languages?",
            "Yes, using triple quotes /* */",
            "Yes, using the ## prefix",
            "No — use multiple # lines for multi-line comments",
            "Yes, using the comment: keyword",
            "2",
            "Python does not have a dedicated multi-line comment syntax. The common workaround is to use multiple # lines. Triple-quoted strings can act as multi-line comments but they are technically string literals, not comments — they are stored in memory if not assigned.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c6,
            "What is the difference between a comment and a docstring in Python?",
            "There is no difference — both are ignored completely",
            "Comments use # and are ignored; docstrings use triple quotes and are stored as the function's __doc__ attribute",
            "Docstrings use # and comments use triple quotes",
            "Comments are only for classes; docstrings are only for functions",
            "1",
            "Comments (# ...) are completely ignored by Python — not stored anywhere. Docstrings (triple-quoted strings at the start of a function/class) are stored as the __doc__ attribute and accessible at runtime. They are Python's built-in documentation mechanism.",
            "HARD"}));

        all.add(make.apply(new String[]{c6,
            "Which of these is a poor comment that adds no value?",
            "# Using floor division to avoid float issues with database int column",
            "# Workaround for API bug in version 2.3 — remove when upgrading",
            "# Add x and y",
            "# Retry up to 3 times because the external service has intermittent failures",
            "2",
            "# Add x and y before result = x + y is a useless comment — it restates what the code already clearly shows. Good comments explain WHY (a constraint, a workaround, a non-obvious decision), not WHAT (which the code already shows).",
            "MEDIUM"}));

        // ── Concept 7: Built-in Data Types ────────────────────────────────────
        String c7 = "6a2533030fc2fb031334a415";
        all.add(make.apply(new String[]{c7,
            "Which of the following is NOT one of Python's five core data types?",
            "int", "float", "array", "bool",
            "2",
            "Python's five core built-in types are int, float, str, bool and NoneType. array is not a core built-in type — it requires importing the array module. Lists, dicts and sets are also built-in but separate from these five primitives.",
            "EASY"}));

        all.add(make.apply(new String[]{c7,
            "What does type(42) return?",
            "<class 'number'>", "<class 'int'>", "<class 'integer'>", "<class 'num'>",
            "1",
            "type(42) returns <class 'int'>. Python integers are of type int. The type() function is your primary tool for checking what type a value is during debugging.",
            "EASY"}));

        all.add(make.apply(new String[]{c7,
            "What is the result of 0.1 + 0.2 == 0.3 in Python?",
            "True", "False", "Error", "None",
            "1",
            "0.1 + 0.2 == 0.3 evaluates to False in Python. This is not a Python bug — it is due to IEEE 754 floating-point representation. 0.1 + 0.2 actually equals 0.30000000000000004. Use round() for display or the decimal module for precise financial math.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c7,
            "What is the result of True + True in Python?",
            "Error", "True", "2", "'TrueTrue'",
            "2",
            "True + True equals 2 because bool is a subclass of int in Python. True has the integer value 1 and False has the value 0. So True + True = 1 + 1 = 2.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c7,
            "What does type(None) return?",
            "<class 'null'>", "<class 'None'>", "<class 'NoneType'>", "<class 'empty'>",
            "2",
            "type(None) returns <class 'NoneType'>. None is a singleton of the NoneType class. There is exactly one None object in Python.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c7,
            "Which statement about Python's bool type is correct?",
            "bool is a completely separate type with no relation to int",
            "bool is a subclass of int — True equals 1 and False equals 0",
            "True and False are variables that can be reassigned",
            "bool can hold any of three values: True, False or Unknown",
            "1",
            "bool is a subclass of int in Python. True == 1 and False == 0 evaluate to True. This is why True + True == 2 works. In Python 3, True and False are keywords and cannot be reassigned.",
            "HARD"}));

        all.add(make.apply(new String[]{c7,
            "What is the difference between None and False in Python?",
            "They are identical — None is just another name for False",
            "None means no value exists; False is a boolean value meaning 'not true'",
            "None is 0 and False is -1",
            "None is only used in functions; False is only used in conditions",
            "1",
            "None and False are different. None means the absence of a value — the variable has nothing. False is a boolean value representing a logical falsehood. Both are falsy (evaluate to False in conditions), but they are different types: NoneType vs bool.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c7,
            "What function checks if a value is an instance of a given type, including subclasses?",
            "type()", "isinstance()", "checktype()", "typecheck()",
            "1",
            "isinstance(value, type) checks if a value is an instance of the given type or any of its subclasses. isinstance(True, int) is True because bool is a subclass of int. type(True) == int is False because type() checks the exact type.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c7,
            "In Python, which of these is NOT a numeric type?",
            "int", "float", "str", "complex",
            "2",
            "Python's numeric types are int, float and complex. str (string) is a text type, not numeric. Although strings can represent numbers ('42'), you cannot perform arithmetic on them without converting first.",
            "EASY"}));

        all.add(make.apply(new String[]{c7,
            "What is the correct way to check if a value is of type int?",
            "value.type == int", "value is int", "isinstance(value, int)", "value.class == int",
            "2",
            "isinstance(value, int) is the correct and preferred way. It also handles subclasses. You can also use type(value) == int but this does not handle subclasses. value is int checks identity with the int class object itself, not the type of value.",
            "MEDIUM"}));

        // ── Concept 8: Variables and Input ────────────────────────────────────
        String c8 = "6a2533030fc2fb031334a416";
        all.add(make.apply(new String[]{c8,
            "In Python, how do you create a variable?",
            "var x = 5",
            "int x = 5",
            "declare x = 5",
            "x = 5",
            "3",
            "In Python you create a variable simply by assigning a value: x = 5. No declaration keyword (var, int, let) is needed. Python creates the variable the moment you assign a value to it.",
            "EASY"}));

        all.add(make.apply(new String[]{c8,
            "Which of the following is NOT a valid Python variable name?",
            "_private", "user_name", "2fast", "totalScore",
            "2",
            "Variable names cannot start with a digit. 2fast is invalid — it starts with 2. _private (starts with underscore), user_name (snake_case) and totalScore (camelCase, valid but not PEP 8) are all valid variable names.",
            "EASY"}));

        all.add(make.apply(new String[]{c8,
            "What does input() always return in Python 3?",
            "int", "float", "str", "The type depends on what the user types",
            "2",
            "input() always returns str (string) in Python 3, regardless of what the user types. If a user types 42, you get the string '42', not the integer 42. Always cast with int() or float() when you need numbers.",
            "EASY"}));

        all.add(make.apply(new String[]{c8,
            "What is the result of: a = b = c = 0?",
            "SyntaxError — you cannot chain assignments",
            "Only c is set to 0",
            "All three variables a, b and c are assigned the value 0",
            "a gets 0, b and c get None",
            "2",
            "Python supports chained assignment: a = b = c = 0 assigns 0 to all three variables simultaneously. This is valid Python and a concise way to initialise multiple variables to the same value.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c8,
            "What does this code do? x, y = 10, 20",
            "SyntaxError", "x gets 10 and y gets 20 via tuple unpacking", "x gets 20 and y gets 10", "Both x and y get 30",
            "1",
            "x, y = 10, 20 is tuple unpacking. x is assigned 10 and y is assigned 20. This is a common Python pattern. You can also swap variables: x, y = y, x without needing a temporary variable.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c8,
            "Are Python variable names case-sensitive?",
            "No — xp and XP are the same variable",
            "Only for variable names starting with uppercase",
            "Yes — xp, XP and Xp are three different variables",
            "Only inside functions",
            "2",
            "Python is case-sensitive. xp, XP and Xp are three completely different variables. Changing the case creates a new, separate variable. This is a common source of bugs for beginners.",
            "EASY"}));

        all.add(make.apply(new String[]{c8,
            "What is the correct way to get an integer from the user?",
            "input(int('Enter number: '))",
            "int = input('Enter number: ')",
            "number = int(input('Enter number: '))",
            "number = input(int, 'Enter number: ')",
            "2",
            "int(input('Enter number: ')) first calls input() to get a string, then wraps it in int() to convert to integer. This is the standard pattern for reading numeric input in Python.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c8,
            "Can a Python variable change its type after it is first assigned?",
            "No — once assigned as int, always int",
            "Yes — x = 5 and then x = 'hello' is valid",
            "Only if you use the var keyword",
            "Only inside a function",
            "1",
            "Python variables can be reassigned to any type: x = 5 makes x an int, then x = 'hello' makes x a str. The variable name is just a label pointing to an object. Reassigning points the label to a different object of any type.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c8,
            "What happens when you try to use a variable that has never been assigned?",
            "Python uses 0 as the default value",
            "Python uses None as the default value",
            "Python raises a NameError",
            "Python creates the variable with an empty string",
            "2",
            "Using an undefined variable raises NameError: name 'x' is not defined. Python does not provide default values for unassigned variables. Always assign a value before using a variable.",
            "EASY"}));

        all.add(make.apply(new String[]{c8,
            "What does the del statement do to a variable?",
            "Sets the variable to None",
            "Sets the variable to 0",
            "Renames the variable",
            "Removes the variable name binding — accessing it after del raises NameError",
            "3",
            "del variable removes the name binding. After del x, trying to use x raises NameError: name 'x' is not defined. This is different from setting x = None which keeps the variable pointing to None.",
            "HARD"}));

        // ── Concept 9: Type Casting ────────────────────────────────────────────
        String c9 = "6a2533030fc2fb031334a417";
        all.add(make.apply(new String[]{c9,
            "What does int('42') return?",
            "Error", "'42' (string unchanged)", "42 (integer)", "42.0 (float)",
            "2",
            "int('42') converts the string '42' to the integer 42. This is explicit type conversion (casting). Python's int() function can convert strings containing valid integers and floats.",
            "EASY"}));

        all.add(make.apply(new String[]{c9,
            "What does int(3.9) return?",
            "4 (rounds to nearest)", "3.9", "3 (truncates toward zero)", "Error",
            "2",
            "int(3.9) returns 3, not 4. int() truncates (chops off) the decimal part — it does NOT round. Use round() if you want rounding. int(-3.9) returns -3 (toward zero, not -4).",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c9,
            "What error does int('hello') raise?",
            "TypeError", "ValueError", "SyntaxError", "RuntimeError",
            "1",
            "int('hello') raises ValueError: invalid literal for int() with base 10: 'hello'. ValueError means the value is the wrong format for the conversion, not the wrong type. int([]) would raise TypeError.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c9,
            "Which of these correctly converts user input to a floating-point number?",
            "float = input('Enter: ')",
            "input(float('Enter: '))",
            "float(input('Enter: '))",
            "input('Enter: ', float)",
            "2",
            "float(input('Enter: ')) first calls input() to get a string, then float() converts it to a float. This is the correct pattern: wrap the input() call inside the conversion function.",
            "EASY"}));

        all.add(make.apply(new String[]{c9,
            "What does bool(0) return?",
            "True", "False", "0", "None",
            "1",
            "bool(0) returns False. 0 is a falsy value in Python. The falsy values are: False, 0, 0.0, '' (empty string), None, [] (empty list) and {} (empty dict). Everything else is truthy.",
            "EASY"}));

        all.add(make.apply(new String[]{c9,
            "What is the result of bool('') and bool('hello')?",
            "True and True",
            "False and False",
            "False and True",
            "True and False",
            "2",
            "bool('') is False — empty string is falsy. bool('hello') is True — any non-empty string is truthy. This is important when checking if a string has content: if my_string: checks if it is non-empty.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c9,
            "Which of the following will raise a ValueError?",
            "int('42')", "float('3.14')", "int(float('3.14'))", "int('3.14')",
            "3",
            "int('3.14') raises ValueError because '3.14' is not a valid integer string. If you need to convert '3.14' to an integer, first convert to float: int(float('3.14')) gives 3.",
            "HARD"}));

        all.add(make.apply(new String[]{c9,
            "What does str(42) return?",
            "42 (int)", "Error", "42.0", "'42' (string)",
            "3",
            "str(42) returns the string '42'. str() converts any Python value to its string representation. str(None) returns 'None', str(True) returns 'True'. str() always succeeds — it never raises an error.",
            "EASY"}));

        all.add(make.apply(new String[]{c9,
            "What is the difference between int() truncating and round() rounding?",
            "There is no difference — both give the same result",
            "int(3.7) gives 3 (truncates); round(3.7) gives 4 (rounds to nearest)",
            "int(3.7) gives 4; round(3.7) gives 3",
            "int() rounds up; round() rounds down",
            "1",
            "int() truncates by removing the decimal: int(3.7) = 3, int(3.1) = 3. round() rounds to nearest: round(3.7) = 4, round(3.1) = 3. For storing prices and scores, use int() carefully — truncation loses data.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c9,
            "What does Number.isNaN() do in Python?",
            "It is a built-in Python function that checks for NaN",
            "Python does not have Number.isNaN — use math.isnan() to check for NaN",
            "It converts NaN to zero",
            "It raises ValueError if the value is NaN",
            "1",
            "Python does not have Number.isNaN() — that is JavaScript. In Python, use math.isnan(value) to check if a float is NaN. NaN (Not a Number) results from invalid float operations like float('inf') - float('inf').",
            "HARD"}));

        // ── Concept 10: is vs == ───────────────────────────────────────────────
        String c10 = "6a2533030fc2fb031334a418";
        all.add(make.apply(new String[]{c10,
            "What is the difference between == and is in Python?",
            "They are identical — both check value equality",
            "== checks if two values are equal; is checks if two variables point to the same object in memory",
            "is checks values; == checks memory addresses",
            "== is for numbers; is is for strings",
            "1",
            "== compares values (calls __eq__). is compares identity — whether two variables reference the exact same object in memory (same id()). Two lists can be equal (==) but different objects (is False).",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c10,
            "How should you always check if a variable is None?",
            "if variable == None:",
            "if variable != None:",
            "if variable is None:",
            "if variable === None:",
            "2",
            "Always use is None (or is not None) to check for None. None is a singleton — there is exactly one None object, so identity check is correct and more efficient. Linters will warn you if you use == None.",
            "EASY"}));

        all.add(make.apply(new String[]{c10,
            "What does id(x) return?",
            "The data type of x", "The value stored in x", "The memory address of the object x points to", "The variable name as a string",
            "2",
            "id(x) returns an integer representing the memory address where the object is stored. If id(a) == id(b), then a is b is True — they point to the same object.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c10,
            "list1 = [1,2,3]; list2 = [1,2,3]. What is the result of list1 == list2 and list1 is list2?",
            "True and True",
            "False and False",
            "True and False",
            "False and True",
            "2",
            "list1 == list2 is True — both lists have the same values. list1 is list2 is False — they are two different list objects in memory with different id() values. == checks content; is checks identity.",
            "HARD"}));

        all.add(make.apply(new String[]{c10,
            "Due to CPython's integer caching, a = 100; b = 100; then a is b may be True. Why?",
            "All integers are always the same object",
            "CPython caches small integers (typically -5 to 256) and reuses the same object",
            "100 is a special constant in Python",
            "is always returns True for numbers",
            "1",
            "CPython caches small integers (-5 to 256) for performance. a = 100 and b = 100 both point to the same cached integer object, so a is b is True. For large integers like 1000, new objects are created each time, so is would be False.",
            "HARD"}));

        all.add(make.apply(new String[]{c10,
            "What is the recommended way to compare with True or False?",
            "if x == True:",
            "if x is True: (for exact True check) or just if x: (for truthiness)",
            "if x === True:",
            "if x.equals(True):",
            "1",
            "For checking exact boolean identity use is True or is False. For checking truthiness (any truthy value), just use if x:. Avoid == True and == False — they can give unexpected results due to type coercion.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c10,
            "a = [1, 2, 3]; b = a. What does b is a return?",
            "False — b is a copy of a",
            "True — b and a point to the same list object",
            "Error",
            "Depends on the values",
            "1",
            "b = a does NOT create a copy — it creates an alias. Both b and a point to the same list object in memory. b is a is True. Modifying b also modifies a. Use b = a.copy() or b = a[:] to create an independent copy.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c10,
            "What does it mean when two objects have the same id()?",
            "They have the same value",
            "They are the same object in memory — one is an alias of the other",
            "They are of the same type",
            "They were created at the same time",
            "1",
            "id() returns the memory address. If id(a) == id(b), then a and b are the same object in memory — a is b will be True. This is identity equality.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c10,
            "Which comparison should you NEVER write according to Python best practices?",
            "x is None",
            "x is not None",
            "x == None",
            "x is True",
            "2",
            "x == None is discouraged. Python style guides say always use x is None. The reason: custom objects can override __eq__ and make obj == None return True even when obj is not None. is None always checks real object identity.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c10,
            "What does 'is' check that '==' does not?",
            "Whether two values are numerically equal",
            "Whether two values are of the same type",
            "Whether two variables are bound to the exact same object in memory (same id)",
            "Whether two strings have the same length",
            "2",
            "is checks object identity — whether two variables reference the exact same object in memory (same id()). == checks value equality — two different objects can have equal values. Use == for value comparison, is only for None/True/False.",
            "MEDIUM"}));

        // ── Concept 11: None ───────────────────────────────────────────────────
        String c11 = "6a2533030fc2fb031334a419";
        all.add(make.apply(new String[]{c11,
            "What is None in Python?",
            "The number zero",
            "An empty string",
            "A false boolean value",
            "The only value of NoneType, representing the absence of a value",
            "3",
            "None is the singleton value of the NoneType class. It represents the intentional absence of a value — not zero, not empty string, not False. There is exactly one None object in Python.",
            "EASY"}));

        all.add(make.apply(new String[]{c11,
            "What does a Python function return if it has no return statement?",
            "0", "False", "None", "An empty string",
            "2",
            "A function without a return statement automatically returns None. This is also true if the function has a return statement with no value: return by itself also returns None.",
            "EASY"}));

        all.add(make.apply(new String[]{c11,
            "Is None equal to False in Python?",
            "Yes — None == False is True",
            "No — None == False is False, but both are falsy",
            "Yes — they are the same object",
            "It depends on the Python version",
            "1",
            "None == False is False — they are different objects with different types (NoneType vs bool). However, both are falsy — they both evaluate to False in a boolean context like if statement. They are different but both cause an if block to be skipped.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c11,
            "How many None objects exist in Python?",
            "One per module", "One per program (it is a singleton)", "As many as needed", "One per variable assigned None",
            "1",
            "None is a singleton — there is exactly one None object in all of Python. Every variable assigned None points to this same single object. This is why id(None) is always the same value.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c11,
            "What is a common mistake developers make with list.sort()?",
            "sort() raises an error on empty lists",
            "sort() does not work on mixed types",
            "Storing the result of list.sort() in a variable — sort() returns None, not the sorted list",
            "sort() only works on numbers",
            "2",
            "list.sort() modifies the list in place and returns None. A common bug: sorted_list = my_list.sort() — sorted_list will be None! Use sorted(my_list) instead if you need a new sorted list returned.",
            "HARD"}));

        all.add(make.apply(new String[]{c11,
            "Which of the following is the correct way to check if a variable is None?",
            "if variable == None:", "if variable = None:", "if variable is None:", "if variable.isNone():",
            "2",
            "Always use is None to check for None. None is a singleton, so identity comparison is correct. if variable == None works but is bad practice and linters will warn you. if variable = None is a SyntaxError.",
            "EASY"}));

        all.add(make.apply(new String[]{c11,
            "What is the difference between None, 0 and '' (empty string) in Python?",
            "They are all equal to each other",
            "None means no value at all; 0 is the number zero; '' is empty text — all falsy but different",
            "None and 0 are the same; '' is different",
            "They are all the same — just different ways to write false",
            "1",
            "None, 0 and '' are three different falsy values representing different things. None = no value exists. 0 = the number zero. '' = empty text. None == 0 is False. None == '' is False. They are all falsy but distinct.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c11,
            "What is the type of None?",
            "bool", "int", "null", "NoneType",
            "3",
            "type(None) returns <class 'NoneType'>. None is the only instance of NoneType. There is no other way to create a NoneType value.",
            "EASY"}));

        all.add(make.apply(new String[]{c11,
            "How is None commonly used as a default parameter value?",
            "def func(items=[]) is preferred",
            "def func(items=None) and then check if items is None inside the function",
            "def func(items=0) and convert to list if needed",
            "Default parameters cannot be None",
            "1",
            "Using None as a default parameter (def func(items=None)) and then checking if items is None: items = [] inside the function is the correct pattern. Using def func(items=[]) is dangerous because the same list object is shared across all calls.",
            "HARD"}));

        all.add(make.apply(new String[]{c11,
            "What does bool(None) return?",
            "None", "True", "False", "Error",
            "2",
            "bool(None) returns False. None is falsy — it evaluates to False in boolean contexts like if statements, while conditions and boolean expressions. However, None is not False — None is not False evaluates to True.",
            "EASY"}));

        // ── Concept 12: Dynamically Typed Language ─────────────────────────────
        String c12 = "6a2533030fc2fb031334a41a";
        all.add(make.apply(new String[]{c12,
            "What does 'dynamically typed' mean in Python?",
            "Type checking is enforced at compile time",
            "Variables must be declared with their type before use",
            "Types are determined at runtime based on the value assigned, not at declaration time",
            "Python converts all types to strings automatically",
            "2",
            "Dynamic typing means type information is associated with VALUES at runtime, not with variable declarations. x = 5 makes x an int. x = 'hello' makes x a str. The same variable can hold different types. Type is determined when the code runs.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c12,
            "In Python, which of the following correctly reassigns a variable to a different type?",
            "Only possible with explicit casting",
            "x = 5; x = 'hello'  — x is first int, then str",
            "Not possible — Python prevents type changes",
            "Only possible inside functions",
            "1",
            "x = 5 creates an integer variable. x = 'hello' is perfectly valid and reassigns x to a string. This is dynamic typing — Python does not restrict what type a variable can hold.",
            "EASY"}));

        all.add(make.apply(new String[]{c12,
            "When does Python check for type errors in a dynamically typed program?",
            "During a compilation step before running",
            "When the source file is saved",
            "At runtime, when the specific line with the type error executes",
            "Never — Python ignores type errors",
            "2",
            "Python checks types at runtime. A type error only appears when the problematic line actually executes. This is different from statically typed languages (Java, C++) which catch type errors before the program runs.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c12,
            "What is 'duck typing' in Python?",
            "A way to convert objects to strings automatically",
            "Python caring about what methods an object has rather than its declared type",
            "A debugging technique for finding type errors",
            "A way to create type aliases",
            "1",
            "Duck typing: 'If it walks like a duck and quacks like a duck, it's a duck.' Python does not check the type of an object — it checks if the object has the required method or attribute. If it does, Python uses it.",
            "HARD"}));

        all.add(make.apply(new String[]{c12,
            "What are type hints in Python? (e.g. def greet(name: str) -> str)",
            "Mandatory declarations that Python enforces at runtime",
            "Documentation annotations that help IDEs and tools but are NOT enforced by Python at runtime",
            "A way to convert types automatically",
            "Python 2 syntax that was removed in Python 3",
            "1",
            "Type hints (PEP 484) are optional annotations. Python does not enforce them at runtime — def greet(name: str): can still be called with an integer and Python will not complain. They are for IDEs, linters (mypy) and documentation purposes.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c12,
            "In a statically typed language like Java, when are types checked?",
            "At runtime when the line executes",
            "Never — all languages are dynamically typed",
            "At compile time — before the program runs",
            "Only when explicitly requested by the developer",
            "2",
            "Statically typed languages check types at compile time. In Java, int x = 'hello' would fail to compile before the program ever runs. Python checks types at runtime, which means type errors only appear during execution.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c12,
            "What does type() return for a variable that was first assigned 5 and then reassigned to 'hello'?",
            "<class 'int'> always", "<class 'str'> because it was reassigned", "<class 'int or str'>", "TypeError",
            "1",
            "After x = 5 then x = 'hello', type(x) returns <class 'str'>. type() always returns the CURRENT type of the object the variable points to. The variable now points to a string, so the type is str.",
            "MEDIUM"}));

        all.add(make.apply(new String[]{c12,
            "Which statement about dynamically typed Python is correct?",
            "Python variables have types; their types cannot change",
            "Python values have types; variables are just labels pointing to values",
            "Python has no type system at all",
            "Python variables are all strings internally",
            "1",
            "In Python, types belong to VALUES not variables. A variable is just a name (label) pointing to an object. The object has a type. When you reassign x = 'hello', the label x now points to a string object.",
            "HARD"}));

        all.add(make.apply(new String[]{c12,
            "Can you enforce type hints at runtime in Python?",
            "Yes — Python enforces type hints automatically",
            "No — but third-party libraries like Pydantic can enforce types at runtime",
            "Yes — using the strict keyword",
            "No — type hints are completely useless",
            "1",
            "Python itself does not enforce type hints at runtime. However, third-party libraries like Pydantic use type hints to validate data at runtime. For static analysis without running code, mypy is used.",
            "HARD"}));

        all.add(make.apply(new String[]{c12,
            "Which of the following is an advantage of dynamic typing?",
            "Type errors are always caught before running",
            "Programs are always faster than statically typed equivalents",
            "Code is shorter and faster to write — no type declarations needed",
            "Dynamic typing prevents all type-related bugs",
            "2",
            "Dynamic typing means no type declarations, shorter code and faster development. You write x = 5 instead of int x = 5. The trade-off is that type errors only appear at runtime and type hints are optional (though recommended).",
            "MEDIUM"}));

        questionRepository.saveAll(all);
        System.out.println("✅ Python Fundamentals concepts 2-12 questions seeded — " + all.size());
    }

    // ─── PYTHON FUNDAMENTALS — CONCEPT 1 QUESTIONS ───────────────────────────
    private void seedPythonFundamentalsC1Questions() {
        final String conceptId = "6a2533030fc2fb031334a40f";
        final String subjectId = "6a2533020fc2fb031334a40e";

        List<Question> questions = new java.util.ArrayList<>();

        // Q1 — correct index 2
        Question q1 = new Question();
        q1.setConceptId(conceptId); q1.setSubjectId(subjectId);
        q1.setText("Which of the following best describes Python?");
        q1.setOptions(List.of(
            "A low-level compiled language that requires manual memory management",
            "A statically typed language that runs only on Windows",
            "A high-level, interpreted, dynamically-typed general-purpose programming language",
            "A markup language used to structure web pages"
        ));
        q1.setCorrectIndex(2);
        q1.setExplanation("Python is high-level (hides low-level details), interpreted (runs line by line), and dynamically-typed (types resolved at runtime). It is general-purpose — used in web development, data science, AI, automation and more.");
        q1.setDifficulty("EASY");
        questions.add(q1);

        // Q2 — correct index 0
        Question q2 = new Question();
        q2.setConceptId(conceptId); q2.setSubjectId(subjectId);
        q2.setText("Guido van Rossum designed Python with the goal that code should be easy to read and close to plain English. Which feature directly reflects this design philosophy?");
        q2.setOptions(List.of(
            "Python uses indentation to define code blocks instead of curly braces",
            "Python requires a semicolon at the end of every line",
            "Python must be compiled before it can run",
            "Python variables must have their type declared before use"
        ));
        q2.setCorrectIndex(0);
        q2.setExplanation("Python enforces indentation to define code structure, which makes the visual layout match the logical structure. This makes Python code significantly more readable than languages that use curly braces.");
        q2.setDifficulty("EASY");
        questions.add(q2);

        // Q3 — correct index 3
        Question q3 = new Question();
        q3.setConceptId(conceptId); q3.setSubjectId(subjectId);
        q3.setText("Who created Python and when was it first released?");
        q3.setOptions(List.of(
            "James Gosling in 1995",
            "Brendan Eich in 1995",
            "Linus Torvalds in 1991",
            "Guido van Rossum in 1991"
        ));
        q3.setCorrectIndex(3);
        q3.setExplanation("Python was created by Guido van Rossum and first released in 1991. James Gosling created Java, Brendan Eich created JavaScript, and Linus Torvalds created the Linux kernel.");
        q3.setDifficulty("EASY");
        questions.add(q3);

        // Q4 — correct index 1
        Question q4 = new Question();
        q4.setConceptId(conceptId); q4.setSubjectId(subjectId);
        q4.setText("What file extension is used for Python source files?");
        q4.setOptions(List.of(
            ".java",
            ".py",
            ".js",
            ".cpp"
        ));
        q4.setCorrectIndex(1);
        q4.setExplanation("Python source files use the .py extension. For example, a script named hello.py is run with: python hello.py. The .py extension tells the operating system and editors that the file contains Python code.");
        q4.setDifficulty("EASY");
        questions.add(q4);

        // Q5 — correct index 3
        Question q5 = new Question();
        q5.setConceptId(conceptId); q5.setSubjectId(subjectId);
        q5.setText("What does it mean that Python is an interpreted language?");
        q5.setOptions(List.of(
            "Python translates the entire program to machine code before running it",
            "Python requires a special browser plugin to execute",
            "Python converts source code to assembly language during installation",
            "Python executes code line by line without a separate compilation step by the developer"
        ));
        q5.setCorrectIndex(3);
        q5.setExplanation("Python runs code line by line using an interpreter. Unlike compiled languages (C, Java), the developer does not need to run a separate compile command — Python reads and executes the source file directly. Internally CPython does compile to bytecode, but this happens automatically.");
        q5.setDifficulty("MEDIUM");
        questions.add(q5);

        // Q6 — correct index 2
        Question q6 = new Question();
        q6.setConceptId(conceptId); q6.setSubjectId(subjectId);
        q6.setText("Which of the following is NOT a common use case for Python?");
        q6.setOptions(List.of(
            "Machine learning and artificial intelligence",
            "Web development and backend APIs",
            "Designing physical hardware circuits",
            "Automation and scripting"
        ));
        q6.setCorrectIndex(2);
        q6.setExplanation("Python is used for web development, data science, AI/ML, automation, scripting, and DevOps. Designing physical hardware circuits requires hardware description languages like VHDL or Verilog — Python is a software language.");
        q6.setDifficulty("MEDIUM");
        questions.add(q6);

        // Q7 — correct index 0
        Question q7 = new Question();
        q7.setConceptId(conceptId); q7.setSubjectId(subjectId);
        q7.setText("What does 'dynamically typed' mean in Python?");
        q7.setOptions(List.of(
            "Python determines the type of a variable at runtime based on the value assigned to it",
            "Python requires you to declare the type of every variable before using it",
            "Python variables can only store one type of value for their entire lifetime",
            "Python automatically converts all values to strings"
        ));
        q7.setCorrectIndex(0);
        q7.setExplanation("In Python, you just write x = 5 and Python figures out that x is an integer at runtime. You never write int x = 5. This is dynamic typing — types are associated with values, not variable names, and they are resolved when the code runs.");
        q7.setDifficulty("MEDIUM");
        questions.add(q7);

        // Q8 — correct index 1
        Question q8 = new Question();
        q8.setConceptId(conceptId); q8.setSubjectId(subjectId);
        q8.setText("Python is often described as having a 'batteries included' philosophy. What does this mean?");
        q8.setOptions(List.of(
            "Python ships with its own hardware and does not need a computer",
            "Python includes a large standard library that covers common tasks like file handling, math, networking and JSON without installing extra packages",
            "Python automatically installs all third-party packages when you start coding",
            "Python's performance is so good that it runs without an operating system"
        ));
        q8.setCorrectIndex(1);
        q8.setExplanation("'Batteries included' means Python's standard library provides modules for almost everything you need — os, json, math, datetime, re, urllib and more. You can accomplish many tasks without pip-installing anything extra.");
        q8.setDifficulty("MEDIUM");
        questions.add(q8);

        // Q9 — correct index 3
        Question q9 = new Question();
        q9.setConceptId(conceptId); q9.setSubjectId(subjectId);
        q9.setText("Which statement about Python's real-world adoption is correct?");
        q9.setOptions(List.of(
            "Python is only suitable for beginners and is not used in production systems",
            "Python is exclusively used for academic research and teaching",
            "Python runs only on Linux and is not cross-platform",
            "Python is used in production by major organisations including Google, NASA and Instagram"
        ));
        q9.setCorrectIndex(3);
        q9.setExplanation("Python is widely used in production at scale. Google uses Python extensively, Instagram's backend runs on Django (a Python framework), and NASA uses Python for scientific computing. Python is cross-platform and runs on Windows, macOS and Linux.");
        q9.setDifficulty("MEDIUM");
        questions.add(q9);

        // Q10 — correct index 1
        Question q10 = new Question();
        q10.setConceptId(conceptId); q10.setSubjectId(subjectId);
        q10.setText("You have written a Python script and saved it as app.py. Which command runs it from the terminal?");
        q10.setOptions(List.of(
            "run app.py",
            "python app.py",
            "execute app.py",
            "start app.py"
        ));
        q10.setCorrectIndex(1);
        q10.setExplanation("The correct command is: python app.py (or python3 app.py on some systems). The python command invokes the Python interpreter which then reads and executes your .py file. The other options (run, execute, start) are not valid Python commands.");
        q10.setDifficulty("EASY");
        questions.add(q10);

        questionRepository.saveAll(questions);
        System.out.println("✅ Python Fundamentals C1 questions seeded — " + questions.size());
    }

    // ─── PYTHON BASICS ────────────────────────────────────────────────────────
    private void seedPythonBasics() {
        Subject py = subjectRepository.save(sub(
            "Python Basics",
            "Master operators, data structures, control flow, functions and exception handling — everything you need before learning OOP",
            "🔰", "#22C55E", "C"
        ));
        py.setOverview("Python Basics covers the core building blocks that every Python developer uses every day. You will learn how to work with operators, store data in lists, tuples, dictionaries and sets, control your program's flow using conditions and loops, write reusable functions, and handle errors gracefully. These concepts are required before moving to Object Oriented Programming.");
        py.setWhyLearn("Every Python interview, project, and job task requires these fundamentals. Whether you are building a web API, automating tasks, or writing data scripts, you will use operators, data structures, loops, functions, and exception handling in every program you write.");
        py.setForWho("Students who have completed Python Fundamentals and understand variables, data types, and basic syntax. This subject builds directly on that knowledge.");
        py.setPrerequisites(List.of("Python Fundamentals subject completed", "Python 3 installed", "VS Code or any editor"));
        py.setOutcomes(List.of(
            "Use all Python operators correctly including arithmetic, comparison, logical and assignment",
            "Store and manipulate data using lists, tuples, dictionaries and sets",
            "Write conditional logic using if, elif and else",
            "Loop through data using for and while loops",
            "Write reusable functions with parameters and return values",
            "Handle runtime errors using try, except and finally"
        ));
        py.setWhatYouWillBuild(List.of(
            "A student grade calculator using operators and conditionals",
            "A contact book using dictionaries and lists",
            "A number guessing game using loops and functions"
        ));
        py.setToolsRequired(List.of("Python 3.x", "VS Code with Python extension", "Terminal"));
        py.setDifficulty("Beginner");
        py.setEstimatedHours(10);
        py.setCareerUse("These concepts appear in every Python job role — backend developer, data analyst, automation engineer, ML engineer. Every technical interview will test loops, functions, and data structures.");
        subjectRepository.save(py);

        List<Concept> concepts = List.of(

            // ── 1. Operators ──────────────────────────────────────────────────
            conceptRich(py, "Operators",
                "Operators are symbols that tell Python to perform arithmetic, comparison, logical, or assignment operations on values.",
                "Operators are the tools Python uses to work with values.\n\nThink of them like the buttons on a calculator — each button does a specific job. The + button adds, the > button checks which number is bigger, and so on.\n\nPython has four main groups of operators:\n- Arithmetic operators do maths: +, -, *, /, //, %, **\n- Comparison operators compare values and return True or False: ==, !=, <, >, <=, >=\n- Logical operators combine conditions: and, or, not\n- Assignment operators store values into variables: =, +=, -=, *=, /=\n\nYou will use these in every program you write.",
                "Python operators are divided into the following categories:\n\n1. Arithmetic operators:\n- + addition, - subtraction, * multiplication\n- / true division (always returns float)\n- // floor division (returns integer, rounds down)\n- % modulo (returns remainder)\n- ** exponentiation (power)\n\n2. Comparison operators:\n- == equal to, != not equal to\n- < less than, > greater than\n- <= less than or equal, >= greater than or equal\n- All comparison operators return a boolean: True or False\n\n3. Logical operators:\n- and: True only if both conditions are True\n- or: True if at least one condition is True\n- not: reverses the boolean value\n\n4. Assignment operators:\n- = assigns a value\n- += adds and assigns: x += 5 is same as x = x + 5\n- -=, *=, /=, //=, %=, **= work the same way\n\n5. Identity and membership operators:\n- is / is not: checks if two variables point to same object\n- in / not in: checks if a value exists in a sequence",
                "# Arithmetic operators\nprint(10 + 3)   # 13\nprint(10 - 3)   # 7\nprint(10 * 3)   # 30\nprint(10 / 3)   # 3.3333... (always float)\nprint(10 // 3)  # 3 (floor division)\nprint(10 % 3)   # 1 (remainder)\nprint(2 ** 8)   # 256 (power)\n\n# Comparison operators\nprint(5 == 5)   # True\nprint(5 != 3)   # True\nprint(10 > 3)   # True\nprint(2 >= 2)   # True\n\n# Logical operators\nprint(True and False)  # False\nprint(True or False)   # True\nprint(not True)        # False\n\n# Assignment operators\nscore = 100\nscore += 50    # score is now 150\nscore -= 20    # score is now 130\nscore *= 2     # score is now 260\nprint(score)\n\n# Membership operator\nfruits = ['apple', 'mango', 'banana']\nprint('mango' in fruits)      # True\nprint('grape' not in fruits)  # True",
                List.of(
                    new Concept.ConceptExample("Calculate student grade",
                        "Use arithmetic and comparison operators to calculate and check a student's grade.",
                        "marks = 85\ntotal = 100\n\npercentage = (marks / total) * 100\nprint('Percentage:', percentage)\n\nif percentage >= 90:\n    print('Grade: A')\nelif percentage >= 75:\n    print('Grade: B')\nelif percentage >= 60:\n    print('Grade: C')\nelse:\n    print('Grade: F')",
                        "Percentage: 85.0\nGrade: B"),
                    new Concept.ConceptExample("Floor division and modulo in real use",
                        "Use // and % to solve practical problems like splitting groups or checking even/odd.",
                        "students = 23\ngroups_of = 5\n\nfull_groups = students // groups_of\nleftover = students % groups_of\n\nprint('Full groups:', full_groups)   # 4\nprint('Leftover students:', leftover) # 3\n\n# Check even or odd\nnumber = 17\nif number % 2 == 0:\n    print(number, 'is even')\nelse:\n    print(number, 'is odd')",
                        "Full groups: 4\nLeftover students: 3\n17 is odd"),
                    new Concept.ConceptExample("Logical operators in login check",
                        "Combine multiple conditions using and, or, not.",
                        "username = 'ravi'\npassword = 'pass123'\nis_active = True\n\nif username == 'ravi' and password == 'pass123' and is_active:\n    print('Login successful')\nelse:\n    print('Login failed')\n\n# or operator\nday = 'Sunday'\nif day == 'Saturday' or day == 'Sunday':\n    print('Weekend — no class')\nelse:\n    print('Weekday — attend class')",
                        "Login successful\nWeekend — no class"),
                    new Concept.ConceptExample("Assignment shorthand operators",
                        "Use +=, -=, *=, /= to update variables without repeating the variable name.",
                        "xp = 0\nprint('Start XP:', xp)\n\nxp += 100   # completed a concept\nxp += 50    # daily bonus\nxp -= 10    # penalty\nxp *= 2     # double XP event\n\nprint('Final XP:', xp)",
                        "Start XP: 0\nFinal XP: 280")
                ),
                List.of(
                    "/ always returns a float in Python 3, even if dividing two integers: 10 / 2 gives 5.0 not 5",
                    "// floor division rounds down toward negative infinity: -7 // 2 gives -4, not -3",
                    "% modulo returns the remainder and is very useful for checking even/odd and cycling through values",
                    "== checks equality of values, is checks if both variables point to the same object in memory",
                    "and returns the first falsy value or the last value if all are truthy",
                    "or returns the first truthy value or the last value if all are falsy",
                    "in and not in work on strings, lists, tuples, sets and dictionaries"
                ),
                "Use // and % together whenever you need to split things into groups. Floor division gives you the number of complete groups, modulo gives you what is left over. This pattern appears constantly in real programs.",
                List.of(
                    "Using = instead of == in a condition: if x = 5 is a SyntaxError in Python",
                    "Expecting 10 / 2 to give 5 (int) — in Python 3 it gives 5.0 (float). Use // if you need an integer",
                    "Confusing and with or — and requires ALL conditions to be True, or requires only ONE",
                    "Using is instead of == to compare values: 'hello' is 'hello' may give unexpected results for large objects"
                ),
                15, 1, "D"),

            // ── 2. Lists and Tuples ───────────────────────────────────────────
            conceptRich(py, "Lists and Tuples",
                "Lists and tuples are ordered collections that store multiple values in a single variable. Lists are mutable, tuples are immutable.",
                "Imagine you have a notebook where you write down your to-do tasks. You can add new tasks, remove finished ones, and change tasks. That is a list — it can be changed after creation.\n\nNow imagine a printed train ticket with fixed details — departure city, arrival city, train number. Those details cannot be changed. That is a tuple.\n\nBoth lists and tuples store multiple values in order, and you access each value using its position number starting from 0.\n\nLists are written with square brackets: [1, 2, 3]\nTuples are written with round brackets: (1, 2, 3)\n\nThe key difference: lists can be modified after creation, tuples cannot.",
                "1. List:\n- Defined with square brackets: my_list = [1, 2, 3]\n- Ordered: items maintain their position\n- Mutable: you can add, remove, and change items after creation\n- Allows duplicate values\n- Common methods: append(), insert(), remove(), pop(), sort(), reverse(), len()\n\n2. Tuple:\n- Defined with round brackets: my_tuple = (1, 2, 3)\n- Ordered: items maintain their position\n- Immutable: once created, items cannot be changed\n- Allows duplicate values\n- Used for fixed data: coordinates, RGB colours, database records\n- Slightly faster than lists and uses less memory\n\n3. Indexing and negative indexing:\n- First element: list[0]\n- Last element: list[-1]\n- Second last: list[-2]\n\n4. When to use which:\n- Use list when data needs to change: shopping cart, student marks\n- Use tuple when data should stay fixed: days of week, GPS coordinates",
                "# Creating lists and tuples\nstudents = ['Ravi', 'Priya', 'Arjun', 'Sneha']\ncoordinates = (17.38, 78.48)\n\n# Accessing elements by index\nprint(students[0])    # Ravi (first)\nprint(students[-1])   # Sneha (last)\nprint(coordinates[0]) # 17.38\n\n# List is mutable\nstudents.append('Kiran')        # add to end\nstudents.insert(1, 'Divya')     # add at position 1\nstudents.remove('Arjun')        # remove by value\npopped = students.pop()         # remove and return last item\nprint(students)\n\n# List methods\nmarks = [85, 92, 78, 95, 88]\nmarks.sort()\nprint(marks)          # [78, 85, 88, 92, 95]\nprint(len(marks))     # 5\nprint(max(marks))     # 95\nprint(min(marks))     # 78\nprint(sum(marks))     # 438\n\n# Tuple is immutable\ndays = ('Mon', 'Tue', 'Wed', 'Thu', 'Fri')\nprint(days[2])        # Wed\n# days[0] = 'Sunday'  # TypeError: tuple does not support assignment",
                List.of(
                    new Concept.ConceptExample("Student marks using a list",
                        "Store, modify and calculate marks using a list.",
                        "marks = [72, 85, 90, 68, 95]\n\nprint('All marks:', marks)\nprint('Total students:', len(marks))\nprint('Highest mark:', max(marks))\nprint('Lowest mark:', min(marks))\nprint('Average:', sum(marks) / len(marks))\n\nmarks.append(88)     # new student result added\nmarks.sort(reverse=True)\nprint('Sorted (high to low):', marks)",
                        "All marks: [72, 85, 90, 68, 95]\nTotal students: 5\nHighest mark: 95\nLowest mark: 68\nAverage: 82.0\nSorted (high to low): [95, 90, 88, 85, 72, 68]"),
                    new Concept.ConceptExample("Looping through a list",
                        "Use a for loop to process each item in a list.",
                        "fruits = ['apple', 'mango', 'banana', 'orange']\n\nfor fruit in fruits:\n    print('Fruit:', fruit)\n\n# Loop with index using enumerate\nfor index, fruit in enumerate(fruits):\n    print(index, '-', fruit)",
                        "Fruit: apple\nFruit: mango\nFruit: banana\nFruit: orange\n0 - apple\n1 - mango\n2 - banana\n3 - orange"),
                    new Concept.ConceptExample("Tuple for fixed data",
                        "Use tuples when data should not change — like student record fields.",
                        "# Student record as a tuple (should not change)\nstudent = ('Ravi Kumar', 'CSE', 2021, 'A')\n\nname, branch, year, grade = student  # tuple unpacking\nprint('Name:', name)\nprint('Branch:', branch)\nprint('Year:', year)\nprint('Grade:', grade)",
                        "Name: Ravi Kumar\nBranch: CSE\nYear: 2021\nGrade: A"),
                    new Concept.ConceptExample("List of lists — 2D data",
                        "Store table-like data using a list of lists.",
                        "# Each inner list is one student's marks\nclassroom = [\n    ['Ravi', 85, 90, 78],\n    ['Priya', 92, 88, 95],\n    ['Arjun', 70, 75, 68]\n]\n\nfor student in classroom:\n    name = student[0]\n    avg = sum(student[1:]) / len(student[1:])\n    print(f'{name}: average = {avg:.1f}')",
                        "Ravi: average = 84.3\nPriya: average = 91.7\nArjun: average = 71.0")
                ),
                List.of(
                    "List index starts at 0: first element is list[0], second is list[1]",
                    "Negative index counts from the end: list[-1] is the last element",
                    "append() adds to the end, insert(index, value) adds at a specific position",
                    "remove() removes by value, pop() removes by index (default last)",
                    "Tuples are immutable — trying to change a tuple element raises TypeError",
                    "Tuple unpacking lets you assign multiple variables at once: a, b, c = (1, 2, 3)",
                    "Use list when data changes over time, use tuple when data is fixed"
                ),
                "Use enumerate() whenever you need both the index and the value in a loop. Writing for i in range(len(list)) and then list[i] is a common beginner habit but enumerate(list) is cleaner and more Pythonic.",
                List.of(
                    "Using index 1 for the first element instead of index 0",
                    "Trying to modify a tuple after creation — this raises a TypeError",
                    "Confusing append() which adds one item with extend() which adds multiple items",
                    "Storing the result of sort() in a variable — list.sort() modifies in place and returns None. Use sorted(list) if you want a new sorted list."
                ),
                20, 2, "D"),

            // ── 3. Dictionaries ───────────────────────────────────────────────
            conceptRich(py, "Dictionaries",
                "A dictionary stores data as key-value pairs, where each key is unique and maps to a specific value.",
                "A dictionary in Python works exactly like a real dictionary — you look up a word (key) and get its meaning (value).\n\nFor example, a student profile:\n- 'name' → 'Ravi Kumar'\n- 'age' → 21\n- 'course' → 'Python'\n\nInstead of remembering position numbers like in a list, you use meaningful keys to access data.\n\nDictionaries are perfect for storing structured data like user profiles, product details, or configuration settings.\n\nKeys must be unique and immutable (strings, numbers, or tuples). Values can be anything.",
                "1. Creating a dictionary:\n- Use curly braces: student = {'name': 'Ravi', 'age': 21}\n- Or using dict(): student = dict(name='Ravi', age=21)\n\n2. Accessing values:\n- student['name'] returns 'Ravi'\n- student.get('name') returns 'Ravi', but returns None if key missing instead of raising error\n\n3. Modifying dictionaries:\n- Add or update: student['email'] = 'ravi@email.com'\n- Delete: del student['age'] or student.pop('age')\n\n4. Useful dictionary methods:\n- keys(): returns all keys\n- values(): returns all values\n- items(): returns all key-value pairs\n- update(): merges another dictionary\n- get(key, default): returns value or default if key missing\n\n5. Iterating:\n- for key in student: loops through keys\n- for key, value in student.items(): loops through key-value pairs\n\n6. From Python 3.7+, dictionaries maintain insertion order.",
                "# Creating a dictionary\nstudent = {\n    'name': 'Ravi Kumar',\n    'age': 21,\n    'course': 'Python',\n    'marks': 85\n}\n\n# Accessing values\nprint(student['name'])           # Ravi Kumar\nprint(student.get('age'))        # 21\nprint(student.get('email', 'N/A'))  # N/A (default)\n\n# Adding and updating\nstudent['email'] = 'ravi@gmail.com'\nstudent['marks'] = 90\n\n# Deleting\ndel student['age']\n\n# Looping through a dictionary\nfor key, value in student.items():\n    print(key, ':', value)\n\n# Dictionary methods\nprint(list(student.keys()))\nprint(list(student.values()))\nprint(len(student))\n\n# Check if key exists\nif 'name' in student:\n    print('Name exists')",
                List.of(
                    new Concept.ConceptExample("Student profile dictionary",
                        "Create and access a student profile stored in a dictionary.",
                        "student = {\n    'name': 'Priya Sharma',\n    'roll_no': 'CS2021042',\n    'branch': 'CSE',\n    'cgpa': 8.7\n}\n\nprint('Name:', student['name'])\nprint('Roll No:', student['roll_no'])\nprint('CGPA:', student['cgpa'])\n\nstudent['cgpa'] = 9.1\nprint('Updated CGPA:', student['cgpa'])",
                        "Name: Priya Sharma\nRoll No: CS2021042\nCGPA: 8.7\nUpdated CGPA: 9.1"),
                    new Concept.ConceptExample("Counting word frequency",
                        "Use a dictionary to count how many times each word appears.",
                        "sentence = 'python is great python is easy python is popular'\nwords = sentence.split()\n\ncount = {}\nfor word in words:\n    if word in count:\n        count[word] += 1\n    else:\n        count[word] = 1\n\nfor word, freq in count.items():\n    print(word, '->', freq)",
                        "python -> 3\nis -> 3\ngreat -> 1\neasy -> 1\npopular -> 1"),
                    new Concept.ConceptExample("Nested dictionary",
                        "Store complex structured data using dictionaries inside dictionaries.",
                        "company = {\n    'name': 'TechCorp',\n    'employees': {\n        'developer': 50,\n        'designer': 20,\n        'manager': 10\n    },\n    'location': 'Hyderabad'\n}\n\nprint(company['name'])\nprint(company['employees']['developer'])\nprint('Total employees:', sum(company['employees'].values()))",
                        "TechCorp\n50\nTotal employees: 80")
                ),
                List.of(
                    "Keys must be unique — assigning the same key twice overwrites the first value",
                    "Use get() instead of [] when a key might not exist — [] raises KeyError, get() returns None",
                    "dict.items() returns key-value pairs — use this in for loops to get both at once",
                    "Dictionaries are ordered by insertion from Python 3.7 onwards",
                    "Keys must be immutable: strings, numbers, or tuples can be keys; lists cannot"
                ),
                "Use get() with a default value instead of [] when reading from a dictionary with uncertain keys. Writing student.get('email', 'Not provided') is safer than student['email'] which crashes with KeyError if the key is missing.",
                List.of(
                    "Using [] to access a key that does not exist — always raises KeyError. Use get() for safe access",
                    "Trying to use a list as a dictionary key — lists are mutable so they cannot be keys",
                    "Forgetting that dict.keys(), dict.values(), dict.items() return view objects, not lists — wrap with list() if needed"
                ),
                20, 3, "D"),

            // ── 4. Sets ───────────────────────────────────────────────────────
            conceptRich(py, "Sets",
                "A set is an unordered collection of unique values. Duplicates are automatically removed and sets support mathematical operations like union, intersection and difference.",
                "Imagine you have a list of students who attended class on Monday, and another list for Tuesday. You want to know who attended both days, or who attended on either day, or who attended Monday but not Tuesday.\n\nThis is exactly what sets are for.\n\nA set automatically removes duplicates and lets you perform operations like:\n- Union: all unique values from both sets\n- Intersection: values that appear in both sets\n- Difference: values in one set but not the other\n\nSets are unordered — unlike lists, you cannot access elements by index.",
                "1. Creating a set:\n- Use curly braces with no key-value pairs: {1, 2, 3}\n- Or use set(): set([1, 2, 2, 3]) gives {1, 2, 3}\n- Empty set must use set(), not {} (that creates an empty dict)\n\n2. Key properties:\n- Unordered: no guaranteed order of elements\n- No duplicates: duplicate values are silently removed\n- Mutable: you can add and remove elements\n- Elements must be immutable (no lists inside a set)\n\n3. Common methods:\n- add(x): adds one element\n- remove(x): removes element, raises error if missing\n- discard(x): removes element, no error if missing\n- pop(): removes and returns a random element\n- clear(): empties the set\n\n4. Set operations:\n- union: A | B or A.union(B)\n- intersection: A & B or A.intersection(B)\n- difference: A - B or A.difference(B)\n- symmetric difference: A ^ B (in either but not both)",
                "# Creating sets\ncolors = {'red', 'blue', 'green', 'red', 'blue'}\nprint(colors)  # {'red', 'blue', 'green'} — duplicates removed\n\n# Adding and removing\ncolors.add('yellow')\ncolors.discard('blue')\nprint(colors)\n\n# Check membership\nprint('red' in colors)   # True\nprint('pink' in colors)  # False\n\n# Set operations\nmonday = {'Ravi', 'Priya', 'Arjun', 'Sneha'}\ntuesday = {'Priya', 'Sneha', 'Kiran', 'Divya'}\n\nprint('Both days:', monday & tuesday)       # intersection\nprint('Either day:', monday | tuesday)      # union\nprint('Only Monday:', monday - tuesday)     # difference\nprint('Not both:', monday ^ tuesday)        # symmetric difference\n\n# Remove duplicates from a list using set\nnumbers = [1, 2, 2, 3, 4, 4, 5, 1]\nunique = list(set(numbers))\nprint(unique)",
                List.of(
                    new Concept.ConceptExample("Remove duplicates from a list",
                        "The fastest way to remove duplicates is to convert to a set and back to a list.",
                        "emails = [\n    'ravi@gmail.com',\n    'priya@gmail.com',\n    'ravi@gmail.com',\n    'arjun@gmail.com',\n    'priya@gmail.com'\n]\n\nunique_emails = list(set(emails))\nprint('Original count:', len(emails))\nprint('Unique count:', len(unique_emails))\nprint(unique_emails)",
                        "Original count: 5\nUnique count: 3\n['arjun@gmail.com', 'priya@gmail.com', 'ravi@gmail.com']"),
                    new Concept.ConceptExample("Find common and unique elements",
                        "Use set operations to compare two groups of data.",
                        "python_students = {'Ravi', 'Priya', 'Arjun', 'Sneha'}\njava_students = {'Priya', 'Sneha', 'Kiran', 'Divya'}\n\nboth = python_students & java_students\nonly_python = python_students - java_students\neither = python_students | java_students\n\nprint('Enrolled in both:', both)\nprint('Only Python:', only_python)\nprint('Total unique students:', either)",
                        "Enrolled in both: {'Priya', 'Sneha'}\nOnly Python: {'Ravi', 'Arjun'}\nTotal unique students: {'Ravi', 'Priya', 'Arjun', 'Sneha', 'Kiran', 'Divya'}")
                ),
                List.of(
                    "Sets automatically remove duplicates — adding the same value twice has no effect",
                    "Sets are unordered — you cannot access elements with an index",
                    "Use discard() instead of remove() when you are not sure if the element exists",
                    "To create an empty set use set(), not {} which creates an empty dictionary",
                    "Set operations: | union, & intersection, - difference, ^ symmetric difference"
                ),
                "The most practical use of sets in real projects is removing duplicates. If you have a list with repeated values and want only unique ones, convert to set and back: list(set(my_list)). This is faster than any loop-based approach.",
                List.of(
                    "Using {} to create an empty set — this creates an empty dictionary. Use set() instead",
                    "Trying to access set elements by index — sets are unordered and have no index",
                    "Using remove() on an element that does not exist — use discard() to avoid KeyError"
                ),
                15, 4, "D"),

            // ── 5. String Methods and Formatting ──────────────────────────────
            conceptRich(py, "String Methods and Formatting",
                "Python strings have built-in methods to search, modify, split, and format text. f-strings are the modern way to insert variables into strings.",
                "A string in Python is not just text — it is a powerful object with many built-in tools.\n\nThink of string methods as operations you perform on text:\n- Change case: upper(), lower()\n- Remove spaces: strip()\n- Split into parts: split()\n- Join parts together: join()\n- Search inside: find(), count(), startswith(), endswith()\n- Replace text: replace()\n\nFor combining strings with variables, Python provides f-strings — the cleanest and fastest way to format text.\n\nInstead of: 'Hello ' + name + ', you are ' + str(age) + ' years old'\nYou write: f'Hello {name}, you are {age} years old'",
                "1. Case methods:\n- upper(): converts to UPPERCASE\n- lower(): converts to lowercase\n- title(): Capitalizes First Letter Of Each Word\n- capitalize(): Capitalizes only first letter\n\n2. Whitespace methods:\n- strip(): removes spaces from both ends\n- lstrip(): removes from left only\n- rstrip(): removes from right only\n\n3. Search methods:\n- find(sub): returns index of first occurrence, -1 if not found\n- count(sub): counts how many times substring appears\n- startswith(prefix): returns True/False\n- endswith(suffix): returns True/False\n- in operator: 'hello' in text\n\n4. Modify methods:\n- replace(old, new): replaces all occurrences\n- split(sep): splits string into a list\n- join(list): joins a list into a string\n\n5. String formatting:\n- f-string: f'Name: {name}, Age: {age}'\n- Format spec: f'{value:.2f}' for 2 decimal places\n- f'{value:>10}' right align in 10 chars\n\n6. String is immutable — all methods return a new string",
                "name = '  Ravi Kumar  '\n\n# Case\nprint(name.strip().upper())    # RAVI KUMAR\nprint(name.strip().lower())    # ravi kumar\nprint(name.strip().title())    # Ravi Kumar\n\n# Search\ntext = 'Python is easy and Python is powerful'\nprint(text.find('Python'))      # 0\nprint(text.count('Python'))     # 2\nprint(text.startswith('Python'))# True\nprint(text.endswith('ful'))     # True\nprint('easy' in text)           # True\n\n# Replace and split\nprint(text.replace('Python', 'Java'))\nwords = text.split(' ')\nprint(words)\nprint(', '.join(words[:3]))\n\n# f-strings\nstudent = 'Priya'\nmarks = 92.5\nprint(f'Student: {student}')\nprint(f'Marks: {marks:.1f}')    # 92.5\nprint(f'Pass: {marks >= 40}')   # Pass: True",
                List.of(
                    new Concept.ConceptExample("Clean and validate user input",
                        "Use strip() and lower() to normalize input from users.",
                        "raw_input = '  Ravi Kumar  '\ncleaned = raw_input.strip()\nnormalized = cleaned.lower()\n\nprint('Original:', repr(raw_input))\nprint('Cleaned:', repr(cleaned))\nprint('Normalized:', repr(normalized))\n\n# Useful when comparing user input\nuser_answer = '  YES  '\nif user_answer.strip().lower() == 'yes':\n    print('User confirmed')",
                        "Original: '  Ravi Kumar  '\nCleaned: 'Ravi Kumar'\nNormalized: 'ravi kumar'\nUser confirmed"),
                    new Concept.ConceptExample("Parse CSV-like data using split and join",
                        "Split a string into parts and join them back in a different format.",
                        "record = 'Ravi,CSE,2021,85.5'\nparts = record.split(',')\n\nname = parts[0]\nbranch = parts[1]\nyear = parts[2]\ncgpa = float(parts[3])\n\nprint(f'Name: {name}')\nprint(f'Branch: {branch}')\nprint(f'Year: {year}')\nprint(f'CGPA: {cgpa:.1f}')\n\n# Rejoin with different separator\nformatted = ' | '.join(parts)\nprint(formatted)",
                        "Name: Ravi\nBranch: CSE\nYear: 2021\nCGPA: 85.5\nRavi | CSE | 2021 | 85.5"),
                    new Concept.ConceptExample("Format numbers in output using f-strings",
                        "Control decimal places, padding and alignment using f-string format specifiers.",
                        "price = 1999.5\ndiscount = 0.15\nfinal = price * (1 - discount)\n\nprint(f'Original price: Rs. {price:.2f}')\nprint(f'Discount: {discount * 100:.0f}%')\nprint(f'Final price: Rs. {final:.2f}')\n\n# Padding for table-like output\nfor item, cost in [('Pen', 10), ('Book', 150), ('Bag', 499)]:\n    print(f'{item:<10} Rs. {cost:>6}')",
                        "Original price: Rs. 1999.50\nDiscount: 15%\nFinal price: Rs. 1699.57\nPen        Rs.     10\nBook       Rs.    150\nBag        Rs.    499")
                ),
                List.of(
                    "Strings are immutable — all string methods return a new string, they do not modify the original",
                    "strip() removes whitespace from both ends — always use it when processing user input",
                    "f-strings are the recommended way to format strings in Python 3.6+",
                    "split() without arguments splits on any whitespace and removes empty strings",
                    "find() returns -1 if not found, while index() raises ValueError — prefer find() for safe searching",
                    "String comparison is case-sensitive: 'Python' != 'python'. Use lower() before comparing."
                ),
                "Always call strip() and lower() on string input before comparing or storing. User input often has accidental spaces and mixed case. This prevents many bugs where valid input fails a check because of invisible whitespace.",
                List.of(
                    "Forgetting that string methods return a new string — text.upper() does not change text, you must do text = text.upper()",
                    "Using + to concatenate many strings in a loop — this is slow. Use join() instead",
                    "Comparing strings without normalizing case: 'yes' == 'YES' is False"
                ),
                20, 5, "D"),

            // ── 6. Mutable vs Immutable ───────────────────────────────────────
            conceptRich(py, "Mutable vs Immutable",
                "Mutable objects can be changed after creation. Immutable objects cannot. Understanding this difference prevents hard-to-find bugs in Python.",
                "Imagine two types of notebooks.\n\nThe first is a whiteboard — you can write on it, erase it, and write again. This is like a mutable object in Python — it can be changed after creation.\n\nThe second is a printed book — once printed, the text cannot be changed. This is like an immutable object — once created, it stays as it is.\n\nIn Python:\n- Mutable: list, dict, set — can be changed after creation\n- Immutable: int, float, str, tuple, bool — cannot be changed\n\nThis matters most when you assign one variable to another or pass data to a function.",
                "1. Immutable types:\n- int, float, bool, str, tuple\n- When you reassign x = 5 and then x = 10, Python creates a new int object and points x to it\n- The original value 5 is not modified — x just points somewhere else\n\n2. Mutable types:\n- list, dict, set\n- When you do list_b = list_a, both variables point to the SAME list in memory\n- Changing list_b also changes list_a because they share the same object\n\n3. The aliasing problem:\n- a = [1, 2, 3]\n- b = a (b is not a copy, b is another name for the same list)\n- b.append(4) also modifies a\n\n4. How to make a real copy:\n- Shallow copy: b = a.copy() or b = a[:]\n- Shallow copy copies the list but not nested objects inside it\n- For full independent copy: import copy; b = copy.deepcopy(a)\n\n5. Why immutable types are safer:\n- Strings and integers passed to functions cannot be accidentally modified\n- Tuples are safe to use as dictionary keys, lists are not",
                "# Immutable — reassignment creates a new object\nx = 10\ny = x\nx = 20\nprint(x)  # 20\nprint(y)  # 10 (y still points to original 10)\n\n# Mutable — assignment shares the same object\nlist_a = [1, 2, 3]\nlist_b = list_a       # NOT a copy, same object\nlist_b.append(4)\nprint(list_a)  # [1, 2, 3, 4] — list_a also changed!\n\n# Making a real copy\nlist_c = list_a.copy()\nlist_c.append(99)\nprint(list_a)  # [1, 2, 3, 4] — unchanged\nprint(list_c)  # [1, 2, 3, 4, 99]\n\n# id() shows memory address\nprint(id(list_a) == id(list_b))  # True — same object\nprint(id(list_a) == id(list_c))  # False — different object\n\n# Strings are immutable\ntext = 'hello'\ntext_b = text\ntext = text.upper()\nprint(text)    # HELLO\nprint(text_b)  # hello (unchanged)",
                List.of(
                    new Concept.ConceptExample("The aliasing bug with lists",
                        "See how assigning a list to another variable creates an alias, not a copy.",
                        "original = [10, 20, 30]\nalias = original      # same object in memory\n\nalias.append(40)\n\nprint('original:', original)  # [10, 20, 30, 40]\nprint('alias:', alias)        # [10, 20, 30, 40]\nprint('Same object?', original is alias)  # True\n\n# Fix: use copy()\ncopy_list = original.copy()\ncopy_list.append(99)\nprint('original after fix:', original)   # [10, 20, 30, 40]\nprint('copy_list:', copy_list)           # [10, 20, 30, 40, 99]",
                        "original: [10, 20, 30, 40]\nalias: [10, 20, 30, 40]\nSame object? True\noriginal after fix: [10, 20, 30, 40]\ncopy_list: [10, 20, 30, 40, 99]"),
                    new Concept.ConceptExample("Function receives mutable object",
                        "Understand that mutable arguments passed to a function can be modified inside the function.",
                        "def add_bonus(marks_list):\n    for i in range(len(marks_list)):\n        marks_list[i] += 5   # modifies original list!\n\nmarks = [80, 75, 90]\nprint('Before:', marks)\nadd_bonus(marks)\nprint('After:', marks)   # original is changed\n\n# To avoid modifying original, pass a copy\nmarks2 = [80, 75, 90]\nadd_bonus(marks2.copy())  # passes a copy\nprint('marks2 unchanged:', marks2)",
                        "Before: [80, 75, 90]\nAfter: [85, 80, 95]\nmarks2 unchanged: [80, 75, 90]")
                ),
                List.of(
                    "Mutable: list, dict, set — can be changed after creation",
                    "Immutable: int, float, str, tuple, bool — cannot be changed",
                    "list_b = list_a does NOT create a copy — both variables point to the same list",
                    "Use list.copy() or list[:] to create a shallow copy of a list",
                    "Strings are immutable — string methods always return a new string",
                    "Immutable objects can be used as dictionary keys, mutable objects cannot"
                ),
                "Whenever you want to work on a copy of a list without affecting the original, use list.copy(). This is especially important when passing lists to functions — if the function modifies the list, it will change the original unless you pass a copy.",
                List.of(
                    "Assigning a list to another variable and expecting them to be independent — they share the same object",
                    "Passing a list to a function expecting it stays unchanged — mutable objects are modified in place",
                    "Trying to use a list as a dictionary key — only immutable types can be dictionary keys"
                ),
                15, 6, "D"),

            // ── 7. Slicing ────────────────────────────────────────────────────
            conceptRich(py, "Slicing",
                "Slicing lets you extract a portion of a list, string, or tuple using start, stop, and step values in the format [start:stop:step].",
                "Slicing is like cutting a portion from a sequence.\n\nImagine a row of lockers numbered 0 to 9. If you want lockers 2 through 5, you would say 'from locker 2 up to locker 6'. That is exactly how slicing works.\n\nThe syntax is: sequence[start:stop:step]\n- start: where to begin (included)\n- stop: where to end (excluded)\n- step: how many positions to jump each time\n\nAll three are optional:\n- [:3] means from start up to index 3\n- [2:] means from index 2 to the end\n- [::2] means every second element\n- [::-1] reverses the sequence",
                "1. Basic slicing syntax:\n- sequence[start:stop:step]\n- start is inclusive, stop is exclusive\n- Default start is 0, default stop is end, default step is 1\n\n2. Common patterns:\n- s[:3]: first 3 elements\n- s[3:]: everything from index 3 onwards\n- s[1:4]: elements at index 1, 2, 3\n- s[::2]: every second element\n- s[::-1]: reverse the sequence\n- s[-3:]: last 3 elements\n\n3. Slicing never raises IndexError:\n- Requesting s[0:100] on a 5-element list gives all 5 — no error\n\n4. Slicing creates a new object:\n- Slicing a list returns a new list\n- The original is not modified\n\n5. Negative step:\n- step of -1 goes backwards through the sequence\n- s[::-1] is the standard idiom to reverse a string or list",
                "text = 'Python Programming'\nnumbers = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]\n\n# Basic slicing\nprint(text[0:6])      # Python\nprint(text[7:])       # Programming\nprint(text[:6])       # Python\n\n# List slicing\nprint(numbers[2:6])   # [2, 3, 4, 5]\nprint(numbers[:5])    # [0, 1, 2, 3, 4]\nprint(numbers[5:])    # [5, 6, 7, 8, 9]\n\n# Step\nprint(numbers[::2])   # [0, 2, 4, 6, 8]\nprint(numbers[1::2])  # [1, 3, 5, 7, 9]\n\n# Negative index slicing\nprint(numbers[-3:])   # [7, 8, 9] last 3\nprint(numbers[:-3])   # [0, 1, 2, 3, 4, 5, 6] all except last 3\n\n# Reverse with [::-1]\nprint(text[::-1])          # gnimmargorP nohtyP\nprint(numbers[::-1])       # [9, 8, 7, 6, 5, 4, 3, 2, 1, 0]\n\n# Check if palindrome\nword = 'radar'\nprint(word == word[::-1])  # True",
                List.of(
                    new Concept.ConceptExample("Extract first, last and middle parts",
                        "Use slicing to extract specific portions of a list or string.",
                        "students = ['Ravi', 'Priya', 'Arjun', 'Sneha', 'Kiran', 'Divya']\n\nprint('First 3:', students[:3])\nprint('Last 2:', students[-2:])\nprint('Middle:', students[2:4])\nprint('Every 2nd:', students[::2])\nprint('Reversed:', students[::-1])",
                        "First 3: ['Ravi', 'Priya', 'Arjun']\nLast 2: ['Kiran', 'Divya']\nMiddle: ['Arjun', 'Sneha']\nEvery 2nd: ['Ravi', 'Arjun', 'Kiran']\nReversed: ['Divya', 'Kiran', 'Sneha', 'Arjun', 'Priya', 'Ravi']"),
                    new Concept.ConceptExample("Check palindrome using slicing",
                        "Use [::-1] to reverse a string and check if it reads the same both ways.",
                        "words = ['radar', 'python', 'level', 'hello', 'madam']\n\nfor word in words:\n    if word == word[::-1]:\n        print(f'{word} is a palindrome')\n    else:\n        print(f'{word} is not a palindrome')",
                        "radar is a palindrome\npython is not a palindrome\nlevel is a palindrome\nhello is not a palindrome\nmadam is a palindrome"),
                    new Concept.ConceptExample("Pagination using slicing",
                        "Slice a list to show results page by page — a real use case in web applications.",
                        "all_results = list(range(1, 51))  # 50 items\npage_size = 10\n\npage_number = 2\nstart = (page_number - 1) * page_size\nend = start + page_size\n\npage = all_results[start:end]\nprint(f'Page {page_number}:', page)",
                        "Page 2: [11, 12, 13, 14, 15, 16, 17, 18, 19, 20]")
                ),
                List.of(
                    "The stop index is exclusive — s[0:3] gives elements at index 0, 1, 2 (not 3)",
                    "Slicing never raises IndexError even if indices are out of range",
                    "Slicing creates a new object — the original list or string is not modified",
                    "[::-1] is the standard Python idiom to reverse a string or list",
                    "Negative indices work in slicing: s[-3:] gives the last 3 elements"
                ),
                "Use s[::-1] to quickly reverse any string or list. This is a very common pattern in interview questions — checking for palindromes, reversing words, and similar tasks. Memorise this idiom.",
                List.of(
                    "Forgetting that stop is exclusive: s[0:3] gives 3 elements (index 0, 1, 2), not 4",
                    "Expecting slicing to modify the original — it always returns a new object"
                ),
                15, 7, "D"),

            // ── 8. if, elif, else ─────────────────────────────────────────────
            conceptRich(py, "if, elif, else",
                "Conditional statements let your program make decisions by executing different code blocks based on whether conditions are True or False.",
                "Every useful program needs to make decisions.\n\nShould I send this email? Is this user logged in? Is the password correct? Is the number positive or negative?\n\nIn Python, you use if, elif, and else to handle decisions:\n- if runs a block of code when a condition is True\n- elif checks another condition if the previous one was False\n- else runs when none of the above conditions were True\n\nOnly one block executes — whichever condition is True first. Python checks conditions from top to bottom and stops at the first match.",
                "1. Basic if statement:\n- if condition: — runs the block when condition is True\n- The colon and indentation are required\n\n2. if-else:\n- else: runs when the if condition is False\n- There is no condition on else\n\n3. if-elif-else chain:\n- elif (else if) checks another condition\n- You can have as many elif blocks as needed\n- Python checks conditions top to bottom and executes the first True one\n- else at the end is optional\n\n4. Nested if:\n- You can put if statements inside other if statements\n- Each level adds 4 more spaces of indentation\n\n5. Truthy and falsy values:\n- Falsy: 0, 0.0, '', None, [], {}, set()\n- Everything else is truthy\n- You can write if username: instead of if username != ''\n\n6. Ternary expression (one-line if):\n- result = 'Pass' if marks >= 40 else 'Fail'",
                "marks = 72\n\n# Basic if-elif-else\nif marks >= 90:\n    grade = 'A'\nelif marks >= 75:\n    grade = 'B'\nelif marks >= 60:\n    grade = 'C'\nelif marks >= 40:\n    grade = 'D'\nelse:\n    grade = 'F'\n\nprint(f'Marks: {marks}, Grade: {grade}')\n\n# Truthy and falsy\nusername = 'ravi'\nif username:\n    print('Username provided')\n\nempty_list = []\nif not empty_list:\n    print('List is empty')\n\n# Nested if\nage = 20\nhas_id = True\n\nif age >= 18:\n    if has_id:\n        print('Entry allowed')\n    else:\n        print('ID required')\nelse:\n    print('Must be 18 or older')\n\n# Ternary expression\nstatus = 'Pass' if marks >= 40 else 'Fail'\nprint(status)",
                List.of(
                    new Concept.ConceptExample("Grade calculator",
                        "Use if-elif-else to assign a grade based on marks.",
                        "def get_grade(marks):\n    if marks >= 90:\n        return 'A+'\n    elif marks >= 80:\n        return 'A'\n    elif marks >= 70:\n        return 'B'\n    elif marks >= 60:\n        return 'C'\n    elif marks >= 40:\n        return 'D'\n    else:\n        return 'F'\n\ntest_marks = [95, 83, 72, 61, 45, 30]\nfor m in test_marks:\n    print(f'{m} -> {get_grade(m)}')",
                        "95 -> A+\n83 -> A\n72 -> B\n61 -> C\n45 -> D\n30 -> F"),
                    new Concept.ConceptExample("Login validation",
                        "Use nested if and logical operators to validate login credentials.",
                        "stored_username = 'admin'\nstored_password = 'secure123'\n\nusername = input('Username: ')\npassword = input('Password: ')\n\nif not username or not password:\n    print('Username and password are required')\nelif username == stored_username and password == stored_password:\n    print('Login successful. Welcome!')\nelse:\n    print('Invalid credentials. Try again.')",
                        "Username: admin\nPassword: secure123\nLogin successful. Welcome!"),
                    new Concept.ConceptExample("Multiple conditions using and, or",
                        "Combine conditions to handle more complex decision logic.",
                        "temperature = 35\nis_raining = False\n\nif temperature > 30 and not is_raining:\n    print('Good day for outdoor activity')\nelif temperature > 30 and is_raining:\n    print('Hot and rainy — stay inside')\nelif temperature <= 30 and is_raining:\n    print('Cool and rainy — carry umbrella')\nelse:\n    print('Pleasant weather')",
                        "Good day for outdoor activity")
                ),
                List.of(
                    "Only the first matching condition block executes — Python stops checking after the first True",
                    "else does not have a condition — it runs when all previous conditions are False",
                    "Indentation defines which code belongs to which block — 4 spaces per level",
                    "Truthy/falsy: if my_list: is the same as if len(my_list) > 0: — both check if list is non-empty",
                    "Ternary: x = value_if_true if condition else value_if_false"
                ),
                "Avoid deeply nested if statements. If you find yourself writing three or four levels of nesting, consider using functions or combining conditions with and/or. Deep nesting is hard to read and maintain.",
                List.of(
                    "Using = instead of == in condition: if x = 5 is a SyntaxError",
                    "Forgetting the colon : after if, elif, or else",
                    "Putting else with a condition: else is unconditional, use elif for conditions",
                    "Not indenting the code block inside if — Python requires indentation"
                ),
                20, 8, "D"),

            // ── 9. for and while Loops ────────────────────────────────────────
            conceptRich(py, "for and while Loops",
                "Loops let you repeat a block of code multiple times. The for loop iterates over a sequence, the while loop runs as long as a condition is True.",
                "Loops save you from writing the same code multiple times.\n\nImagine printing the names of 100 students. Without a loop you would write 100 print statements. With a loop, you write the logic once and Python repeats it.\n\nPython has two types of loops:\n\nfor loop — use when you know what you want to loop over:\n- Loop through each item in a list\n- Loop a specific number of times using range()\n- Loop through characters in a string\n\nwhile loop — use when you loop until a condition becomes False:\n- Keep asking for input until valid input is received\n- Count up or down until a target is reached",
                "1. for loop:\n- for item in sequence: iterates over each element\n- for i in range(n): iterates n times, i goes from 0 to n-1\n- range(start, stop, step): gives numbers from start to stop-1 with step\n- enumerate(sequence): gives both index and value\n- zip(a, b): iterates two sequences together\n\n2. while loop:\n- while condition: runs as long as condition is True\n- Must update something inside the loop to eventually make condition False\n- Infinite loop: while True: — must use break to exit\n\n3. Nested loops:\n- A loop inside another loop\n- Inner loop runs completely for each iteration of outer loop\n- Total iterations = outer count x inner count\n\n4. range() function:\n- range(5): 0, 1, 2, 3, 4\n- range(1, 6): 1, 2, 3, 4, 5\n- range(0, 10, 2): 0, 2, 4, 6, 8\n- range(10, 0, -1): 10, 9, 8, ... 1",
                "# for loop with a list\nfruits = ['apple', 'mango', 'banana']\nfor fruit in fruits:\n    print(fruit)\n\n# for loop with range\nfor i in range(5):\n    print(i, end=' ')  # 0 1 2 3 4\nprint()\n\n# range with start and stop\nfor i in range(1, 6):\n    print(i, end=' ')  # 1 2 3 4 5\nprint()\n\n# enumerate\nfor index, fruit in enumerate(fruits):\n    print(index, '-', fruit)\n\n# while loop\ncount = 0\nwhile count < 5:\n    print(count, end=' ')\n    count += 1\nprint()\n\n# while with user input\nwhile True:\n    answer = input('Enter yes to continue: ')\n    if answer.lower() == 'yes':\n        break\n    print('Please type yes')\n\n# Nested loops\nfor i in range(1, 4):\n    for j in range(1, 4):\n        print(i * j, end='\\t')\n    print()",
                List.of(
                    new Concept.ConceptExample("Sum and average using for loop",
                        "Loop through a list to calculate sum and average.",
                        "marks = [85, 92, 78, 95, 88, 72]\ntotal = 0\n\nfor mark in marks:\n    total += mark\n\naverage = total / len(marks)\nprint(f'Total: {total}')\nprint(f'Average: {average:.2f}')\nprint(f'Pass count: {sum(1 for m in marks if m >= 40)}')",
                        "Total: 510\nAverage: 85.00\nPass count: 6"),
                    new Concept.ConceptExample("Number guessing game with while loop",
                        "Use a while loop to keep asking the user to guess until correct.",
                        "secret = 42\nattempts = 0\n\nwhile True:\n    guess = int(input('Guess the number: '))\n    attempts += 1\n\n    if guess < secret:\n        print('Too low')\n    elif guess > secret:\n        print('Too high')\n    else:\n        print(f'Correct! You got it in {attempts} attempts')\n        break",
                        "Guess the number: 25\nToo low\nGuess the number: 50\nToo high\nGuess the number: 42\nCorrect! You got it in 3 attempts"),
                    new Concept.ConceptExample("Multiplication table using nested loops",
                        "Print a multiplication table using two nested for loops.",
                        "for i in range(1, 6):\n    for j in range(1, 6):\n        print(f'{i * j:4}', end='')\n    print()",
                        "   1   2   3   4   5\n   2   4   6   8  10\n   3   6   9  12  15\n   4   8  12  16  20\n   5  10  15  20  25"),
                    new Concept.ConceptExample("Loop through dictionary items",
                        "Use a for loop with items() to iterate through key-value pairs.",
                        "student_grades = {\n    'Ravi': 85,\n    'Priya': 92,\n    'Arjun': 78,\n    'Sneha': 95\n}\n\nfor name, grade in student_grades.items():\n    status = 'Pass' if grade >= 40 else 'Fail'\n    print(f'{name}: {grade} - {status}')",
                        "Ravi: 85 - Pass\nPriya: 92 - Pass\nArjun: 78 - Pass\nSneha: 95 - Pass")
                ),
                List.of(
                    "for loop is used when you know what to iterate over — a list, string, range, or any sequence",
                    "while loop is used when you loop until a condition changes — like waiting for valid input",
                    "range(n) gives 0 to n-1, not 0 to n",
                    "Always update the loop variable in a while loop — forgetting causes an infinite loop",
                    "enumerate(list) gives both index and value — prefer it over range(len(list))",
                    "break exits the loop completely, continue skips to the next iteration"
                ),
                "Be careful with while loops — always make sure the condition will eventually become False. The most common cause of an infinite loop is forgetting to update the variable that the condition checks. If your program hangs, it is probably an infinite while loop.",
                List.of(
                    "Forgetting to increment the counter in a while loop — causes infinite loop",
                    "Using range(1, 5) and expecting 5 iterations — range(1, 5) gives 1, 2, 3, 4 (only 4 values)",
                    "Modifying a list while iterating over it — can cause elements to be skipped or processed twice",
                    "Using a for loop when a while loop is needed or vice versa"
                ),
                20, 9, "D"),

            // ── 10. break, continue, pass ─────────────────────────────────────
            conceptRich(py, "break, continue, pass",
                "break exits a loop immediately, continue skips the current iteration and moves to the next, pass does nothing and acts as a placeholder.",
                "When looping, sometimes you need finer control:\n\nbreak — stop the loop entirely and move on.\nLike a fire alarm that makes you stop everything and leave the building.\n\ncontinue — skip what is left for this round and go to the next.\nLike skipping a bad song in a playlist and moving to the next one.\n\npass — do nothing.\nLike an empty drawer — a placeholder that keeps the structure valid when you have nothing to write yet.\n\nAll three work inside for and while loops.",
                "1. break:\n- Exits the loop immediately\n- Code after the loop continues running\n- Used when you found what you were looking for or a condition to stop is met\n- Works with both for and while loops\n\n2. continue:\n- Skips the rest of the current iteration\n- Loop continues with the next iteration\n- Used to skip unwanted items while still processing others\n\n3. pass:\n- Does absolutely nothing\n- Used as a placeholder in empty code blocks\n- Python requires at least one statement inside if, for, def, class blocks — pass satisfies this\n- Useful when writing a function or class skeleton before filling in the logic\n\n4. else clause on loops:\n- for...else and while...else are valid Python\n- The else block runs only if the loop completed without hitting break\n- Useful for search operations: if break was never hit, the item was not found",
                "# break — exit loop when target found\nnumbers = [3, 7, 12, 5, 19, 8]\n\nfor num in numbers:\n    if num > 10:\n        print(f'First number > 10: {num}')\n        break\n\n# continue — skip even numbers\nfor i in range(1, 11):\n    if i % 2 == 0:\n        continue\n    print(i, end=' ')  # 1 3 5 7 9\nprint()\n\n# pass — placeholder\ndef process_data():\n    pass   # TODO: implement this later\n\nclass DatabaseManager:\n    pass   # empty class for now\n\n# for...else\ntarget = 15\nfor num in numbers:\n    if num == target:\n        print('Found!')\n        break\nelse:\n    print(f'{target} not found in list')\n\n# while with break\nattempts = 0\nmax_attempts = 3\n\nwhile attempts < max_attempts:\n    password = input('Enter password: ')\n    attempts += 1\n    if password == 'secret123':\n        print('Access granted')\n        break\nelse:\n    print('Too many failed attempts')",
                List.of(
                    new Concept.ConceptExample("Search with break",
                        "Use break to stop searching once the target is found.",
                        "students = ['Ravi', 'Priya', 'Arjun', 'Sneha', 'Kiran']\nsearch = 'Arjun'\n\nfor i, name in enumerate(students):\n    if name == search:\n        print(f'Found {search} at position {i}')\n        break\nelse:\n    print(f'{search} not found')",
                        "Found Arjun at position 2"),
                    new Concept.ConceptExample("Filter with continue",
                        "Use continue to skip items that do not meet a condition.",
                        "marks = [85, 30, 92, 15, 78, 45, 22, 88]\n\nprint('Passed students:')\nfor i, mark in enumerate(marks):\n    if mark < 40:\n        continue   # skip failed marks\n    print(f'  Student {i+1}: {mark}')",
                        "Passed students:\n  Student 1: 85\n  Student 3: 92\n  Student 5: 78\n  Student 6: 45\n  Student 8: 88"),
                    new Concept.ConceptExample("pass as placeholder in class or function",
                        "Use pass when writing the structure first and logic later.",
                        "# Write structure first, fill logic later\nclass UserService:\n    def get_user(self, user_id):\n        pass   # TODO: fetch from database\n\n    def create_user(self, data):\n        pass   # TODO: validate and save\n\n    def delete_user(self, user_id):\n        pass   # TODO: remove from database\n\n# Code is valid Python even without implementation\nservice = UserService()\nprint('UserService created:', service)",
                        "UserService created: <__main__.UserService object at 0x...>")
                ),
                List.of(
                    "break exits the entire loop, continue only skips the current iteration",
                    "pass is a no-op placeholder — Python requires at least one statement in any code block",
                    "The for...else and while...else clause runs only if the loop was not exited with break",
                    "break in a nested loop only exits the innermost loop, not all loops",
                    "continue in a while loop still evaluates the while condition before the next iteration"
                ),
                "Use the for...else pattern when searching for an item in a list. If break is hit, the item was found. If the loop finishes without break, the else runs and you know the item was not there. This is cleaner than using a flag variable.",
                List.of(
                    "Thinking break exits all nested loops — it only exits the innermost loop",
                    "Forgetting pass in an empty function or class body — Python raises IndentationError or SyntaxError",
                    "Using continue in a while loop without ensuring the condition will still eventually be False"
                ),
                15, 10, "D"),

            // ── 11. Functions ─────────────────────────────────────────────────
            conceptRich(py, "Functions — def, Parameters, return",
                "Functions let you group reusable code under a name. You define a function once and call it as many times as needed with different inputs.",
                "A function is like a recipe.\n\nA biryani recipe tells you the steps once. Whenever you want to make biryani, you follow that recipe. You can use it for 2 people, 10 people, or 100 people — the recipe is the same, only the quantity (input) changes.\n\nIn Python:\n- def keyword defines the function\n- Parameters are the inputs the function accepts\n- return sends a result back to whoever called the function\n- Arguments are the actual values you pass when calling\n\nFunctions prevent you from writing the same logic in ten different places. If the logic needs to change, you change it in one place.",
                "1. Defining a function:\n- def function_name(parameters):\n- Colon and indentation required\n- Function body is the indented block\n\n2. Parameters and arguments:\n- Parameters are the variable names in the function definition: def add(a, b)\n- Arguments are the values passed when calling: add(3, 5)\n- Positional arguments: matched by position\n- Keyword arguments: matched by name: add(b=5, a=3)\n- Default parameters: def greet(name, message='Hello')\n\n3. return statement:\n- Sends a value back to the caller\n- Function stops executing when return is reached\n- Without return, function returns None\n- Can return multiple values as a tuple: return x, y\n\n4. Scope:\n- Variables inside a function are local — not accessible outside\n- Variables outside a function are global — readable inside but not writable without global keyword\n\n5. Docstrings:\n- String on first line of function body describes what it does\n- Accessed via function.__doc__",
                "# Basic function\ndef greet(name):\n    return f'Hello, {name}!'\n\nprint(greet('Ravi'))     # Hello, Ravi!\nprint(greet('Priya'))   # Hello, Priya!\n\n# Multiple parameters\ndef add(a, b):\n    return a + b\n\nprint(add(3, 5))         # 8\nprint(add(10, 20))       # 30\n\n# Default parameter\ndef power(base, exponent=2):\n    return base ** exponent\n\nprint(power(4))          # 16 (uses default exponent 2)\nprint(power(4, 3))       # 64 (overrides default)\n\n# Return multiple values\ndef min_max(numbers):\n    return min(numbers), max(numbers)\n\nlow, high = min_max([3, 7, 1, 9, 5])\nprint(f'Min: {low}, Max: {high}')\n\n# Local scope\ndef calculate(x):\n    result = x * 2     # result is local\n    return result\n\nprint(calculate(5))   # 10\n# print(result)       # NameError: result is not defined outside",
                List.of(
                    new Concept.ConceptExample("Calculate grade using a function",
                        "Write a reusable function to convert marks into grades.",
                        "def get_grade(marks):\n    if marks >= 90:\n        return 'A'\n    elif marks >= 75:\n        return 'B'\n    elif marks >= 60:\n        return 'C'\n    elif marks >= 40:\n        return 'D'\n    else:\n        return 'F'\n\nstudent_marks = [95, 82, 67, 45, 30]\nfor mark in student_marks:\n    print(f'{mark} -> Grade {get_grade(mark)}')",
                        "95 -> Grade A\n82 -> Grade B\n67 -> Grade C\n45 -> Grade D\n30 -> Grade F"),
                    new Concept.ConceptExample("Function with default parameters",
                        "Use default parameter values to make arguments optional.",
                        "def create_profile(name, role='Student', active=True):\n    status = 'Active' if active else 'Inactive'\n    return f'{name} | {role} | {status}'\n\nprint(create_profile('Ravi'))\nprint(create_profile('Priya', 'Developer'))\nprint(create_profile('Arjun', 'Manager', False))",
                        "Ravi | Student | Active\nPriya | Developer | Active\nArjun | Manager | Inactive"),
                    new Concept.ConceptExample("Return multiple values",
                        "A function can return multiple values which are unpacked on the caller side.",
                        "def analyse_marks(marks_list):\n    total = sum(marks_list)\n    average = total / len(marks_list)\n    highest = max(marks_list)\n    lowest = min(marks_list)\n    return total, average, highest, lowest\n\nmarks = [85, 92, 78, 95, 88]\ntotal, avg, high, low = analyse_marks(marks)\n\nprint(f'Total: {total}')\nprint(f'Average: {avg:.1f}')\nprint(f'Highest: {high}')\nprint(f'Lowest: {low}')",
                        "Total: 438\nAverage: 87.6\nHighest: 95\nLowest: 78"),
                    new Concept.ConceptExample("Function calling another function",
                        "Functions can call other functions to build modular logic.",
                        "def is_pass(marks):\n    return marks >= 40\n\ndef result_summary(name, marks):\n    status = 'Pass' if is_pass(marks) else 'Fail'\n    return f'{name}: {marks} marks - {status}'\n\nstudents = [('Ravi', 85), ('Priya', 35), ('Arjun', 62)]\nfor name, marks in students:\n    print(result_summary(name, marks))",
                        "Ravi: 85 marks - Pass\nPriya: 35 marks - Fail\nArjun: 62 marks - Pass")
                ),
                List.of(
                    "def only defines the function, it does not run it. Call the function by name with () to run it",
                    "Parameters are local variables — they only exist inside the function",
                    "A function without a return statement returns None automatically",
                    "Default parameters must come after non-default parameters in the function signature",
                    "You can return multiple values as a tuple: return x, y — unpack with a, b = function()"
                ),
                "Keep functions small and focused on one task. A function that does one thing clearly is easy to test, reuse, and debug. If a function needs a paragraph to describe what it does, it is probably doing too much and should be split.",
                List.of(
                    "Calling a function without parentheses: print_hello instead of print_hello() — this references the function object, not calling it",
                    "Expecting a variable defined inside a function to be available outside — local variables are not accessible outside the function",
                    "Putting mutable objects like lists as default parameters — default values are created once and shared across calls"
                ),
                20, 11, "D"),

            // ── 12. Exception Handling ────────────────────────────────────────
            conceptRich(py, "Exception Handling — try, except, finally",
                "Exception handling lets your program deal with runtime errors gracefully instead of crashing. Use try to run code, except to catch errors, and finally to clean up.",
                "Every program will encounter unexpected situations — a user types a letter when you expect a number, a file is missing, or a network request fails.\n\nWithout exception handling, your program crashes and shows an ugly error message.\n\nWith exception handling, your program catches the problem, shows a friendly message, and continues or exits cleanly.\n\nPython uses:\n- try: put the code that might fail here\n- except: handle the specific error here\n- finally: code that always runs regardless of success or failure\n- else: runs only if no exception occurred",
                "1. try-except block:\n- Code inside try is attempted\n- If an exception occurs, Python jumps to the matching except block\n- The program does not crash\n\n2. Catching specific exceptions:\n- except ValueError: catches only ValueError\n- except (TypeError, ValueError): catches multiple types\n- except Exception as e: catches any exception and stores it in e\n- Using bare except: is not recommended — it catches everything including system exits\n\n3. finally block:\n- Runs always, whether an exception occurred or not\n- Used for cleanup: closing files, database connections, releasing resources\n\n4. else block:\n- Runs only when no exception occurred in try\n- Keeps the success path separate from the exception path\n\n5. Common built-in exceptions:\n- ValueError: wrong value type (int('hello'))\n- TypeError: wrong operation for the type (5 + 'hello')\n- KeyError: dictionary key not found\n- IndexError: list index out of range\n- FileNotFoundError: file does not exist\n- ZeroDivisionError: dividing by zero",
                "# Basic try-except\ntry:\n    number = int(input('Enter a number: '))\n    result = 100 / number\n    print(f'Result: {result}')\nexcept ValueError:\n    print('Please enter a valid integer')\nexcept ZeroDivisionError:\n    print('Cannot divide by zero')\n\n# Catching any exception with details\ntry:\n    data = [1, 2, 3]\n    print(data[10])\nexcept Exception as e:\n    print(f'Error: {e}')\n    print(f'Type: {type(e).__name__}')\n\n# try-except-else-finally\ntry:\n    marks = int(input('Enter marks: '))\nexcept ValueError:\n    print('Invalid input')\nelse:\n    print(f'Marks entered: {marks}')  # runs only if no exception\nfinally:\n    print('Input attempt complete')   # always runs\n\n# Nested try-except\ndef safe_divide(a, b):\n    try:\n        return a / b\n    except ZeroDivisionError:\n        return None",
                List.of(
                    new Concept.ConceptExample("Safe integer input from user",
                        "Keep asking for input until the user enters a valid integer.",
                        "def get_integer(prompt):\n    while True:\n        try:\n            return int(input(prompt))\n        except ValueError:\n            print('Invalid input. Please enter a whole number.')\n\nage = get_integer('Enter your age: ')\nprint(f'Your age is {age}')",
                        "Enter your age: twenty\nInvalid input. Please enter a whole number.\nEnter your age: 21\nYour age is 21"),
                    new Concept.ConceptExample("Handle multiple exceptions",
                        "Catch different types of errors with separate except blocks.",
                        "def process(items, index):\n    try:\n        value = items[index]\n        result = 100 / value\n        return result\n    except IndexError:\n        print(f'Index {index} is out of range')\n    except ZeroDivisionError:\n        print('Value at index is zero, cannot divide')\n    except TypeError:\n        print('List contains non-numeric value')\n    return None\n\ndata = [10, 0, 'five', 25]\nprint(process(data, 0))   # works fine\nprint(process(data, 1))   # ZeroDivisionError\nprint(process(data, 2))   # TypeError\nprint(process(data, 9))   # IndexError",
                        "10.0\nValue at index is zero, cannot divide\nNone\nList contains non-numeric value\nNone\nIndex 9 is out of range\nNone"),
                    new Concept.ConceptExample("finally for cleanup",
                        "Use finally to ensure cleanup code always runs, even when an error occurs.",
                        "def read_config(filename):\n    file = None\n    try:\n        file = open(filename, 'r')\n        content = file.read()\n        return content\n    except FileNotFoundError:\n        print(f'File {filename} not found')\n        return None\n    finally:\n        if file:\n            file.close()   # always close the file\n            print('File closed')\n\nresult = read_config('config.txt')\nif result:\n    print('Config loaded')",
                        "File config.txt not found\nFile closed")
                ),
                List.of(
                    "try contains the code that might fail — keep it as small as possible",
                    "Catch specific exceptions rather than bare except — this prevents hiding unexpected errors",
                    "finally always runs — use it for cleanup like closing files or database connections",
                    "else runs only when no exception occurred — keeps success logic separate from error handling",
                    "except Exception as e: gives you the error message in e — useful for logging"
                ),
                "Always catch specific exceptions rather than using a bare except or except Exception for everything. When you catch too broadly, you hide bugs and make debugging very difficult. If you are not sure which exception to catch, let the program crash once to see what exception it raises, then catch that specific one.",
                List.of(
                    "Using bare except: without specifying the exception type — catches everything including keyboard interrupts",
                    "Putting too much code inside try — only wrap the specific line that might fail",
                    "Swallowing exceptions silently with an empty except block — always at least print the error",
                    "Forgetting that finally runs even when there is a return inside try or except"
                ),
                20, 12, "D")

        );

        conceptRepository.saveAll(concepts);
        py.setTotalConcepts(concepts.size());
        subjectRepository.save(py);
        System.out.println("✅ Python Basics seeded — " + concepts.size() + " concepts");
    }
}
