package com.ai.interview.ai.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Official JDBC-backed ChatMemoryRepository implementation persisting conversation history into MariaDB.
 * Ensures isolation across user and session conversation IDs.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initSchema() {
        try {
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    conversation_id VARCHAR(255) NOT NULL,
                    message_type VARCHAR(50) NOT NULL,
                    content LONGTEXT NOT NULL,
                    metadata_json LONGTEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_conversation_id (conversation_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;
            jdbcTemplate.execute(createTableSql);

            String createMetaTableSql = """
                CREATE TABLE IF NOT EXISTS career_conversation_meta (
                    conversation_id VARCHAR(255) PRIMARY KEY,
                    user_id VARCHAR(255) NOT NULL,
                    custom_title VARCHAR(255) NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_user_id (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;
            jdbcTemplate.execute(createMetaTableSql);

            log.info("Initialized MariaDB tables spring_ai_chat_memory and career_conversation_meta for Spring AI ChatMemory");
        } catch (Exception e) {
            log.warn("Could not auto-create tables in MariaDB: {}", e.getMessage());
        }
    }

    @Override
    public List<String> findConversationIds() {
        try {
            return jdbcTemplate.queryForList(
                "SELECT DISTINCT conversation_id FROM spring_ai_chat_memory ORDER BY conversation_id",
                String.class
            );
        } catch (Exception e) {
            log.error("Failed to query conversation IDs from MariaDB: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getConversationSummariesForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        String prefix = "career:user:" + userId + ":";
        String sql = """
            SELECT conversation_id, MIN(created_at) as created_at, MAX(created_at) as updated_at
            FROM spring_ai_chat_memory
            WHERE conversation_id LIKE ?
            GROUP BY conversation_id
            ORDER BY updated_at DESC
        """;
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String fullConvId = rs.getString("conversation_id");
                String rawId = fullConvId.startsWith(prefix) ? fullConvId.substring(prefix.length()) : fullConvId;
                String createdAt = rs.getString("created_at");
                String updatedAt = rs.getString("updated_at");

                String customTitle = null;
                try {
                    List<String> metaTitles = jdbcTemplate.queryForList(
                        "SELECT custom_title FROM career_conversation_meta WHERE conversation_id = ? LIMIT 1",
                        String.class,
                        fullConvId
                    );
                    if (!metaTitles.isEmpty() && metaTitles.get(0) != null && !metaTitles.get(0).isBlank()) {
                        customTitle = metaTitles.get(0);
                    }
                } catch (Exception ignored) {}

                String firstUserMsg = null;
                if (customTitle == null || customTitle.isBlank()) {
                    try {
                        List<String> titles = jdbcTemplate.queryForList(
                            "SELECT content FROM spring_ai_chat_memory WHERE conversation_id = ? AND message_type = 'USER' ORDER BY id ASC LIMIT 1",
                            String.class,
                            fullConvId
                        );
                        if (!titles.isEmpty() && titles.get(0) != null && !titles.get(0).isBlank()) {
                            firstUserMsg = titles.get(0);
                        }
                    } catch (Exception ignored) {}
                }

                String title = (customTitle != null && !customTitle.isBlank())
                    ? customTitle
                    : (firstUserMsg != null ? (firstUserMsg.length() > 50 ? firstUserMsg.substring(0, 47) + "..." : firstUserMsg) : "New Conversation");

                Map<String, Object> summary = new HashMap<>();
                summary.put("id", rawId);
                summary.put("title", title);
                summary.put("createdAt", createdAt);
                summary.put("updatedAt", updatedAt);
                return summary;
            }, prefix + "%");
        } catch (Exception e) {
            log.error("Failed to query conversation summaries for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getFormattedMessages(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Collections.emptyList();
        }
        String sql = "SELECT message_type, content, created_at FROM spring_ai_chat_memory WHERE conversation_id = ? ORDER BY id ASC";
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String type = rs.getString("message_type");
                String content = rs.getString("content");
                String createdAt = rs.getString("created_at");
                String role = "user";
                if ("ASSISTANT".equalsIgnoreCase(type)) {
                    role = "assistant";
                } else if ("SYSTEM".equalsIgnoreCase(type)) {
                    role = "system";
                }
                Map<String, Object> msg = new HashMap<>();
                msg.put("role", role);
                msg.put("content", content);
                msg.put("createdAt", createdAt);
                return msg;
            }, conversationId);
        } catch (Exception e) {
            log.error("Failed to query formatted messages for {}: {}", conversationId, e.getMessage());
            return Collections.emptyList();
        }
    }


    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String sql = "SELECT message_type, content, metadata_json FROM spring_ai_chat_memory WHERE conversation_id = ? ORDER BY id ASC";
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToMessage(rs), conversationId);
        } catch (Exception e) {
            log.error("Failed to query conversation {} from MariaDB: {}", conversationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.isBlank() || messages == null || messages.isEmpty()) {
            return;
        }
        try {
            // Delete existing records for this conversation to prevent duplicate append on window sync
            deleteByConversationId(conversationId);

            String insertSql = "INSERT INTO spring_ai_chat_memory (conversation_id, message_type, content, metadata_json) VALUES (?, ?, ?, ?)";
            for (Message msg : messages) {
                if (msg == null) continue;
                String type = msg.getMessageType() != null ? msg.getMessageType().name() : "USER";
                String content = msg.getText() != null ? msg.getText() : "";
                String metadataJson = null;
                try {
                    if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
                        metadataJson = objectMapper.writeValueAsString(msg.getMetadata());
                    }
                } catch (Exception ignored) {}

                jdbcTemplate.update(insertSql, conversationId, type, content, metadataJson);
            }
            log.debug("Saved {} messages for conversation {} into MariaDB", messages.size(), conversationId);
        } catch (Exception e) {
            log.error("Failed to save messages for conversation {} into MariaDB: {}", conversationId, e.getMessage());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM spring_ai_chat_memory WHERE conversation_id = ?", conversationId);
            jdbcTemplate.update("DELETE FROM career_conversation_meta WHERE conversation_id = ?", conversationId);
        } catch (Exception e) {
            log.error("Failed to delete conversation {} from MariaDB: {}", conversationId, e.getMessage());
        }
    }

    public void renameConversation(String scopedConvId, String userId, String newTitle) {
        if (scopedConvId == null || scopedConvId.isBlank() || newTitle == null || newTitle.isBlank()) {
            return;
        }
        try {
            String sql = """
                INSERT INTO career_conversation_meta (conversation_id, user_id, custom_title)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE custom_title = VALUES(custom_title)
            """;
            jdbcTemplate.update(sql, scopedConvId, userId, newTitle.trim());
            log.info("Renamed conversation {} to '{}' for user {}", scopedConvId, newTitle.trim(), userId);
        } catch (Exception e) {
            log.warn("Failed to store custom title for conversation {}: {}", scopedConvId, e.getMessage());
        }
    }

    private Message mapRowToMessage(ResultSet rs) throws SQLException {
        String typeStr = rs.getString("message_type");
        String content = rs.getString("content");
        String metadataJson = rs.getString("metadata_json");

        Map<String, Object> metadata = new HashMap<>();
        if (metadataJson != null && !metadataJson.isBlank()) {
            try {
                metadata = objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }

        MessageType type;
        try {
            type = MessageType.valueOf(typeStr);
        } catch (Exception e) {
            type = MessageType.USER;
        }

        if (type == MessageType.ASSISTANT) {
            return new AssistantMessage(content);
        } else if (type == MessageType.SYSTEM) {
            return new SystemMessage(content);
        } else {
            return new UserMessage(content);
        }
    }
}
