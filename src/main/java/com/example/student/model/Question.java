package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "questions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Question {

    @Id
    private String id;

    @Indexed
    private String conceptId;

    @Indexed
    private String subjectId;

    private String text;
    private List<String> options;
    private int correctIndex;
    private String explanation;
    private String difficulty;
}
