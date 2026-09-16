package com.ai.interview.service;

import com.ai.interview.dto.InterviewFinalReport;
import com.ai.interview.dto.InterviewTurnEvaluation;
import com.ai.interview.exception.ForbiddenException;
import com.ai.interview.exception.ResourceNotFoundException;
import com.ai.interview.exception.ServiceUnavailableException;
import com.ai.interview.exception.UnauthorizedException;
import com.ai.interview.model.InterviewSession;
import com.ai.interview.model.Profile;
import com.ai.interview.repository.InterviewSessionRepository;
import com.ai.interview.repository.ProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class MockInterviewService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final ProfileRepository profileRepository;
    private final SkillAnalyzerService skillAnalyzerService;
    private final ChatClient mockInterviewChatClient;
    private final ChatClient mockInterviewFinalChatClient;
    private final String geminiApiKey;

    public MockInterviewService(InterviewSessionRepository interviewSessionRepository,
            ProfileRepository profileRepository,
            SkillAnalyzerService skillAnalyzerService,
            @Qualifier("mockInterviewChatClient") ChatClient mockInterviewChatClient,
            @Qualifier("mockInterviewFinalChatClient") ChatClient mockInterviewFinalChatClient,
            @Value("${spring.ai.google.genai.api-key:}") String geminiApiKey) {
        this.interviewSessionRepository = interviewSessionRepository;
        this.profileRepository = profileRepository;
        this.skillAnalyzerService = skillAnalyzerService;
        this.mockInterviewChatClient = mockInterviewChatClient;
        this.mockInterviewFinalChatClient = mockInterviewFinalChatClient;
        this.geminiApiKey = geminiApiKey;
    }

    public InterviewSession startInterview(String userId, String topic) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("User authentication is required to start an interview session.");
        }

        log.info("Mock interview start: loading profile for user {}", userId);
        Profile profile = profileRepository.findByUserId(userId).orElse(null);
        
        String targetRole = (profile != null && profile.getTargetRole() != null && !profile.getTargetRole().isBlank())
                ? profile.getTargetRole().trim()
                : "Software Engineer";
                
        String skills = (profile != null && profile.getSkills() != null && !profile.getSkills().isEmpty())
                ? profile.getSkills().stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).reduce((a, b) -> a + ", " + b).orElse("General Software Engineering")
                : "Core Technical Concepts, Architecture, Problem Solving";

        String interviewTopic = (topic != null && !topic.isBlank()) ? topic.trim() : targetRole;

        String firstQuestionPrompt = String.format(
            "You are an expert technical interviewer conducting a live AI Technical Mock Interview for a candidate.\n" +
            "Candidate Role: %s\n" +
            "Candidate Skills: %s\n" +
            "Interview Topic: %s\n\n" +
            "Ask exactly ONE single, targeted, open-ended technical interview question for Question 1 of 4.\n" +
            "Do NOT ask multiple questions. Do NOT provide answers or preamble. Ask directly and professionally.",
            targetRole, skills, interviewTopic
        );

        String sessionId = UUID.randomUUID().toString();
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new ServiceUnavailableException("AI interview is not configured. Set GEMINI_API_KEY and try again.",
                    new IllegalStateException("GEMINI_API_KEY is missing"));
        }

        log.info("Mock interview start: requesting initial question from AI provider");
        String initialQuestion = null;
        try {
            initialQuestion = mockInterviewChatClient.prompt()
                .user(firstQuestionPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        } catch (Exception ex) {
            log.warn("Mock interview start AI failure for session {} (using fallback question 1): {}: {}", sessionId, ex.getClass().getSimpleName(), ex.getMessage());
            initialQuestion = getFallbackFollowupQuestion(interviewTopic, 1, "");
        }

        if (initialQuestion == null || initialQuestion.isBlank()) {
            initialQuestion = getFallbackFollowupQuestion(interviewTopic, 1, "");
        }
        log.info("Mock interview start: initial question ready for session {}", sessionId);

        InterviewSession session = InterviewSession.builder()
                .id(sessionId)
                .userId(userId)
                .targetRole(targetRole)
                .topic(interviewTopic)
                .status("IN_PROGRESS")
                .currentQuestion(initialQuestion.trim())
                .questionCount(1)
                .maxQuestions(4)
                .history(new ArrayList<>())
                .finalReport(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .build();

        InterviewSession saved = interviewSessionRepository.save(session);
        log.info("Mock interview started: session {} saved successfully", sessionId);
        return saved;
    }

    public InterviewSession submitAnswer(String sessionId, String userId, String candidateAnswer) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResourceNotFoundException("Interview session ID is required");
        }

        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found: " + sessionId));

        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied to interview session: " + sessionId);
        }

        if ("COMPLETED".equals(session.getStatus())) {
            return session;
        }

        String safeAnswer = (candidateAnswer != null && !candidateAnswer.isBlank())
                ? candidateAnswer.trim()
                : "No response provided.";

        boolean nonAnswerDetected = isNonAnswer(safeAnswer);

        // OPTIMIZATION: Single Gemini call for both evaluation AND next-question generation
        // This replaces two separate calls with one combined structured call
        com.ai.interview.dto.InterviewTurnResponse turnResponse = null;
        double score = 0.0;
        String feedback = nonAnswerDetected 
                ? "The candidate did not know or provide a technical answer for this question." 
                : "Technical answer provided.";
        String nextQuestion = null;

        try {
            turnResponse = mockInterviewChatClient.prompt()
                    .system("You are an expert technical interviewer evaluating a live candidate. " +
                            "Evaluate the candidate's answer objectively and accurately according to technical depth and correctness. " +
                            "CRITICAL: If the candidate says they don't know, passes, skips, gives an off-topic greeting, or fails to answer the question, score MUST be 0.0 with constructive notes explaining the correct answer. " +
                            "If the answer is a genuine technical attempt, score between 20.0 and 100.0 based on precision and depth.")
                    .user(String.format(
                        "Evaluate this candidate interview answer objectively.\n" +
                        "Topic: %s\n" +
                        "Question: %s\n" +
                        "Candidate Answer: %s\n\n" +
                        "SCORING GUIDELINES:\n" +
                        "- If candidate states 'I don't know', passes, skips, or gives irrelevant greeting/filler: score = 0.0\n" +
                        "- If weak/incomplete technical answer: score = 20.0 - 50.0\n" +
                        "- If good/thorough technical answer: score = 60.0 - 100.0\n\n" +
                        "Provide score (0.0 to 100.0) and constructive interviewer feedback explaining the correct technical principles.%s",
                        session.getTopic(), session.getCurrentQuestion(), safeAnswer,
                        session.getQuestionCount() < session.getMaxQuestions() 
                            ? String.format("\n\nAlso formulate Question %d of %d based on the candidate's answer. " +
                                "Ask exactly ONE clear technical question with no preamble.",
                                session.getQuestionCount() + 1, session.getMaxQuestions())
                            : ""
                    ))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .entity(com.ai.interview.dto.InterviewTurnResponse.class);
            
            if (turnResponse != null) {
                if (nonAnswerDetected) {
                    score = 0.0;
                } else {
                    score = Math.min(100.0, Math.max(0.0, turnResponse.score()));
                }

                if (turnResponse.evaluationFeedback() != null && !turnResponse.evaluationFeedback().isBlank()) {
                    feedback = turnResponse.evaluationFeedback().trim();
                } else if (nonAnswerDetected) {
                    feedback = "Candidate did not provide a technical answer. Study core concepts for this topic.";
                }

                if (!turnResponse.isLastQuestion() && turnResponse.nextQuestion() != null && !turnResponse.nextQuestion().isBlank()) {
                    nextQuestion = turnResponse.nextQuestion().trim();
                }
                log.info("OPTIMIZED: Mock interview session {}: evaluated answer (score: {}) + generated next question in 1 call", sessionId, score);
            } else {
                score = nonAnswerDetected ? 0.0 : 30.0;
                feedback = nonAnswerDetected ? "The candidate did not know or provide an answer for this question." : "Answer recorded.";
            }
        } catch (Exception e) {
            log.warn("Interview turn answer evaluation (optimized call) AI fallback for session {}: {}", sessionId, e.getMessage());
            if (nonAnswerDetected) {
                score = 0.0;
                feedback = "Candidate did not provide a technical answer for this question.";
            } else {
                score = safeAnswer.length() > 60 ? 55.0 : 30.0;
                feedback = safeAnswer.length() > 60 ? "Answer addressed the question with reasonable depth." : "Answer was brief; recommend providing more technical detail.";
            }
        }

        InterviewSession.InterviewTurn turn = new InterviewSession.InterviewTurn(
                session.getCurrentQuestion(), safeAnswer, feedback, score, LocalDateTime.now()
        );
        if (session.getHistory() == null) {
            session.setHistory(new ArrayList<>());
        }
        session.getHistory().add(turn);

        // Check if all 4 questions have been answered
        if (session.getQuestionCount() >= session.getMaxQuestions()) {
            log.info("Mock interview session {} completed all {} questions. Generating final evaluation report.", sessionId, session.getMaxQuestions());
            return finalizeInterview(session);
        }

        // If next question not provided by optimized call, use fallback
        if (nextQuestion == null || nextQuestion.isBlank()) {
            int nextQuestionNumber = session.getQuestionCount() + 1;
            nextQuestion = getFallbackFollowupQuestion(session.getTopic(), nextQuestionNumber, safeAnswer);
        }

        session.setCurrentQuestion(nextQuestion);
        session.setQuestionCount(session.getQuestionCount() + 1);

        return interviewSessionRepository.save(session);
    }

    private String getFallbackFollowupQuestion(String topic, int questionNumber, String previousAnswer) {
        String lower = (topic != null ? topic : "").toLowerCase(Locale.ROOT);
        if (lower.contains("java") || lower.contains("spring") || lower.contains("backend")) {
            if (questionNumber == 1) {
                return "Could you walk me through the lifecycle of an incoming HTTP request in a Spring Boot application, detailing what happens from the moment it reaches the embedded server down to the controller and back?";
            } else if (questionNumber == 2) {
                return "Building on your previous answer, how would you design and implement robust concurrency controls or distributed locks when multiple service instances update the same shared resource?";
            } else if (questionNumber == 3) {
                return "In high-throughput Spring Boot architectures, what specific caching patterns and cache invalidation strategies (such as cache-aside or write-through) do you apply with Redis?";
            } else {
                return "When designing production RESTful APIs, how do you handle idempotency, distributed transaction rollbacks (e.g. Saga pattern), and graceful failure degradation under heavy traffic?";
            }
        } else if (lower.contains("design") || lower.contains("architecture")) {
            if (questionNumber == 1) {
                return "How would you design a scalable notification service that handles millions of notifications daily across push, email, and SMS with rate limiting and fault tolerance?";
            } else if (questionNumber == 2) {
                return "How do you evaluate database partitioning, sharding strategies, and read replica synchronization trade-offs when designing for millions of daily active users?";
            } else if (questionNumber == 3) {
                return "How do you implement asynchronous event-driven messaging (using Kafka or RabbitMQ) while guaranteeing at-least-once or exactly-once processing semantics?";
            } else {
                return "What architectural mechanisms (rate limiting, circuit breakers, backpressure) would you incorporate to ensure 99.99% system availability during peak traffic spikes?";
            }
        } else if (lower.contains("sql") || lower.contains("database")) {
            if (questionNumber == 1) {
                return "How does relational indexing work under the hood in PostgreSQL/MySQL, and how do composite indexes impact query performance versus table write latency?";
            } else if (questionNumber == 2) {
                return "How do B-Tree and LSM-tree indexing structures differ under heavy write versus heavy read workloads, and how do you diagnose slow query execution plans using EXPLAIN ANALYZE?";
            } else if (questionNumber == 3) {
                return "Explain the four ANSI SQL transaction isolation levels and what concurrency anomalies (dirty reads, non-repeatable reads, phantom reads, serialization anomalies) each level prevents.";
            } else {
                return "How do you manage database connection pooling (e.g., HikariCP) sizing, connection timeouts, and deadlock detection under high concurrent request loads?";
            }
        } else {
            if (questionNumber == 1) {
                return "Could you explain the core architecture of your recent full-stack system and how data flows between client, API gateway, services, and storage?";
            } else if (questionNumber == 2) {
                return "Could you delve deeper into the trade-offs of your proposed solution regarding scalability, maintainability, and latency?";
            } else if (questionNumber == 3) {
                return "How would you handle error recovery, logging, and observability (metrics & tracing) for this workflow in a production environment?";
            } else {
                return "What edge cases and boundary conditions would you prioritize in automated testing before shipping this feature to production?";
            }
        }
    }

    private InterviewSession finalizeInterview(InterviewSession session) {
        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now());

        double avgTurnScore = session.getHistory().stream()
                .mapToDouble(t -> t.getScore() != null ? t.getScore() : 0.0)
                .average().orElse(0.0);

        StringBuilder compactTranscript = new StringBuilder();
        for (int i = 0; i < session.getHistory().size(); i++) {
            InterviewSession.InterviewTurn turn = session.getHistory().get(i);
            compactTranscript.append(String.format(
                    "Turn %d:\nQuestion: %s\nAnswer: %s\nScore: %.1f\nFeedback: %s\n\n",
                    (i + 1),
                    turn.getQuestion(),
                    turn.getCandidateAnswer(),
                    turn.getScore(),
                    turn.getEvaluationFeedback()
            ));
        }

        String finalPrompt = String.format(
            "You are a Senior Technical Interview Evaluator. Evaluate the candidate's complete 4-round mock interview performance.\n" +
            "Role: %s\n" +
            "Topic: %s\n" +
            "Calculated Average Turn Score: %.1f\n\n" +
            "Turn-by-turn interview summary:\n%s\n\n" +
            "Generate a comprehensive final structured evaluation report containing:\n" +
            "- overallScore: overall percentage (0-100). If candidate failed to answer or stated they did not know for questions, reflect this honestly with a low score (e.g. 0-25).\n" +
            "- technicalScore: technical accuracy score (0-100)\n" +
            "- communicationScore: communication clarity score (0-100)\n" +
            "- confidenceScore: confidence & structured reasoning score (0-100)\n" +
            "- performanceSummary: interviewer evaluation summary analyzing how candidate handled the 4 technical questions\n" +
            "- strongAreas: list of concrete technical concepts, patterns, or tools candidate demonstrated well\n" +
            "- weakAreas: list of specific areas where candidate lagged, showed gaps, or was imprecise\n" +
            "- conceptsToImprove: list of core technical concepts/topics candidate needs to study\n" +
            "- communicationFeedback: feedback on technical articulation, trade-off explanation, and structure\n" +
            "- improvementSuggestions: practical, actionable real-world improvement steps and exercises\n" +
            "- finalFeedback: professional concluding feedback and recommendation.",
            session.getTargetRole(), session.getTopic(), avgTurnScore, compactTranscript.toString()
        );

        Map<String, Object> report = new HashMap<>();
        double calculatedOverall = Math.round(avgTurnScore);

        try {
            InterviewFinalReport generatedReport = mockInterviewFinalChatClient.prompt()
                    .system("You are an expert technical interview evaluator. Generate authentic, constructive evaluation grounded strictly in the candidate's 4 answers.")
                    .user(finalPrompt)
                    .call()
                    .entity(InterviewFinalReport.class);

            if (generatedReport != null) {
                double overall = generatedReport.overallScore() >= 0 ? generatedReport.overallScore() : avgTurnScore;
                double tech = generatedReport.technicalScore() >= 0 ? generatedReport.technicalScore() : avgTurnScore;
                double comm = generatedReport.communicationScore() >= 0 ? generatedReport.communicationScore() : avgTurnScore;
                double conf = generatedReport.confidenceScore() >= 0 ? generatedReport.confidenceScore() : avgTurnScore;

                calculatedOverall = Math.round(overall);
                report.put("overallScore", calculatedOverall);
                report.put("technicalScore", Math.round(tech));
                report.put("communicationScore", Math.round(comm));
                report.put("confidenceScore", Math.round(conf));
                report.put("performanceSummary", (generatedReport.performanceSummary() != null && !generatedReport.performanceSummary().isBlank())
                        ? generatedReport.performanceSummary()
                        : "Candidate demonstrated solid understanding across the technical interview rounds with structured problem-solving.");
                report.put("strongAreas", (generatedReport.strongAreas() != null && !generatedReport.strongAreas().isEmpty())
                        ? generatedReport.strongAreas()
                        : (calculatedOverall >= 50 
                            ? List.of("Structured problem breakdown", "Clear articulation of core concepts")
                            : List.of("Interview participation", "Clear voice audio")));
                report.put("weakAreas", (generatedReport.weakAreas() != null && !generatedReport.weakAreas().isEmpty())
                        ? generatedReport.weakAreas()
                        : (calculatedOverall >= 50
                            ? List.of("Edge-case handling depth", "Architectural trade-off analysis")
                            : List.of("Core technical fundamentals", "Directly answering technical questions")));
                report.put("conceptsToImprove", (generatedReport.conceptsToImprove() != null && !generatedReport.conceptsToImprove().isEmpty())
                        ? generatedReport.conceptsToImprove()
                        : List.of(session.getTopic() + " Deep Dive", "Concurrency & Scalability"));
                report.put("communicationFeedback", (generatedReport.communicationFeedback() != null && !generatedReport.communicationFeedback().isBlank())
                        ? generatedReport.communicationFeedback()
                        : "Clear voice delivery. Practice structuring solutions step-by-step.");
                report.put("improvementSuggestions", (generatedReport.improvementSuggestions() != null && !generatedReport.improvementSuggestions().isEmpty())
                        ? generatedReport.improvementSuggestions()
                        : List.of("Practice answering scenario questions by stating assumptions first.",
                                  "Deepen knowledge of core technical concepts for " + session.getTopic() + ".",
                                  "Review real-world interview practice problems related to " + session.getTopic() + "."));
                report.put("finalFeedback", (generatedReport.finalFeedback() != null && !generatedReport.finalFeedback().isBlank())
                        ? generatedReport.finalFeedback()
                        : "Continue strengthening in-depth architectural knowledge and hands-on concepts.");
            }
        } catch (Exception e) {
            log.warn("Mock interview final report AI generation fallback for session {}: {}", session.getId(), e.getMessage());
            calculatedOverall = Math.round(avgTurnScore);
            report.put("overallScore", calculatedOverall);
            report.put("technicalScore", Math.round(avgTurnScore));
            report.put("communicationScore", Math.round(avgTurnScore));
            report.put("confidenceScore", Math.round(avgTurnScore));
            report.put("performanceSummary", calculatedOverall >= 50
                    ? "The candidate completed all 4 questions on " + session.getTopic() + " with an average score of " + calculatedOverall + "%."
                    : "The candidate completed the mock interview on " + session.getTopic() + ". Further study and practice of foundational concepts is recommended.");
            report.put("strongAreas", calculatedOverall >= 50 
                    ? List.of("Clear technical articulation", "Demonstrated problem solving on " + session.getTopic())
                    : List.of("Willingness to practice", "Clear verbal delivery"));
            report.put("weakAreas", calculatedOverall >= 50
                    ? List.of("Could provide more detailed architectural trade-offs", "Edge case considerations")
                    : List.of("Core technical subject mastery", "Answering technical questions directly"));
            report.put("conceptsToImprove", List.of(session.getTopic() + " Best Practices", "Foundational Concepts & Principles"));
            report.put("communicationFeedback", "Good verbal delivery. Recommend structuring technical answers systematically.");
            report.put("improvementSuggestions", List.of(
                    "Practice explaining core principles before diving into implementations.",
                    "Review foundational tutorials and documentation for " + session.getTopic() + ".",
                    "Conduct timed mock interview drills to build recall under pressure."
            ));
            report.put("finalFeedback", "Promising foundation. Dedicate time to core technical concepts to boost interview readiness.");
        }

        session.setOverallScore(calculatedOverall);
        session.setFinalReport(report);
        InterviewSession saved = interviewSessionRepository.save(session);
        log.info("Mock interview session {} successfully persisted with status COMPLETED and score {}%", session.getId(), calculatedOverall);

        // Trigger continuous skill analysis update for Career Dashboard
        try {
            skillAnalyzerService.analyzeUserPerformance(session.getUserId());
            log.info("Skill analysis updated successfully for user {}", session.getUserId());
        } catch (Exception e) {
            log.warn("Failed to update skill analysis after mock interview completion for user {}: {}", session.getUserId(), e.getMessage());
        }

        return saved;
    }

    public InterviewSession getInterviewSession(String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResourceNotFoundException("Interview session ID is required");
        }
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied to interview session: " + sessionId);
        }
        return session;
    }

    public List<InterviewSession> getUserInterviews(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        return interviewSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public InterviewSession exitInterview(String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResourceNotFoundException("Interview session ID is required");
        }
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied to interview session: " + sessionId);
        }
        if (!"COMPLETED".equalsIgnoreCase(session.getStatus())) {
            session.setStatus("EXITED");
            session = interviewSessionRepository.save(session);
            log.info("Interview session {} marked as EXITED by user {}", sessionId, userId);
        }
        return session;
    }

    public String transcribeAudio(String audioBase64, String mimeType) {
        if (audioBase64 == null || audioBase64.isBlank() || geminiApiKey == null || geminiApiKey.isBlank()) {
            return "";
        }

        String safeMime = (mimeType != null && !mimeType.isBlank()) ? mimeType.trim() : "audio/webm";
        if (safeMime.contains(";")) {
            safeMime = safeMime.split(";")[0].trim();
        }

        String cleanBase64 = audioBase64;
        int commaIndex = cleanBase64.indexOf(",");
        if (commaIndex != -1) {
            cleanBase64 = cleanBase64.substring(commaIndex + 1);
        }
        cleanBase64 = cleanBase64.replaceAll("\\s+", "");

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

                Map<String, Object> inlineData = Map.of(
                        "mime_type", safeMime,
                        "data", cleanBase64
                );
                Map<String, Object> textPart = Map.of(
                        "text", "Transcribe the following spoken English technical interview response verbatim. Return ONLY the transcribed words with proper capitalization and punctuation. Do not add any greetings, commentary, explanations, or quotes."
                );
                Map<String, Object> audioPart = Map.of("inline_data", inlineData);
                Map<String, Object> contentMap = Map.of("parts", List.of(textPart, audioPart));
                Map<String, Object> requestBody = Map.of("contents", List.of(contentMap));

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String jsonPayload = mapper.writeValueAsString(requestBody);

                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
                    com.fasterxml.jackson.databind.JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && candidates.size() > 0) {
                        com.fasterxml.jackson.databind.JsonNode parts = candidates.get(0).path("content").path("parts");
                        if (parts.isArray() && parts.size() > 0) {
                            String text = parts.get(0).path("text").asText("");
                            log.info("Audio transcription successfully completed: {} chars", text.length());
                            return text.trim();
                        }
                    }
                } else if (response.statusCode() == 503 && attempt < 2) {
                    log.warn("Gemini 503 temporary demand spike, retrying transcription in 400ms...");
                    Thread.sleep(400);
                    continue;
                } else {
                    log.warn("Gemini audio transcription API returned status {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Failed to transcribe audio via Gemini on attempt {}: {}", attempt, e.getMessage());
            }
        }
        return "";
    }

    private boolean isNonAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        String lower = answer.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("no response provided.") || lower.equals("no response provided")) {
            return true;
        }
        // Explicit admissions of not knowing, passing, or skipping
        if (lower.contains("don't know") || lower.contains("dont know") || lower.contains("do not know")
                || lower.contains("dont answer") || lower.contains("don't answer") || lower.contains("no answer")
                || lower.contains("no idea") || lower.contains("no clue") || lower.contains("not sure")
                || lower.contains("have no idea") || lower.contains("have no clue") || lower.contains("not aware")
                || lower.contains("will study") || lower.contains("need to study")
                || lower.equals("skip") || lower.equals("pass") || lower.equals("next")) {
            return true;
        }
        // Non-technical greetings and fillers
        if (lower.length() < 70) {
            boolean hasGreeting = lower.startsWith("hello") || lower.startsWith("hi ") || lower.startsWith("hey ")
                    || lower.contains("how are you") || lower.contains("what are you doing");
            boolean hasTech = lower.contains("java") || lower.contains("spring") || lower.contains("react")
                    || lower.contains("code") || lower.contains("class") || lower.contains("method")
                    || lower.contains("database") || lower.contains("api") || lower.contains("token")
                    || lower.contains("jwt") || lower.contains("hook") || lower.contains("effect");
            if (hasGreeting && !hasTech) {
                return true;
            }
        }
        return false;
    }
}

