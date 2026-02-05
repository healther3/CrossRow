package com.dyx.crossrow.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 *  Spring Ai Demo - 仅用于演示
 */
@Component
@org.springframework.context.annotation.Profile("!test")  // 测试时不运行
public class SpringAiAiInvoke implements CommandLineRunner {
//    var chatClient = ChatClient.builder(chatModel)
//            .defaultAdvisors(
//                    new MessageChatMemoryAdvisor(chatMemory),
//                    new QuestionAnswerAdvisor(vectorStore)
//            )
//            .build();
    @Resource
    private ChatModel chatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage assistantMessage = chatModel.call(new Prompt("你好"))
                .getResult()
                .getOutput();
        System.out.println(assistantMessage.getText());
    }
}
