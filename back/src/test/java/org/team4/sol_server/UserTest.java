//package org.team4.sol_server;
//
//import org.junit.jupiter.api.Test;
//import org.team4.sol_server.domain.login.dto.OauthUserInfo;
//import org.team4.sol_server.domain.login.entity.User;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//public class UserTest {
//    @Test
//    public  void createUserFromOauthUserInfo() {
//        OauthUserInfo userInfo = new OauthUserInfo(
//                "12345",
//                "OAuthUser",
//                "user@test.com",
//                "M",
//                "01-01",
//                "1990"
//        );
//
//        User user = User.from(userInfo, "kakao");
//
//        assertEquals("OAuthUser", user.getUsername());
//        assertEquals("user@test.com", user.getEmail());
//        assertEquals("M", user.getGender());
//        assertEquals("01-01", user.getBirthday());
//        assertEquals("1990", user.getBirthyear());
//        assertEquals("kakao", user.getLoginType());
//    }
//}
