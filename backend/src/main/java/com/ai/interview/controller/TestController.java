
package com.ai.interview.controller;

import com.ai.interview.model.TestSession;
import com.ai.interview.model.Submission;
import com.ai.interview.model.Result;
import com.ai.interview.service.TestService;
import com.ai.interview.repository.ResultRepository;
import com.ai.interview.exception.ResourceNotFoundException;
import com.ai.interview.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@RestController
@RequestMapping({"/api/test", "/api/v1/tests"})
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;
    private final ResultRepository resultRepository;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startTest(Authentication authentication) {
        String userId = authentication.getName();
        TestSession session = testService.startTest(userId);
        
        Map<String, Object> questionCounts = Map.of(
                "mcq", session.getMcqQuestions() != null ? session.getMcqQuestions().size() : 0,
                "scenario", session.getScenarioQuestions() != null ? session.getScenarioQuestions().size() : 0,
                "project", session.getProjectQuestions() != null ? session.getProjectQuestions().size() : 0
        );

        Map<String, Object> body = new HashMap<>();
        body.put("testId", session.getId());
        body.put("durationMinutes", session.getDurationMinutes());
        body.put("questionCounts", questionCounts);
        body.put("mcqQuestions", session.getMcqQuestions() != null ? session.getMcqQuestions() : List.of());
        body.put("scenarioQuestions", session.getScenarioQuestions() != null ? session.getScenarioQuestions() : List.of());
        body.put("projectQuestions", session.getProjectQuestions() != null ? session.getProjectQuestions() : List.of());

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{testId}")
    public ResponseEntity<TestSession> getTestSession(@PathVariable String testId, Authentication authentication) {
        String userId = authentication.getName();
        TestSession session = testService.getTestSession(testId, userId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{testId}/save-answer")
    public ResponseEntity<Map<String, Object>> saveAnswer(
            @PathVariable String testId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        
        String questionId = (String) body.get("questionId");
        String questionType = (String) body.get("questionType");
        String answer = (String) body.get("answer");
        int timeSpentSeconds = ((Number) body.getOrDefault("timeSpentSeconds", 0)).intValue();
        String status = (String) body.getOrDefault("status", "ANSWERED");

        testService.saveAnswer(authentication.getName(), testId, questionId, questionType, answer, timeSpentSeconds, status);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    @PostMapping("/{testId}/exit")
    public ResponseEntity<Map<String, Object>> exitTest(
            @PathVariable String testId,
            Authentication authentication) {
        String userId = authentication.getName();
        testService.exitTest(testId, userId);
        return ResponseEntity.ok(Map.of("exited", true));
    }

    @PostMapping("/{testId}/submit-violation")
    public ResponseEntity<Map<String, Object>> submitViolation(
            @PathVariable String testId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        
        String type = body.get("type");
        String severity = body.get("severity");
        String description = body.get("description");

        testService.submitViolation(authentication.getName(), testId, type, severity, description);
        return ResponseEntity.ok(Map.of("recorded", true));
    }

    @PostMapping("/{testId}/identity/enroll")
    public ResponseEntity<Map<String, Object>> enrollIdentity(
            @PathVariable String testId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Object embedding = body.get("embedding");
        if (!(embedding instanceof List<?>)) {
            throw new com.ai.interview.exception.BadRequestException("A face embedding is required");
        }
        List<Double> descriptor = new ArrayList<>();
        for (Object value : (List<?>) embedding) {
            if (!(value instanceof Number)) {
                throw new com.ai.interview.exception.BadRequestException("Invalid face embedding");
            }
            descriptor.add(((Number) value).doubleValue());
        }
        testService.enrollIdentity(authentication.getName(), testId, descriptor);
        return ResponseEntity.ok(Map.of("enrolled", true));
    }

    @PostMapping("/{testId}/identity/check")
    public ResponseEntity<Map<String, Object>> recordIdentityCheck(
            @PathVariable String testId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String status = String.valueOf(body.getOrDefault("status", "UNKNOWN"));
        int faceCount = ((Number) body.getOrDefault("faceCount", 0)).intValue();
        testService.recordIdentityCheck(authentication.getName(), testId, status, faceCount);
        return ResponseEntity.ok(Map.of("recorded", true));
    }

    @PostMapping("/{testId}/submit")
    public ResponseEntity<Map<String, Object>> submitTest(
            @PathVariable String testId,
            @RequestBody(required = false) Map<String, Object> payload,
            Authentication authentication) {
        String userId = authentication.getName();
        
        Submission submission = testService.submitTest(testId, userId, payload);
        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED).body(Map.of(
            "message", "Test submitted successfully",
            "testId", testId,
            "sessionId", testId,
            "submissionId", submission.getId() != null ? submission.getId() : "",
            "status", "PROCESSING"
        ));
    }

    @GetMapping({"/{testId}/evaluation-status", "/{testId}/status"})
    public ResponseEntity<Map<String, Object>> getEvaluationStatus(
            @PathVariable String testId,
            Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(testService.getEvaluationStatus(testId, userId));
    }

    @GetMapping("/{testId}/result")
    public ResponseEntity<Result> getResult(@PathVariable String testId, Authentication authentication) {
        String userId = authentication.getName();
        Result result = resultRepository.findByTestId(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation in progress"));

        if (!result.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied: you do not own this test result");
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-tests")
    public ResponseEntity<List<Map<String, Object>>> getMyTests(Authentication authentication) {
        String userId = authentication.getName();
        List<Map<String, Object>> myTests = testService.getMyTests(userId);
        return ResponseEntity.ok(myTests);
    }

    @PostMapping("/transcribe-audio")
    public ResponseEntity<Map<String, String>> transcribeAudio(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        String transcript = testService.transcribeAudio(file);
        return ResponseEntity.ok(Map.of("transcript", transcript != null ? transcript : ""));
    }
}
