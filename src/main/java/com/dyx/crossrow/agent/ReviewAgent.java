package com.dyx.crossrow.agent;

import com.dyx.crossrow.model.ReviewResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReviewAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewAgent(@Qualifier("openAiChatModel") ChatModel qwenChatModel,
                       @Value("classpath:/prompts/review-agent-result-prompt.st") Resource reviewPromptResource) {
        SystemPromptTemplate promptTemplate = new SystemPromptTemplate(reviewPromptResource);
        this.chatClient = ChatClient.builder(qwenChatModel)
                .defaultSystem(promptTemplate.render())
                .build();
    }
    public ReviewResult review(String task, String finalOutput) {
        String userMessage = """
        ## User Task
        {task}
        
        ## Agent Response
        {output}
        """.formatted(task, finalOutput);

        String response = chatClient.prompt()
                .user(u -> u.text(userMessage)
                        .param("task", task)
                        .param("output", finalOutput))
                .call()
                .content();

        return parseResponse(response);
    }

    public ReviewResult parseResponse(String response) {
        try {
            // 清理可能的 markdown 代码块
            String json = response.replace("```json", "")
                    .replace("```", "")
                    .trim();
            return objectMapper.readValue(json, ReviewResult.class);
        } catch (Exception e) {
            // 解析失败，默认通过（避免阻塞）
            return ReviewResult.builder()
                    .approved(true)
                    .reason("Review parsing failed: " + e.getMessage())
                    .build();
        }
    }
}