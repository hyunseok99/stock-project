package org.team4.sol_server.domain.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게스트 유저 엔티티 (MariaDB users 테이블 매핑)
 *
 * CREATE TABLE users (
 *     user_id    VARCHAR(50)  NOT NULL,
 *     balance    BIGINT(20)   NOT NULL DEFAULT 10000000,
 *     created_at TIMESTAMP    NULL DEFAULT current_timestamp(),
 *     PRIMARY KEY (user_id)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */
@Entity
@Table(name = "users")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void changeBalance(long targetBalance) {
        if (targetBalance < 0) {
            throw new IllegalArgumentException("잔고는 0 미만이 될 수 없습니다.");
        }
        this.balance = targetBalance;
    }
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
