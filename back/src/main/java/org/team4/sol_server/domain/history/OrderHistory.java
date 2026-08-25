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
 * 주문 체결 내역 엔티티 (MariaDB order_history 테이블 매핑, 읽기 전용 용도)
 *
 * CREATE TABLE order_history (
 *     id         BIGINT(20)  NOT NULL AUTO_INCREMENT,
 *     user_id    VARCHAR(50) NOT NULL,
 *     ticker     VARCHAR(20) NOT NULL,
 *     amount     BIGINT(20)  NOT NULL DEFAULT 0 COMMENT '주문 총액',
 *     order_type VARCHAR(10) NOT NULL COMMENT 'BUY/SELL',
 *     status     VARCHAR(30) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL ...',
 *     price      BIGINT(20)  NOT NULL,
 *     quantity   INT(11)     NOT NULL,
 *     ordered_at TIMESTAMP   NULL DEFAULT current_timestamp(),
 *     PRIMARY KEY (id),
 *     KEY user_id (user_id),
 *     CONSTRAINT `1` FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
 */
@Entity
@Table(name = "order_history")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistory {

    public static final String TYPE_BUY = "BUY";
    public static final String TYPE_SELL = "SELL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "order_type", nullable = false, length = 10)
    private String orderType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;
}
