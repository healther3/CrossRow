package com.dyx.crossrow.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CrossRow documents loader
 */
@Slf4j
@Component
public class CrossRowDocumentLoader {
    private final ResourcePatternResolver resourcePatternResolver;

    public CrossRowDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * load multiply markdown files
     * @return list of files
     */
    public List<Document> loadMarkDownFiles() {
        List<Document> allFiles = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:documents/*.md");
            for(Resource resource : resources) {
                String fileName = resource.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", fileName)
                        .build();
                MarkdownDocumentReader markdownDocumentReader= new  MarkdownDocumentReader(resource,config);
                // 读取后处理：在内容前加上文件名
                List<Document> docs = markdownDocumentReader.read();
                for (Document doc : docs) {
                    String enrichedContent = "[文档来源: " + fileName + "]\n\n" + doc.getText();
                    Document enrichedDoc = Document.builder()
                            .text(enrichedContent)
                            .metadata(doc.getMetadata())
                            .build();
                    allFiles.add(enrichedDoc);
                }
            }
        } catch(IOException e) {
            log.error("Failed to load markdown files.", e);
        }
        return allFiles;
    }
}
