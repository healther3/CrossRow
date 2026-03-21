package com.dyx.crossrow.service;

import com.dyx.crossrow.model.ChatFolder;
import com.dyx.crossrow.model.ChatSession;
import com.dyx.crossrow.model.dto.ChatSessionDTO;
import com.dyx.crossrow.model.dto.FolderWithSessionsDTO;
import com.dyx.crossrow.repository.ChatFolderRepository;
import com.dyx.crossrow.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatFolderService {

    private static final String DEFAULT_FOLDER_NAME = "default";

    private final ChatFolderRepository folderRepository;
    private final ChatSessionRepository sessionRepository;

    /**
     * 获取用户所有文件夹及其对话列表
     */
    public List<FolderWithSessionsDTO> getUserFoldersWithSessions(String userId) {
        ensureDefaultFolder(userId);

        List<ChatFolder> folders = folderRepository.findByUserIdOrderBySortOrderAsc(userId);
        List<ChatSession> allSessions = sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        return folders.stream().map(folder -> {
            List<ChatSessionDTO> sessions = allSessions.stream()
                    .filter(s -> folder.getIsDefault()
                            ? (s.getFolderId() == null || s.getFolderId().equals(folder.getId()))
                            : folder.getId().equals(s.getFolderId()))
                    .map(this::toSessionDTO)
                    .toList();

            return new FolderWithSessionsDTO(
                    folder.getId(), folder.getName(), folder.getIsDefault(),
                    folder.getSortOrder(), sessions);
        }).toList();
    }

    /**
     * 确保用户有默认文件夹，如果没有则创建
     */
    public ChatFolder ensureDefaultFolder(String userId) {
        return folderRepository.findByUserIdAndIsDefaultTrue(userId)
                .orElseGet(() -> {
                    ChatFolder defaultFolder = new ChatFolder();
                    defaultFolder.setUserId(userId);
                    defaultFolder.setName(DEFAULT_FOLDER_NAME);
                    defaultFolder.setIsDefault(true);
                    defaultFolder.setSortOrder(0);
                    log.info("Created default folder for user: {}", userId);
                    return folderRepository.save(defaultFolder);
                });
    }

    /**
     * 创建文件夹
     */
    public ChatFolder createFolder(String userId, String name) {
        // 获取当前最大 sortOrder
        List<ChatFolder> folders = folderRepository.findByUserIdOrderBySortOrderAsc(userId);
        int maxSortOrder = folders.stream()
                .mapToInt(ChatFolder::getSortOrder)
                .max()
                .orElse(0);

        ChatFolder folder = new ChatFolder();
        folder.setUserId(userId);
        folder.setName(name);
        folder.setIsDefault(false);
        folder.setSortOrder(maxSortOrder + 1);

        log.info("Created folder '{}' for user: {}", name, userId);
        return folderRepository.save(folder);
    }

    /**
     * 删除文件夹（对话移到默认文件夹）
     */
    public void deleteFolder(String folderId, String userId) {
        ChatFolder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Folder doesn't exist."));

        if (folder.getIsDefault()) {
            throw new IllegalArgumentException("You can not remove default folder");
        }

        // 将该文件夹下的对话移到默认文件夹（设为 null，会自动归入默认）
        List<ChatSession> sessions = sessionRepository.findByUserIdAndFolderIdOrderByUpdatedAtDesc(userId, folderId);
        for (ChatSession session : sessions) {
            session.setFolderId(null);
        }
        sessionRepository.saveAll(sessions);

        folderRepository.delete(folder);
        log.info("Deleted folder '{}' for user: {}, moved {} sessions to default", folder.getName(), userId, sessions.size());
    }

    /**
     * 重命名文件夹
     */
    public void renameFolder(String folderId, String userId, String newName) {
        ChatFolder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Folder doesn't exist."));

        folder.setName(newName);
        folderRepository.save(folder);
        log.info("Renamed folder to '{}' for user: {}", newName, userId);
    }

    /**
     * 移动对话到指定文件夹
     */
    public void moveSessionToFolder(String sessionId, String folderId, String userId) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Session doesn't exist."));

        // 校验目标文件夹存在且属于该用户
        if (!folderRepository.existsByIdAndUserId(folderId, userId)) {
            throw new IllegalArgumentException("Target folder doesn't exist.");
        }

        session.setFolderId(folderId);
        sessionRepository.save(session);
        log.info("Moved session {} to folder {} for user: {}", sessionId, folderId, userId);
    }

    private ChatSessionDTO toSessionDTO(ChatSession session) {
        return new ChatSessionDTO(
                session.getId(),
                session.getTitle(),
                session.getFolderId(),
                session.getUpdatedAt()
        );
    }
}
