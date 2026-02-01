package com.dyx.crossrow.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
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
     * Split by Q&A pairs (#### headers) to preserve semantic integrity
     * @return list of files
     */

    public List<Document> loadMarkDownFiles() {
        List<Document> allFiles = new ArrayList<>();

        // 作为后备，当单个问答对太长时使用
        TokenTextSplitter textSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMaxNumChunks(20)
                .withMinChunkLengthToEmbed(50)
                .withMinChunkSizeChars(100)
                .build();

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
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                List<Document> docs = markdownDocumentReader.read();

                for (Document doc : docs) {
                    // 按 #### 标题分割，保持问答对完整
                    List<Document> qaPairs = splitByQAHeaders(doc.getText(), fileName);
                    
                    if (qaPairs.isEmpty()) {
                        // 如果没有找到问答格式，使用 TokenTextSplitter 作为后备
                        List<Document> chunks = textSplitter.split(List.of(doc));
                        for (Document chunk : chunks) {
                            allFiles.add(enrichDocument(chunk, fileName));
                        }
                    } else {
                        allFiles.addAll(qaPairs);
                    }
                }
            }
        } catch(IOException e) {
            log.error("Failed to load markdown files.", e);
        }
        return allFiles;
    }

    /**
     * 按 #### 标题分割文档，保持每个问答对的语义完整性
     */
    private List<Document> splitByQAHeaders(String content, String fileName) {
        List<Document> qaPairs = new ArrayList<>();
        // 按 #### 分割，但保留分隔符
        String[] parts = content.split("(?=####\\s)");

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty() || trimmed.length() < 20) {
                continue; // 跳过太短的片段
            }
            Document doc = Document.builder()
                    .text("[文档来源: " + fileName + "]\n\n" + trimmed)
                    .metadata(java.util.Map.of("filename", fileName, "type", "qa_pair"))
                    .build();
            qaPairs.add(doc);
        }
        return qaPairs;
    }

    /**
     * 为文档添加来源信息
     */
    private Document enrichDocument(Document chunk, String fileName) {
        String enrichedContent = "[文档来源: " + fileName + "]\n\n" + chunk.getText();
        return Document.builder()
                .text(enrichedContent)
                .metadata(chunk.getMetadata())
                .build();
    }
}
