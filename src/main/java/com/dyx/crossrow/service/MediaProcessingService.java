package com.dyx.crossrow.service;

import com.dyx.crossrow.model.dto.MediaContentDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MediaProcessingService {

    /**
     * 将 DTO 中的媒体内容转换为 Spring AI 的 Media 对象
     */
    public List<Media> processMediaContents(List<MediaContentDTO> mediaContents) {
        if (mediaContents == null || mediaContents.isEmpty()) {
            return Collections.emptyList();
        }

        return mediaContents.stream()
                .map(this::convertToMedia)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 构建带多模态内容的 UserMessage
     * @param text 文本消息
     * @param mediaContents 媒体内容列表（可为 null）
     * @return UserMessage（纯文本或多模态）
     */
    public UserMessage buildUserMessage(String text, List<MediaContentDTO> mediaContents) {
        List<Media> mediaList = processMediaContents(mediaContents);

        if (mediaList.isEmpty()) {
            return new UserMessage(text);
        }

        log.info("Building multimodal UserMessage with {} media items", mediaList.size());
        return  UserMessage.builder()
                .text(text)
                .media(mediaList)
                .build();
    }

    /**
     * 检查是否包含多模态内容
     */
    public boolean hasMedia(List<MediaContentDTO> mediaContents) {
        return mediaContents != null && !mediaContents.isEmpty();
    }

    private Media convertToMedia(MediaContentDTO dto) {
        MimeType mimeType = MimeTypeUtils.parseMimeType(dto.getMimeType());

        if (dto.getUrl() != null && !dto.getUrl().isEmpty()) {
            // URL 引用方式（GCS URL）
            log.debug("Creating Media from URL: {}", dto.getUrl());
            return new Media(mimeType, URI.create(dto.getUrl()));
        } else if (dto.getData() != null && !dto.getData().isEmpty()) {
            // Base64 数据方式
            log.debug("Creating Media from Base64 data, mimeType: {}", mimeType);
            byte[] bytes = Base64.getDecoder().decode(dto.getData());
            return new Media(mimeType, new ByteArrayResource(bytes));
        }

        log.warn("Invalid media content: no url or data provided");
        return null;
    }
}
