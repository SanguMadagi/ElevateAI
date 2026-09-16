package com.ai.interview.dto;

import java.util.List;

/**
 * Response from batching all scenario answer evaluations in a single Gemini call.
 * Contains detailed evaluation results for multiple scenario questions evaluated together.
 */
public record BatchScenarioEvaluation(
        List<ScenarioAnswerEvaluation> evaluations
) {
    public record ScenarioAnswerEvaluation(
            String questionId,
            double score,
            String feedback,
            List<String> expectedKeyPoints,
            String recommendedAnswer,
            List<String> missingPoints
    ) {
        public ScenarioAnswerEvaluation(String questionId, double score, String feedback) {
            this(questionId, score, feedback, List.of(), "", List.of());
        }
    }
}
