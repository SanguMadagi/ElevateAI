package com.ai.interview;

import com.ai.interview.dto.BatchProjectEvaluation;
import com.ai.interview.dto.BatchScenarioEvaluation;
import com.ai.interview.model.*;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AssessmentPersonalizationAndDetailedEvaluationTest {

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
    private AssessmentEvaluationAsyncService assessmentEvaluationAsyncService;

    @Mock
    private StringRedisTemplate redisTemplate;

    private ThreadPoolTaskExecutor taskExecutor;
    private EvaluationService evaluationService;
    private TestService testService;

    private Profile profile;
    private final String userId = "candidate-pers-001";

    @BeforeEach
    void setUp() {
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(4);
        taskExecutor.setMaxPoolSize(8);
        taskExecutor.setQueueCapacity(100);
        taskExecutor.setThreadNamePrefix("eval-test-ai-task-");
        taskExecutor.initialize();

        evaluationService = new EvaluationService(
                resultRepository,
                profileRepository,
                aiService,
                taskExecutor
        );

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
        profile.setId("prof-99");
        profile.setUserId(userId);
        profile.setName("Alex Developer");
        profile.setTargetRole("Senior Backend Engineer");
        profile.setExpertiseLevel("SENIOR");
        profile.setSkills(List.of("Java", "Spring Boot", "Redis", "Kafka", "MongoDB"));
        profile.setResumeTechnologies(List.of("Docker", "Kubernetes", "PostgreSQL"));
        profile.setProjects(List.of("Distributed Order Service", "High-Throughput Notification Gateway"));
        profile.setYearsOfExperience(6);
    }

    @AfterEach
    void tearDown() {
        taskExecutor.shutdown();
    }

    @Test
    void test1_NegativeContextHistoryIsPassedToQuestionGenerators() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        TestSession pastSession = new TestSession();
        pastSession.setId("past-1");
        pastSession.setUserId(userId);
        pastSession.setMcqQuestions(List.of(new McqQuestion("m1", "Past MCQ question text?", List.of("A", "B", "C", "D"), "A", "Java", "MEDIUM")));
        pastSession.setScenarioQuestions(List.of(new ScenarioQuestion("s1", "Past Scenario question text?", "ctx", "crit", "Architecture", "HARD")));
        pastSession.setProjectQuestions(List.of(new ProjectQuestion("p1", "Past Project question text?", "Order Service", "crit", "DB", "HARD")));

        when(testSessionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(pastSession));
        when(testSessionRepository.save(any(TestSession.class))).thenAnswer(inv -> {
            TestSession s = inv.getArgument(0);
            s.setId("new-session-1");
            return s;
        });

        when(aiService.generateMcqQuestions(eq(profile), anyList())).thenReturn(List.of(
                new McqQuestion("mcq_1", "Fresh MCQ 1", List.of("A", "B", "C", "D"), "A", "Java", "EASY"),
                new McqQuestion("mcq_2", "Fresh MCQ 2", List.of("A", "B", "C", "D"), "B", "Spring", "MEDIUM"),
                new McqQuestion("mcq_3", "Fresh MCQ 3", List.of("A", "B", "C", "D"), "C", "Kafka", "MEDIUM"),
                new McqQuestion("mcq_4", "Fresh MCQ 4", List.of("A", "B", "C", "D"), "D", "Redis", "HARD")
        ));
        when(aiService.generateScenarioQuestions(eq(profile), anyList())).thenReturn(List.of(
                new ScenarioQuestion("scen_1", "Fresh Scenario 1", "ctx1", "crit1", "Resilience", "MEDIUM"),
                new ScenarioQuestion("scen_2", "Fresh Scenario 2", "ctx2", "crit2", "Concurrency", "HARD"),
                new ScenarioQuestion("scen_3", "Fresh Scenario 3", "ctx3", "crit3", "Distributed", "HARD")
        ));
        when(aiService.generateProjectQuestions(eq(profile), anyList())).thenReturn(List.of(
                new ProjectQuestion("proj_1", "Fresh Project 1", "Order Service", "crit1", "Scale", "MEDIUM"),
                new ProjectQuestion("proj_2", "Fresh Project 2", "Gateway", "crit2", "Throughput", "HARD"),
                new ProjectQuestion("proj_3", "Fresh Project 3", "Order Service", "crit3", "Consistency", "HARD")
        ));

        TestSession session = testService.startTest(userId);

        assertNotNull(session);
        assertEquals(4, session.getMcqQuestions().size());
        assertEquals(3, session.getScenarioQuestions().size());
        assertEquals(3, session.getProjectQuestions().size());

        ArgumentCaptor<List<String>> mcqHistoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiService).generateMcqQuestions(eq(profile), mcqHistoryCaptor.capture());
        assertTrue(mcqHistoryCaptor.getValue().contains("Past MCQ question text?"));

        ArgumentCaptor<List<String>> scenHistoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiService).generateScenarioQuestions(eq(profile), scenHistoryCaptor.capture());
        assertTrue(scenHistoryCaptor.getValue().contains("Past Scenario question text?"));

        ArgumentCaptor<List<String>> projHistoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiService).generateProjectQuestions(eq(profile), projHistoryCaptor.capture());
        assertTrue(projHistoryCaptor.getValue().contains("Past Project question text?"));
    }

    @Test
    void test2_DeterministicMcqEvaluationProducesDetailedQuestionReview() {
        String testId = "test-eval-101";

        TestSession session = new TestSession();
        session.setId(testId);
        session.setUserId(userId);
        session.setTargetRole("Senior Backend Engineer");

        McqQuestion q1 = new McqQuestion("mcq_1", "In Java, what is default boolean?", List.of("A", "B", "C", "D"), "B", "Java", "EASY", "Primitives default to false.");
        McqQuestion q2 = new McqQuestion("mcq_2", "Which annotation marks a repository?", List.of("A", "B", "C", "D"), "C", "Spring", "MEDIUM", "@Repository is used.");
        McqQuestion q3 = new McqQuestion("mcq_3", "What handles cache invalidation?", List.of("A", "B", "C", "D"), "A", "Caching", "MEDIUM", "Cache-aside invalidation.");
        McqQuestion q4 = new McqQuestion("mcq_4", "What is HashMap lookup complexity?", List.of("A", "B", "C", "D"), "A", "Data Structures", "HARD", "O(1) average lookup.");

        session.setMcqQuestions(List.of(q1, q2, q3, q4));
        session.setMcqAnswers(Map.of(
                "mcq_1", "B", // Correct
                "mcq_2", "C", // Correct
                "mcq_3", "B", // Incorrect (correct is A)
                "mcq_4", "A"  // Correct
        ));

        session.setScenarioQuestions(List.of());
        session.setProjectQuestions(List.of());
        session.setScenarioAnswers(Collections.emptyMap());
        session.setProjectAnswers(Collections.emptyMap());

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(resultRepository.findByTestId(testId)).thenReturn(Optional.empty());
        when(resultRepository.save(any(Result.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiService.generateFinalEvaluation(any(), any(), any(), anyList())).thenReturn(Map.of(
                "technicalFeedback", "Strong fundamentals with 75% MCQ score.",
                "strengths", List.of("Java core, Spring Framework"),
                "weakAreas", List.of("Distributed caching invalidation"),
                "learningRecommendations", List.of("Review Redis Cache-Aside patterns"),
                "hiringRecommendation", "HIRE",
                "hiringExplanation", "Solid candidate."
        ));

        Result result = evaluationService.evaluateTest(session);

        assertNotNull(result);
        assertEquals(75.0, result.getMcqScore(), 0.01);
        assertNotNull(result.getQuestionEvaluations());
        assertEquals(4, result.getQuestionEvaluations().size());

        QuestionEvaluation qe1 = result.getQuestionEvaluations().get(0);
        assertEquals("mcq_1", qe1.getQuestionId());
        assertEquals("MCQ", qe1.getQuestionType());
        assertTrue(qe1.isCorrect());
        assertEquals(100.0, qe1.getScore());
        assertEquals("B", qe1.getCandidateAnswer());
        assertEquals("B", qe1.getCorrectAnswer());
        assertEquals("Primitives default to false.", qe1.getExplanation());

        QuestionEvaluation qe3 = result.getQuestionEvaluations().get(2);
        assertEquals("mcq_3", qe3.getQuestionId());
        assertFalse(qe3.isCorrect());
        assertEquals(0.0, qe3.getScore());
        assertEquals("B", qe3.getCandidateAnswer());
        assertEquals("A", qe3.getCorrectAnswer());
        assertEquals("Cache-aside invalidation.", qe3.getExplanation());
    }

    @Test
    void test3_BatchScenarioAndProjectEvaluationsPopulateRichDetailedFields() {
        String testId = "test-eval-202";

        TestSession session = new TestSession();
        session.setId(testId);
        session.setUserId(userId);
        session.setTargetRole("Senior Backend Engineer");

        // 4 MCQs
        McqQuestion m1 = new McqQuestion("mcq_1", "MCQ 1", List.of("A", "B", "C", "D"), "A", "Java", "EASY");
        McqQuestion m2 = new McqQuestion("mcq_2", "MCQ 2", List.of("A", "B", "C", "D"), "B", "Java", "MEDIUM");
        McqQuestion m3 = new McqQuestion("mcq_3", "MCQ 3", List.of("A", "B", "C", "D"), "C", "Java", "MEDIUM");
        McqQuestion m4 = new McqQuestion("mcq_4", "MCQ 4", List.of("A", "B", "C", "D"), "D", "Java", "HARD");
        session.setMcqQuestions(List.of(m1, m2, m3, m4));
        session.setMcqAnswers(Map.of("mcq_1", "A", "mcq_2", "B", "mcq_3", "C", "mcq_4", "D")); // 100%

        // 3 Scenarios
        ScenarioQuestion s1 = new ScenarioQuestion("scen_1", "Token refresh 401 incident", "JWT auth", "Clock skew & Redis blacklist", "Security", "HARD");
        ScenarioQuestion s2 = new ScenarioQuestion("scen_2", "Concurrent document lost updates", "MongoDB PUT", "Optimistic locking", "Concurrency", "MEDIUM");
        ScenarioQuestion s3 = new ScenarioQuestion("scen_3", "Payment latency spike cascade", "Tomcat thread pool", "Circuit breaker & async", "Resilience", "HARD");
        session.setScenarioQuestions(List.of(s1, s2, s3));
        session.setScenarioAnswers(Map.of(
                "scen_1", "Check clock skew between auth servers and Redis blacklist cluster.",
                "scen_2", "Use @Version for optimistic concurrency control and retry upon collision.",
                "scen_3", "Wrap external call in Resilience4j circuit breaker with 500ms timeout."
        ));

        // 3 Projects
        ProjectQuestion p1 = new ProjectQuestion("proj_1", "Explain database indexing in Order Service", "Order Service", "Compound indexes and EXPLAIN plans", "Database", "MEDIUM");
        ProjectQuestion p2 = new ProjectQuestion("proj_2", "Explain cache invalidation trade-offs in Gateway", "Gateway", "Cache-aside vs write-through", "Architecture", "HARD");
        ProjectQuestion p3 = new ProjectQuestion("proj_3", "How did you diagnose race condition under 500 RPS load?", "Order Service", "Distributed tracing and distributed locks", "Reliability", "HARD");
        session.setProjectQuestions(List.of(p1, p2, p3));
        session.setProjectAnswers(Map.of(
                "proj_1", "Indexed orderId and customerId with composite index.",
                "proj_2", "Used cache-aside with 60s TTL and event-driven invalidation via Kafka.",
                "proj_3", "Used Redisson distributed lock to synchronize inventory decrements."
        ));

        // Mock Batch Scenario Evaluation
        when(aiService.batchEvaluateScenarioAnswers(eq(session.getScenarioQuestions()), anyMap(), eq(profile)))
                .thenReturn(new BatchScenarioEvaluation(List.of(
                        new BatchScenarioEvaluation.ScenarioAnswerEvaluation("scen_1", 90.0, "Excellent diagnosis.", List.of("Clock skew", "Redis sync"), "Verify clock skew and token blacklist cluster.", List.of()),
                        new BatchScenarioEvaluation.ScenarioAnswerEvaluation("scen_2", 85.0, "Solid optimistic locking.", List.of("Optimistic locking", "Atomic updates"), "Apply @Version field.", List.of()),
                        new BatchScenarioEvaluation.ScenarioAnswerEvaluation("scen_3", 95.0, "Outstanding resilience pattern.", List.of("Circuit breaker", "Timeouts"), "Use Resilience4j with bulkhead.", List.of())
                )));

        // Mock Batch Project Evaluation
        when(aiService.batchEvaluateProjectAnswers(eq(session.getProjectQuestions()), anyMap(), eq(profile)))
                .thenReturn(new BatchProjectEvaluation(List.of(
                        new BatchProjectEvaluation.ProjectAnswerEvaluation("proj_1", 85.0, "Clear indexing strategy.", List.of("Composite index", "Execution plans"), "Walk through composite index selectivity.", List.of()),
                        new BatchProjectEvaluation.ProjectAnswerEvaluation("proj_2", 90.0, "Good cache invalidation trade-offs.", List.of("Event-driven invalidation", "TTL trade-off"), "Use event-driven invalidation via Kafka.", List.of()),
                        new BatchProjectEvaluation.ProjectAnswerEvaluation("proj_3", 95.0, "Strong concurrency resolution.", List.of("Distributed locking", "Idempotency"), "Employ distributed locks with lease timeout.", List.of())
                )));

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(resultRepository.findByTestId(testId)).thenReturn(Optional.empty());
        when(resultRepository.save(any(Result.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiService.generateFinalEvaluation(any(), any(), any(), anyList())).thenReturn(Map.of(
                "technicalFeedback", "Outstanding technical architecture and incident debugging competence.",
                "strengths", List.of("Resilience patterns", "Distributed concurrency", "Database indexing"),
                "weakAreas", List.of("None significant"),
                "learningRecommendations", List.of("Explore multi-region active-active databases"),
                "hiringRecommendation", "STRONG_HIRE",
                "hiringExplanation", "Demonstrated senior-level engineering depth across all 10 questions."
        ));

        Result result = evaluationService.evaluateTest(session);

        assertNotNull(result);
        assertEquals(100.0, result.getMcqScore(), 0.01);
        assertEquals(90.0, result.getScenarioScore(), 0.01); // (90+85+95)/3 = 90.0
        assertEquals(90.0, result.getProjectScore(), 0.01); // (85+90+95)/3 = 90.0
        // Weighted: 100*0.4 + 90*0.3 + 90*0.3 = 40 + 27 + 27 = 94.0
        assertEquals(94.0, result.getFinalScore(), 0.01);

        assertNotNull(result.getQuestionEvaluations());
        assertEquals(10, result.getQuestionEvaluations().size(), "Result must persist all 10 QuestionEvaluation items");

        long mcqCount = result.getQuestionEvaluations().stream().filter(q -> "MCQ".equals(q.getQuestionType())).count();
        long scenCount = result.getQuestionEvaluations().stream().filter(q -> "SCENARIO".equals(q.getQuestionType())).count();
        long projCount = result.getQuestionEvaluations().stream().filter(q -> "PROJECT".equals(q.getQuestionType())).count();

        assertEquals(4, mcqCount);
        assertEquals(3, scenCount);
        assertEquals(3, projCount);

        QuestionEvaluation scenEval = result.getQuestionEvaluations().stream().filter(q -> "scen_1".equals(q.getQuestionId())).findFirst().orElseThrow();
        assertEquals(90.0, scenEval.getScore());
        assertEquals("Excellent diagnosis.", scenEval.getFeedback());
        assertNotNull(scenEval.getExpectedPoints());
        assertTrue(scenEval.getExpectedPoints().contains("Clock skew"));
        assertEquals("Verify clock skew and token blacklist cluster.", scenEval.getRecommendedAnswer());

        QuestionEvaluation projEval = result.getQuestionEvaluations().stream().filter(q -> "proj_3".equals(q.getQuestionId())).findFirst().orElseThrow();
        assertEquals(95.0, projEval.getScore());
        assertEquals("Order Service", projEval.getProjectReference());
        assertEquals("Strong concurrency resolution.", projEval.getFeedback());
        assertEquals("Employ distributed locks with lease timeout.", projEval.getRecommendedAnswer());
    }

    @Test
    void test4_CandidatePersonalizationWithCodeSyncAndMediaSearchProfile() {
        Profile customProfile = new Profile();
        customProfile.setUserId("candidate-codesync-123");
        customProfile.setName("Dev Tester");
        customProfile.setTargetRole("Full Stack Engineer");
        customProfile.setSkills(List.of("React", "Spring Boot", "WebSockets", "MongoDB", "Redis"));
        customProfile.setProjects(List.of("CodeSync", "MediaSearch"));
        customProfile.setYearsOfExperience(4);

        when(profileRepository.findByUserId("candidate-codesync-123")).thenReturn(Optional.of(customProfile));
        when(testSessionRepository.findByUserIdOrderByCreatedAtDesc("candidate-codesync-123")).thenReturn(Collections.emptyList());
        when(testSessionRepository.save(any(TestSession.class))).thenAnswer(inv -> {
            TestSession s = inv.getArgument(0);
            s.setId("sess-cs-99");
            return s;
        });

        when(aiService.generateMcqQuestions(eq(customProfile), anyList())).thenReturn(List.of(
                new McqQuestion("mcq_1", "In Spring Security, what handles WebSocket STOMP auth?", List.of("A", "B", "C", "D"), "A", "WebSockets", "EASY"),
                new McqQuestion("mcq_2", "How to manage state in React?", List.of("A", "B", "C", "D"), "B", "React", "MEDIUM"),
                new McqQuestion("mcq_3", "Redis cache eviction policy", List.of("A", "B", "C", "D"), "C", "Redis", "MEDIUM"),
                new McqQuestion("mcq_4", "MongoDB document concurrency", List.of("A", "B", "C", "D"), "D", "MongoDB", "HARD")
        ));
        when(aiService.generateScenarioQuestions(eq(customProfile), anyList())).thenReturn(List.of(
                new ScenarioQuestion("scen_1", "WebSocket reconnection storm during traffic surge", "Client heartbeat drop", "Backoff retry & connection throttling", "Reliability", "MEDIUM"),
                new ScenarioQuestion("scen_2", "Concurrent document modification race condition in MongoDB", "PUT edits", "Optimistic locking with @Version", "Concurrency", "HARD"),
                new ScenarioQuestion("scen_3", "Redis cache hit rate drop causes latency spike", "High read volume", "Cache-aside & TTL tuning", "Performance", "HARD")
        ));
        when(aiService.generateProjectQuestions(eq(customProfile), anyList())).thenReturn(List.of(
                new ProjectQuestion("proj_1", "In CodeSync, walk through your WebSocket and STOMP message synchronization flow.", "CodeSync", "STOMP broker & session management", "Architecture", "MEDIUM"),
                new ProjectQuestion("proj_2", "In CodeSync, how did you diagnose out-of-order code edits arriving at clients?", "CodeSync", "Sequence numbering & distributed locks", "Debugging", "HARD"),
                new ProjectQuestion("proj_3", "In MediaSearch, if search volume scales 10x, what becomes the bottleneck in MongoDB/Redis?", "MediaSearch", "Indexing, cache invalidation, connection pool", "Scalability", "HARD")
        ));

        TestSession session = testService.startTest("candidate-codesync-123");

        assertNotNull(session);
        assertEquals(4, session.getMcqQuestions().size());
        assertEquals(3, session.getScenarioQuestions().size());
        assertEquals(3, session.getProjectQuestions().size());

        // Verify project questions reference CodeSync and MediaSearch
        assertTrue(session.getProjectQuestions().stream().anyMatch(q -> "CodeSync".equals(q.getProjectReference())));
        assertTrue(session.getProjectQuestions().stream().anyMatch(q -> "MediaSearch".equals(q.getProjectReference())));
    }
}
