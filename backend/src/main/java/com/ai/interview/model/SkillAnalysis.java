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

@Document(collection = "skill_analyses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillAnalysis {

    @Id
    private String id;
    private String userId;
    
    private double overallReadinessScore; // e.g. 67%
    
    @Builder.Default
    private Map<String, Double> skillScores = new HashMap<>(); // Java: 86%, SQL: 54%
    
    @Builder.Default
    private List<String> strongConcepts = new ArrayList<>();
    
    @Builder.Default
    private List<String> weakConcepts = new ArrayList<>();
    
    @Builder.Default
    private List<RepeatedMistake> repeatedMistakes = new ArrayList<>();
    
    @Builder.Default
    private List<String> gapDetections = new ArrayList<>(); // e.g. Knowledge gap between resume claim & assessment performance
    
    private String recommendedNextAction;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepeatedMistake {
        private String topic;
        private String concept;
        private int attemptCount;
        private String status; // "Needs Improvement", "Resolved"
    }
}
