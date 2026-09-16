package com.ai.interview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    @Id
    private String id;
    private String testId;
    private String userId;
    private String profileId;
    private String candidateName;
    private String targetRole;

    private double mcqScore;
    private double scenarioScore;
    private double projectScore;
    private double finalScore; // weighted: MCQ*0.40 + Scenario*0.30 + Project*0.30

    private Map<String, Double> questionScores; // questionId -> individual score
    private Map<String, Double> categoryScores; // category -> average score

    private List<QuestionEvaluation> questionEvaluations; // Detailed question-by-question breakdown

    private String technicalFeedback;
    private List<String> strengths;
    private List<String> weakAreas;
    private List<String> learningRecommendations;

    private String hiringRecommendation; // STRONG_HIRE / HIRE / CONSIDER / REJECT
    private String hiringExplanation;

    private List<Violation> violations;
    private int totalViolations;
    private String cheatingRisk; // LOW / MEDIUM / HIGH

    private long totalTimeSpentSeconds;
    private String evaluationStatus; // COMPLETED or PARTIAL_AI_FAILURE
    private String evaluationWarning;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
