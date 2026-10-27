package org.team4.sol_server.domain.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.team4.sol_server.domain.notification.dto.CompletedOrderDTO;

import java.time.Duration;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaCompletedOrderListenerTest {

    @Mock
    private SseEmitterManager sseEmitterManager;

    @Mock
    private Executor notificationExecutor;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private KafkaCompletedOrderListener listener;

    private CompletedOrderDTO sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = CompletedOrderDTO.builder()
                .orderId("uuid-completed-order-001")
                .userId("test_user")
                .ticker("005930")
                .status("SUCCESS")
                .message("주문 체결 완료")
                .processedPartition(0)
                .build();
    }

    @Test
    @DisplayName("신규 체결 완료 이벤트인 경우 SSE 알림을 전송한다")
    void consume_firstTime_success() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("order:completed:notified:uuid-completed-order-001"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        // when
        listener.consume(sampleDto);

        // then
        verify(notificationExecutor, times(1)).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("이미 처리된 체결 완료 이벤트인 경우 SSE 중복 발송을 차단한다")
    void consume_duplicate_blocked() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("order:completed:notified:uuid-completed-order-001"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        // when
        listener.consume(sampleDto);

        // then
        verify(notificationExecutor, never()).execute(any(Runnable.class));
        verify(sseEmitterManager, never()).send(any(), any(), any());
    }
}
