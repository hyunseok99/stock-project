package org.team4.sol_server.domain.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    private String userId;

    @Column(name = "balance")
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
