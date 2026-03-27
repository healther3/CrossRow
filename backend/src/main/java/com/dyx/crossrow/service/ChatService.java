package com.dyx.crossrow.service;

import com.dyx.crossrow.advisor.MyLogAdvisor;
import com.dyx.crossrow.advisor.PromptInjectionGuardAdvisor;
import com.dyx.crossrow.advisor.SimpleAuthAdvisor;
import com.dyx.crossrow.advisor.SimpleQuotaAdvisor;
import com.dyx.crossrow.agent.CrossRowAgent;
import com.dyx.crossrow.agent.ExpertAgent;
import com.dyx.crossrow.factory.AgentFactory;
import com.dyx.crossrow.factory.ChatModelProvider;
import com.dyx.crossrow.model.ChatSession;
import com.dyx.crossrow.model.QuotaType;
import com.dyx.crossrow.model.dto.MediaContentDTO;
import com.dyx.crossrow.orchestrator.ExpertOrchestrator;
import com.dyx.crossrow.service.ModelRouterService.RouteDecision;
import com.dyx.crossrow.tool.ImageGenerationTool;
import com.dyx.crossrow.tool.WebSearchTool;
import org.springframework.ai.chat.messages.UserMessage;
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
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
public class ChatService {

    private final SystemPromptTemplate systemPromptTemplate;
    private final AgentFactory agentFactory;
    private final ChatModelProvider chatModelProvider;
    private final UserService userService;

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

    @jakarta.annotation.Resource
    private final SimpleAuthAdvisor simpleAuthAdvisor;

    @jakarta.annotation.Resource
    private ExpertOrchestrator expertOrchestrator;

    @jakarta.annotation.Resource
    private ChatSessionService chatSessionService;

    @jakarta.annotation.Resource
    private ModelRouterService modelRouterService;

    @jakarta.annotation.Resource
    private MediaProcessingService mediaProcessingService;

    @jakarta.annotation.Resource
    private QuotaService quotaService;

    /**
     * Initialize the ChatService with ChatModelProvider for unified model management.
     * Models are selected based on user preferences stored in the database.
     */
    public ChatService(@Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource,
                       AgentFactory agentFactory, ChatMemory chatMemory, SimpleAuthAdvisor simpleAuthAdvisor,
                       ChatModelProvider chatModelProvider, UserService userService, QuotaService quotaService) {

        this.systemPromptTemplate = new SystemPromptTemplate(systemPromptResource);
        this.agentFactory = agentFactory;
        this.chatMemory = chatMemory;
        this.simpleAuthAdvisor = simpleAuthAdvisor;
        this.chatModelProvider = chatModelProvider;
        this.userService = userService;
        this.quotaService = quotaService;
    }

    /**
     * Build a ChatClient with user's preferred model and standard advisors.
     */
    private ChatClient buildChatClientForUser(String userId) {
        String modelName = userService.getPreferredModel(userId);
        ChatModel model = chatModelProvider.getByName(modelName);
        return ChatClient.builder(model)
                .defaultSystem(systemPromptTemplate.render())
                .defaultAdvisors(
                        simpleAuthAdvisor,
                        new SimpleQuotaAdvisor(quotaService, QuotaType.CHAT),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLogAdvisor(100),
                        new PromptInjectionGuardAdvisor()
                )
                .build();
    }

    /**
     * Build a simple ChatClient with user's preferred model (no advisors).
     */
    private ChatClient buildDefaultChatClientForUser(String userId) {
        String modelName = userService.getPreferredModel(userId);
        ChatModel model = chatModelProvider.getByName(modelName);
        return ChatClient.builder(model)
                .defaultAdvisors(new PromptInjectionGuardAdvisor())
                .build();
    }


