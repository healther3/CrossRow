package com.dyx.crossrow.service;

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
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageGenerationService {

    private final Client genAiClient;
    private final ImageModelProperties imageModelProperties;
    @Value("${google.maps.api-key}")
    private String apiKey;

    private final Random random = new Random();

    // 预定义的高质量风景坐标池 (避免随机到大海)
    // 格式：纬度,经度
    private final List<String> SCENIC_SPOTS = Arrays.asList(
            "35.3606,138.7274",   // 富士山 (Mt. Fuji)
            "48.8584,2.2945",     // 埃菲尔铁塔 (Eiffel Tower)
            "40.6892,-74.0445",   // 自由女神像 (Statue of Liberty)
            "-33.8568,151.2153",  // 悉尼歌剧院 (Sydney Opera House)
            "27.1751,78.0421",    // 泰姬陵 (Taj Mahal)
            "37.9715,23.7257",    // 雅典卫城 (Acropolis of Athens)
            "29.9792,31.1342"     // 吉萨金字塔 (Pyramids of Giza)
    );

    public ImageGenerationService(Client genAiClient, ImageModelProperties imageModelProperties) {
        this.genAiClient = genAiClient;
        this.imageModelProperties = imageModelProperties;
    }
        public String generateImage(String prompt) {
            System.out.println("[Debug] 准备构建 Config...");
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseModalities("TEXT", "IMAGE")
                    .imageConfig(ImageConfig.builder()
                            .aspectRatio(imageModelProperties.getAspectRatio())
                            .imageSize(imageModelProperties.getImageSize())
                            .build())
                    .build();
            System.out.println("Config OK");
            try {
                GenerateContentResponse response = genAiClient.models.generateContent(
                        imageModelProperties.getModel(),
                        prompt,
                        config
                );
                System.out.println("[Debug] Google 响应成功");

                for (Part part : response.parts()) {
                    if (part.inlineData().isPresent()) {
                        var blob = part.inlineData().get();
                        if (blob.data().isPresent()) {
                            return saveImage(blob.data().get());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Debug] 发生异常: " + e.getMessage());
                throw e;
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

    public String generateWorldView(Double lat, Double lng) {
        String location;
        // 1. 判断逻辑：如果有坐标，用坐标；没坐标，随机挑一个
        if (lat != null && lng != null) {
            location = lat + "," + lng;
        } else {
            location = SCENIC_SPOTS.get(random.nextInt(SCENIC_SPOTS.size()));
        }

        // 2. 构建 URL
        // heading: 调整朝向 (可选，这里不传默认对着路)
        // pitch: 10 (稍微仰视，更有代入感)
        // fov: 120 (广角)
        return String.format(
                "https://maps.googleapis.com/maps/api/streetview?size=640x640&location=%s&fov=120&pitch=10&key=%s",
                location,
                apiKey
        );
    }
}
