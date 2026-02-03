package com.dyx.crossrow.retriever;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.dyx.crossrow.properties.ElasticsearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class HybridDocumentRetriever implements DocumentRetriever {
    private final ElasticsearchClient esClient;
    private final EmbeddingModel embeddingModel;
    private final ElasticsearchProperties properties;

    // 检索参数
    private int topK = 10;
    private int numCandidates = 100;  // KNN 候选数量
    private int rankConstant = 60;     // RRF 常数

    public HybridDocumentRetriever(ElasticsearchClient esClient, EmbeddingModel embeddingModel, ElasticsearchProperties properties) {
        this.esClient = esClient;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }


    @Override
    public List<Document> retrieve(Query query) {
        try {
            String queryText = query.text();

            // 1. 将查询文本向量化
            float[] queryVector = embeddingModel.embed(queryText);
            List<Float> queryVectorList = toFloatList(queryVector);

            // 2. 构建混合查询
            SearchRequest searchRequest = buildHybridSearchRequest(queryText, queryVectorList);

            // 3. 执行查询
            SearchResponse<DocumentRecord> response = esClient.search(
                    searchRequest,
                    DocumentRecord.class
            );

            // 4. 转换结果
            return convertToDocuments(response);

        } catch (IOException e) {
            log.error("混合检索失败: {}", e.getMessage());
            return List.of();
        }
    }
}
