package com.ai.interview.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class AiCallTiming {

    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\b([1-5][0-9]{2})\\b");
    // Matches only actual credential assignments like key=VALUE, token: VALUE, api-key=VALUE
    // Requires '=' or ':' so natural phrases like "API key not valid" are never corrupted
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile("(?i)(key|token|auth|bearer|secret|api-key)[=:]+\\s*([a-zA-Z0-9_\\-\\.]+)");
    // Redacts Google API keys (which start with 'AIza' followed by 35 base64 characters)
    private static final Pattern GOOGLE_API_KEY_PATTERN = Pattern.compile("AIza[0-9A-Za-z\\-_]{35}");

    private AiCallTiming() {
    }

    public static void log(String operation, long startedNanos, boolean success, String detail) {
        long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        log.info("AI_CALL operation={} durationMs={} success={} detail={}",
                operation, durationMs, success, sanitize(detail));
    }

    public static DiagnosticResult logFailure(String operation, long startedNanos, Throwable throwable) {
        long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        DiagnosticResult diagnostic = diagnose(throwable);
        
        log.error("AI_CALL operation={} durationMs={} success=false category={} providerStatus={} errorClass={} rootCauseClass={} rootCauseMessage=\"{}\"",
                operation,
                durationMs,
                diagnostic.category(),
                diagnostic.httpStatus() != null ? diagnostic.httpStatus() : "N/A",
                diagnostic.exceptionClass(),
                diagnostic.rootCauseClass(),
                sanitize(diagnostic.rootCauseMessage())
        );

        return diagnostic;
    }

    public record DiagnosticResult(
            String category,
            Integer httpStatus,
            String exceptionClass,
            String rootCauseClass,
            String rootCauseMessage
    ) {}

    public static DiagnosticResult diagnose(Throwable throwable) {
        if (throwable == null) {
            return new DiagnosticResult("UNKNOWN", null, "Unknown", "Unknown", "No exception provided");
        }

        Throwable root = throwable;
        StringBuilder fullChain = new StringBuilder();
        Integer detectedStatus = null;

        while (root != null) {
            String msg = root.getMessage() != null ? root.getMessage() : "";
            fullChain.append(" ").append(root.getClass().getName()).append(": ").append(msg);
            
            if (detectedStatus == null) {
                Matcher matcher = HTTP_STATUS_PATTERN.matcher(msg);
                if (matcher.find()) {
                    try {
                        int code = Integer.parseInt(matcher.group(1));
                        if (code >= 400 && code <= 599) {
                            detectedStatus = code;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (root.getCause() == null || root.getCause() == root) {
                break;
            }
            root = root.getCause();
        }

        String combined = fullChain.toString().toLowerCase(Locale.ROOT);
        String category;

        if (combined.contains("401") || combined.contains("unauthenticated") || combined.contains("api_key_invalid") || combined.contains("api key not valid") || combined.contains("invalid api key") || combined.contains("unauthorized")) {
            category = "AUTHENTICATION";
            if (detectedStatus == null) detectedStatus = 401;
        } else if (combined.contains("403") || combined.contains("permission_denied") || combined.contains("forbidden")) {
            category = "PERMISSION_DENIED";
            if (detectedStatus == null) detectedStatus = 403;
        } else if (combined.contains("404") || combined.contains("not_found") || combined.contains("model not found") || combined.contains("models/")) {
            category = "MODEL_NOT_FOUND";
            if (detectedStatus == null) detectedStatus = 404;
        } else if (combined.contains("429") || combined.contains("resource_exhausted") || combined.contains("quota") || combined.contains("rate limit")) {
            category = "RATE_LIMIT";
            if (detectedStatus == null) detectedStatus = 429;
        } else if (combined.contains("jsonparseexception") || combined.contains("jsonmappingexception") || combined.contains("deserialization") || combined.contains("beanoutputconverter") || combined.contains("structured output")) {
            category = "STRUCTURED_OUTPUT_PARSING";
        } else if (combined.contains("timeout") || combined.contains("timed out") || combined.contains("sockettimeout") || combined.contains("deadline_exceeded")) {
            category = "TIMEOUT";
            if (detectedStatus == null) detectedStatus = 408;
        } else if (combined.contains("connection refused") || combined.contains("unknownhost") || combined.contains("connectexception") || combined.contains("sslhandshake")) {
            category = "CONNECTION_FAILURE";
        } else if (combined.contains("invalid_argument") || combined.contains("illegalargumentexception")) {
            category = "VALIDATION_ERROR";
            if (detectedStatus == null) detectedStatus = 400;
        } else if (combined.contains("500") || combined.contains("503") || combined.contains("internal server error") || combined.contains("unavailable")) {
            category = "PROVIDER_ERROR";
            if (detectedStatus == null) detectedStatus = 503;
        } else {
            category = "GENERIC_ERROR";
        }

        return new DiagnosticResult(
                category,
                detectedStatus,
                throwable.getClass().getSimpleName(),
                root != null ? root.getClass().getSimpleName() : throwable.getClass().getSimpleName(),
                root != null && root.getMessage() != null ? root.getMessage() : (throwable.getMessage() != null ? throwable.getMessage() : "Unknown error")
        );
    }

    public static String sanitize(String text) {
        if (text == null) return "";
        String clean = GOOGLE_API_KEY_PATTERN.matcher(text).replaceAll("[REDACTED]");
        return SENSITIVE_KEY_PATTERN.matcher(clean).replaceAll("$1=[REDACTED]");
    }
}
