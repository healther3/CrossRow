package com.dyx.crossrow.retriever;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.dyx.crossrow.elasticsearch.CrossRowDocument;
import com.dyx.crossrow.properties.ElasticsearchProperties;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Component
public class HybridDocumentRetriever implements DocumentRetriever {
    private final ElasticsearchClient esClient;
    private final EmbeddingModel embeddingModel;
    private final ElasticsearchProperties properties;

    // 检索参数
    private final int topK = 10;

    public HybridDocumentRetriever(ElasticsearchClient esClient, EmbeddingModel embeddingModel, ElasticsearchProperties properties) {
        this.esClient = esClient;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public List<Document> retrieve(Query query) {
        try {
            String queryText = query.text();
            log.info(" 开始检索，查询文本: {}", queryText);

            // 1. 将查询文本向量化
            float[] queryVector = embeddingModel.embed(queryText);
            List<Float> queryVectorList = IntStream.range(0, queryVector.length)
                    .mapToObj(j -> queryVector[j])
                    .toList();
            log.info(" 向量化完成，维度: {}", queryVector.length);

            // 2. 构建混合查询
            SearchRequest searchRequest = buildHybridSearchRequest(queryText, queryVectorList);

            // 3. 执行查询
            SearchResponse<CrossRowDocument> response = esClient.search(
                    searchRequest,
                    CrossRowDocument.class
            );

            // 4. 转换结果
            List<Document> results = convertToDocuments(response);
            log.info(" 检索完成，命中 {} 条文档", results.size());
            
            if (!results.isEmpty()) {
                log.info(" 第一条结果预览: {}", results.get(0).getText().substring(0, Math.min(100, results.get(0).getText().length())));
            }
            
            return results;

        } catch (IOException e) {
            log.error(" 混合检索失败: {}", e.getMessage(), e);
            return List.of();
        } catch (Exception e) {
            log.error(" 检索时发生异常: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private SearchRequest buildHybridSearchRequest(String queryText, List<Float> queryVector) {
        // 关键词检索（BM25 + IK分词）
        return SearchRequest.of(s -> s
                .index(properties.getIndexName())
                .size(topK)
                .query(q -> q
                        .match(m -> m
                                .field("content")
                                .query(queryText)
                        )
                )
                // KNN 向量检索
                .knn(k -> k
                        .field("embedding")
                        .queryVector(queryVector)
                        .k(50)
                        .numCandidates(100)
                )
                // RRF 融合两种查询方式 -> 付费功能
//                .rank(r -> r
//                        .rrf(rrf -> rrf
//                                .rankConstant(60L)
//                                .rankWindowSize(100L)
//                        )
//                )
        );


    }

    // 主方法
    private List<Document> convertToDocuments(SearchResponse<CrossRowDocument> response) {
        return response.hits().hits().stream()
                .map(this::mapToSpringAIDocument)
                .toList();
    }

    // 专门负责转换的私有方法
    private Document mapToSpringAIDocument(Hit<CrossRowDocument> hit) {
        CrossRowDocument record = hit.source();
        return Document.builder()
                .id(record.getId())
                .text(record.getContent())
                .metadata(record.getMetadata())
                .score(hit.score())
                .build();
    }

}
