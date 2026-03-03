package com.dyx.crossrow.tool;

import com.dyx.crossrow.elasticsearch.ElasticsearchDocumentStore;
import com.dyx.crossrow.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class UpdateUserMemoryTool {

     private final ElasticsearchDocumentStore elasticsearchDocumentStore;

    public UpdateUserMemoryTool(ElasticsearchDocumentStore elasticsearchDocumentStore) {
        this.elasticsearchDocumentStore = elasticsearchDocumentStore;
    }

    @Tool(
            name = "updateUserMemory",
            description = "Save important user info (preferences, personal history, philosophical stance) to long-term memory. Use this when the user reveals something new and significant about themselves."
    )
    public String updateUserMemory(
            @ToolParam(description = "The factual statement about the user to remember. E.g., 'User believes in Stoicism' or 'User is a CS student.")
            String memoryFact) {

        // 1. 获取当前用户 ID
        String userId = UserContext.getUserId();
        if (userId == null || userId.isEmpty()) {
            return "Failed: No user login context.";
        }

        try {
            // 2. 准备元数据 (Metadata)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("userId", userId);      // 绑定给特定用户
            metadata.put("type", "user_memory"); // 标记类型为“用户记忆”
            metadata.put("filename", "user_memory_stream");

            // 3. 封装成 Document
            Document memoryDoc = new Document(memoryFact, metadata);

            // 4. 调用你现有的 ES 服务进行存储
            elasticsearchDocumentStore.storeAll(List.of(memoryDoc));

            log.info("User {} Memory: {}", userId, memoryFact);
            return "Successfully saved memory to Elasticsearch.";

        } catch (Exception e) {
            log.error("fail write in memory", e);
            return "Error saving memory: " + e.getMessage();
        }
    }
}