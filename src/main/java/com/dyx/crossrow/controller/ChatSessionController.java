package com.dyx.crossrow.controller;

import com.dyx.crossrow.model.ChatSession;
import com.dyx.crossrow.model.dto.ChatMessageDTO;
import com.dyx.crossrow.service.ChatSessionService;
import com.dyx.crossrow.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService sessionService;

    @GetMapping
    public List<ChatSession> listSessions() {
        String userId = UserContext.getUserId();
        return sessionService.getUserSessions(userId);
    }

    /**
     * 获取会话的聊天历史记录
     */
    @GetMapping("/{chatId}/history")
    public List<ChatMessageDTO> getChatHistory(@PathVariable String chatId) {
        String userId = UserContext.getUserId();
        return sessionService.getChatHistory(chatId, userId);
    }

    @PostMapping
    public ChatSession createSession(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String folderId) {
        String userId = UserContext.getUserId();
        return sessionService.createSession(userId, title, folderId);
    }

    @PutMapping("/{chatId}/title")
    public void updateTitle(@PathVariable String chatId, @RequestParam String title) {
        String userId = UserContext.getUserId();
        sessionService.updateSessionTitle(chatId, userId, title);
    }

    @DeleteMapping("/{chatId}")
    public void deleteSession(@PathVariable String chatId) {
        String userId = UserContext.getUserId();
        sessionService.deleteSession(chatId, userId);
    }
}