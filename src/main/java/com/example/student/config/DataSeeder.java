package com.example.student.config;

import com.example.student.model.ProblemQuestion;
import com.example.student.model.Question;
import com.example.student.model.Roadmap;
import com.example.student.model.User;
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

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      ProblemRepository problemRepository,
                      RoadmapRepository roadmapRepository,
                      QuestionRepository questionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.problemRepository = problemRepository;
        this.roadmapRepository = roadmapRepository;
        this.questionRepository = questionRepository;
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
                a.setPassword(passwordEncoder.encode("Admin@123"));
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
        seedTrack1(all);
        seedTrack2(all);
        seedSkillUpAndInterview(all);
        problemRepository.saveAll(all);
        System.out.println("✅ " + all.size() + " problem questions seeded");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TRACK 2 — LOGIC BUILDING
    // ═══════════════════════════════════════════════════════════════════════════
    private void seedTrack2(List<ProblemQuestion> all) {
        final List<String> T = List.of("LOGIC_BUILDING");

        // ── 1. Right Triangle Stars ───────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Patterns", "Loops"), "Star Patterns", "BEGINNER", "PATTERN", "Right Triangle Stars", 1);
            p.setDescription("Print a right-angled triangle of stars with n = 5 rows:\n\n*\n* *\n* * *\n* * * *\n* * * * *\n\nRow 1 has 1 star, row 2 has 2 stars, and so on.");
            p.setInputFormat("n = 5 (hardcoded)");
            p.setOutputFormat("5 rows of increasing stars separated by spaces.");
            p.setSampleInput("(none)");
            p.setSampleOutput("*\n* *\n* * *\n* * * *\n* * * * *");
            p.setConstraints("Stars separated by a space.");
            p.setHints(List.of(
                "Use two loops: outer for row number, inner for stars in that row.",
                "For row i (starting from 1), the inner loop runs i times.",
                "After each row's stars, print a newline."
            ));
            p.setApproach("Outer loop i from 1 to n. Inner loop j from 1 to i — print a star each time. After inner loop, move to next line.");
            p.setExplanation("The key insight: the number of stars in a row equals the row number. That relationship drives the inner loop limit.");
            var bruteCode = sc(
                "#include <stdio.h>\nint main() {\n    int n=5;\n    for(int i=1;i<=n;i++){\n        for(int j=1;j<=n;j++)\n            if(j<=i) printf(\"* \");\n        printf(\"\\n\");\n    }\n    return 0;\n}",
                "n = 5\nfor i in range(1, n+1):\n    for j in range(1, n+1):\n        if j <= i:\n            print('*', end=' ')\n    print()  # n*n iterations — extra checks wasted",
                "public class Main {\n    public static void main(String[] args) {\n        int n=5;\n        for(int i=1;i<=n;i++){\n            for(int j=1;j<=n;j++)\n                if(j<=i) System.out.print(\"* \");\n            System.out.println();\n        }\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n=5;\n    for(int i=1;i<=n;i++){\n        for(int j=1;j<=n;j++)\n            if(j<=i) cout<<\"* \";\n        cout<<endl;\n    }\n    return 0;\n}"
            );
            var normalCode = sc(
                "#include <stdio.h>\nint main() {\n    int n=5;\n    for(int i=1;i<=n;i++){\n        for(int j=1;j<=i;j++) printf(\"* \");\n        printf(\"\\n\");\n    }\n    return 0;\n}",
                "n = 5\nfor i in range(1, n+1):\n    for j in range(i):\n        print('*', end=' ')\n    print()",
                "public class Main {\n    public static void main(String[] args) {\n        int n=5;\n        for(int i=1;i<=n;i++){\n            for(int j=0;j<i;j++) System.out.print(\"* \");\n            System.out.println();\n        }\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n=5;\n    for(int i=1;i<=n;i++){\n        for(int j=0;j<i;j++) cout<<\"* \";\n        cout<<endl;\n    }\n    return 0;\n}"
            );
            var optCode = sc(
                normalCode.getC(),
                "n = 5\nfor i in range(1, n+1):\n    print('* ' * i)  # string repetition — no inner loop",
                normalCode.getJava(),
                normalCode.getCpp()
            );
            p.setSolutions(sols(
                sv("Full n×n grid with an if condition — many wasted iterations.", "O(n²)", "O(1)", bruteCode),
                sv("Inner loop runs exactly i times per row — clean and direct.", "O(n²)", "O(1)", normalCode),
                sv("Python: string multiplication replaces the inner loop entirely.", "O(n²)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 2. Inverted Triangle Stars ────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Patterns", "Loops"), "Star Patterns", "BEGINNER", "PATTERN", "Inverted Triangle Stars", 2);
            p.setDescription("Print an inverted right triangle for n = 5:\n\n* * * * *\n* * * *\n* * *\n* *\n*\n\nRow 1 has n stars, row 2 has n-1, and so on.");
            p.setInputFormat("n = 5 (hardcoded)");
            p.setOutputFormat("5 rows of decreasing stars.");
            p.setSampleInput("(none)");
            p.setSampleOutput("* * * * *\n* * * *\n* * *\n* *\n*");
            p.setConstraints("Stars separated by a space.");
            p.setHints(List.of(
                "Row 1 needs n stars, row 2 needs n-1 stars...",
                "Outer loop from 1 to n, inner loop from n down to i.",
                "Or: outer loop i from n down to 1, inner loop j from 0 to i."
            ));
            p.setApproach("Outer loop i from n down to 1. Inner loop j from 0 to i — print a star each time. Then newline.");
            p.setExplanation("Inverted patterns use a decreasing loop limit. Understanding both normal and inverted triangles teaches you to control loop bounds in both directions.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    int n=5;\n    for(int i=n;i>=1;i--){\n        for(int j=0;j<i;j++) printf(\"* \");\n        printf(\"\\n\");\n    }\n    return 0;\n}",
                "n = 5\nfor i in range(n, 0, -1):\n    print('* ' * i)",
                "public class Main {\n    public static void main(String[] args) {\n        int n=5;\n        for(int i=n;i>=1;i--){\n            for(int j=0;j<i;j++) System.out.print(\"* \");\n            System.out.println();\n        }\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n=5;\n    for(int i=n;i>=1;i--){\n        for(int j=0;j<i;j++) cout<<\"* \";\n        cout<<endl;\n    }\n    return 0;\n}"
            );
            p.setSolutions(same(sv("Outer loop decreases from n to 1, inner loop runs i times.", "O(n²)", "O(1)", code)));
            all.add(p);
        }

        // ── 3. Check Even or Odd ──────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Conditionals", "Operators"), "Number Problems", "BEGINNER", "WRITE", "Check Even or Odd", 3);
            p.setDescription("Read an integer n. Print \"Even\" if it is divisible by 2, otherwise print \"Odd\".\n\nExamples:\nInput: 4  → Output: Even\nInput: 7  → Output: Odd\nInput: 0  → Output: Even");
            p.setInputFormat("One integer n.");
            p.setOutputFormat("Even or Odd.");
            p.setSampleInput("7");
            p.setSampleOutput("Odd");
            p.setConstraints("-10^6 <= n <= 10^6");
            p.setHints(List.of(
                "A number is even if it leaves remainder 0 when divided by 2.",
                "Use the modulo operator: n % 2 == 0 means even.",
                "0 is even because 0 % 2 == 0."
            ));
            p.setApproach("One condition: if n % 2 == 0, print Even, else print Odd. The % operator gives the remainder of division.");
            p.setExplanation("The modulo operator (%) is fundamental. It is used constantly in programming — to check divisibility, wrap array indices, detect patterns, and more.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    int n; scanf(\"%d\", &n);\n    printf(\"%s\\n\", n%2==0 ? \"Even\" : \"Odd\");\n    return 0;\n}",
                "n = int(input())\nprint(\"Even\" if n % 2 == 0 else \"Odd\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n = new Scanner(System.in).nextInt();\n        System.out.println(n%2==0 ? \"Even\" : \"Odd\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n; cin>>n;\n    cout<<(n%2==0?\"Even\":\"Odd\")<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("n % 2 == 0 means even, else odd.", "O(1)", "O(1)", code)));
            all.add(p);
        }

        // ── 4. Sum of Digits ──────────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Loops", "Operators"), "Number Problems", "BEGINNER", "WRITE", "Sum of Digits", 4);
            p.setDescription("Read a positive integer n. Find and print the sum of its digits.\n\nExample:\nInput: 1234\nOutput: 10\n\nBecause 1 + 2 + 3 + 4 = 10");
            p.setInputFormat("One positive integer n.");
            p.setOutputFormat("The digit sum.");
            p.setSampleInput("1234");
            p.setSampleOutput("10");
            p.setConstraints("1 <= n <= 10^9");
            p.setHints(List.of(
                "Extract the last digit using n % 10.",
                "Remove the last digit using n = n // 10.",
                "Repeat until n becomes 0."
            ));
            p.setApproach("While n > 0: extract last digit with n % 10 and add to sum. Remove it with n = n / 10. Repeat until n is 0.");
            p.setExplanation("This pattern — extract last digit with %, remove it with / — is used in many number problems: reverse a number, check palindrome, count digits. Learn this pattern well.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    int n, s=0; scanf(\"%d\",&n);\n    while(n>0){s+=n%10; n/=10;}\n    printf(\"%d\\n\",s); return 0;\n}",
                "n = int(input())\ntotal = 0\nwhile n > 0:\n    total += n % 10\n    n //= 10\nprint(total)",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n=new Scanner(System.in).nextInt(), s=0;\n        while(n>0){s+=n%10;n/=10;}\n        System.out.println(s);\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n,s=0; cin>>n;\n    while(n>0){s+=n%10;n/=10;}\n    cout<<s<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("Extract last digit with %, remove it with /, accumulate sum.", "O(digits)", "O(1)", code)));
            all.add(p);
        }

        // ── 5. Reverse a Number ───────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Loops", "Operators"), "Number Problems", "BEGINNER", "WRITE", "Reverse a Number", 5);
            p.setDescription("Read a positive integer. Print it reversed.\n\nExamples:\nInput: 1234  → Output: 4321\nInput: 1200  → Output: 21  (leading zeros dropped)\nInput: 7     → Output: 7");
            p.setInputFormat("One positive integer.");
            p.setOutputFormat("The reversed number (leading zeros removed).");
            p.setSampleInput("1234");
            p.setSampleOutput("4321");
            p.setConstraints("1 <= n <= 10^9");
            p.setHints(List.of(
                "Extract the last digit with n % 10.",
                "Build the reversed number: rev = rev * 10 + last_digit.",
                "Remove the last digit with n //= 10. Stop when n is 0."
            ));
            p.setApproach("Initialize rev = 0. While n > 0: digit = n % 10, rev = rev * 10 + digit, n /= 10. Print rev.");
            p.setExplanation("This uses the same extract-and-remove pattern as sum of digits. Understanding this pattern unlocks palindrome check, count digits, and many other problems.");
            var code = sc(
                "#include <stdio.h>\nint main(){\n    int n,rev=0; scanf(\"%d\",&n);\n    while(n>0){rev=rev*10+n%10;n/=10;}\n    printf(\"%d\\n\",rev); return 0;\n}",
                "n = int(input())\nrev = 0\nwhile n > 0:\n    rev = rev * 10 + n % 10\n    n //= 10\nprint(rev)",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n=new Scanner(System.in).nextInt(),rev=0;\n        while(n>0){rev=rev*10+n%10;n/=10;}\n        System.out.println(rev);\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n,rev=0; cin>>n;\n    while(n>0){rev=rev*10+n%10;n/=10;}\n    cout<<rev<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("Extract digits from the right, build reversed number by multiplying by 10 each time.", "O(digits)", "O(1)", code)));
            all.add(p);
        }

        // ── 6. Palindrome Number ──────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Loops", "Operators"), "Number Problems", "BEGINNER", "WRITE", "Palindrome Number", 6);
            p.setDescription("A palindrome number reads the same forwards and backwards.\n\nExamples:\n121 → Palindrome\n1331 → Palindrome\n123 → Not Palindrome\n\nRead an integer n. Print \"Palindrome\" or \"Not Palindrome\".");
            p.setInputFormat("One positive integer n.");
            p.setOutputFormat("Palindrome or Not Palindrome.");
            p.setSampleInput("121");
            p.setSampleOutput("Palindrome");
            p.setConstraints("1 <= n <= 10^9");
            p.setHints(List.of(
                "Reverse the number (same technique as the previous problem).",
                "If reversed == original, it's a palindrome.",
                "Save the original before reversing because the loop changes n."
            ));
            p.setApproach("Save original = n. Reverse n. If reversed == original, it's a palindrome.");
            p.setExplanation("This combines the reversal technique directly into a check. Notice how problems build on each other — palindrome uses the reverse pattern from the previous problem.");
            var code = sc(
                "#include <stdio.h>\nint main(){\n    int n,orig,rev=0; scanf(\"%d\",&n);\n    orig=n;\n    while(n>0){rev=rev*10+n%10;n/=10;}\n    printf(\"%s\\n\",rev==orig?\"Palindrome\":\"Not Palindrome\");\n    return 0;\n}",
                "n = int(input())\norig = n\nrev = 0\nwhile n > 0:\n    rev = rev * 10 + n % 10\n    n //= 10\nprint(\"Palindrome\" if rev == orig else \"Not Palindrome\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n=new Scanner(System.in).nextInt(),orig=n,rev=0;\n        while(n>0){rev=rev*10+n%10;n/=10;}\n        System.out.println(rev==orig?\"Palindrome\":\"Not Palindrome\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n,orig,rev=0; cin>>n; orig=n;\n    while(n>0){rev=rev*10+n%10;n/=10;}\n    cout<<(rev==orig?\"Palindrome\":\"Not Palindrome\")<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("Reverse the number and compare with original.", "O(digits)", "O(1)", code)));
            all.add(p);
        }

        // ── 7. Factorial ──────────────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Loops", "Recursion"), "Number Problems", "BEGINNER", "WRITE", "Factorial", 7);
            p.setDescription("The factorial of n (written n!) is the product of all integers from 1 to n.\n\n5! = 5 × 4 × 3 × 2 × 1 = 120\n0! = 1 (by definition)\n\nRead n. Print n!.");
            p.setInputFormat("One non-negative integer n.");
            p.setOutputFormat("The factorial of n.");
            p.setSampleInput("5");
            p.setSampleOutput("120");
            p.setConstraints("0 <= n <= 12 (to avoid overflow with int)");
            p.setHints(List.of(
                "Initialize result = 1. Multiply it by each number from 1 to n.",
                "0! is 1 by definition — handle this before the loop.",
                "Recursive approach: factorial(n) = n * factorial(n-1), base case factorial(0) = 1."
            ));
            p.setApproach("Iterative: result = 1, loop i from 1 to n, multiply result by i. Recursive: if n <= 1 return 1, else return n * factorial(n-1).");
            p.setExplanation("Factorial introduces both iteration and recursion. The recursive version is elegant and teaches the key recursion pattern: base case + recursive call. Both are O(n).");
            var iterCode = sc(
                "#include <stdio.h>\nint main(){\n    int n; long long f=1; scanf(\"%d\",&n);\n    for(int i=2;i<=n;i++) f*=i;\n    printf(\"%lld\\n\",f); return 0;\n}",
                "n = int(input())\nresult = 1\nfor i in range(2, n+1):\n    result *= i\nprint(result)",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n=new Scanner(System.in).nextInt();\n        long f=1;\n        for(int i=2;i<=n;i++) f*=i;\n        System.out.println(f);\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n; long long f=1; cin>>n;\n    for(int i=2;i<=n;i++) f*=i;\n    cout<<f<<endl; return 0;\n}"
            );
            var recCode = sc(
                "#include <stdio.h>\nlong long fact(int n){return n<=1?1:n*fact(n-1);}\nint main(){\n    int n; scanf(\"%d\",&n);\n    printf(\"%lld\\n\",fact(n)); return 0;\n}",
                "def factorial(n):\n    if n <= 1:\n        return 1\n    return n * factorial(n - 1)\n\nprint(factorial(int(input())))",
                "import java.util.Scanner;\npublic class Main {\n    static long fact(int n){return n<=1?1:n*fact(n-1);}\n    public static void main(String[] args) {\n        System.out.println(fact(new Scanner(System.in).nextInt()));\n    }\n}",
                "#include <iostream>\nusing namespace std;\nlong long fact(int n){return n<=1?1:n*fact(n-1);}\nint main(){\n    int n; cin>>n;\n    cout<<fact(n)<<endl; return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Iterative loop — multiply all numbers from 2 to n.", "O(n)", "O(1)", iterCode),
                sv("Iterative loop — same but written more explicitly.", "O(n)", "O(1)", iterCode),
                sv("Recursive — elegant and shows the mathematical definition directly.", "O(n)", "O(n)", recCode)
            ));
            all.add(p);
        }

        // ── 8. Fibonacci Series ───────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Loops", "Recursion"), "Number Problems", "INTERMEDIATE", "WRITE", "Fibonacci Series", 8);
            p.setDescription("The Fibonacci sequence: 0 1 1 2 3 5 8 13 21 34 ...\nEach number is the sum of the two before it.\n\nRead n. Print the first n Fibonacci numbers separated by spaces.\n\nExample:\nInput: 7\nOutput: 0 1 1 2 3 5 8");
            p.setInputFormat("One integer n.");
            p.setOutputFormat("First n Fibonacci numbers separated by spaces.");
            p.setSampleInput("7");
            p.setSampleOutput("0 1 1 2 3 5 8");
            p.setConstraints("1 <= n <= 20");
            p.setHints(List.of(
                "Start with a = 0, b = 1. Print a, then generate the next by a, b = b, a+b.",
                "Repeat n times.",
                "A recursive approach is elegant but much slower — O(2^n) vs O(n)."
            ));
            p.setApproach("Two-variable rolling update: a=0, b=1. Each step: print a, then new_b = a+b, a = b, b = new_b. Repeat n times.");
            p.setExplanation("Fibonacci is a classic sequence that appears in nature. The recursive approach is the most intuitive but extremely slow (exponential) — this teaches why iterative solutions are often better.");
            var iterCode = sc(
                "#include <stdio.h>\nint main(){\n    int n; scanf(\"%d\",&n);\n    long long a=0,b=1;\n    for(int i=0;i<n;i++){\n        printf(\"%lld\",a);\n        if(i<n-1) printf(\" \");\n        long long t=a+b; a=b; b=t;\n    }\n    printf(\"\\n\"); return 0;\n}",
                "n = int(input())\na, b = 0, 1\nresult = []\nfor _ in range(n):\n    result.append(a)\n    a, b = b, a + b\nprint(*result)",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n=new Scanner(System.in).nextInt();\n        long a=0,b=1;\n        for(int i=0;i<n;i++){\n            System.out.print(a+(i<n-1?\" \":\"\"));\n            long t=a+b;a=b;b=t;\n        }\n        System.out.println();\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n; cin>>n;\n    long long a=0,b=1;\n    for(int i=0;i<n;i++){\n        cout<<a;if(i<n-1)cout<<\" \";\n        long long t=a+b;a=b;b=t;\n    }\n    cout<<endl; return 0;\n}"
            );
            var recCode = sc(
                "#include <stdio.h>\nlong long fib(int n){return n<=1?n:fib(n-1)+fib(n-2);}\nint main(){\n    int n; scanf(\"%d\",&n);\n    for(int i=0;i<n;i++) printf(\"%lld \",fib(i));\n    printf(\"\\n\"); return 0;\n}",
                "def fib(n):\n    if n <= 1: return n\n    return fib(n-1) + fib(n-2)  # O(2^n) — very slow!\n\nn = int(input())\nprint(*[fib(i) for i in range(n)])",
                "import java.util.Scanner;\npublic class Main {\n    static long fib(int n){return n<=1?n:fib(n-1)+fib(n-2);}\n    public static void main(String[] args) {\n        int n=new Scanner(System.in).nextInt();\n        for(int i=0;i<n;i++) System.out.print(fib(i)+\" \");\n        System.out.println();\n    }\n}",
                "#include <iostream>\nusing namespace std;\nlong long fib(int n){return n<=1?n:fib(n-1)+fib(n-2);}\nint main(){\n    int n; cin>>n;\n    for(int i=0;i<n;i++) cout<<fib(i)<<\" \";\n    cout<<endl; return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Naive recursion — recalculates subproblems repeatedly. O(2^n).", "O(2^n)", "O(n)", recCode),
                sv("Iterative two-variable approach — O(n) and O(1) space.", "O(n)", "O(1)", iterCode),
                sv("Iterative two-variable approach — this IS optimal for generating series.", "O(n)", "O(1)", iterCode)
            ));
            all.add(p);
        }

        // ── 9. Check Prime ────────────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Loops", "Math"), "Number Problems", "INTERMEDIATE", "WRITE", "Check Prime Number", 9);
            p.setDescription("A prime number is a number greater than 1 that is divisible only by 1 and itself.\n\nExamples: 2, 3, 5, 7, 11, 13...\n\nRead n. Print \"Prime\" or \"Not Prime\".");
            p.setInputFormat("One integer n.");
            p.setOutputFormat("Prime or Not Prime.");
            p.setSampleInput("17");
            p.setSampleOutput("Prime");
            p.setConstraints("2 <= n <= 10^6");
            p.setHints(List.of(
                "Check if any number from 2 to n-1 divides n evenly.",
                "Optimization: you only need to check up to sqrt(n). Why? If n = a*b and a > sqrt(n), then b < sqrt(n), so b would have been found first.",
                "Also: skip even numbers after checking 2."
            ));
            p.setApproach("Brute: check all divisors 2 to n-1. Normal: check only 2 to sqrt(n). Optimized: check 2 separately, then only odd numbers up to sqrt(n).");
            p.setExplanation("The sqrt(n) optimization is a key insight. If n has a factor > sqrt(n), its paired factor must be < sqrt(n), so we would find it already. This cuts O(n) to O(sqrt(n)).");
            var bruteCode = sc(
                "#include <stdio.h>\n#include <stdbool.h>\nint main(){\n    int n; scanf(\"%d\",&n);\n    bool prime=true;\n    for(int i=2;i<n;i++) if(n%i==0){prime=false;break;}\n    printf(\"%s\\n\",prime?\"Prime\":\"Not Prime\"); return 0;\n}",
                "n = int(input())\nif n < 2:\n    print(\"Not Prime\")\nelse:\n    prime = all(n % i != 0 for i in range(2, n))  # O(n)\n    print(\"Prime\" if prime else \"Not Prime\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n=new Scanner(System.in).nextInt();\n        boolean p=n>1;\n        for(int i=2;i<n&&p;i++) if(n%i==0) p=false;\n        System.out.println(p?\"Prime\":\"Not Prime\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n; cin>>n;\n    bool p=n>1;\n    for(int i=2;i<n&&p;i++) if(n%i==0) p=false;\n    cout<<(p?\"Prime\":\"Not Prime\")<<endl; return 0;\n}"
            );
            var normalCode = sc(
                "#include <stdio.h>\n#include <math.h>\nint main(){\n    int n; scanf(\"%d\",&n);\n    if(n<2){printf(\"Not Prime\\n\");return 0;}\n    for(int i=2;i<=(int)sqrt(n);i++)\n        if(n%i==0){printf(\"Not Prime\\n\");return 0;}\n    printf(\"Prime\\n\"); return 0;\n}",
                "import math\nn = int(input())\nif n < 2:\n    print(\"Not Prime\")\nelse:\n    prime = all(n % i != 0 for i in range(2, int(math.sqrt(n))+1))\n    print(\"Prime\" if prime else \"Not Prime\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n=new Scanner(System.in).nextInt();\n        if(n<2){System.out.println(\"Not Prime\");return;}\n        for(int i=2;i*i<=n;i++) if(n%i==0){System.out.println(\"Not Prime\");return;}\n        System.out.println(\"Prime\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main(){\n    int n; cin>>n;\n    if(n<2){cout<<\"Not Prime\";return 0;}\n    for(int i=2;i*i<=n;i++) if(n%i==0){cout<<\"Not Prime\\n\";return 0;}\n    cout<<\"Prime\\n\"; return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\nint main(){\n    int n; scanf(\"%d\",&n);\n    if(n<2){printf(\"Not Prime\\n\");return 0;}\n    if(n==2){printf(\"Prime\\n\");return 0;}\n    if(n%2==0){printf(\"Not Prime\\n\");return 0;}\n    for(int i=3;i*i<=n;i+=2) if(n%i==0){printf(\"Not Prime\\n\");return 0;}\n    printf(\"Prime\\n\"); return 0;\n}",
                "def is_prime(n):\n    if n < 2: return False\n    if n == 2: return True\n    if n % 2 == 0: return False\n    for i in range(3, int(n**0.5)+1, 2):  # only odd divisors\n        if n % i == 0: return False\n    return True\n\nprint(\"Prime\" if is_prime(int(input())) else \"Not Prime\")",
                "import java.util.Scanner;\npublic class Main {\n    static boolean isPrime(int n){\n        if(n<2) return false;\n        if(n==2) return true;\n        if(n%2==0) return false;\n        for(int i=3;i*i<=n;i+=2) if(n%i==0) return false;\n        return true;\n    }\n    public static void main(String[] args) {\n        System.out.println(isPrime(new Scanner(System.in).nextInt())?\"Prime\":\"Not Prime\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nbool isPrime(int n){\n    if(n<2)return false; if(n==2)return true;\n    if(n%2==0)return false;\n    for(int i=3;i*i<=n;i+=2) if(n%i==0) return false;\n    return true;\n}\nint main(){int n;cin>>n;cout<<(isPrime(n)?\"Prime\":\"Not Prime\")<<endl;return 0;}"
            );
            p.setSolutions(sols(
                sv("Check all divisors from 2 to n-1. Correct but slow O(n).", "O(n)", "O(1)", bruteCode),
                sv("Check only up to sqrt(n) — if no divisor found, it's prime.", "O(√n)", "O(1)", normalCode),
                sv("Skip even numbers after 2 — halves the work vs normal solve.", "O(√n)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 10. Armstrong Number ──────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Loops", "Math"), "Number Problems", "INTERMEDIATE", "WRITE", "Armstrong Number", 10);
            p.setDescription("An Armstrong number (narcissistic number) is a number equal to the sum of its own digits each raised to the power of the number of digits.\n\nExamples:\n153 = 1³ + 5³ + 3³ = 1+125+27 = 153 ✓\n370 = 3³ + 7³ + 0³ = 27+343+0 = 370 ✓\n9474 = 9⁴ + 4⁴ + 7⁴ + 4⁴ = 9474 ✓\n\nRead n. Print \"Armstrong\" or \"Not Armstrong\".");
            p.setInputFormat("One positive integer n.");
            p.setOutputFormat("Armstrong or Not Armstrong.");
            p.setSampleInput("153");
            p.setSampleOutput("Armstrong");
            p.setConstraints("1 <= n <= 10^6");
            p.setHints(List.of(
                "Count the number of digits in n first.",
                "Extract each digit, raise it to the power of digit count, add to sum.",
                "Compare the sum to the original number."
            ));
            p.setApproach("1. Count digits d = len(str(n)). 2. Extract each digit with % 10. 3. Add digit^d to sum. 4. Compare sum to original n.");
            p.setExplanation("Armstrong numbers combine digit extraction with power calculation. This problem tests whether you can correctly chain two patterns: counting digits and the extract-and-remove loop.");
            var code = sc(
                "#include <stdio.h>\n#include <math.h>\nint main(){\n    int n,orig,d=0,s=0,t; scanf(\"%d\",&n);\n    orig=n; t=n;\n    while(t>0){d++;t/=10;}\n    while(n>0){int digit=n%10;s+=(int)pow(digit,d);n/=10;}\n    printf(\"%s\\n\",s==orig?\"Armstrong\":\"Not Armstrong\");\n    return 0;\n}",
                "n = int(input())\nd = len(str(n))\ntotal = sum(int(digit)**d for digit in str(n))\nprint(\"Armstrong\" if total == n else \"Not Armstrong\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n=new Scanner(System.in).nextInt(),orig=n,d=String.valueOf(n).length(),s=0;\n        while(n>0){int digit=n%10;s+=(int)Math.pow(digit,d);n/=10;}\n        System.out.println(s==orig?\"Armstrong\":\"Not Armstrong\");\n    }\n}",
                "#include <iostream>\n#include <cmath>\n#include <string>\nusing namespace std;\nint main(){\n    int n,orig,s=0; cin>>n; orig=n;\n    int d=to_string(n).size();\n    int t=n;\n    while(t>0){int dg=t%10;s+=pow(dg,d);t/=10;}\n    cout<<(s==orig?\"Armstrong\":\"Not Armstrong\")<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("Count digits, then extract each digit, raise to power of digit count, compare sum to original.", "O(digits)", "O(1)", code)));
            all.add(p);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TRACK 1 — START CODING
    // ═══════════════════════════════════════════════════════════════════════════
    private void seedTrack1(List<ProblemQuestion> all) {
        final List<String> T = List.of("START_CODING");

        // ── 1. Hello World ────────────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Output"), "Basics", "BEGINNER", "WRITE", "Hello World", 1);
            p.setDescription("Write a program that prints exactly:\n\nHello, World!\n\nThis is the very first program every developer writes. It confirms your setup is correct and teaches you how to display text.");
            p.setInputFormat("No input needed.");
            p.setOutputFormat("Print: Hello, World!");
            p.setSampleInput("(none)");
            p.setSampleOutput("Hello, World!");
            p.setConstraints("Print exactly as shown — comma and exclamation mark included.");
            p.setHints(List.of(
                "Every language has a built-in function to print text to the screen.",
                "In Python use print(), in Java System.out.println(), in C printf().",
                "The text goes inside quotes inside the print function."
            ));
            p.setApproach("Call the output function of your language with the string \"Hello, World!\". No logic needed — this is pure syntax learning.");
            p.setExplanation("Hello World is tradition — every programmer's first program. It proves your environment works.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    printf(\"Hello, World!\\n\");\n    return 0;\n}",
                "print(\"Hello, World!\")",
                "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main() {\n    cout << \"Hello, World!\" << endl;\n    return 0;\n}"
            );
            p.setSolutions(same(sv("Call the language's print function with the string.", "O(1)", "O(1)", code)));
            all.add(p);
        }

        // ── 2. Variables and Data Types ───────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Variables"), "Basics", "BEGINNER", "WRITE", "Variables and Data Types", 2);
            p.setDescription("Declare three variables:\n- An integer named age with value 20\n- A decimal number named gpa with value 8.5\n- A string named name with value \"Alex\"\n\nThen print them in one line like:\nName: Alex | Age: 20 | GPA: 8.5\n\nThis teaches you how different types of data are stored in memory.");
            p.setInputFormat("No input needed — values are hardcoded.");
            p.setOutputFormat("Name: Alex | Age: 20 | GPA: 8.5");
            p.setSampleInput("(none)");
            p.setSampleOutput("Name: Alex | Age: 20 | GPA: 8.5");
            p.setConstraints("Use exactly three separate variables. Do not hardcode the output string.");
            p.setHints(List.of(
                "In Python you don't declare a type — just assign: age = 20",
                "In Java/C/C++ you must declare the type first: int age = 20;",
                "To combine variables in output, use string formatting or concatenation."
            ));
            p.setApproach("Declare each variable with the appropriate type. Then use the print function with string formatting to combine them in one line.");
            p.setExplanation("Variables are named storage boxes. int holds whole numbers, float/double holds decimals, String holds text. Every program you ever write will use these three types.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    int age = 20;\n    float gpa = 8.5;\n    char name[] = \"Alex\";\n    printf(\"Name: %s | Age: %d | GPA: %.1f\\n\", name, age, gpa);\n    return 0;\n}",
                "age = 20\ngpa = 8.5\nname = \"Alex\"\nprint(f\"Name: {name} | Age: {age} | GPA: {gpa}\")",
                "public class Main {\n    public static void main(String[] args) {\n        int age = 20;\n        double gpa = 8.5;\n        String name = \"Alex\";\n        System.out.println(\"Name: \" + name + \" | Age: \" + age + \" | GPA: \" + gpa);\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main() {\n    int age = 20;\n    float gpa = 8.5;\n    string name = \"Alex\";\n    cout << \"Name: \" << name << \" | Age: \" << age << \" | GPA: \" << gpa << endl;\n    return 0;\n}"
            );
            p.setSolutions(same(sv("Declare three typed variables and print them with formatting.", "O(1)", "O(1)", code)));
            all.add(p);
        }

        // ── 3. Reading User Input ─────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Input/Output"), "Basics", "BEGINNER", "WRITE", "Reading User Input", 3);
            p.setDescription("Read a person's name from input, then print a greeting:\n\nHello, [name]! Welcome to coding.\n\nExample:\nInput:  Manmadha\nOutput: Hello, Manmadha! Welcome to coding.\n\nThis teaches you how programs interact with users by reading what they type.");
            p.setInputFormat("One line — the user's name.");
            p.setOutputFormat("Hello, [name]! Welcome to coding.");
            p.setSampleInput("Manmadha");
            p.setSampleOutput("Hello, Manmadha! Welcome to coding.");
            p.setConstraints("Name will be a single word, no spaces.");
            p.setHints(List.of(
                "Python uses input() to read from the user. Java uses Scanner.",
                "Store what the user types in a variable.",
                "Then use that variable inside your print statement."
            ));
            p.setApproach("1. Call the input function. 2. Store the result in a variable called name. 3. Print the greeting using that variable.");
            p.setExplanation("Programs are useful because they can react to what the user provides. Reading input is how a program becomes interactive instead of just printing fixed text.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    char name[100];\n    scanf(\"%s\", name);\n    printf(\"Hello, %s! Welcome to coding.\\n\", name);\n    return 0;\n}",
                "name = input()\nprint(f\"Hello, {name}! Welcome to coding.\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        String name = sc.nextLine();\n        System.out.println(\"Hello, \" + name + \"! Welcome to coding.\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main() {\n    string name;\n    cin >> name;\n    cout << \"Hello, \" << name << \"! Welcome to coding.\" << endl;\n    return 0;\n}"
            );
            p.setSolutions(same(sv("Read input into a variable, then use it in the output.", "O(1)", "O(1)", code)));
            all.add(p);
        }

        // ── 4. Arithmetic Operations ──────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Operators"), "Basics", "BEGINNER", "WRITE", "Arithmetic Operations", 4);
            p.setDescription("Read two integers a and b. Print all five arithmetic results:\n\nSum: [a+b]\nDifference: [a-b]\nProduct: [a*b]\nQuotient: [a/b]\nRemainder: [a%b]\n\nExample:\nInput: 10 3\nOutput:\nSum: 13\nDifference: 7\nProduct: 30\nQuotient: 3\nRemainder: 1");
            p.setInputFormat("Two integers a and b on one line.");
            p.setOutputFormat("Five lines showing each operation result.");
            p.setSampleInput("10 3");
            p.setSampleOutput("Sum: 13\nDifference: 7\nProduct: 30\nQuotient: 3\nRemainder: 1");
            p.setConstraints("b != 0 | -1000 <= a,b <= 1000");
            p.setHints(List.of(
                "Use +, -, *, / and % operators.",
                "For integer division in Python 3, use // to get a whole number result.",
                "Print each result on a separate line with the label."
            ));
            p.setApproach("Read two integers. Apply each of the five operators and print the result with the appropriate label.");
            p.setExplanation("These five operators are the foundation of all calculations in programming. The % (modulo) operator is especially important — it gives the remainder and is used in many algorithms to check divisibility.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    int a, b;\n    scanf(\"%d %d\", &a, &b);\n    printf(\"Sum: %d\\nDifference: %d\\nProduct: %d\\nQuotient: %d\\nRemainder: %d\\n\",\n           a+b, a-b, a*b, a/b, a%b);\n    return 0;\n}",
                "a, b = map(int, input().split())\nprint(f\"Sum: {a+b}\")\nprint(f\"Difference: {a-b}\")\nprint(f\"Product: {a*b}\")\nprint(f\"Quotient: {a//b}\")\nprint(f\"Remainder: {a%b}\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int a = sc.nextInt(), b = sc.nextInt();\n        System.out.println(\"Sum: \" + (a+b));\n        System.out.println(\"Difference: \" + (a-b));\n        System.out.println(\"Product: \" + (a*b));\n        System.out.println(\"Quotient: \" + (a/b));\n        System.out.println(\"Remainder: \" + (a%b));\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main() {\n    int a, b; cin >> a >> b;\n    cout << \"Sum: \" << a+b << \"\\nDifference: \" << a-b\n         << \"\\nProduct: \" << a*b << \"\\nQuotient: \" << a/b\n         << \"\\nRemainder: \" << a%b << endl;\n    return 0;\n}"
            );
            p.setSolutions(same(sv("Read two integers and apply all five arithmetic operators.", "O(1)", "O(1)", code)));
            all.add(p);
        }

        // ── 5. If/Else — Positive, Negative, Zero ────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Conditionals"), "Conditionals", "BEGINNER", "WRITE", "Positive, Negative or Zero", 5);
            p.setDescription("Read an integer n. Print whether it is:\n- Positive (n > 0)\n- Negative (n < 0)\n- Zero (n == 0)\n\nExample:\nInput: -5\nOutput: Negative");
            p.setInputFormat("One integer n.");
            p.setOutputFormat("One word: Positive, Negative, or Zero.");
            p.setSampleInput("-5");
            p.setSampleOutput("Negative");
            p.setConstraints("-10^6 <= n <= 10^6");
            p.setHints(List.of(
                "An if/else chain evaluates conditions one by one.",
                "Check n > 0 first, then n < 0, otherwise it must be 0.",
                "Only one block will execute — the first true condition."
            ));
            p.setApproach("Three conditions, three outputs. Use if/elif/else (Python) or if/else if/else (Java, C, C++). The last else handles zero since it's the only remaining case.");
            p.setExplanation("Conditionals let a program make decisions. This is the foundation — real programs have hundreds of conditions that decide what to do based on data.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    int n; scanf(\"%d\", &n);\n    if (n > 0) printf(\"Positive\\n\");\n    else if (n < 0) printf(\"Negative\\n\");\n    else printf(\"Zero\\n\");\n    return 0;\n}",
                "n = int(input())\nif n > 0:\n    print(\"Positive\")\nelif n < 0:\n    print(\"Negative\")\nelse:\n    print(\"Zero\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n = new Scanner(System.in).nextInt();\n        if (n > 0) System.out.println(\"Positive\");\n        else if (n < 0) System.out.println(\"Negative\");\n        else System.out.println(\"Zero\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main() {\n    int n; cin >> n;\n    if (n > 0) cout << \"Positive\";\n    else if (n < 0) cout << \"Negative\";\n    else cout << \"Zero\";\n    cout << endl; return 0;\n}"
            );
            p.setSolutions(same(sv("If/else chain checking three mutually exclusive conditions.", "O(1)", "O(1)", code)));
            all.add(p);
        }

        // ── 6. Grade Calculator ───────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Conditionals"), "Conditionals", "BEGINNER", "WRITE", "Grade Calculator", 6);
            p.setDescription("Read a score (0–100) and print the grade:\n\n90–100 → A\n80–89  → B\n70–79  → C\n60–69  → D\nBelow 60 → F\n\nExample:\nInput: 85\nOutput: B");
            p.setInputFormat("One integer score (0–100).");
            p.setOutputFormat("A single letter grade: A, B, C, D, or F.");
            p.setSampleInput("85");
            p.setSampleOutput("B");
            p.setConstraints("0 <= score <= 100");
            p.setHints(List.of(
                "Check from the highest grade downward.",
                "Use >= to check a lower bound once you have already ruled out higher grades.",
                "The final else catches anything below 60."
            ));
            p.setApproach("Check from 90+ downward. Each elif only runs if the previous checks failed, so you only need the lower bound of each range.");
            p.setExplanation("A chain of elif/else if is cleaner than nested ifs. Because checks run top to bottom, if score >= 80 runs only when we already know score < 90.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    int s; scanf(\"%d\", &s);\n    if (s >= 90) printf(\"A\\n\");\n    else if (s >= 80) printf(\"B\\n\");\n    else if (s >= 70) printf(\"C\\n\");\n    else if (s >= 60) printf(\"D\\n\");\n    else printf(\"F\\n\");\n    return 0;\n}",
                "score = int(input())\nif score >= 90:\n    print(\"A\")\nelif score >= 80:\n    print(\"B\")\nelif score >= 70:\n    print(\"C\")\nelif score >= 60:\n    print(\"D\")\nelse:\n    print(\"F\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int s = new Scanner(System.in).nextInt();\n        if (s >= 90) System.out.println(\"A\");\n        else if (s >= 80) System.out.println(\"B\");\n        else if (s >= 70) System.out.println(\"C\");\n        else if (s >= 60) System.out.println(\"D\");\n        else System.out.println(\"F\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main() {\n    int s; cin >> s;\n    if (s>=90) cout<<\"A\";\n    else if(s>=80) cout<<\"B\";\n    else if(s>=70) cout<<\"C\";\n    else if(s>=60) cout<<\"D\";\n    else cout<<\"F\";\n    cout<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("if/elif chain from highest to lowest range.", "O(1)", "O(1)", code)));
            all.add(p);
        }

        // ── 7. For Loop — Sum 1 to N ──────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Loops"), "Loops", "BEGINNER", "WRITE", "Sum from 1 to N", 7);
            p.setDescription("Read an integer N. Print the sum of all integers from 1 to N.\n\nExample:\nInput: 5\nOutput: 15\n\nBecause 1+2+3+4+5 = 15\n\nThis teaches you how loops repeat an action a fixed number of times.");
            p.setInputFormat("One integer N.");
            p.setOutputFormat("The sum.");
            p.setSampleInput("5");
            p.setSampleOutput("15");
            p.setConstraints("1 <= N <= 10000");
            p.setHints(List.of(
                "Create a variable total = 0 before the loop.",
                "Loop from 1 to N (inclusive) and add each number to total.",
                "Print total after the loop ends."
            ));
            p.setApproach("Initialize total = 0. Use a for loop from 1 to N. Each iteration: total = total + i. After the loop, print total.");
            p.setExplanation("The loop approach is O(n). There is also a formula: sum = N*(N+1)/2 which gives the same result instantly. Both are valid — the loop teaches the concept, the formula teaches optimization.");
            var bruteCode = sc(
                "#include <stdio.h>\nint main() {\n    int n, total = 0;\n    scanf(\"%d\", &n);\n    for (int i = 1; i <= n; i++)\n        total += i;\n    printf(\"%d\\n\", total);\n    return 0;\n}",
                "n = int(input())\ntotal = 0\nfor i in range(1, n+1):\n    total += i\nprint(total)",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n = new Scanner(System.in).nextInt();\n        int total = 0;\n        for (int i = 1; i <= n; i++) total += i;\n        System.out.println(total);\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main() {\n    int n, total=0; cin>>n;\n    for(int i=1;i<=n;i++) total+=i;\n    cout<<total<<endl; return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\nint main() {\n    long long n; scanf(\"%lld\", &n);\n    printf(\"%lld\\n\", n*(n+1)/2);\n    return 0;\n}",
                "n = int(input())\nprint(n * (n + 1) // 2)  # Gauss formula — O(1)",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        long n = new Scanner(System.in).nextLong();\n        System.out.println(n * (n + 1) / 2);\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main() {\n    long long n; cin>>n;\n    cout << n*(n+1)/2 << endl; return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Loop from 1 to N, add each number to a running total.", "O(n)", "O(1)", bruteCode),
                sv("Loop from 1 to N, add each number to a running total.", "O(n)", "O(1)", bruteCode),
                sv("Gauss formula: N*(N+1)/2. One calculation, no loop needed.", "O(1)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 8. While Loop — Count Down ────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Loops"), "Loops", "BEGINNER", "WRITE", "Count Down", 8);
            p.setDescription("Read an integer N. Print a countdown from N down to 1, then print \"Go!\".\n\nExample:\nInput: 5\nOutput:\n5\n4\n3\n2\n1\nGo!");
            p.setInputFormat("One integer N.");
            p.setOutputFormat("N to 1 on separate lines, then Go!");
            p.setSampleInput("5");
            p.setSampleOutput("5\n4\n3\n2\n1\nGo!");
            p.setConstraints("1 <= N <= 100");
            p.setHints(List.of(
                "A while loop keeps running as long as a condition is true.",
                "Start with count = N. Decrease count by 1 each iteration.",
                "Stop when count reaches 0. Print Go! after the loop."
            ));
            p.setApproach("Initialize count = N. While count >= 1: print count, then count = count - 1. After the loop, print Go!");
            p.setExplanation("While loops are used when you don't know in advance how many times to loop, or when looping until a condition changes. For loops are used when the count is known.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    int n; scanf(\"%d\", &n);\n    while (n >= 1) {\n        printf(\"%d\\n\", n);\n        n--;\n    }\n    printf(\"Go!\\n\");\n    return 0;\n}",
                "n = int(input())\nwhile n >= 1:\n    print(n)\n    n -= 1\nprint(\"Go!\")",
                "import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        int n = new Scanner(System.in).nextInt();\n        while (n >= 1) { System.out.println(n); n--; }\n        System.out.println(\"Go!\");\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint main() {\n    int n; cin>>n;\n    while(n>=1){cout<<n<<\"\\n\";n--;}\n    cout<<\"Go!\"<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("Decrement counter each iteration, print Go! after loop exits.", "O(n)", "O(1)", code)));
            all.add(p);
        }

        // ── 9. Functions — Rectangle Area ─────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Functions"), "Functions", "BEGINNER", "WRITE", "Rectangle Area", 9);
            p.setDescription("Write a function called area that takes length and width as parameters and returns their product (the area of a rectangle).\n\nRead length and width from input. Call area() and print the result.\n\nExample:\nInput: 6 4\nOutput: Area = 24");
            p.setInputFormat("Two integers: length and width.");
            p.setOutputFormat("Area = [result]");
            p.setSampleInput("6 4");
            p.setSampleOutput("Area = 24");
            p.setConstraints("1 <= length, width <= 1000");
            p.setHints(List.of(
                "A function is defined with a name, parameters, and a body.",
                "Use the return keyword to send a value back to the caller.",
                "Call the function with the two values and store the result."
            ));
            p.setApproach("Define area(length, width) that returns length * width. Read two numbers, call area(), print the result with the label.");
            p.setExplanation("Functions let you package logic with a name and reuse it. Instead of copying code, you call a function. This is the single most important concept in programming.");
            var code = sc(
                "#include <stdio.h>\nint area(int l, int w) {\n    return l * w;\n}\nint main() {\n    int l, w;\n    scanf(\"%d %d\", &l, &w);\n    printf(\"Area = %d\\n\", area(l, w));\n    return 0;\n}",
                "def area(length, width):\n    return length * width\n\nl, w = map(int, input().split())\nprint(f\"Area = {area(l, w)}\")",
                "import java.util.Scanner;\npublic class Main {\n    static int area(int l, int w) { return l * w; }\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int l = sc.nextInt(), w = sc.nextInt();\n        System.out.println(\"Area = \" + area(l, w));\n    }\n}",
                "#include <iostream>\nusing namespace std;\nint area(int l, int w){return l*w;}\nint main(){\n    int l,w; cin>>l>>w;\n    cout<<\"Area = \"<<area(l,w)<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("Define a function that multiplies two parameters and returns the result.", "O(1)", "O(1)", code)));
            all.add(p);
        }

        // ── 10. List Basics ───────────────────────────────────────────────────
        {
            ProblemQuestion p = base(T, List.of("Arrays"), "Collections", "BEGINNER", "WRITE", "List Basics", 10);
            p.setDescription("Read N integers from the user. Store them in a list/array. Then:\n1. Print the first element\n2. Print the last element\n3. Print the total count\n4. Print all elements separated by spaces\n\nExample:\nInput:\n5\n10 20 30 40 50\nOutput:\nFirst: 10\nLast: 50\nCount: 5\nAll: 10 20 30 40 50");
            p.setInputFormat("First line: N\nSecond line: N space-separated integers");
            p.setOutputFormat("Four lines as shown.");
            p.setSampleInput("5\n10 20 30 40 50");
            p.setSampleOutput("First: 10\nLast: 50\nCount: 5\nAll: 10 20 30 40 50");
            p.setConstraints("1 <= N <= 100");
            p.setHints(List.of(
                "Store all values in a list/array after reading them.",
                "The first element is at index 0, last is at index N-1 (or -1 in Python).",
                "Loop through all elements to print them on one line."
            ));
            p.setApproach("Read N, then read N integers into a list. Access [0] for first, [-1]/[n-1] for last, len() for count, then loop to print all.");
            p.setExplanation("Lists/arrays let you store many values under one name and access each by index. Index 0 is first, index n-1 is last. This is the foundation for all data structure problems.");
            var code = sc(
                "#include <stdio.h>\nint main() {\n    int n; scanf(\"%d\", &n);\n    int arr[n];\n    for(int i=0;i<n;i++) scanf(\"%d\",&arr[i]);\n    printf(\"First: %d\\nLast: %d\\nCount: %d\\nAll:\",arr[0],arr[n-1],n);\n    for(int i=0;i<n;i++) printf(\" %d\",arr[i]);\n    printf(\"\\n\"); return 0;\n}",
                "n = int(input())\narr = list(map(int, input().split()))\nprint(f\"First: {arr[0]}\")\nprint(f\"Last: {arr[-1]}\")\nprint(f\"Count: {len(arr)}\")\nprint(\"All:\", *arr)",
                "import java.util.*;\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.nextInt();\n        int[] arr = new int[n];\n        for(int i=0;i<n;i++) arr[i]=sc.nextInt();\n        System.out.println(\"First: \"+arr[0]);\n        System.out.println(\"Last: \"+arr[n-1]);\n        System.out.println(\"Count: \"+n);\n        System.out.print(\"All:\");\n        for(int x:arr) System.out.print(\" \"+x);\n        System.out.println();\n    }\n}",
                "#include <iostream>\n#include <vector>\nusing namespace std;\nint main(){\n    int n; cin>>n;\n    vector<int> arr(n);\n    for(auto& x:arr) cin>>x;\n    cout<<\"First: \"<<arr[0]<<\"\\nLast: \"<<arr[n-1]<<\"\\nCount: \"<<n<<\"\\nAll:\";\n    for(auto x:arr) cout<<\" \"<<x;\n    cout<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("Store in array, access by index for first/last, loop to print all.", "O(n)", "O(n)", code)));
            all.add(p);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TRACK 3 (SKILL_UP) + TRACK 4 (INTERVIEW_PREP) — shared and unique
    // ═══════════════════════════════════════════════════════════════════════════
    private void seedSkillUpAndInterview(List<ProblemQuestion> all) {
        final List<String> S  = List.of("SKILL_UP");
        final List<String> SI = List.of("SKILL_UP","INTERVIEW_PREP");
        final List<String> I  = List.of("INTERVIEW_PREP");

        // ── 1. Find Maximum ───────────────────────────────────────────────────
        {
            ProblemQuestion p = base(S, List.of("Arrays"), "Data Structures", "BEGINNER", "WRITE", "Find the Maximum Element", 1);
            p.setDescription("Given an array of integers, return the largest element.\n\nExample:\nInput:  [3, 1, 4, 1, 5, 9, 2, 6]\nOutput: 9");
            p.setInputFormat("Array of n integers.");
            p.setOutputFormat("The maximum element.");
            p.setSampleInput("[3, 1, 4, 1, 5, 9, 2, 6]");
            p.setSampleOutput("9");
            p.setConstraints("1 <= n <= 10^5 | -10^9 <= arr[i] <= 10^9");
            p.setHints(List.of(
                "Assume the first element is the maximum.",
                "Walk through the array. If any element is larger, update your max.",
                "Return max after the full traversal."
            ));
            p.setApproach("Initialize max = arr[0]. Single pass: for each element, if element > max, update max. Return max.");
            p.setExplanation("The brute force compares every pair — completely unnecessary. One pass is enough because you only need the largest value, not a sorted order.");
            var bruteCode = sc(
                "#include <stdio.h>\nint main(){\n    int arr[]={3,1,4,1,5,9,2,6},n=8,max=arr[0];\n    for(int i=0;i<n;i++)\n        for(int j=0;j<n;j++)\n            if(arr[j]>max) max=arr[j];\n    printf(\"%d\\n\",max); return 0;\n}",
                "arr=[3,1,4,1,5,9,2,6]\nmax_val=arr[0]\nfor i in arr:\n    for j in arr:         # O(n^2) — unnecessary inner loop\n        if j>max_val: max_val=j\nprint(max_val)",
                "public class Main{\n    public static void main(String[] a){\n        int[]arr={3,1,4,1,5,9,2,6};int max=arr[0];\n        for(int i=0;i<arr.length;i++)\n            for(int j=0;j<arr.length;j++)\n                if(arr[j]>max) max=arr[j];\n        System.out.println(max);\n    }\n}",
                "#include<iostream>\n#include<vector>\nusing namespace std;\nint main(){\n    vector<int>arr={3,1,4,1,5,9,2,6};int max=arr[0];\n    for(int i=0;i<arr.size();i++)\n        for(int j=0;j<arr.size();j++)\n            if(arr[j]>max) max=arr[j];\n    cout<<max; return 0;\n}"
            );
            var normalCode = sc(
                "#include <stdio.h>\nint main(){\n    int arr[]={3,1,4,1,5,9,2,6},n=8,max=arr[0];\n    for(int i=1;i<n;i++) if(arr[i]>max) max=arr[i];\n    printf(\"%d\\n\",max); return 0;\n}",
                "arr=[3,1,4,1,5,9,2,6]\nmax_val=arr[0]\nfor num in arr:\n    if num>max_val: max_val=num\nprint(max_val)",
                "public class Main{\n    public static void main(String[] a){\n        int[]arr={3,1,4,1,5,9,2,6};int max=arr[0];\n        for(int i=1;i<arr.length;i++) if(arr[i]>max) max=arr[i];\n        System.out.println(max);\n    }\n}",
                "#include<iostream>\n#include<vector>\n#include<algorithm>\nusing namespace std;\nint main(){\n    vector<int>arr={3,1,4,1,5,9,2,6};\n    int max=arr[0];\n    for(int x:arr) if(x>max) max=x;\n    cout<<max; return 0;\n}"
            );
            var optCode = sc(normalCode.getC(),
                "arr=[3,1,4,1,5,9,2,6]\nprint(max(arr))  # built-in max — O(n) internally",
                "import java.util.Arrays;\npublic class Main{\n    public static void main(String[] a){\n        int[]arr={3,1,4,1,5,9,2,6};\n        System.out.println(Arrays.stream(arr).max().getAsInt());\n    }\n}",
                "#include<iostream>\n#include<vector>\n#include<algorithm>\nusing namespace std;\nint main(){\n    vector<int>arr={3,1,4,1,5,9,2,6};\n    cout<<*max_element(arr.begin(),arr.end()); return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Double loop — redundant, O(n²).", "O(n²)", "O(1)", bruteCode),
                sv("Single pass tracking current max.", "O(n)", "O(1)", normalCode),
                sv("Use built-in max — same O(n) but idiomatic.", "O(n)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 2. Find Minimum ───────────────────────────────────────────────────
        {
            ProblemQuestion p = base(S, List.of("Arrays"), "Data Structures", "BEGINNER", "WRITE", "Find the Minimum Element", 2);
            p.setDescription("Given an array, return the smallest element.\n\nExample:\nInput:  [8, 3, 1, 7, 4]\nOutput: 1");
            p.setInputFormat("Array of n integers.");
            p.setOutputFormat("The minimum element.");
            p.setSampleInput("[8, 3, 1, 7, 4]");
            p.setSampleOutput("1");
            p.setConstraints("1 <= n <= 10^5");
            p.setHints(List.of("Assume first element is minimum.", "If any element is smaller, update your minimum.", "Return min after full traversal."));
            p.setApproach("Same pattern as Find Maximum — initialize min = arr[0], single pass comparing each element.");
            p.setExplanation("Identical logic to Find Maximum — just flip the comparison from > to <. These two together are among the most common array operations.");
            var code = sc(
                "#include <stdio.h>\nint main(){\n    int arr[]={8,3,1,7,4},n=5,min=arr[0];\n    for(int i=1;i<n;i++) if(arr[i]<min) min=arr[i];\n    printf(\"%d\\n\",min); return 0;\n}",
                "arr=[8,3,1,7,4]\nprint(min(arr))",
                "import java.util.Arrays;\npublic class Main{\n    public static void main(String[] a){\n        int[]arr={8,3,1,7,4};\n        System.out.println(Arrays.stream(arr).min().getAsInt());\n    }\n}",
                "#include<iostream>\n#include<vector>\n#include<algorithm>\nusing namespace std;\nint main(){\n    vector<int>v={8,3,1,7,4};\n    cout<<*min_element(v.begin(),v.end()); return 0;\n}"
            );
            p.setSolutions(same(sv("Single pass tracking current minimum.", "O(n)", "O(1)", code)));
            all.add(p);
        }

        // ── 3. Linear Search ──────────────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Arrays", "Searching"), "Data Structures", "BEGINNER", "WRITE", "Linear Search", 3);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("TCS","Infosys","Wipro"));
            p.setDescription("Given an array and a target value, return the index of the first occurrence of target. Return -1 if not found.\n\nExample:\nInput:  arr=[10,20,30,40,50], target=30\nOutput: 2");
            p.setInputFormat("Array of n integers, then a target.");
            p.setOutputFormat("Index (0-based) or -1.");
            p.setSampleInput("[10,20,30,40,50], target=30");
            p.setSampleOutput("2");
            p.setConstraints("1 <= n <= 10^5");
            p.setHints(List.of(
                "Walk through each element from index 0.",
                "If arr[i] == target, return i immediately.",
                "If loop completes without finding it, return -1."
            ));
            p.setApproach("Walk left to right. Return the index as soon as you find the target. If you reach the end, return -1.");
            p.setExplanation("Linear search works on any array — sorted or unsorted. For sorted arrays, binary search is much faster. Understanding WHEN to use each is what interviews test.");
            p.setInterviewTip("Follow-up questions: 'What if the array is sorted?' → use binary search. 'What if there are duplicates?' → return first or all occurrences.");
            var code = sc(
                "#include <stdio.h>\nint search(int*a,int n,int t){\n    for(int i=0;i<n;i++) if(a[i]==t) return i;\n    return -1;\n}\nint main(){\n    int arr[]={10,20,30,40,50},n=5,t=30;\n    printf(\"%d\\n\",search(arr,n,t)); return 0;\n}",
                "def linear_search(arr, target):\n    for i, val in enumerate(arr):\n        if val == target:\n            return i\n    return -1\n\nprint(linear_search([10,20,30,40,50], 30))",
                "public class Main{\n    static int search(int[]a,int t){\n        for(int i=0;i<a.length;i++) if(a[i]==t) return i;\n        return -1;\n    }\n    public static void main(String[] x){\n        System.out.println(search(new int[]{10,20,30,40,50},30));\n    }\n}",
                "#include<iostream>\n#include<vector>\nusing namespace std;\nint main(){\n    vector<int>v={10,20,30,40,50};\n    int t=30;\n    for(int i=0;i<v.size();i++) if(v[i]==t){cout<<i;return 0;}\n    cout<<-1; return 0;\n}"
            );
            p.setSolutions(same(sv("Walk each element and return index on match, -1 if not found.", "O(n)", "O(1)", code)));
            all.add(p);
        }

        // ── 4. Second Largest Element ─────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Arrays"), "Data Structures", "INTERMEDIATE", "WRITE", "Second Largest Element", 4);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("Amazon","TCS","Infosys","Capgemini"));
            p.setDescription("Find the second largest element in an array.\n\nExample:\nInput:  [12, 35, 1, 10, 34, 1]\nOutput: 34\n\nNote: All elements may not be distinct.");
            p.setInputFormat("Array of n integers.");
            p.setOutputFormat("The second largest distinct value.");
            p.setSampleInput("[12, 35, 1, 10, 34, 1]");
            p.setSampleOutput("34");
            p.setConstraints("n >= 2 | at least two distinct values exist");
            p.setHints(List.of(
                "Brute: sort and return the second unique value from the end.",
                "Better: single pass tracking both first and second maximums.",
                "Update: if new element > max → second=max, max=new. Else if > second and != max → second=new."
            ));
            p.setApproach("Track two variables: max1 and max2. Walk the array. If element > max1: max2=max1, max1=element. Else if element > max2 and element != max1: max2=element.");
            p.setExplanation("The single-pass O(n) solution avoids sorting entirely. It directly tracks the top two values, updating them as it goes. This is the expected answer in interviews.");
            p.setInterviewTip("Common mistake: using INT_MIN for initial values without handling the case where all elements are equal. Clarify constraints before coding.");
            var bruteCode = sc(
                "#include <stdio.h>\n#include <stdlib.h>\nint cmp(const void*a,const void*b){return *(int*)b-*(int*)a;}\nint main(){\n    int arr[]={12,35,1,10,34,1},n=6;\n    qsort(arr,n,sizeof(int),cmp);\n    for(int i=1;i<n;i++) if(arr[i]!=arr[0]){printf(\"%d\\n\",arr[i]);return 0;}\n    return 0;\n}",
                "arr=[12,35,1,10,34,1]\nuniq=sorted(set(arr),reverse=True)\nprint(uniq[1])  # O(n log n)",
                "import java.util.*;\npublic class Main{\n    public static void main(String[] a){\n        int[]arr={12,35,1,10,34,1};\n        TreeSet<Integer>s=new TreeSet<>(Arrays.stream(arr).boxed().collect(java.util.stream.Collectors.toList()));\n        int max=s.pollLast();\n        System.out.println(s.last());\n    }\n}",
                "#include<iostream>\n#include<vector>\n#include<algorithm>\n#include<set>\nusing namespace std;\nint main(){\n    vector<int>v={12,35,1,10,34,1};\n    set<int>s(v.begin(),v.end());\n    auto it=s.end();--it;--it;\n    cout<<*it; return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\n#include <limits.h>\nint main(){\n    int arr[]={12,35,1,10,34,1},n=6;\n    int m1=INT_MIN,m2=INT_MIN;\n    for(int i=0;i<n;i++){\n        if(arr[i]>m1){m2=m1;m1=arr[i];}\n        else if(arr[i]>m2&&arr[i]!=m1) m2=arr[i];\n    }\n    printf(\"%d\\n\",m2); return 0;\n}",
                "arr=[12,35,1,10,34,1]\nm1=m2=float('-inf')\nfor x in arr:\n    if x>m1: m2,m1=m1,x\n    elif x>m2 and x!=m1: m2=x\nprint(m2)  # O(n) single pass",
                "public class Main{\n    public static void main(String[] a){\n        int[]arr={12,35,1,10,34,1};\n        int m1=Integer.MIN_VALUE,m2=Integer.MIN_VALUE;\n        for(int x:arr){\n            if(x>m1){m2=m1;m1=x;}\n            else if(x>m2&&x!=m1) m2=x;\n        }\n        System.out.println(m2);\n    }\n}",
                "#include<iostream>\n#include<vector>\n#include<climits>\nusing namespace std;\nint main(){\n    vector<int>v={12,35,1,10,34,1};\n    int m1=INT_MIN,m2=INT_MIN;\n    for(int x:v){\n        if(x>m1){m2=m1;m1=x;}\n        else if(x>m2&&x!=m1) m2=x;\n    }\n    cout<<m2; return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Sort descending, return first element different from maximum. O(n log n).", "O(n log n)", "O(1)", bruteCode),
                sv("Single pass with two variables tracking top two distinct values.", "O(n)", "O(1)", optCode),
                sv("Single pass — this is already optimal.", "O(n)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 5. Move Zeros to End ──────────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Arrays", "Two Pointers"), "Data Structures", "INTERMEDIATE", "WRITE", "Move All Zeros to End", 5);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("Microsoft","Amazon","Flipkart"));
            p.setDescription("Given an array, move all zeros to the end while maintaining the relative order of non-zero elements. Do it in place.\n\nExample:\nInput:  [0, 1, 0, 3, 12]\nOutput: [1, 3, 12, 0, 0]");
            p.setInputFormat("Array of n integers.");
            p.setOutputFormat("Modified array — non-zeros first, zeros at end.");
            p.setSampleInput("[0, 1, 0, 3, 12]");
            p.setSampleOutput("[1, 3, 12, 0, 0]");
            p.setConstraints("1 <= n <= 10^5 | In place, preserve relative order.");
            p.setHints(List.of(
                "Use a pointer that tracks the next position to place a non-zero element.",
                "First pass: copy all non-zeros to the front.",
                "Second pass: fill remaining positions with zeros."
            ));
            p.setApproach("Two-pointer: pos = 0. Walk array — when arr[i] != 0, place it at arr[pos] and increment pos. After loop, fill arr[pos] to end with zeros.");
            p.setExplanation("The two-pointer approach avoids O(n²) shifting. By tracking where non-zeros go, you get O(n) time and O(1) extra space — what interviewers want.");
            p.setInterviewTip("Key constraint: maintain relative order. A common wrong answer is to swap zeros to the end with swapping — this can change relative order of non-zero elements.");
            var bruteCode = sc(
                "#include <stdio.h>\nint main(){\n    int a[]={0,1,0,3,12},n=5,tmp;\n    for(int i=0;i<n;i++)\n        for(int j=0;j<n-1;j++)\n            if(a[j]==0){tmp=a[j];a[j]=a[j+1];a[j+1]=tmp;}\n    for(int i=0;i<n;i++) printf(\"%d \",a[i]); return 0;\n}",
                "arr=[0,1,0,3,12]\n# Bubble zeros to end — O(n^2)\nfor i in range(len(arr)):\n    for j in range(len(arr)-1):\n        if arr[j]==0: arr[j],arr[j+1]=arr[j+1],arr[j]\nprint(arr)",
                "import java.util.Arrays;\npublic class Main{\n    public static void main(String[] a){\n        int[]arr={0,1,0,3,12};\n        for(int i=0;i<arr.length;i++)\n            for(int j=0;j<arr.length-1;j++)\n                if(arr[j]==0){int t=arr[j];arr[j]=arr[j+1];arr[j+1]=t;}\n        System.out.println(Arrays.toString(arr));\n    }\n}",
                "#include<iostream>\n#include<vector>\nusing namespace std;\nint main(){\n    vector<int>v={0,1,0,3,12};\n    for(int i=0;i<v.size();i++)\n        for(int j=0;j<v.size()-1;j++)\n            if(v[j]==0) swap(v[j],v[j+1]);\n    for(int x:v) cout<<x<<\" \"; return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\nint main(){\n    int a[]={0,1,0,3,12},n=5,pos=0;\n    for(int i=0;i<n;i++) if(a[i]!=0) a[pos++]=a[i];\n    while(pos<n) a[pos++]=0;\n    for(int i=0;i<n;i++) printf(\"%d \",a[i]); return 0;\n}",
                "arr=[0,1,0,3,12]\npos=0\nfor x in arr:\n    if x!=0: arr[pos]=x; pos+=1\nwhile pos<len(arr): arr[pos]=0; pos+=1\nprint(arr)",
                "import java.util.Arrays;\npublic class Main{\n    public static void main(String[] a){\n        int[]arr={0,1,0,3,12};int pos=0;\n        for(int x:arr) if(x!=0) arr[pos++]=x;\n        while(pos<arr.length) arr[pos++]=0;\n        System.out.println(Arrays.toString(arr));\n    }\n}",
                "#include<iostream>\n#include<vector>\nusing namespace std;\nint main(){\n    vector<int>v={0,1,0,3,12};int pos=0;\n    for(int x:v) if(x!=0) v[pos++]=x;\n    while(pos<v.size()) v[pos++]=0;\n    for(int x:v) cout<<x<<\" \"; return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Bubble zeros toward end — stable but O(n²).", "O(n²)", "O(1)", bruteCode),
                sv("Two-pass: first copy non-zeros forward, then fill with zeros.", "O(n)", "O(1)", optCode),
                sv("Same two-pass approach — already optimal.", "O(n)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 6. Missing Number ─────────────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Arrays", "Math"), "Data Structures", "INTERMEDIATE", "WRITE", "Missing Number", 6);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("Amazon","Google","Microsoft","Flipkart"));
            p.setDescription("Given an array of n distinct integers in range [0, n], find the one missing number.\n\nExample:\nInput:  [3, 0, 1]\nOutput: 2\n\nThe array contains 0,1,3 — so 2 is missing.");
            p.setInputFormat("Array of n integers containing values from 0 to n, one value missing.");
            p.setOutputFormat("The missing integer.");
            p.setSampleInput("[3, 0, 1]");
            p.setSampleOutput("2");
            p.setConstraints("n unique integers from [0,n], exactly one missing.");
            p.setHints(List.of(
                "Brute: check which number from 0 to n is not in the array.",
                "Math trick: expected sum of 0 to n is n*(n+1)/2. Subtract actual sum.",
                "XOR trick: XOR all indices and all values — duplicates cancel out."
            ));
            p.setApproach("Expected sum = n*(n+1)/2. Actual sum = sum of array elements. Missing = expected - actual. No sorting needed.");
            p.setExplanation("The math sum trick is elegant: the expected total minus actual total gives exactly the missing value. The XOR trick works too but the sum approach is more intuitive.");
            p.setInterviewTip("Interviewers may ask for three approaches: O(n log n) sort-and-scan, O(n) hash set, O(n) math. Know all three. The math approach is the most impressive.");
            var bruteCode = sc(
                "#include <stdio.h>\n#include <stdbool.h>\nint main(){\n    int arr[]={3,0,1},n=3;\n    for(int i=0;i<=n;i++){\n        bool found=false;\n        for(int j=0;j<n;j++) if(arr[j]==i){found=true;break;}\n        if(!found){printf(\"%d\\n\",i);return 0;}\n    }\n    return 0;\n}",
                "arr=[3,0,1]\nn=len(arr)\nfor i in range(n+1):\n    if i not in arr:  # O(n^2) — 'in' is O(n) for lists\n        print(i)\n        break",
                "public class Main{\n    public static void main(String[] a){\n        int[]arr={3,0,1},n=arr.length;\n        outer: for(int i=0;i<=n;i++){\n            for(int j=0;j<n;j++) if(arr[j]==i) continue outer;\n            System.out.println(i);return;\n        }\n    }\n}",
                "#include<iostream>\n#include<vector>\nusing namespace std;\nint main(){\n    vector<int>v={3,0,1};int n=v.size();\n    for(int i=0;i<=n;i++){\n        bool f=false;\n        for(int x:v) if(x==i){f=true;break;}\n        if(!f){cout<<i;return 0;}\n    }\n    return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\nint main(){\n    int arr[]={3,0,1},n=3;\n    long expected=(long)n*(n+1)/2,actual=0;\n    for(int i=0;i<n;i++) actual+=arr[i];\n    printf(\"%ld\\n\",expected-actual); return 0;\n}",
                "arr=[3,0,1]\nn=len(arr)\nprint(n*(n+1)//2 - sum(arr))  # Gauss formula minus actual sum",
                "public class Main{\n    public static void main(String[] a){\n        int[]arr={3,0,1},n=arr.length;\n        int sum=0; for(int x:arr) sum+=x;\n        System.out.println(n*(n+1)/2-sum);\n    }\n}",
                "#include<iostream>\n#include<vector>\nusing namespace std;\nint main(){\n    vector<int>v={3,0,1};int n=v.size(),s=0;\n    for(int x:v) s+=x;\n    cout<<n*(n+1)/2-s; return 0;\n}"
            );
            p.setSolutions(sols(
                sv("For each number 0..n check if it's in the array. O(n²).", "O(n²)", "O(1)", bruteCode),
                sv("Expected sum minus actual sum = missing value. One formula, one loop.", "O(n)", "O(1)", optCode),
                sv("Same math formula — already the best possible approach.", "O(n)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 7. Two Sum ────────────────────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Arrays","Hash Map"), "Algorithms", "INTERMEDIATE", "WRITE", "Two Sum", 7);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("Amazon","Google","Microsoft","Flipkart","Goldman Sachs"));
            p.setDescription("Given an array of integers and a target, return the indices of two numbers that add up to target. Exactly one solution exists.\n\nExample:\nInput:  nums=[2,7,11,15], target=9\nOutput: [0,1]\n\nBecause nums[0]+nums[1] = 2+7 = 9");
            p.setInputFormat("Array of integers + target value.");
            p.setOutputFormat("[i, j] — two indices.");
            p.setSampleInput("nums=[2,7,11,15], target=9");
            p.setSampleOutput("[0, 1]");
            p.setConstraints("2 <= n <= 10^4 | Exactly one solution | No same element twice.");
            p.setHints(List.of(
                "Brute: for each pair (i,j) check if they sum to target.",
                "For each element, its complement is target - element.",
                "Store visited elements in a hash map. For each new element, check if its complement is already in the map."
            ));
            p.setApproach("Hash map: as you iterate, for each nums[i] check if (target - nums[i]) exists in the map. If yes, return both indices. Else, store nums[i] → i.");
            p.setExplanation("This is the most commonly asked interview question ever. The O(n²) answer fails interviews. You must know the O(n) hash map solution and be able to explain why it works.");
            p.setInterviewTip("Always clarify: Can I use extra space? (Yes → hash map). What if no solution? What if duplicates? These follow-ups show senior-level thinking.");
            var bruteCode = sc(
                "#include <stdio.h>\nint main(){\n    int nums[]={2,7,11,15},n=4,t=9;\n    for(int i=0;i<n;i++)\n        for(int j=i+1;j<n;j++)\n            if(nums[i]+nums[j]==t)\n                {printf(\"[%d,%d]\\n\",i,j);return 0;}\n    return 0;\n}",
                "def two_sum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i+1, len(nums)):\n            if nums[i]+nums[j]==target:\n                return [i,j]  # O(n^2)\n    return []\nprint(two_sum([2,7,11,15],9))",
                "public class Main{\n    public static int[] twoSum(int[]nums,int t){\n        for(int i=0;i<nums.length;i++)\n            for(int j=i+1;j<nums.length;j++)\n                if(nums[i]+nums[j]==t) return new int[]{i,j};\n        return new int[]{};\n    }\n    public static void main(String[] a){\n        int[]r=twoSum(new int[]{2,7,11,15},9);\n        System.out.println(\"[\"+r[0]+\",\"+r[1]+\"]\");\n    }\n}",
                "#include<iostream>\n#include<vector>\nusing namespace std;\nint main(){\n    vector<int>v={2,7,11,15};int t=9;\n    for(int i=0;i<v.size();i++)\n        for(int j=i+1;j<v.size();j++)\n            if(v[i]+v[j]==t){cout<<\"[\"<<i<<\",\"<<j<<\"]\";return 0;}\n    return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\n#include <stdlib.h>\n// Using offset map for simplicity\nint main(){\n    int nums[]={2,7,11,15},n=4,t=9;\n    int map[20001]={0};\n    for(int i=0;i<n;i++){\n        int comp=t-nums[i];\n        if(map[comp+10000]){\n            printf(\"[%d,%d]\\n\",map[comp+10000]-1,i);return 0;\n        }\n        map[nums[i]+10000]=i+1;\n    }\n    return 0;\n}",
                "def two_sum(nums, target):\n    seen={}\n    for i,num in enumerate(nums):\n        comp=target-num\n        if comp in seen:\n            return [seen[comp],i]  # O(n)\n        seen[num]=i\n    return []\nprint(two_sum([2,7,11,15],9))",
                "import java.util.*;\npublic class Main{\n    public static int[] twoSum(int[]nums,int t){\n        Map<Integer,Integer>map=new HashMap<>();\n        for(int i=0;i<nums.length;i++){\n            int comp=t-nums[i];\n            if(map.containsKey(comp)) return new int[]{map.get(comp),i};\n            map.put(nums[i],i);\n        }\n        return new int[]{};\n    }\n    public static void main(String[] a){\n        int[]r=twoSum(new int[]{2,7,11,15},9);\n        System.out.println(\"[\"+r[0]+\",\"+r[1]+\"]\");\n    }\n}",
                "#include<iostream>\n#include<vector>\n#include<unordered_map>\nusing namespace std;\nint main(){\n    vector<int>v={2,7,11,15};int t=9;\n    unordered_map<int,int>seen;\n    for(int i=0;i<v.size();i++){\n        int c=t-v[i];\n        if(seen.count(c)){cout<<\"[\"<<seen[c]<<\",\"<<i<<\"]\";return 0;}\n        seen[v[i]]=i;\n    }\n    return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Check every pair — always wrong in interviews. O(n²).", "O(n²)", "O(1)", bruteCode),
                sv("Hash map: store each value's index, check complement in O(1).", "O(n)", "O(n)", optCode),
                sv("Hash map is already optimal — same as normal.", "O(n)", "O(n)", optCode)
            ));
            all.add(p);
        }

        // ── 8. Reverse String ─────────────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Strings"), "Data Structures", "BEGINNER", "WRITE", "Reverse a String", 8);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("TCS","Infosys","Wipro","Cognizant"));
            p.setDescription("Given a string, return it reversed.\n\nExample:\nInput:  \"hello\"\nOutput: \"olleh\"");
            p.setInputFormat("A string s.");
            p.setOutputFormat("The reversed string.");
            p.setSampleInput("hello");
            p.setSampleOutput("olleh");
            p.setConstraints("1 <= len(s) <= 10^5");
            p.setHints(List.of(
                "Build result by reading characters from right to left.",
                "Two-pointer: left at 0, right at end. Swap and move both inward.",
                "Python shortcut: s[::-1]"
            ));
            p.setApproach("Two-pointer: l=0, r=len-1. While l < r: swap s[l] and s[r], l++, r--. O(n) time, O(1) space.");
            p.setExplanation("String reversal is a fundamental operation. Interviewers specifically ask for in-place reversal with two pointers — not the built-in shortcut.");
            p.setInterviewTip("'Can you do it in-place?' → two-pointer swap on a char array. Never just call reverse() in an interview without being told it's allowed.");
            var bruteCode = sc(
                "#include <stdio.h>\n#include <string.h>\nint main(){\n    char s[]=\"hello\";int n=strlen(s);\n    char r[n+1];\n    for(int i=0;i<n;i++) r[i]=s[n-1-i];\n    r[n]='\\0'; printf(\"%s\\n\",r); return 0;\n}",
                "s=\"hello\"\nresult=\"\"\nfor i in range(len(s)-1,-1,-1):\n    result+=s[i]  # O(n^2) — string concat creates new string each time\nprint(result)",
                "public class Main{\n    public static void main(String[] a){\n        String s=\"hello\",r=\"\";\n        for(int i=s.length()-1;i>=0;i++) r+=s.charAt(i);  // O(n^2)\n        System.out.println(r);\n    }\n}",
                "#include<iostream>\n#include<string>\nusing namespace std;\nint main(){\n    string s=\"hello\",r=\"\";\n    for(int i=s.size()-1;i>=0;i--) r+=s[i];\n    cout<<r; return 0;\n}"
            );
            var normalCode = sc(
                "#include <stdio.h>\n#include <string.h>\nvoid swap(char*a,char*b){char t=*a;*a=*b;*b=t;}\nint main(){\n    char s[]=\"hello\";\n    int l=0,r=strlen(s)-1;\n    while(l<r) swap(&s[l++],&s[r--]);\n    printf(\"%s\\n\",s); return 0;\n}",
                "s=list(\"hello\")\nl,r=0,len(s)-1\nwhile l<r:\n    s[l],s[r]=s[r],s[l]\n    l+=1;r-=1\nprint(''.join(s))",
                "public class Main{\n    public static void main(String[] a){\n        char[]s=\"hello\".toCharArray();\n        int l=0,r=s.length-1;\n        while(l<r){char t=s[l];s[l++]=s[r];s[r--]=t;}\n        System.out.println(new String(s));\n    }\n}",
                "#include<iostream>\n#include<string>\n#include<algorithm>\nusing namespace std;\nint main(){\n    string s=\"hello\";\n    int l=0,r=s.size()-1;\n    while(l<r) swap(s[l++],s[r--]);\n    cout<<s; return 0;\n}"
            );
            var optCode = sc(normalCode.getC(),
                "s=\"hello\"\nprint(s[::-1])  # slice with step -1",
                "public class Main{\n    public static void main(String[] a){\n        System.out.println(new StringBuilder(\"hello\").reverse());\n    }\n}",
                "#include<iostream>\n#include<string>\n#include<algorithm>\nusing namespace std;\nint main(){\n    string s=\"hello\";\n    reverse(s.begin(),s.end());\n    cout<<s; return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Build new string backwards — O(n²) due to immutable string concatenation.", "O(n²)", "O(n)", bruteCode),
                sv("Two-pointer in-place swap — O(n) time, O(1) space.", "O(n)", "O(1)", normalCode),
                sv("Built-in reverse/slice — same O(n) but idiomatic.", "O(n)", "O(n)", optCode)
            ));
            all.add(p);
        }

        // ── 9. Valid Palindrome String ────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Strings","Two Pointers"), "Data Structures", "INTERMEDIATE", "WRITE", "Valid Palindrome", 9);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("Amazon","Microsoft","TCS","Zoho"));
            p.setDescription("A string is a palindrome if it reads the same forwards and backwards.\n\nIgnore case and non-alphanumeric characters.\n\nExamples:\n\"racecar\" → true\n\"A man a plan a canal Panama\" → true\n\"hello\" → false");
            p.setInputFormat("A string s.");
            p.setOutputFormat("true or false.");
            p.setSampleInput("racecar");
            p.setSampleOutput("true");
            p.setConstraints("1 <= len(s) <= 2*10^5");
            p.setHints(List.of(
                "Clean approach: filter only alphanumeric, lowercase, then compare with reverse.",
                "Two-pointer: left and right pointers, skip non-alphanumeric, compare characters.",
                "Two-pointer is O(n) time and O(1) space — cleaner than building a filtered string."
            ));
            p.setApproach("Two-pointer: l=0, r=end. Skip non-alphanumeric on both sides. Compare lowercase chars. If mismatch → not palindrome. If pointers cross → palindrome.");
            p.setExplanation("The real interview version requires handling spaces and punctuation. The two-pointer approach handles this without building an extra string.");
            p.setInterviewTip("Follow-up: 'What if you could remove at most one character — is it still a palindrome?' This leads to the 'Valid Palindrome II' problem (greedy two-pointer).");
            var bruteCode = sc(
                "#include <stdio.h>\n#include <string.h>\n#include <ctype.h>\nint main(){\n    char s[]=\"racecar\",clean[100];int k=0;\n    for(int i=0;s[i];i++) if(isalnum(s[i])) clean[k++]=tolower(s[i]);\n    clean[k]='\\0';\n    char rev[100];strcpy(rev,clean);\n    for(int l=0,r=k-1;l<r;l++,r--){char t=rev[l];rev[l]=rev[r];rev[r]=t;}\n    printf(\"%s\\n\",strcmp(clean,rev)==0?\"true\":\"false\"); return 0;\n}",
                "s=\"racecar\"\nclean=''.join(c.lower() for c in s if c.isalnum())\nprint(\"true\" if clean==clean[::-1] else \"false\")",
                "public class Main{\n    public static void main(String[] a){\n        String s=\"racecar\";\n        String clean=s.replaceAll(\"[^a-zA-Z0-9]\",\"\").toLowerCase();\n        System.out.println(clean.equals(new StringBuilder(clean).reverse().toString()));\n    }\n}",
                "#include<iostream>\n#include<string>\n#include<algorithm>\n#include<cctype>\nusing namespace std;\nint main(){\n    string s=\"racecar\",clean=\"\";\n    for(char c:s) if(isalnum(c)) clean+=tolower(c);\n    string rev=clean; reverse(rev.begin(),rev.end());\n    cout<<(clean==rev?\"true\":\"false\"); return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\n#include <string.h>\n#include <ctype.h>\nint main(){\n    char s[]=\"racecar\";\n    int l=0,r=strlen(s)-1;\n    while(l<r){\n        while(l<r&&!isalnum(s[l])) l++;\n        while(l<r&&!isalnum(s[r])) r--;\n        if(tolower(s[l])!=tolower(s[r])){printf(\"false\\n\");return 0;}\n        l++;r--;\n    }\n    printf(\"true\\n\"); return 0;\n}",
                "def is_palindrome(s):\n    l,r=0,len(s)-1\n    while l<r:\n        while l<r and not s[l].isalnum(): l+=1\n        while l<r and not s[r].isalnum(): r-=1\n        if s[l].lower()!=s[r].lower(): return False\n        l+=1;r-=1\n    return True\nprint(\"true\" if is_palindrome(\"racecar\") else \"false\")",
                "public class Main{\n    static boolean isPal(String s){\n        int l=0,r=s.length()-1;\n        while(l<r){\n            while(l<r&&!Character.isLetterOrDigit(s.charAt(l))) l++;\n            while(l<r&&!Character.isLetterOrDigit(s.charAt(r))) r--;\n            if(Character.toLowerCase(s.charAt(l))!=Character.toLowerCase(s.charAt(r))) return false;\n            l++;r--;\n        }\n        return true;\n    }\n    public static void main(String[] a){System.out.println(isPal(\"racecar\"));}\n}",
                "#include<iostream>\n#include<string>\n#include<cctype>\nusing namespace std;\nbool isPal(string s){\n    int l=0,r=s.size()-1;\n    while(l<r){\n        while(l<r&&!isalnum(s[l]))l++;\n        while(l<r&&!isalnum(s[r]))r--;\n        if(tolower(s[l])!=tolower(s[r]))return false;\n        l++;r--;\n    }\n    return true;\n}\nint main(){cout<<(isPal(\"racecar\")?\"true\":\"false\");return 0;}"
            );
            p.setSolutions(sols(
                sv("Filter to clean string, compare with its reverse. O(n) time, O(n) space.", "O(n)", "O(n)", bruteCode),
                sv("Two-pointer skips non-alphanumeric in-place. O(n) time, O(1) space.", "O(n)", "O(1)", optCode),
                sv("Two-pointer is already optimal.", "O(n)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 10. Count Vowels ──────────────────────────────────────────────────
        {
            ProblemQuestion p = base(S, List.of("Strings"), "Data Structures", "BEGINNER", "WRITE", "Count Vowels and Consonants", 10);
            p.setDescription("Given a string of lowercase letters, count the number of vowels (a,e,i,o,u) and consonants.\n\nExample:\nInput:  \"hello\"\nOutput:\nVowels: 2\nConsonants: 3");
            p.setInputFormat("A lowercase string s (no spaces).");
            p.setOutputFormat("Two lines: Vowels: X and Consonants: Y.");
            p.setSampleInput("hello");
            p.setSampleOutput("Vowels: 2\nConsonants: 3");
            p.setConstraints("Only lowercase alphabets.");
            p.setHints(List.of(
                "Create a set of vowels: {a,e,i,o,u}",
                "For each character, check if it's in the vowel set.",
                "Increment vowel counter or consonant counter accordingly."
            ));
            p.setApproach("Walk each character. If it's in 'aeiou', increment vowels. Else increment consonants. Print both.");
            p.setExplanation("Using a set for vowel lookup is O(1) per character. This is cleaner than a long if/else chain and teaches the value of using the right data structure for lookup.");
            var code = sc(
                "#include <stdio.h>\n#include <string.h>\nint main(){\n    char s[]=\"hello\",v[]=\"aeiou\";\n    int vc=0,cc=0;\n    for(int i=0;s[i];i++){\n        int isV=0;\n        for(int j=0;v[j];j++) if(s[i]==v[j]){isV=1;break;}\n        if(isV) vc++; else cc++;\n    }\n    printf(\"Vowels: %d\\nConsonants: %d\\n\",vc,cc); return 0;\n}",
                "s=\"hello\"\nvowels=set('aeiou')\nv=sum(1 for c in s if c in vowels)\nprint(f\"Vowels: {v}\\nConsonants: {len(s)-v}\")",
                "public class Main{\n    public static void main(String[] a){\n        String s=\"hello\",vowels=\"aeiou\";\n        int v=0;\n        for(char c:s.toCharArray()) if(vowels.indexOf(c)>=0) v++;\n        System.out.println(\"Vowels: \"+v+\"\\nConsonants: \"+(s.length()-v));\n    }\n}",
                "#include<iostream>\n#include<string>\nusing namespace std;\nint main(){\n    string s=\"hello\",vow=\"aeiou\";int v=0;\n    for(char c:s) if(vow.find(c)!=string::npos) v++;\n    cout<<\"Vowels: \"<<v<<\"\\nConsonants: \"<<s.size()-v<<endl; return 0;\n}"
            );
            p.setSolutions(same(sv("Walk each character, check membership in vowel set, count both.", "O(n)", "O(1)", code)));
            all.add(p);
        }

        // ── 11. Check Anagram ─────────────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Strings","Hash Map"), "Data Structures", "INTERMEDIATE", "WRITE", "Check Anagram", 11);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("Amazon","Microsoft","Adobe","Zoho"));
            p.setDescription("Two strings are anagrams if they contain the same characters with the same frequency.\n\nExamples:\n\"listen\" and \"silent\" → true\n\"hello\" and \"world\" → false");
            p.setInputFormat("Two strings s and t.");
            p.setOutputFormat("true or false.");
            p.setSampleInput("listen\nsilent");
            p.setSampleOutput("true");
            p.setConstraints("Only lowercase letters | 1 <= len <= 10^5");
            p.setHints(List.of(
                "Sort both strings — if they match, they're anagrams.",
                "Faster: use a frequency count array of size 26.",
                "Increment for s, decrement for t. If all counts are 0 at the end → anagram."
            ));
            p.setApproach("Frequency count: create array[26] = 0. For each char in s, increment. For each char in t, decrement. If any value != 0, not anagram.");
            p.setExplanation("The sort approach is simple but O(n log n). The frequency array is O(n) and O(1) space. It's a common pattern for character-frequency problems.");
            p.setInterviewTip("Follow-up: 'What about Unicode characters?' → use a HashMap instead of array[26]. Always ask if input is only lowercase ASCII.");
            var bruteCode = sc(
                "#include <stdio.h>\n#include <string.h>\n#include <stdlib.h>\nint cmp(const void*a,const void*b){return *(char*)a-*(char*)b;}\nint main(){\n    char s[]=\"listen\",t[]=\"silent\";\n    if(strlen(s)!=strlen(t)){printf(\"false\\n\");return 0;}\n    qsort(s,strlen(s),1,cmp); qsort(t,strlen(t),1,cmp);\n    printf(\"%s\\n\",strcmp(s,t)==0?\"true\":\"false\"); return 0;\n}",
                "s,t=\"listen\",\"silent\"\nprint(\"true\" if sorted(s)==sorted(t) else \"false\")  # O(n log n)",
                "import java.util.Arrays;\npublic class Main{\n    public static void main(String[] a){\n        char[]s=\"listen\".toCharArray(),t=\"silent\".toCharArray();\n        Arrays.sort(s);Arrays.sort(t);\n        System.out.println(Arrays.equals(s,t));\n    }\n}",
                "#include<iostream>\n#include<string>\n#include<algorithm>\nusing namespace std;\nint main(){\n    string s=\"listen\",t=\"silent\";\n    sort(s.begin(),s.end());sort(t.begin(),t.end());\n    cout<<(s==t?\"true\":\"false\"); return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\n#include <string.h>\nint main(){\n    char s[]=\"listen\",t[]=\"silent\";\n    if(strlen(s)!=strlen(t)){printf(\"false\\n\");return 0;}\n    int freq[26]={0};\n    for(int i=0;s[i];i++){freq[s[i]-'a']++;freq[t[i]-'a']--;}\n    for(int i=0;i<26;i++) if(freq[i]){printf(\"false\\n\");return 0;}\n    printf(\"true\\n\"); return 0;\n}",
                "from collections import Counter\ns,t=\"listen\",\"silent\"\nprint(\"true\" if Counter(s)==Counter(t) else \"false\")  # O(n)",
                "public class Main{\n    public static void main(String[] a){\n        String s=\"listen\",t=\"silent\";\n        if(s.length()!=t.length()){System.out.println(false);return;}\n        int[]freq=new int[26];\n        for(int i=0;i<s.length();i++){freq[s.charAt(i)-'a']++;freq[t.charAt(i)-'a']--;}\n        for(int f:freq) if(f!=0){System.out.println(false);return;}\n        System.out.println(true);\n    }\n}",
                "#include<iostream>\n#include<string>\nusing namespace std;\nint main(){\n    string s=\"listen\",t=\"silent\";\n    if(s.size()!=t.size()){cout<<\"false\";return 0;}\n    int f[26]={0};\n    for(int i=0;i<s.size();i++){f[s[i]-'a']++;f[t[i]-'a']--;}\n    for(int x:f) if(x){cout<<\"false\";return 0;}\n    cout<<\"true\"; return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Sort both strings and compare — O(n log n).", "O(n log n)", "O(1)", bruteCode),
                sv("Frequency count array — increment for s, decrement for t, check all zeros.", "O(n)", "O(1)", optCode),
                sv("Counter/HashMap approach — same O(n) but handles Unicode too.", "O(n)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 12. Most Frequent Character ───────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Strings","Hash Map"), "Data Structures", "INTERMEDIATE", "WRITE", "Most Frequent Character", 12);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("Amazon","Zoho","Infosys","Wipro"));
            p.setDescription("Find the character that appears most often in a string. If tie, return the one that appears first.\n\nExample:\nInput:  \"abracadabra\"\nOutput: a\n\nBecause 'a' appears 5 times.");
            p.setInputFormat("A lowercase string s.");
            p.setOutputFormat("The most frequent character.");
            p.setSampleInput("abracadabra");
            p.setSampleOutput("a");
            p.setConstraints("Only lowercase letters | 1 <= len <= 10^5.");
            p.setHints(List.of(
                "Count frequency of each character using an array of size 26.",
                "Find the index with the highest frequency.",
                "Convert index back to character: 'a' + index."
            ));
            p.setApproach("Count frequency using freq[c - 'a']++. Then find max frequency index. Return 'a' + maxIndex.");
            p.setExplanation("Frequency counting with a fixed-size array is O(n) time and O(1) space (since the array is always size 26). This pattern applies to any problem involving character frequencies.");
            var code = sc(
                "#include <stdio.h>\n#include <string.h>\nint main(){\n    char s[]=\"abracadabra\";\n    int freq[26]={0},maxF=0,maxI=0;\n    for(int i=0;s[i];i++) freq[s[i]-'a']++;\n    for(int i=0;i<26;i++) if(freq[i]>maxF){maxF=freq[i];maxI=i;}\n    printf(\"%c\\n\",'a'+maxI); return 0;\n}",
                "s=\"abracadabra\"\nfrom collections import Counter\nprint(Counter(s).most_common(1)[0][0])",
                "public class Main{\n    public static void main(String[] a){\n        String s=\"abracadabra\";\n        int[]freq=new int[26];\n        for(char c:s.toCharArray()) freq[c-'a']++;\n        int max=0,idx=0;\n        for(int i=0;i<26;i++) if(freq[i]>max){max=freq[i];idx=i;}\n        System.out.println((char)('a'+idx));\n    }\n}",
                "#include<iostream>\n#include<string>\nusing namespace std;\nint main(){\n    string s=\"abracadabra\";\n    int f[26]={0},mf=0,mi=0;\n    for(char c:s) f[c-'a']++;\n    for(int i=0;i<26;i++) if(f[i]>mf){mf=f[i];mi=i;}\n    cout<<(char)('a'+mi); return 0;\n}"
            );
            p.setSolutions(same(sv("freq[c-'a']++ for each char, then find index of max frequency.", "O(n)", "O(1)", code)));
            all.add(p);
        }

        // ── 13. Binary Search ─────────────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Arrays","Searching"), "Algorithms", "INTERMEDIATE", "WRITE", "Binary Search", 13);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("Amazon","Google","Microsoft","Flipkart","Goldman Sachs"));
            p.setDescription("Given a sorted array and a target, return the index of target using binary search. Return -1 if not found.\n\nExample:\nInput:  arr=[-1,0,3,5,9,12], target=9\nOutput: 4\n\nRequirement: O(log n) time complexity.");
            p.setInputFormat("Sorted array of distinct integers + target.");
            p.setOutputFormat("Index or -1.");
            p.setSampleInput("arr=[-1,0,3,5,9,12], target=9");
            p.setSampleOutput("4");
            p.setConstraints("Array is sorted in ascending order | 1 <= n <= 10^4.");
            p.setHints(List.of(
                "Look at the middle element. Is it the target? If yes, done.",
                "If target > mid, search the right half. If target < mid, search the left half.",
                "Repeat with the new half until found or range is empty."
            ));
            p.setApproach("lo=0, hi=n-1. While lo <= hi: mid=(lo+hi)/2. If arr[mid]==target return mid. If arr[mid]<target, lo=mid+1. Else hi=mid-1. Return -1.");
            p.setExplanation("Binary search halves the search space each step, giving O(log n). On a 1 billion element array, linear search needs 1B steps, binary needs only 30. This is a fundamental algorithm.");
            p.setInterviewTip("Most common bugs: 1) off-by-one (use lo<=hi not lo<hi), 2) integer overflow in mid calculation (use lo+(hi-lo)/2 not (lo+hi)/2), 3) wrong boundary update.");
            var bruteCode = sc(
                "#include <stdio.h>\nint main(){\n    int arr[]={-1,0,3,5,9,12},n=6,t=9;\n    for(int i=0;i<n;i++) if(arr[i]==t){printf(\"%d\\n\",i);return 0;}\n    printf(\"-1\\n\"); return 0;\n}",
                "arr=[-1,0,3,5,9,12];target=9\nfor i,v in enumerate(arr):\n    if v==target: print(i); break\nelse: print(-1)  # O(n) — doesn't use sorted property",
                "import java.util.Arrays;\npublic class Main{\n    public static void main(String[] a){\n        int[]arr={-1,0,3,5,9,12};int t=9;\n        for(int i=0;i<arr.length;i++) if(arr[i]==t){System.out.println(i);return;}\n        System.out.println(-1);\n    }\n}",
                "#include<iostream>\n#include<vector>\nusing namespace std;\nint main(){\n    vector<int>v={-1,0,3,5,9,12};int t=9;\n    for(int i=0;i<v.size();i++) if(v[i]==t){cout<<i;return 0;}\n    cout<<-1; return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\nint binarySearch(int*a,int n,int t){\n    int lo=0,hi=n-1;\n    while(lo<=hi){\n        int mid=lo+(hi-lo)/2;\n        if(a[mid]==t) return mid;\n        if(a[mid]<t) lo=mid+1;\n        else hi=mid-1;\n    }\n    return -1;\n}\nint main(){\n    int arr[]={-1,0,3,5,9,12};\n    printf(\"%d\\n\",binarySearch(arr,6,9)); return 0;\n}",
                "def binary_search(arr, target):\n    lo, hi = 0, len(arr)-1\n    while lo <= hi:\n        mid = lo + (hi-lo)//2\n        if arr[mid] == target: return mid\n        if arr[mid] < target: lo = mid+1\n        else: hi = mid-1\n    return -1\nprint(binary_search([-1,0,3,5,9,12], 9))",
                "public class Main{\n    static int binarySearch(int[]a,int t){\n        int lo=0,hi=a.length-1;\n        while(lo<=hi){\n            int mid=lo+(hi-lo)/2;\n            if(a[mid]==t) return mid;\n            if(a[mid]<t) lo=mid+1;\n            else hi=mid-1;\n        }\n        return -1;\n    }\n    public static void main(String[] x){\n        System.out.println(binarySearch(new int[]{-1,0,3,5,9,12},9));\n    }\n}",
                "#include<iostream>\n#include<vector>\nusing namespace std;\nint main(){\n    vector<int>v={-1,0,3,5,9,12};int t=9,lo=0,hi=v.size()-1;\n    while(lo<=hi){\n        int mid=lo+(hi-lo)/2;\n        if(v[mid]==t){cout<<mid;return 0;}\n        if(v[mid]<t) lo=mid+1; else hi=mid-1;\n    }\n    cout<<-1; return 0;\n}"
            );
            p.setSolutions(sols(
                sv("Linear scan — O(n). Ignores the sorted property completely.", "O(n)", "O(1)", bruteCode),
                sv("Binary search — halve search space each step. O(log n).", "O(log n)", "O(1)", optCode),
                sv("Binary search is the optimal solution for sorted arrays.", "O(log n)", "O(1)", optCode)
            ));
            all.add(p);
        }

        // ── 14. Valid Parentheses ─────────────────────────────────────────────
        {
            ProblemQuestion p = base(SI, List.of("Strings","Stack"), "Algorithms", "INTERMEDIATE", "WRITE", "Valid Parentheses", 14);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("Amazon","Google","Microsoft","Flipkart","Adobe"));
            p.setDescription("Given a string containing only '(', ')', '{', '}', '[' and ']', determine if the brackets are balanced.\n\nExamples:\n\"()[]{}\" → true\n\"([{}])\" → true\n\"(]\" → false\n\"([)]\" → false");
            p.setInputFormat("A string of bracket characters.");
            p.setOutputFormat("true or false.");
            p.setSampleInput("()[]{}");
            p.setSampleOutput("true");
            p.setConstraints("1 <= len <= 10^4 | Only bracket characters.");
            p.setHints(List.of(
                "Use a stack. When you see an opening bracket, push it.",
                "When you see a closing bracket, the top of the stack must be its matching opener.",
                "At the end, the stack must be empty."
            ));
            p.setApproach("Push every opening bracket. For every closing bracket: if stack is empty or top doesn't match → false. If stack empty at end → true.");
            p.setExplanation("This is the classic stack application. The stack correctly handles nesting because it processes the most recently opened bracket first (LIFO matches bracket nesting perfectly).");
            p.setInterviewTip("Very common interview question. The stack solution is the only correct O(n) approach. Know it cold — no hesitation. Follow-up: 'Remove minimum brackets to make valid.'");
            var bruteCode = sc(
                "// Brute: count only — wrong for '([)]'\n#include <stdio.h>\nint main(){\n    char s[]=\"()[]{}\";\n    int open=0;\n    for(int i=0;s[i];i++){\n        if(s[i]=='('||s[i]=='['||s[i]=='{') open++;\n        else open--;\n        if(open<0){printf(\"false\\n\");return 0;}\n    }\n    printf(\"%s\\n\",open==0?\"true\":\"false\"); return 0;\n}",
                "# Brute count — wrong for mixed types like '([)]'\ns=\"()[]{}\"\ncount=0\nfor c in s:\n    if c in '([{': count+=1\n    else: count-=1\n    if count<0: print(\"false\"); break\nelse: print(\"true\" if count==0 else \"false\")",
                "public class Main{\n    public static void main(String[] a){\n        // Count only — wrong for ([)]\n        String s=\"()[]{}\";\n        int c=0;\n        for(char ch:s.toCharArray()){\n            if(ch=='('||ch=='['||ch=='{') c++; else c--;\n            if(c<0){System.out.println(false);return;}\n        }\n        System.out.println(c==0);\n    }\n}",
                "#include<iostream>\n#include<string>\nusing namespace std;\nint main(){\n    string s=\"()[]{}\";int c=0;\n    for(char ch:s){\n        if(ch=='('||ch=='['||ch=='{') c++; else c--;\n        if(c<0){cout<<\"false\";return 0;}\n    }\n    cout<<(c==0?\"true\":\"false\"); return 0;\n}"
            );
            var optCode = sc(
                "#include <stdio.h>\n#include <string.h>\nchar stack[10001];int top=0;\nchar match(char c){\n    if(c==')') return '('; if(c==']') return '['; return '{';\n}\nint main(){\n    char s[]=\"()[]{}\";\n    for(int i=0;s[i];i++){\n        if(s[i]=='('||s[i]=='['||s[i]=='{') stack[top++]=s[i];\n        else{\n            if(top==0||stack[top-1]!=match(s[i])){printf(\"false\\n\");return 0;}\n            top--;\n        }\n    }\n    printf(\"%s\\n\",top==0?\"true\":\"false\"); return 0;\n}",
                "def is_valid(s):\n    stack=[]\n    for c in s:\n        if c in \"([{\": stack.append(c)\n        else:\n            if not stack: return False\n            top=stack.pop()\n            if (c==\")\" and top!=\"(\") or (c==\"]\" and top!=\"[\") or (c==\"}\" and top!=\"{\"):\n                return False\n    return len(stack)==0\nprint(\"true\" if is_valid(\"()[]{}\") else \"false\")",
                "import java.util.*;\npublic class Main{\n    static boolean isValid(String s){\n        Deque<Character>st=new ArrayDeque<>();\n        for(char c:s.toCharArray()){\n            if(c=='('||c=='['||c=='{') st.push(c);\n            else{\n                if(st.isEmpty()) return false;\n                char top=st.pop();\n                if(c==')'&&top!='('||c==']'&&top!='['||c=='}'&&top!='{') return false;\n            }\n        }\n        return st.isEmpty();\n    }\n    public static void main(String[] a){System.out.println(isValid(\"()[]{}\"));}\n}",
                "#include<iostream>\n#include<stack>\n#include<string>\nusing namespace std;\nbool isValid(string s){\n    stack<char>st;\n    for(char c:s){\n        if(c=='('||c=='['||c=='{') st.push(c);\n        else{\n            if(st.empty()) return false;\n            char t=st.top();st.pop();\n            if(c==')'&&t!='('||c==']'&&t!='['||c=='}'&&t!='{') return false;\n        }\n    }\n    return st.empty();\n}\nint main(){cout<<(isValid(\"()[]{}\")? \"true\":\"false\");return 0;}"
            );
            p.setSolutions(sols(
                sv("Count-based — wrong for mixed types like '([)]'. Fails the real test.", "O(n)", "O(1)", bruteCode),
                sv("Stack: push openers, match closers. O(n) time, O(n) space.", "O(n)", "O(n)", optCode),
                sv("Stack solution is the only correct approach for all bracket types.", "O(n)", "O(n)", optCode)
            ));
            all.add(p);
        }

        // ── 15. FizzBuzz ──────────────────────────────────────────────────────
        {
            ProblemQuestion p = base(I, List.of("Loops","Conditionals"), "Basics", "BEGINNER", "WRITE", "FizzBuzz", 15);
            p.setIsInterview(true);
            p.setCompaniesThatAsk(List.of("TCS","Infosys","Capgemini","Wipro","HCL","Cognizant"));
            p.setDescription("Print numbers 1 to 15. But:\n- Multiples of 3 → print \"Fizz\"\n- Multiples of 5 → print \"Buzz\"\n- Multiples of both → print \"FizzBuzz\"\n\nOutput:\n1 2 Fizz 4 Buzz Fizz 7 8 Fizz Buzz 11 Fizz 13 14 FizzBuzz");
            p.setInputFormat("n=15 (hardcoded)");
            p.setOutputFormat("Numbers 1–15 with replacements.");
            p.setSampleInput("(none)");
            p.setSampleOutput("1 2 Fizz 4 Buzz Fizz 7 8 Fizz Buzz 11 Fizz 13 14 FizzBuzz");
            p.setConstraints("1 <= n <= 10^4");
            p.setHints(List.of(
                "Check divisibility by 15 (both 3 and 5) FIRST.",
                "Then check 3, then 5, else print the number.",
                "The order matters — FizzBuzz must come before Fizz and Buzz."
            ));
            p.setApproach("if i%15==0 → FizzBuzz, elif i%3==0 → Fizz, elif i%5==0 → Buzz, else → i. Check 15 first or you'll never reach FizzBuzz.");
            p.setExplanation("A classic screening question. The most common mistake: checking 3 before 15, so FizzBuzz never triggers because 15%3==0 is caught first as just Fizz.");
            p.setInterviewTip("FizzBuzz tests: 1) Do you know modulo? 2) Do you handle the combined case (15) first? 3) Can you extend it? Follow-up: 'Make it work for any list of divisor→label pairs.'");
            var normalCode = sc(
                "#include <stdio.h>\nint main(){\n    for(int i=1;i<=15;i++){\n        if(i%15==0) printf(\"FizzBuzz \");\n        else if(i%3==0) printf(\"Fizz \");\n        else if(i%5==0) printf(\"Buzz \");\n        else printf(\"%d \",i);\n    }\n    return 0;\n}",
                "for i in range(1,16):\n    if i%15==0: print('FizzBuzz',end=' ')\n    elif i%3==0: print('Fizz',end=' ')\n    elif i%5==0: print('Buzz',end=' ')\n    else: print(i,end=' ')",
                "public class Main{\n    public static void main(String[] a){\n        for(int i=1;i<=15;i++){\n            if(i%15==0) System.out.print(\"FizzBuzz \");\n            else if(i%3==0) System.out.print(\"Fizz \");\n            else if(i%5==0) System.out.print(\"Buzz \");\n            else System.out.print(i+\" \");\n        }\n    }\n}",
                "#include<iostream>\nusing namespace std;\nint main(){\n    for(int i=1;i<=15;i++){\n        if(i%15==0) cout<<\"FizzBuzz \";\n        else if(i%3==0) cout<<\"Fizz \";\n        else if(i%5==0) cout<<\"Buzz \";\n        else cout<<i<<\" \";\n    }\n    return 0;\n}"
            );
            var optCode = sc(normalCode.getC(),
                "for i in range(1,16):\n    print(('Fizz'*(i%3==0)+'Buzz'*(i%5==0)) or str(i), end=' ')\n# Pythonic: build string by bool*string, fallback to number",
                normalCode.getJava(),
                normalCode.getCpp()
            );
            p.setSolutions(sols(
                sv("Standard if/elif chain — check 15 first to handle FizzBuzz case.", "O(n)", "O(1)", normalCode),
                sv("Same logic, clean and readable.", "O(n)", "O(1)", normalCode),
                sv("Python trick: concatenate 'Fizz'/'Buzz' based on bool; 'or str(i)' as fallback.", "O(n)", "O(1)", optCode)
            ));
            all.add(p);
        }
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
