package org.team4.sol_server.error;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomErrorResponse {
    private int status;
    private String code; // 도메인별 에러 코드
    private String message;
}
