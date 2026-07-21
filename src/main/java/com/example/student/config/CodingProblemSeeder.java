package com.example.student.config;

import com.example.student.model.CodingProblem;
import com.example.student.model.CodingProblem.Example;
import com.example.student.model.CodingProblem.IOCase;
import com.example.student.repository.CodingProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds a starter set of LeetCode-style problems into the (empty) coding_problems
 * collection on startup. Idempotent: does nothing once any problem exists, so it
 * never overwrites edited/added content.
 */
@Component
@Order(50)
public class CodingProblemSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CodingProblemSeeder.class);
    private static final List<String> ALL_LANGS = List.of("python", "java", "c", "cpp");

    private final CodingProblemRepository repo;

    public CodingProblemSeeder(CodingProblemRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return;

        List<CodingProblem> seed = new ArrayList<>();

        seed.add(problem(0, "Two Sum", "Easy", "Arrays",
                "Given an array of integers nums and an integer target, return the indices of the two "
                        + "numbers that add up to target. Each input has exactly one solution, and you may not "
                        + "use the same element twice.\n\nInput: first line is the space-separated array; second "
                        + "line is the target. Output: the two indices (ascending), space-separated.",
                List.of("2 <= nums.length <= 10^4", "-10^9 <= nums[i] <= 10^9", "Exactly one valid answer exists"),
                List.of(new Example("nums = [2,7,11,15], target = 9", "0 1", "nums[0] + nums[1] = 2 + 7 = 9")),
                List.of(io("2 7 11 15\n9", "0 1", "Example 1"), io("3 2 4\n6", "1 2", "Example 2")),
                List.of(io("3 3\n6", "0 1", null), io("-1 -2 -3 -4 -5\n-8", "2 4", null),
                        io("1 5 3 7\n10", "2 3", null), io("0 4 3 0\n0", "0 3", null))));

        seed.add(problem(1, "Sum of Array", "Easy", "Arrays",
                "Read a line of space-separated integers and print their sum.\n\n"
                        + "Input: one line of space-separated integers. Output: a single integer, their sum.",
                List.of("1 <= n <= 10^4", "-10^6 <= nums[i] <= 10^6"),
                List.of(new Example("nums = [1,2,3]", "6", "1 + 2 + 3 = 6")),
                List.of(io("1 2 3", "6", "Example 1"), io("10 20 30", "60", "Example 2")),
                List.of(io("-5 5", "0", null), io("100", "100", null), io("1 1 1 1 1", "5", null))));

        seed.add(problem(2, "Reverse a String", "Easy", "Strings",
                "Read a line of text and print it reversed.\n\n"
                        + "Input: one line. Output: the same characters in reverse order.",
                List.of("1 <= length <= 10^4"),
                List.of(new Example("\"hello\"", "olleh", "The characters are reversed.")),
                List.of(io("hello", "olleh", "Example 1"), io("world", "dlrow", "Example 2")),
                List.of(io("a", "a", null), io("racecar", "racecar", null), io("AB CD", "DC BA", null))));

        seed.add(problem(3, "FizzBuzz", "Easy", "Math",
                "Read an integer n. For each number from 1 to n, print \"Fizz\" if it is divisible by 3, "
                        + "\"Buzz\" if divisible by 5, \"FizzBuzz\" if divisible by both, otherwise the number "
                        + "itself. Print one value per line.",
                List.of("1 <= n <= 10^4"),
                List.of(new Example("n = 5", "1\n2\nFizz\n4\nBuzz", "3 -> Fizz, 5 -> Buzz.")),
                List.of(io("3", "1\n2\nFizz", "Example 1"), io("5", "1\n2\nFizz\n4\nBuzz", "Example 2")),
                List.of(io("1", "1", null), io("6", "1\n2\nFizz\n4\nBuzz\nFizz", null),
                        io("15", "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz", null))));

        seed.add(problem(4, "Maximum in Array", "Easy", "Arrays",
                "Read a line of space-separated integers and print the largest one.\n\n"
                        + "Input: one line of space-separated integers. Output: the maximum value.",
                List.of("1 <= n <= 10^4", "-10^9 <= nums[i] <= 10^9"),
                List.of(new Example("nums = [3,7,2]", "7", "7 is the largest value.")),
                List.of(io("3 7 2", "7", "Example 1"), io("-1 -5 -3", "-1", "Example 2")),
                List.of(io("42", "42", null), io("5 5 5", "5", null), io("-10 0 10", "10", null))));

        seed.add(problem(5, "Palindrome Number", "Medium", "Math",
                "Read an integer n. Print \"true\" if it reads the same forwards and backwards, otherwise "
                        + "\"false\". Negative numbers are never palindromes.",
                List.of("-2^31 <= n <= 2^31 - 1"),
                List.of(new Example("n = 121", "true", "121 reversed is 121."),
                        new Example("n = -121", "false", "Reversed it becomes 121-, so it is not a palindrome.")),
                List.of(io("121", "true", "Example 1"), io("123", "false", "Example 2")),
                List.of(io("0", "true", null), io("-121", "false", null), io("10", "false", null))));

        repo.saveAll(seed);
        log.info("Seeded {} coding-platform problems into coding_problems.", seed.size());
    }

    private static CodingProblem problem(int order, String title, String difficulty, String category,
                                         String description, List<String> constraints, List<Example> examples,
                                         List<IOCase> samples, List<IOCase> hidden) {
        CodingProblem p = new CodingProblem();
        p.setOrderIndex(order);
        p.setTitle(title);
        p.setDifficulty(difficulty);
        p.setCategory(category);
        p.setDescription(description);
        p.setConstraints(constraints);
        p.setExamples(examples);
        p.setSampleTestCases(samples);
        p.setHiddenTestCases(hidden);
        p.setSupportedLanguages(ALL_LANGS);
        p.setTimeLimit(5);
        p.setMemoryLimit(256);
        return p;
    }

    private static IOCase io(String input, String expected, String label) {
        return new IOCase(input, expected, label);
    }
}
