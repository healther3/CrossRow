package com.dyx.crossrow.service;

import cn.hutool.core.io.resource.ClassPathResource;
import com.dyx.crossrow.model.BackGroundMode;
import com.dyx.crossrow.model.CityCoordinates;
import com.dyx.crossrow.properties.ImageModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

@Service
public class ImageGenerationService {

    private final Client genAiClient;
    private final ImageModelProperties imageModelProperties;
    @Value("${google.maps.api-key}")
    private String apiKey;
    private List<CityCoordinates> cityList;

    private final Random random = new Random();

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

    public String generateWorldView(Double lat, Double lng, BackGroundMode mode) {
        String location;

        switch (mode) {
            case USER:
                // 物理坐标模式：必须校验参数
                if (lat == null || lng == null) {
                    // 如果用户选了物理坐标但没传，降级为伪随机
                    location = generatePureRandomLocation();
                } else {
                    location = lat + "," + lng;
                }
                break;

            case RANDOM:
                // 纯随机模式：数学随机
                location = generatePureRandomLocation();
                break;
                default:
                // 默认伪随机
                location = generatePureRandomLocation();
                break;
        }

        return buildGoogleUrl(location);
    }

    private String generatePureRandomLocation() {
        double randomLat = -90 + (180 * random.nextDouble());
        double randomLng = -180 + (360 * random.nextDouble());

        // 保留6位小数，格式化为字符串
        return String.format("%.6f,%.6f", randomLat, randomLng);
    }

    private String buildGoogleUrl(String location) {
        return String.format(
                "https://maps.googleapis.com/maps/api/streetview?size=640x640&location=%s&fov=120&pitch=10&source=outdoor&key=%s",
                location,
                apiKey
        );
    }


}
