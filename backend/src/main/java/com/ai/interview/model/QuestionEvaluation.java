package com.ai.interview.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionEvaluation {
    private String questionId;
    private String questionType; // MCQ, SCENARIO, PROJECT
    private String question;
    private String context; // For scenario
    private String projectReference; // For project
    private String candidateAnswer;
    private String correctAnswer; // For MCQ only

    @JsonProperty("isCorrect")
    private boolean isCorrect; // For MCQ

    private double score; // 0.0 to 100.0
    private String feedback; // Evaluation summary
    private List<String> expectedPoints; // Key points expected
    private String recommendedAnswer; // Recommended/strong approach (for Scenario & Project)
    private List<String> missingPoints; // What was missing from candidate answer
    private String explanation; // Explanation for MCQ
    private String category;
    private String difficulty;

    @JsonProperty("isCorrect")
    public boolean isCorrect() {
        return isCorrect;
    }

    @JsonProperty("isCorrect")
    public void setCorrect(boolean isCorrect) {
        this.isCorrect = isCorrect;
    }
}
