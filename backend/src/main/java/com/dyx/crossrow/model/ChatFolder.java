package com.dyx.crossrow.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_folders", indexes = {
        @Index(name = "idx_folder_user_id", columnList = "user_id"),
        @Index(name = "idx_folder_order", columnList = "sort_order")
})
public class ChatFolder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;  // 文件夹排序

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;  // 是否为默认文件夹

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (sortOrder == null) sortOrder = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
