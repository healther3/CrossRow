package com.dyx.crossrow.retriever;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.dyx.crossrow.elasticsearch.CrossRowDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import com.dyx.crossrow.utils.UserContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {
    private final ElasticsearchClient esClient;
    private final EmbeddingModel embeddingModel;
    private final String indexName;

    //分数阈值
    private final double minScoreThreshold = 1.0;   // 绝对下限
    private final double relativeThreshold = 0.3;    // 相对比例

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
                                .should(m -> m
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

    private List<Document> convertToDocuments(SearchResponse<CrossRowDocument> response) {
        List<Document> allDocs = response.hits().hits().stream()
                .map(this::mapToSpringAIDocument)
                .toList();
        if (allDocs.isEmpty()) return allDocs;

        Map<String, Document> deduplicated = new LinkedHashMap<>();
        for (Document doc : allDocs) {
            deduplicated.merge(doc.getId(), doc, (existing, incoming) ->
                    (incoming.getScore() != null && incoming.getScore() > existing.getScore()) ? incoming : existing
            );
        }
        List<Document> uniqueDocs = new ArrayList<>(deduplicated.values());

        logScoreDistribution(uniqueDocs);

        double maxScore = uniqueDocs.stream()
                .mapToDouble(doc -> doc.getScore() != null ? doc.getScore() : 0.0)
                .max().orElse(0.0);

        double effectiveThreshold = Math.max(minScoreThreshold, maxScore * relativeThreshold);
        List<Document> filtered = uniqueDocs.stream()
                .filter(doc -> doc.getScore() != null && doc.getScore() >= effectiveThreshold)
                .toList();

        log.info("[RAG过滤] maxScore={}, threshold={}, 去重后{}条 -> 过滤后{}条",
                String.format("%.4f", maxScore), String.format("%.4f", effectiveThreshold),
                uniqueDocs.size(), filtered.size());

        return filtered;
    }

    /**
     * 诊断日志：输出每条命中的分数、来源文件名，以及分数分布统计。
     * 用于校准 minScoreThreshold，观察稳定后可降级为 debug 或删除。
     */
    private void logScoreDistribution(List<Document> docs) {
        if (docs.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("\n[RAG分数诊断] index=%s, 命中%d条:\n", indexName, docs.size()));
        sb.append(String.format("  %-4s | %-10s | %s\n", "rank", "score", "source"));
        sb.append("  -----|------------|------------------------------------------\n");

        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        List<Double> scores = new ArrayList<>();

        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            double score = doc.getScore() != null ? doc.getScore() : 0.0;
            String filename = (String) doc.getMetadata().getOrDefault("filename", "unknown");
            String preview = doc.getText().substring(0, Math.min(50, doc.getText().length())).replace("\n", " ");

            sb.append(String.format("  #%-3d | %-10.4f | %s [%s]\n", i + 1, score, filename, preview));

            scores.add(score);
            sum += score;
            if (score < min) min = score;
            if (score > max) max = score;
        }

        double avg = sum / scores.size();

        // 计算相邻文档之间的最大分数跌落（gap），帮助找到自然分界线
        double maxGap = 0;
        int gapPosition = -1;
        for (int i = 0; i < scores.size() - 1; i++) {
            double gap = scores.get(i) - scores.get(i + 1);
            if (gap > maxGap) {
                maxGap = gap;
                gapPosition = i + 1;
            }
        }

        sb.append("  -----|------------|------------------------------------------\n");
        sb.append(String.format("  统计: min=%.4f, max=%.4f, avg=%.4f\n", min, max, avg));
        if (gapPosition > 0) {
            sb.append(String.format("  最大分数跌落: #%d→#%d, gap=%.4f (建议阈值参考: %.4f)\n",
                    gapPosition, gapPosition + 1, maxGap, scores.get(gapPosition)));
        }
        sb.append(String.format("  当前阈值: absolute=%.4f, relative=%.1f%%×max=%.4f, effective=%.4f",
                minScoreThreshold, relativeThreshold * 100, max * relativeThreshold,
                Math.max(minScoreThreshold, max * relativeThreshold)));

        log.info(sb.toString());
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
