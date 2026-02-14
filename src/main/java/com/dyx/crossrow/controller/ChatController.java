package com.dyx.crossrow.controller;

import com.dyx.crossrow.service.ChatService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping
public class ChatController {
    @Resource
    ChatService chatService;

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
     *
     * @param message user prompt
     * @param chatId conversation id
     * @param userId user id
     * @return LLM text in streaming form
     */
    @GetMapping(value = "/crossrow/chat/simple/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> simpleChatAsync(@RequestParam("message") String message,
                                        @RequestParam("chatId") String chatId,
                                        @RequestParam ("userId") String userId) {
        return chatService.doChatStream(message, chatId, userId);
    }




}
