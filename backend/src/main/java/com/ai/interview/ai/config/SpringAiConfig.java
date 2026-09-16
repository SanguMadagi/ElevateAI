package com.ai.interview.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.RedisClient;

import java.time.Duration;
import java.util.Locale;

@Configuration
@EnableAsync
@Slf4j
public class SpringAiConfig {

    @Bean
    public JedisConnectionFactory jedisConnectionFactory(DataRedisProperties properties) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                properties.getHost() != null ? properties.getHost() : "localhost",
                properties.getPort() != 0 ? properties.getPort() : 6379);
        if (properties.getUsername() != null) {
            configuration.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null) {
            configuration.setPassword(properties.getPassword());
        }
        return new JedisConnectionFactory(configuration);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean(name = "aiTaskExecutor")
    public TaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean
    public RetryTemplate retryTemplate() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(1)
                .delay(Duration.ofMillis(300))
                .maxDelay(Duration.ofMillis(1000))
                .predicate(throwable -> {
                    if (throwable == null) return false;
                    Throwable curr = throwable;
                    StringBuilder fullChain = new StringBuilder();
                    while (curr != null) {
                        if (curr.getMessage() != null) {
                            fullChain.append(" ").append(curr.getMessage().toLowerCase(Locale.ROOT));
                        }
                        if (curr.getCause() == null || curr.getCause() == curr) break;
                        curr = curr.getCause();
                    }
                    String msg = fullChain.toString();
                    // Fast fail on terminal errors (400, 401, 403, 404, 429, authentication, invalid key, quota, not found)
                    if (msg.contains("400") || msg.contains("401") || msg.contains("403") || msg.contains("404") || msg.contains("429") ||
                        msg.contains("api_key_invalid") || msg.contains("api key not valid") || msg.contains("invalid api key") ||
                        msg.contains("unauthenticated") || msg.contains("permission_denied") || msg.contains("not_found") ||
                        msg.contains("resource_exhausted") || msg.contains("quota") || msg.contains("bad request") ||
                        msg.contains("clientexception")) {
                        return false;
                    }
                    return true;
                })
                .build();
        return new RetryTemplate(policy);
    }

    @Bean
    public ChatMemory chatMemory(com.ai.interview.ai.memory.JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public RedisClient redisClient(DataRedisProperties properties) {
        String host = properties.getHost() != null ? properties.getHost() : "localhost";
        int port = properties.getPort() != 0 ? properties.getPort() : 6379;
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            return RedisClient.create(host, port, properties.getUsername(), properties.getPassword());
        }
        return RedisClient.create(new HostAndPort(host, port));
    }

    @Bean
    @Primary
    public org.springframework.ai.vectorstore.redis.RedisVectorStore vectorStore(
            RedisClient redisClient,
            org.springframework.ai.embedding.EmbeddingModel embeddingModel) {
        org.springframework.ai.vectorstore.redis.RedisVectorStore store = org.springframework.ai.vectorstore.redis.RedisVectorStore.builder(redisClient, embeddingModel)
                .indexName("career-knowledge")
                .prefix("career-embedding:")
                .initializeSchema(true)
                .metadataFields(
                        org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField.tag("userId"),
                        org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField.tag("documentType"),
                        org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField.text("topic"),
                        org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField.text("source")
                )
                .build();
        try {
            store.afterPropertiesSet();
        } catch (Exception e) {
            log.warn("RedisVectorStore schema initialization deferred: {}", e.getMessage());
        }
        return store;
    }

    @Bean
    public ChatClient mockInterviewChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public ChatClient mockInterviewFinalChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ChatClient careerAgentChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}

