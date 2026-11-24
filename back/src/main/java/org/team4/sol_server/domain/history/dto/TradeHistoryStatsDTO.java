package org.team4.sol_server.domain.history.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeHistoryStatsDTO {

    private int totalOrders;
    private int successOrders;
    private int failedOrders;
    private double successRate;
    private double avgTotalLatencyMs;
    private long maxTotalLatencyMs;
    private double avgKafkaLagMs;
    private long totalBuyAmount;
    private long totalSellAmount;
}
