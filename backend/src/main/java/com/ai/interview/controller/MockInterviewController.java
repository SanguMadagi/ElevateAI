package com.ai.interview.controller;

import com.ai.interview.exception.UnauthorizedException;
import com.ai.interview.model.InterviewSession;
import com.ai.interview.service.MockInterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;

    private String getUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            throw new UnauthorizedException("User must be authenticated");
        }
        return authentication.getName();
    }

    @PostMapping("/start")
    public ResponseEntity<InterviewSession> startInterview(
            Authentication authentication,
            @RequestBody(required = false) Map<String, String> payload) {
        String userId = getUserId(authentication);
        String topic = payload != null ? payload.get("topic") : null;
        InterviewSession session = mockInterviewService.startInterview(userId, topic);
        return ResponseEntity.ok(session);
    }

    @PostMapping({"/{sessionId}/answer", "/{sessionId}/message"})
    public ResponseEntity<InterviewSession> submitAnswer(
            Authentication authentication,
            @PathVariable String sessionId,
            @RequestBody Map<String, String> payload) {
        String userId = getUserId(authentication);
        String candidateAnswer = payload != null ? (payload.containsKey("answer") ? payload.get("answer") : payload.get("message")) : "";
        InterviewSession session = mockInterviewService.submitAnswer(sessionId, userId, candidateAnswer);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<InterviewSession> getInterviewSession(
            Authentication authentication,
            @PathVariable String sessionId) {
        String userId = getUserId(authentication);
        return ResponseEntity.ok(mockInterviewService.getInterviewSession(sessionId, userId));
    }

    @GetMapping("/my-interviews")
    public ResponseEntity<List<InterviewSession>> getMyInterviews(
            Authentication authentication) {
        String userId = getUserId(authentication);
        return ResponseEntity.ok(mockInterviewService.getUserInterviews(userId));
    }

    @PostMapping("/{sessionId}/exit")
    public ResponseEntity<InterviewSession> exitInterview(
            Authentication authentication,
            @PathVariable String sessionId) {
        String userId = getUserId(authentication);
        return ResponseEntity.ok(mockInterviewService.exitInterview(sessionId, userId));
    }

    @PostMapping("/transcribe")
    public ResponseEntity<Map<String, String>> transcribeAudio(
            Authentication authentication,
            @RequestBody Map<String, String> payload) {
        getUserId(authentication); // Ensure authenticated
        String audioBase64 = payload != null ? payload.get("audio") : null;
        String mimeType = payload != null ? payload.getOrDefault("mimeType", "audio/webm") : "audio/webm";
        String transcript = mockInterviewService.transcribeAudio(audioBase64, mimeType);
        return ResponseEntity.ok(Map.of("transcript", transcript != null ? transcript : ""));
    }
}

