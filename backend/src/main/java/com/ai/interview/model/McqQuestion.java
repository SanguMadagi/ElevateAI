package com.ai.interview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class McqQuestion {
    private String id;
    private String question;
    private List<String> options;
    private String correctAnswer;
    private String category;
    private String difficulty; // EASY / MEDIUM / HARD
    private String explanation; // Explanation of why the correct answer is right

    public McqQuestion(String id, String question, List<String> options, String correctAnswer, String category, String difficulty) {
        this.id = id;
        this.question = question;
        this.options = options;
        this.correctAnswer = correctAnswer;
        this.category = category;
        this.difficulty = difficulty;
    }
}
