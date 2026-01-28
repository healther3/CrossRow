package com.dyx.crossrow.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAgentApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfiguration {
    @Value("${spring.ai.dashscope.api-key}")
    private  String dashscopeApiKey;

    /**
     * ragAdvisor that use cloud data base. Based on dashscope and Spring
     * AI alibaba
     * @return arg cloud advisor
     */
    @Bean
    public Advisor ragCloudAdvisor() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashscopeApiKey)
                .build();
        final String KNOWLEDGE_INDEX = "哲学观念";
        DocumentRetriever dashScopeDocumentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder()
                        .indexName(KNOWLEDGE_INDEX)
                        .build());

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(dashScopeDocumentRetriever)
                .build();

    }
    @Bean
    public Advisor ragAdvisor(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        QueryTransformer translationQueryTransformer = TranslationQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .targetLanguage("Chinese")
                .build();

        QueryTransformer compressionQueryTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        QueryTransformer rewriteQureryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.5)
                .topK(5)
                .build();

//        增强召回率，扩展能捕获更多意图，会增加调用延迟并且消耗更多tokens
//        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
//                .chatClientBuilder(chatClientBuilder) // 需要引入大模型
//                .numberOfQueries(3) // 生成新的问题条数
//                .includeOriginal(true) // 是否包括原先问题，默认是true
//                .build();

        PromptTemplate promptTemplate = new PromptTemplate("""
                下面是一些会帮助回答用户问题的信息
                ---------------------
                {context}
                ---------------------
                结合这些可以帮助回答的上下文信息，给出用户问题分析和解决方案.
                *回答时请注明信息来源，例如:"根据存在主义哲学观念，..."*
                *不要在回答中写出文件名*
                问题: {query}
                回答:
                """);
        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder().promptTemplate(promptTemplate).build();

        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(
                        compressionQueryTransformer,
                        rewriteQureryTransformer)
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter)
                .build();
    }
}