    /**
     * Chat with language model that has memory.
     * Uses user's preferred model from ChatModelProvider.
     *
     * @param message user given message
     * @param chatId  chat conversation ID
     * @return information in chat
     */
    public String doChat(String message, String chatId, String userId) {
        chatSessionService.validateSessionOwnership(chatId, userId);
        chatSessionService.autoGenerateTitleIfNeeded(chatId, userId, message);
        ChatClient chatClient = buildChatClientForUser(userId);
        
        ChatClientResponse chatClientResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
                .call()
                .chatClientResponse();
        
        ChatResponse chatResponse = chatClientResponse.chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    record PainReport(String reportName, List<String> Solutions) {

    }

    /**
     * Chat with LLM and generate a report.
     * Uses user's preferred model from ChatModelProvider.
     *
     * @param message user given message
     * @param chatId  chat conversation ID
     * @return ai response with report
     */
    public PainReport doChatReport(String message, String chatId, String userId) {
        chatSessionService.validateSessionOwnership(chatId, userId);
        ChatClient chatClient = buildChatClientForUser(userId);
        
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
     * Chat with RAG (Retrieval Augmented Generation).
     * Uses user's preferred model from ChatModelProvider.
     *
     * @param message user given message
     * @param chatId  chat id
     * @param userId  userid
     * @return ai response
     */
    public String doChatWithRag(String message, String chatId, String userId) {
        chatSessionService.validateSessionOwnership(chatId, userId);
        ChatClient chatClient = buildChatClientForUser(userId);
        
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
                .call()
                .chatClientResponse();

        String content = chatClientResponse.chatResponse().getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * Chat with tools (search + image generation).
     * Uses user's preferred model from ChatModelProvider.
     */
    public String doChatWithTools(String message, String chatId, String userId, boolean allowImage, boolean allowSearch) {
        ChatClient defaultChatClient = buildDefaultChatClientForUser(userId);

        log.info("[链式调用] 开始处理: {}", message);

        log.info("正在调用搜索工具...");
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

        log.info(" (Summary): {}", summary);

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
        log.info(" 结果 (ImageContent): {}", imageContent);
        String content = summary + imageContent;
        log.info("content: {}", content);
        return content;
    }

    /**
     * Chat with MCP tools.
     * Uses user's preferred model from ChatModelProvider.
     */
    public String doChatWithMCP(String message, String chatId, String userId) {
        ChatClient defaultChatClient = buildDefaultChatClientForUser(userId);

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
        chatSessionService.validateSessionOwnership(chatId, userId);
        chatSessionService.autoGenerateTitleIfNeeded(chatId, userId, message);
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
     * Chat with language model that has memory, streaming output.
     * Uses user's preferred model from ChatModelProvider.
     * Returns ServerSentEvent to support custom event names (e.g., session_title).
     *
     * @param message user given message
     * @param chatId  chat conversation ID
     * @return SSE stream with content events and optional session_title event
     */
    public Flux<ServerSentEvent<String>> doChatStream(String message, String chatId, String userId) {
        chatSessionService.validateSessionOwnership(chatId, userId);
        ChatClient chatClient = buildChatClientForUser(userId);
        
        // 聊天内容流，每个 chunk 作为 message 事件
        Flux<ServerSentEvent<String>> contentStream = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
                .stream()
                .content()
                .map(content -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(content)
                        .build());
        
        // 异步生成标题，完成后发送 session_title 事件
        final String finalChatId = chatId;
        Flux<ServerSentEvent<String>> titleStream = chatSessionService
                .generateTitleIfNeededAsync(chatId, userId, message)
                .map(title -> ServerSentEvent.<String>builder()
                        .event("session_title")
                        .data("{\"title\":\"" + title.replace("\"", "\\\"") + "\", \"chatId\":\"" + finalChatId + "\"}")
                        .build())
                .flux();
        
        // 合并两个流：内容流和标题流并行执行
        return Flux.merge(contentStream, titleStream);
    }

    /**
     * Multimodal chat with streaming output.
     * Supports images and audio from GCS URLs.
     *
     * @param message user text message
     * @param mediaContents list of media (images/audio) with GCS URLs
     * @param chatId chat conversation ID
     * @param userId user ID
     * @return SSE stream with content events and optional session_title event
     */
    public Flux<ServerSentEvent<String>> doChatStreamWithMedia(
            String message, 
            List<MediaContentDTO> mediaContents,
            String chatId, 
            String userId) {
        
        chatSessionService.validateSessionOwnership(chatId, userId);
        ChatClient chatClient = buildChatClientForUser(userId);

        // 构建多模态 UserMessage
        UserMessage userMessage = mediaProcessingService.buildUserMessage(message, mediaContents);
        log.info("Multimodal chat - User: {}, Session: {}, Media count: {}", 
                userId, chatId, mediaContents != null ? mediaContents.size() : 0);

        // 聊天内容流
        Flux<ServerSentEvent<String>> contentStream = chatClient
                .prompt()
                .messages(userMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
                .stream()
                .content()
                .map(content -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(content)
                        .build());

        // 异步生成标题
        final String finalChatId = chatId;
        Flux<ServerSentEvent<String>> titleStream = chatSessionService
                .generateTitleIfNeededAsync(chatId, userId, message)
                .map(title -> ServerSentEvent.<String>builder()
                        .event("session_title")
                        .data("{\"title\":\"" + title.replace("\"", "\\\"") + "\", \"chatId\":\"" + finalChatId + "\"}")
                        .build())
                .flux();

        return Flux.merge(contentStream, titleStream);
    }

    public SseEmitter doChatWithCrossRowAgentStream(String message, String chatId, String userId,
                                                     boolean enableReview, int maxReviewRetries) {
        chatSessionService.validateSessionOwnership(chatId, userId);
        log.info("开始 Agent 对话流 - User: {}, Session: {}, Review: {}, MaxRetries: {}", 
                userId, chatId, enableReview, maxReviewRetries);

        //  通过工厂创建一个干净的、绑定了当前用户的 Agent
        CrossRowAgent agent = agentFactory.createAgent(userId, chatId);
        
        // 设置 Review 参数
        agent.setReviewEnabled(enableReview);
        agent.setMaxReviewRetries(maxReviewRetries);

        //  从 Redis 中提取历史记忆
        List<Message> history = chatMemory.get(chatId);
        if (!history.isEmpty()) {
            // 将历史记忆“装载”进 Agent 的大脑 (messageList)
            agent.setMessageList(new ArrayList<>(history));
            log.info("成功加载历史记忆，共 {} 条", history.size());
        }

        // 让 Agent 开始推理想象并执行工具 (它会自动将新问题 add 进 messageList)
        // 使用回调在 Agent 完成后保存内存，避免异步执行时机问题
        // 注意：标题生成必须在 onComplete 中同步执行，确保在 emitter.complete() 之前发送
        SseEmitter response = agent.runStream(message, () -> {
            // 提取 Agent 思考完毕后的完整大脑状态
            List<Message> updatedMemory = agent.getMessageList();

            // 保存回 Redis (注意：你的 RedisChatMemory.add 是追加逻辑，如果传入全量 list 会导致重复。
            // 所以我们先 clear 掉旧的，再存入新的全量历史，或者你可以在 RedisChatMemory 里优化一下合并逻辑)
            chatMemory.clear(chatId);
            chatMemory.add(chatId, updatedMemory);
            log.info("Agent 完成，已保存 {} 条消息到内存", updatedMemory.size());
        }, emitter -> {
            // 同步生成标题，确保在 emitter.complete() 之前发送 session_title 事件
            chatSessionService.autoGenerateTitleIfNeededSync(chatId, userId, message, emitter);
        });

        return response;
    }

    /**
     * Agent chat with image support
     */
    public SseEmitter doChatWithCrossRowAgentStreamWithImages(String message, List<MediaContentDTO> images,
                                                     String chatId, String userId,
                                                     boolean enableReview, int maxReviewRetries) {
        chatSessionService.validateSessionOwnership(chatId, userId);
        log.info("开始 Agent 对话流(带图片) - User: {}, Session: {}, Images: {}, Review: {}, MaxRetries: {}", 
                userId, chatId, images != null ? images.size() : 0, enableReview, maxReviewRetries);

        CrossRowAgent agent = agentFactory.createAgent(userId, chatId);
        agent.setReviewEnabled(enableReview);
        agent.setMaxReviewRetries(maxReviewRetries);

        List<Message> history = chatMemory.get(chatId);
        if (!history.isEmpty()) {
            agent.setMessageList(new ArrayList<>(history));
            log.info("成功加载历史记忆，共 {} 条", history.size());
        }

        SseEmitter response = agent.runStream(message, images, () -> {
            List<Message> updatedMemory = agent.getMessageList();
            chatMemory.clear(chatId);
            chatMemory.add(chatId, updatedMemory);
            log.info("Agent 完成，已保存 {} 条消息到内存", updatedMemory.size());
        }, emitter -> {
            // 同步生成标题，确保在 emitter.complete() 之前发送 session_title 事件
            chatSessionService.autoGenerateTitleIfNeededSync(chatId, userId, message, emitter);
        });

        return response;
    }

    /**
     * Multi-Agent Expert mode: routes to appropriate expert (philosophy/psychology/sociology)
     * @param message user prompt
     * @param images image list
     * @param chatId conversation id
     * @param userId user id
     * @param enableReview whether to enable review agent
     * @param maxReviewRetries max review retry attempts
     * @return expert agent response in SSE form
     */
    public SseEmitter doChatWithExpertStream(String message, List<MediaContentDTO> images, String chatId, String userId,
                                             boolean enableReview, int maxReviewRetries) {
        chatSessionService.validateSessionOwnership(chatId, userId);
        log.info("开始 Expert 对话流 - User: {}, Session: {}, Images: {}, Review: {}, MaxRetries: {}", 
                userId, chatId, images != null ? images.size() : 0, enableReview, maxReviewRetries);

        // 1. 先让 Orchestrator 判断路由到哪个专家
        String domain = expertOrchestrator.previewRoute(message);
        log.info("路由决策: {} 专家", domain);

        // 2. 创建对应的专家 Agent
        ExpertAgent expert = agentFactory.createExpertAgent(domain, userId, chatId);
        
        // 设置 Review 参数
        expert.setReviewEnabled(enableReview);
        expert.setMaxReviewRetries(maxReviewRetries);

        // 3. 从 Redis 中提取历史记忆
        List<Message> history = chatMemory.get(chatId);
        if (history != null && !history.isEmpty()) {
            expert.setMessageList(new ArrayList<>(history));
            log.info("成功加载历史记忆，共 {} 条", history.size());
        }

        // 4. 执行专家 Agent，完成后保存记忆
        SseEmitter response = expert.runStream(message, images, () -> {
            List<Message> updatedMemory = expert.getMessageList();
            chatMemory.clear(chatId);
            chatMemory.add(chatId, updatedMemory);
            log.info("{} 专家完成，已保存 {} 条消息到内存", domain, updatedMemory.size());
        }, emitter -> {
            // 同步生成标题，确保在 emitter.complete() 之前发送 session_title 事件
            chatSessionService.autoGenerateTitleIfNeededSync(chatId, userId, message, emitter);
        });

        return response;
    }

    /**
     * Preview which expert would handle the query (for testing)
     */
    public String previewExpert(String message) {
        return expertOrchestrator.previewRoute(message);
    }


    /**
     * 智能路由流式聊天：使用 AI 评审自动选择模型，支持多模态输入
     * Returns ServerSentEvent to support custom event names (e.g., session_title).
     *
     * @param message 用户消息
     * @param mediaContents 媒体内容列表（可为 null）
     * @param chatId  会话ID
     * @param userId  用户ID
     * @return SSE stream with content events and optional session_title event
     */
    public Flux<ServerSentEvent<String>> doChatStreamWithAutoRoute(String message, List<MediaContentDTO> mediaContents, String chatId, String userId) {
        chatSessionService.validateSessionOwnership(chatId, userId);
        
        RouteDecision decision = modelRouterService.getRouteDecision(message);
        log.info("流式AI评审路由: 复杂度={}, 选择模型={}, 多模态={}", 
                decision.review().isComplex() ? "复杂" : "简单", 
                decision.selectedModel(),
                mediaContents != null && !mediaContents.isEmpty());

        ChatModel selectedModel = modelRouterService.route(message);
        
        ChatClient routedClient = ChatClient.builder(selectedModel)
                .defaultSystem(systemPromptTemplate.render())
                .defaultAdvisors(
                        simpleAuthAdvisor,
                        new SimpleQuotaAdvisor(quotaService, QuotaType.CHAT),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLogAdvisor(100),
                        new PromptInjectionGuardAdvisor()
                )
                .build();

        // 构建 UserMessage (支持多模态)
        UserMessage userMessage = mediaProcessingService.buildUserMessage(message, mediaContents);

        // 聊天内容流
        Flux<ServerSentEvent<String>> contentStream = routedClient
                .prompt()
                .messages(userMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
                .stream()
                .content()
                .map(content -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(content)
                        .build());
        
        // 异步生成标题流
        final String finalChatId = chatId;
        Flux<ServerSentEvent<String>> titleStream = chatSessionService
                .generateTitleIfNeededAsync(chatId, userId, message)
                .map(title -> ServerSentEvent.<String>builder()
                        .event("session_title")
                        .data("{\"title\":\"" + title.replace("\"", "\\\"") + "\", \"chatId\":\"" + finalChatId + "\"}")
                        .build())
                .flux();
        
        return Flux.merge(contentStream, titleStream);
    }

    /**
     * 单独调用任务评审（用于调试或前端展示）
     */
    public ModelRouterService.TaskReview reviewTask(String message) {
        return modelRouterService.reviewTask(message);
    }

    /**
     * 使用指定模型聊天
     * Returns ServerSentEvent to support custom event names (e.g., session_title).
     *
     * @param message   用户消息
     * @param chatId    会话ID
     * @param userId    用户ID
     * @param modelName 模型名称 (gemini/qwen)
     * @return SSE stream with content events and optional session_title event
     */
    public Flux<ServerSentEvent<String>> doChatWithModel(String message, String chatId, String userId, String modelName) {
        chatSessionService.validateSessionOwnership(chatId, userId);
        
        ChatModel selectedModel = modelRouterService.getByName(modelName);
        log.info("指定模型聊天: {}", modelName);

        ChatClient routedClient = ChatClient.builder(selectedModel)
                .defaultSystem(systemPromptTemplate.render())
                .defaultAdvisors(
                        simpleAuthAdvisor,
                        new SimpleQuotaAdvisor(quotaService, QuotaType.CHAT),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLogAdvisor(100),
                        new PromptInjectionGuardAdvisor()
                )
                .build();

        // 聊天内容流
        Flux<ServerSentEvent<String>> contentStream = routedClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("userId", userId))
                .stream()
                .content()
                .map(content -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(content)
                        .build());
        
        // 异步生成标题流
        final String finalChatId = chatId;
        Flux<ServerSentEvent<String>> titleStream = chatSessionService
                .generateTitleIfNeededAsync(chatId, userId, message)
                .map(title -> ServerSentEvent.<String>builder()
                        .event("session_title")
                        .data("{\"title\":\"" + title.replace("\"", "\\\"") + "\", \"chatId\":\"" + finalChatId + "\"}")
                        .build())
                .flux();
        
        return Flux.merge(contentStream, titleStream);
    }

    /**
     * 获取模型路由信息
     */
    public RouteDecision previewRoute(String message) {
        return modelRouterService.getRouteDecision(message);
    }

}