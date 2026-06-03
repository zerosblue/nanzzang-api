package com.nanzzang.api.domain.repository;

import com.nanzzang.api.domain.SpectatorVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpectatorVoteRepository extends JpaRepository<SpectatorVote, UUID> {
    Optional<SpectatorVote> findByTopicIdAndUserId(UUID topicId, UUID userId);
}
