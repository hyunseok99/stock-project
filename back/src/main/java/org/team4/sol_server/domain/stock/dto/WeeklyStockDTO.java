package org.team4.sol_server.domain.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyStockDTO {
    private String ticker;
    private String date;
    private int weeklyStartPrice;
    private int weeklyHighPrice;
    private int weeklyLowPrice;
    private int weeklyEndPrice;
    private long weeklyVolume;
}
