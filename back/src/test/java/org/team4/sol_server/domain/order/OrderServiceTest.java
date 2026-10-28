package org.team4.sol_server.domain.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.team4.sol_server.domain.order.dto.OrderRequestDTO;
import org.team4.sol_server.error.CustomException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private KafkaOrderProducerService kafkaOrderProducerService;

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
    @DisplayName("유효한 주문 요청인 경우 카프카에 정상 발행한다")
    void processOrder_valid_success() {
        // when
        orderService.processOrder(validDto);

        // then
        verify(kafkaOrderProducerService, times(1)).sendOrderEvent(any(OrderRequestDTO.class));
    }

    @Test
    @DisplayName("수량이 0 이하인 경우 예외를 발생시킨다")
    void processOrder_invalidQuantity_throwsException() {
        OrderRequestDTO invalidDto = validDto.toBuilder().quantity(0).build();

        assertThatThrownBy(() -> orderService.processOrder(invalidDto))
                .isInstanceOf(CustomException.class);

        verify(kafkaOrderProducerService, never()).sendOrderEvent(any());
    }

    @Test
    @DisplayName("가격이 0 이하인 경우 예외를 발생시킨다")
    void processOrder_invalidPrice_throwsException() {
        OrderRequestDTO invalidDto = validDto.toBuilder().price(0L).build();

        assertThatThrownBy(() -> orderService.processOrder(invalidDto))
                .isInstanceOf(CustomException.class);

        verify(kafkaOrderProducerService, never()).sendOrderEvent(any());
    }
}
