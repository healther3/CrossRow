package com.dyx.crossrow.controller;

import com.dyx.crossrow.model.BackGroundMode;
import com.dyx.crossrow.service.ImageGenerationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class ImageGenerationController {
    @Resource
    private ImageGenerationService imageGenerationService;

    /**
     * 获取风景背景图接口
     * * 用法 1 (随机): GET /crossrow/image/background
     * 用法 2 (指定): GET /crossrow/image/background?lat=31.23&lng=121.47&mode=USER/RANDOM
     */
    @GetMapping("/crossrow/image/background")
    public String getStreetView(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) BackGroundMode mode
    ) {
        // 调用 Service 获取 URL 字符串
        return imageGenerationService.generateWorldView(lat, lng, mode);
    }
}
