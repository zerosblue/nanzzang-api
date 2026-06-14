package com.nanzzang.api.domain.repository;

import com.nanzzang.api.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByNickname(String nickname);
    Page<User> findByRoleNotOrderByCreatedAtDesc(String role, Pageable pageable);
    long countByRoleNot(String role);

    @Modifying
    @Query("UPDATE User u SET u.lastVisitedAt = :now WHERE u.id = :id")
    void updateLastVisitedAt(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
