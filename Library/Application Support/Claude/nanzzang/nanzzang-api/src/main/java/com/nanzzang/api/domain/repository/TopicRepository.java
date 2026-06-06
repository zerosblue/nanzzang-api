package com.nanzzang.api.domain.repository;

import com.nanzzang.api.domain.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {
    Page<Topic> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Topic> findAllByOrderByHotScoreDesc(Pageable pageable);
    Page<Topic> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);
    List<Topic> findByExpiresAtBeforeAndIsClosedFalse(LocalDateTime now);

    @Query("SELECT t FROM Topic t ORDER BY " +
           "CASE WHEN t.isClosed = false AND t.expiresAt > :now THEN 0 ELSE 1 END ASC, " +
           "t.hotScore DESC")
    Page<Topic> findAllOrderByActiveFirstThenHotScore(@Param("now") LocalDateTime now, Pageable pageable);

    Page<Topic> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);

    @Modifying
    @Query("UPDATE Topic t SET t.viewCount = t.viewCount + :delta WHERE t.id = :id")
    void incrementViewCount(@Param("id") UUID id, @Param("delta") long delta);
}
