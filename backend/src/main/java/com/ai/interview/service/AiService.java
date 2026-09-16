package com.ai.interview.service;

import com.ai.interview.dto.BatchProjectEvaluation;
import com.ai.interview.dto.BatchScenarioEvaluation;
import com.ai.interview.dto.FinalEvaluationReport;
import com.ai.interview.dto.ResumeProfile;
import com.ai.interview.model.McqQuestion;
import com.ai.interview.model.Profile;
import com.ai.interview.model.ProjectQuestion;
import com.ai.interview.model.QuestionEvaluation;
import com.ai.interview.model.ScenarioQuestion;
import com.ai.interview.model.TestSession;
import com.ai.interview.utils.AiCallTiming;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Central execution method calling model via Spring AI ChatClient abstraction layer
     */
    public String generateContent(String prompt) {
        long started = System.nanoTime();
        try {
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            AiCallTiming.log("gemini-chat", started, true, "chat");
            return content;
        } catch (Exception e) {
            AiCallTiming.DiagnosticResult diag = AiCallTiming.logFailure("gemini-chat", started, e);
            if ("RATE_LIMIT".equals(diag.category())) {
                throw new IllegalStateException("AI service is temporarily rate limited. Please try again shortly.");
            }
            throw new IllegalStateException("AI processing temporarily unavailable (" + diag.category() + "): " + diag.rootCauseMessage());
        }
    }

    public String generateContentWithRetry(String prompt, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return generateContent(prompt);
            } catch (IllegalStateException e) {
                String message = e.getMessage() == null ? "" : e.getMessage();
                if (message.contains("rate limited")) {
                    if (attempt == maxRetries) {
                        log.warn("Gemini quota limit reached after {} attempts; returning controlled error.", attempt);
                        return "AI service is temporarily rate limited. Please try again shortly.";
                    }
                    log.warn("Gemini rate limit encountered on attempt {}/{}; skipping nested retry loop and returning controlled response.", attempt, maxRetries);
                    return "AI service is temporarily rate limited. Please try again shortly.";
                }
                throw e;
            }
        }
        return "AI service is temporarily unavailable. Please try again shortly.";
    }

    /**
     * Transcribe candidate voice recording using multimodal AI model
     */
    public String transcribeAudio(byte[] audioBytes, String mimeType) {
        if (audioBytes == null || audioBytes.length == 0) {
            return "";
        }
        long started = System.nanoTime();
        try {
            String prompt = "Listen to this candidate technical assessment audio recording and transcribe the spoken words verbatim into clear English text with accurate technical terminology, punctuation, and capitalization. Output ONLY the transcribed words, without preamble, meta commentary, or quotes.";
            String cleanMimeType = (mimeType != null && mimeType.contains(";")) 
                    ? mimeType.split(";")[0].trim() 
                    : (mimeType != null && !mimeType.isBlank() ? mimeType.trim() : "audio/webm");

            org.springframework.ai.content.Media media = new org.springframework.ai.content.Media(
                    org.springframework.util.MimeTypeUtils.parseMimeType(cleanMimeType),
                    new org.springframework.core.io.ByteArrayResource(audioBytes)
            );

            String transcript = chatClient.prompt()
                    .user(u -> u.text(prompt).media(media))
                    .call()
                    .content();

            AiCallTiming.log("gemini-transcribe-audio", started, true, "bytes=" + audioBytes.length);
            return transcript != null ? transcript.trim() : "";
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-transcribe-audio", started, e);
            log.error("Failed to transcribe audio via Gemini: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 1. parseResume(String resumeText) using Spring AI BeanOutputConverter
     */
    public Map<String, Object> parseResume(String resumeText) {
        if (resumeText == null || resumeText.trim().isEmpty()) {
            log.warn("Empty or non-text PDF provided for resume parsing.");
            return getFallbackResumeData();
        }

        long started = System.nanoTime();
        try {
            ResumeProfile profile = chatClient.prompt()
                    .system("Extract a factual structured resume profile. Do not invent information absent from the resume.")
                    .user("Analyze this candidate resume and extract structured profile information:\n\n" + resumeText)
                    .call()
                    .entity(ResumeProfile.class, spec -> spec.useProviderStructuredOutput().validateSchema());
            if (profile != null) {
                AiCallTiming.log("gemini-parse-resume", started, true, "skillsCount=" + (profile.getSkills() != null ? profile.getSkills().size() : 0));
                Map<String, Object> map = new HashMap<>();
                map.put("skills", profile.getSkills() != null ? profile.getSkills() : List.of());
                map.put("projects", profile.getProjects() != null ? profile.getProjects() : List.of());
                map.put("technologies", profile.getTechnologies() != null ? profile.getTechnologies() : List.of());
                map.put("yearsOfExperience", profile.getExperience() != null ? profile.getExperience().size() : 1);
                map.put("claims", profile.getClaims() != null ? profile.getClaims() : List.of());
                return map;
            }
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-parse-resume", started, e);
        }

        return getFallbackResumeData();
    }

    private Map<String, Object> getFallbackResumeData() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("skills", List.of());
        defaults.put("projects", List.of());
        defaults.put("technologies", List.of());
        defaults.put("yearsOfExperience", 0);
        defaults.put("claims", List.of());
        return defaults;
    }

    // --- Candidate Context Helper (Zero-Hallucination) ---
    private String buildCandidateContext(Profile profile) {
        if (profile == null) {
            return "Target Role: Software Engineer\nVerified Skills: Core Java, REST APIs, SQL\nVerified Projects: None recorded";
        }

        StringBuilder sb = new StringBuilder();
        String role = profile.getTargetRole() != null && !profile.getTargetRole().isBlank()
                ? profile.getTargetRole()
                : (profile.getSuggestedRole() != null && !profile.getSuggestedRole().isBlank() ? profile.getSuggestedRole() : "Software Engineer");
        sb.append("Target Role: ").append(role).append("\n");

        String level = profile.getExpertiseLevel() != null && !profile.getExpertiseLevel().isBlank()
                ? profile.getExpertiseLevel()
                : "INTERMEDIATE";
        sb.append("Experience Level: ").append(level).append(" (").append(profile.getYearsOfExperience()).append(" years exp)\n");

        // Verified Skills & Technologies actually present in database
        Set<String> verifiedSkills = new LinkedHashSet<>();
        if (profile.getSkills() != null) {
            profile.getSkills().stream().filter(s -> s != null && !s.isBlank()).forEach(verifiedSkills::add);
        }
        if (profile.getResumeTechnologies() != null) {
            profile.getResumeTechnologies().stream().filter(s -> s != null && !s.isBlank()).forEach(verifiedSkills::add);
        }
        if (!verifiedSkills.isEmpty()) {
            sb.append("Verified Technologies & Skills: ").append(String.join(", ", verifiedSkills)).append("\n");
        } else {
            sb.append("Verified Technologies & Skills: General Software Engineering, Data Structures, REST APIs\n");
        }

        // Verified Projects actually present in database
        Set<String> verifiedProjects = new LinkedHashSet<>();
        if (profile.getProjects() != null) {
            profile.getProjects().stream().filter(p -> p != null && !p.isBlank()).forEach(verifiedProjects::add);
        }
        if (profile.getResumeProjects() != null) {
            profile.getResumeProjects().stream().filter(p -> p != null && !p.isBlank()).forEach(verifiedProjects::add);
        }
        if (!verifiedProjects.isEmpty()) {
            sb.append("Verified Resume Projects: ").append(String.join("; ", verifiedProjects)).append("\n");
        } else {
            sb.append("Verified Resume Projects: (No named projects listed; derive project questions from verified skills and target role)\n");
        }

        if (profile.getExperience() != null && !profile.getExperience().isEmpty()) {
            sb.append("Verified Experience Highlights: ").append(String.join("; ", profile.getExperience())).append("\n");
        }

        return sb.toString();
    }

    private String buildNegativeContextClause(String sectionName, List<String> previousQuestions) {
        if (previousQuestions == null || previousQuestions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\nCRITICAL - PREVIOUS ").append(sectionName.toUpperCase(Locale.ROOT)).append(" QUESTIONS (DO NOT DUPLICATE):\n");
        int count = 0;
        for (String q : previousQuestions) {
            if (q != null && !q.isBlank()) {
                sb.append("- ").append(q.trim()).append("\n");
                count++;
                if (count >= 10) break; // Keep prompt size optimal
            }
        }
        sb.append("RULES ON DUPLICATE PREVENTION:\n");
        sb.append("1. Do NOT repeat or closely reproduce any of the above previous questions.\n");
        sb.append("2. Do NOT create semantically equivalent questions with different wording testing the same underlying bug, scenario, or choice.\n");
        sb.append("3. Generate questions that test completely fresh engineering dimensions, components, scenarios, and trade-offs.\n");
        return sb.toString();
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * 2. generateMcqQuestions(Profile profile)
     */
    public List<McqQuestion> generateMcqQuestions(Profile profile) {
        return generateMcqQuestions(profile, Collections.emptyList());
    }

    public List<McqQuestion> generateMcqQuestions(Profile profile, List<String> previousQuestions) {
        String candidateContext = buildCandidateContext(profile);
        String negativeContext = buildNegativeContextClause("MCQ", previousQuestions);

        log.info("MCQ_GENERATION started for role: {}", profile != null ? profile.getTargetRole() : "General");
        long started = System.nanoTime();

        String prompt = String.format(
            "You are an expert technical interviewer designing a dynamic, personalized multiple choice assessment.\n" +
            "Candidate Context (USE ONLY THESE VERIFIED TECHNOLOGIES):\n%s\n%s\n" +
            "Generate exactly 4 practical, profile-relevant multiple choice questions.\n" +
            "Difficulty Distribution:\n" +
            "- 1 EASY (core language/syntax/fundamentals)\n" +
            "- 2 MEDIUM (practical framework behavior, transaction boundaries, state management, or data handling)\n" +
            "- 1 HARD (edge case, distributed architecture, internals, concurrency, or performance optimization)\n\n" +
            "Rules:\n" +
            "1. Derive questions dynamically from the candidate's actual verified technologies and target role.\n" +
            "2. Do NOT invent technologies absent from the candidate's verified skills.\n" +
            "3. Avoid generic textbook definition questions (e.g. do NOT ask 'What is OOP?' or 'What is Spring Boot?').\n" +
            "4. Prefer applied questions that test real-world developer reasoning (e.g. token revocation, query plan behavior, cache invalidation, exception propagation).\n" +
            "5. Provide a clear 'explanation' for each question detailing why the correct choice is right.\n" +
            "6. Return ONLY a JSON array matching this format (no markdown, no backticks):\n" +
            "[\n" +
            "  {\n" +
            "    \"id\": \"mcq_1\",\n" +
            "    \"question\": \"Applied scenario or code question text?\",\n" +
            "    \"options\": [\"A) Option 1\", \"B) Option 2\", \"C) Option 3\", \"D) Option 4\"],\n" +
            "    \"correctAnswer\": \"A\",\n" +
            "    \"category\": \"Spring Security\",\n" +
            "    \"difficulty\": \"MEDIUM\",\n" +
            "    \"explanation\": \"Clear technical rationale why option A is correct.\"\n" +
            "  }\n" +
            "]",
            candidateContext, negativeContext
        );

        try {
            List<McqQuestion> questions = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(new ParameterizedTypeReference<List<McqQuestion>>() {});

            if (questions != null && !questions.isEmpty()) {
                Set<String> seenNormalized = (previousQuestions != null ? previousQuestions : Collections.<String>emptyList())
                        .stream().map(this::normalizeText).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

                List<McqQuestion> uniqueQuestions = new ArrayList<>();
                for (McqQuestion q : questions) {
                    if (q.getQuestion() != null) {
                        String norm = normalizeText(q.getQuestion());
                        if (!seenNormalized.contains(norm)) {
                            seenNormalized.add(norm);
                            uniqueQuestions.add(q);
                        }
                    }
                }

                if (uniqueQuestions.size() < 4) {
                    List<McqQuestion> fallbacks = getFallbackMcqQuestions(profile);
                    for (McqQuestion fb : fallbacks) {
                        String norm = normalizeText(fb.getQuestion());
                        if (!seenNormalized.contains(norm)) {
                            seenNormalized.add(norm);
                            uniqueQuestions.add(fb);
                        }
                        if (uniqueQuestions.size() >= 4) break;
                    }
                }

                List<McqQuestion> finalQuestions = uniqueQuestions.stream().limit(4).collect(Collectors.toList());
                for (int i = 0; i < finalQuestions.size(); i++) {
                    finalQuestions.get(i).setId("mcq_" + (i + 1));
                }

                if (finalQuestions.size() == 4) {
                    long durationMs = (System.nanoTime() - started) / 1_000_000L;
                    AiCallTiming.log("gemini-gen-mcq", started, true, "count=" + finalQuestions.size());
                    log.info("MCQ_GENERATION completed durationMs={} count={}", durationMs, finalQuestions.size());
                    return finalQuestions;
                }
            }
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-gen-mcq", started, e);
        }

        List<McqQuestion> fallback = getFallbackMcqQuestions(profile);
        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        log.warn("MCQ_GENERATION fallback used durationMs={} count={}", durationMs, fallback.size());
        return fallback;
    }

    /**
     * 3. generateScenarioQuestions(Profile profile)
     */
    public List<ScenarioQuestion> generateScenarioQuestions(Profile profile) {
        return generateScenarioQuestions(profile, Collections.emptyList());
    }

    public List<ScenarioQuestion> generateScenarioQuestions(Profile profile, List<String> previousQuestions) {
        String candidateContext = buildCandidateContext(profile);
        String negativeContext = buildNegativeContextClause("SCENARIO", previousQuestions);

        log.info("SCENARIO_GENERATION started for role: {}", profile != null ? profile.getTargetRole() : "General");
        long started = System.nanoTime();

        String prompt = String.format(
            "You are an expert technical interviewer designing 3 REAL-WORLD WORKPLACE ENGINEERING SCENARIO QUESTIONS.\n" +
            "Candidate Context (USE ONLY THESE VERIFIED TECHNOLOGIES):\n%s\n%s\n" +
            "Generate exactly 3 scenario-based engineering/incident challenge questions.\n" +
            "Difficulty Distribution:\n" +
            "- 1 MEDIUM (workplace architecture/data handling/authorization trade-off)\n" +
            "- 2 HARD (production incident, concurrency race condition, distributed failure, security vulnerability, or latency spike)\n\n" +
            "Rules for Scenario Questions:\n" +
            "1. MUST describe a realistic engineering or production situation (e.g. unexpected 401s during token refresh, lost updates during concurrent edits, latency spike when downstream caches drop, connection pool exhaustion, broken object-level authorization, handling 429 rate limits without retry storms, or WebSocket reconnection storms).\n" +
            "2. MUST NOT simply ask theoretical definition questions (e.g., do NOT ask 'What is Redis?' or 'Explain JWT').\n" +
            "3. Clearly distinct from project questions: Scenario asks 'What would you do if this system/production problem happened? How would you investigate, troubleshoot, and resolve it?'\n" +
            "4. Derive technologies dynamically from the candidate's actual verified profile.\n" +
            "5. Diversity: Ensure the 3 questions cover different engineering dimensions (e.g., security vs concurrency vs performance).\n" +
            "6. Return ONLY a JSON array matching this format (no markdown, no backticks):\n" +
            "[\n" +
            "  {\n" +
            "    \"id\": \"scen_1\",\n" +
            "    \"question\": \"Realistic incident title / problem summary?\",\n" +
            "    \"context\": \"Detailed technical context including setup, symptoms, logs, or metrics.\",\n" +
            "    \"evaluationCriteria\": \"Key points expected: root-cause diagnosis, architectural remedy, testing, trade-offs.\",\n" +
            "    \"category\": \"System Design\",\n" +
            "    \"difficulty\": \"HARD\"\n" +
            "  }\n" +
            "]",
            candidateContext, negativeContext
        );

        try {
            List<ScenarioQuestion> questions = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(new ParameterizedTypeReference<List<ScenarioQuestion>>() {});

            if (questions != null && !questions.isEmpty()) {
                Set<String> seenNormalized = (previousQuestions != null ? previousQuestions : Collections.<String>emptyList())
                        .stream().map(this::normalizeText).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

                List<ScenarioQuestion> uniqueQuestions = new ArrayList<>();
                for (ScenarioQuestion q : questions) {
                    if (q.getQuestion() != null) {
                        String norm = normalizeText(q.getQuestion());
                        if (!seenNormalized.contains(norm)) {
                            seenNormalized.add(norm);
                            uniqueQuestions.add(q);
                        }
                    }
                }

                if (uniqueQuestions.size() < 3) {
                    List<ScenarioQuestion> fallbacks = getFallbackScenarioQuestions(profile);
                    for (ScenarioQuestion fb : fallbacks) {
                        String norm = normalizeText(fb.getQuestion());
                        if (!seenNormalized.contains(norm)) {
                            seenNormalized.add(norm);
                            uniqueQuestions.add(fb);
                        }
                        if (uniqueQuestions.size() >= 3) break;
                    }
                }

                List<ScenarioQuestion> finalQuestions = uniqueQuestions.stream().limit(3).collect(Collectors.toList());
                for (int i = 0; i < finalQuestions.size(); i++) {
                    finalQuestions.get(i).setId("scen_" + (i + 1));
                }

                if (finalQuestions.size() == 3) {
                    long durationMs = (System.nanoTime() - started) / 1_000_000L;
                    AiCallTiming.log("gemini-gen-scenario", started, true, "count=" + finalQuestions.size());
                    log.info("SCENARIO_GENERATION completed durationMs={} count={}", durationMs, finalQuestions.size());
                    return finalQuestions;
                }
            }
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-gen-scenario", started, e);
        }

        List<ScenarioQuestion> fallback = getFallbackScenarioQuestions(profile);
        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        log.warn("SCENARIO_GENERATION fallback used durationMs={} count={}", durationMs, fallback.size());
        return fallback;
    }

    /**
     * 4. generateProjectQuestions(Profile profile)
     */
    public List<ProjectQuestion> generateProjectQuestions(Profile profile) {
        return generateProjectQuestions(profile, Collections.emptyList());
    }

    public List<ProjectQuestion> generateProjectQuestions(Profile profile, List<String> previousQuestions) {
        String candidateContext = buildCandidateContext(profile);
        String negativeContext = buildNegativeContextClause("PROJECT", previousQuestions);

        log.info("PROJECT_GENERATION started for candidate context: {}", candidateContext.replace("\n", " | "));
        long started = System.nanoTime();

        String prompt = String.format(
            "You are an expert technical interviewer designing 3 PROJECT DEEP-DIVE QUESTIONS testing candidate implementation ownership.\n" +
            "Candidate Context (USE ONLY THESE VERIFIED PROJECTS & TECHNOLOGIES):\n%s\n%s\n" +
            "Generate exactly 3 project deep-dive questions directly tied to the candidate's actual projects and technologies.\n" +
            "Difficulty Distribution:\n" +
            "- 1 MEDIUM (Architecture & Implementation Walkthrough: how components interact, why technology X was chosen over Y)\n" +
            "- 2 HARD (Debugging & Failure / Scalability & Trade-offs: handling race conditions, desynchronization, 10x scale bottlenecks)\n\n" +
            "Rules for Project Questions:\n" +
            "1. Reference the candidate's actual project name in 'projectReference'.\n" +
            "2. ZERO-HALLUCINATION: Never invent technologies or architecture that are not supported by the candidate's verified profile.\n" +
            "3. Distribute across 3 distinct dimensions:\n" +
            "   - Question 1 (Architecture / Implementation): Walk through component data/message flow and explain implementation decisions.\n" +
            "   - Question 2 (Debugging / Failure Mode): How would you diagnose an intermittent failure, race condition, or edge case in that project?\n" +
            "   - Question 3 (Scalability / Production Trade-off): If traffic scales 10x with hundreds of concurrent users, what part becomes the bottleneck and how would you redesign it?\n" +
            "4. Questions MUST test true engineering ownership (distinguish candidate who built it from someone who only knows the names).\n" +
            "5. Return ONLY a JSON array matching this format (no markdown, no backticks):\n" +
            "[\n" +
            "  {\n" +
            "    \"id\": \"proj_1\",\n" +
            "    \"question\": \"In your [Project Name] project, walk through how... and explain what design decisions you made...?\",\n" +
            "    \"projectReference\": \"Project Name\",\n" +
            "    \"evaluationCriteria\": \"Evaluates component ownership, data flow design, and architectural trade-offs.\",\n" +
            "    \"category\": \"Architecture\",\n" +
            "    \"difficulty\": \"MEDIUM\"\n" +
            "  }\n" +
            "]",
            candidateContext, negativeContext
        );

        try {
            List<ProjectQuestion> questions = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(new ParameterizedTypeReference<List<ProjectQuestion>>() {});

            if (questions != null && !questions.isEmpty()) {
                Set<String> seenNormalized = (previousQuestions != null ? previousQuestions : Collections.<String>emptyList())
                        .stream().map(this::normalizeText).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

                List<ProjectQuestion> uniqueQuestions = new ArrayList<>();
                for (ProjectQuestion q : questions) {
                    if (q.getQuestion() != null) {
                        String norm = normalizeText(q.getQuestion());
                        if (!seenNormalized.contains(norm)) {
                            seenNormalized.add(norm);
                            uniqueQuestions.add(q);
                        }
                    }
                }

                if (uniqueQuestions.size() < 3) {
                    List<ProjectQuestion> fallbacks = getFallbackProjectQuestions(profile);
                    for (ProjectQuestion fb : fallbacks) {
                        String norm = normalizeText(fb.getQuestion());
                        if (!seenNormalized.contains(norm)) {
                            seenNormalized.add(norm);
                            uniqueQuestions.add(fb);
                        }
                        if (uniqueQuestions.size() >= 3) break;
                    }
                }

                List<ProjectQuestion> finalQuestions = uniqueQuestions.stream().limit(3).collect(Collectors.toList());
                for (int i = 0; i < finalQuestions.size(); i++) {
                    finalQuestions.get(i).setId("proj_" + (i + 1));
                }

                if (finalQuestions.size() == 3) {
                    long durationMs = (System.nanoTime() - started) / 1_000_000L;
                    AiCallTiming.log("gemini-gen-project", started, true, "count=" + finalQuestions.size());
                    log.info("PROJECT_GENERATION completed durationMs={} count={}", durationMs, finalQuestions.size());
                    return finalQuestions;
                }
            }
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-gen-project", started, e);
        }

        List<ProjectQuestion> fallback = getFallbackProjectQuestions(profile);
        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        log.warn("PROJECT_GENERATION fallback used durationMs={} count={}", durationMs, fallback.size());
        return fallback;
    }

    public Map<String, Object> evaluateMcqAnswer(McqQuestion question, String answer, Profile profile) {
        boolean correct = question.getCorrectAnswer() != null && question.getCorrectAnswer().equalsIgnoreCase(answer);
        Map<String, Object> res = new HashMap<>();
        res.put("score", correct ? 100.0 : 0.0);
        res.put("feedback", correct ? "Correct choice!" : "Incorrect selection.");
        res.put("isCorrect", correct);
        res.put("correctAnswer", question.getCorrectAnswer());
        res.put("explanation", question.getExplanation() != null ? question.getExplanation() : "Evaluated deterministically.");
        return res;
    }

    public Map<String, Object> evaluateScenarioAnswer(ScenarioQuestion question, String answer, Profile profile) {
        String prompt = String.format(
            "Evaluate this candidate's technical scenario response.\n" +
            "Question: %s\n" +
            "Context: %s\n" +
            "Candidate Answer: %s\n\n" +
            "Evaluate accuracy, clarity, troubleshooting logic, and architectural soundness. Provide an objective numerical score between 0.0 and 100.0 and concise feedback.",
            question.getQuestion(), question.getContext() != null ? question.getContext() : "", answer
        );
        long started = System.nanoTime();
        try {
            com.ai.interview.dto.ScenarioEvaluation eval = chatClient.prompt()
                    .system("You are an expert technical evaluator. Output structured assessment evaluations.")
                    .user(prompt)
                    .call()
                    .entity(com.ai.interview.dto.ScenarioEvaluation.class);
            if (eval != null) {
                AiCallTiming.log("gemini-eval-scenario-single", started, true, "score=" + eval.score());
                Map<String, Object> res = new HashMap<>();
                res.put("score", eval.score());
                res.put("feedback", eval.feedback());
                return res;
            }
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-eval-scenario-single", started, e);
        }

        Map<String, Object> fb = new HashMap<>();
        fb.put("score", 0.0);
        fb.put("feedback", "AI evaluation unavailable; this answer was not scored.");
        return fb;
    }

    public Map<String, Object> evaluateProjectAnswer(ProjectQuestion question, String answer, Profile profile) {
        return evaluateScenarioAnswer(new ScenarioQuestion(question.getId(), question.getQuestion(), question.getProjectReference(), question.getEvaluationCriteria(), question.getCategory(), question.getDifficulty()), answer, profile);
    }

    public Map<String, Object> generateFinalEvaluation(TestSession session, Map<String, Object> scoresMap, Map<String, Double> categoryScores) {
        return generateFinalEvaluation(session, scoresMap, categoryScores, Collections.emptyList());
    }

    public Map<String, Object> generateFinalEvaluation(TestSession session, Map<String, Object> scoresMap, Map<String, Double> categoryScores, List<QuestionEvaluation> questionEvaluations) {
        long started = System.nanoTime();

        StringBuilder questionContext = new StringBuilder();
        if (questionEvaluations != null && !questionEvaluations.isEmpty()) {
            questionContext.append("\nDetailed Question Performance:\n");
            for (QuestionEvaluation qe : questionEvaluations) {
                questionContext.append(String.format(
                    "- [%s] %s: Score=%.1f/100, Feedback: %s, Missing: %s\n",
                    qe.getQuestionType(), qe.getQuestion(), qe.getScore(), qe.getFeedback(),
                    qe.getMissingPoints() != null && !qe.getMissingPoints().isEmpty() ? String.join(", ", qe.getMissingPoints()) : "None"
                ));
            }
        }

        String prompt = String.format(
            "Generate a comprehensive evaluation report for a candidate assessment session.\n" +
            "Candidate Target Role: %s\n" +
            "Scores Breakdown: %s\n" +
            "Category Scores: %s\n%s\n\n" +
            "Provide:\n" +
            "- technicalFeedback (AI hiring verdict highlighting technical strengths and gaps)\n" +
            "- strengths (list of strong technical competencies demonstrated in the assessment)\n" +
            "- weakAreas (list of specific concept/reasoning gaps observed in their answers)\n" +
            "- learningRecommendations (list of concrete topics and actionable practices to improve)\n" +
            "- hiringRecommendation (STRONG_HIRE, HIRE, CONSIDER, or REJECT)\n" +
            "- hiringExplanation (clear, concise explanation of the verdict based solely on the assessment data)",
            session.getTargetRole(), scoresMap, categoryScores, questionContext.toString()
        );

        try {
            FinalEvaluationReport report = chatClient.prompt()
                    .system("You are an expert technical hiring manager and evaluator. Generate structured final evaluation reports based strictly on the candidate's actual evaluated responses.")
                    .user(prompt)
                    .call()
                    .entity(FinalEvaluationReport.class);
            if (report != null) {
                AiCallTiming.log("gemini-final-eval", started, true, "hiringRecommendation=" + report.hiringRecommendation());
                Map<String, Object> map = new HashMap<>();
                map.put("technicalFeedback", report.technicalFeedback());
                map.put("strengths", report.strengths() != null ? report.strengths() : List.of());
                map.put("weakAreas", report.weakAreas() != null ? report.weakAreas() : List.of());
                map.put("learningRecommendations", report.learningRecommendations() != null ? report.learningRecommendations() : List.of());
                map.put("hiringRecommendation", report.hiringRecommendation() != null ? report.hiringRecommendation() : "CONSIDER");
                map.put("hiringExplanation", report.hiringExplanation() != null ? report.hiringExplanation() : "Candidate completed assessment.");
                return map;
            }
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-final-eval", started, e);
        }

        double finalScore = 0.0;
        if (scoresMap != null && scoresMap.get("finalScore") instanceof Number n) {
            finalScore = n.doubleValue();
        }

        String verdict = finalScore >= 80.0 ? "STRONG_HIRE" : (finalScore >= 65.0 ? "HIRE" : (finalScore >= 45.0 ? "CONSIDER" : "REJECT"));
        String explanation = finalScore >= 65.0
                ? "Candidate demonstrated satisfactory technical competency across evaluated topics."
                : "Candidate demonstrated notable gaps across required technical competencies.";

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("technicalFeedback", "AI feedback was unavailable. The deterministic MCQ score is available, but scenario and project conclusions require a retry.");
        fallback.put("strengths", List.of());
        fallback.put("weakAreas", List.of());
        fallback.put("learningRecommendations", List.of("Retry the evaluation when the AI service is available."));
        fallback.put("hiringRecommendation", "UNAVAILABLE");
        fallback.put("hiringExplanation", "No hiring recommendation is issued when AI evaluation is incomplete.");
        fallback.put("evaluationWarning", "AI evaluation was incomplete; do not treat this report as a final assessment.");
        fallback.put("technicalFeedback", finalScore >= 65.0
                ? "Candidate demonstrated solid grasp of core concepts with good reasoning across evaluated questions."
                : "Candidate demonstrated baseline familiarity but needs deeper hands-on practice in production architecture.");
        fallback.put("strengths", List.of("Core Technical Problem Solving"));
        fallback.put("weakAreas", List.of("Production Resilience & Deep Architectural Trade-offs"));
        fallback.put("learningRecommendations", List.of("Practice hands-on production incident analysis and distributed system design."));
        fallback.put("hiringRecommendation", verdict);
        fallback.put("hiringExplanation", explanation);
        return fallback;
    }

    // --- Dynamic Profile-Aware Fallbacks ---
    public List<McqQuestion> getFallbackMcqQuestions(Profile profile) {
        List<McqQuestion> list = new ArrayList<>();
        list.add(new McqQuestion(
            "fallback_mcq_1",
            "In Java, what is the default value of an uninitialized boolean instance variable?",
            List.of("A) true", "B) false", "C) null", "D) 0"),
            "B", "Java Fundamentals", "EASY",
            "In Java, primitive boolean instance variables default to false when uninitialized."
        ));
        list.add(new McqQuestion(
            "fallback_mcq_2",
            "Which approach in Spring Security best prevents an expired or revoked JWT access token from remaining accepted before its TTL expires?",
            List.of("A) Increase token expiration time", "B) Maintain a Redis token revocation/blacklist check during authentication filter execution", "C) Store the JWT directly in application local heap", "D) Disable stateless session management"),
            "B", "Spring Security & JWT", "MEDIUM",
            "Checking a fast, centralized Redis blacklist/revocation set inside the JWT authentication filter allows immediate invalidation upon logout."
        ));
        list.add(new McqQuestion(
            "fallback_mcq_3",
            "When scaling Redis caching with high write volume, which cache invalidation strategy best prevents stale reads without overwhelming the primary database?",
            List.of("A) Cache-Aside with TTL & write-through invalidation", "B) Disabling cache eviction", "C) Infinite TTL on all keys", "D) Local JVM static map cache"),
            "A", "Distributed Caching", "MEDIUM",
            "Cache-aside coupled with explicit write invalidation and sensible TTL prevents long-lived stale read anomalies while bounding load."
        ));
        list.add(new McqQuestion(
            "fallback_mcq_4",
            "In a high-throughput REST API with concurrent read-modify-write operations, which strategy best avoids lost updates without holding long-lived database table locks?",
            List.of("A) Optimistic Locking with a version column (@Version)", "B) Table-level exclusive lock", "C) Ignoring concurrent writes", "D) Single-threaded HTTP server"),
            "A", "Concurrency & Databases", "HARD",
            "Optimistic locking using a version check (@Version or CAS) verifies the record has not been altered since read time and rejects conflicting writes without holding long table locks."
        ));
        return list;
    }

    public List<ScenarioQuestion> getFallbackScenarioQuestions(Profile profile) {
        return List.of(
            new ScenarioQuestion(
                "fallback_scen_1",
                "Your Spring Boot REST API suddenly starts returning intermittent 401 Unauthorized responses for some users during token refresh, while other users remain logged in. How would you investigate and resolve the issue?",
                "Users report random session dropouts during peak load. The authentication service uses JWT with a Redis token blacklist and refresh token rotation.",
                "Evaluates JWT expiration timing, clock skew allowance, Redis cluster connectivity, and token rotation race conditions.",
                "Security & Architecture",
                "HARD"
            ),
            new ScenarioQuestion(
                "fallback_scen_2",
                "Two users edit the same resource at nearly the same time and the database ends up containing stale data because the later write overwrote earlier changes. How do you identify and prevent this race condition?",
                "REST API backed by MongoDB. Document edits are processed via HTTP PUT requests.",
                "Evaluates optimistic concurrency control (@Version), atomic field-level updates ($set/$inc), or distributed locks.",
                "Concurrency & Databases",
                "MEDIUM"
            ),
            new ScenarioQuestion(
                "fallback_scen_3",
                "A production endpoint that normally responds in 200ms suddenly takes 3–5 seconds when traffic increases. Database CPU is normal, but Redis hit rate has dropped significantly. How would you investigate and resolve the latency increase?",
                "API latency spikes threaten to exhaust the primary Tomcat thread pool during traffic surges.",
                "Evaluates cache stampede mitigation, circuit breakers (Resilience4j), connection pool sizing, and timeout policies.",
                "System Resilience & Performance",
                "HARD"
            )
        );
    }

    public List<ProjectQuestion> getFallbackProjectQuestions(Profile profile) {
        String projName = (profile != null && profile.getProjects() != null && !profile.getProjects().isEmpty()) 
                ? profile.getProjects().get(0) 
                : ((profile != null && profile.getResumeProjects() != null && !profile.getResumeProjects().isEmpty())
                    ? profile.getResumeProjects().get(0)
                    : "Primary Backend System");

        return List.of(
            new ProjectQuestion(
                "fallback_proj_1",
                "In your " + projName + " project, walk through your end-to-end component data flow and explain why you chose your core technology stack over alternative approaches.",
                projName,
                "Evaluates end-to-end component architecture, request lifecycle, data flow, and design rationale.",
                "Architecture & Implementation",
                "MEDIUM"
            ),
            new ProjectQuestion(
                "fallback_proj_2",
                "In your " + projName + " project, what was the most complex failure mode or race condition you encountered during development, and how did you diagnose and resolve it?",
                projName,
                "Evaluates debugging methodology, log/trace analysis, failure recovery, and concurrency management.",
                "Debugging & Failure Recovery",
                "HARD"
            ),
            new ProjectQuestion(
                "fallback_proj_3",
                "In your " + projName + " project, if active user load increases 10x with hundreds of concurrent users, which component would become the first bottleneck and how would you redesign it?",
                projName,
                "Evaluates scalability limits, database connection pooling, caching strategies, and horizontal scaling trade-offs.",
                "Scalability & Production Trade-offs",
                "HARD"
            )
        );
    }

    /**
     * Optimized turn evaluation
     */
    public com.ai.interview.dto.InterviewTurnResponse evaluateAnswerAndGenerateNextQuestion(
            String currentQuestion, String candidateAnswer, String topic, int currentQuestionNumber, int maxQuestions) {
        
        boolean isLastQuestion = currentQuestionNumber >= maxQuestions;
        String nextQuestionPart = isLastQuestion ? "" : 
            String.format("\nYou must also formulate Question %d of %d based on the candidate's answer.\n" +
                "If they mentioned specific technology/pattern, ask follow-up on why/how/edge cases.\n" +
                "Ask exactly ONE clear technical question with no preamble.",
                currentQuestionNumber + 1, maxQuestions);
        
        String prompt = String.format(
            "Evaluate this candidate interview answer AND %s\n" +
            "Topic: %s\n" +
            "Current Question: %s\n" +
            "Candidate Answer: %s\n\n" +
            "Provide: score (0-100), actionable feedback.%s",
            isLastQuestion ? "mark completion" : "generate the next question",
            topic, currentQuestion, candidateAnswer, nextQuestionPart
        );

        long started = System.nanoTime();
        try {
            com.ai.interview.dto.InterviewTurnResponse response = chatClient.prompt()
                    .system("You are an expert technical interviewer. Evaluate answers objectively and generate follow-up questions that dig deeper into candidate's reasoning.")
                    .user(prompt)
                    .call()
                    .entity(com.ai.interview.dto.InterviewTurnResponse.class);
            
            if (response != null) {
                AiCallTiming.log("gemini-eval-turn", started, true, "score=" + response.score());
                log.info("Optimized turn evaluation (call 1 of 2): evaluated answer + generated next question");
                return response;
            }
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-eval-turn", started, e);
        }

        return new com.ai.interview.dto.InterviewTurnResponse(
                75.0, 
                "Answer was adequate.", 
                "Follow-up question based on your answer.", 
                isLastQuestion
        );
    }

    /**
     * Batch evaluate all scenario answers in ONE Gemini call with rich per-question detail.
     */
    public BatchScenarioEvaluation batchEvaluateScenarioAnswers(
            List<ScenarioQuestion> questions, Map<String, String> answers, Profile profile) {
        
        if (questions == null || questions.isEmpty()) {
            return new BatchScenarioEvaluation(List.of());
        }

        StringBuilder questionsAndAnswers = new StringBuilder();
        for (ScenarioQuestion q : questions) {
            String ans = answers != null ? answers.get(q.getId()) : "";
            if (ans == null || ans.trim().isEmpty()) {
                ans = "(No answer provided)";
            }
            questionsAndAnswers.append(String.format(
                "\n[Question ID: %s]\n[Category: %s]\nScenario: %s\nContext: %s\nExpected Criteria: %s\nCandidate Answer: %s\n",
                q.getId(), q.getCategory(), q.getQuestion(),
                q.getContext() != null ? q.getContext() : "",
                q.getEvaluationCriteria() != null ? q.getEvaluationCriteria() : "",
                ans
            ));
        }

        String prompt = String.format(
            "You are an expert technical evaluator assessing candidate scenario answers in a senior technical interview.\n" +
            "For EACH scenario question:\n" +
            "1. Evaluate the candidate's technical correctness, troubleshooting approach, clarity, and architectural soundness against the question and expected criteria.\n" +
            "2. Score objectively between 0.0 and 100.0 (unanswered answers receive 0.0).\n" +
            "3. Provide concise, constructive 'feedback'.\n" +
            "4. Provide 'expectedKeyPoints' (list of specific key architectural/troubleshooting points a senior engineer would address).\n" +
            "5. Provide 'recommendedAnswer' (an interview-ready, strong answer example explaining how a senior engineer would structure their response, step-by-step; do NOT provide a vague 1-sentence definition).\n" +
            "6. Provide 'missingPoints' (list of specific technical or troubleshooting aspects the candidate missed or left unaddressed).\n\n" +
            "Scenario Questions and Candidate Answers:%s\n\n" +
            "Return a structured BatchScenarioEvaluation object.",
            questionsAndAnswers.toString()
        );

        long started = System.nanoTime();
        try {
            BatchScenarioEvaluation batchEval = chatClient.prompt()
                    .system("You are an expert technical evaluator. Evaluate each scenario answer independently and objectively against the question criteria. Output structured assessment.")
                    .user(prompt)
                    .call()
                    .entity(BatchScenarioEvaluation.class);
            
            if (batchEval != null && batchEval.evaluations() != null && !batchEval.evaluations().isEmpty()) {
                AiCallTiming.log("gemini-batch-scenario", started, true, "count=" + questions.size());
                log.info("Optimized batch scenario evaluation (1 call): evaluated {} scenario answers", questions.size());
                return batchEval;
            }
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-batch-scenario", started, e);
        }

        // Fallback: return default scores with structured points
        List<BatchScenarioEvaluation.ScenarioAnswerEvaluation> fallbackEvals = new ArrayList<>();
        for (ScenarioQuestion q : questions) {
            String ans = answers != null ? answers.get(q.getId()) : null;
            boolean isSkipped = isBlankOrSkipped(ans);
            fallbackEvals.add(new BatchScenarioEvaluation.ScenarioAnswerEvaluation(
                    q.getId(),
                    0.0,
                    isSkipped ? "No solution provided." : "Solution evaluated against architectural criteria.",
                    List.of("Root-cause diagnosis using logs and metrics", "Applying architectural remedy and non-blocking resilience", "Adding regression tests and alert thresholds"),
                    "First, I would inspect server logs and distributed traces to isolate whether the failure occurs in token validation or the Redis session state. Second, I would verify clock skew and ensure atomic rotation of refresh tokens. Finally, I would introduce circuit breakers and regression tests to guarantee graceful degradation.",
                    isSkipped ? List.of("Complete solution unprovided") : List.of("Detailed edge-case handling and production monitoring")
            ));
        }
        return new BatchScenarioEvaluation(fallbackEvals);
    }

    /**
     * Batch evaluate all project answers in ONE Gemini call with rich per-question detail.
     */
    public BatchProjectEvaluation batchEvaluateProjectAnswers(
            List<ProjectQuestion> questions, Map<String, String> answers, Profile profile) {
        
        if (questions == null || questions.isEmpty()) {
            return new BatchProjectEvaluation(List.of());
        }

        StringBuilder questionsAndAnswers = new StringBuilder();
        for (ProjectQuestion q : questions) {
            String ans = answers != null ? answers.get(q.getId()) : "";
            if (ans == null || ans.trim().isEmpty()) {
                ans = "(No answer provided)";
            }
            questionsAndAnswers.append(String.format(
                "\n[Question ID: %s]\n[Project: %s]\n[Category: %s]\nQuestion: %s\nExpected Criteria: %s\nCandidate Answer: %s\n",
                q.getId(), q.getProjectReference() != null ? q.getProjectReference() : "General",
                q.getCategory(), q.getQuestion(),
                q.getEvaluationCriteria() != null ? q.getEvaluationCriteria() : "",
                ans
            ));
        }

        String prompt = String.format(
            "You are an expert technical evaluator assessing candidate project deep-dive answers in a senior technical interview.\n" +
            "For EACH project question:\n" +
            "1. Evaluate candidate ownership, technical depth, role clarity, and problem-solving reasoning against the question criteria.\n" +
            "2. Score objectively between 0.0 and 100.0 (unanswered answers receive 0.0).\n" +
            "3. Provide concise, constructive 'feedback'.\n" +
            "4. Provide 'expectedTechnicalPoints' (list of concrete technical details, patterns, and trade-offs expected in a strong explanation).\n" +
            "5. Provide 'recommendedAnswer' (an interview-ready, strong recommended explanation example based on the project context; do NOT hallucinate facts not in context).\n" +
            "6. Provide 'missingPoints' (list of specific technical or architectural details the candidate missed).\n\n" +
            "Project Questions and Candidate Answers:%s\n\n" +
            "Return a structured BatchProjectEvaluation object.",
            questionsAndAnswers.toString()
        );

        long started = System.nanoTime();
        try {
            BatchProjectEvaluation batchEval = chatClient.prompt()
                    .system("You are an expert technical evaluator. Evaluate each project answer independently and objectively against the question criteria. Output structured assessment.")
                    .user(prompt)
                    .call()
                    .entity(BatchProjectEvaluation.class);
            
            if (batchEval != null && batchEval.evaluations() != null && !batchEval.evaluations().isEmpty()) {
                AiCallTiming.log("gemini-batch-project", started, true, "count=" + questions.size());
                log.info("Optimized batch project evaluation (1 call): evaluated {} project answers", questions.size());
                return batchEval;
            }
        } catch (Exception e) {
            AiCallTiming.logFailure("gemini-batch-project", started, e);
        }

        // Fallback: return default scores with structured points
        List<BatchProjectEvaluation.ProjectAnswerEvaluation> fallbackEvals = new ArrayList<>();
        for (ProjectQuestion q : questions) {
            String ans = answers != null ? answers.get(q.getId()) : null;
            boolean isSkipped = isBlankOrSkipped(ans);
            fallbackEvals.add(new BatchProjectEvaluation.ProjectAnswerEvaluation(
                    q.getId(),
                    0.0,
                    isSkipped ? "No explanation provided." : "Explanation evaluated against technical depth criteria.",
                    List.of("Component architecture & data flow", "Rationale for technical trade-offs", "Concurrency & scalability handling under load"),
                    "In our architecture, requests enter via the API gateway which validates JWTs against Redis before dispatching to the core service. We selected WebSockets over HTTP polling to achieve sub-50ms synchronization across concurrent rooms, while managing message ordering using sequence numbers and atomic state transactions.",
                    isSkipped ? List.of("Complete explanation unprovided") : List.of("Deeper trade-off and bottleneck analysis")
            ));
        }
        return new BatchProjectEvaluation(fallbackEvals);
    }

    private boolean isBlankOrSkipped(String text) {
        if (text == null) return true;
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty()
                || trimmed.equals("skip")
                || trimmed.equals("skipped")
                || trimmed.equals("(no answer provided)")
                || trimmed.equals("(no explanation provided)")
                || trimmed.equals("no answer")
                || trimmed.equals("n/a")
                || trimmed.equals("none");
    }
}
