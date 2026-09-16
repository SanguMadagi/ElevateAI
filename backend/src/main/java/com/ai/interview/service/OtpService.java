package com.ai.interview.service;

import com.ai.interview.dto.SignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public static final String SIGNUP_PREFIX = "OTP_SIGNUP:";
    public static final String RESET_PREFIX = "OTP_RESET:";
    private static final String ATTEMPT_PREFIX = "OTP_ATTEMPTS:";
    private static final String USER_DATA_PREFIX = "PENDING_USER:";
    
    private static final long OTP_EXPIRATION_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    public String generateSignupOtp(String email, SignupRequest signupRequest) {
        String otp = generateNumericOtp();
        redisTemplate.opsForValue().set(SIGNUP_PREFIX + email, otp, OTP_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        redisTemplate.delete(ATTEMPT_PREFIX + email); // Reset attempts on new OTP
        try {
            String userDataJson = objectMapper.writeValueAsString(signupRequest);
            redisTemplate.opsForValue().set(USER_DATA_PREFIX + email, userDataJson, OTP_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException("Error saving user data to Redis", e);
        }
        return otp;
    }

    public String generateResetOtp(String email) {
        String otp = generateNumericOtp();
        redisTemplate.opsForValue().set(RESET_PREFIX + email, otp, OTP_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        redisTemplate.delete(ATTEMPT_PREFIX + email); // Reset attempts on new OTP
        return otp;
    }

    private String generateNumericOtp() {
        return String.format("%06d", secureRandom.nextInt(1000000));
    }

    public boolean verifyOtp(String email, String otp, String prefix) {
        String attemptKey = ATTEMPT_PREFIX + email;
        String storedOtp = redisTemplate.opsForValue().get(prefix + email);

        if (storedOtp == null) {
            return false;
        }

        // Check if max attempts reached
        String attemptsStr = redisTemplate.opsForValue().get(attemptKey);
        int attempts = (attemptsStr != null) ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_ATTEMPTS) {
            log.warn("Max OTP attempts reached for email: {}", email);
            invalidateOtp(email, prefix);
            return false;
        }

        if (storedOtp.equals(otp)) {
            redisTemplate.delete(attemptKey);
            return true;
        } else {
            // Increment attempts
            redisTemplate.opsForValue().increment(attemptKey);
            redisTemplate.expire(attemptKey, OTP_EXPIRATION_MINUTES, TimeUnit.MINUTES);
            return false;
        }
    }

    private void invalidateOtp(String email, String prefix) {
        redisTemplate.delete(prefix + email);
        redisTemplate.delete(ATTEMPT_PREFIX + email);
        if (SIGNUP_PREFIX.equals(prefix)) {
            redisTemplate.delete(USER_DATA_PREFIX + email);
        }
    }

    public SignupRequest getPendingUser(String email) {
        String userDataJson = redisTemplate.opsForValue().get(USER_DATA_PREFIX + email);
        if (userDataJson == null) return null;
        try {
            return objectMapper.readValue(userDataJson, SignupRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Error reading user data from Redis", e);
        }
    }

    public void clearSignupData(String email) {
        redisTemplate.delete(SIGNUP_PREFIX + email);
        redisTemplate.delete(USER_DATA_PREFIX + email);
        redisTemplate.delete(ATTEMPT_PREFIX + email);
    }

    public void clearResetData(String email) {
        redisTemplate.delete(RESET_PREFIX + email);
        redisTemplate.delete(ATTEMPT_PREFIX + email);
    }
}
