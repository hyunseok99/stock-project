package org.team4.sol_server.domain.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.team4.sol_server.domain.order.dto.OrderRequestDTO;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private KafkaOrderProducerService kafkaOrderProducerService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OrderService orderService;

    private OrderRequestDTO validDto;

    @BeforeEach
    void setUp() {
        validDto = OrderRequestDTO.builder()
                .orderId("uuid-test-order-001")
                .userId("test_user")
                .ticker("005930")
                .orderType("BUY")
                .quantity(10)
                .price(70000L)
                .build();
    }

    @Test
    @DisplayName("신규 orderId 요청인 경우 카프카에 정상 발행한다")
    void processOrder_firstTime_success() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("order:request:dedup:uuid-test-order-001"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        // when
        orderService.processOrder(validDto);

        // then
        verify(kafkaOrderProducerService, times(1)).sendOrderEvent(any(OrderRequestDTO.class));
    }

    @Test
    @DisplayName("이미 접수된 동일한 orderId 요청인 경우 카프카 중복 발행을 차단한다")
    void processOrder_duplicate_blocked() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("order:request:dedup:uuid-test-order-001"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        // when
        orderService.processOrder(validDto);

        // then
        verify(kafkaOrderProducerService, never()).sendOrderEvent(any(OrderRequestDTO.class));
    }
}
