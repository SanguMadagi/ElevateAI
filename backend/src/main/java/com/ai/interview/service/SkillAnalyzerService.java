package com.ai.interview.service;

import com.ai.interview.model.*;
import com.ai.interview.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillAnalyzerService {

    private final SkillAnalysisRepository skillAnalysisRepository;
    private final ResultRepository resultRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final ProfileRepository profileRepository;
    private final TestSessionRepository testSessionRepository;

    public SkillAnalysis analyzeUserPerformance(String userId) {
        if (userId == null || userId.isBlank()) {
            return SkillAnalysis.builder().build();
        }

        Profile profile = profileRepository.findByUserId(userId).orElse(null);
        List<Result> allResults = resultRepository.findByUserId(userId);
        List<InterviewSession> allInterviews = interviewSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // ─── 1. FILTER: VALID COMPLETED ASSESSMENTS ONLY ─────────────────────
        // Strictly exclude EXITED, ABANDONED, or invalid assessments
        List<Result> validResults = new ArrayList<>();
        if (allResults != null) {
            for (Result res : allResults) {
                if (res == null || res.getFinalScore() < 0.0) {
                    continue;
                }
                if (res.getTestId() != null) {
                    Optional<TestSession> sessionOpt = testSessionRepository.findById(res.getTestId());
                    if (sessionOpt.isPresent()) {
                        TestSession session = sessionOpt.get();
                        if (session.getStatus() == TestStatus.EXITED || session.getStatus() == TestStatus.CREATED) {
                            continue;
                        }
                    }
                }
                validResults.add(res);
            }
        }

        // ─── 2. FILTER: VALID COMPLETED MOCK INTERVIEWS ONLY ─────────────────
        // Exclude IN_PROGRESS or un-evaluated interviews
        List<InterviewSession> validInterviews = new ArrayList<>();
        if (allInterviews != null) {
            for (InterviewSession inv : allInterviews) {
                if (inv == null || !"COMPLETED".equalsIgnoreCase(inv.getStatus())) {
                    continue;
                }
                Double score = resolveInterviewScore(inv);
                if (score != null && score > 0.0) {
                    validInterviews.add(inv);
                }
            }
        }

        // ─── 3. EMPTY STATE ──────────────────────────────────────────────────
        if (validResults.isEmpty() && validInterviews.isEmpty()) {
            SkillAnalysis emptyAnalysis = skillAnalysisRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                    .orElse(SkillAnalysis.builder().userId(userId).build());

            emptyAnalysis.setOverallReadinessScore(0.0);
            emptyAnalysis.setSkillScores(Collections.emptyMap());
            emptyAnalysis.setStrongConcepts(Collections.emptyList());
            emptyAnalysis.setWeakConcepts(Collections.emptyList());
            emptyAnalysis.setRepeatedMistakes(Collections.emptyList());
            emptyAnalysis.setGapDetections(Collections.emptyList());
            emptyAnalysis.setRecommendedNextAction("Complete your first technical assessment or mock interview to compute your readiness score.");
            emptyAnalysis.setUpdatedAt(LocalDateTime.now());
            
            // Clean up any duplicates before saving
            List<SkillAnalysis> dups = skillAnalysisRepository.findAllByUserId(userId);
            if (dups.size() > 1) {
                for (SkillAnalysis d : dups) {
                    if (d.getId() != null && !d.getId().equals(emptyAnalysis.getId())) {
                        skillAnalysisRepository.delete(d);
                    }
                }
            }
            return skillAnalysisRepository.save(emptyAnalysis);
        }

        // ─── 4. OVERALL READINESS SCORE (STRICT COMPLETED AVERAGE) ─────────────
        double totalScoreSum = 0.0;
        int completedCount = 0;

        for (Result res : validResults) {
            totalScoreSum += res.getFinalScore();
            completedCount++;
        }

        for (InterviewSession inv : validInterviews) {
            Double interviewScore = resolveInterviewScore(inv);
            if (interviewScore != null) {
                totalScoreSum += interviewScore;
                completedCount++;
            }
        }

        double overallReadiness = completedCount > 0
                ? Math.round((totalScoreSum / completedCount) * 10.0) / 10.0
                : 0.0;

        // ─── 5. SKILL BREAKDOWN COMPUTATION ──────────────────────────────────
        Map<String, List<Double>> skillDataPoints = new LinkedHashMap<>();
        Set<String> strongConceptsSet = new LinkedHashSet<>();
        Set<String> weakConceptsSet = new LinkedHashSet<>();
        List<SkillAnalysis.RepeatedMistake> repeatedMistakes = new ArrayList<>();
        List<String> gapDetections = new ArrayList<>();

        // Process Assessment Results into Skill Breakdown
        for (Result res : validResults) {
            processAssessmentSkillsAndFeedback(res, skillDataPoints, strongConceptsSet, weakConceptsSet, repeatedMistakes);
        }

        // Process Completed Interview Results into Skill Breakdown
        for (InterviewSession inv : validInterviews) {
            processInterviewSkillsAndFeedback(inv, skillDataPoints, strongConceptsSet, weakConceptsSet, repeatedMistakes);
        }

        // Aggregate scores per category
        Map<String, Double> finalSkillScores = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> entry : skillDataPoints.entrySet()) {
            String skill = entry.getKey();
            List<Double> scores = entry.getValue();
            if (scores == null || scores.isEmpty()) continue;

            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double rounded = Math.round(avg * 10.0) / 10.0;
            finalSkillScores.put(skill, rounded);

            // Populate category-level strengths (>= 70%) or needs focus (< 65%)
            if (rounded >= 70.0) {
                strongConceptsSet.add(skill + " (" + Math.round(rounded) + "%)");
            } else if (rounded < 65.0) {
                weakConceptsSet.add(skill + " — Needs Focus (" + Math.round(rounded) + "%)");
                repeatedMistakes.add(new SkillAnalysis.RepeatedMistake(skill, "Core Competency", 1, "Needs Improvement"));
            }
        }

        // ─── 6. RESUME GAP ANALYSIS ──────────────────────────────────────────
        if (profile != null && profile.getSkills() != null) {
            for (String resumeSkill : profile.getSkills()) {
                if (resumeSkill == null || resumeSkill.isBlank()) continue;
                String cleanResumeSkill = resumeSkill.trim();

                for (Map.Entry<String, Double> tested : finalSkillScores.entrySet()) {
                    if (tested.getKey().equalsIgnoreCase(cleanResumeSkill)
                            || tested.getKey().toLowerCase(Locale.ROOT).contains(cleanResumeSkill.toLowerCase(Locale.ROOT))
                            || cleanResumeSkill.toLowerCase(Locale.ROOT).contains(tested.getKey().toLowerCase(Locale.ROOT))) {
                        if (tested.getValue() < 65.0) {
                            gapDetections.add("Identified Gap: '" + cleanResumeSkill + "' is highlighted in your profile, but tested score is " + Math.round(tested.getValue()) + "%.");
                        }
                    }
                }
            }
        }

        // ─── 7. FINAL SANITIZATION & RECOMMENDATIONS ─────────────────────────
        List<String> strongConcepts = sanitizeAndLimit(strongConceptsSet, 6);

        // Ensure clean separation: candidate cannot have the same competency marked as both strong and weak
        Set<String> filteredWeakSet = new LinkedHashSet<>();
        for (String w : weakConceptsSet) {
            boolean isAlreadyStrong = strongConcepts.stream().anyMatch(s ->
                s.equalsIgnoreCase(w) ||
                (w.toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT)) && !w.contains("Needs Focus")) ||
                s.toLowerCase(Locale.ROOT).contains(w.toLowerCase(Locale.ROOT))
            );
            if (!isAlreadyStrong) {
                filteredWeakSet.add(w);
            }
        }
        List<String> weakConcepts = sanitizeAndLimit(filteredWeakSet, 6);

        String nextAction;
        if (overallReadiness >= 85.0) {
            nextAction = "Outstanding interview readiness! Schedule an advanced architectural mock interview or apply for senior roles.";
        } else if (!weakConcepts.isEmpty()) {
            nextAction = "Recommended focus: " + String.join(", ", weakConcepts.stream().limit(2).toList());
        } else {
            nextAction = "Take another AI Mock Interview or Assessment to continue sharpening your evaluated competencies.";
        }

        SkillAnalysis analysis = skillAnalysisRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .orElse(SkillAnalysis.builder().userId(userId).build());

        analysis.setOverallReadinessScore(overallReadiness);
        analysis.setSkillScores(finalSkillScores);
        analysis.setStrongConcepts(strongConcepts);
        analysis.setWeakConcepts(weakConcepts);
        analysis.setRepeatedMistakes(repeatedMistakes);
        analysis.setGapDetections(gapDetections);
        analysis.setRecommendedNextAction(nextAction);
        analysis.setUpdatedAt(LocalDateTime.now());

        // Ensure no duplicate records remain for this userId
        List<SkillAnalysis> dups = skillAnalysisRepository.findAllByUserId(userId);
        if (dups.size() > 1) {
            for (SkillAnalysis d : dups) {
                if (d.getId() != null && !d.getId().equals(analysis.getId())) {
                    skillAnalysisRepository.delete(d);
                }
            }
        }

        log.info("SkillAnalysis updated for user {}: readiness={}%, skills={}", userId, overallReadiness, finalSkillScores.keySet());
        return skillAnalysisRepository.save(analysis);
    }

    private Double resolveInterviewScore(InterviewSession interview) {
        if (interview.getOverallScore() != null && interview.getOverallScore() > 0) {
            return interview.getOverallScore();
        }
        if (interview.getFinalReport() != null) {
            Object overallObj = interview.getFinalReport().get("overallScore");
            if (overallObj instanceof Number n) {
                return n.doubleValue();
            }
            Object techObj = interview.getFinalReport().get("technicalScore");
            if (techObj instanceof Number n) {
                return n.doubleValue();
            }
        }
        if (interview.getHistory() != null && !interview.getHistory().isEmpty()) {
            return interview.getHistory().stream()
                    .mapToDouble(t -> t.getScore() != null ? t.getScore() : 70.0)
                    .average().orElse(70.0);
        }
        return null;
    }

    private void processAssessmentSkillsAndFeedback(Result res,
                                                   Map<String, List<Double>> skillDataPoints,
                                                   Set<String> strongConceptsSet,
                                                   Set<String> weakConceptsSet,
                                                   List<SkillAnalysis.RepeatedMistake> repeatedMistakes) {
        // Qualitative Strengths from Gemini Assessment Evaluation
        if (res.getStrengths() != null) {
            for (String str : res.getStrengths()) {
                if (isUsableFeedback(str)) {
                    strongConceptsSet.add(str.trim());
                }
            }
        }

        // Qualitative Weak Areas from Gemini Assessment Evaluation
        if (res.getWeakAreas() != null) {
            for (String weak : res.getWeakAreas()) {
                if (isUsableFeedback(weak)) {
                    weakConceptsSet.add(weak.trim());
                }
            }
        }

        // Process questions: map to categories & extract concrete mastered / missed concepts
        List<QuestionEvaluation> evals = res.getQuestionEvaluations();
        if (evals != null && !evals.isEmpty()) {
            Map<String, List<QuestionEvaluation>> byCategory = new LinkedHashMap<>();
            for (QuestionEvaluation qe : evals) {
                String cat = cleanCategory(qe.getCategory());
                byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(qe);

                // If candidate mastered question (score >= 85%), extract specific concept verified
                if (qe.getScore() >= 85.0) {
                    String verifiedConcept = extractSpecificConcept(qe);
                    if (verifiedConcept != null && !verifiedConcept.isBlank()) {
                        strongConceptsSet.add(verifiedConcept);
                    }
                }

                // If candidate missed question or scored poorly (score < 60%), extract specific concept tested
                if (qe.getScore() < 60.0) {
                    String specificConcept = extractSpecificConcept(qe);
                    if (specificConcept != null && !specificConcept.isBlank()) {
                        weakConceptsSet.add(specificConcept);
                        repeatedMistakes.add(new SkillAnalysis.RepeatedMistake(cat, specificConcept, 1, "Needs Improvement"));
                    }
                    if (qe.getMissingPoints() != null) {
                        for (String mp : qe.getMissingPoints()) {
                            if (isUsableFeedback(mp) && mp.length() > 5) {
                                weakConceptsSet.add(mp.trim());
                            }
                        }
                    }
                }
            }

            // Calculate category scores
            for (Map.Entry<String, List<QuestionEvaluation>> entry : byCategory.entrySet()) {
                String cat = entry.getKey();
                List<QuestionEvaluation> qList = entry.getValue();

                double catAvg = qList.stream().mapToDouble(QuestionEvaluation::getScore).average().orElse(0.0);
                skillDataPoints.computeIfAbsent(cat, k -> new ArrayList<>()).add(catAvg);
            }
        } else if (res.getCategoryScores() != null) {
            // Fallback to precomputed categoryScores if questionEvaluations is not present
            res.getCategoryScores().forEach((rawCat, score) -> {
                if (score != null) {
                    String cat = cleanCategory(rawCat);
                    skillDataPoints.computeIfAbsent(cat, k -> new ArrayList<>()).add(score);
                }
            });
        }
    }

    private void processInterviewSkillsAndFeedback(InterviewSession inv,
                                                   Map<String, List<Double>> skillDataPoints,
                                                   Set<String> strongConceptsSet,
                                                   Set<String> weakConceptsSet,
                                                   List<SkillAnalysis.RepeatedMistake> repeatedMistakes) {
        Double interviewScore = resolveInterviewScore(inv);
        if (interviewScore == null) return;

        String topic = cleanCategory(inv.getTopic() != null && !inv.getTopic().isBlank()
                ? inv.getTopic().trim()
                : "Technical Mock Interview");
        skillDataPoints.computeIfAbsent(topic, k -> new ArrayList<>()).add(interviewScore);

        if (inv.getFinalReport() != null) {
            extractListFeedback(inv.getFinalReport().get("strongAreas"), strongConceptsSet);
            extractListFeedback(inv.getFinalReport().get("strengths"), strongConceptsSet);

            extractListFeedback(inv.getFinalReport().get("weakAreas"), weakConceptsSet);
            extractListFeedback(inv.getFinalReport().get("weaknesses"), weakConceptsSet);
            extractListFeedback(inv.getFinalReport().get("conceptsToImprove"), weakConceptsSet);
        }
    }

    private void extractListFeedback(Object obj, Set<String> targetSet) {
        if (obj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s && isUsableFeedback(s)) {
                    targetSet.add(s.trim());
                }
            }
        }
    }

    private String extractSpecificConcept(QuestionEvaluation qe) {
        if (qe == null) return null;
        String text = ((qe.getQuestion() != null ? qe.getQuestion() : "") + " "
                + (qe.getExplanation() != null ? qe.getExplanation() : "") + " "
                + (qe.getFeedback() != null ? qe.getFeedback() : "")).toLowerCase(Locale.ROOT);

        if (text.contains("401") || text.contains("status code") || text.contains("unauthorized")) {
            return "HTTP Authentication & Status Codes (401 vs 403)";
        }
        if (text.contains("@transactional") || text.contains("transactional boundary") || text.contains("rollback")) {
            return "Spring @Transactional Boundary & Rollback";
        }
        if (text.contains("hashset") || text.contains("linkedhashset") || text.contains("uniqueness")) {
            return "Java Collections: Set Uniqueness & Hash Internals";
        }
        if (text.contains("binary search tree") || text.contains("bst") || text.contains("avl")) {
            return "Binary Search Tree Complexity & Operations";
        }
        if (text.contains("saga") || text.contains("compensat")) {
            return "Distributed Saga Orchestration & Compensation";
        }
        if (text.contains("optimistic locking") || text.contains("@version") || text.contains("version column")) {
            return "Optimistic Concurrency Control (@Version)";
        }
        if (text.contains("redis") && (text.contains("blacklist") || text.contains("revocation") || text.contains("jwt"))) {
            return "JWT Revocation & Redis Token Blacklist";
        }
        if (text.contains("cache-aside") || text.contains("cache aside") || text.contains("invalidation")) {
            return "Distributed Cache-Aside & Invalidation Strategy";
        }
        if (text.contains("circuit breaker") || text.contains("resilience4j") || text.contains("bulkhead")) {
            return "Fault Tolerance: Circuit Breakers & Resilience Patterns";
        }
        if (text.contains("stampede") || text.contains("thundering herd")) {
            return "Cache Stampede Prevention & Mutex Strategies";
        }
        if (text.contains("kafka") || text.contains("partition") || text.contains("consumer group")) {
            return "Kafka Consumer Groups & Partition Rebalancing";
        }
        if (text.contains("b-tree") || text.contains("indexing") || text.contains("query plan")) {
            return "Database Indexing Trade-offs & Query Optimization";
        }

        String cat = qe.getCategory();
        if (cat != null && !cat.isBlank() && !"General".equalsIgnoreCase(cat) && !"Software Engineering".equalsIgnoreCase(cat)) {
            return cat.trim();
        }
        return null;
    }

    private boolean isUsableFeedback(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("none") || lower.equals("n/a") || lower.equals("none.") || lower.contains("no answer") || lower.contains("complete solution unprovided")) {
            return false;
        }
        if (lower.contains("ai evaluation unavailable")) {
            return false;
        }
        if (lower.contains("completed multiple choice") || lower.contains("further practice recommended")) {
            return false;
        }
        return true;
    }

    private String cleanCategory(String raw) {
        if (raw == null || raw.isBlank()) return "Core Engineering Competency";
        String trimmed = raw.trim();
        if ("General".equalsIgnoreCase(trimmed)) return "Core Engineering Principles";
        return trimmed;
    }

    private List<String> sanitizeAndLimit(Set<String> set, int limit) {
        if (set == null || set.isEmpty()) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (String s : set) {
            if (s != null && !s.isBlank() && !list.contains(s.trim())) {
                list.add(s.trim());
                if (list.size() >= limit) break;
            }
        }
        return list;
    }

    public SkillAnalysis getAnalysis(String userId) {
        if (userId == null || userId.isBlank()) {
            return SkillAnalysis.builder().build();
        }
        return analyzeUserPerformance(userId);
    }
}
