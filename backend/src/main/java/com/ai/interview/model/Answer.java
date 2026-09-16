package com.ai.interview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Answer {
    private String questionId;
    private String answer;
    private String status; // ANSWERED, SKIPPED
    private int timeSpent; // in seconds
}
