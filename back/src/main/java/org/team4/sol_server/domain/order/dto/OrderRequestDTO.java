package org.team4.sol_server.domain.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequestDTO {
    private String userId;
    private String ticker;
    private String orderType; // Buy/Sell
    private long price;
    private int quantity;
}
