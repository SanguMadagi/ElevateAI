package com.ai.interview;

import com.ai.interview.ai.rag.TenantVectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RagVerificationTest {

    @Autowired
    private TenantVectorStoreService tenantVectorStoreService;

    @Test
    void testTenantVectorStoreIsolation() throws InterruptedException {
        String userA = "usera" + UUID.randomUUID().toString().replace("-", "");
        String userB = "userb" + UUID.randomUUID().toString().replace("-", "");

        String docA = "Candidate " + userA + " has deep expertise in Spring Boot microservices and Kafka event streaming.";
        String docB = "Candidate " + userB + " specializes in iOS Swift development and SwiftUI components.";

        // Ingest documents
        tenantVectorStoreService.addDocument(userA, docA, "RESUME", "Backend", "TEST");
        tenantVectorStoreService.addDocument(userB, docB, "RESUME", "Mobile", "TEST");

        // Allow Redis async index indexing
        Thread.sleep(1000);

        // Search for User A
        List<Document> resultsA = tenantVectorStoreService.searchUserDocuments(userA, "Spring Boot microservices", 5);
        assertNotNull(resultsA);
        assertFalse(resultsA.isEmpty(), "User A should find their ingested document");
        assertTrue(resultsA.stream().allMatch(d -> docA.equals(d.getText()) || d.getText().contains(userA)));

        // Search for User B
        List<Document> resultsB = tenantVectorStoreService.searchUserDocuments(userB, "iOS Swift", 5);
        assertNotNull(resultsB);
        assertFalse(resultsB.isEmpty(), "User B should find their ingested document");
        assertTrue(resultsB.stream().allMatch(d -> docB.equals(d.getText()) || d.getText().contains(userB)));

        // Cross-tenant search: User A searches for iOS Swift -> Must only contain User A's documents, NEVER User B's
        List<Document> crossA = tenantVectorStoreService.searchUserDocuments(userA, "iOS Swift development", 5);
        assertNotNull(crossA);
        assertTrue(crossA.stream().noneMatch(d -> docB.equals(d.getText()) || d.getText().contains(userB)),
                "User A must NEVER retrieve User B's documents");
        assertTrue(crossA.stream().allMatch(d -> userA.equals(d.getMetadata().get("userId"))),
                "All results for User A must belong to User A");

        // Cross-tenant search: User B searches for Spring Boot -> Must only contain User B's documents, NEVER User A's
        List<Document> crossB = tenantVectorStoreService.searchUserDocuments(userB, "Spring Boot microservices", 5);
        assertNotNull(crossB);
        assertTrue(crossB.stream().noneMatch(d -> docA.equals(d.getText()) || d.getText().contains(userA)),
                "User B must NEVER retrieve User A's documents");
        assertTrue(crossB.stream().allMatch(d -> userB.equals(d.getMetadata().get("userId"))),
                "All results for User B must belong to User B");
    }
}
