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
    private final int maxMessages;

    public RedisChatMemory(StringRedisTemplate redisTemplate, Duration ttl) {
        this(redisTemplate, ttl, 10);  // 默认10条
    }

    public RedisChatMemory(StringRedisTemplate redisTemplate, Duration ttl, int maxMessages) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.ttl = ttl;
        this.maxMessages = maxMessages;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // 不再依赖 ThreadLocal 的 userId，因为异步线程中可能为 null
        // conversationId (chatId) 本身是 UUID，已经全局唯一
        String key = KEY_PREFIX + conversationId;

        try {
            // 获取现有消息
            List<Map<String, Object>> existingMessages = getMessagesFromRedis(key);

            int size = existingMessages.size();
            if (size > maxMessages) {
                existingMessages = existingMessages.subList(size - maxMessages, size);
            }

            // 添加新消息（只添加有有效内容的消息）
            for (Message message : messages) {
                String content = message.getText();
                // 跳过没有文本内容的消息（如只有 toolCalls 的 AssistantMessage 或 ToolResponseMessage）
                if (content != null && !content.trim().isEmpty()) {
                    existingMessages.add(messageToMap(message));
                } else {
                    log.debug("Skipping message with empty content, type: {}", message.getMessageType());
                }
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
        // 不再依赖 ThreadLocal 的 userId，因为异步线程中可能为 null
        // conversationId (chatId) 本身是 UUID，已经全局唯一
        String key = KEY_PREFIX + conversationId;

        try {
            List<Map<String, Object>> messagesData = getMessagesFromRedis(key);
            return messagesData.stream()
                    .map(this::mapToMessage)
                    .filter(Objects::nonNull)  // 过滤掉无效消息（内容为空的消息）
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get messages for conversation: {}", conversationId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void clear(String conversationId) {
        // 不再依赖 ThreadLocal 的 userId
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
        
        // 获取消息内容，处理可能为 null 的情况
        String content = message.getText();
        map.put("content", content);
        map.put("timestamp", System.currentTimeMillis());
        
        // 标记消息是否有有效内容（用于过滤）
        map.put("hasContent", content != null && !content.trim().isEmpty());
        
        return map;
    }

    private Message mapToMessage(Map<String, Object> map) {
        String type = (String) map.get("type");
        String content = (String) map.get("content");

        // 如果内容为空，返回 null，后续会被过滤掉
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        return switch (type) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }
}