package com.dyx.crossrow.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class FileUploadService {
    @Value("${google.cloud.storage.bucket-name}")
    private String bucketName;
    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    /**
     * 上传用户自定义背景图
     */
    public String uploadBackground(MultipartFile file) throws IOException {
        // 1. 校验文件是否为空
        if (file.isEmpty()) {
            throw new RuntimeException("empty file");
        }

        // 2. 提取原始文件名和后缀 (比如 .jpg, .png)
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 3. 生成全局唯一的 UUID 文件名，防止不同用户传了同名文件互相覆盖
        // 例如：backgrounds/a1b2c3d4-e5f6...7890.jpg
        String uniqueFileName = "backgrounds/" + UUID.randomUUID().toString() + extension;

        // 4. 构建 GCS 的 BlobInfo (文件元数据)
        BlobId blobId = BlobId.of(bucketName, uniqueFileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType()) // 设置 MIME 类型，这样浏览器会直接显示图片而不是触发下载
                .build();

        // 5. 执行物理上传
        storage.create(blobInfo, file.getBytes());

        // 6. 拼装公开访问的 URL 并返回
        // GCS 的标准公开访问格式是：https://storage.googleapis.com/{桶名}/{文件名}
        return String.format("https://storage.googleapis.com/%s/%s", bucketName, uniqueFileName);
    }
}
