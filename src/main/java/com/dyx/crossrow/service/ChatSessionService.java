package com.dyx.crossrow.service;

import com.dyx.crossrow.exceptions.SessionAccessDeniedException;
import com.dyx.crossrow.model.ChatSession;
import com.dyx.crossrow.model.dto.ChatMessageDTO;
import com.dyx.crossrow.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final String DEFAULT_SESSION_TITLE = "new session";
    
    private final ChatSessionRepository sessionRepository;
    private final ChatMemory chatMemory;
    private final ChatModelProvider chatModelProvider;

    /**
     * 创建新会话
     */
    public ChatSession createSession(String userId, String title, String folderId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : "new session");
        session.setFolderId(folderId);
        return sessionRepository.save(session);
    }

    public ChatSession createSession(String userId, String title) {
        return createSession(userId, title, null);
    }

    /**
     * 获取会话（确保会话属于当前用户）
     */
    public ChatSession getSession(String chatId, String userId) {
        return sessionRepository.findById(chatId)
                .map(session -> {
                    if (!session.getUserId().equals(userId)) {
                        throw new SessionAccessDeniedException("无权访问此会话", userId);
                    }
                    return session;
                })
                .orElseThrow(() -> new SessionAccessDeniedException("会话不存在", userId));
    }

    /**
     * 校验会话归属权
     */
    public void validateSessionOwnership(String chatId, String userId) {
        if (!sessionRepository.existsByIdAndUserId(chatId, userId)) {
            // 检查会话是否存在
            if (sessionRepository.existsById(chatId)) {
                throw new SessionAccessDeniedException("无权访问此会话",userId);
            }
            // 会话不存在，允许创建
        }
    }

    /**
     * 获取用户的所有会话
     */
    public List<ChatSession> getUserSessions(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /**
     * 更新会话标题
     */
    public void updateSessionTitle(String chatId, String userId, String title) {
        ChatSession session = sessionRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new SessionAccessDeniedException("无权访问此会话",userId));
        session.setTitle(title);
        sessionRepository.save(session);
    }

    /**
     * 删除会话
     */
    public void deleteSession(String chatId, String userId) {
        ChatSession session = sessionRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new SessionAccessDeniedException("无权访问此会话", userId));
        sessionRepository.delete(session);
        chatMemory.clear(chatId);
    }

    /**
     * 获取会话的聊天历史记录
     */
    public List<ChatMessageDTO> getChatHistory(String chatId, String userId) {
        validateSessionOwnership(chatId, userId);
        List<Message> messages = chatMemory.get(chatId);
        return messages.stream()
                .map(msg -> new ChatMessageDTO(
                        msg.getMessageType().name().toLowerCase(),
                        msg.getText()
                ))
                .toList();
    }

    /**
     * 异步生成会话标题（如果是第一条消息）
     * 使用轻量级模型 Qwen Turbo 生成简短标题
     */
    @Async
    public void autoGenerateTitleIfNeeded(String chatId, String userId, String userMessage) {
        autoGenerateTitleIfNeeded(chatId, userId, userMessage, null);
    }

    /**
     * 异步生成会话标题（如果是第一条消息），并通过 SSE 通知前端
     * 使用轻量级模型 Qwen Turbo 生成简短标题
     * 
     * @param chatId 会话ID
     * @param userId 用户ID
     * @param userMessage 用户消息
     * @param emitter SSE emitter，用于通知前端标题更新（可为 null）
     */
    @Async
    public void autoGenerateTitleIfNeeded(String chatId, String userId, String userMessage, SseEmitter emitter) {
        try {
            ChatSession session = sessionRepository.findByIdAndUserId(chatId, userId).orElse(null);
            if (session == null) {
                log.debug("Session {} not found, skipping title generation", chatId);
                return;
            }
            
            if (!DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
                log.debug("Session {} already has custom title: {}", chatId, session.getTitle());
                return;
            }

            String title = generateTitleFromMessage(userMessage);
            session.setTitle(title);
            sessionRepository.save(session);
            log.info("Auto-generated title for session {}: {}", chatId, title);
            
            // 通过 SSE 通知前端标题更新
            if (emitter != null) {
                sendTitleUpdateEvent(emitter, title);
            }
        } catch (Exception e) {
            log.warn("Failed to auto-generate title for session {}: {}", chatId, e.getMessage());
        }
    }

    /**
     * 发送标题更新的 SSE 事件
     */
    private void sendTitleUpdateEvent(SseEmitter emitter, String title) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> data = Map.of("title", title);
            emitter.send(SseEmitter.event()
                    .name("session_title")
                    .data(objectMapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
            log.debug("Sent session_title event: {}", title);
        } catch (Exception e) {
            log.warn("Failed to send session_title event: {}", e.getMessage());
        }
    }

    /**
     * 使用 Qwen Turbo 生成简短的会话标题
     */
    private String generateTitleFromMessage(String userMessage) {
        String truncatedMessage = userMessage.length() > 200 
                ? userMessage.substring(0, 200) + "..." 
                : userMessage;
        
        ChatClient chatClient = ChatClient.builder(chatModelProvider.getQwenModel()).build();
        
        String title = chatClient.prompt()
                .system("""
                        your only task is to generate a short session title based on user text
                        Requirements:
                        - Length between 5–15 characters
                        - Summarize the core theme of the message
                        - Return only the title text with no explanations or punctuation
                        """)
                .user(truncatedMessage)
                .call()
                .content();
        
        if (title == null || title.isBlank()) {
            return truncatedMessage.substring(0, Math.min(20, truncatedMessage.length()));
        }
        
        return title.length() > 50 ? title.substring(0, 50) : title.trim();
    }

    /**
     * 异步生成会话标题并返回 Mono（用于 Flux 流式响应）
     * 如果不需要生成标题，返回 empty Mono
     * 
     * @param chatId 会话ID
     * @param userId 用户ID
     * @param userMessage 用户消息
     * @return Mono<String> 生成的标题，如果不需要生成则为 empty
     */
    public Mono<String> generateTitleIfNeededAsync(String chatId, String userId, String userMessage) {
        return Mono.fromCallable(() -> {
            ChatSession session = sessionRepository.findByIdAndUserId(chatId, userId).orElse(null);
            if (session == null) {
                log.debug("Session {} not found, skipping title generation", chatId);
                return Optional.<String>empty();
            }
            
            if (!DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
                log.debug("Session {} already has custom title: {}", chatId, session.getTitle());
                return Optional.<String>empty();
            }

            String title = generateTitleFromMessage(userMessage);
            session.setTitle(title);
            sessionRepository.save(session);
            log.info("Auto-generated title for session {}: {}", chatId, title);
            return Optional.of(title);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(opt -> opt.map(Mono::just).orElse(Mono.empty()));
    }
}
