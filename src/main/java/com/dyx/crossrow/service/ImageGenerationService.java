package com.dyx.crossrow.service;

import com.dyx.crossrow.model.BackGroundMode;
import com.dyx.crossrow.model.CityCoordinates;
import com.dyx.crossrow.properties.ImageModelProperties;
import com.dyx.crossrow.repository.CityRepository;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ImageGenerationService {

    private final Client genAiClient;
    private final ImageModelProperties imageModelProperties;
    private final CityRepository cityRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    public static final int MAX_RETRIES = 3;

   @Value("${google.maps.api-key}")
    private String apiKey;
   @Value("${google.cloud.storage.bucket-name}")
   private String bucketName;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();
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
            String uniqueFileName = "ai-generated/gemini_" + UUID.randomUUID().toString() + ".png";

            BlobId blobId = BlobId.of(bucketName, uniqueFileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("image/png") // Gemini 生成的默认是 PNG
                    .build();

            storage.create(blobInfo, data);
            return String.format("https://storage.googleapis.com/%s/%s", bucketName, uniqueFileName);

        } catch (Exception e){
            throw new RuntimeException("upload to GCS failed", e);
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
            case URBAN:
                    location = getRandomLocationByType("URBAN");
                    break;
            case NATURE:
                location = getRandomLocationByType("NATURE");
                break;
            case LANDMARK:
                location = getRandomLocationByType("LANDMARK");
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
            return "default_location"; // set a default place you like
        }
// 1. 随机选一个城市
        CityCoordinates city = cityList.get(random.nextInt(cityList.size()));
        return getValidStreetViewLocation(city, 0.03);
    }

    private String buildGoogleUrl(String location) {
        if(location.equals("default_location"))
        {
            return "/api/images/default_BG.jpg";
        }
        return String.format(
                "https://maps.googleapis.com/maps/api/streetview?size=640x640&location=%s&fov=90&heading=%d&pitch=0&radius=1000&source=outdoor&key=%s",
                location,
                new Random().nextInt(360),
                apiKey
        );
    }

    private String getRandomLocationByType(String type) {
        // avoid empty
        if (cityList == null || cityList.isEmpty()) {
            return "default_location"; // set a default place you like
        }

        // get a pool of all the target type city
        List<CityCoordinates> filteredList = cityList.stream()
                .filter(city -> type.equals(city.getType()))
                .toList();

        // avoid empty
        if (filteredList.isEmpty()) {
            filteredList = cityList;
        }

        CityCoordinates city = filteredList.get(random.nextInt(filteredList.size()));

       return getValidStreetViewLocation(city, 0.002);
    }

    /**
     * 核心校验逻辑：带有重试机制的坐标生成
     * @param targetCity 目标城市
     * @param jitterRange 偏移范围 (例如 0.03 代表约 3km)
     */
    private String getValidStreetViewLocation(CityCoordinates targetCity, double jitterRange) {

        for (int i = 0; i < MAX_RETRIES; i++) {
            // 修复数学 Bug：确保偏移量在 [-jitterRange, +jitterRange] 之间均匀分布
            double latOffset = (random.nextDouble() * 2 * jitterRange) - jitterRange;
            double lngOffset = (random.nextDouble() * 2 * jitterRange) - jitterRange;

            double finalLat = targetCity.getLat() + latOffset;
            double finalLng = targetCity.getLng() + lngOffset;
            String loc = String.format("%.6f,%.6f", finalLat, finalLng);

            // 构建 Metadata API URL，参数必须与 buildGoogleUrl 完全一致 (radius=1000, source=outdoor)
            String metaUrl = String.format(
                    "https://maps.googleapis.com/maps/api/streetview/metadata?location=%s&radius=1000&source=outdoor&key=%s",
                    loc, apiKey
            );

            try {
                // 发送 GET 请求检查是否有街景
                String response = restTemplate.getForObject(metaUrl, String.class);
                if (response != null && response.contains("\"status\" : \"OK\"")) {
                    System.out.println("[Debug] 找到有效街景，重试次数: " + i);
                    return loc; // 校验成功，返回有效坐标
                }
            } catch (Exception e) {
                System.err.println("[Debug] Metadata 校验请求失败: " + e.getMessage());
            }
        }

        System.out.println("[Debug] 达到最大重试次数，降级为默认背景");
        return "default_location"; // 如果试了5次还是海里/深山，返回默认背景
    }
}
