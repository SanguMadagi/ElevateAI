package com.ai.interview;

import com.ai.interview.ai.agent.CareerAgentTools;
import com.ai.interview.ai.memory.JdbcChatMemoryRepository;
import com.ai.interview.exception.UnauthorizedException;
import com.ai.interview.model.Profile;
import com.ai.interview.model.SkillAnalysis;
import com.ai.interview.repository.ProfileRepository;
import com.ai.interview.repository.SkillAnalysisRepository;
import com.ai.interview.service.PersonalCareerAgentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CareerAgentIsolationAndMemoryTest {

    @Autowired
    private PersonalCareerAgentService agentService;

    @Autowired
    private CareerAgentTools careerAgentTools;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private SkillAnalysisRepository skillAnalysisRepository;

    @Autowired
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    private void authenticate(String userId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId,
                userId + "@example.com",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("USERID_" + userId))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testUnauthenticatedAccessRejected() {
        SecurityContextHolder.clearContext();
        assertThrows(UnauthorizedException.class, () -> {
            agentService.processUserMessage("conversation-1", "What are my weakest skills?");
        });
    }

    @Test
    void testToolCallingAndUserDataIsolation() {
        String userA = "user-a-" + UUID.randomUUID().toString().substring(0, 8);
        String userB = "user-b-" + UUID.randomUUID().toString().substring(0, 8);

        // Seed User A data
        Profile profileA = new Profile();
        profileA.setUserId(userA);
        profileA.setName("Alice Candidate");
        profileA.setTargetRole("Distributed Systems Architect");
        profileA.setSkills(List.of("Go", "Kubernetes", "Kafka"));
        profileRepository.save(profileA);

        SkillAnalysis saA = new SkillAnalysis();
        saA.setUserId(userA);
        saA.setOverallReadinessScore(88.5);
        saA.setSkillScores(Map.of("Kubernetes", 90.0, "Kafka", 85.0));
        saA.setWeakConcepts(List.of("Raft Consensus"));
        skillAnalysisRepository.save(saA);

        // Seed User B data
        Profile profileB = new Profile();
        profileB.setUserId(userB);
        profileB.setName("Bob Candidate");
        profileB.setTargetRole("Frontend Lead");
        profileB.setSkills(List.of("React", "CSS", "TypeScript"));
        profileRepository.save(profileB);

        SkillAnalysis saB = new SkillAnalysis();
        saB.setUserId(userB);
        saB.setOverallReadinessScore(62.0);
        saB.setSkillScores(Map.of("React", 70.0, "CSS", 55.0));
        saB.setWeakConcepts(List.of("Browser Rendering Engine"));
        skillAnalysisRepository.save(saB);

        try {
            // Authenticate as User A
            authenticate(userA);
            String userAProfile = careerAgentTools.getUserProfile();
            assertTrue(userAProfile.contains("Distributed Systems Architect"), "User A must see their target role");
            assertFalse(userAProfile.contains("Frontend Lead"), "User A must NOT see User B's profile");

            String userASkills = careerAgentTools.getSkillScores();
            assertTrue(userASkills.contains("88.5%"), "User A must see their 88.5% score");
            assertFalse(userASkills.contains("62.0%"), "User A must NOT see User B's score");

            String userAWeak = careerAgentTools.getWeakConcepts();
            assertTrue(userAWeak.contains("Raft Consensus"), "User A must see their weak concept");
            assertFalse(userAWeak.contains("Browser Rendering Engine"), "User A must NOT see User B's weak concept");

            // Authenticate as User B
            authenticate(userB);
            String userBProfile = careerAgentTools.getUserProfile();
            assertTrue(userBProfile.contains("Frontend Lead"), "User B must see their target role");
            assertFalse(userBProfile.contains("Distributed Systems Architect"), "User B must NOT see User A's profile");

            String userBSkills = careerAgentTools.getSkillScores();
            assertTrue(userBSkills.contains("62.0%"), "User B must see their 62.0% score");
            assertFalse(userBSkills.contains("88.5%"), "User B must NOT see User A's score");

        } finally {
            profileRepository.deleteById(profileA.getId());
            profileRepository.deleteById(profileB.getId());
            skillAnalysisRepository.deleteById(saA.getId());
            skillAnalysisRepository.deleteById(saB.getId());
        }
    }

    @Test
    void testZeroHallucinationWhenDataMissing() {
        String freshUser = "empty-user-" + UUID.randomUUID().toString().substring(0, 8);
        authenticate(freshUser);

        assertEquals("Not enough data available yet.", careerAgentTools.getUserProfile());
        assertEquals("Not enough data available yet.", careerAgentTools.getSkillScores());
        assertEquals("Not enough data available yet.", careerAgentTools.getWeakConcepts());
        assertEquals("Not enough data available yet.", careerAgentTools.getMistakeAnalysis());
        assertEquals("Not enough data available yet.", careerAgentTools.getAssessmentResults());
        assertEquals("Not enough data available yet.", careerAgentTools.getInterviewHistory());
    }

    @Test
    void testConversationIsolationBetweenTenants() {
        String userA = "tenant-a-" + UUID.randomUUID().toString().substring(0, 8);
        String userB = "tenant-b-" + UUID.randomUUID().toString().substring(0, 8);

        String convA = "conv-alice-1";
        String convB = "conv-bob-1";

        String scopedIdA = "career:user:" + userA + ":" + convA;
        String scopedIdB = "career:user:" + userB + ":" + convB;

        // Directly populate chat memory for User A and User B
        jdbcChatMemoryRepository.saveAll(scopedIdA, List.of(
                new UserMessage("What are my weakest skills?"),
                new AssistantMessage("Based on your assessment, your weakest skill is Raft Consensus.")
        ));

        jdbcChatMemoryRepository.saveAll(scopedIdB, List.of(
                new UserMessage("What is my interview feedback?"),
                new AssistantMessage("Your interview feedback recommends reviewing CSS Grid.")
        ));

        try {
            // As User A
            authenticate(userA);
            List<Map<String, Object>> convsA = agentService.getConversations();
            assertNotNull(convsA);
            assertEquals(1, convsA.size(), "User A should have exactly 1 conversation");
            assertEquals(convA, convsA.get(0).get("id"));

            List<Map<String, Object>> msgsA = agentService.getConversationMessages(convA);
            assertEquals(2, msgsA.size());
            assertTrue(msgsA.get(1).get("content").toString().contains("Raft Consensus"));

            // User A attempts to read User B's conversation ID -> Scoped query for User A will look for career:user:userA:convB which is empty
            List<Map<String, Object>> crossRead = agentService.getConversationMessages(convB);
            assertTrue(crossRead.isEmpty(), "User A must NEVER read User B's conversation messages");

            // As User B
            authenticate(userB);
            List<Map<String, Object>> convsB = agentService.getConversations();
            assertNotNull(convsB);
            assertEquals(1, convsB.size(), "User B should have exactly 1 conversation");
            assertEquals(convB, convsB.get(0).get("id"));

            List<Map<String, Object>> msgsB = agentService.getConversationMessages(convB);
            assertEquals(2, msgsB.size());
            assertTrue(msgsB.get(1).get("content").toString().contains("CSS Grid"));

            // User B attempts to read User A's conversation ID -> Empty
            List<Map<String, Object>> crossReadB = agentService.getConversationMessages(convA);
            assertTrue(crossReadB.isEmpty(), "User B must NEVER read User A's conversation messages");

        } finally {
            jdbcChatMemoryRepository.deleteByConversationId(scopedIdA);
            jdbcChatMemoryRepository.deleteByConversationId(scopedIdB);
        }
    }

    @Test
    void testGracefulFallbackWhenAiFailsWithoutThrowing500() {
        String user = "graceful-user-" + UUID.randomUUID().toString().substring(0, 8);
        authenticate(user);

        // Process message - even if Google API is rejected / rate limited / offline, it returns a safe result map with status
        Map<String, Object> result = agentService.processUserMessage("test-fallback-session", "What are my weakest skills?");
        assertNotNull(result);
        assertNotNull(result.get("response"));
        assertNotNull(result.get("conversationId"));
        assertNotNull(result.get("status"));
        String responseText = result.get("response").toString();
        assertFalse(responseText.isBlank(), "Response text must never be blank");
        // Must never throw an uncaught 500
    }
}
