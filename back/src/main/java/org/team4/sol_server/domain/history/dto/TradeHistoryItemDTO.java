package org.team4.sol_server.domain.history.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래 히스토리 통합 응답 DTO (입출금 + 주문 체결 병합)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeHistoryItemDTO {

    private Long id;

    /** 데이터 출처: CASH(입출금) / ORDER(주문 체결) */
    private String source;

    /** DEPOSIT / WITHDRAWAL / BUY / SELL */
    private String type;

    /** 주문 종목 (입출금은 null) */
    private String ticker;

    /** 주문 수량 (입출금은 null) */
    private Integer quantity;

    /** 주문 단가 (입출금은 null) */
    private Long price;

    /** 총액 (입출금 금액 or 주문 총액) */
    private long amount;

    /** 처리 상태: 입출금은 COMPLETED 고정 / 주문은 order_history.status 값(SUCCESS 등) */
    private String status;

    /** 발생 시각 (ISO-8601) */
    private String timestamp;

    /** 주문 고유 ID (입출금은 null) */
    private String orderId;

    /** 주문 접수 시각 (ISO-8601) */
    private String orderedAt;

    /** 체결 완료 시각 (ISO-8601) */
    private String executedAt;

    /** 카프카 브로커 지연 시간 (ms) */
    private Integer kafkaLagMs;

    /** 전체 처리 소요 시간 (ms) */
    private Integer totalLatencyMs;

    /** DB 재시도 횟수 */
    private Integer retryCount;
}
