package org.team4.sol_server.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.team4.sol_server.domain.notification.dto.CompletedOrderDTO;

import java.time.Duration;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaCompletedOrderListener {
    private final SseEmitterManager sseEmitterManager;
    private final Executor notificationExecutor;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(topics = "stock_orders_completed", groupId = "stock-sse-order-group")
    public void consume(CompletedOrderDTO dto){
        log.debug("📥 [Kafka] 체결 완료 이벤트 수신 - 유저: {}, 파티션: {}, orderId: {}",
                dto.getUserId(), dto.getProcessedPartition(), dto.getOrderId());

        // orderId 기반 멱등성 검사 (카프카 재시도/리밸런싱으로 인한 중복 메시지 수신 시 SSE 중복 발송 차단)
        if (dto.getOrderId() != null && !dto.getOrderId().isBlank()) {
            try {
                String dedupKey = "order:completed:notified:" + dto.getOrderId();
                Boolean isFirst = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(1));
                if (Boolean.FALSE.equals(isFirst)) {
                    log.warn("⚠️ [Kafka Completed] 이미 SSE 알림이 발송된 중복 orderId 입니다. 스킵: {}", dto.getOrderId());
                    return;
                }
            } catch (Exception e) {
                log.warn("⚠️ [Kafka Completed] Redis 중복 검증 중 예외 발생 (알림 계속 진행): {}", e.getMessage());
            }
        }

        // SSE 전송 작업을 notificationExecutor 스레드 풀에 비동기로 던집니다.
        notificationExecutor.execute(() -> {
            sseEmitterManager.send(
                dto.getUserId(),
                "order_status",
                dto
            );
        });
    }
}
