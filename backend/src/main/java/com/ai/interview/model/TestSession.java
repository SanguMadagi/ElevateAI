package com.ai.interview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "test_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestSession {
    @Id
    private String id;
    private String testId; // same value as id
    private String userId;
    private String profileId;
    private String targetRole;
    private String expertiseLevel;
    private TestStatus status; // CREATED / IN_PROGRESS / SUBMITTED / EVALUATED
    private String evaluationStatus; // PROCESSING / COMPLETED / FAILED
    private LocalDateTime evaluationStartedAt;
    private LocalDateTime evaluationCompletedAt;
    private String evaluationError;

    private List<McqQuestion> mcqQuestions;
    private List<ScenarioQuestion> scenarioQuestions;
    private List<ProjectQuestion> projectQuestions;

    private Map<String, String> mcqAnswers;
    private Map<String, String> scenarioAnswers;
    private Map<String, String> projectAnswers;
    private Map<String, Answer> answers; // questionId -> Answer

    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private int durationMinutes = 90;
    private Map<String, Long> timePerQuestion; // questionId -> seconds spent

    private List<Violation> violations;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<Double> identityEmbedding;
    private LocalDateTime identityEnrolledAt;
    private String identityVerificationStatus;
    private int successfulIdentityChecks;
    private int failedIdentityChecks;
    private int noFaceEvents;
    private int multipleFaceEvents;
    private int cameraDisconnectEvents;
    private LocalDateTime lastIdentityVerificationAt;
    private LocalDateTime createdAt;
}
