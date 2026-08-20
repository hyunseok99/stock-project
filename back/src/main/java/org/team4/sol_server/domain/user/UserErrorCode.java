package org.team4.sol_server.domain.user;

import org.team4.sol_server.error.CustomErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements CustomErrorCode {

    INVALID_USER_ID(HttpStatus.BAD_REQUEST, "U001", "userId는 비어있거나 공백일 수 없습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "U002", "0원을 소모할 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U003", "존재하지 않는 게스트 유저입니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "U004", "예수금이 부족하여 매수 주문을 처리할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
