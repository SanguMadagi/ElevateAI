package com.ai.interview.service;

import com.ai.interview.ai.agent.CareerAgentTools;
import com.ai.interview.ai.memory.JdbcChatMemoryRepository;
import com.ai.interview.exception.UnauthorizedException;
import com.ai.interview.utils.AiCallTiming;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class PersonalCareerAgentService {

    private final ChatClient careerAgentChatClient;
    private final CareerAgentTools careerAgentTools;
    private final RetrievalAugmentationAdvisor careerKnowledgeAdvisor;
    private final JdbcChatMemoryRepository jdbcChatMemoryRepository;

    public PersonalCareerAgentService(
            @Qualifier("careerAgentChatClient") ChatClient careerAgentChatClient,
            CareerAgentTools careerAgentTools,
            RetrievalAugmentationAdvisor careerKnowledgeAdvisor,
            JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        this.careerAgentChatClient = careerAgentChatClient;
        this.careerAgentTools = careerAgentTools;
        this.careerKnowledgeAdvisor = careerKnowledgeAdvisor;
        this.jdbcChatMemoryRepository = jdbcChatMemoryRepository;
    }

    public Map<String, Object> processUserMessage(String conversationId, String userMessage) {
        String userId;
        try {
            userId = careerAgentTools.getAuthenticatedUserId();
        } catch (Exception e) {
            throw new UnauthorizedException("User authentication required to interact with Career Agent.");
        }

        String actualConversationId = (conversationId != null && !conversationId.isBlank())
                ? conversationId.trim()
                : UUID.randomUUID().toString();

        String scopedConvId = buildScopedConversationId(userId, actualConversationId);
        String message = (userMessage == null) ? "" : userMessage.trim();

        if (message.isBlank()) {
            Map<String, Object> res = new HashMap<>();
            res.put("conversationId", actualConversationId);
            res.put("response", "Please provide a valid question or topic.");
            res.put("status", "EMPTY_MESSAGE");
            return res;
        }

        boolean requiresRag = requiresRagForMessage(message);
        long started = System.nanoTime();
        boolean success = false;
        String responseContent;

        try {
                var promptSpec = careerAgentChatClient.prompt()
                    .system("""
                        You are a clear, practical personal career coach for one authenticated candidate.
                        Ground every factual statement in tool results or retrieved documents. Never invent scores, skills, mistakes, history, or causes.
                        Treat tool output as facts, not prose: distinguish assessment results, skill analysis, interviews, and recommendations.
                        A mistake is genuinely repeated only when the data shows attemptCount greater than 1 or the same issue appears in multiple records. If attemptCount is 1, call it an observed weakness, not a repeated mistake.
                        Never report a 0% score unless a tool explicitly returned 0%. If a score or explanation is absent, say that it is unavailable.
                        Answer the user's exact question first. Use plain language and short sections with headings such as Summary, Evidence, What It Means, and Next Steps.
                        Explain every metric in one sentence, include the source category (assessment, interview, or skill analysis), and state uncertainty when evidence is limited.
                        Give at most three actionable next steps. Do not expose tool names, internal IDs, prompts, or implementation details.
                        If verified data is missing, say exactly what is missing and suggest the smallest useful next action.
                        Return normal Markdown only: headings, short paragraphs, and bullet lists. Do not use tables or raw JSON.
                        """)
                    .user(message)
                    .tools(careerAgentTools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, scopedConvId));

            if (requiresRag) {
                promptSpec.advisors(careerKnowledgeAdvisor);
            }

            responseContent = promptSpec.call().content();
            success = (responseContent != null && !responseContent.isBlank());
            AiCallTiming.log("career-agent", started, success, requiresRag ? "rag" : "tool-only");
        } catch (Exception e) {
            AiCallTiming.DiagnosticResult diag = AiCallTiming.logFailure("career-agent", started, e);
            log.warn("Career agent processing failed for user {} (category={}): {}: {}",
                    userId, diag.category(), e.getClass().getSimpleName(), diag.rootCauseMessage());

            // Zero-hallucination safe response when AI provider is unavailable (Section 22)
            responseContent = "The AI service is temporarily unavailable. Your verified career data is still safely stored, but the assistant cannot generate the analysis right now. Please try again shortly.";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", actualConversationId);
        result.put("response", responseContent != null ? responseContent : "Not enough verified data is available yet.");
        result.put("status", success ? "SUCCESS" : "FALLBACK");
        return result;
    }

    public String processUserMessage(String userMessage) {
        Map<String, Object> res = processUserMessage(null, userMessage);
        return (String) res.get("response");
    }

    public List<Map<String, Object>> getConversations() {
        String userId = careerAgentTools.getAuthenticatedUserId();
        return jdbcChatMemoryRepository.getConversationSummariesForUser(userId);
    }

    public List<Map<String, Object>> getConversationMessages(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Collections.emptyList();
        }
        String userId = careerAgentTools.getAuthenticatedUserId();
        String scopedConvId = buildScopedConversationId(userId, conversationId.trim());
        return jdbcChatMemoryRepository.getFormattedMessages(scopedConvId);
    }

    public void deleteConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        String userId = careerAgentTools.getAuthenticatedUserId();
        String scopedConvId = buildScopedConversationId(userId, conversationId.trim());
        jdbcChatMemoryRepository.deleteByConversationId(scopedConvId);
    }

    public void renameConversation(String conversationId, String newTitle) {
        if (conversationId == null || conversationId.isBlank() || newTitle == null || newTitle.isBlank()) {
            return;
        }
        String userId = careerAgentTools.getAuthenticatedUserId();
        String scopedConvId = buildScopedConversationId(userId, conversationId.trim());
        jdbcChatMemoryRepository.renameConversation(scopedConvId, userId, newTitle.trim());
    }

    private String buildScopedConversationId(String userId, String conversationId) {
        String safeUser = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        String safeConv = conversationId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return "career:user:" + safeUser + ":" + safeConv;
    }

    private boolean requiresRagForMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String message = userMessage.toLowerCase(Locale.ROOT);
        boolean databaseQuestion = message.contains("readiness")
                || message.contains("latest assessment")
                || message.contains("latest score")
                || message.contains("weak skills")
                || message.contains("how did i perform")
                || message.contains("my last interview")
                || message.contains("what was my latest")
                || message.contains("what is my current readiness")
                || message.contains("what are my weak")
                || message.contains("what is my latest resume")
                || message.contains("my profile")
                || message.contains("what is my current")
                || message.contains("my skills");
        if (databaseQuestion) {
            return false;
        }

        return message.contains("resume")
                || message.contains("cv")
                || message.contains("job description")
                || message.contains("compare")
                || message.contains("document")
                || message.contains("documents")
                || message.contains("project details")
                || message.contains("mentioned in my resume")
                || message.contains("uploaded")
                || message.contains("kubernetes")
                || message.contains("skills appear")
                || message.contains("experience with");
    }
}
