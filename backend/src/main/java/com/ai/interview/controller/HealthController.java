package com.ai.interview.controller;

import com.ai.interview.service.AiService;
import com.ai.interview.utils.MetricsTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AiService aiService;

    @GetMapping("/health/live")
    public ResponseEntity<Map<String, String>> live() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", java.time.Instant.now().toString()
        ));
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> details = new HashMap<>();
        boolean overallHealthy = true;

        // MongoDB Health check
        try {
            mongoTemplate.executeCommand("{ping:1}");
            details.put("mongodb", "UP");
        } catch (Exception e) {
            log.error("MongoDB health check failed: {}", e.getMessage());
            details.put("mongodb", "DOWN");
            overallHealthy = false;
        }

        // Redis Health check
        try {
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            String redisStatus = "PONG".equalsIgnoreCase(ping) || "OK".equalsIgnoreCase(ping) ? "UP" : "DOWN";
            details.put("redis", redisStatus);
            if (!"UP".equals(redisStatus)) {
                overallHealthy = false;
            }
        } catch (Exception e) {
            log.error("Redis health check failed: {}", e.getMessage());
            details.put("redis", "DOWN");
            overallHealthy = false;
        }

        details.put("status", overallHealthy ? "UP" : "DOWN");
        
        if (overallHealthy) {
            return ResponseEntity.ok(details);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(details);
        }
    }

    @GetMapping("/metrics/system")
    public ResponseEntity<Map<String, Object>> metrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        long requests = MetricsTracker.totalRequests.get();
        long errors = MetricsTracker.totalErrors.get();
        long totalReqDuration = MetricsTracker.totalRequestDurationMs.get();
        
        metrics.put("totalRequests", requests);
        metrics.put("totalErrors", errors);
        metrics.put("errorRate", requests > 0 ? (double) errors / requests : 0.0);
        metrics.put("avgRequestLatencyMs", requests > 0 ? (double) totalReqDuration / requests : 0.0);
        
        long aiCalls = MetricsTracker.geminiCalls.get();
        long aiFailures = MetricsTracker.geminiFailures.get();
        long aiDuration = MetricsTracker.geminiTotalDurationMs.get();
        
        metrics.put("aiCalls", aiCalls);
        metrics.put("aiFailures", aiFailures);
        metrics.put("aiErrorRate", aiCalls > 0 ? (double) aiFailures / aiCalls : 0.0);
        metrics.put("avgAiLatencyMs", aiCalls > 0 ? (double) aiDuration / aiCalls : 0.0);
        
        return ResponseEntity.ok(metrics);
    }
}

