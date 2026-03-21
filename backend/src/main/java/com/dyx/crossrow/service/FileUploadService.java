package com.dyx.crossrow.service;

import com.dyx.crossrow.model.dto.MediaContentDTO;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileUploadService {
    @Value("${google.cloud.storage.bucket-name}")
    private String bucketName;
    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"
    );

    /**
     * 上传用户自定义背景图
     */
    public String uploadBackground(MultipartFile file) throws IOException {
        return uploadToGcs(file, "backgrounds");
    }

    /**
     * 上传图片用于聊天
     * @return MediaContentDTO 包含 GCS URL 和 mimeType
     */
    public MediaContentDTO uploadImageForChat(MultipartFile file, String userId) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedImageType(contentType)) {
            throw new IllegalArgumentException("Unsupported image type: " + contentType);
        }

        String folder = "chat-media/" + userId + "/image";
        String gcsUrl = uploadToGcs(file, folder);

        MediaContentDTO dto = new MediaContentDTO();
        dto.setType("image");
        dto.setMimeType(contentType);
        dto.setUrl(gcsUrl);
        return dto;
    }

    /**
     * 批量上传图片
     */
    public java.util.List<MediaContentDTO> uploadMultipleImagesForChat(
            MultipartFile[] files, String userId) throws IOException {
        java.util.List<MediaContentDTO> results = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                results.add(uploadImageForChat(file, userId));
            }
        }
        return results;
    }

    private String uploadToGcs(MultipartFile file, String folder) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("empty file");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String uniqueFileName = folder + "/" + UUID.randomUUID().toString() + extension;

        BlobId blobId = BlobId.of(bucketName, uniqueFileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        storage.create(blobInfo, file.getBytes());
        log.info("Uploaded image to GCS: {}", uniqueFileName);

        return String.format("https://storage.googleapis.com/%s/%s", bucketName, uniqueFileName);
    }

    public boolean isAllowedImageType(String contentType) {
        return ALLOWED_IMAGE_TYPES.contains(contentType);
    }
}
