package com.ai.interview.dto;

import java.util.List;

public record FinalEvaluationReport(
        String technicalFeedback,
        List<String> strengths,
        List<String> weakAreas,
        List<String> learningRecommendations,
        String hiringRecommendation,
        String hiringExplanation
) {}
