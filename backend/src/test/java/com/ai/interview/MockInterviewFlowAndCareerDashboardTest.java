package com.ai.interview;

import com.ai.interview.model.InterviewSession;
import com.ai.interview.model.Result;
import com.ai.interview.model.SkillAnalysis;
import com.ai.interview.model.TestSession;
import com.ai.interview.model.TestStatus;
import com.ai.interview.repository.InterviewSessionRepository;
import com.ai.interview.repository.ResultRepository;
import com.ai.interview.repository.SkillAnalysisRepository;
import com.ai.interview.repository.TestSessionRepository;
import com.ai.interview.service.MockInterviewService;
import com.ai.interview.service.SkillAnalyzerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MockInterviewFlowAndCareerDashboardTest {

    @Autowired
    private MockInterviewService mockInterviewService;

    @Autowired
    private SkillAnalyzerService skillAnalyzerService;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private TestSessionRepository testSessionRepository;

    @Autowired
    private SkillAnalysisRepository skillAnalysisRepository;

    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "user-mocktest-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void testMockInterviewFourQuestionsAndFinalFeedbackPersistence() {
        // 1. Start Interview Session
        InterviewSession session = mockInterviewService.startInterview(testUserId, "Java Backend Engineering");
        assertNotNull(session);
        assertNotNull(session.getId());
        assertEquals("IN_PROGRESS", session.getStatus());
        assertEquals(1, session.getQuestionCount());
        assertEquals(4, session.getMaxQuestions());
        assertNotNull(session.getCurrentQuestion());
        assertFalse(session.getCurrentQuestion().isBlank());

        // 2. Submit Answer to Question 1 -> should advance to Question 2
        InterviewSession q1Result = mockInterviewService.submitAnswer(
                session.getId(), testUserId,
                "Dependency injection decouples class creation from business logic by supplying dependencies from the Spring IoC container.");
        assertEquals("IN_PROGRESS", q1Result.getStatus());
        assertEquals(2, q1Result.getQuestionCount());
        assertEquals(1, q1Result.getHistory().size());
        assertNotNull(q1Result.getCurrentQuestion());

        // 3. Submit Answer to Question 2 -> should advance to Question 3
        InterviewSession q2Result = mockInterviewService.submitAnswer(
                session.getId(), testUserId,
                "In high throughput microservices, Redis caching reduces relational database load by caching hot data with TTL expiration.");
        assertEquals("IN_PROGRESS", q2Result.getStatus());
        assertEquals(3, q2Result.getQuestionCount());
        assertEquals(2, q2Result.getHistory().size());

        // 4. Submit Answer to Question 3 -> should advance to Question 4
        InterviewSession q3Result = mockInterviewService.submitAnswer(
                session.getId(), testUserId,
                "Database indexing uses B-Tree structures to achieve logarithmic time search, but requires write overhead during inserts and updates.");
        assertEquals("IN_PROGRESS", q3Result.getStatus());
        assertEquals(4, q3Result.getQuestionCount());
        assertEquals(3, q3Result.getHistory().size());

        // 5. Submit Answer to Question 4 -> triggers final evaluation report generation and status COMPLETED
        InterviewSession completedSession = mockInterviewService.submitAnswer(
                session.getId(), testUserId,
                "Distributed transactions across microservices can be coordinated using the Saga pattern with compensating transactions.");
        
        assertNotNull(completedSession);
        assertEquals("COMPLETED", completedSession.getStatus());
        assertNotNull(completedSession.getCompletedAt());
        assertEquals(4, completedSession.getHistory().size());
        assertNotNull(completedSession.getOverallScore());
        assertTrue(completedSession.getOverallScore() > 0, "Overall score must be greater than 0");

        // Verify Final Feedback Report
        Map<String, Object> finalReport = completedSession.getFinalReport();
        assertNotNull(finalReport, "Final report must be generated");
        assertTrue(finalReport.containsKey("overallScore"));
        assertTrue(finalReport.containsKey("technicalScore"));
        assertTrue(finalReport.containsKey("communicationScore"));
        assertTrue(finalReport.containsKey("confidenceScore"));
        assertTrue(finalReport.containsKey("performanceSummary"));
        assertTrue(finalReport.containsKey("strongAreas"));
        assertTrue(finalReport.containsKey("weakAreas"));
        assertTrue(finalReport.containsKey("conceptsToImprove"));
        assertTrue(finalReport.containsKey("communicationFeedback"));
        assertTrue(finalReport.containsKey("improvementSuggestions"));
        assertTrue(finalReport.containsKey("finalFeedback"));

        // Verify persistence in MongoDB
        InterviewSession fromDb = interviewSessionRepository.findById(completedSession.getId()).orElse(null);
        assertNotNull(fromDb);
        assertEquals("COMPLETED", fromDb.getStatus());
        assertEquals(4, fromDb.getHistory().size());
        assertEquals(completedSession.getOverallScore(), fromDb.getOverallScore());
    }

    @Test
    void testCareerDashboardAggregationWithCompletedInterviewAndExitedAssessment() {
        // 1. Create a completed Assessment Result (Score = 78%)
        Result completedAssessment = new Result();
        completedAssessment.setUserId(testUserId);
        completedAssessment.setTestId("test-session-" + UUID.randomUUID());
        completedAssessment.setFinalScore(78.0);
        completedAssessment.setCategoryScores(Map.of("Core Java", 80.0, "Spring Boot", 76.0));
        completedAssessment.setHiringRecommendation("HIRE");
        completedAssessment.setCreatedAt(LocalDateTime.now().minusDays(2));
        resultRepository.save(completedAssessment);

        // 2. Create an EXITED Assessment TestSession (must NOT create a Result and must NOT contribute to readiness)
        TestSession exitedAssessment = new TestSession();
        exitedAssessment.setId("test-exited-" + UUID.randomUUID());
        exitedAssessment.setUserId(testUserId);
        exitedAssessment.setTargetRole("Backend Engineer");
        exitedAssessment.setStatus(TestStatus.EXITED);
        exitedAssessment.setCreatedAt(LocalDateTime.now().minusDays(1));
        testSessionRepository.save(exitedAssessment);

        // 3. Create a completed Mock Interview (Score = 86%)
        InterviewSession completedInterview = InterviewSession.builder()
                .id("session-interview-" + UUID.randomUUID())
                .userId(testUserId)
                .targetRole("Backend Engineer")
                .topic("Java Backend Engineering")
                .status("COMPLETED")
                .overallScore(86.0)
                .finalReport(Map.of(
                        "overallScore", 86.0,
                        "technicalScore", 85.0,
                        "communicationScore", 88.0,
                        "confidenceScore", 85.0,
                        "performanceSummary", "Solid performance on microservices and Java internals.",
                        "strongAreas", List.of("Spring Boot DI", "Redis Caching"),
                        "weakAreas", List.of("Distributed Locks"),
                        "conceptsToImprove", List.of("Saga Orchestration")
                ))
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        interviewSessionRepository.save(completedInterview);

        // 4. Run Skill & Readiness Analysis
        SkillAnalysis analysis = skillAnalyzerService.analyzeUserPerformance(testUserId);
        assertNotNull(analysis);
        assertEquals(testUserId, analysis.getUserId());

        // Expected readiness: (78 + 86) / 2 = 82%
        assertEquals(82.0, analysis.getOverallReadinessScore(), 0.5, "Readiness score must average completed assessment and mock interview");

        // Verify Skill Scores Breakdown includes Assessment categories & Interview topic
        Map<String, Double> skillScores = analysis.getSkillScores();
        assertNotNull(skillScores);
        assertTrue(skillScores.containsKey("Core Java") || skillScores.containsKey("Java Backend Engineering"));
        assertTrue(skillScores.containsKey("Java Backend Engineering"));

        // Verify Verified Strengths and Detected Weak Concepts include interview insights
        List<String> strong = analysis.getStrongConcepts();
        List<String> weak = analysis.getWeakConcepts();
        assertNotNull(strong);
        assertNotNull(weak);
        assertTrue(strong.stream().anyMatch(s -> s.contains("Spring Boot") || s.contains("Core Java") || s.contains("Redis")));
        assertTrue(weak.stream().anyMatch(w -> w.contains("Distributed Locks") || w.contains("Saga")));

        // Cleanup
        resultRepository.delete(completedAssessment);
        testSessionRepository.delete(exitedAssessment);
        interviewSessionRepository.delete(completedInterview);
        skillAnalysisRepository.delete(analysis);
    }
}
