package com.dyx.crossrow.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static java.lang.Thread.sleep;

@SpringBootTest
@ActiveProfiles("test")
class CrossRowAgentTest {
    @Resource
    CrossRowAgent crossRowAgent;

    @Test
    public void run(){
        String ans = crossRowAgent.run("What is the meaning of life?");
        Assertions.assertNotNull(ans);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }
}