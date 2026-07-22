package com.example.student.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Slim list payload for GET /roadmaps — path card fields only.
 * Rich About content is fetched on demand via GET /roadmaps/:id.
 */
@Data @NoArgsConstructor
public class RoadmapListDTO {
    private String id;
    private String title;
    private String roleTarget;
    private String icon;
    private String color;
    private int estimatedWeeks;
    private int subjectCount;
    private boolean enrolled;
    private boolean paused;
    private boolean allSubjectsDone;
}
