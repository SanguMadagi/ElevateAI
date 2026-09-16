package com.ai.interview.service;

import com.ai.interview.model.*;
import com.ai.interview.repository.*;
import com.ai.interview.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestService {

    private final TestSessionRepository testSessionRepository;
    private final ProfileRepository profileRepository;
    private final SubmissionRepository submissionRepository;
    private final ResultRepository resultRepository;
    private final AiService aiService;
    private final EvaluationService evaluationService;
    private final AssessmentEvaluationAsyncService assessmentEvaluationAsyncService;
    private final StringRedisTemplate redisTemplate;
    private final TaskExecutor aiTaskExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public TestSession getActiveTest(String userId) {
        List<TestSession> sessions = testSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (!sessions.isEmpty()) {
            TestSession latest = sessions.get(0);
            if (latest.getStatus() == TestStatus.CREATED || latest.getStatus() == TestStatus.IN_PROGRESS) {
                return latest;
            }
        }
        return null;
    }

    public TestSession startTest(String userId) {
        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user: " + userId));

        TestSession active = getActiveTest(userId);
        if (active != null) {
            log.info("User {} already has an active test: {}", userId, active.getId());
            return active;
        }

        log.info("Generating new test session for user: {}", userId);
        long generationStart = System.nanoTime();

        // Extract recent question history to prevent repeated questions across assessments
        List<String> previousMcqs = new ArrayList<>();
        List<String> previousScenarios = new ArrayList<>();
        List<String> previousProjects = new ArrayList<>();
        try {
            List<TestSession> pastSessions = testSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
            if (pastSessions != null) {
                for (TestSession past : pastSessions.stream().limit(5).toList()) {
                    if (past.getMcqQuestions() != null) {
                        past.getMcqQuestions().forEach(q -> { if (q.getQuestion() != null) previousMcqs.add(q.getQuestion()); });
                    }
                    if (past.getScenarioQuestions() != null) {
                        past.getScenarioQuestions().forEach(q -> { if (q.getQuestion() != null) previousScenarios.add(q.getQuestion()); });
                    }
                    if (past.getProjectQuestions() != null) {
                        past.getProjectQuestions().forEach(q -> { if (q.getQuestion() != null) previousProjects.add(q.getQuestion()); });
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve previous question history for user {}: {}", userId, e.getMessage());
        }

        CompletableFuture<List<McqQuestion>> mcqFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return aiService.generateMcqQuestions(profile, previousMcqs);
            } catch (Exception e) {
                log.warn("MCQ generation failed in async supplier, falling back: {}", e.getMessage());
                return aiService.getFallbackMcqQuestions(profile);
            }
        }, aiTaskExecutor);

        CompletableFuture<List<ScenarioQuestion>> scenarioFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return aiService.generateScenarioQuestions(profile, previousScenarios);
            } catch (Exception e) {
                log.warn("Scenario generation failed in async supplier, falling back: {}", e.getMessage());
                return aiService.getFallbackScenarioQuestions(profile);
            }
        }, aiTaskExecutor);

        CompletableFuture<List<ProjectQuestion>> projectFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return aiService.generateProjectQuestions(profile, previousProjects);
            } catch (Exception e) {
                log.warn("Project generation failed in async supplier, falling back: {}", e.getMessage());
                return aiService.getFallbackProjectQuestions(profile);
            }
        }, aiTaskExecutor);

        CompletableFuture.allOf(mcqFuture, scenarioFuture, projectFuture).join();

        List<McqQuestion> mcqs = mcqFuture.join();
        if (mcqs == null || mcqs.isEmpty()) {
            mcqs = aiService.getFallbackMcqQuestions(profile);
        }
        List<ScenarioQuestion> scenarios = scenarioFuture.join();
        if (scenarios == null || scenarios.isEmpty()) {
            scenarios = aiService.getFallbackScenarioQuestions(profile);
        }
        List<ProjectQuestion> projects = projectFuture.join();
        if (projects == null || projects.isEmpty()) {
            projects = aiService.getFallbackProjectQuestions(profile);
        }

        long totalDurationMs = (System.nanoTime() - generationStart) / 1_000_000L;
        com.ai.interview.utils.AiCallTiming.log("assessment-question-generation-phase", generationStart, true,
                String.format("mcqs=%d, scenarios=%d, projects=%d", mcqs.size(), scenarios.size(), projects.size()));
        log.info("ASSESSMENT_QUESTION_GENERATION completed durationMs={} for user: {} (MCQ={}, Scenario={}, Project={})",
                totalDurationMs, userId, mcqs.size(), scenarios.size(), projects.size());

        TestSession session = new TestSession();
        session.setUserId(userId);
        session.setProfileId(profile.getId());
        session.setTargetRole(profile.getTargetRole());
        session.setExpertiseLevel(profile.getExpertiseLevel());
        session.setStatus(TestStatus.CREATED);
        session.setMcqQuestions(mcqs);
        session.setScenarioQuestions(scenarios);
        session.setProjectQuestions(projects);
        session.setMcqAnswers(new HashMap<>());
        session.setScenarioAnswers(new HashMap<>());
        session.setProjectAnswers(new HashMap<>());
        session.setAnswers(new HashMap<>());
        session.setViolations(new ArrayList<>());
        session.setTimePerQuestion(new HashMap<>());
        session.setDurationMinutes(60);
        session.setCreatedAt(LocalDateTime.now());
        session.setStartedAt(LocalDateTime.now());

        TestSession saved = testSessionRepository.save(session);
        saved.setTestId(saved.getId()); // assign testId = id after save
        return testSessionRepository.save(saved);
    }

    public TestSession getTestSession(String testId, String userId) {
        TestSession session = testSessionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test session not found: " + testId));

        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied: you do not own this test session");
        }

        // Strip correct answer from MCQs to prevent cheating
        if (session.getMcqQuestions() != null) {
            List<McqQuestion> stripped = new ArrayList<>();
            for (McqQuestion original : session.getMcqQuestions()) {
                McqQuestion q = new McqQuestion();
                q.setId(original.getId());
                q.setQuestion(original.getQuestion());
                q.setOptions(original.getOptions());
                q.setCorrectAnswer(null); // Clear answer
                q.setCategory(original.getCategory());
                q.setDifficulty(original.getDifficulty());
                stripped.add(q);
            }
            session.setMcqQuestions(stripped);
        }

        return session;
    }

    public void saveAnswer(String userId, String testId, String questionId, String questionType, String answer, int timeSpentSeconds) {
        saveAnswer(userId, testId, questionId, questionType, answer, timeSpentSeconds, "ANSWERED");
    }

    public void saveAnswer(String userId, String testId, String questionId, String questionType, String answer, int timeSpentSeconds, String status) {
        TestSession session = testSessionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test session not found: " + testId));

        verifyOwnership(session, userId);

        if (session.getStatus() == TestStatus.SUBMITTED || session.getStatus() == TestStatus.EVALUATED || session.getStatus() == TestStatus.EXITED) {
            throw new BadRequestException("Test already finalized");
        }

        if (session.getStatus() == TestStatus.CREATED) {
            session.setStatus(TestStatus.IN_PROGRESS);
            session.setStartedAt(LocalDateTime.now());
        }

        String finalStatus = (status == null || status.trim().isEmpty()) ? "ANSWERED" : status.toUpperCase();

        if ("MCQ".equalsIgnoreCase(questionType)) {
            if (session.getMcqAnswers() == null) session.setMcqAnswers(new HashMap<>());
            if ("ANSWERED".equals(finalStatus) && answer != null) {
                session.getMcqAnswers().put(questionId, answer);
            }
        } else if ("SCENARIO".equalsIgnoreCase(questionType)) {
            if (session.getScenarioAnswers() == null) session.setScenarioAnswers(new HashMap<>());
            if ("ANSWERED".equals(finalStatus) && answer != null) {
                session.getScenarioAnswers().put(questionId, answer);
            }
        } else if ("PROJECT".equalsIgnoreCase(questionType)) {
            if (session.getProjectAnswers() == null) session.setProjectAnswers(new HashMap<>());
            if ("ANSWERED".equals(finalStatus) && answer != null) {
                session.getProjectAnswers().put(questionId, answer);
            }
        } else {
            throw new BadRequestException("Invalid question type: " + questionType);
        }

        if (session.getTimePerQuestion() == null) session.setTimePerQuestion(new HashMap<>());
        session.getTimePerQuestion().merge(questionId, (long) timeSpentSeconds, Long::sum);

        // Update the structured Answer map
        if (session.getAnswers() == null) session.setAnswers(new HashMap<>());
        long totalTimeSpent = session.getTimePerQuestion().getOrDefault(questionId, 0L);
        Answer ansObj = new Answer(questionId, answer, finalStatus, (int) totalTimeSpent);
        session.getAnswers().put(questionId, ansObj);

        testSessionRepository.save(session);

        // Redis caching for recovery
        try {
            Map<String, Object> cachedAnswers = Map.of(
                    "mcqAnswers", session.getMcqAnswers() != null ? session.getMcqAnswers() : Collections.emptyMap(),
                    "scenarioAnswers", session.getScenarioAnswers() != null ? session.getScenarioAnswers() : Collections.emptyMap(),
                    "projectAnswers", session.getProjectAnswers() != null ? session.getProjectAnswers() : Collections.emptyMap(),
                    "answers", session.getAnswers() != null ? session.getAnswers() : Collections.emptyMap(),
                    "timePerQuestion", session.getTimePerQuestion() != null ? session.getTimePerQuestion() : Collections.emptyMap()
            );
            redisTemplate.opsForValue().set("test:" + testId + ":answers", objectMapper.writeValueAsString(cachedAnswers), 2, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to cache answers in Redis: {}", e.getMessage());
        }
    }

    public void submitViolation(String userId, String testId, String type, String severity, String description) {
        TestSession session = testSessionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test session not found: " + testId));

        verifyOwnership(session, userId);

        Violation violation = new Violation();
        violation.setType(type);
        violation.setSeverity(severity);
        violation.setDescription(description);
        violation.setTimestamp(LocalDateTime.now());

        if (session.getViolations() == null) {
            session.setViolations(new ArrayList<>());
        }
        session.getViolations().add(violation);
        testSessionRepository.save(session);
        log.info("Recorded violation: {} on test session: {}", type, testId);
    }

    public void enrollIdentity(String userId, String testId, List<Double> embedding) {
        if (embedding == null || embedding.size() != 128 || embedding.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new BadRequestException("A live identity capture is required");
        }
        TestSession session = testSessionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test session not found: " + testId));
        verifyOwnership(session, userId);
        if (session.getIdentityEnrolledAt() != null) {
            throw new BadRequestException("Identity is already enrolled for this assessment");
        }
        session.setIdentityEmbedding(List.copyOf(embedding));
        session.setIdentityEnrolledAt(LocalDateTime.now());
        session.setIdentityVerificationStatus("ENROLLED");
        session.setLastIdentityVerificationAt(LocalDateTime.now());
        testSessionRepository.save(session);
    }

    public void recordIdentityCheck(String userId, String testId, String status, int faceCount) {
        TestSession session = testSessionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test session not found: " + testId));
        verifyOwnership(session, userId);
        if (session.getIdentityEnrolledAt() == null) {
            throw new BadRequestException("Identity has not been enrolled for this assessment");
        }
        session.setLastIdentityVerificationAt(LocalDateTime.now());
        session.setIdentityVerificationStatus(status);
        if ("CAMERA_DISCONNECTED".equalsIgnoreCase(status)) {
            session.setCameraDisconnectEvents(session.getCameraDisconnectEvents() + 1);
        }
        if ("IDENTITY_VERIFIED".equalsIgnoreCase(status)) session.setSuccessfulIdentityChecks(session.getSuccessfulIdentityChecks() + 1);
        else session.setFailedIdentityChecks(session.getFailedIdentityChecks() + 1);
        if (faceCount == 0) session.setNoFaceEvents(session.getNoFaceEvents() + 1);
        if (faceCount > 1) session.setMultipleFaceEvents(session.getMultipleFaceEvents() + 1);
        testSessionRepository.save(session);
    }

    public void exitTest(String testId, String userId) {
        TestSession session = testSessionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test session not found: " + testId));

        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied: you do not own this test session");
        }

        if (session.getStatus() == TestStatus.SUBMITTED || session.getStatus() == TestStatus.EVALUATED || session.getStatus() == TestStatus.EXITED) {
            return;
        }

        session.setStatus(TestStatus.EXITED);
        testSessionRepository.save(session);
        log.info("Test session: {} exited by user: {}", testId, userId);
    }

    private void verifyOwnership(TestSession session, String userId) {
        if (!userId.equals(session.getUserId())) {
            throw new ForbiddenException("Access denied: you do not own this test session");
        }
    }

    public Submission submitTest(String testId, String userId) {
        return submitTest(testId, userId, null);
    }

    public Submission submitTest(String testId, String userId, Map<String, Object> payload) {
        TestSession session = testSessionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test session not found: " + testId));

        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied: you do not own this test session");
        }

        // If already completed evaluation, return the existing completed submission
        if (session.getStatus() == TestStatus.EVALUATED || "COMPLETED".equalsIgnoreCase(session.getEvaluationStatus())) {
            return submissionRepository.findFirstByTestId(testId)
                    .orElseGet(() -> {
                        Submission s = new Submission();
                        s.setTestId(testId);
                        s.setUserId(userId);
                        s.setStatus(SubmissionStatus.COMPLETED);
                        s.setSubmittedAt(session.getSubmittedAt());
                        s.setEvaluatedAt(session.getEvaluationCompletedAt());
                        return s;
                    });
        }

        // If already submitted and actively processing, return existing processing submission without triggering duplicate jobs
        if ("PROCESSING".equalsIgnoreCase(session.getEvaluationStatus()) || 
            (session.getStatus() == TestStatus.SUBMITTED && !"FAILED".equalsIgnoreCase(session.getEvaluationStatus()))) {
            return submissionRepository.findFirstByTestId(testId)
                    .orElseGet(() -> {
                        Submission s = new Submission();
                        s.setTestId(testId);
                        s.setUserId(userId);
                        s.setStatus(SubmissionStatus.EVALUATING);
                        s.setSubmittedAt(session.getSubmittedAt());
                        return s;
                    });
        }

        String lockKey = "lock:submit:" + testId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", 10, TimeUnit.SECONDS);
        if (acquired == null || !acquired) {
            // Check if already submitted/evaluating
            TestSession currentSession = testSessionRepository.findById(testId).orElse(null);
            if (currentSession != null && (currentSession.getStatus() == TestStatus.SUBMITTED || currentSession.getStatus() == TestStatus.EVALUATED)) {
                return submissionRepository.findFirstByTestId(testId)
                        .orElseThrow(() -> new ResourceNotFoundException("Submission mapping not found for submitted test"));
            }
            throw new BadRequestException("Another submission is in progress. Please try again.");
        }

        try {
            // Fetch session again inside the lock to check status
            TestSession activeSession = testSessionRepository.findById(testId)
                    .orElseThrow(() -> new ResourceNotFoundException("Test session not found: " + testId));

            if (activeSession.getStatus() == TestStatus.EVALUATED || "COMPLETED".equalsIgnoreCase(activeSession.getEvaluationStatus())) {
                return submissionRepository.findFirstByTestId(testId)
                        .orElseThrow(() -> new ResourceNotFoundException("Submission mapping not found for submitted test"));
            }

            if (payload != null) {
                populateAnswersFromPayload(activeSession, payload);
            }

            activeSession.setStatus(TestStatus.SUBMITTED);
            activeSession.setEvaluationStatus("PROCESSING");
            activeSession.setEvaluationStartedAt(LocalDateTime.now());
            activeSession.setEvaluationError(null);
            activeSession.setSubmittedAt(LocalDateTime.now());
            testSessionRepository.save(activeSession);

            Submission submission = submissionRepository.findFirstByTestId(testId).orElse(new Submission());
            submission.setTestId(testId);
            submission.setUserId(userId);
            submission.setStatus(SubmissionStatus.EVALUATING);
            submission.setSubmittedAt(LocalDateTime.now());
            Submission savedSubmission = submissionRepository.save(submission);

            // Asynchronously trigger background evaluation via aiTaskExecutor
            assessmentEvaluationAsyncService.triggerEvaluationAsync(testId);

            return savedSubmission;

        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public Map<String, Object> getEvaluationStatus(String testId, String userId) {
        TestSession session = testSessionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test session not found: " + testId));

        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied: you do not own this test session");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("testId", testId);
        response.put("sessionId", testId);

        if (session.getStatus() == TestStatus.EVALUATED || "COMPLETED".equalsIgnoreCase(session.getEvaluationStatus())) {
            response.put("status", "COMPLETED");
            resultRepository.findByTestId(testId).ifPresent(r -> response.put("resultId", r.getId()));
            return response;
        }

        if ("FAILED".equalsIgnoreCase(session.getEvaluationStatus())) {
            response.put("status", "FAILED");
            response.put("message", session.getEvaluationError() != null ? session.getEvaluationError() : "We could not complete your AI evaluation. Please try again.");
            return response;
        }

        if (session.getStatus() == TestStatus.SUBMITTED || "PROCESSING".equalsIgnoreCase(session.getEvaluationStatus())) {
            response.put("status", "PROCESSING");
            return response;
        }

        if (session.getStatus() == TestStatus.EXITED) {
            response.put("status", "EXITED");
            return response;
        }

        response.put("status", "IN_PROGRESS");
        return response;
    }

    private void populateAnswersFromPayload(TestSession session, Map<String, Object> payload) {
        if (payload == null) return;
        
        // 1. MCQ
        if (payload.containsKey("mcqAnswers")) {
            Object mcqObj = payload.get("mcqAnswers");
            if (mcqObj instanceof List) {
                List<?> list = (List<?>) mcqObj;
                if (session.getMcqAnswers() == null) session.setMcqAnswers(new HashMap<>());
                List<McqQuestion> questions = session.getMcqQuestions();
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) item;
                        String questionText = (String) map.get("question");
                        String selectedOption = (String) map.get("selectedOption");
                        if (selectedOption != null) {
                            boolean matched = false;
                            if (questionText != null && questions != null) {
                                for (McqQuestion q : questions) {
                                    if (questionText.trim().equalsIgnoreCase(q.getQuestion().trim())) {
                                        session.getMcqAnswers().put(q.getId(), selectedOption);
                                        matched = true;
                                        break;
                                    }
                                }
                            }
                            if (!matched && questions != null && i < questions.size()) {
                                session.getMcqAnswers().put(questions.get(i).getId(), selectedOption);
                            }
                        }
                    }
                }
            }
        }

        // 2. Scenario
        if (payload.containsKey("scenarioAnswers")) {
            Object scenarioObj = payload.get("scenarioAnswers");
            if (scenarioObj instanceof List) {
                List<?> list = (List<?>) scenarioObj;
                if (session.getScenarioAnswers() == null) session.setScenarioAnswers(new HashMap<>());
                List<ScenarioQuestion> questions = session.getScenarioQuestions();
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) item;
                        String questionText = (String) map.get("question");
                        String answer = (String) map.get("answer");
                        if (answer != null) {
                            boolean matched = false;
                            if (questionText != null && questions != null) {
                                for (ScenarioQuestion q : questions) {
                                    if (questionText.trim().equalsIgnoreCase(q.getQuestion().trim())) {
                                        session.getScenarioAnswers().put(q.getId(), answer);
                                        matched = true;
                                        break;
                                    }
                                }
                            }
                            if (!matched && questions != null && i < questions.size()) {
                                session.getScenarioAnswers().put(questions.get(i).getId(), answer);
                            }
                        }
                    }
                }
            }
        }

        // 3. Project
        if (payload.containsKey("projectAnswers")) {
            Object projectObj = payload.get("projectAnswers");
            if (projectObj instanceof List) {
                List<?> list = (List<?>) projectObj;
                if (session.getProjectAnswers() == null) session.setProjectAnswers(new HashMap<>());
                List<ProjectQuestion> questions = session.getProjectQuestions();
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) item;
                        String questionText = (String) map.get("question");
                        String answer = (String) map.get("answer");
                        if (answer != null) {
                            boolean matched = false;
                            if (questionText != null && questions != null) {
                                for (ProjectQuestion q : questions) {
                                    if (questionText.trim().equalsIgnoreCase(q.getQuestion().trim())) {
                                        session.getProjectAnswers().put(q.getId(), answer);
                                        matched = true;
                                        break;
                                    }
                                }
                            }
                            if (!matched && questions != null && i < questions.size()) {
                                session.getProjectAnswers().put(questions.get(i).getId(), answer);
                            }
                        }
                    }
                }
            }
        }

        // 4. Violations
        if (payload.containsKey("violations")) {
            Object violationsObj = payload.get("violations");
            if (violationsObj instanceof Number) {
                int count = ((Number) violationsObj).intValue();
                if (session.getViolations() == null) session.setViolations(new ArrayList<>());
                for (int i = 0; i < count; i++) {
                    Violation violation = new Violation();
                    violation.setType("Tab switch");
                    violation.setSeverity("MEDIUM");
                    violation.setDescription("Candidate switched tab during test (E2E simulation)");
                    violation.setTimestamp(LocalDateTime.now());
                    session.getViolations().add(violation);
                }
            }
        }
        syncAnswersMap(session);
    }

    private void syncAnswersMap(TestSession session) {
        if (session.getAnswers() == null) {
            session.setAnswers(new HashMap<>());
        }
        if (session.getMcqAnswers() != null) {
            session.getMcqAnswers().forEach((qid, ans) -> {
                long time = session.getTimePerQuestion() != null ? session.getTimePerQuestion().getOrDefault(qid, 0L) : 0L;
                session.getAnswers().put(qid, new Answer(qid, ans, "ANSWERED", (int) time));
            });
        }
        if (session.getScenarioAnswers() != null) {
            session.getScenarioAnswers().forEach((qid, ans) -> {
                long time = session.getTimePerQuestion() != null ? session.getTimePerQuestion().getOrDefault(qid, 0L) : 0L;
                session.getAnswers().put(qid, new Answer(qid, ans, "ANSWERED", (int) time));
            });
        }
        if (session.getProjectAnswers() != null) {
            session.getProjectAnswers().forEach((qid, ans) -> {
                long time = session.getTimePerQuestion() != null ? session.getTimePerQuestion().getOrDefault(qid, 0L) : 0L;
                session.getAnswers().put(qid, new Answer(qid, ans, "ANSWERED", (int) time));
            });
        }
    }

    public List<Map<String, Object>> getMyTests(String userId) {
        List<TestSession> sessions = testSessionRepository.findByUserId(userId);
        List<Map<String, Object>> list = new ArrayList<>();

        for (TestSession s : sessions) {
            Map<String, Object> map = new HashMap<>();
            map.put("testId", s.getId());
            map.put("status", s.getStatus().toString());
            map.put("createdAt", s.getCreatedAt());

            String targetRole = s.getTargetRole();
            if (targetRole == null || targetRole.trim().isEmpty()) {
                targetRole = "Technical Screening";
            } else {
                targetRole = targetRole + " Assessment";
            }
            map.put("title", targetRole);

            if (s.getStatus() == TestStatus.EVALUATED) {
                Optional<Result> resOpt = resultRepository.findByTestId(s.getId());
                resOpt.ifPresent(result -> map.put("finalScore", result.getFinalScore()));
            }
            list.add(map);
        }
        return list;
    }

    public String transcribeAudio(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }
        try {
            return aiService.transcribeAudio(file.getBytes(), file.getContentType());
        } catch (Exception e) {
            log.error("Failed to read audio file bytes for transcription: {}", e.getMessage());
            return "";
        }
    }
}
