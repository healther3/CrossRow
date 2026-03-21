package com.dyx.crossrow.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FolderWithSessionsDTO {
    private String id;
    private String name;
    private Boolean isDefault;
    private Integer sortOrder;
    private List<ChatSessionDTO> sessions;  // 该文件夹下的对话列表
}