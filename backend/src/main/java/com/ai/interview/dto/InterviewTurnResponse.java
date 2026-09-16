package com.ai.interview.dto;

import java.util.List;

/**
 * Combined response from a single Gemini call that evaluates the current answer
 * AND generates the next question (for questions 1-3) or indicates completion (for question 4).
 */
public record InterviewTurnResponse(
        double score,
        String evaluationFeedback,
        String nextQuestion,
        boolean isLastQuestion
) {}
