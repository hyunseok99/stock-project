package org.team4.sol_server.domain.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 입출금 거래내역 엔티티 (MariaDB cash_transactions_history 테이블 매핑, 읽기 전용 용도)
 *
 * CREATE TABLE cash_transactions_history (
 *     id               BIGINT(20)  NOT NULL AUTO_INCREMENT,
 *     event_id         VARCHAR(64) NOT NULL,
 *     user_id          VARCHAR(50) NOT NULL,
 *     transaction_type VARCHAR(30) NOT NULL COMMENT 'DEPOSIT/WITHDRAWAL',
 *     amount           BIGINT(20)  NOT NULL,
 *     created_at       DATETIME    DEFAULT current_timestamp(),
 *     PRIMARY KEY (id),
 *     UNIQUE KEY uk_event_id (event_id),
 *     KEY idx_user_id (user_id)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
 */
@Entity
@Table(name = "cash_transactions_history")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashTransactionHistory {

    public static final String TYPE_DEPOSIT = "DEPOSIT";
    public static final String TYPE_WITHDRAWAL = "WITHDRAWAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
