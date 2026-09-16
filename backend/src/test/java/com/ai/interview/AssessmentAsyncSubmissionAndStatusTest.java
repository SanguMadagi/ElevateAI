package com.ai.interview;

import com.ai.interview.exception.ForbiddenException;
import com.ai.interview.model.*;
import com.ai.interview.repository.ProfileRepository;
import com.ai.interview.repository.ResultRepository;
import com.ai.interview.repository.SubmissionRepository;
import com.ai.interview.repository.TestSessionRepository;
import com.ai.interview.service.AssessmentEvaluationAsyncService;
import com.ai.interview.service.TestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AssessmentAsyncSubmissionAndStatusTest {

    @Autowired
    private TestService testService;

    @Autowired
    private AssessmentEvaluationAsyncService asyncService;

    @Autowired
    private TestSessionRepository testSessionRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ProfileRepository profileRepository;

    private String testUserId;
    private String otherUserId;
    private String testId;
    private Profile profile;
    private TestSession session;

    @BeforeEach
    void setUp() {
        testUserId = "user-async-" + UUID.randomUUID().toString().substring(0, 8);
        otherUserId = "user-intruder-" + UUID.randomUUID().toString().substring(0, 8);
        testId = "session-async-" + UUID.randomUUID();

        profile = new Profile();
        profile.setUserId(testUserId);
        profile.setName("Async Test Candidate");
        profile.setTargetRole("Backend Java Engineer");
        profile.setSkills(List.of("Java", "Spring Boot", "Redis"));
        profileRepository.save(profile);

        session = new TestSession();
        session.setId(testId);
        session.setTestId(testId);
        session.setUserId(testUserId);
        session.setTargetRole("Backend Java Engineer");
        session.setStatus(TestStatus.IN_PROGRESS);

        // 4 MCQs
        McqQuestion q1 = new McqQuestion("q1", "What is Java?", List.of("A", "B", "C", "D"), "A", "Java", "EASY");
        McqQuestion q2 = new McqQuestion("q2", "What is Spring?", List.of("A", "B", "C", "D"), "B", "Spring", "MEDIUM");
        McqQuestion q3 = new McqQuestion("q3", "What is Redis?", List.of("A", "B", "C", "D"), "C", "Redis", "MEDIUM");
        McqQuestion q4 = new McqQuestion("q4", "What is SQL?", List.of("A", "B", "C", "D"), "D", "Database", "EASY");
        session.setMcqQuestions(List.of(q1, q2, q3, q4));

        // 3 Scenarios
        ScenarioQuestion s1 = new ScenarioQuestion("s1", "Design high concurrency rate limiter", "Rate limiting", "Token bucket", "System Design", "HARD");
        ScenarioQuestion s2 = new ScenarioQuestion("s2", "Design cache invalidation", "Caching", "Cache aside", "System Design", "MEDIUM");
        ScenarioQuestion s3 = new ScenarioQuestion("s3", "Design database sharding", "Database", "Consistent hashing", "System Design", "HARD");
        session.setScenarioQuestions(List.of(s1, s2, s3));

        // 3 Projects
        ProjectQuestion p1 = new ProjectQuestion("p1", "Explain your backend API architecture", "Primary project", "Clean architecture", "Backend", "MEDIUM");
        ProjectQuestion p2 = new ProjectQuestion("p2", "How did you optimize slow SQL queries?", "Primary project", "Indexing and EXPLAIN plan", "Database", "HARD");
        ProjectQuestion p3 = new ProjectQuestion("p3", "How did you manage authentication and JWT expiry?", "Security project", "Refresh tokens & Redis blacklist", "Security", "MEDIUM");
        session.setProjectQuestions(List.of(p1, p2, p3));

        Map<String, String> mcqAnswers = new HashMap<>();
        mcqAnswers.put("q1", "A");
        mcqAnswers.put("q2", "B");
        mcqAnswers.put("q3", "C");
        mcqAnswers.put("q4", "D");
        session.setMcqAnswers(mcqAnswers);

        Map<String, String> scenarioAnswers = new HashMap<>();
        scenarioAnswers.put("s1", "Implement Redis Token Bucket filter with sliding window rate limiting.");
        scenarioAnswers.put("s2", "Use cache-aside pattern with write-through invalidation and sensible TTL.");
        scenarioAnswers.put("s3", "Implement consistent hashing algorithm with virtual nodes to balance partitions.");
        session.setScenarioAnswers(scenarioAnswers);

        Map<String, String> projectAnswers = new HashMap<>();
        projectAnswers.put("p1", "Designed layered architecture using Spring Boot, JPA, and PostgreSQL.");
        projectAnswers.put("p2", "Analyzed query plans with EXPLAIN ANALYZE and added composite B-Tree indexes.");
        projectAnswers.put("p3", "Secured REST API using JWT tokens with stateless validation and Redis revocation.");
        session.setProjectAnswers(projectAnswers);

        testSessionRepository.save(session);
    }

    @AfterEach
    void tearDown() {
        profileRepository.delete(profile);
        testSessionRepository.delete(session);
        submissionRepository.findFirstByTestId(testId).ifPresent(submissionRepository::delete);
        resultRepository.findByTestId(testId).ifPresent(resultRepository::delete);
    }

    @Test
    void testAsyncSubmissionPersistsStateAndReturnsProcessingStatus() {
        Submission submission = testService.submitTest(testId, testUserId);

        assertNotNull(submission);
        assertEquals(testId, submission.getTestId());
        assertEquals(testUserId, submission.getUserId());

        TestSession updatedSession = testSessionRepository.findById(testId).orElse(null);
        assertNotNull(updatedSession);
        assertEquals(TestStatus.SUBMITTED, updatedSession.getStatus());
        assertTrue("PROCESSING".equalsIgnoreCase(updatedSession.getEvaluationStatus()) ||
                   "COMPLETED".equalsIgnoreCase(updatedSession.getEvaluationStatus()));

        Map<String, Object> statusMap = testService.getEvaluationStatus(testId, testUserId);
        assertNotNull(statusMap);
        assertEquals(testId, statusMap.get("testId"));
        assertTrue("PROCESSING".equals(statusMap.get("status")) || "COMPLETED".equals(statusMap.get("status")));
    }

    @Test
    void testDuplicateSubmitDoesNotLaunchDuplicateJobs() {
        Submission firstSubmission = testService.submitTest(testId, testUserId);
        assertNotNull(firstSubmission);

        Submission duplicateSubmission = testService.submitTest(testId, testUserId);
        assertNotNull(duplicateSubmission);
        assertEquals(testId, duplicateSubmission.getTestId());

        long count = submissionRepository.findByUserId(testUserId).stream()
                .filter(s -> testId.equals(s.getTestId()))
                .count();
        assertEquals(1, count, "Must only have exactly 1 submission record for testId");
    }

    @Test
    void testUnauthorizedUserCannotSubmitOrQueryStatus() {
        assertThrows(ForbiddenException.class, () -> {
            testService.submitTest(testId, otherUserId);
        }, "Intruder cannot submit test session owned by candidate");

        assertThrows(ForbiddenException.class, () -> {
            testService.getEvaluationStatus(testId, otherUserId);
        }, "Intruder cannot query evaluation status of test session owned by candidate");
    }

    @Test
    void testCompletedEvaluationReflectsInStatusWithResultId() {
        session.setStatus(TestStatus.SUBMITTED);
        session.setEvaluationStatus("PROCESSING");
        testSessionRepository.save(session);

        Submission sub = new Submission();
        sub.setTestId(testId);
        sub.setUserId(testUserId);
        sub.setStatus(SubmissionStatus.EVALUATING);
        submissionRepository.save(sub);

        asyncService.runEvaluation(testId);

        TestSession evaluated = testSessionRepository.findById(testId).orElse(null);
        assertNotNull(evaluated);
        assertEquals(TestStatus.EVALUATED, evaluated.getStatus());
        assertEquals("COMPLETED", evaluated.getEvaluationStatus());

        Map<String, Object> status = testService.getEvaluationStatus(testId, testUserId);
        assertEquals("COMPLETED", status.get("status"));
        assertNotNull(status.get("resultId"), "Completed status must provide resultId");

        Result result = resultRepository.findByTestId(testId).orElse(null);
        assertNotNull(result);
        assertEquals(100.0, result.getMcqScore(), 0.01);
        assertTrue(result.getFinalScore() > 0);
    }
}
