package com.dyx.crossrow.repository;

import com.dyx.crossrow.model.ChatFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatFolderRepository extends JpaRepository<ChatFolder, String> {
    List<ChatFolder> findByUserIdOrderBySortOrderAsc(String userId);

    Optional<ChatFolder> findByIdAndUserId(String id, String userId);

    Optional<ChatFolder> findByUserIdAndIsDefaultTrue(String userId);

    boolean existsByIdAndUserId(String id, String userId);
}