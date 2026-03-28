package com.dyx.crossrow.config;

import com.dyx.crossrow.elasticsearch.ElasticsearchDocumentStore;
import com.dyx.crossrow.properties.SearchEngineProperties;
import com.dyx.crossrow.retriever.HybridDocumentRetriever;
import com.dyx.crossrow.service.ImageGenerationService;
import com.dyx.crossrow.tool.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegister {

    //集中注册工具

    @Bean
    public WebSearchTool  webSearchTool(SearchEngineProperties searchEngineProperties){
        return new WebSearchTool(searchEngineProperties);
    }

    @Bean
    public ImageGenerationTool imageGenerationTool(ImageGenerationService imageGenerationService){
        return new ImageGenerationTool(imageGenerationService);
    }

    @Bean
    public TerminateTool terminateTool(){
        return new TerminateTool();
    }

    @Bean
    public PhilosophyRetrieveTool philosophyRetrieveTool(@Qualifier("philosophyRetriever") HybridDocumentRetriever retriever){
        return new PhilosophyRetrieveTool(retriever);
    }

    @Bean
    public PsychologyRetrieveTool psychologyRetrieveTool(@Qualifier("psychologyRetriever") HybridDocumentRetriever retriever){
        return new PsychologyRetrieveTool(retriever);
    }

    @Bean
    public SociologyRetrieveTool sociologyRetrieveTool(@Qualifier("sociologyRetriever") HybridDocumentRetriever retriever){
        return new SociologyRetrieveTool(retriever);
    }

    @Bean
    public UpdateUserMemoryTool updateUserMemoryTool(ElasticsearchDocumentStore elasticsearchDocumentStore){
        return new UpdateUserMemoryTool(elasticsearchDocumentStore);
    }

    @Bean
    public AskHumanTool askHumanTool(){
        return new AskHumanTool();
    }

    @Bean
    public CalculatorTool calculatorTool(){
        return new CalculatorTool();
    }

    @Bean
    public GetCurrentTimeTool getCurrentTimeTool(){
        return new GetCurrentTimeTool();
    }

    @Bean("crossRowTools")
    public ToolCallback[] crossRowTools(@Qualifier("sharedTools") ToolCallback[] sharedTools,
                                        PhilosophyRetrieveTool philosophyRetrieveTool,
                                        PsychologyRetrieveTool psychologyRetrieveTool,
                                        SociologyRetrieveTool sociologyRetrieveTool)
    {
        ToolCallback[] domainTools = ToolCallbacks.from(
                philosophyRetrieveTool,
                psychologyRetrieveTool,
                sociologyRetrieveTool
        );
        return mergeTools(domainTools, sharedTools);
    }

    // 通用工具（所有 Agent 共享）
    @Bean("sharedTools")
    public ToolCallback[] sharedTools(WebSearchTool webSearchTool,
                                      ImageGenerationTool imageGenerationTool,
                                      TerminateTool terminateTool,
                                      UpdateUserMemoryTool updateUserMemoryTool,
                                      AskHumanTool askHumanTool,
                                      GetCurrentTimeTool getCurrentTimeTool,
                                      CalculatorTool calculatorTool){
        return ToolCallbacks.from(
                webSearchTool,
                imageGenerationTool,
                terminateTool,
                updateUserMemoryTool,
                askHumanTool,
                getCurrentTimeTool,
                calculatorTool
        );
    }

    // Philosophy Agent 专用工具集
    @Bean("philosophyTools")
    public ToolCallback[] philosophyTools(@Qualifier("sharedTools") ToolCallback[] sharedTools,
                                          PhilosophyRetrieveTool philosophyRetrieveTool){
        return mergeTools(sharedTools, ToolCallbacks.from(philosophyRetrieveTool));
    }

    // Psychology Agent 专用工具集
    @Bean("psychologyTools")
    public ToolCallback[] psychologyTools(@Qualifier("sharedTools") ToolCallback[] sharedTools,
                                          PsychologyRetrieveTool psychologyRetrieveTool){
        return mergeTools(sharedTools, ToolCallbacks.from(psychologyRetrieveTool));
    }

    // Sociology Agent 专用工具集
    @Bean("sociologyTools")
    public ToolCallback[] sociologyTools(@Qualifier("sharedTools") ToolCallback[] sharedTools,
                                         SociologyRetrieveTool sociologyRetrieveTool){
        return mergeTools(sharedTools, ToolCallbacks.from(sociologyRetrieveTool));
    }

    // 包含所有工具，用于 SimpleToolCallManager 执行工具调用
    @Bean("allTools")
    public ToolCallback[] allTools(@Qualifier("sharedTools") ToolCallback[] sharedTools,
                                   PhilosophyRetrieveTool philosophyRetrieveTool,
                                   PsychologyRetrieveTool psychologyRetrieveTool,
                                   SociologyRetrieveTool sociologyRetrieveTool){
        ToolCallback[] domainTools = ToolCallbacks.from(
                philosophyRetrieveTool,
                psychologyRetrieveTool,
                sociologyRetrieveTool
        );
        return mergeTools(sharedTools, domainTools);
    }

    private ToolCallback[] mergeTools(ToolCallback[] base, ToolCallback[] additional) {
        ToolCallback[] merged = new ToolCallback[base.length + additional.length];
        System.arraycopy(base, 0, merged, 0, base.length);
        System.arraycopy(additional, 0, merged, base.length, additional.length);
        return merged;
    }
}
