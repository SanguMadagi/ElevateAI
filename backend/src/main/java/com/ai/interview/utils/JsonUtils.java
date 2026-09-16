package com.ai.interview.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtils {

    public static String cleanJsonResponse(String response) {
        if (response == null || response.isEmpty()) {
            return "{}";
        }
        
        String cleaned = response.trim();
        
        // Remove markdown code blocks if present
        if (cleaned.contains("```")) {
            // Try to extract content between ```json and ```
            int jsonStart = cleaned.indexOf("```json");
            if (jsonStart != -1) {
                int start = jsonStart + 7;
                int end = cleaned.indexOf("```", start);
                if (end != -1) {
                    cleaned = cleaned.substring(start, end).trim();
                    return cleaned;
                }
            }
            
            // Try to extract content between ``` and ```
            int firstBlock = cleaned.indexOf("```");
            int secondBlock = cleaned.indexOf("```", firstBlock + 3);
            if (firstBlock != -1 && secondBlock != -1) {
                cleaned = cleaned.substring(firstBlock + 3, secondBlock).trim();
                return cleaned;
            }
        }

        // Final attempt: find first { or [ and last } or ]
        int firstBrace = cleaned.indexOf('{');
        int firstBracket = cleaned.indexOf('[');
        int lastBrace = cleaned.lastIndexOf('}');
        int lastBracket = cleaned.lastIndexOf(']');

        int start = -1;
        int end = -1;

        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            start = firstBrace;
            end = lastBrace;
        } else if (firstBracket != -1) {
            start = firstBracket;
            end = lastBracket;
        }

        if (start != -1 && end != -1 && end > start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned;
    }
}
