package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "roadmap_subjects")
@CompoundIndex(def = "{'roadmapId': 1, 'subjectId': 1}", unique = true)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RoadmapSubject {

    @Id
    private String id;

    private String roadmapId;
    private String subjectId;

    private Subject subject;

    private int orderIndex;
}
