package org.team4.sol_server.error;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Log4j2
public class GlobalCustomExceptionHandler {
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<CustomErrorResponse> handleCustomException(CustomException e){
        CustomErrorCode errorCode = e.getErrorCode();

        CustomErrorResponse response = CustomErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<CustomErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("[HTTP Message Not Readable] 요청 본문 역직렬화 실패: {}", e.getMessage());

        CustomErrorResponse response = CustomErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .code("COMMON_001")
                .message("요청 본문 형식이 올바르지 않거나 숫자 허용 범위를 초과했습니다.")
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
