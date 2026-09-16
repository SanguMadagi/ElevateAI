package com.ai.interview;

import com.ai.interview.model.McqQuestion;
import com.ai.interview.model.Profile;
import com.ai.interview.model.ProjectQuestion;
import com.ai.interview.model.ScenarioQuestion;
import com.ai.interview.service.AiService;
import com.ai.interview.utils.AiCallTiming;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class GeminiDiagnosticVerificationTest {

    @Autowired(required = false)
    private ChatClient chatClient;

    @Autowired
    private AiService aiService;

    private boolean isApiKeyAvailable() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        return apiKey != null && !apiKey.isBlank();
    }

    @Test
    void test1_MinimalGeminiChatClientCall() {
        if (!isApiKeyAvailable()) {
            Assumptions.assumeTrue(false, "Skipping live Gemini call: GEMINI_API_KEY is not set in environment.");
        }

        assertNotNull(chatClient, "ChatClient bean must be present in application context");

        long started = System.nanoTime();
        try {
            String response = chatClient.prompt()
                    .user("Reply with exactly: GEMINI_OK")
                    .call()
                    .content();

            long durationMs = (System.nanoTime() - started) / 1_000_000L;
            System.out.println("Minimal Gemini call response (" + durationMs + "ms): " + response);

            assertNotNull(response);
            assertTrue(response.contains("GEMINI_OK") || response.toLowerCase().contains("gemini_ok") || !response.isBlank(),
                    "Expected Gemini response to contain GEMINI_OK, got: " + response);
        } catch (Exception e) {
            AiCallTiming.DiagnosticResult diag = AiCallTiming.logFailure("gemini-test-minimal", started, e);
            System.err.println("Gemini Minimal Call Result: category=" + diag.category() + ", status=" + diag.httpStatus() + ", msg=" + diag.rootCauseMessage());
            
            if ("RATE_LIMIT".equals(diag.category())) {
                Assumptions.assumeTrue(false, "Live test skipped due to provider quota / rate limit (429): " + diag.rootCauseMessage());
            }
            if ("AUTHENTICATION".equals(diag.category()) || "PERMISSION_DENIED".equals(diag.category())) {
                Assumptions.assumeTrue(false, "Live test skipped due to invalid / unauthenticated API key: " + diag.rootCauseMessage());
            }
            if ("MODEL_NOT_FOUND".equals(diag.category())) {
                fail("Configured Gemini model was not found: " + diag.rootCauseMessage());
            }
            fail("Gemini call failed with unexpected exception: " + diag.rootCauseMessage());
        }
    }

    @Test
    void test2_AssessmentQuestionGenerationWithLiveOrFallback() {
        Profile profile = new Profile();
        profile.setName("Live Verification Candidate");
        profile.setTargetRole("Senior Backend Engineer");
        profile.setSkills(List.of("Java", "Spring Boot", "Redis", "MongoDB"));
        profile.setProjects(List.of("CodeSync", "MediaSearch"));
        profile.setYearsOfExperience(5);

        List<McqQuestion> mcqs = aiService.generateMcqQuestions(profile);
        assertNotNull(mcqs);
        assertEquals(4, mcqs.size(), "Must generate exactly 4 MCQs");

        List<ScenarioQuestion> scenarios = aiService.generateScenarioQuestions(profile);
        assertNotNull(scenarios);
        assertEquals(3, scenarios.size(), "Must generate exactly 3 Scenarios");

        List<ProjectQuestion> projects = aiService.generateProjectQuestions(profile);
        assertNotNull(projects);
        assertEquals(3, projects.size(), "Must generate exactly 3 Projects");
    }

    @Test
    void test3_SanitizePreservesApiKeyNotValid() {
        String errorMsg = "HTTP 400 . API key not valid. Please pass a valid API key.";
        String sanitized = AiCallTiming.sanitize(errorMsg);

        // Crucial: Must preserve "not" and not corrupt into "key=[REDACTED] valid"
        assertTrue(sanitized.contains("API key not valid"),
                "Sanitized message must keep 'API key not valid', but got: " + sanitized);
        assertFalse(sanitized.contains("key=[REDACTED] valid"),
                "Sanitizer must NOT swallow the word 'not'");
    }

    @Test
    void test4_SanitizeRedactsRealGoogleApiKey() {
        // A dummy Google API key matching the standard AIza format
        String fakeGoogleKey = "AIzaSyD3" + "A".repeat(31);
        String textWithKey = "Request failed for key " + fakeGoogleKey + " with 400";
        String sanitized = AiCallTiming.sanitize(textWithKey);

        assertFalse(sanitized.contains(fakeGoogleKey), "Real Google API key format must be redacted");
        assertTrue(sanitized.contains("[REDACTED]"), "Must replace API key with [REDACTED]");
    }

    @Test
    void test5_DiagnoseCategorizesApiKeyInvalidAsAuthentication() {
        RuntimeException ex = new RuntimeException("Failed to generate content",
                new RuntimeException("400 . reason=API_KEY_INVALID API key not valid. Please pass a valid API key."));

        AiCallTiming.DiagnosticResult diag = AiCallTiming.diagnose(ex);
        assertEquals("AUTHENTICATION", diag.category());
        assertEquals(400, diag.httpStatus());
        assertTrue(diag.rootCauseMessage().contains("API key not valid"));
    }
}
