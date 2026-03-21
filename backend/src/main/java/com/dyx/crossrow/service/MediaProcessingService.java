package com.dyx.crossrow.service;

import com.dyx.crossrow.model.dto.MediaContentDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MediaProcessingService {

    /**
     * 将 DTO 中的图片内容转换为 Spring AI 的 Media 对象
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
     * 构建带图片的 UserMessage
     * @param text 文本消息
     * @param mediaContents 图片内容列表（可为 null）
     * @return UserMessage（纯文本或带图片）
     */
    public UserMessage buildUserMessage(String text, List<MediaContentDTO> mediaContents) {
        List<Media> mediaList = processMediaContents(mediaContents);

        if (mediaList.isEmpty()) {
            return new UserMessage(text);
        }

        log.info("Building UserMessage with {} image(s)", mediaList.size());
        return UserMessage.builder()
                .text(text)
                .media(mediaList)
                .build();
    }

    /**
     * 检查是否包含图片
     */
    public boolean hasMedia(List<MediaContentDTO> mediaContents) {
        return mediaContents != null && !mediaContents.isEmpty();
    }

    private Media convertToMedia(MediaContentDTO dto) {
        if (dto.getUrl() == null || dto.getUrl().isEmpty()) {
            log.warn("Invalid media content: no url provided");
            return null;
        }

        MimeType mimeType = MimeTypeUtils.parseMimeType(dto.getMimeType());
        log.debug("Creating Media from GCS URL: {}", dto.getUrl());
        return new Media(mimeType, URI.create(dto.getUrl()));
    }
}
