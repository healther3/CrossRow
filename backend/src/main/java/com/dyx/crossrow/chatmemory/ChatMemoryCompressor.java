package com.dyx.crossrow.chatmemory;

import com.dyx.crossrow.factory.ChatModelProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compresses chat memory when conversations grow too large.
 * Uses token estimation to decide compression level, and a lightweight model (Qwen) for summarization.
 */
@Slf4j
@Service
public class ChatMemoryCompressor {

    private final ChatMemory chatMemory;
    private final ChatClient summarizationClient;

    private static final int LIGHT_COMPRESS_TOKEN_THRESHOLD = 3000;
    private static final int DEEP_COMPRESS_TOKEN_THRESHOLD = 6000;
    private static final int LIGHT_KEEP_RECENT = 8;
    private static final int DEEP_KEEP_RECENT = 4;

    private static final double CN_CHAR_TOKEN_RATIO = 1.5;
    private static final double EN_WORD_TOKEN_RATIO = 1.3;

    public ChatMemoryCompressor(ChatMemory chatMemory, ChatModelProvider chatModelProvider) {
        this.chatMemory = chatMemory;
        this.summarizationClient = ChatClient.builder(chatModelProvider.getQwenModel()).build();
    }

    /**
     * Check stored memory size and compress if needed. Runs asynchronously to avoid blocking the response.
     */
    @Async
    public void compressIfNeeded(String conversationId) {
        try {
            List<Message> messages = chatMemory.get(conversationId);
            if (messages == null || messages.size() <= 2) {
                return;
            }

            int estimatedTokens = estimateTokens(messages);
            log.debug("Conversation {} memory: {} messages, ~{} tokens", conversationId, messages.size(), estimatedTokens);

            if (estimatedTokens >= DEEP_COMPRESS_TOKEN_THRESHOLD) {
                compress(conversationId, messages, DEEP_KEEP_RECENT, CompressionLevel.DEEP);
            } else if (estimatedTokens >= LIGHT_COMPRESS_TOKEN_THRESHOLD) {
                compress(conversationId, messages, LIGHT_KEEP_RECENT, CompressionLevel.LIGHT);
            }
        } catch (Exception e) {
            log.error("Failed to compress memory for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    private void compress(String conversationId, List<Message> messages, int keepRecent, CompressionLevel level) {
        if (messages.size() <= keepRecent) {
            return;
        }

        log.info("Compressing conversation {} ({} compression): {} messages, keeping last {}",
                conversationId, level, messages.size(), keepRecent);

        int splitIndex = messages.size() - keepRecent;
        List<Message> oldMessages = messages.subList(0, splitIndex);
        List<Message> recentMessages = messages.subList(splitIndex, messages.size());

        String existingSummary = extractExistingSummary(oldMessages);
        String chatHistory = formatMessagesForSummary(oldMessages);
        String summary = generateSummary(existingSummary, chatHistory, level);

        List<Message> compressed = new ArrayList<>();
        compressed.add(new SystemMessage("Previous conversation summary: " + summary));
        compressed.addAll(recentMessages);

        chatMemory.clear(conversationId);
        chatMemory.add(conversationId, compressed);

        int newTokens = estimateTokens(compressed);
        log.info("Compression done for {}: {} messages (~{} tokens) -> {} messages (~{} tokens)",
                conversationId, messages.size(), estimateTokens(messages), compressed.size(), newTokens);
    }

    private String generateSummary(String existingSummary, String chatHistory, CompressionLevel level) {
        String prompt = switch (level) {
            case LIGHT -> buildLightSummaryPrompt(existingSummary, chatHistory);
            case DEEP -> buildDeepSummaryPrompt(existingSummary, chatHistory);
        };

        return summarizationClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private String buildLightSummaryPrompt(String existingSummary, String chatHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("请提取以下对话的关键信息，分为：\n");
        sb.append("1. 用户的核心问题和偏好\n");
        sb.append("2. AI给出的重要结论和分析\n");
        sb.append("3. 需要在后续对话中记住的关键事实\n");
        sb.append("保留具体细节，不要过度概括。用中文回答。\n\n");
        if (existingSummary != null) {
            sb.append("已有的历史总结（请合并更新，不要重复）：\n").append(existingSummary).append("\n\n");
        }
        sb.append("需要总结的对话：\n").append(chatHistory);
        return sb.toString();
    }

    private String buildDeepSummaryPrompt(String existingSummary, String chatHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("用3-5句话高度浓缩以下对话的核心上下文，确保后续对话能无缝衔接。\n");
        sb.append("重点保留：结论性观点、用户立场、关键事实数据。用中文回答。\n\n");
        if (existingSummary != null) {
            sb.append("已有的历史总结（请合并浓缩）：\n").append(existingSummary).append("\n\n");
        }
        sb.append("需要总结的对话：\n").append(chatHistory);
        return sb.toString();
    }

    /**
     * Extract existing summary from a previous compression (stored as SystemMessage at the start).
     */
    private String extractExistingSummary(List<Message> messages) {
        if (messages.isEmpty()) {
            return null;
        }
        Message first = messages.getFirst();
        if (first instanceof SystemMessage && first.getText().startsWith("Previous conversation summary:")) {
            return first.getText().substring("Previous conversation summary: ".length());
        }
        return null;
    }

    private String formatMessagesForSummary(List<Message> messages) {
        return messages.stream()
                .filter(m -> !(m instanceof SystemMessage && m.getText().startsWith("Previous conversation summary:")))
                .map(m -> m.getMessageType().name() + ": " + m.getText())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Rough token estimation: Chinese chars * 1.5 + English words * 1.3.
     * Precise enough for threshold decisions; not meant to replace model tokenizers.
     */
    static int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message message : messages) {
            String text = message.getText();
            if (text != null) {
                total += estimateTokens(text);
            }
        }
        return total;
    }

    static int estimateTokens(String text) {
        int cnChars = 0;
        int enChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                cnChars++;
            } else if (Character.isLetterOrDigit(c)) {
                enChars++;
            }
        }
        int enWords = enChars / 5;
        return (int) (cnChars * CN_CHAR_TOKEN_RATIO + enWords * EN_WORD_TOKEN_RATIO);
    }

    private enum CompressionLevel { LIGHT, DEEP }
}
