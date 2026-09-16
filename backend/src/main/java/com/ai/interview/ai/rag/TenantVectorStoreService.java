package com.ai.interview.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantVectorStoreService {

    private final VectorStore vectorStore;

    public void addDocument(String userId, String content, String documentType, String topic, String source) {
        if (userId == null || content == null || content.trim().isEmpty()) return;

        String safeUserId = sanitize(userId);
        Map<String, Object> metadata = Map.of(
                "userId", safeUserId,
                "documentType", documentType != null ? documentType : "GENERAL",
                "topic", topic != null ? topic : "CAREER",
                "source", source != null ? source : "SYSTEM",
                "timestamp", System.currentTimeMillis()
        );

        Document doc = new Document(content, metadata);
        try {
            vectorStore.add(List.of(doc));
            log.info("Vectorized document for user: {}, type: {}", safeUserId, documentType);
        } catch (Exception e) {
            log.warn("Vector store indexing warning for user {}: {}", safeUserId, e.getMessage());
        }
    }

    public List<Document> searchUserDocuments(String userId, String query, int topK) {
        if (userId == null || query == null || query.trim().isEmpty()) return List.of();

        String safeUserId = sanitize(userId);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(new FilterExpressionBuilder().eq("userId", safeUserId).build())
                .build();

        try {
            return vectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.warn("Vector store search failed for user {}: {}", safeUserId, e.getMessage());
            return List.of();
        }
    }

    private String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
