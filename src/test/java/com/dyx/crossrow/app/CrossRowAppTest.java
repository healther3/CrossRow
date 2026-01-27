package com.dyx.crossrow.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CrossRowAppTest {

    @Resource
    private CrossRowApp crossRowApp;
    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // first conversation
        String message = "你是谁？";
        String answer = crossRowApp.doChat(message, chatId, "admin");
        Assertions.assertNotNull(answer);
        // second conversation
         message = "我每天上学很累";
         answer = crossRowApp.doChat(message, chatId,"张三");
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
        CrossRowApp.PainReport answer = crossRowApp.doChatReport(message, chatId, "admin");
        Assertions.assertNotNull(answer);
    }
}