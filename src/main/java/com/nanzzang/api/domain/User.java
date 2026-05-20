package com.nanzzang.api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(nullable = false)
    private boolean isPro = false;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'USER'")
    private String role = "USER"; // "USER", "ADMIN"

    @Builder
    public User(String email, String nickname, boolean isPro, String role) {
        this.email = email;
        this.nickname = nickname;
        this.isPro = isPro;
        if (role != null) {
            this.role = role;
        }
    }

    public void promoteToAdmin() {
        this.role = "ADMIN";
    }
}
