package com.ai.interview;

import com.ai.interview.dto.BatchProjectEvaluation;
import com.ai.interview.dto.BatchScenarioEvaluation;
import com.ai.interview.model.*;
import com.ai.interview.repository.ProfileRepository;
import com.ai.interview.repository.ResultRepository;
import com.ai.interview.service.AiService;
import com.ai.interview.service.EvaluationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AssessmentParallelEvaluationTest {

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private AiService aiService;

    private ThreadPoolTaskExecutor taskExecutor;
    private EvaluationService evaluationService;

    private TestSession session;
    private Profile profile;

    @BeforeEach
    void setUp() {
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(4);
        taskExecutor.setMaxPoolSize(8);
        taskExecutor.setQueueCapacity(100);
        taskExecutor.setThreadNamePrefix("test-ai-task-");
        taskExecutor.initialize();

        evaluationService = new EvaluationService(resultRepository, profileRepository, aiService, taskExecutor);

        profile = new Profile();
        profile.setUserId("user-parallel-test");
        profile.setName("Parallel Test Candidate");
        profile.setTargetRole("Senior Java Engineer");
        profile.setSkills(List.of("Java", "Spring Boot", "Concurrency"));

        when(profileRepository.findByUserId(any())).thenReturn(Optional.of(profile));
        when(resultRepository.findByTestId(any())).thenReturn(Optional.empty());
        when(resultRepository.save(any(Result.class))).thenAnswer(invocation -> invocation.getArgument(0));

        session = new TestSession();
        session.setId("test-session-parallel-1");
        session.setUserId("user-parallel-test");
        session.setTargetRole("Senior Java Engineer");

        // 4 MCQs
        session.setMcqQuestions(List.of(
                new McqQuestion("mcq1", "Q1", List.of("A", "B", "C", "D"), "A", "Java", "EASY"),
                new McqQuestion("mcq2", "Q2", List.of("A", "B", "C", "D"), "B", "Java", "MEDIUM"),
                new McqQuestion("mcq3", "Q3", List.of("A", "B", "C", "D"), "C", "Spring", "MEDIUM"),
                new McqQuestion("mcq4", "Q4", List.of("A", "B", "C", "D"), "D", "Database", "EASY")
        ));
        session.setMcqAnswers(Map.of("mcq1", "A", "mcq2", "B", "mcq3", "C", "mcq4", "D"));

        // 3 Scenarios
        session.setScenarioQuestions(List.of(
                new ScenarioQuestion("s1", "Scenario Q1", "ref1", "crit1", "System Design", "HARD"),
                new ScenarioQuestion("s2", "Scenario Q2", "ref2", "crit2", "System Design", "HARD"),
                new ScenarioQuestion("s3", "Scenario Q3", "ref3", "crit3", "System Design", "HARD")
        ));
        session.setScenarioAnswers(Map.of("s1", "Ans S1", "s2", "Ans S2", "s3", "Ans S3"));

        // 3 Projects
        session.setProjectQuestions(List.of(
                new ProjectQuestion("p1", "Project Q1", "pref1", "pcrit1", "Architecture", "MEDIUM"),
                new ProjectQuestion("p2", "Project Q2", "pref2", "pcrit2", "Security", "HARD"),
                new ProjectQuestion("p3", "Project Q3", "pref3", "pcrit3", "Database", "MEDIUM")
        ));
        session.setProjectAnswers(Map.of("p1", "Ans P1", "p2", "Ans P2", "p3", "Ans P3"));
    }

    @AfterEach
    void tearDown() {
        taskExecutor.shutdown();
    }

    @Test
    void testParallelEvaluationExecutionOrderingAndCallCounts() {
        AtomicBoolean scenarioCompleted = new AtomicBoolean(false);
        AtomicBoolean projectCompleted = new AtomicBoolean(false);
        AtomicLong scenarioStartNanos = new AtomicLong(0);
        AtomicLong projectStartNanos = new AtomicLong(0);
        AtomicLong scenarioEndNanos = new AtomicLong(0);
        AtomicLong projectEndNanos = new AtomicLong(0);
        AtomicLong finalStartNanos = new AtomicLong(0);

        when(aiService.batchEvaluateScenarioAnswers(any(), any(), any())).thenAnswer(inv -> {
            scenarioStartNanos.set(System.nanoTime());
            Thread.sleep(100);
            scenarioEndNanos.set(System.nanoTime());
            scenarioCompleted.set(true);
            return new BatchScenarioEvaluation(List.of(
                    new BatchScenarioEvaluation.ScenarioAnswerEvaluation("s1", 80.0, "Good design"),
                    new BatchScenarioEvaluation.ScenarioAnswerEvaluation("s2", 90.0, "Great caching"),
                    new BatchScenarioEvaluation.ScenarioAnswerEvaluation("s3", 85.0, "Solid sharding")
            ));
        });

        when(aiService.batchEvaluateProjectAnswers(any(), any(), any())).thenAnswer(inv -> {
            projectStartNanos.set(System.nanoTime());
            Thread.sleep(100);
            projectEndNanos.set(System.nanoTime());
            projectCompleted.set(true);
            return new BatchProjectEvaluation(List.of(
                    new BatchProjectEvaluation.ProjectAnswerEvaluation("p1", 85.0, "Solid architecture"),
                    new BatchProjectEvaluation.ProjectAnswerEvaluation("p2", 95.0, "Strong auth design"),
                    new BatchProjectEvaluation.ProjectAnswerEvaluation("p3", 90.0, "Effective query tuning")
            ));
        });

        when(aiService.generateFinalEvaluation(any(), any(), any(), any())).thenAnswer(inv -> {
            finalStartNanos.set(System.nanoTime());
            // Assert that final evaluation ONLY begins after BOTH scenario and project have completed
            assertTrue(scenarioCompleted.get(), "Final evaluation must not run before Scenario evaluation completes");
            assertTrue(projectCompleted.get(), "Final evaluation must not run before Project evaluation completes");
            return Map.of(
                    "technicalFeedback", "Strong technical candidate",
                    "strengths", List.of("System Design", "Architecture"),
                    "weakAreas", List.of("Minor syntax details"),
                    "learningRecommendations", List.of("Advanced Kubernetes"),
                    "hiringRecommendation", "STRONG_HIRE",
                    "hiringExplanation", "Excellent scores across all dimensions"
            );
        });

        long overallStart = System.currentTimeMillis();
        Result result = evaluationService.evaluateTest(session);
        long overallDurationMs = System.currentTimeMillis() - overallStart;

        assertNotNull(result, "Evaluation result must not be null");
        assertEquals(100.0, result.getMcqScore(), 0.01, "MCQ score (4/4) must be 100%");
        assertEquals(85.0, result.getScenarioScore(), 0.01, "Scenario score avg((80+90+85)/3) must be 85%");
        assertEquals(90.0, result.getProjectScore(), 0.01, "Project score avg((85+95+90)/3) must be 90%");
        assertEquals("STRONG_HIRE", result.getHiringRecommendation());

        // Verify EXACTLY 1 call each for Scenario, Project, and Final evaluations
        verify(aiService, times(1)).batchEvaluateScenarioAnswers(any(), any(), any());
        verify(aiService, times(1)).batchEvaluateProjectAnswers(any(), any(), any());
        verify(aiService, times(1)).generateFinalEvaluation(any(), any(), any(), any());
        verify(aiService, never()).evaluateMcqAnswer(any(), any(), any());

        // Verify timing order
        assertTrue(finalStartNanos.get() >= scenarioEndNanos.get(), "Final evaluation must start after scenario completes");
        assertTrue(finalStartNanos.get() >= projectEndNanos.get(), "Final evaluation must start after project completes");

        // Verify parallel start: scenario and project should start within ~60ms of each other
        long startDiffMs = Math.abs(scenarioStartNanos.get() - projectStartNanos.get()) / 1_000_000L;
        assertTrue(startDiffMs < 60, "Scenario and Project evaluations must start concurrently (diff was " + startDiffMs + "ms)");

        // Total duration should be close to ~100ms for both concurrent tasks rather than ~200ms sequential
        assertTrue(overallDurationMs < 250, "Concurrent execution should complete well under sequential duration");
    }

    @Test
    void testScenarioBatchFailureFallsBackSafelyAndAllowsProjectAndFinalToComplete() {
        // Scenario batch throws exception
        when(aiService.batchEvaluateScenarioAnswers(any(), any(), any())).thenThrow(new RuntimeException("Gemini quota 429"));
        when(aiService.evaluateScenarioAnswer(any(), any(), any())).thenReturn(Map.of("score", 70.0, "feedback", "Acceptable fallback"));

        // Project batch succeeds
        when(aiService.batchEvaluateProjectAnswers(any(), any(), any())).thenReturn(new BatchProjectEvaluation(List.of(
                new BatchProjectEvaluation.ProjectAnswerEvaluation("p1", 90.0, "Good"),
                new BatchProjectEvaluation.ProjectAnswerEvaluation("p2", 90.0, "Good"),
                new BatchProjectEvaluation.ProjectAnswerEvaluation("p3", 90.0, "Good")
        )));

        when(aiService.generateFinalEvaluation(any(), any(), any(), any())).thenReturn(Map.of(
                "technicalFeedback", "Recovered from scenario batch failure",
                "strengths", List.of("Project competence"),
                "weakAreas", List.of("Scenario consistency"),
                "learningRecommendations", List.of("Practice system design"),
                "hiringRecommendation", "CONSIDER",
                "hiringExplanation", "Completed with fallback"
        ));

        Result result = evaluationService.evaluateTest(session);

        assertNotNull(result);
        assertEquals(70.0, result.getScenarioScore(), 0.01, "Fallback scenario score must be used");
        assertEquals(90.0, result.getProjectScore(), 0.01, "Project batch score must be preserved");
        assertEquals("CONSIDER", result.getHiringRecommendation());

        verify(aiService, times(1)).batchEvaluateScenarioAnswers(any(), any(), any());
        verify(aiService, times(3)).evaluateScenarioAnswer(any(), any(), any()); // fallback for 3 scenario questions
        verify(aiService, times(1)).batchEvaluateProjectAnswers(any(), any(), any());
        verify(aiService, times(1)).generateFinalEvaluation(any(), any(), any(), any());
    }

    @Test
    void testProjectBatchFailureFallsBackSafelyAndAllowsScenarioAndFinalToComplete() {
        // Scenario batch succeeds
        when(aiService.batchEvaluateScenarioAnswers(any(), any(), any())).thenReturn(new BatchScenarioEvaluation(List.of(
                new BatchScenarioEvaluation.ScenarioAnswerEvaluation("s1", 85.0, "Good"),
                new BatchScenarioEvaluation.ScenarioAnswerEvaluation("s2", 85.0, "Good"),
                new BatchScenarioEvaluation.ScenarioAnswerEvaluation("s3", 85.0, "Good")
        )));

        // Project batch throws exception
        when(aiService.batchEvaluateProjectAnswers(any(), any(), any())).thenThrow(new RuntimeException("Gemini quota 429"));
        when(aiService.evaluateProjectAnswer(any(), any(), any())).thenReturn(Map.of("score", 80.0, "feedback", "Fallback project score"));

        when(aiService.generateFinalEvaluation(any(), any(), any(), any())).thenReturn(Map.of(
                "technicalFeedback", "Recovered from project batch failure",
                "strengths", List.of("System design"),
                "weakAreas", List.of("Project depth"),
                "learningRecommendations", List.of("Review documentation"),
                "hiringRecommendation", "HIRE",
                "hiringExplanation", "Solid overall performance"
        ));

        Result result = evaluationService.evaluateTest(session);

        assertNotNull(result);
        assertEquals(85.0, result.getScenarioScore(), 0.01, "Scenario batch score must be preserved");
        assertEquals(80.0, result.getProjectScore(), 0.01, "Fallback project score must be used");
        assertEquals("HIRE", result.getHiringRecommendation());

        verify(aiService, times(1)).batchEvaluateScenarioAnswers(any(), any(), any());
        verify(aiService, times(1)).batchEvaluateProjectAnswers(any(), any(), any());
        verify(aiService, times(3)).evaluateProjectAnswer(any(), any(), any()); // fallback for 3 project questions
        verify(aiService, times(1)).generateFinalEvaluation(any(), any(), any(), any());
    }
}
