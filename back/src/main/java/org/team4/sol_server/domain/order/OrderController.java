package org.team4.sol_server.domain.order;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.team4.sol_server.domain.order.dto.OrderRequestDTO;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<String> createOrder(@RequestBody OrderRequestDTO requestDTO){
        orderService.processOrder(requestDTO);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Order request has been accepted");
    }
}
