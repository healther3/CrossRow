package com.dyx.crossrow.controller;

import com.dyx.crossrow.model.dto.MediaContentDTO;
import com.dyx.crossrow.service.FileUploadService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Media upload controller for handling file uploads (images, audio, etc.)
 */
@RestController
@RequestMapping("/media")
public class MediaController {

    @Resource
    private FileUploadService fileUploadService;

    /**
     * Upload images to GCS for chat
     * @param files image files to upload (PNG, JPEG, GIF, WebP)
     * @param userId user id
     * @return list of MediaContentDTO with GCS URLs
     */
    @PostMapping("/image/upload")
    public ResponseEntity<List<MediaContentDTO>> uploadImages(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("userId") String userId) throws IOException {
        List<MediaContentDTO> imageList = fileUploadService.uploadMultipleImagesForChat(files, userId);
        return ResponseEntity.ok(imageList);
    }

    /**
     * Upload a single image to GCS for chat
     * @param file image file to upload (PNG, JPEG, GIF, WebP)
     * @param userId user id
     * @return MediaContentDTO with GCS URL
     */
    @PostMapping("/image/upload/single")
    public ResponseEntity<MediaContentDTO> uploadSingleImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) throws IOException {
        MediaContentDTO result = fileUploadService.uploadImageForChat(file, userId);
        return ResponseEntity.ok(result);
    }
}
