package com.dyx.crossrow.chatmemory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);

    private static final String KEY_PREFIX = "chat:memory:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;  // 过期时间

    public RedisChatMemory(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Duration.ofDays(7));  // 默认7天过期
    }

    public RedisChatMemory(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.ttl = ttl;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;

        try {
            // 获取现有消息
            List<Map<String, Object>> existingMessages = getMessagesFromRedis(key);

            // 添加新消息
            for (Message message : messages) {
                existingMessages.add(messageToMap(message));
            }

            // 保存回 Redis
            String json = objectMapper.writeValueAsString(existingMessages);
            redisTemplate.opsForValue().set(key, json, ttl);

            log.debug("Saved {} messages for conversation: {}", messages.size(), conversationId);

        } catch (JsonProcessingException e) {
            log.error("Failed to save messages for conversation: {}", conversationId, e);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        String key = KEY_PREFIX + conversationId;

        try {
            List<Map<String, Object>> messagesData = getMessagesFromRedis(key);
            return messagesData.stream()
                    .map(this::mapToMessage)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get messages for conversation: {}", conversationId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void clear(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.delete(key);
        log.debug("Cleared conversation: {}", conversationId);
    }

    private List<Map<String, Object>> getMessagesFromRedis(String key) {
        String json = redisTemplate.opsForValue().get(key);

        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse messages from Redis", e);
            return new ArrayList<>();
        }
    }

    private Map<String, Object> messageToMap(Message message) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", message.getMessageType().name());
        map.put("content", message.getText());
        map.put("timestamp", System.currentTimeMillis());
        return map;
    }

    private Message mapToMessage(Map<String, Object> map) {
        String type = (String) map.get("type");
        String content = (String) map.get("content");

        if (content == null) {
            content = "";
        }

        return switch (type) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }
}