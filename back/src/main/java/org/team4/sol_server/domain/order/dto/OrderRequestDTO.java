package org.team4.sol_server.domain.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequestDTO {
    private String orderId;
    private String userId;
    private String ticker;
    private String orderType; // Buy/Sell
    private long price;
    private int quantity;
    private LocalDateTime orderedAt;
    private Long requestedAt; // epoch ms
    private Long publishedAt; // epoch ms
}
