package com.dyx.crossrow.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalChatRequestDTO {
    private String message;
    private String chatId;
    private String userId;
    private List<MediaContentDTO> media;
}
