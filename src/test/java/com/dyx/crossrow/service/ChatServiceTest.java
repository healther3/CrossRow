package com.dyx.crossrow.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
class ChatServiceTest {

    @Resource
    private ChatService crossRowApp;
    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // first conversation
        String message = "你是谁？";
        String answer = crossRowApp.doChat(message, chatId, "admin");
        Assertions.assertNotNull(answer);
        // second conversation
         message = "我每天上学很累";
         answer = crossRowApp.doChat(message, chatId,"admin");
         Assertions.assertNotNull(answer);
        // third conversation
         message = "你还记得我为什么很累吗";
         answer = crossRowApp.doChat(message, chatId,"admin");
        Assertions.assertNotNull(answer);
    }


    @Test
    void doChatReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "我每天上学很累，家里人对我要求太高";
        ChatService.PainReport answer = crossRowApp.doChatReport(message, chatId, "admin");
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "西西弗斯好累。";
        String answer = crossRowApp.doChatWithRag(message, chatId, "admin");
        Assertions.assertNotNull(answer);
    }

    @Test
    void chatWithTools() {
        String chatId = UUID.randomUUID().toString();
        String message = "去查一下长颈鹿长什么样，然后给我画一张";
        String answer = crossRowApp.doChatWithTools(message, chatId, "admin",true, true);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMCP() {
        String chatId = UUID.randomUUID().toString();
        String message = "查找五张跟康德有关的文章";
        String answer = crossRowApp.doChatWithMCP(message, chatId, "admin");
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithCrossRowAgent() {
        String chatId = UUID.randomUUID().toString();
        String message =  "这是一个包含三个步骤的任务，请一步步来： 第一步，去网上查一下‘赛博朋克2077’这款游戏的标志性色彩搭配是什么； 第二步，再去查一下‘赛博哥特（CyberGoth）’这种亚文化的典型穿搭特征； 第三步，结合这两个搜索结果，给我画一张‘一个穿着赛博哥特服饰的女孩站在赛博朋克小巷里’的电影级CG图。 图片生成成功后，请调用 terminate 工具结束任务，理由写‘画图任务圆满完成’。";
        String answer = crossRowApp.doChatWithCrossRowAgent(message, chatId, "admin");
        Assertions.assertNotNull(answer);
    }
}