package com.dyx.crossrow.imagegenerate;

import com.dyx.crossrow.properties.ImageModelProperties;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class ImageGenerationService {
    private final Client genAiClient;
    private final ImageModelProperties imageModelProperties;

    public ImageGenerationService(Client genAiClient, ImageModelProperties imageModelProperties) {
        this.genAiClient = genAiClient;
        this.imageModelProperties = imageModelProperties;
    }
        public String generateImage(String prompt) {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseModalities("TEXT", "IMAGE")
                    .imageConfig(ImageConfig.builder()
                            .aspectRatio(imageModelProperties.getAspectRatio())
                            .imageSize(imageModelProperties.getImageSize())
                            .build())
                    .build();

            GenerateContentResponse response = genAiClient.models.generateContent(
                    imageModelProperties.getModel(),
                    prompt,
                    config
            );

            for (Part part : response.parts()) {
                if (part.inlineData().isPresent()) {
                    var blob = part.inlineData().get();
                    if (blob.data().isPresent()) {
                        return saveImage(blob.data().get());
                    }
                }
            }
            return null;
        }
    private String saveImage(byte[] data) {
        try {
            // 检查路径
            Path dir = Paths.get(imageModelProperties.getSavePath());
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            // 创建唯一路径
            String fileName = "gemini_" + System.currentTimeMillis() + ".png";
            Path filePath = dir.resolve(fileName);
            //保存
            Files.write(filePath, data);
            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("图片保存失败", e);
        }
    }
}
