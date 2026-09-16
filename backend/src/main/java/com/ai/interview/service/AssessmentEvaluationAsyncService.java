package com.ai.interview.service;

import com.ai.interview.model.Result;
import com.ai.interview.model.Submission;
import com.ai.interview.model.SubmissionStatus;
import com.ai.interview.model.TestSession;
import com.ai.interview.model.TestStatus;
import com.ai.interview.repository.ResultRepository;
import com.ai.interview.repository.SubmissionRepository;
import com.ai.interview.repository.TestSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentEvaluationAsyncService {

    private final EvaluationService evaluationService;
    private final TestSessionRepository testSessionRepository;
    private final SubmissionRepository submissionRepository;
    private final ResultRepository resultRepository;
    private final SkillAnalyzerService skillAnalyzerService;
    private final StringRedisTemplate redisTemplate;

    @Async("aiTaskExecutor")
    public void triggerEvaluationAsync(String testId) {
        runEvaluation(testId);
    }

    public void runEvaluation(String testId) {
        String evalLockKey = "lock:eval:" + testId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(evalLockKey, "evaluating", 5, TimeUnit.MINUTES);
        if (acquired == null || !acquired) {
            log.info("Evaluation already active in background for test session: {}", testId);
            return;
        }

        try {
            TestSession session = testSessionRepository.findById(testId).orElse(null);
            if (session == null) {
                log.error("TestSession not found for evaluation: {}", testId);
                return;
            }

            if (session.getStatus() == TestStatus.EVALUATED || "COMPLETED".equalsIgnoreCase(session.getEvaluationStatus())) {
                log.info("TestSession already completed evaluation: {}", testId);
                return;
            }

            log.info("Starting Gemini evaluation for test session: {}", testId);
            Result result = evaluationService.evaluateTest(session);
            resultRepository.save(result);

            session.setStatus(TestStatus.EVALUATED);
            session.setEvaluationStatus("COMPLETED");
            session.setEvaluationCompletedAt(LocalDateTime.now());
            session.setEvaluationError(null);
            testSessionRepository.save(session);

            Submission submission = submissionRepository.findFirstByTestId(testId).orElse(null);
            if (submission != null) {
                submission.setStatus(SubmissionStatus.COMPLETED);
                submission.setEvaluatedAt(LocalDateTime.now());
                submissionRepository.save(submission);
            }
            log.info("Gemini evaluation completed successfully for test session: {}", testId);
            try {
                skillAnalyzerService.analyzeUserPerformance(session.getUserId());
            } catch (Exception se) {
                log.warn("Non-fatal: failed to refresh skill analysis for user {}: {}", session.getUserId(), se.getMessage());
            }

        } catch (Exception e) {
            log.error("Evaluation failed for test session {}: {}", testId, e.getMessage(), e);
            TestSession session = testSessionRepository.findById(testId).orElse(null);
            if (session != null) {
                session.setEvaluationStatus("FAILED");
                session.setEvaluationError("We could not complete your AI evaluation. Please try again.");
                testSessionRepository.save(session);
            }
            Submission submission = submissionRepository.findFirstByTestId(testId).orElse(null);
            if (submission != null) {
                submission.setStatus(SubmissionStatus.FAILED);
                submissionRepository.save(submission);
            }
        } finally {
            redisTemplate.delete(evalLockKey);
        }
    }
}
