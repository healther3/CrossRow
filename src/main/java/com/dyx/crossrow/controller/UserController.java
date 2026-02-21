package com.dyx.crossrow.controller;

import com.dyx.crossrow.model.User;
import com.dyx.crossrow.repository.UserRepository;
import com.dyx.crossrow.service.FileUploadService;
import com.dyx.crossrow.utils.UserContext;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserRepository userRepository;

    @Resource
    private FileUploadService fileUploadService;

    @GetMapping("/background")
    public ResponseEntity<String> getBackground() {
        // 1. 从“工作牌”中获取当前是谁在发请求
        String userId = UserContext.getUserId();

        // 2. 去数据库查这个人的信息
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("unknown user"));

        // 3. 核心逻辑：判断并返回
        if (user.getCustomBackgroundUrl() != null && !user.getCustomBackgroundUrl().isEmpty()) {
            // 如果用户传过自定义背景，返回 GCS 的 https://... 链接
            return ResponseEntity.ok(user.getCustomBackgroundUrl());
        } else {
            // 如果用户没传过，返回本地的默认图片路径
            return ResponseEntity.ok("/api/images/default_BG.jpg");
        }
    }

    @PostMapping("/background")
    public ResponseEntity<String> uploadBackground(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 拿到当前用户 ID
            String userId = UserContext.getUserId();

            // 2. 调用刚才写好的 GCS 服务，把文件传到 Google 云端
            String gcsUrl = fileUploadService.uploadBackground(file);

            // 3. 把返回的 GCS 链接，存到这个用户的数据库记录里（极简覆盖）
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("unknown user"));
            user.setCustomBackgroundUrl(gcsUrl);
            userRepository.save(user);

            // 4. 返回成功信息和新的 URL
            return ResponseEntity.ok(gcsUrl);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("upload failed: " + e.getMessage());
        }
    }
}
