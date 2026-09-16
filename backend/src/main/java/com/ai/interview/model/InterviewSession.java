package com.ai.interview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "interview_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    @Id
    private String id;
    private String userId;
    private String targetRole;
    private String topic;
    private String status; // IN_PROGRESS, COMPLETED
    private String currentQuestion;
    private int questionCount;
    private int maxQuestions;
    private Double overallScore;
    
    @Builder.Default
    private List<InterviewTurn> history = new ArrayList<>();
    
    @Builder.Default
    private Map<String, Object> finalReport = new HashMap<>();
    
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterviewTurn {
        private String question;
        private String candidateAnswer;
        private String evaluationFeedback;
        private Double score;
        private LocalDateTime timestamp;
    }
}
