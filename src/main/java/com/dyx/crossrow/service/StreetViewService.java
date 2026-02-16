package com.dyx.crossrow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Service
public class StreetViewService {

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
