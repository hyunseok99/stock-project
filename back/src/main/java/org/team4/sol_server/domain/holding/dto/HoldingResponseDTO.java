package org.team4.sol_server.domain.holding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 보유 주식 응답 DTO
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HoldingResponseDTO {
    private String ticker;
    private String name;
    private Integer quantity;
    private Long purchasePrice;
}