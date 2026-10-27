package org.team4.sol_server.domain.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.team4.sol_server.domain.history.dto.RetryOrderDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaOrderRetryListenerTest {

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @InjectMocks
    private KafkaOrderRetryListener listener;

    private RetryOrderDTO sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = RetryOrderDTO.builder()
                .orderId("uuid-test-order-001")
                .userId("user_123")
                .ticker("AAPL")
                .amount(150000L)
                .price(15000L)
                .quantity(10)
                .orderType("BUY")
                .status("SUCCESS")
                .orderedAt("2026-10-27 15:30:00")
                .executedAt("2026-10-27 15:30:01")
                .kafkaLagMs(45)
                .totalLatencyMs(1020)
                .retryCount(1)
                .build();
    }

    @Test
    @DisplayName("신규 orderId인 경우 OrderHistory 엔티티를 생성하여 DB에 정상 저장한다")
    void consume_success() {
        // given
        when(orderHistoryRepository.existsByOrderId("uuid-test-order-001")).thenReturn(false);

        // when
        listener.consume(sampleDto);

        // then
        ArgumentCaptor<OrderHistory> captor = ArgumentCaptor.forClass(OrderHistory.class);
        verify(orderHistoryRepository, times(1)).save(captor.capture());

        OrderHistory saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo("uuid-test-order-001");
        assertThat(saved.getUserId()).isEqualTo("user_123");
        assertThat(saved.getTicker()).isEqualTo("AAPL");
        assertThat(saved.getAmount()).isEqualTo(150000L);
        assertThat(saved.getPrice()).isEqualTo(15000L);
        assertThat(saved.getQuantity()).isEqualTo(10);
        assertThat(saved.getOrderType()).isEqualTo("BUY");
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getKafkaLagMs()).isEqualTo(45);
        assertThat(saved.getTotalLatencyMs()).isEqualTo(1020);
        assertThat(saved.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 존재하는 orderId인 경우 DB 저장을 스킵하여 멱등성을 보장한다")
    void consume_duplicate_skip() {
        // given
        when(orderHistoryRepository.existsByOrderId("uuid-test-order-001")).thenReturn(true);

        // when
        listener.consume(sampleDto);

        // then
        verify(orderHistoryRepository, never()).save(any(OrderHistory.class));
    }

    @Test
    @DisplayName("orderId가 null이거나 빈 문자열인 경우 저장을 스킵한다")
    void consume_invalid_orderId_skip() {
        // given
        sampleDto.setOrderId("");

        // when
        listener.consume(sampleDto);

        // then
        verify(orderHistoryRepository, never()).existsByOrderId(anyString());
        verify(orderHistoryRepository, never()).save(any(OrderHistory.class));
    }
}
