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
}