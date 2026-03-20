package com.dyx.crossrow.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaContentDTO {
    private String type;      // "image", "audio", "video"
    private String mimeType;  // "image/png", "audio/mp3" 等
    private String data;      // Base64 编码或 URL
}