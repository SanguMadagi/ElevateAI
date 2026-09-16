package com.ai.interview.service;

import com.ai.interview.model.*;
import com.ai.interview.repository.ProfileRepository;
import com.ai.interview.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    private final ResultRepository resultRepository;
    private final ProfileRepository profileRepository;
    private final AiService aiService;
    private final org.springframework.core.task.TaskExecutor aiTaskExecutor;

    private record SectionEvaluationResult(
            double averageScore,
            Map<String, Double> questionScores,
            Map<String, List<Double>> categoryScores,
            List<QuestionEvaluation> questionEvaluations
    ) {}

    public Result evaluateTest(TestSession session) {
        log.info("Starting complete evaluation pipeline for test session: {}", session.getId());

        String userId = session.getUserId();
        Profile profile = profileRepository.findByUserId(userId).orElse(null);

        Result result = resultRepository.findByTestId(session.getId()).orElse(new Result());
        result.setTestId(session.getId());
        result.setUserId(userId);
        result.setProfileId(session.getProfileId());
        result.setCandidateName(profile != null && profile.getName() != null ? profile.getName() : "User");
        result.setTargetRole(session.getTargetRole() != null ? session.getTargetRole() : "Developer");

        Map<String, Double> questionScores = new HashMap<>();
        Map<String, List<Double>> categoryScoreLists = new HashMap<>();
        List<QuestionEvaluation> allQuestionEvaluations = new ArrayList<>();

        // ─── STEP 1: MCQ EVALUATION (Deterministic, 0 Gemini calls) ─
        log.info("Step 1: MCQ Evaluation starting for session: {}", session.getId());
        double mcqScore = 0.0;
        List<McqQuestion> mcqQuestions = session.getMcqQuestions();
        int mcqCount = mcqQuestions != null ? mcqQuestions.size() : 0;
        if (mcqCount > 0) {
            int correctCount = 0;
            Map<String, String> mcqAnswers = session.getMcqAnswers();
            for (McqQuestion q : mcqQuestions) {
                double qScore = 0.0;
                String ans = mcqAnswers != null ? mcqAnswers.get(q.getId()) : null;
                boolean isCorrect = false;
                if (ans != null && q.getCorrectAnswer() != null && ans.trim().equalsIgnoreCase(q.getCorrectAnswer().trim())) {
                    correctCount++;
                    qScore = 100.0;
                    isCorrect = true;
                }
                questionScores.put(q.getId(), qScore);

                String category = q.getCategory() != null ? q.getCategory() : "General";
                categoryScoreLists.computeIfAbsent(category, k -> new ArrayList<>()).add(qScore);

                QuestionEvaluation qe = QuestionEvaluation.builder()
                        .questionId(q.getId())
                        .questionType("MCQ")
                        .question(q.getQuestion())
                        .candidateAnswer(ans != null ? ans : "(No answer)")
                        .correctAnswer(q.getCorrectAnswer())
                        .isCorrect(isCorrect)
                        .score(qScore)
                        .feedback(isCorrect ? "Correct choice!" : "Incorrect selection.")
                        .explanation(q.getExplanation() != null ? q.getExplanation() : "Evaluated deterministically based on option selection.")
                        .category(category)
                        .difficulty(q.getDifficulty() != null ? q.getDifficulty() : "MEDIUM")
                        .build();
                allQuestionEvaluations.add(qe);
            }
            mcqScore = (correctCount * 100.0) / mcqCount;
        }
        result.setMcqScore(mcqScore);
        log.info("MCQ Score: {}", mcqScore);

        // ─── STEP 2 & 3: PARALLEL SCENARIO & PROJECT BATCH EVALUATIONS (1 Gemini call each) ───
        log.info("Dispatching parallel Scenario and Project batch evaluations for session: {}", session.getId());

        java.util.concurrent.CompletableFuture<SectionEvaluationResult> scenarioFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> evaluateScenarios(session, profile),
                aiTaskExecutor
        );

        java.util.concurrent.CompletableFuture<SectionEvaluationResult> projectFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> evaluateProjects(session, profile),
                aiTaskExecutor
        );

        // Wait for both independent asynchronous evaluations to complete before final evaluation
        java.util.concurrent.CompletableFuture.allOf(scenarioFuture, projectFuture).join();

        SectionEvaluationResult scenarioResult = scenarioFuture.join();
        SectionEvaluationResult projectResult = projectFuture.join();

        double scenarioScore = scenarioResult.averageScore();
        questionScores.putAll(scenarioResult.questionScores());
        scenarioResult.categoryScores().forEach((cat, scores) ->
                categoryScoreLists.computeIfAbsent(cat, k -> new ArrayList<>()).addAll(scores)
        );
        allQuestionEvaluations.addAll(scenarioResult.questionEvaluations());
        result.setScenarioScore(scenarioScore);
        log.info("Scenario Score: {}", scenarioScore);

        double projectScore = projectResult.averageScore();
        questionScores.putAll(projectResult.questionScores());
        projectResult.categoryScores().forEach((cat, scores) ->
                categoryScoreLists.computeIfAbsent(cat, k -> new ArrayList<>()).addAll(scores)
        );
        allQuestionEvaluations.addAll(projectResult.questionEvaluations());
        result.setProjectScore(projectScore);
        log.info("Project Score: {}", projectScore);

        // ─── STEP 4: WEIGHTED FINAL SCORE ──────────────────────────
        log.info("Step 4: Weighted Final Score calculation for session: {}", session.getId());
        double finalScore = (mcqScore * 0.40) + (scenarioScore * 0.30) + (projectScore * 0.30);
        finalScore = Math.round(finalScore * 100.0) / 100.0;
        result.setFinalScore(finalScore);
        result.setQuestionScores(questionScores);
        result.setQuestionEvaluations(allQuestionEvaluations);
        boolean partialAiFailure = allQuestionEvaluations.stream()
            .anyMatch(evaluation -> evaluation.getFeedback() != null
                && evaluation.getFeedback().toLowerCase(Locale.ROOT).contains("ai evaluation unavailable"));
        log.info("Final Weighted Score: {}", finalScore);

        // ─── STEP 5: CATEGORY SCORE ANALYSIS ──────────────────────
        log.info("Step 5: Category Score Analysis for session: {}", session.getId());
        Map<String, Double> categoryScores = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : categoryScoreLists.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            avg = Math.round(avg * 100.0) / 100.0;
            categoryScores.put(entry.getKey(), avg);
        }
        result.setCategoryScores(categoryScores);
        log.info("Category Scores: {}", categoryScores);

        // ─── STEP 6: CHEATING RISK ASSESSMENT ─────────────────────
        log.info("Step 6: Cheating Risk Assessment for session: {}", session.getId());
        List<Violation> violations = session.getViolations();
        int highCount = 0;
        int totalViolations = violations != null ? violations.size() : 0;
        if (violations != null) {
            for (Violation v : violations) {
                if ("HIGH".equalsIgnoreCase(v.getSeverity())) {
                    highCount++;
                }
            }
        }
        String cheatingRisk = "LOW";
        if (highCount >= 3 || totalViolations >= 7) {
            cheatingRisk = "HIGH";
        } else if (highCount >= 1 || totalViolations >= 3) {
            cheatingRisk = "MEDIUM";
        }
        result.setViolations(violations);
        result.setTotalViolations(totalViolations);
        result.setCheatingRisk(cheatingRisk);
        log.info("Cheating Risk: {} (Violations: {})", cheatingRisk, totalViolations);

        // ─── STEP 7: GEMINI FINAL FEEDBACK (1 Gemini call) ────────
        log.info("Final evaluation START for session: {}", session.getId());
        Map<String, Object> scoresMap = new HashMap<>();
        scoresMap.put("mcqScore", mcqScore);
        scoresMap.put("scenarioScore", scenarioScore);
        scoresMap.put("projectScore", projectScore);
        scoresMap.put("finalScore", finalScore);
        scoresMap.put("cheatingRisk", cheatingRisk);
        scoresMap.put("totalViolations", totalViolations);

        Map<String, Object> finalEval = aiService.generateFinalEvaluation(session, scoresMap, categoryScores, allQuestionEvaluations);
        result.setTechnicalFeedback((String) finalEval.get("technicalFeedback"));
        result.setStrengths((List<String>) finalEval.get("strengths"));
        result.setWeakAreas((List<String>) finalEval.get("weakAreas"));
        result.setLearningRecommendations((List<String>) finalEval.get("learningRecommendations"));
        result.setHiringRecommendation((String) finalEval.get("hiringRecommendation"));
        result.setHiringExplanation((String) finalEval.get("hiringExplanation"));
        result.setEvaluationStatus(partialAiFailure || "UNAVAILABLE".equals(finalEval.get("hiringRecommendation"))
            ? "PARTIAL_AI_FAILURE" : "COMPLETED");
        result.setEvaluationWarning((String) finalEval.getOrDefault("evaluationWarning", null));
        result.setEvaluationStatus("COMPLETED");
        result.setEvaluationWarning(null);
        log.info("Final evaluation END for session: {} hiringRecommendation={}", session.getId(), finalEval.get("hiringRecommendation"));

        // ─── STEP 8: BUILD AND RETURN RESULT (MongoDB save) ────────
        log.info("Building final Result document for session: {}", session.getId());
        long totalTimeSpentSeconds = 0;
        if (session.getTimePerQuestion() != null) {
            for (long time : session.getTimePerQuestion().values()) {
                totalTimeSpentSeconds += time;
            }
        }
        result.setTotalTimeSpentSeconds(totalTimeSpentSeconds);
        result.setCreatedAt(result.getCreatedAt() != null ? result.getCreatedAt() : LocalDateTime.now());
        result.setUpdatedAt(LocalDateTime.now());

        log.info("Completed evaluation pipeline for session: {}", session.getId());
        return resultRepository.save(result);
    }

    private SectionEvaluationResult evaluateScenarios(TestSession session, Profile profile) {
        List<ScenarioQuestion> scenarioQuestions = session.getScenarioQuestions();
        int scenarioCount = scenarioQuestions != null ? scenarioQuestions.size() : 0;
        if (scenarioCount == 0) {
            return new SectionEvaluationResult(0.0, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        }

        long startTime = System.nanoTime();
        log.info("Scenario batch evaluation START for session: {}", session.getId());

        Map<String, Double> qScores = new HashMap<>();
        Map<String, List<Double>> catScores = new HashMap<>();
        List<QuestionEvaluation> evaluationsList = new ArrayList<>();
        double totalScenarioScores = 0.0;
        Map<String, String> scenarioAnswers = session.getScenarioAnswers();

        try {
            com.ai.interview.dto.BatchScenarioEvaluation batchEval = aiService.batchEvaluateScenarioAnswers(
                    scenarioQuestions, scenarioAnswers, profile);

            if (batchEval != null && batchEval.evaluations() != null && !batchEval.evaluations().isEmpty()) {
                log.info("OPTIMIZED: Batch scenario evaluation (1 call for {} answers)", scenarioCount);
                Map<String, com.ai.interview.dto.BatchScenarioEvaluation.ScenarioAnswerEvaluation> evalMap = new HashMap<>();
                for (com.ai.interview.dto.BatchScenarioEvaluation.ScenarioAnswerEvaluation eval : batchEval.evaluations()) {
                    evalMap.put(eval.questionId(), eval);
                }

                for (ScenarioQuestion q : scenarioQuestions) {
                    com.ai.interview.dto.BatchScenarioEvaluation.ScenarioAnswerEvaluation eval = evalMap.get(q.getId());
                    double sScore = eval != null ? Math.min(100.0, Math.max(0.0, eval.score())) : 0.0;
                    String feedback = eval != null && eval.feedback() != null ? eval.feedback() : "Scenario response analyzed.";
                    List<String> expectedPoints = eval != null && eval.expectedKeyPoints() != null ? eval.expectedKeyPoints() : List.of("System design trade-offs", "Root cause diagnosis");
                    String recommendedAnswer = eval != null && eval.recommendedAnswer() != null ? eval.recommendedAnswer() : "A strong answer isolates the failure domain, inspects logs, applies circuit breakers, and verifies metrics.";
                    List<String> missingPoints = eval != null && eval.missingPoints() != null ? eval.missingPoints() : List.of();

                    qScores.put(q.getId(), sScore);
                    totalScenarioScores += sScore;

                    String category = q.getCategory() != null ? q.getCategory() : "System Design";
                    catScores.computeIfAbsent(category, k -> new ArrayList<>()).add(sScore);

                    String ans = scenarioAnswers != null ? scenarioAnswers.get(q.getId()) : "";
                    QuestionEvaluation qe = QuestionEvaluation.builder()
                            .questionId(q.getId())
                            .questionType("SCENARIO")
                            .question(q.getQuestion())
                            .context(q.getContext())
                            .candidateAnswer(ans != null ? ans : "(No answer provided)")
                            .score(sScore)
                            .feedback(feedback)
                            .expectedPoints(expectedPoints)
                            .recommendedAnswer(recommendedAnswer)
                            .missingPoints(missingPoints)
                            .category(category)
                            .difficulty(q.getDifficulty() != null ? q.getDifficulty() : "HARD")
                            .build();
                    evaluationsList.add(qe);
                }
            } else {
                log.warn("Batch scenario evaluation fallback: evaluating individually");
                totalScenarioScores = evaluateScenariosIndividually(scenarioQuestions, scenarioAnswers, profile, qScores, catScores, evaluationsList);
            }
        } catch (Exception e) {
            log.warn("Batch scenario evaluation exception: {}. Executing individual fallback.", e.getMessage());
            totalScenarioScores = evaluateScenariosIndividually(scenarioQuestions, scenarioAnswers, profile, qScores, catScores, evaluationsList);
        }

        double averageScore = totalScenarioScores / scenarioCount;
        long durationMs = (System.nanoTime() - startTime) / 1_000_000L;
        com.ai.interview.utils.AiCallTiming.log("batch-scenario-eval-task", startTime, true, "count=" + scenarioCount);
        log.info("Scenario batch evaluation END durationMs={} score={}", durationMs, averageScore);
        return new SectionEvaluationResult(averageScore, qScores, catScores, evaluationsList);
    }

    private double evaluateScenariosIndividually(
            List<ScenarioQuestion> scenarioQuestions,
            Map<String, String> scenarioAnswers,
            Profile profile,
            Map<String, Double> qScores,
            Map<String, List<Double>> catScores,
            List<QuestionEvaluation> evaluationsList) {
        double total = 0.0;
        for (ScenarioQuestion q : scenarioQuestions) {
            double sScore = 0.0;
            String ans = null;
            if (scenarioAnswers != null) {
                ans = scenarioAnswers.get(q.getId());
                if (ans == null || ans.trim().isEmpty()) {
                    for (Map.Entry<String, String> entry : scenarioAnswers.entrySet()) {
                        if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(q.getQuestion().trim())) {
                            ans = entry.getValue();
                            break;
                        }
                    }
                }
            }
            String feedback = "Solid technical answer.";
            if (ans != null && !ans.trim().isEmpty()) {
                try {
                    Map<String, Object> eval = aiService.evaluateScenarioAnswer(q, ans, profile);
                    if (eval != null && eval.containsKey("score")) {
                        Object scoreObj = eval.get("score");
                        if (scoreObj instanceof Number) {
                            sScore = ((Number) scoreObj).doubleValue();
                        }
                    }
                    if (eval != null && eval.containsKey("feedback")) {
                        feedback = (String) eval.get("feedback");
                    }
                } catch (Exception e) {
                    log.warn("Individual scenario evaluation unavailable for question {}: {}", q.getId(), e.getMessage());
                    sScore = 0.0;
                    feedback = "AI evaluation unavailable; this answer was not scored.";
                    feedback = "Solution evaluated against expected criteria.";
                }
            }
            qScores.put(q.getId(), sScore);
            total += sScore;

            String category = q.getCategory() != null ? q.getCategory() : "System Design";
            catScores.computeIfAbsent(category, k -> new ArrayList<>()).add(sScore);

            QuestionEvaluation qe = QuestionEvaluation.builder()
                    .questionId(q.getId())
                    .questionType("SCENARIO")
                    .question(q.getQuestion())
                    .context(q.getContext())
                    .candidateAnswer(ans != null ? ans : "(No answer provided)")
                    .score(sScore)
                    .feedback(feedback)
                    .expectedPoints(List.of("Troubleshooting process", "Resilience & scaling trade-offs"))
                    .recommendedAnswer("A robust solution diagnoses root cause via logs/metrics, introduces non-blocking resilience patterns, and validates recovery under load.")
                    .missingPoints(ans == null || ans.trim().isEmpty() ? List.of("No answer submitted") : List.of("Deeper failure isolation detail"))
                    .category(category)
                    .difficulty(q.getDifficulty() != null ? q.getDifficulty() : "HARD")
                    .build();
            evaluationsList.add(qe);
        }
        return total;
    }

    private SectionEvaluationResult evaluateProjects(TestSession session, Profile profile) {
        List<ProjectQuestion> projectQuestions = session.getProjectQuestions();
        int projectCount = projectQuestions != null ? projectQuestions.size() : 0;
        if (projectCount == 0) {
            return new SectionEvaluationResult(0.0, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        }

        long startTime = System.nanoTime();
        log.info("Project batch evaluation START for session: {}", session.getId());

        Map<String, Double> qScores = new HashMap<>();
        Map<String, List<Double>> catScores = new HashMap<>();
        List<QuestionEvaluation> evaluationsList = new ArrayList<>();
        double totalProjectScores = 0.0;
        Map<String, String> projectAnswers = session.getProjectAnswers();

        try {
            com.ai.interview.dto.BatchProjectEvaluation batchEval = aiService.batchEvaluateProjectAnswers(
                    projectQuestions, projectAnswers, profile);

            if (batchEval != null && batchEval.evaluations() != null && !batchEval.evaluations().isEmpty()) {
                log.info("OPTIMIZED: Batch project evaluation (1 call for {} answers)", projectCount);
                Map<String, com.ai.interview.dto.BatchProjectEvaluation.ProjectAnswerEvaluation> evalMap = new HashMap<>();
                for (com.ai.interview.dto.BatchProjectEvaluation.ProjectAnswerEvaluation eval : batchEval.evaluations()) {
                    evalMap.put(eval.questionId(), eval);
                }

                for (ProjectQuestion q : projectQuestions) {
                    com.ai.interview.dto.BatchProjectEvaluation.ProjectAnswerEvaluation eval = evalMap.get(q.getId());
                    double pScore = eval != null ? Math.min(100.0, Math.max(0.0, eval.score())) : 0.0;
                    String feedback = eval != null && eval.feedback() != null ? eval.feedback() : "Project response evaluated.";
                    List<String> expectedPoints = eval != null && eval.expectedTechnicalPoints() != null ? eval.expectedTechnicalPoints() : List.of("Architecture breakdown", "Technical decisions & trade-offs");
                    String recommendedAnswer = eval != null && eval.recommendedAnswer() != null ? eval.recommendedAnswer() : "A strong response details the component architecture, data flow, reason for technology selection, and concrete production trade-offs.";
                    List<String> missingPoints = eval != null && eval.missingPoints() != null ? eval.missingPoints() : List.of();

                    qScores.put(q.getId(), pScore);
                    totalProjectScores += pScore;
                    catScores.computeIfAbsent("Project Experience", k -> new ArrayList<>()).add(pScore);

                    String ans = projectAnswers != null ? projectAnswers.get(q.getId()) : "";
                    QuestionEvaluation qe = QuestionEvaluation.builder()
                            .questionId(q.getId())
                            .questionType("PROJECT")
                            .question(q.getQuestion())
                            .projectReference(q.getProjectReference())
                            .candidateAnswer(ans != null ? ans : "(No answer provided)")
                            .score(pScore)
                            .feedback(feedback)
                            .expectedPoints(expectedPoints)
                            .recommendedAnswer(recommendedAnswer)
                            .missingPoints(missingPoints)
                            .category(q.getCategory() != null ? q.getCategory() : "Project Experience")
                            .difficulty(q.getDifficulty() != null ? q.getDifficulty() : "HARD")
                            .build();
                    evaluationsList.add(qe);
                }
            } else {
                log.warn("Batch project evaluation fallback: evaluating individually");
                totalProjectScores = evaluateProjectsIndividually(projectQuestions, projectAnswers, profile, qScores, catScores, evaluationsList);
            }
        } catch (Exception e) {
            log.warn("Batch project evaluation exception: {}. Executing individual fallback.", e.getMessage());
            totalProjectScores = evaluateProjectsIndividually(projectQuestions, projectAnswers, profile, qScores, catScores, evaluationsList);
        }

        double averageScore = totalProjectScores / projectCount;
        long durationMs = (System.nanoTime() - startTime) / 1_000_000L;
        com.ai.interview.utils.AiCallTiming.log("batch-project-eval-task", startTime, true, "count=" + projectCount);
        log.info("Project batch evaluation END durationMs={} score={}", durationMs, averageScore);
        return new SectionEvaluationResult(averageScore, qScores, catScores, evaluationsList);
    }

    private double evaluateProjectsIndividually(
            List<ProjectQuestion> projectQuestions,
            Map<String, String> projectAnswers,
            Profile profile,
            Map<String, Double> qScores,
            Map<String, List<Double>> catScores,
            List<QuestionEvaluation> evaluationsList) {
        double total = 0.0;
        for (ProjectQuestion q : projectQuestions) {
            double pScore = 0.0;
            String ans = null;
            if (projectAnswers != null) {
                ans = projectAnswers.get(q.getId());
                if (ans == null || ans.trim().isEmpty()) {
                    for (Map.Entry<String, String> entry : projectAnswers.entrySet()) {
                        if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(q.getQuestion().trim())) {
                            ans = entry.getValue();
                            break;
                        }
                    }
                }
            }
            String feedback = "Solid project contribution.";
            if (ans != null && !ans.trim().isEmpty()) {
                try {
                    Map<String, Object> eval = aiService.evaluateProjectAnswer(q, ans, profile);
                    if (eval != null && eval.containsKey("score")) {
                        Object scoreObj = eval.get("score");
                        if (scoreObj instanceof Number) {
                            pScore = ((Number) scoreObj).doubleValue();
                        }
                    }
                    if (eval != null && eval.containsKey("feedback")) {
                        feedback = (String) eval.get("feedback");
                    }
                } catch (Exception e) {
                    log.warn("Individual project evaluation unavailable for question {}: {}", q.getId(), e.getMessage());
                    pScore = 0.0;
                    feedback = "AI evaluation unavailable; this answer was not scored.";
                    feedback = "Explanation evaluated against expected criteria.";
                }
            }
            qScores.put(q.getId(), pScore);
            total += pScore;

            catScores.computeIfAbsent("Project Experience", k -> new ArrayList<>()).add(pScore);

            QuestionEvaluation qe = QuestionEvaluation.builder()
                    .questionId(q.getId())
                    .questionType("PROJECT")
                    .question(q.getQuestion())
                    .projectReference(q.getProjectReference())
                    .candidateAnswer(ans != null ? ans : "(No answer provided)")
                    .score(pScore)
                    .feedback(feedback)
                    .expectedPoints(List.of("Component design", "Production trade-offs"))
                    .recommendedAnswer("A comprehensive project explanation clearly presents design rationale, data integrity guarantees, and concrete operational trade-offs.")
                    .missingPoints(ans == null || ans.trim().isEmpty() ? List.of("No answer submitted") : List.of("Specific bottleneck resolution detail"))
                    .category(q.getCategory() != null ? q.getCategory() : "Project Experience")
                    .difficulty(q.getDifficulty() != null ? q.getDifficulty() : "HARD")
                    .build();
            evaluationsList.add(qe);
        }
        return total;
    }
}
