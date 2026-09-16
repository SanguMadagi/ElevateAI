package com.ai.interview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Submission {
    @Id
    private String id;
    private String testId;
    private String userId;
    private SubmissionStatus status; // PENDING / EVALUATING / COMPLETED / FAILED
    private LocalDateTime submittedAt;
    private LocalDateTime evaluatedAt;
}
