package com.dyx.crossrow.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 图像生成模型配置属性
 * 用于配置 Nano Banana (Gemini 2.5 Flash Image) 等图像生成模型
 */
@Data
@ConfigurationProperties(prefix = "app.image-model")
@Validated
@Component
public class ImageModelProperties {

    /**
     * 模型名称
     * 可选值：gemini-2.5-flash-image (Nano Banana), gemini-3-pro-image-preview (Nano Banana Pro)
     */
    @NotBlank(message = "模型名称不能为空")
    private String model = "gemini-2.5-flash-image";

    /**
     * GCP 项目 ID
     */
    @NotBlank(message = "项目ID不能为空")
    private String projectId;

    /**
     * GCP 区域
     */
    @NotBlank(message = "区域不能为空")
    private String location = "us-central1";

    /**
     * 图像宽高比
     * 可选值：1:1, 2:3, 3:2, 3:4, 4:3, 4:5, 5:4, 9:16, 16:9, 21:9
     */
    private String aspectRatio = "1:1";

    /**
     * 图像分辨率 (仅 Nano Banana Pro 支持 2K/4K)
     * 可选值：1K, 2K, 4K
     */
    private String imageSize = "1K";

    /**
     * 生成图片保存目录
     */
    private String outputDir = "generated-images";

    /**
     * 是否启用 Google Search Grounding (仅 Pro 版支持)
     */
    private boolean googleSearchGrounding = false;
}
