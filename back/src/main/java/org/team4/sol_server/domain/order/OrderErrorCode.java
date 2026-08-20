package org.team4.sol_server.domain.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.team4.sol_server.error.CustomErrorCode;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements CustomErrorCode {
    INVALID_ORDER_TYPE(HttpStatus.BAD_REQUEST, "O001", "주문 타입은 BUY 또는 SELL만 가능합니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "O002", "주문 수량은 0보다 커야 합니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "O003", "주문 가격은 0보다 커야 합니다."),
    INVALID_MAPPER(HttpStatus.BAD_REQUEST, "O004", "주문 요청 데이터 형식이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
