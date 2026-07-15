package com.example.student.service;

import com.example.student.model.AptitudeTopic;
import com.example.student.model.Concept;
import com.example.student.model.LogicalTopic;
import com.example.student.model.Mission;
import com.example.student.model.ProblemQuestion;
import com.example.student.model.Roadmap;
import com.example.student.model.Subject;
import com.example.student.model.VerbalTopic;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Global search across public learning content (subjects, roadmaps, missions, problems).
 * Case-insensitive partial title match, capped per category. Never returns admin-only
 * or private data — only lightweight display fields are exposed.
 */
@Service
public class SearchService {

    public static final int MIN_QUERY_LEN = 2;
    private static final int PER_CATEGORY = 6;
    private static final int FETCH_BUFFER = 12; // fetch extra, then filter unpublished in Java

    private final MongoTemplate mongoTemplate;

    public SearchService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Map<String, Object> search(String rawQuery) {
        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("subjects", List.of());
        groups.put("concepts", List.of());
        groups.put("roadmaps", List.of());
        groups.put("aptitude", List.of());
        groups.put("missions", List.of());
        groups.put("problems", List.of());

        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.length() < MIN_QUERY_LEN) {
            return Map.of("query", q, "results", groups);
        }

        String regex = ".*" + Pattern.quote(q) + ".*";
        Query titleQuery = new Query(Criteria.where("title").regex(regex, "i")).limit(FETCH_BUFFER);

        // Subjects — all public
        List<Map<String, Object>> subjects = mongoTemplate.find(titleQuery, Subject.class).stream()
                .limit(PER_CATEGORY)
                .map(s -> item(s.getId(), s.getTitle(), s.getRank() != null ? s.getRank() + "-Rank" : null,
                        s.getIcon(), "SUBJECT"))
                .collect(Collectors.toList());

        // Roadmaps — published only
        List<Map<String, Object>> roadmaps = mongoTemplate.find(titleQuery, Roadmap.class).stream()
                .filter(Roadmap::isPublished)
                .limit(PER_CATEGORY)
                .map(r -> item(r.getId(), r.getTitle(), r.getRoleTarget(), r.getIcon(), "ROADMAP"))
                .collect(Collectors.toList());

        // Missions — published only
        List<Map<String, Object>> missions = mongoTemplate.find(titleQuery, Mission.class).stream()
                .filter(Mission::isPublished)
                .limit(PER_CATEGORY)
                .map(m -> item(m.getId(), m.getTitle(),
                        m.getRank() != null ? m.getRank() + "-Rank mission" : "Mission", "🎯", "MISSION"))
                .collect(Collectors.toList());

        // Problems — all public
        List<Map<String, Object>> problems = mongoTemplate.find(titleQuery, ProblemQuestion.class).stream()
                .limit(PER_CATEGORY)
                .map(p -> item(p.getId(), p.getTitle(), p.getLevel(), "💻", "PROBLEM"))
                .collect(Collectors.toList());

        // Concepts — carry subjectId so the result can deep-link into the subject's gate.
        List<Map<String, Object>> concepts = mongoTemplate.find(titleQuery, Concept.class).stream()
                .limit(PER_CATEGORY)
                .map(c -> {
                    Map<String, Object> m = item(c.getId(), c.getTitle(), c.getSubjectTitle(),
                            c.getSubjectIcon() != null ? c.getSubjectIcon() : "📘", "CONCEPT");
                    m.put("subjectId", c.getSubjectId());
                    return m;
                })
                .collect(Collectors.toList());

        // Aptitude topics — live across 3 collections; only active topics, matched on displayName.
        Query aptQuery = new Query(new Criteria().andOperator(
                Criteria.where("displayName").regex(regex, "i"),
                Criteria.where("isActive").is(true)
        )).limit(FETCH_BUFFER);
        List<Map<String, Object>> aptitude = new ArrayList<>();
        for (AptitudeTopic t : mongoTemplate.find(aptQuery, AptitudeTopic.class))
            aptitude.add(aptItem(t.getTopic(), t.getDisplayName(), t.getCategory(), t.getGroup(), t.getIcon()));
        for (LogicalTopic t : mongoTemplate.find(aptQuery, LogicalTopic.class))
            aptitude.add(aptItem(t.getTopic(), t.getDisplayName(), t.getCategory(), t.getGroup(), t.getIcon()));
        for (VerbalTopic t : mongoTemplate.find(aptQuery, VerbalTopic.class))
            aptitude.add(aptItem(t.getTopic(), t.getDisplayName(), t.getCategory(), t.getGroup(), t.getIcon()));
        if (aptitude.size() > PER_CATEGORY) aptitude = new ArrayList<>(aptitude.subList(0, PER_CATEGORY));

        groups.put("subjects", subjects);
        groups.put("concepts", concepts);
        groups.put("roadmaps", roadmaps);
        groups.put("aptitude", aptitude);
        groups.put("missions", missions);
        groups.put("problems", problems);

        return Map.of("query", q, "results", groups);
    }

    /** Aptitude result — id is the topic slug; category+group+topic build the /aptitude route. */
    private Map<String, Object> aptItem(String topicSlug, String displayName, String category, String group, String icon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", topicSlug);
        m.put("title", displayName);
        m.put("subtitle", prettyCategory(category));
        m.put("icon", icon != null ? icon : "🧠");
        m.put("type", "APTITUDE");
        m.put("category", category);
        m.put("group", group);
        m.put("topic", topicSlug);
        return m;
    }

    private String prettyCategory(String category) {
        if (category == null || category.isBlank()) return "Aptitude";
        return switch (category) {
            case "quantitative" -> "Quantitative";
            case "logical" -> "Logical Reasoning";
            case "verbal" -> "Verbal Ability";
            case "data-interpretation" -> "Data Interpretation";
            default -> category;
        };
    }

    private Map<String, Object> item(String id, String title, String subtitle, String icon, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("subtitle", subtitle);
        m.put("icon", icon);
        m.put("type", type);
        return m;
    }
}
