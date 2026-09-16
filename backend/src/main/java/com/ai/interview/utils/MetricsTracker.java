package com.ai.interview.utils;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MetricsTracker {
    public static final AtomicLong totalRequests = new AtomicLong(0);
    public static final AtomicLong totalErrors = new AtomicLong(0);
    public static final AtomicLong totalRequestDurationMs = new AtomicLong(0);
    
    public static final AtomicLong geminiCalls = new AtomicLong(0);
    public static final AtomicLong geminiFailures = new AtomicLong(0);
    public static final AtomicLong geminiTotalDurationMs = new AtomicLong(0);

    public static void recordRequest(long durationMs, boolean isError) {
        totalRequests.incrementAndGet();
        totalRequestDurationMs.addAndGet(durationMs);
        if (isError) {
            totalErrors.incrementAndGet();
        }
    }

    public static void recordGeminiCall(long durationMs, boolean success) {
        geminiCalls.incrementAndGet();
        geminiTotalDurationMs.addAndGet(durationMs);
        if (!success) {
            geminiFailures.incrementAndGet();
        }
    }
}

