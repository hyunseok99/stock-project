package org.team4.sol_server.domain.holding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.team4.sol_server.domain.user.User;

import java.time.LocalDateTime;

/**
 * 사용자 보유 주식 엔티티 (MariaDB user_stocks 테이블 매핑)
 *
 * CREATE TABLE user_stocks (
 *     id            BIGINT(20)     NOT NULL AUTO_INCREMENT,
 *     user_id       VARCHAR(50)    NOT NULL,
 *     ticker        VARCHAR(20)    NOT NULL,
 *     quantity      INT(11)        NOT NULL DEFAULT 0,
 *     average_price BIGINT(20)     NOT NULL DEFAULT 0,
 *     updated_at    TIMESTAMP      NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
 *     PRIMARY KEY (id),
 *     UNIQUE KEY uq_user_ticker (user_id, ticker),
 *     CONSTRAINT fk_user_stocks_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 */
@Entity
@Table(
        name = "user_stocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_ticker",
                columnNames = {"user_id", "ticker"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * users.user_id 를 참조하는 FK (DDL: CONSTRAINT fk_user_stocks_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE)
     * user_stocks.user_id 컬럼과 매핑된다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_stocks_user")
    )
    private User user;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "average_price", nullable = false)
    private long averagePrice;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
