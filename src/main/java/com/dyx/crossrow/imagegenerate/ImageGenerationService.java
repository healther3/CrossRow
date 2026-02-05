package com.dyx.crossrow.imagegenerate;

import com.google.cloud.aiplatform.v1.Modality;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.springframework.stereotype.Service;

@Service
public class ImageGenerationService {
    public class TextToImage {
        public String generateImage(String prompt) {
            // 1. 初始化客户端 (建议做成 Bean 单例，不要每次请求都 new)
            try (Client client = new Client()) {

                // 2. 配置：明确要求返回 IMAGE
                GenerateContentConfig config = GenerateContentConfig.builder()
                        .responseModalities("TEXT", "IMAGE")
                        .build();

                // 3. 调用 Gemini 2.5 Flash Image
                GenerateContentResponse response = client.models.generateContent(
                        "gemini-2.5-flash-image",
                        prompt,
                        config
                );

                // 4. 解析结果并上传
                for (Part part : response.parts()) {
                    // 检查是否有二进制图片数据
                    if (part.text().isPresent()) {
                        System.out.println(part.text().get());
                    } else if(part.inlineData().isPresent()) {
                        byte[] imageBytes = part.inlineData().get().data().get();
                        // 关键：这里不要直接保存文件，而是上传到对象存储或返回 URL
                       // return uploadToStorage(imageBytes);
                    }
                }
                throw new RuntimeException("生成失败，未收到图片数据");
            } catch (Exception e) {
                throw new RuntimeException("生图服务异常: " + e.getMessage());
            }
        }

//        private String uploadToStorage(byte[] data) {
//            // 模拟上传，实际请接入 MinIO 或 Google Cloud Storage
//            String fileName = "gen_" + UUID.randomUUID() + ".png";
//            // Files.write(Paths.get("static/images/" + fileName), data);
//            return "http://localhost:8080/images/" + fileName;
//        }
    }
}
