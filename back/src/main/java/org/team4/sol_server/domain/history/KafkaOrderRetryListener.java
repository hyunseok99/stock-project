package org.team4.sol_server.domain.history;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.team4.sol_server.domain.history.dto.RetryOrderDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DB 벌크 플러시 장애 시 격리 토픽(stock_orders_retry)에 보관된 주문을
 * 안전하게 소비(Single Consumer Rate-Limited Drain)하여 MariaDB order_history에 재적재하는 리스너
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaOrderRetryListener {

    private final OrderHistoryRepository orderHistoryRepository;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @KafkaListener(topics = "stock_orders_retry", groupId = "stock-order-retry-group")
    public void consume(RetryOrderDTO dto) {
        log.warn("🔄 [Kafka Retry] DB 격리 주문 재적재 이벤트 수신 - orderId: {}, user: {}, retryCount: {}",
                dto.getOrderId(), dto.getUserId(), dto.getRetryCount());

        if (dto.getOrderId() == null || dto.getOrderId().isBlank()) {
            log.error("❌ [Kafka Retry] 유효하지 않은 orderId 입니다. 스킵합니다.");
            return;
        }

        // 멱등성 검사 (Idempotency): 이미 적재된 주문이면 중복 저장 방지
        if (orderHistoryRepository.existsByOrderId(dto.getOrderId())) {
            log.warn("⚠️ [Kafka Retry] 이미 order_history에 존재하는 orderId 입니다. 중복 스킵: {}", dto.getOrderId());
            return;
        }

        try {
            OrderHistory history = OrderHistory.builder()
                    .orderId(dto.getOrderId())
                    .userId(dto.getUserId())
                    .ticker(dto.getTicker())
                    .amount(dto.getAmount())
                    .price(dto.getPrice())
                    .quantity(dto.getQuantity())
                    .orderType(dto.getOrderType())
                    .status(dto.getStatus() != null ? dto.getStatus() : "SUCCESS")
                    .orderedAt(parseDateTime(dto.getOrderedAt()))
                    .executedAt(parseDateTime(dto.getExecutedAt()))
                    .kafkaLagMs(dto.getKafkaLagMs())
                    .totalLatencyMs(dto.getTotalLatencyMs())
                    .retryCount(dto.getRetryCount())
                    .build();

            orderHistoryRepository.save(history);
            log.info("✅ [Kafka Retry] MariaDB 주문 재적재 성공 - orderId: {}, user: {}, lag: {}ms, latency: {}ms",
                    dto.getOrderId(), dto.getUserId(), dto.getKafkaLagMs(), dto.getTotalLatencyMs());
        } catch (Exception e) {
            log.error("❌ [Kafka Retry] MariaDB 저장 실패 (카프카 오프셋 보류 후 재시도): {}", e.getMessage(), e);
            throw e;
        }
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("⚠️ [Kafka Retry] 날짜 파싱 실패 ('{}'). 현재 시각으로 대체합니다.", text);
            return LocalDateTime.now();
        }
    }
}
