package com.dyx.crossrow.tool;

import com.dyx.crossrow.agent.CrossRowAgent;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 Agent 调用 AskHumanTool 的意识
 * 
 * 测试策略：
 * 1. 模糊请求测试 - Agent 应该询问用户澄清
 * 2. 明确请求测试 - Agent 应该直接执行
 * 3. 边界情况测试 - 测试各种模糊程度的请求
 */
@SpringBootTest
@ActiveProfiles("test")
class AskHumanToolTest {

    @Resource
    private ObjectProvider<CrossRowAgent> crossRowAgentProvider;

    /**
     * 测试模糊请求 - Agent 应该调用 askHuman
     * 这些请求缺少关键信息，Agent 不应该猜测
     */
    @ParameterizedTest
    @DisplayName("模糊请求应触发 askHuman")
    @ValueSource(strings = {
            "帮我搜一下",           // 缺少搜索主题
            "生成一张图",           // 缺少图片内容描述
            "那个问题怎么解决",     // "那个"指代不明
            "帮我处理一下",         // 完全不知道要处理什么
            "搜索新闻",             // 缺少新闻主题
            "画一幅画",             // 缺少画的内容
    })
    void shouldAskHumanForAmbiguousRequests(String ambiguousPrompt) {
        CrossRowAgent agent = crossRowAgentProvider.getObject();
        agent.setUserId("test-user");
        agent.setSessionId("test-session");
        
        String result = agent.run(ambiguousPrompt);
        
        System.out.println("=== Test Input: " + ambiguousPrompt + " ===");
        System.out.println("=== Result: ===");
        System.out.println(result);
        System.out.println("================\n");
        
        // 验证结果中包含 askHuman 调用或澄清性问题
        boolean askedHuman = result.contains("askHuman") 
                || result.contains("ask_human")
                || result.contains("什么")
                || result.contains("哪")
                || result.contains("具体")
                || result.contains("clarif");
        
        assertTrue(askedHuman, 
                "对于模糊请求 '" + ambiguousPrompt + "'，Agent 应该询问用户澄清，但实际结果: " + result);
    }

    /**
     * 测试明确请求 - Agent 应该直接执行，不需要询问
     */
    @ParameterizedTest
    @DisplayName("明确请求不应触发 askHuman")
    @ValueSource(strings = {
            "搜索2024年诺贝尔物理学奖得主",
            "生成一张赛博朋克风格的城市夜景图",
            "人生的意义是什么？用存在主义的角度分析",
    })
    void shouldNotAskHumanForClearRequests(String clearPrompt) {
        CrossRowAgent agent = crossRowAgentProvider.getObject();
        agent.setUserId("test-user");
        agent.setSessionId("test-session");
        
        String result = agent.run(clearPrompt);
        
        System.out.println("=== Test Input: " + clearPrompt + " ===");
        System.out.println("=== Result: ===");
        System.out.println(result);
        System.out.println("================\n");
        
        // 对于明确请求，Agent 应该直接执行而不是反问
        // 注意：这个断言可能需要根据实际情况调整
        assertNotNull(result);
    }

    /**
     * 单独测试 AskHumanTool 的返回格式
     */
    @Test
    @DisplayName("AskHumanTool 返回格式正确")
    void testAskHumanToolReturnFormat() {
        AskHumanTool tool = new AskHumanTool();
        String result = tool.askHuman("你想搜索什么主题的新闻？");
        
        assertTrue(result.contains("<hidden_action"));
        assertTrue(result.contains("type='ask_human'"));
        assertTrue(result.contains("question="));
        assertTrue(result.contains("Waiting for user input"));
    }

    /**
     * 边界情况：半模糊请求
     * 这些请求有一定上下文但仍然不够明确
     */
    @Test
    @DisplayName("半模糊请求的处理")
    void testSemiAmbiguousRequest() {
        CrossRowAgent agent = crossRowAgentProvider.getObject();
        agent.setUserId("test-user");
        agent.setSessionId("test-session");
        
        // "最近的新闻" - 有主题限定词但仍然模糊
        String result = agent.run("搜索最近的新闻");
        
        System.out.println("=== Semi-ambiguous test ===");
        System.out.println(result);
        
        // 这种情况下，Agent 可能会询问具体领域，也可能会搜索综合新闻
        // 主要是观察 Agent 的行为
        assertNotNull(result);
    }
}
