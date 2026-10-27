package org.team4.sol_server.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * order-process가 체결 완료/실패 후 발행한 이벤트를
 * 스프링 부트에서 역직렬화하기 위한 DTO(Data Transfer Object) 클래스입니다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompletedOrderDTO {
    private String orderId;
    private String userId;
    private String ticker;
    private String status;
    private String message;
    private Integer processedPartition;
}
