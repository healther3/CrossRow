package com.dyx.crossrow.controller;

import com.dyx.crossrow.model.ChatFolder;
import com.dyx.crossrow.model.dto.FolderWithSessionsDTO;
import com.dyx.crossrow.service.ChatFolderService;
import com.dyx.crossrow.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
public class ChatFolderController {

    private final ChatFolderService folderService;

    @GetMapping
    public List<FolderWithSessionsDTO> listFolders() {
        String userId = UserContext.getUserId();
        return folderService.getUserFoldersWithSessions(userId);
    }

    @PostMapping
    public ChatFolder createFolder(@RequestParam String name) {
        String userId = UserContext.getUserId();
        return folderService.createFolder(userId, name);
    }

    @PutMapping("/{folderId}/name")
    public void renameFolder(@PathVariable String folderId, @RequestParam String name) {
        String userId = UserContext.getUserId();
        folderService.renameFolder(folderId, userId, name);
    }

    @DeleteMapping("/{folderId}")
    public void deleteFolder(@PathVariable String folderId) {
        String userId = UserContext.getUserId();
        folderService.deleteFolder(folderId, userId);
    }

    @PutMapping("/sessions/{sessionId}/move")
    public void moveSession(@PathVariable String sessionId, @RequestParam String folderId) {
        String userId = UserContext.getUserId();
        folderService.moveSessionToFolder(sessionId, folderId, userId);
    }
}
