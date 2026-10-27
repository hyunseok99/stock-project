package org.team4.sol_server.domain.order;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.team4.sol_server.domain.order.dto.OrderRequestDTO;
import org.team4.sol_server.error.CustomException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final KafkaOrderProducerService kafkaOrderProducerService;
    private final StringRedisTemplate redisTemplate;

    public void processOrder(OrderRequestDTO dto) {
        /**
        * function: 매수,매도 요청을 카프카 브로커로 전송
        * param: OrderRequestDTO
        *
        **/
        if (!"BUY".equals(dto.getOrderType()) && !"SELL".equals(dto.getOrderType())) {
            throw new CustomException(OrderErrorCode.INVALID_ORDER_TYPE);
        }
        if (dto.getQuantity() <= 0) {
            throw new CustomException(OrderErrorCode.INVALID_QUANTITY);
        }
        if (dto.getPrice() <= 0) {
            throw new CustomException(OrderErrorCode.INVALID_PRICE);
        }

        // 클라이언트에서 동일한 orderId로 중복 요청(더블 클릭, 네트워크 재전송)한 경우 멱등성 방어
        if (dto.getOrderId() != null && !dto.getOrderId().isBlank()) {
            try {
                String dedupKey = "order:request:dedup:" + dto.getOrderId();
                Boolean isFirst = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofMinutes(10));
                if (Boolean.FALSE.equals(isFirst)) {
                    log.warn("⚠️ [OrderService] 이미 접수 처리된 중복 orderId 요청입니다. 중복 발행 차단: {}", dto.getOrderId());
                    return;
                }
            } catch (Exception e) {
                log.warn("⚠️ [OrderService] Redis 멱등성 검증 중 예외 발생 (주문 계속 진행): {}", e.getMessage());
            }
        }

        long now = System.currentTimeMillis();
        String orderId = (dto.getOrderId() != null && !dto.getOrderId().isBlank())
                ? dto.getOrderId()
                : UUID.randomUUID().toString();

        OrderRequestDTO eventDto = dto.toBuilder()
                .orderId(orderId)
                .orderedAt(dto.getOrderedAt() != null ? dto.getOrderedAt() : LocalDateTime.now())
                .requestedAt(dto.getRequestedAt() != null ? dto.getRequestedAt() : now)
                .publishedAt(now)
                .build();

        kafkaOrderProducerService.sendOrderEvent(eventDto);
    }
}
