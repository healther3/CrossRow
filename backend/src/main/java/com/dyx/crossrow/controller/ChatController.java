package com.dyx.crossrow.controller;

import com.dyx.crossrow.model.dto.MultimodalChatRequestDTO;
import com.dyx.crossrow.service.ChatService;
import com.dyx.crossrow.service.ModelRouterService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping
public class ChatController {

    @Resource
    private ChatService chatService;

    /**
     *
     * @param message user prompt
     * @param chatId conversation id
     * @param userId user id
     * @return LLM text
     */
    @GetMapping("/crossrow/chat/simple/sync")
    public String simpleChatSync(@RequestParam("message") String message,
                             @RequestParam("chatId") String chatId,
                             @RequestParam ("userId") String userId) {
        return chatService.doChat(message, chatId, userId);
    }

    /**
     * Streaming chat with SSE events.
     * Returns two event types:
     * - event: message (chat content chunks)
     * - event: session_title (auto-generated title for new sessions)
     *
     * @param message user prompt
     * @param chatId conversation id
     * @param userId user id
     * @return SSE stream with message and session_title events
     */
    @GetMapping(value = "/crossrow/chat/simple/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> simpleChatAsync(@RequestParam("message") String message,
                                                         @RequestParam("chatId") String chatId,
                                                         @RequestParam ("userId") String userId) {
        return chatService.doChatStream(message, chatId, userId);
    }

    /**
     *
     * @param message user prompt
     * @param chatId conversation id
     * @param userId userid
     * @param enableReview whether to enable review agent (default: false)
     * @param maxReviewRetries max review retry attempts (default: 2)
     * @return agent text in sse form
     */
    @GetMapping(value = "/crossrow/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatWithCrossRowAgent(@RequestParam("message") String message,
                                           @RequestParam("chatId") String chatId,
                                           @RequestParam("userId") String userId,
                                           @RequestParam(value = "enableReview", defaultValue = "false") boolean enableReview,
                                           @RequestParam(value = "maxReviewRetries", defaultValue = "2") int maxReviewRetries) {
        return chatService.doChatWithCrossRowAgentStream(message, chatId, userId, enableReview, maxReviewRetries);
    }

    /**
     * Multi-agent endpoint: routes to appropriate expert (philosophy/psychology/sociology)
     * Supports multimodal input.
     * @param request contains message, media, chatId, and userId
     * @param enableReview whether to enable review agent (default: false)
     * @param maxReviewRetries max review retry attempts (default: 2)
     * @return expert agent response in sse form
     */
    @PostMapping(value = "/crossrow/expert/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatWithExpert(@RequestBody MultimodalChatRequestDTO request,
                                     @RequestParam(value = "enableReview", defaultValue = "false") boolean enableReview,
                                     @RequestParam(value = "maxReviewRetries", defaultValue = "2") int maxReviewRetries) {
        return chatService.doChatWithExpertStream(
                request.getMessage(),
                request.getMedia(),
                request.getChatId(),
                request.getUserId(),
                enableReview,
                maxReviewRetries
        );
    }

    /**
     * Preview which expert would handle the query (for testing)
     */
    @GetMapping("/crossrow/expert/preview")
    public String previewExpert(@RequestParam("message") String message) {
        return chatService.previewExpert(message);
    }

    // ==================== Model Router APIs ====================

    /**
     * 智能路由流式聊天：AI 自动评审任务复杂度并选择模型，支持多模态输入
     * - 简单任务 → Qwen（成本低）
     * - 复杂任务 → Gemini（能力强）
     * 
     * Returns two event types:
     * - event: message (chat content chunks)
     * - event: session_title (auto-generated title for new sessions)
     */
    @PostMapping(value = "/crossrow/chat/auto-route/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithAutoRoute(@RequestBody MultimodalChatRequestDTO request) {
        return chatService.doChatStreamWithAutoRoute(
                request.getMessage(),
                request.getMedia(),
                request.getChatId(),
                request.getUserId()
        );
    }

    /**
     * 使用指定模型流式聊天
     * @param modelName 模型名称: gemini / qwen
     * 
     * Returns two event types:
     * - event: message (chat content chunks)
     * - event: session_title (auto-generated title for new sessions)
     */
    @GetMapping(value = "/crossrow/chat/model/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithModel(@RequestParam("message") String message,
                                                       @RequestParam("chatId") String chatId,
                                                       @RequestParam("userId") String userId,
                                                       @RequestParam("model") String modelName) {
        return chatService.doChatWithModel(message, chatId, userId, modelName);
    }

    /**
     * 预览任务评审结果（用于调试/前端展示）
     * 返回 AI 对任务复杂度的判断
     */
    @GetMapping("/crossrow/route/preview")
    public ModelRouterService.TaskReview previewRoute(@RequestParam("message") String message) {
        return chatService.reviewTask(message);
    }

    /**
     * 获取完整的路由决策信息
     * 包含评审结果和最终选择的模型
     */
    @GetMapping("/crossrow/route/decision")
    public ModelRouterService.RouteDecision getRouteDecision(@RequestParam("message") String message) {
        return chatService.previewRoute(message);
    }

    // ==================== Multimodal Chat ====================

    /**
     * Multimodal streaming chat - supports images
     * Receives a request containing text and media resources (e.g., GCS URLs).
     *
     * @param request contains message, chatId, userId, and media list with GCS URLs
     * @return SSE stream with message and session_title events
     */
    @PostMapping(value = "/crossrow/chat/multimodal/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> multimodalChatStream(@RequestBody MultimodalChatRequestDTO request) {
        return chatService.doChatStreamWithMedia(
                request.getMessage(),
                request.getMedia(),
                request.getChatId(),
                request.getUserId()
        );
    }

    /**
     * Multimodal Agent chat - supports images with tool calling
     * Receives a request containing text and media resources (e.g., GCS URLs) for agent processing.
     *
     * @param request contains message, chatId, userId, and media list with GCS URLs
     * @return SSE stream with agent step events
     */
    @PostMapping(value = "/crossrow/agent/chat/multimodal", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter multimodalAgentChat(
            @RequestBody MultimodalChatRequestDTO request,
            @RequestParam(value = "enableReview", defaultValue = "false") boolean enableReview,
            @RequestParam(value = "maxReviewRetries", defaultValue = "2") int maxReviewRetries) {
        return chatService.doChatWithCrossRowAgentStreamWithImages(
                request.getMessage(),
                request.getMedia(),
                request.getChatId(),
                request.getUserId(),
                enableReview,
                maxReviewRetries
        );
    }

}
