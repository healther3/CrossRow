package com.dyx.crossrow.agent;

import com.dyx.crossrow.agent.model.ToolChoice;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CrossRow extends ToolCallAgent{

    public CrossRow(ToolCallback[] allTools,
                    List<String> specialToolNames,
                    ToolCallingManager toolCallingManager,
                    ChatModel chatModel,
                    @Value("classpath:/prompts/system-prompt.st") Resource systemPromptResource,
                    @Value("classpath:/prompts/next-step-prompt.st") Resource nextStepPromptResource
       ) {
        super(  allTools,
                List.of("terminate"),
                toolCallingManager,
                ToolChoice.AUTO);
        //set name
        setName("NoName");
        //set default prompt and next-step prompt
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPromptResource);
        PromptTemplate nextStepPromptTemplate = new PromptTemplate(nextStepPromptResource);
        setSystemPrompt(systemPromptTemplate.render());
        setNextStepPrompt(nextStepPromptTemplate.render());
    }



}
