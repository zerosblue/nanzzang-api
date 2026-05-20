package com.nanzzang.api.domain.repository;

import com.nanzzang.api.domain.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {
    Page<Topic> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Topic> findAllByOrderByHotScoreDesc(Pageable pageable);
    Page<Topic> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);
    List<Topic> findByExpiresAtBeforeAndIsClosedFalse(LocalDateTime now);
}
