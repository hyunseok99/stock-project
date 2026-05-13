package org.team4.sol_server.config;


import lombok.Getter;

/**
 * 에러 코드 관리
 */
@Getter
public enum BaseResponseStatus {
    /**
     * 1000 : 요청 성공
     */
    SUCCESS(true, 1000, "Request success"),
    UPDATED_SUCCESS(true,1001,"업데이트 완료."),

    /**
     * 2000 : Request 오류
     */
    // Common
    EMPTY_JWT(false, 2001, "JWT를 입력해주세요."),
    INVALID_JWT(false, 2002, "유효하지 않은 JWT입니다."),
    INVALID_USER_JWT(false, 2003, "권한이 없는 유저의 접근입니다."),
    UPDATED_NOT_SUCCESS(false,2004,"업데이트가 실패되었습니다."),



    /**
     * 3000 : 사용자 관련 오류
     */
    USER_NOT_FOUND(false, 3001, "사용자가 존재하지 않습니다."),  // 추가된 응답 코드
    UNAUTHORIZED_USER(false,3002,"사용자의 토큰이 아닙니다"),
    /**
     * 4000 : 기본 정보 관련 응답
     */
    BASIC_INFO_REQUIRED(true, 4001, "기본 정보 입력이 필요합니다."),
    BASIC_INFO_ALREADY_COMPLETED(true, 4002, "이미 기본 정보를 입력했습니다."),

    /**
     * 5000 : 스탬프 관련 응답
     */
    STAMP_NOT_YET(false, 5001, "아직 스탬프를 찍지 않았습니다."),
    STAMP_SAVE(true, 5002, "스탬프가 저장되었습니다."), // 스탬프 정상 저장
    STAMP_NOT_SAVE(false, 5003, "스탬프가 저장되지 않았습니다."), // 스탬프 저장 실패
    STAMP_TIME(true, 5004, "챌린지 시작 날짜"), // 챌린지 시작 날짜
    STAMP_HISTORY(true, 5005, "스탬프 기록"), // 스탬프 기록 조회 성공// 추가된 응답 코드

    /**
     * 6000 : 사용자 점수 관련 응답
     */
    SCORE_SAVE_SUCCESS(true, 6001, "점수 저장 성공."),
    SCORE_SAVE_FAILED(false, 6002, "점수 저장 실패."),
    SCORE_FETCH_SUCCESS(true, 6003, "점수 조회 성공."),
    SCORE_FETCH_FAILED(false, 6004, "점수 조회 실패."),
    USER_NAME_FETCH_SUCCESS(true, 6005, "사용자 이름 조회 성공."),
    USER_NAME_FETCH_FAILED(false, 6006, "사용자 이름 조회 실패.");

    private final boolean isSuccess;
    private final int code;
    private final String message;

    private BaseResponseStatus(boolean isSuccess, int code, String message) {
        this.isSuccess = isSuccess;
        this.code = code;
        this.message = message;
    }
}