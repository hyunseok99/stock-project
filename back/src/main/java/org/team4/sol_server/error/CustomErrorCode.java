package org.team4.sol_server.error;

import org.springframework.http.HttpStatus;

public interface CustomErrorCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
}
