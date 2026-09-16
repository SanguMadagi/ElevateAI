package com.ai.interview;

import com.ai.interview.model.McqQuestion;
import com.ai.interview.model.Profile;
import com.ai.interview.model.Result;
import com.ai.interview.model.TestSession;
import com.ai.interview.repository.ProfileRepository;
import com.ai.interview.repository.ResultRepository;
import com.ai.interview.repository.TestSessionRepository;
import com.ai.interview.service.AiService;
import com.ai.interview.service.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AssessmentMcqGenerationTest {

    @Autowired
    private AiService aiService;

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private TestSessionRepository testSessionRepository;

    @Test
    void testMcqGenerationReturnsExactlyFourQuestions() {
        Profile profile = new Profile();
        profile.setUserId("test-user-mcq-" + UUID.randomUUID().toString().substring(0, 8));
        profile.setName("Candidate Tester");
        profile.setTargetRole("Backend Java Engineer");
        profile.setExpertiseLevel("INTERMEDIATE");
        profile.setSkills(List.of("Java", "Spring Boot", "Redis"));
        profile.setYearsOfExperience(3);

        List<McqQuestion> mcqQuestions = aiService.generateMcqQuestions(profile);

        assertNotNull(mcqQuestions, "Generated MCQ list must not be null");
        assertEquals(4, mcqQuestions.size(), "Assessment must generate exactly 4 MCQ questions");
        for (McqQuestion q : mcqQuestions) {
            assertNotNull(q.getId(), "MCQ ID must not be null");
            assertNotNull(q.getQuestion(), "MCQ question text must not be null");
            assertNotNull(q.getOptions(), "MCQ options must not be null");
            assertEquals(4, q.getOptions().size(), "MCQ must have 4 options");
        }
    }

    @Test
    void testFallbackMcqGenerationReturnsExactlyFourQuestions() {
        Profile profile = new Profile();
        List<McqQuestion> fallbackQuestions = aiService.getFallbackMcqQuestions(profile);

        assertNotNull(fallbackQuestions, "Fallback MCQ list must not be null");
        assertEquals(4, fallbackQuestions.size(), "Fallback MCQ generator must return exactly 4 questions");
        for (McqQuestion q : fallbackQuestions) {
            assertNotNull(q.getId(), "Fallback MCQ ID must not be null");
            assertNotNull(q.getQuestion(), "Fallback MCQ question text must not be null");
            assertNotNull(q.getOptions(), "Fallback MCQ options must not be null");
            assertEquals(4, q.getOptions().size(), "Fallback MCQ must have 4 options");
            assertNotNull(q.getCorrectAnswer(), "Fallback MCQ correctAnswer must not be null");
        }
    }

    @Test
    void testEvaluationServiceWithFourMcqQuestions() {
        String testUserId = "user-eval-" + UUID.randomUUID().toString().substring(0, 8);
        String testId = "session-eval-" + UUID.randomUUID();

        Profile profile = new Profile();
        profile.setUserId(testUserId);
        profile.setName("Eval Candidate");
        profile.setTargetRole("Backend Developer");
        profile.setSkills(List.of("Java"));
        profileRepository.save(profile);

        TestSession session = new TestSession();
        session.setId(testId);
        session.setUserId(testUserId);
        session.setTargetRole("Backend Developer");

        // Prepare exactly 4 MCQs
        McqQuestion q1 = new McqQuestion("q1", "What is Java?", List.of("A", "B", "C", "D"), "A", "Java", "EASY");
        McqQuestion q2 = new McqQuestion("q2", "What is Spring?", List.of("A", "B", "C", "D"), "B", "Spring", "MEDIUM");
        McqQuestion q3 = new McqQuestion("q3", "What is Redis?", List.of("A", "B", "C", "D"), "C", "Redis", "MEDIUM");
        McqQuestion q4 = new McqQuestion("q4", "What is SQL?", List.of("A", "B", "C", "D"), "D", "Database", "EASY");
        session.setMcqQuestions(List.of(q1, q2, q3, q4));

        // Candidate answers 3 out of 4 correctly (75% MCQ score)
        Map<String, String> mcqAnswers = new HashMap<>();
        mcqAnswers.put("q1", "A"); // Correct
        mcqAnswers.put("q2", "B"); // Correct
        mcqAnswers.put("q3", "C"); // Correct
        mcqAnswers.put("q4", "A"); // Incorrect (correct is D)
        session.setMcqAnswers(mcqAnswers);

        session.setScenarioQuestions(List.of());
        session.setProjectQuestions(List.of());
        session.setScenarioAnswers(new HashMap<>());
        session.setProjectAnswers(new HashMap<>());
        testSessionRepository.save(session);

        Result result = evaluationService.evaluateTest(session);

        assertNotNull(result);
        assertEquals(75.0, result.getMcqScore(), 0.01, "MCQ score for 3/4 correct answers must be 75.0%");

        // Clean up
        profileRepository.delete(profile);
        testSessionRepository.delete(session);
        resultRepository.findByTestId(testId).ifPresent(resultRepository::delete);
    }
}
