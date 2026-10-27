package org.team4.sol_server.domain.history.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DB 적재 실패 시 stock_orders_retry 토픽으로 격리된 주문을
 * 스프링 부트에서 역직렬화하여 DB에 재적재(Forward Recovery)하기 위한 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryOrderDTO {

    @JsonProperty("order_id")
    @JsonAlias({"orderId", "order_id"})
    private String orderId;

    @JsonProperty("user_id")
    @JsonAlias({"userId", "user_id"})
    private String userId;

    private String ticker;

    private long amount;

    private long price;

    private int quantity;

    @JsonProperty("order_type")
    @JsonAlias({"orderType", "order_type"})
    private String orderType;

    private String status;

    @JsonProperty("ordered_at")
    @JsonAlias({"orderedAt", "ordered_at"})
    private String orderedAt;

    @JsonProperty("executed_at")
    @JsonAlias({"executedAt", "executed_at"})
    private String executedAt;

    @JsonProperty("kafka_lag_ms")
    @JsonAlias({"kafkaLagMs", "kafka_lag_ms"})
    private Integer kafkaLagMs;

    @JsonProperty("total_latency_ms")
    @JsonAlias({"totalLatencyMs", "total_latency_ms"})
    private Integer totalLatencyMs;

    @JsonProperty("retry_count")
    @JsonAlias({"retryCount", "retry_count"})
    private int retryCount;
}
