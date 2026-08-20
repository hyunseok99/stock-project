package org.team4.sol_server.domain.balance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BalanceRequestDTO {
    private String eventId; // 멱등성 추적용 고유 uuid
    private String userId;
    private TransactionType type; // DEPOSIT/WITHDRAWAL
    private long amount;
    private LocalDateTime timestamp;
    public enum TransactionType{
        DEPOSIT, WITHDRAWAL
    }

    public static BalanceRequestDTO create(String userId, TransactionType type, Long amount) {
        return BalanceRequestDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(userId)
                .type(type)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
