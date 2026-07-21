package com.example.student.dto;

import com.example.student.model.ProblemQuestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Authenticated Code Gym problem view.
 * Deliberately exposes only {@code sample} test cases — hidden expected outputs
 * must never reach the client (judging uses the full document server-side).
 */
public record ProblemDetailDTO(
        String id,
        String track,
        int orderIndex,
        String level,
        String category,
        List<String> topics,
        String title,
        String description,
        String inputFormat,
        String outputFormat,
        String constraints,
        String codeSnippet,
        List<ProblemQuestion.Example> examples,
        List<String> hints,
        String approach,
        List<String> whatYouLearn,
        List<ProblemQuestion.TestCase> sampleTestCases,
        int hiddenTestCount,
        boolean judgeable,
        ProblemQuestion.SolutionCode starterCode,
        ProblemQuestion.Solutions solutions,
        String explanation,
        String tip,
        boolean isSolved
) {
    public static ProblemDetailDTO from(ProblemQuestion p) {
        // Effective cases fall back to authored examples when no explicit testCases exist,
        // so beginner problems are runnable without a data migration. Hidden expected
        // outputs are never included below.
        List<ProblemQuestion.TestCase> all = p.effectiveTestCases();
        List<ProblemQuestion.TestCase> samples = new ArrayList<>();
        int hidden = 0;
        for (ProblemQuestion.TestCase t : all) {
            if (t == null) continue;
            if (t.isSample()) samples.add(t);
            else hidden++;
        }
        boolean judgeable = !all.isEmpty();
        return new ProblemDetailDTO(
                p.getId(),
                p.getTrack(),
                p.getOrderIndex(),
                p.getLevel(),
                p.getCategory(),
                p.getTopics(),
                p.getTitle(),
                p.getDescription(),
                p.getInputFormat(),
                p.getOutputFormat(),
                p.getConstraints(),
                p.getCodeSnippet(),
                p.getExamples(),
                p.getHints(),
                p.getApproach(),
                p.getWhatYouLearn(),
                samples,
                hidden,
                judgeable,
                p.getStarterCode(),
                p.getSolutions(),
                p.getExplanation(),
                p.getTip(),
                false // per-user; set by the controller via withSolved, never cached
        );
    }

    /** Returns a copy with the per-request {@code isSolved} flag for the current user. */
    public ProblemDetailDTO withSolved(boolean solved) {
        return new ProblemDetailDTO(
                id, track, orderIndex, level, category, topics, title, description,
                inputFormat, outputFormat, constraints, codeSnippet, examples, hints,
                approach, whatYouLearn, sampleTestCases, hiddenTestCount, judgeable,
                starterCode, solutions, explanation, tip, solved
        );
    }
}
