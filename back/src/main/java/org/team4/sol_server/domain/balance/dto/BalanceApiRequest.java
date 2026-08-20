package org.team4.sol_server.domain.balance.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BalanceApiRequest {
    private String userId;
    private long amount;
}
