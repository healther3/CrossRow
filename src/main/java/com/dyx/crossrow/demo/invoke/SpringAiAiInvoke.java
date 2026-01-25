package com.dyx.crossrow.demo.invoke;

import jakarta.annotation.Resource;
import org.apache.commons.exec.CommandLine;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.comments.CommentLine;

/**
 *  Spring Ai
 */
@Component
public class SpringAiAiInvoke implements CommandLineRunner {
//    var chatClient = ChatClient.builder(chatModel)
//            .defaultAdvisors(
//                    new MessageChatMemoryAdvisor(chatMemory),
//                    new QuestionAnswerAdvisor(vectorStore)
//            )
//            .build();
    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage assistantMessage =  dashscopeChatModel.call(new Prompt("你好"))
                .getResult()
                .getOutput();
        System.out.println(assistantMessage.getText());
    }
}
