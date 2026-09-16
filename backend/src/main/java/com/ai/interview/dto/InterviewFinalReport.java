package com.ai.interview.dto;

import java.util.List;

public record InterviewFinalReport(
        double overallScore,
        double technicalScore,
        double communicationScore,
        double confidenceScore,
        String performanceSummary,
        List<String> strongAreas,
        List<String> weakAreas,
        List<String> conceptsToImprove,
        String communicationFeedback,
        List<String> improvementSuggestions,
        String finalFeedback
) {}

