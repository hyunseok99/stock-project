package org.team4.sol_server.domain.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class YearlyStockDTO {
    private String ticker;
    private String date;
    private int yearlyStartPrice;
    private int yearlyHighPrice;
    private int yearlyLowPrice;
    private int yearlyEndPrice;
    private long yearlyVolume;
}
