package com.dyx.crossrow.service;

import com.dyx.crossrow.exceptions.SessionAccessDeniedException;
import com.dyx.crossrow.model.ChatSession;
import com.dyx.crossrow.model.dto.ChatMessageDTO;
import com.dyx.crossrow.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMemory chatMemory;

    /**
     * 创建新会话
     */
    public ChatSession createSession(String userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : "new session");
        return sessionRepository.save(session);
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
}
