package com.dyx.crossrow.imagegenerate;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"local", "test"})  // local 加载配置，test 禁用 DocumentIndexer
class ImageGenerationServiceTest {
    @Resource
    ImageGenerationService imageGenerationService;
    
    @Test
    void generateImage() {
        String prompt = "生成一个银色胸甲绿色头发的老者";
        String result = imageGenerationService.generateImage(prompt);
        System.out.println("生成的图片路径: " + result);
        assertNotNull(result, "应该返回图片路径");
    }
}