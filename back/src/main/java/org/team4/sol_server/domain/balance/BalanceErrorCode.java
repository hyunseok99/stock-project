    package org.team4.sol_server.domain.balance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.team4.sol_server.error.CustomErrorCode;

@Getter
@RequiredArgsConstructor
public enum BalanceErrorCode implements CustomErrorCode {
    // 400 Bad Request (클라이언트 요청 오류)
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "B001", "잔액이 부족합니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "B002", "입출금 금액은 0원보다 커야 합니다."),
    INVALID_TRANSACTION_TYPE(HttpStatus.BAD_REQUEST, "B003", "유효하지 않은 거래 유형(Type)입니다."),
    EXCEED_MAX_DEPOSIT_AMOUNT(HttpStatus.BAD_REQUEST, "B008", "1회 최대 입금 금액은 100억 원입니다."),

    // 404 Not Found (대상 리소스 없음)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "B004", "존재하지 않는 유저입니다."),

    // 500 Internal Server Error (서버/인프라 처리 오류)
    INVALID_MAPPER(HttpStatus.INTERNAL_SERVER_ERROR, "B005", "JSON 변환 중 예외가 발생했습니다."),
    REDIS_OPERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "B006", "Redis 잔고 반영 처리 중 오류가 발생했습니다."),
    KAFKA_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "B007", "입출금 이벤트 카프카 전송에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
