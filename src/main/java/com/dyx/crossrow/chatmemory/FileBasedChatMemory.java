package com.dyx.crossrow.chatmemory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileBasedChatMemory implements ChatMemory {

    private final Path storageDir;
    private final ObjectMapper objectMapper;
    private final Map<String, List<Message>> cache = new ConcurrentHashMap<>();

    public FileBasedChatMemory(String fileDir) {
        this.storageDir = Path.of(fileDir);
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + fileDir, e);
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> conversation = getOrCreateConversation(conversationId);
        conversation.addAll(messages);
        saveToFile(conversationId, conversation);
    }

    @Override
    public List<Message> get(String conversationId) {
        return new ArrayList<>(getOrCreateConversation(conversationId));
    }

    @Override
    public void clear(String conversationId) {
        cache.remove(conversationId);
        try {
            Files.deleteIfExists(getFilePath(conversationId));
        } catch (IOException e) {
            // ignore
        }
    }

    private List<Message> getOrCreateConversation(String conversationId) {
        return cache.computeIfAbsent(conversationId, this::loadFromFile);
    }

    private Path getFilePath(String conversationId) {
        // 对 conversationId 进行简单处理，避免非法文件名字符
        String safeId = conversationId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return storageDir.resolve(safeId + ".json");
    }

    private void saveToFile(String conversationId, List<Message> messages) {
        try {
            List<Map<String, Object>> serializable = messages.stream()
                    .map(this::messageToMap)
                    .toList();
            objectMapper.writeValue(getFilePath(conversationId).toFile(), serializable);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save messages for: " + conversationId, e);
        }
    }

    private List<Message> loadFromFile(String conversationId) {
        Path file = getFilePath(conversationId);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> data = objectMapper.readValue(
                    file.toFile(),
                    new TypeReference<>() {}
            );
            return data.stream()
                    .map(this::mapToMessage)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private Map<String, Object> messageToMap(Message message) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", message.getMessageType().name());
        map.put("content", message.getText());

        // 保存 metadata（如果有）
        if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
            map.put("metadata", message.getMetadata());
        }

        return map;
    }

    @SuppressWarnings("unchecked")
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