package com.dyx.crossrow.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChatSessionDTO {
    private String id;
    private String title;
    private String folderId;
    private LocalDateTime updatedAt;
}