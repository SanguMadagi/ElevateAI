package com.ai.interview.service;

import com.ai.interview.dto.AuthResponse;
import com.ai.interview.dto.LoginRequest;
import com.ai.interview.dto.SignupRequest;
import com.ai.interview.exception.*;
import com.ai.interview.model.User;
import com.ai.interview.repository.UserRepository;
import com.ai.interview.repository.ProfileRepository;
import com.ai.interview.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String RESET_KEY_PREFIX = "reset:";

    private String generateNumericOtp() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        String name = ((request.getFirstName() != null ? request.getFirstName() : "") + " " +
                      (request.getLastName() != null ? request.getLastName() : "")).trim();
        if (name.isEmpty()) {
            name = "User";
        }
        user.setName(name);
        user.setEmail(request.getEmail());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        } else {
            user.setPassword(""); // Set during OTP verification in the alternate flow
        }
        user.setRole("CANDIDATE");
        user.setEmailVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        String otp = generateNumericOtp();
        redisTemplate.opsForValue().set(OTP_KEY_PREFIX + request.getEmail(), otp, 10, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set("OTP_SIGNUP:" + request.getEmail(), otp, 10, TimeUnit.MINUTES);

        emailService.sendOtpEmail(request.getEmail(), otp, "Your AI Interview OTP");
        log.info("Signup OTP generated and email sent for {}", request.getEmail());
    }

    public void resendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.isEmailVerified()) {
            throw new BadRequestException("Email already verified");
        }

        String otp = generateNumericOtp();
        redisTemplate.opsForValue().set(OTP_KEY_PREFIX + email, otp, 10, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set("OTP_SIGNUP:" + email, otp, 10, TimeUnit.MINUTES);

        emailService.sendOtpEmail(email, otp, "Your AI Interview OTP");
        log.info("Resend OTP generated and email sent for {}", email);
    }

    public AuthResponse verifyOtp(String email, String otp, String optionalPassword) {
        String storedOtp = redisTemplate.opsForValue().get(OTP_KEY_PREFIX + email);
        if (storedOtp == null) {
            throw new BadRequestException("OTP expired");
        }
        if (!storedOtp.equals(otp)) {
            throw new BadRequestException("Invalid OTP");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setEmailVerified(true);
        if (optionalPassword != null && !optionalPassword.isBlank()) {
            user.setPassword(passwordEncoder.encode(optionalPassword));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        redisTemplate.delete(OTP_KEY_PREFIX + email);
        redisTemplate.delete("OTP_SIGNUP:" + email);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, new AuthResponse.UserDto(user.getId(), user.getName(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRole()));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, new AuthResponse.UserDto(user.getId(), user.getName(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRole()));
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String otp = generateNumericOtp();
        redisTemplate.opsForValue().set(RESET_KEY_PREFIX + email, otp, 15, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set("OTP_RESET:" + email, otp, 15, TimeUnit.MINUTES);

        emailService.sendOtpEmail(email, otp, "Your Password Reset OTP");
        log.info("Reset OTP generated and email sent for {}", email);
    }

    public void resetPassword(String email, String otp, String newPassword) {
        String storedOtp = redisTemplate.opsForValue().get(RESET_KEY_PREFIX + email);
        if (storedOtp == null) {
            throw new BadRequestException("OTP expired");
        }
        if (!storedOtp.equals(otp)) {
            throw new BadRequestException("Invalid OTP");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        redisTemplate.delete(RESET_KEY_PREFIX + email);
        redisTemplate.delete("OTP_RESET:" + email);
        log.info("Password reset successful for {}", email);
    }

    public Map<String, Object> getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String profileStatus = "INCOMPLETE";
        com.ai.interview.model.Profile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile != null) {
            if (profile.getTargetRole() != null && !profile.getTargetRole().isBlank() &&
                profile.getSkills() != null && !profile.getSkills().isEmpty()) {
                profileStatus = "COMPLETE";
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("name", user.getName());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("email", user.getEmail());
        response.put("role", user.getRole());
        response.put("profileStatus", profileStatus);
        return response;
    }
}
