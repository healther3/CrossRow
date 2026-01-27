package com.dyx.crossrow.app;
import com.dyx.crossrow.advisor.MyLogAdvisor;
import com.dyx.crossrow.advisor.ReReadingAdvisor;
import com.dyx.crossrow.chatmemory.FileBasedChatMemory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@Slf4j
public class CrossRowApp {

    private final ChatClient chatClient;
    private final SystemPromptTemplate systemPromptTemplate;

    /**
     *  initalize the app(memory based)
     * @param dashScopeChatModel
     */
    public CrossRowApp(ChatModel dashScopeChatModel, @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource) {

        this.systemPromptTemplate = new SystemPromptTemplate(systemPromptResource);
        String fileDir = System.getProperty("user.dir")+"/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);


//        In memory chat memory
//        ChatMemory chatMemory = MessageWindowChatMemory.builder()
//                .chatMemoryRepository(new InMemoryChatMemoryRepository())
//                .maxMessages(10)
//                .build();

        // 基于内存的
        chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultSystem(systemPromptTemplate.render())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
//                              .conversationId() 设置会话id
                                .build(),
                        // customized logger advisor
                        new MyLogAdvisor(100)
                        // customized enhanced advisor
                        // new ReReadingAdvisor()
                )
                .build();
    }

    /**
     *  chat with language model that has memory
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatClientResponse chatClientResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatClientResponse();
        // get information from response
        //log.info(chatClientResponse.context().);
        // get content from response
        ChatResponse chatResponse = chatClientResponse.chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    record PainReport(String reportName, List<String> Solutions) {

    }

    /**
     * chat with LLM and generate a report
     * @param message
     * @param chatId
     * @return
     */
    public PainReport doChatReport(String message, String chatId) {
        PainReport painReport = chatClient
                .prompt()
                .system(systemPromptTemplate.render()+"***对话完后生成一个报告，标题为{user_name}的痛苦诊断，内容为解决方案列表，请以列表形式至少列举3-5    个解决方案***")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(PainReport.class);

        log.info("Report: {}", painReport);
        return painReport;
    }
}
