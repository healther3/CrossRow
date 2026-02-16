package com.dyx.crossrow.service;

import com.dyx.crossrow.model.BackGroundMode;
import com.dyx.crossrow.model.CityCoordinates;
import com.dyx.crossrow.properties.ImageModelProperties;
import com.dyx.crossrow.repository.CityRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageGenerationService {

    private final Client genAiClient;
    private final ImageModelProperties imageModelProperties;
    private final CityRepository cityRepository;
   @Value("${google.maps.api-key}")
    private String apiKey;
    private List<CityCoordinates> cityList;

    private final Random random = new Random();

    public ImageGenerationService(Client genAiClient, ImageModelProperties imageModelProperties,
                                  CityRepository cityRepository) {
        this.genAiClient = genAiClient;
        this.imageModelProperties = imageModelProperties;
        this.cityRepository = cityRepository;
        cityList = cityRepository.findCities();
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
        if (cityList == null || cityList.isEmpty()) {
            return "48.8584,2.2945"; // set a default place you like
        }
// 1. 随机选一个城市
        CityCoordinates city = cityList.get(random.nextInt(cityList.size()));

        // 2. 添加随机偏移 (Jitter)
        // 0.01 度大约是 1.11 公里。
        // 我们在 +/- 0.03 度 (约3km) 范围内随机，这样既保证在城市里，又保证每次景色不同
        double latOffset = (random.nextDouble() * 0.01) - 0.03;
        double lngOffset = (random.nextDouble() * 0.01) - 0.03;

        double finalLat = city.getLat() + latOffset;
        double finalLng = city.getLng() + lngOffset;

        return String.format("%.6f,%.6f", finalLat, finalLng);
    }

    private String buildGoogleUrl(String location) {
        return String.format(
                "https://maps.googleapis.com/maps/api/streetview?size=640x640&location=%s&fov=120&heading=%d&pitch=10&radius=1000&source=outdoor&key=%s",
                location,
                new Random().nextInt(360),
                apiKey
        );
    }


}
