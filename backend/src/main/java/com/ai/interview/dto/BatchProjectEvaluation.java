package com.ai.interview.dto;

import java.util.List;

/**
 * Response from batching all project answer evaluations in a single Gemini call.
 * Contains detailed evaluation results for multiple project questions evaluated together.
 */
public record BatchProjectEvaluation(
        List<ProjectAnswerEvaluation> evaluations
) {
    public record ProjectAnswerEvaluation(
            String questionId,
            double score,
            String feedback,
            List<String> expectedTechnicalPoints,
            String recommendedAnswer,
            List<String> missingPoints
    ) {
        public ProjectAnswerEvaluation(String questionId, double score, String feedback) {
            this(questionId, score, feedback, List.of(), "", List.of());
        }
    }
}
