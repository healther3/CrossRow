package com.dyx.crossrow.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaContentDTO {
    private String type;      // "image"
    private String mimeType;  // "image/png", "image/jpeg" 等
    private String url;       // GCS URL
}