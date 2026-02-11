package com.dyx.crossrow.tool;

import com.dyx.crossrow.service.ImageGenerationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

public class ImageGenerationTool {

    private final ImageGenerationService imageGenerationService;

    public ImageGenerationTool(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }
    @Tool(description = "Generate images based on user descriptions. Use this tool when the user wants to see memes, comics, or photos.")
    public String generateImage(@ToolParam(description = "A detailed English description of the scene, including the subject, actions, and environment.")
                                    String prompt,
                                @ToolParam(description = "Image style, such as MEME, COMIC, or REALISTIC", required = false)
                                String style) {

        if (style == null) {
            style = "CINEMATIC";
        }

        System.out.println("Tool 被调用: " + prompt + " | 风格: " + style);

        try {
            String imageUrl = imageGenerationService.generateImage(prompt + ", " + style + " style");
            return "图片已生成，地址: " + imageUrl;
        } catch (Exception e) {
            return "绘图失败: " + e.getMessage();
        }
    };
}
