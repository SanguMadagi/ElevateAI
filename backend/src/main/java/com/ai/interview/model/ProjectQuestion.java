package com.ai.interview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectQuestion {
    private String id;
    private String question;
    private String projectReference;
    private String evaluationCriteria;
    private String category;
    private String difficulty;

    public ProjectQuestion(String id, String question, String category, String difficulty) {
        this.id = id;
        this.question = question;
        this.category = category;
        this.difficulty = difficulty;
    }
}
