package com.dyx.crossrow.config;

import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
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
import org.springframework.core.io.Resource;

@Configuration
public class HybridRagConfiguration {

//    @Bean
//    public Advisor ragAdvisor(VectorStore pgVectorStore, ChatClient.Builder chatClientBuilder, @Value("classpath:/prompts/rag-retrieve-answer-prompt.st") Resource ragPromptResource) {
//        QueryTransformer translationQueryTransformer = TranslationQueryTransformer.builder()
//                .chatClientBuilder(chatClientBuilder)
//                .targetLanguage("Chinese")
//                .build();
//
//        QueryTransformer compressionQueryTransformer = CompressionQueryTransformer.builder()
//                .chatClientBuilder(chatClientBuilder)
//                .build();
//
//        QueryTransformer rewriteQureryTransformer = RewriteQueryTransformer.builder()
//                .chatClientBuilder(chatClientBuilder)
//                .build();
//
//        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
//                .vectorStore(pgVectorStore)
//                .similarityThreshold(0.5)
//                .topK(10)
//                .build();
//
////        增强召回率，扩展能捕获更多意图，会增加调用延迟并且消耗更多tokens
////        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
////                .chatClientBuilder(chatClientBuilder) // 需要引入大模型
////                .numberOfQueries(3) // 生成新的问题条数
////                .includeOriginal(true) // 是否包括原先问题，默认是true
////                .build();
//
//        PromptTemplate promptTemplate = new PromptTemplate(ragPromptResource);
//        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder().promptTemplate(promptTemplate).build();
//
//        return RetrievalAugmentationAdvisor.builder()
//                .queryTransformers(
//                        compressionQueryTransformer,
//                        rewriteQureryTransformer)
//                .documentRetriever(documentRetriever)
//                .queryAugmenter(queryAugmenter)
//                .build();
//    }

    /**
     *  混个检索RAG advisor -> knn向量查询+BM25，ik分词检索
     * @param hybridRetriever 混合检索器
     * @return  混合检索advisor
     */
    @Bean
    public Advisor hybridRagAdvisor(@org.springframework.beans.factory.annotation.Qualifier("philosophyRetriever") HybridDocumentRetriever hybridRetriever,
                                    ChatClient.Builder chatClientBuilder,
                                    @Value("classpath:/prompts/rag-retrieve-answer-prompt.st") Resource ragPromptResource){
        // 聚合转换器
        QueryTransformer compressionQueryTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();
        // 重写转换器
        QueryTransformer rewriteQureryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        PromptTemplate promptTemplate = new PromptTemplate(ragPromptResource);
        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(promptTemplate)
                .build();

        // 构建 Advisor
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(compressionQueryTransformer, rewriteQureryTransformer)
                .documentRetriever(hybridRetriever)  // 使用混合检索器
                .queryAugmenter(queryAugmenter)
                .build();
    }

}
