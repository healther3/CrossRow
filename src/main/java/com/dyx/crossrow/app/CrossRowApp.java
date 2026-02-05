package com.dyx.crossrow.app;

import com.dyx.crossrow.advisor.MyLogAdvisor;
import com.dyx.crossrow.advisor.SimpleAuthAdvisor;
import com.dyx.crossrow.advisor.SimpleQuotaAdvisor;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.ai.vertexai.gemini.api.VertexAiGeminiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;



import java.util.List;


@Component
@Slf4j
public class CrossRowApp {

    private final ChatClient chatClient;
    private final SystemPromptTemplate systemPromptTemplate;

    @jakarta.annotation.Resource
    private VectorStore vectorStore;

    @jakarta.annotation.Resource(name = "ragAdvisor")
    private Advisor ragAdvisor;

    @jakarta.annotation.Resource(name = "hybridRagAdvisor")
    private Advisor hybridRagAdvisor;

    /**
     *  initalize the app(memory based)
     * @param chatModel Gemini chat model
     */
    public CrossRowApp(ChatModel chatModel, @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource, ChatMemory chatMemory) {

        // get template from resource
            this.systemPromptTemplate = new SystemPromptTemplate(systemPromptResource);

//            基于文件保存 chat memory
//            String fileDir = System.getProperty("user.dir")+"/tmp/chat-memory";
//            ChatMemory chatMemory = new FileBasedChatMemory(fileDir);


//        In memory chat memory 基于内存的chat memory
//        ChatMemory chatMemory = MessageWindowChatMemory.builder()
//                .chatMemoryRepository(new InMemoryChatMemoryRepository())
//                .maxMessages(10)
//                .build();

        chatClient = ChatClient.builder(chatModel)
                .defaultSystem(systemPromptTemplate.render())
                .defaultAdvisors(
                        new SimpleAuthAdvisor(),
                        new SimpleQuotaAdvisor(5),
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
     * @param message user given message
     * @param chatId chat conversation ID
     * @return information in chat
     */
    public String doChat(String message, String chatId, String userId) {
        ChatClientResponse chatClientResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
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
     * @param message user given message
     * @param chatId chat conversation ID
     * @return  ai response with report
     */
    public PainReport doChatReport(String message, String chatId, String userId) {
        PainReport painReport = chatClient
                .prompt()
                .system(systemPromptTemplate.render()+"***对话完后生成一个报告，标题为{user_name}的痛苦诊断，内容为解决方案列表，请以列表形式至少列举3-5    个解决方案***")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
                .call()
                .entity(PainReport.class);

        log.info("Report: {}", painReport);
        return painReport;
    }

    /**
     * Chat with RAG (Retrieval Augmented Generation)
     * @param message user given message
     * @param chatId chat id
     * @param userId userid
     * @return ai response
     */
    public String doChatWithRag(String message, String chatId, String userId) {
        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .googleSearchRetrieval(true)
                .build();

        ChatClientResponse chatClientResponse = chatClient
                .prompt()
                .options(options)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
                .advisors(hybridRagAdvisor)
//                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
//                        .searchRequest(SearchRequest.builder()
//                                .topK(2)
//                                .similarityThreshold(0.5)
//
//                        .build())
//                        .promptTemplate(new PromptTemplate("""
//                下面是一些会帮助回答用户问题的信息
//                ---------------------
//                {question_answer_context}
//                ---------------------
//                结合这些可以帮助回答的上下文信息，给出用户问题分析和解决方案.
//                *回答时请注明信息来源，例如:根据存在主义哲学观念，....*
//                问题: {query}
//                回答:
//                """))
                .call()
                .chatClientResponse();

        String content = chatClientResponse.chatResponse().getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

}
