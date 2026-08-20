package org.team4.sol_server.domain.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.team4.sol_server.domain.order.dto.OrderRequestDTO;
import org.team4.sol_server.error.CustomException;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final KafkaOrderProducerService kafkaOrderProducerService;

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

        kafkaOrderProducerService.sendOrderEvent(dto);
    }
}
