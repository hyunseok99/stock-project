package org.team4.sol_server.error;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final CustomErrorCode errorCode;

    public CustomException(CustomErrorCode errorCode){
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
