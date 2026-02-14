package com.dyx.crossrow.service;

import com.dyx.crossrow.advisor.MyLogAdvisor;
import com.dyx.crossrow.advisor.SimpleAuthAdvisor;
import com.dyx.crossrow.advisor.SimpleQuotaAdvisor;
import com.dyx.crossrow.agent.CrossRowAgent;
import com.dyx.crossrow.factory.AgentFactory;
import com.dyx.crossrow.tool.ImageGenerationTool;
import com.dyx.crossrow.tool.WebSearchTool;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;


import java.util.ArrayList;
import java.util.List;


@Component
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final ChatClient defaultChatClient;
    private final SystemPromptTemplate systemPromptTemplate;
    private final AgentFactory agentFactory;

    @jakarta.annotation.Resource
    private VectorStore vectorStore;

    @jakarta.annotation.Resource(name = "ragAdvisor")
    private Advisor ragAdvisor;

    @jakarta.annotation.Resource(name = "hybridRagAdvisor")
    private Advisor hybridRagAdvisor;

    @jakarta.annotation.Resource(name = "imageGenerationTool")
    private ImageGenerationTool imageGenerationTool;

    @jakarta.annotation.Resource(name = "webSearchTool")
    private WebSearchTool webSearchTool;

    @jakarta.annotation.Resource
    private ToolCallbackProvider toolCallbackProvider;

    @jakarta.annotation.Resource
    private ChatMemory chatMemory;
    /**
     * initalize the app(memory based)
     *
     * @param chatModel Gemini chat model
     */
    public ChatService(ChatModel chatModel, @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource,
                       AgentFactory agentFactory, ChatMemory chatMemory) {

        // get template from resource
        this.systemPromptTemplate = new SystemPromptTemplate(systemPromptResource);
        // get factory
        this.agentFactory = agentFactory;
        this.chatMemory = chatMemory;

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

        defaultChatClient = ChatClient.builder(chatModel)
                .build();
    }


    /**
     * chat with language model that has memory
     *
     * @param message user given message
     * @param chatId  chat conversation ID
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
     *
     * @param message user given message
     * @param chatId  chat conversation ID
     * @return ai response with report
     */
    public PainReport doChatReport(String message, String chatId, String userId) {
        PainReport painReport = chatClient
                .prompt()
                .system(systemPromptTemplate.render() + "***对话完后生成一个报告，标题为{user_name}的痛苦诊断，内容为解决方案列表，请以列表形式至少列举3-5    个解决方案***")
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
     *
     * @param message user given message
     * @param chatId  chat id
     * @param userId  userid
     * @return ai response
     */
    public String doChatWithRag(String message, String chatId, String userId) {
        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .googleSearchRetrieval(false)
                .build();

        ChatClientResponse chatClientResponse = chatClient
                .prompt()
                .options(options)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
                .advisors(hybridRagAdvisor)
                .tools(imageGenerationTool)
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

    public String doChatWithTools(String message, String chatId, String userId, boolean allowImage, boolean allowSearch) {

        System.out.println("[链式调用] 开始处理: " + message);

        System.out.println("正在调用搜索工具...");
        ChatClientResponse chatClientTextResponse = defaultChatClient
                .prompt()
                .system("""
                         请根据用户问题，搜索相关信息并给出回答
                        """)
                .user(message)
                .tools(webSearchTool)
                .call()
                .chatClientResponse();

        String summary = chatClientTextResponse
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();

        System.out.println(" (Summary): " + summary);

        if (summary == null || summary.isEmpty()) {
            summary = "（搜索未返回有效总结，尝试直接基于原问题生成）" + message;
        }

        ChatClientResponse chatClientImageResponse = defaultChatClient
                .prompt()
                .system("""
                         你是一个画师。根据用户提供的描述生成图片，可以为卡通或者写实
                         请直接生成。
                        """)
                .user(summary)
                .tools(imageGenerationTool)
                .call()
                .chatClientResponse();

        String imageContent = chatClientImageResponse
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();
        System.out.println(" 结果 (ImageContent): " + imageContent);
        String content = summary + imageContent;
        log.info("content: {}", content);
        return content;

    }

    public String doChatWithMCP(String message, String chatId, String userId) {

        ChatClientResponse chatClientTextResponse = defaultChatClient
                .prompt()
                .system("""
                         请根据用户问题，搜索相关信息并给出回答
                        """)
                .user(message)
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatClientResponse();

        String content = chatClientTextResponse
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();

        log.info("content: {}", content);
        return content;

    }

    public  String doChatWithCrossRowAgent(String message, String chatId, String userId) {
        log.info("开始 Agent 对话流 - User: {}, Session: {}", userId, chatId);

        //  通过工厂创建一个干净的、绑定了当前用户的 Agent
        CrossRowAgent agent = agentFactory.createAgent(userId, chatId);

        //  从 Redis 中提取历史记忆
        List<Message> history = chatMemory.get(chatId);
        if (history != null && !history.isEmpty()) {
            // 将历史记忆“装载”进 Agent 的大脑 (messageList)
            agent.setMessageList(new ArrayList<>(history));
            log.info("成功加载历史记忆，共 {} 条", history.size());
        }

        // 让 Agent 开始推理想象并执行工具 (它会自动将新问题 add 进 messageList)
        String response = agent.run(message);

        // 提取 Agent 思考完毕后的完整大脑状态
        List<Message> updatedMemory = agent.getMessageList();

        // 保存回 Redis (注意：你的 RedisChatMemory.add 是追加逻辑，如果传入全量 list 会导致重复。
        // 所以我们先 clear 掉旧的，再存入新的全量历史，或者你可以在 RedisChatMemory 里优化一下合并逻辑)
        chatMemory.clear(chatId);
        chatMemory.add(chatId, updatedMemory);

        return response;
    }

    /**
     * chat with language model that has memory,
     * streaming output
     *
     * @param message user given message
     * @param chatId  chat conversation ID
     * @return information in chat
     */
    public Flux<String> doChatStream(String message, String chatId, String userId) {
        return  chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
                .stream()
                .content();

    }
}