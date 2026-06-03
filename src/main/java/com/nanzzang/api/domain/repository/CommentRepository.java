package com.nanzzang.api.domain.repository;

import com.nanzzang.api.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByTopicIdAndParentIsNullOrderByCreatedAtDesc(UUID topicId);
    int countByTopicId(UUID topicId);
}
