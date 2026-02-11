package com.dyx.crossrow.tool;

import com.dyx.crossrow.properties.SearchEngineProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@EnableConfigurationProperties(SearchEngineProperties.class)
public class WebSearchTool {
    private final SearchEngineProperties searchEngineProperties;
    public final RestClient restClient;

    public WebSearchTool(SearchEngineProperties searchEngineProperties) {
        this.searchEngineProperties = searchEngineProperties;
        this.restClient = RestClient.create();
    }
    @Tool(description = "Search the web for information. Use this tool when the user wants to know " +
            "something that is not in the context of the conversation.")
    public Map<String, Object> searchWeb(@ToolParam(description = "search the keywords, try to be specific.")
                                String query) {
        String url = "https://api.search.brave.com/res/v1/web/search?q={q}&count=5";
        System.out.println("正在调用 Brave Search: " + query);

        try {
            // 构建 URI (GET 请求的核心)
            // 发送GET请求
            JsonNode response = restClient.get()
                    .uri(url, query)
                    .header("X-Subscription-Token", searchEngineProperties.getApiKey())
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);

            StringBuilder resultBuilder = new StringBuilder();
            if (response.has("web") && response.get("web").has("results")) {
                int index = 1;
                for (JsonNode item : response.get("web").get("results")) {
                    String title = item.path("title").asText();
                    String desc = item.path("description").asText();
                    String link = item.path("url").asText();
                    resultBuilder.append(String.format("%d. 标题: %s\n   摘要: %s\n   链接: %s\n\n",
                            index++, title, desc, link));
                }
            } else {
                resultBuilder.append("未找到相关结果。");
            }

            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("search_results", resultBuilder.toString());
            return finalResult;

        } catch (Exception e) {
            return Map.of("error", "Search failed: " + e.getMessage());
        }
    }
}