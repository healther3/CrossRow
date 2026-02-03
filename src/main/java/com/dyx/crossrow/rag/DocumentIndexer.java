package com.dyx.crossrow.rag;

import com.dyx.crossrow.elasticsearch.ElasticsearchDocumentStore;
import com.dyx.crossrow.elasticsearch.ElasticsearchIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentIndexer implements ApplicationRunner {

    private final ElasticsearchDocumentStore documentStore;
    private final CrossRowDocumentLoader documentLoader;
    private final SimpleKeyWordEnricher keywordEnricher;
    private final ElasticsearchIndexManager indexManager; // 建议引入，用于初始化前检查

    @Override
    public void run(ApplicationArguments args) throws Exception {

        indexManager.deleteIndex();

        log.info(" 开始启动自动化文档索引流程...");

        indexManager.ensureIndexExists();

        //  加载文档
        List<Document> documents = documentLoader.loadMarkDownFiles();
        if (documents.isEmpty()) {
            log.warn(" 未发现待处理的 Markdown 文件，跳过索引流程");
            return;
        }

        //  关键词丰富
        List<Document> enrichedDocuments = keywordEnricher.enrichDocuments(documents);

        //  存储到 ES
        documentStore.storeAll(enrichedDocuments);

        log.info("文档索引完成，共 {} 条数据已同步至 Elasticsearch", enrichedDocuments.size());
    }
}
