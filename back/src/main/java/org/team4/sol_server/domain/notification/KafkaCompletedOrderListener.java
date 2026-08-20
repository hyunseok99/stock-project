package org.team4.sol_server.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.team4.sol_server.domain.notification.dto.CompletedOrderDTO;

import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaCompletedOrderListener {
    private final SseEmitterManager sseEmitterManager;

    private final Executor notificationExecutor;

    @KafkaListener(topics = "stock_orders_completed", groupId = "stock-sse-order-group")
    public void consume(CompletedOrderDTO dto){
        log.debug("📥 [Kafka] 체결 완료 이벤트 수신 - 유저: {}, 파티션: {}", dto.getUserId(), dto.getProcessedPartition());
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
