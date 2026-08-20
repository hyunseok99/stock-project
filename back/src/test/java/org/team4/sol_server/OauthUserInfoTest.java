//package org.team4.sol_server;
//
//import org.junit.jupiter.api.Test;
//import org.team4.sol_server.domain.login.dto.OauthUserInfo;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class OauthUserInfoTest {
//
//    @Test
//    void createOauthUserInfo_Kakao() {
//        OauthUserInfo userInfo = new OauthUserInfo(
//                "12345",
//                "KakaoUser",
//                "test@kakao.com",
//                null, null, null
//        );
//
//        assertEquals("KakaoUser", userInfo.nickname());
//        assertEquals("test@kakao.com", userInfo.email());
//    }
//
//    @Test
//    void createOauthUserInfo_Naver() {
//        OauthUserInfo userInfo = new OauthUserInfo(
//                "67890",
//                "NaverUser",
//                "test@naver.com",
//                "M",
//                "01-01",
//                "1990"
//        );
//
//        assertEquals("NaverUser", userInfo.nickname());
//        assertEquals("test@naver.com", userInfo.email());
//        assertEquals("M", userInfo.gender());
//        assertEquals("01-01", userInfo.birthday());
//        assertEquals("1990", userInfo.birthyear());
//    }
//}
