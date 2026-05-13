package org.team4.sol_server.domain.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DailyStockDTO {
    private String ticker;
    private String date;
    private int startPrice;
    private int highPrice;
    private int lowPrice;
    private int endPrice;
    private long volume;
}
