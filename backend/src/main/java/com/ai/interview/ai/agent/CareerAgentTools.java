package com.ai.interview.ai.agent;

import com.ai.interview.model.InterviewSession;
import com.ai.interview.model.Profile;
import com.ai.interview.model.Result;
import com.ai.interview.model.SkillAnalysis;
import com.ai.interview.repository.InterviewSessionRepository;
import com.ai.interview.repository.ProfileRepository;
import com.ai.interview.repository.ResultRepository;
import com.ai.interview.repository.SkillAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerAgentTools {

    private final ProfileRepository profileRepository;
    private final ResultRepository resultRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final SkillAnalysisRepository skillAnalysisRepository;

    public String getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().equalsIgnoreCase("anonymousUser")) {
            throw new SecurityException("Unauthorized access: Authentication required.");
        }
        return auth.getName();
    }

    @Tool(description = "Retrieve the authenticated user's profile, target role, experience level, and skills")
    public String getUserProfile() {
        String userId = getAuthenticatedUserId();
        Profile p = profileRepository.findByUserId(userId).orElse(null);
        if (p == null) return "Not enough data available yet.";
        return String.format("Target Role: %s, Expertise Level: %s, Skills: %s",
                p.getTargetRole(), p.getExpertiseLevel(), p.getSkills());
    }

    @Tool(description = "Retrieve the authenticated user's stored resume information, projects, and extracted skills")
    public String getResumeInformation() {
        String userId = getAuthenticatedUserId();
        Profile p = profileRepository.findByUserId(userId).orElse(null);
        if (p == null || p.getResumeFileName() == null || p.getResumeFileName().trim().isEmpty()) {
            return "Not enough data available yet.";
        }
        return String.format("Resume File: %s, Projects: %s, Extracted Skills: %s",
                p.getResumeFileName(), p.getProjects(), p.getSkills());
    }

    @Tool(description = "Retrieve the authenticated user's completed assessment count, latest score, and feedback")
    public String getAssessmentResults() {
        String userId = getAuthenticatedUserId();
        List<Result> results = resultRepository.findByUserId(userId);
        if (results.isEmpty()) return "Not enough data available yet.";
        Result latest = results.stream()
            .max(Comparator.comparing(Result::getCreatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(results.get(0));
        Map<String, Double> categoryScores = latest.getCategoryScores() == null
            ? Map.of()
            : latest.getCategoryScores();
        return String.format(Locale.ROOT,
            "Assessment facts: completedCount=%d; latestScore=%.1f%%; latestCategoryScores=%s; technicalFeedback=%s",
            results.size(), latest.getFinalScore(), categoryScores, latest.getTechnicalFeedback());
    }

    @Tool(description = "Retrieve the authenticated user's mock interview history and latest report")
    public String getInterviewHistory() {
        String userId = getAuthenticatedUserId();
        List<InterviewSession> sessions = interviewSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (sessions.isEmpty()) return "Not enough data available yet.";
        InterviewSession latest = sessions.get(0);
        return String.format("Interview facts: completedCount=%d; latestTopic=%s; latestStatus=%s; latestFinalReport=%s",
                sessions.size(), latest.getTopic(), latest.getStatus(), latest.getFinalReport());
    }

    @Tool(description = "Retrieve the authenticated user's skill scores and readiness")
    public String getSkillScores() {
        String userId = getAuthenticatedUserId();
        SkillAnalysis sa = skillAnalysisRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);
        if (sa == null || sa.getSkillScores() == null || sa.getSkillScores().isEmpty()) {
            return "Not enough data available yet.";
        }
        return String.format(Locale.ROOT, "Skill facts: overallReadiness=%.1f%%; skillScores=%s",
                sa.getOverallReadinessScore(), sa.getSkillScores());
    }

    @Tool(description = "Retrieve the authenticated user's weak concepts, repeated mistakes, and detected gaps")
    public String getMistakeAnalysis() {
        String userId = getAuthenticatedUserId();
        SkillAnalysis sa = skillAnalysisRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);
        if (sa == null) return "Not enough data available yet.";
        return String.format("Analysis facts: weakConcepts=%s; repeatedMistakes=%s; gapDetections=%s",
                sa.getWeakConcepts(), sa.getRepeatedMistakes(), sa.getGapDetections());
    }

    @Tool(description = "Retrieve the authenticated user's weak concepts and knowledge gaps")
    public String getWeakConcepts() {
        String userId = getAuthenticatedUserId();
        SkillAnalysis sa = skillAnalysisRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);
        if (sa == null || sa.getWeakConcepts() == null || sa.getWeakConcepts().isEmpty()) {
            return "Not enough data available yet.";
        }
        return String.format("Weak Concepts: %s, Knowledge Gap Detections: %s",
                sa.getWeakConcepts(), sa.getGapDetections());
    }

    @Tool(description = "Retrieve the authenticated user's assessment and interview practice counts")
    public String getPracticeHistory() {
        String userId = getAuthenticatedUserId();
        List<Result> results = resultRepository.findByUserId(userId);
        List<InterviewSession> sessions = interviewSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return String.format("Completed Assessments: %d, Completed Interviews: %d", results.size(), sessions.size());
    }
}

