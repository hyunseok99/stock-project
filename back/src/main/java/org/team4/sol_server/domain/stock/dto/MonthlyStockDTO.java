package org.team4.sol_server.domain.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyStockDTO {
    private String ticker;
    private String date;
    private int monthlyStartPrice;
    private int monthlyHighPrice;
    private int monthlyLowPrice;
    private int monthlyEndPrice;
    private long monthlyVolume;
}
