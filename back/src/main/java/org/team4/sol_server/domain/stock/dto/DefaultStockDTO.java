package org.team4.sol_server.domain.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DefaultStockDTO {
    private String ticker;
    private String tickerName;
    private int startPrice;
    private int highPrice;
    private int lowPrice;
    private int endPrice;
    private double diffRate;
    private int diff;
    private long volume;
}
