package com.ai.interview;

import com.ai.interview.ai.memory.JdbcChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JdbcChatMemoryVerificationTest {

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @Test
    void testJdbcChatMemoryPersistenceAndIsolation() {
        String convId1 = "session-" + UUID.randomUUID();
        String convId2 = "session-" + UUID.randomUUID();

        // 1. Add messages to session 1
        chatMemory.add(convId1, new UserMessage("What is dependency injection?"));
        chatMemory.add(convId1, new AssistantMessage("It is a design pattern where an object receives its dependencies."));

        // 2. Add messages to session 2
        chatMemory.add(convId2, new UserMessage("What is Redis?"));

        // 3. Verify isolation in ChatMemory
        List<Message> session1Msgs = chatMemory.get(convId1);
        List<Message> session2Msgs = chatMemory.get(convId2);

        assertEquals(2, session1Msgs.size(), "Session 1 must have 2 messages");
        assertEquals(1, session2Msgs.size(), "Session 2 must have 1 message");
        assertTrue(session1Msgs.get(0).getText().contains("dependency injection"));
        assertTrue(session2Msgs.get(0).getText().contains("Redis"));

        // 4. Verify directly in MariaDB repository
        List<Message> directFromDb1 = jdbcChatMemoryRepository.findByConversationId(convId1);
        assertEquals(2, directFromDb1.size(), "MariaDB must contain 2 messages for session 1");

        // Clean up
        chatMemory.clear(convId1);
        chatMemory.clear(convId2);
        assertEquals(0, chatMemory.get(convId1).size());
        assertEquals(0, chatMemory.get(convId2).size());
    }
}
