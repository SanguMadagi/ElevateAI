package com.ai.interview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class GeminiLiveVerificationTest {

    @Autowired(required = false)
    private ChatClient chatClient;

    @Test
    void verifyGeminiLiveCall() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            Assumptions.assumeTrue(false, "Skipping live Gemini verification: GEMINI_API_KEY is not configured.");
        }

        String trimmedKey = apiKey.trim();
        boolean looksLikeValidGeminiKey = trimmedKey.matches("AIza[0-9A-Za-z_-]{35,}");
        if (!looksLikeValidGeminiKey) {
            Assumptions.assumeTrue(false, "Skipping live Gemini verification: GEMINI_API_KEY is missing or not a valid Google GenAI key.");
        }

        assertNotNull(chatClient, "ChatClient must be configured in application context");

        String response = null;
        try {
            response = chatClient.prompt()
                    .user("Return exactly: PONG")
                    .call()
                    .content();
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            String combined = (message + " " + (e.getCause() != null ? e.getCause().getMessage() : "")).trim();
            String lower = combined.toLowerCase();

            if (combined.contains("429") || combined.contains("RESOURCE_EXHAUSTED") || lower.contains("quota") || lower.contains("rate limit")) {
                Assumptions.assumeTrue(false, "Skipping live Gemini verification: provider quota/rate limit reached.");
            }
            if (combined.contains("401") || combined.contains("403") || combined.contains("UNAUTHENTICATED") || combined.contains("PERMISSION_DENIED") || lower.contains("unauthorized") || lower.contains("forbidden")) {
                fail("Gemini authentication/configuration failure: " + combined);
            }
            if (lower.contains("timeout") || lower.contains("connection") || lower.contains("unreachable") || lower.contains("temporarily unavailable") || lower.contains("failed to connect") || lower.contains("network")) {
                Assumptions.assumeTrue(false, "Skipping live Gemini verification: provider unavailable or network issue.");
            }
            fail("Gemini live verification failed unexpectedly: " + combined);
        }

        assertNotNull(response, "Gemini live verification returned a null response");
        String normalized = response.trim();
        assertFalse(normalized.isBlank(), "Gemini live verification returned an empty response");
        assertTrue(normalized.equalsIgnoreCase("PONG"), "Expected Gemini response to contain PONG, but got: " + normalized);
    }
}
