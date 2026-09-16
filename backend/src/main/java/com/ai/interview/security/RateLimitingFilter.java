package com.ai.interview.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(2) // Run after TraceFilter
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter implements Filter {

    private final StringRedisTemplate redisTemplate;
    
    // Limits: 100 requests per minute
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final String RATE_LIMIT_PREFIX = "rate:limit:";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            String clientKey = getClientIdentifier(httpRequest);
            String redisKey = RATE_LIMIT_PREFIX + clientKey;

            try {
                Long currentCount = redisTemplate.opsForValue().increment(redisKey);
                if (currentCount != null && currentCount == 1) {
                    redisTemplate.expire(redisKey, 1, TimeUnit.MINUTES);
                }

                if (currentCount != null && currentCount > MAX_REQUESTS_PER_MINUTE) {
                    log.warn("Rate limit exceeded for client {}: {} requests in current minute", clientKey, currentCount);
                    httpResponse.setStatus(429); // Too Many Requests
                    httpResponse.setContentType("application/json");
                    httpResponse.getWriter().write("{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Please try again in a minute.\"}");
                    return;
                }
            } catch (Exception e) {
                // Fail-safe: if Redis is down, don't block API requests
                log.error("Redis rate limiter failed, falling back to bypass: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private String getClientIdentifier(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return "token:" + String.valueOf(authHeader.substring(7).hashCode());
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return "ip:" + ip;
    }
}
