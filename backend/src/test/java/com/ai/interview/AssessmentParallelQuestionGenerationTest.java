package com.ai.interview;

import com.ai.interview.model.McqQuestion;
import com.ai.interview.model.Profile;
import com.ai.interview.model.ProjectQuestion;
import com.ai.interview.model.ScenarioQuestion;
import com.ai.interview.model.TestSession;
import com.ai.interview.model.TestStatus;
import com.ai.interview.repository.ProfileRepository;
import com.ai.interview.repository.ResultRepository;
import com.ai.interview.repository.SubmissionRepository;
import com.ai.interview.repository.TestSessionRepository;
import com.ai.interview.service.AiService;
import com.ai.interview.service.AssessmentEvaluationAsyncService;
import com.ai.interview.service.EvaluationService;
import com.ai.interview.service.TestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AssessmentParallelQuestionGenerationTest {

    @Mock
    private TestSessionRepository testSessionRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private AiService aiService;

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private AssessmentEvaluationAsyncService assessmentEvaluationAsyncService;

    @Mock
    private StringRedisTemplate redisTemplate;

    private ThreadPoolTaskExecutor taskExecutor;
    private TestService testService;

    private Profile profile;
    private final String userId = "user-gen-test";

    @BeforeEach
    void setUp() {
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(4);
        taskExecutor.setMaxPoolSize(8);
        taskExecutor.setQueueCapacity(100);
        taskExecutor.setThreadNamePrefix("gen-test-ai-task-");
        taskExecutor.initialize();

        testService = new TestService(
                testSessionRepository,
                profileRepository,
                submissionRepository,
                resultRepository,
                aiService,
                evaluationService,
                assessmentEvaluationAsyncService,
                redisTemplate,
                taskExecutor
        );

        profile = new Profile();
        profile.setId("profile-123");
        profile.setUserId(userId);
        profile.setName("Question Gen Tester");
        profile.setTargetRole("Senior Java Architect");
        profile.setExpertiseLevel("HARD");
        profile.setSkills(List.of("Java", "Spring Boot", "Distributed Systems"));
        profile.setProjects(List.of("Cloud Gateway API", "Order Processing"));
        profile.setYearsOfExperience(8);

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(testSessionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Collections.emptyList());
        when(testSessionRepository.save(any(TestSession.class))).thenAnswer(inv -> {
            TestSession s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId("session-gen-999");
            }
            return s;
        });
    }

    @AfterEach
    void tearDown() {
        taskExecutor.shutdown();
    }

    private List<McqQuestion> mockAiMcqs() {
        return List.of(
                new McqQuestion("mcq_1", "MCQ Q1", List.of("A", "B", "C", "D"), "A", "Java", "HARD"),
                new McqQuestion("mcq_2", "MCQ Q2", List.of("A", "B", "C", "D"), "B", "Spring", "HARD"),
                new McqQuestion("mcq_3", "MCQ Q3", List.of("A", "B", "C", "D"), "C", "Concurrency", "HARD"),
                new McqQuestion("mcq_4", "MCQ Q4", List.of("A", "B", "C", "D"), "D", "JVM", "HARD")
        );
    }

    private List<ScenarioQuestion> mockAiScenarios() {
        return List.of(
                new ScenarioQuestion("scen_1", "Scenario Q1", "ref1", "crit1", "System Design", "HARD"),
                new ScenarioQuestion("scen_2", "Scenario Q2", "ref2", "crit2", "Distributed Systems", "HARD"),
                new ScenarioQuestion("scen_3", "Scenario Q3", "ref3", "crit3", "Resilience", "HARD")
        );
    }

    private List<ProjectQuestion> mockAiProjects() {
        return List.of(
                new ProjectQuestion("proj_1", "Project Q1", "Gateway", "crit1", "Architecture", "HARD"),
                new ProjectQuestion("proj_2", "Project Q2", "Order Processing", "crit2", "Data Integrity", "HARD"),
                new ProjectQuestion("proj_3", "Project Q3", "Cloud", "crit3", "Scaling", "HARD")
        );
    }

    @Test
    void test1_ParallelExecutionTimingAndOverlappingStartup() {
        AtomicLong mcqStartNanos = new AtomicLong(0);
        AtomicLong scenarioStartNanos = new AtomicLong(0);
        AtomicLong projectStartNanos = new AtomicLong(0);

        when(aiService.generateMcqQuestions(eq(profile), anyList())).thenAnswer(inv -> {
            mcqStartNanos.set(System.nanoTime());
            Thread.sleep(100);
            return mockAiMcqs();
        });

        when(aiService.generateScenarioQuestions(eq(profile), anyList())).thenAnswer(inv -> {
            scenarioStartNanos.set(System.nanoTime());
            Thread.sleep(100);
            return mockAiScenarios();
        });

        when(aiService.generateProjectQuestions(eq(profile), anyList())).thenAnswer(inv -> {
            projectStartNanos.set(System.nanoTime());
            Thread.sleep(100);
            return mockAiProjects();
        });

        long startTimeMs = System.currentTimeMillis();
        TestSession session = testService.startTest(userId);
        long elapsedMs = System.currentTimeMillis() - startTimeMs;

        assertNotNull(session);
        assertEquals(TestStatus.CREATED, session.getStatus());

        // Concurrency check: all three tasks started within ~60ms of each other
        long startSpreadMs = Math.max(
                Math.abs(mcqStartNanos.get() - scenarioStartNanos.get()),
                Math.max(
                        Math.abs(scenarioStartNanos.get() - projectStartNanos.get()),
                        Math.abs(mcqStartNanos.get() - projectStartNanos.get())
                )
        ) / 1_000_000L;
        assertTrue(startSpreadMs < 70, "MCQ, Scenario, and Project generation must start concurrently (spread was " + startSpreadMs + "ms)");

        // Wall-clock duration should be close to ~100ms (max of tasks) rather than ~300ms (sum of tasks)
        assertTrue(elapsedMs < 250, "Parallel duration (" + elapsedMs + "ms) must be significantly less than sequential sum (300ms)");
    }

    @Test
    void test2_CorrectCallCounts() {
        when(aiService.generateMcqQuestions(eq(profile), anyList())).thenReturn(mockAiMcqs());
        when(aiService.generateScenarioQuestions(eq(profile), anyList())).thenReturn(mockAiScenarios());
        when(aiService.generateProjectQuestions(eq(profile), anyList())).thenReturn(mockAiProjects());

        testService.startTest(userId);

        verify(aiService, times(1)).generateMcqQuestions(eq(profile), anyList());
        verify(aiService, times(1)).generateScenarioQuestions(eq(profile), anyList());
        verify(aiService, times(1)).generateProjectQuestions(eq(profile), anyList());
    }

    @Test
    void test3_CorrectQuestionCounts() {
        when(aiService.generateMcqQuestions(eq(profile), anyList())).thenReturn(mockAiMcqs());
        when(aiService.generateScenarioQuestions(eq(profile), anyList())).thenReturn(mockAiScenarios());
        when(aiService.generateProjectQuestions(eq(profile), anyList())).thenReturn(mockAiProjects());

        TestSession session = testService.startTest(userId);

        assertEquals(4, session.getMcqQuestions().size(), "Must generate exactly 4 MCQs");
        assertEquals(3, session.getScenarioQuestions().size(), "Must generate exactly 3 Scenarios");
        assertEquals(3, session.getProjectQuestions().size(), "Must generate exactly 3 Projects");
        int totalQuestions = session.getMcqQuestions().size() + session.getScenarioQuestions().size() + session.getProjectQuestions().size();
        assertEquals(10, totalQuestions, "Total assessment questions must be exactly 10");
    }

    @Test
    void test4_McqFailureIsolation() {
        // MCQ generation fails
        when(aiService.generateMcqQuestions(eq(profile), anyList())).thenThrow(new RuntimeException("Gemini quota 429 on MCQ"));
        when(aiService.getFallbackMcqQuestions(profile)).thenReturn(List.of(
                new McqQuestion("fb_mcq1", "Fallback Q1", List.of("A", "B", "C", "D"), "A", "Java", "EASY"),
                new McqQuestion("fb_mcq2", "Fallback Q2", List.of("A", "B", "C", "D"), "B", "Java", "MEDIUM"),
                new McqQuestion("fb_mcq3", "Fallback Q3", List.of("A", "B", "C", "D"), "C", "Spring", "MEDIUM"),
                new McqQuestion("fb_mcq4", "Fallback Q4", List.of("A", "B", "C", "D"), "D", "SQL", "EASY")
        ));

        // Scenario and Project succeed normally
        when(aiService.generateScenarioQuestions(eq(profile), anyList())).thenReturn(mockAiScenarios());
        when(aiService.generateProjectQuestions(eq(profile), anyList())).thenReturn(mockAiProjects());

        TestSession session = testService.startTest(userId);

        assertNotNull(session);
        assertEquals(4, session.getMcqQuestions().size(), "Fallback must supply exactly 4 MCQs");
        assertEquals(3, session.getScenarioQuestions().size(), "Scenario questions must still succeed");
        assertEquals(3, session.getProjectQuestions().size(), "Project questions must still succeed");
        assertEquals("fb_mcq1", session.getMcqQuestions().get(0).getId());
        assertEquals("scen_1", session.getScenarioQuestions().get(0).getId());
        assertEquals("proj_1", session.getProjectQuestions().get(0).getId());
    }

    @Test
    void test5_ScenarioFailureIsolation() {
        when(aiService.generateMcqQuestions(eq(profile), anyList())).thenReturn(mockAiMcqs());
        // Scenario generation fails
        when(aiService.generateScenarioQuestions(eq(profile), anyList())).thenThrow(new RuntimeException("Gemini timeout on Scenario"));
        when(aiService.getFallbackScenarioQuestions(profile)).thenReturn(List.of(
                new ScenarioQuestion("fb_scen1", "Fallback S1", "ref1", "crit1", "System Design", "HARD"),
                new ScenarioQuestion("fb_scen2", "Fallback S2", "ref2", "crit2", "Observability", "MEDIUM"),
                new ScenarioQuestion("fb_scen3", "Fallback S3", "ref3", "crit3", "System Design", "HARD")
        ));
        when(aiService.generateProjectQuestions(eq(profile), anyList())).thenReturn(mockAiProjects());

        TestSession session = testService.startTest(userId);

        assertNotNull(session);
        assertEquals(4, session.getMcqQuestions().size(), "MCQs must still succeed");
        assertEquals(3, session.getScenarioQuestions().size(), "Fallback must supply exactly 3 Scenarios");
        assertEquals(3, session.getProjectQuestions().size(), "Projects must still succeed");
        assertEquals("mcq_1", session.getMcqQuestions().get(0).getId());
        assertEquals("fb_scen1", session.getScenarioQuestions().get(0).getId());
        assertEquals("proj_1", session.getProjectQuestions().get(0).getId());
    }

    @Test
    void test6_ProjectFailureIsolation() {
        when(aiService.generateMcqQuestions(eq(profile), anyList())).thenReturn(mockAiMcqs());
        when(aiService.generateScenarioQuestions(eq(profile), anyList())).thenReturn(mockAiScenarios());
        // Project generation fails
        when(aiService.generateProjectQuestions(eq(profile), anyList())).thenThrow(new RuntimeException("Gemini error on Project"));
        when(aiService.getFallbackProjectQuestions(profile)).thenReturn(List.of(
                new ProjectQuestion("fb_proj1", "Fallback P1", "pref1", "pcrit1", "Database", "MEDIUM"),
                new ProjectQuestion("fb_proj2", "Fallback P2", "pref2", "pcrit2", "Security", "HARD"),
                new ProjectQuestion("fb_proj3", "Fallback P3", "pref3", "pcrit3", "Architecture", "MEDIUM")
        ));

        TestSession session = testService.startTest(userId);

        assertNotNull(session);
        assertEquals(4, session.getMcqQuestions().size(), "MCQs must still succeed");
        assertEquals(3, session.getScenarioQuestions().size(), "Scenarios must still succeed");
        assertEquals(3, session.getProjectQuestions().size(), "Fallback must supply exactly 3 Projects");
        assertEquals("mcq_1", session.getMcqQuestions().get(0).getId());
        assertEquals("scen_1", session.getScenarioQuestions().get(0).getId());
        assertEquals("fb_proj1", session.getProjectQuestions().get(0).getId());
    }

    @Test
    void test7_FinalTestSessionIntegrity() {
        when(aiService.generateMcqQuestions(eq(profile), anyList())).thenReturn(mockAiMcqs());
        when(aiService.generateScenarioQuestions(eq(profile), anyList())).thenReturn(mockAiScenarios());
        when(aiService.generateProjectQuestions(eq(profile), anyList())).thenReturn(mockAiProjects());

        TestSession session = testService.startTest(userId);

        assertNotNull(session);
        assertEquals(userId, session.getUserId());
        assertEquals("profile-123", session.getProfileId());
        assertEquals("Senior Java Architect", session.getTargetRole());
        assertEquals("HARD", session.getExpertiseLevel());
        assertEquals(60, session.getDurationMinutes());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getStartedAt());

        // Verify distinct questions without duplicates
        long uniqueMcqIds = session.getMcqQuestions().stream().map(McqQuestion::getId).distinct().count();
        assertEquals(4, uniqueMcqIds);
        long uniqueScenarioIds = session.getScenarioQuestions().stream().map(ScenarioQuestion::getId).distinct().count();
        assertEquals(3, uniqueScenarioIds);
        long uniqueProjectIds = session.getProjectQuestions().stream().map(ProjectQuestion::getId).distinct().count();
        assertEquals(3, uniqueProjectIds);
    }
}
