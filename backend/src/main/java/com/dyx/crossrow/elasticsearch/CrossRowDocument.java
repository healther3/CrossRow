package com.dyx.crossrow.elasticsearch;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ES 文档记录实体
 *
 * 与 ES 索引的 Mapping 一一对应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CrossRowDocument {

    /**
     * 文档唯一标识
     */
    private String id;

    /**
     * 文档内容（用于全文检索）
     */
    private String content;

    /**
     * 向量嵌入（用于语义检索）
     */
    private List<Float> embedding;

    /**
     * 关键词列表（用于精确匹配）
     */
    private List<String> keywords;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "UTC")
    private Instant createdAt;
}
