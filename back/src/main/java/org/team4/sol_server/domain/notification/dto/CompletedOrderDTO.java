package org.team4.sol_server.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * order-process가 체결 완료/실패 후 발행한 이벤트를
 * 스프링 부트에서 역직렬화하고, SSE 알림으로 프론트엔드에 전달하기 위한 DTO
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompletedOrderDTO {
    private String orderId;
    private String userId;
    private String ticker;
    private String orderType;
    private String status;
    private String message;
    private Integer processedPartition;

    /** 주문 접수 시각 */
    @JsonProperty("orderedAt")
    @JsonAlias({"ordered_at", "orderedAt"})
    private String orderedAt;

    /** 체결 완료 시각 */
    @JsonProperty("executedAt")
    @JsonAlias({"executed_at", "executedAt"})
    private String executedAt;

    /** 카프카 브로커 지연 시간 (ms) */
    @JsonProperty("kafkaLagMs")
    @JsonAlias({"kafka_lag_ms", "kafkaLagMs"})
    private Integer kafkaLagMs;

    /** 전체 E2E 지연 시간 (ms) */
    @JsonProperty("totalLatencyMs")
    @JsonAlias({"total_latency_ms", "totalLatencyMs"})
    private Integer totalLatencyMs;
}
