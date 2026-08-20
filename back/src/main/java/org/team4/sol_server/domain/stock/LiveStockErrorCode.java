package org.team4.sol_server.domain.stock;

import org.springframework.http.HttpStatus;
import org.team4.sol_server.error.CustomErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LiveStockErrorCode implements CustomErrorCode {
    INVALID_STOCK_TYPE(HttpStatus.BAD_REQUEST, "S001", "올바르지 않은 조회 타입입니다. (daily, weekly, monthly, yearly만 가능)");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
