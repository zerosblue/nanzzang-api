package com.nanzzang.api.domain.repository;

import com.nanzzang.api.domain.Participation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipationRepository extends JpaRepository<Participation, UUID> {
    Optional<Participation> findByTopicIdAndUserId(UUID topicId, UUID userId);
    int countByTopicIdAndTeamSide(UUID topicId, String teamSide);
    List<Participation> findByUserId(UUID userId);
    List<Participation> findByTopicId(UUID topicId);
}
