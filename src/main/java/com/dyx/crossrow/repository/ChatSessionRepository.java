package com.dyx.crossrow.repository;

import com.dyx.crossrow.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<ChatSession> findByIdAndUserId(String id, String userId);

    boolean existsByIdAndUserId(String id, String userId);

    List<ChatSession> findByUserIdAndFolderIdOrderByUpdatedAtDesc(String userId, String folderId);

    List<ChatSession> findByUserIdAndFolderIdIsNullOrderByUpdatedAtDesc(String userId);
}