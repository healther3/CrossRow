package com.dyx.crossrow.retriever;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.dyx.crossrow.elasticsearch.CrossRowDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.rag.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;
import com.dyx.crossrow.utils.UserContext;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {
    private final ElasticsearchClient esClient;
    private final EmbeddingModel embeddingModel;
    private final String indexName;

    // 检索参数
    private final int topK = 10;

    public HybridDocumentRetriever(ElasticsearchClient esClient, EmbeddingModel embeddingModel, String indexName) {
        this.esClient = esClient;
        this.embeddingModel = embeddingModel;
        this.indexName = indexName;
    }

    @Override
    public List<Document> retrieve(org.springframework.ai.rag.Query query) {
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
        //user memory 检查

        String userId = UserContext.getUserId();
        Query accessControlQuery = Query.of(q -> q.bool(b -> {
            // 公开文档 (没有 userId 字段)
            b.should(s -> s.bool(n -> n.mustNot(mn -> mn.exists(e -> e.field("metadata.userId")))));

            // 私有文档 (userId 匹配)
            if (userId != null) {
                // 对于动态映射的字符串字段，通常使用 .keyword 子字段进行精确匹配
                b.should(s -> s.term(t -> t.field("metadata.userId.keyword").value(userId)));
            }
            // 至少满足 A 或 B 中的一个
            b.minimumShouldMatch("1");
            return b;
        }));


        // 关键词检索（BM25 + IK分词）
        return SearchRequest.of(s -> s
                .index(this.indexName)
                .size(topK)
                .query(q -> q
                        .bool(b -> b
                                .must(m -> m
                                    .match(ma -> ma
                                        .field("content")
                                        .query(queryText)
                                    )
                                )
                                .filter(accessControlQuery)
                        )
                )
                // KNN 向量检索
                .knn(k -> k
                        .field("embedding")
                        .queryVector(queryVector)
                        .k(50)
                        .numCandidates(100)
                        .filter(accessControlQuery)
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
