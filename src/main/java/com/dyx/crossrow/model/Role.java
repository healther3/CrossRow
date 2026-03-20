package com.dyx.crossrow.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "roles")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false, length = 50)
    private String name;  // USER, ADMIN

    @Column(name = "display_name", length = 100)
    private String displayName;  // 普通用户, 管理员

    @Column(length = 500)
    private String description;

    @Column(name = "daily_chat_limit", nullable = false)
    private Integer dailyChatLimit = 100;  // 每日普通对话次数限制

    @Column(name = "daily_agent_limit", nullable = false)
    private Integer dailyAgentLimit = 5;  // 每日 Agent 调用次数限制

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
