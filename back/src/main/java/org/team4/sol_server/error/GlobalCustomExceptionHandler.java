package org.team4.sol_server.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
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
}
