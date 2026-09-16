package com.ai.interview.controller;

import com.ai.interview.service.PersonalCareerAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class CareerAgentController {

    private final PersonalCareerAgentService agentService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            Authentication authentication,
            @RequestBody Map<String, String> payload) {
        String query = payload != null ? payload.get("message") : "";
        String conversationId = payload != null ? payload.get("conversationId") : null;
        Map<String, Object> response = agentService.processUserMessage(conversationId, query);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<Map<String, Object>>> getConversations(Authentication authentication) {
        return ResponseEntity.ok(agentService.getConversations());
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<List<Map<String, Object>>> getConversationMessages(
            Authentication authentication,
            @PathVariable String conversationId) {
        return ResponseEntity.ok(agentService.getConversationMessages(conversationId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, String>> deleteConversation(
            Authentication authentication,
            @PathVariable String conversationId) {
        agentService.deleteConversation(conversationId);
        return ResponseEntity.ok(Map.of("message", "Conversation deleted successfully"));
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, String>> createConversation(Authentication authentication) {
        String newId = UUID.randomUUID().toString();
        return ResponseEntity.ok(Map.of("conversationId", newId));
    }

    @PatchMapping("/conversations/{conversationId}/rename")
    public ResponseEntity<Map<String, String>> renameConversation(
            Authentication authentication,
            @PathVariable String conversationId,
            @RequestBody Map<String, String> payload) {
        String newTitle = payload != null ? payload.get("title") : "";
        if (newTitle == null || newTitle.trim().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title cannot be blank"));
        }
        agentService.renameConversation(conversationId, newTitle.trim());
        return ResponseEntity.ok(Map.of("message", "Conversation renamed successfully", "title", newTitle.trim()));
    }
}
