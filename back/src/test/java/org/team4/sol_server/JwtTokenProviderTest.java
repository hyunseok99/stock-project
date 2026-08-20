//package org.team4.sol_server;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import static org.junit.jupiter.api.Assertions.*;
//import org.mockito.Mockito;
//import org.springframework.data.relational.core.sql.In;
//import org.team4.sol_server.config.jwt.JwtTokenProvider;
//import org.team4.sol_server.domain.login.repository.UserRepository;
//
//public class JwtTokenProviderTest {
//
//    private JwtTokenProvider jwtTokenProvider;
//
//    @BeforeEach
//    public void setup() {
//        // Mock UserRepository (외부 의존성 제거)
//        UserRepository mockUserRepository = Mockito.mock(UserRepository.class);
//        jwtTokenProvider = new JwtTokenProvider(mockUserRepository);
//    }
//
//    @Test
//    public void testCreateJwt() {
//        int userIdx = 8;
//
//        // JWT 생성
//        String token = jwtTokenProvider.createJwt(userIdx);
//        System.out.println("Generated JWT: " + token);
//
//        assertNotNull(token);
//        assertTrue(token.startsWith("eyJ"));
//    }
//
//    @Test
//    public void testValidateToken() {
//        int userIdx = 8;
//
//        // JWT 생성
//        String token = jwtTokenProvider.createJwt(userIdx);
//
//        // 토큰 검증
//        assertTrue(jwtTokenProvider.validateToken(token));
//    }
//
//    @Test
//    public void testGetUserIdFromToken() {
//        int userIdx = 8;
//
//        // JWT 생성
//        String token = jwtTokenProvider.createJwt(userIdx);
//
//        // JWT에서 유저 ID 추출
//        Integer extractedUserId = jwtTokenProvider.getUserIdx(token);
//
//        System.out.println("Generated JWT: " + token);
//
//        assertEquals(userIdx, extractedUserId);
//        System.out.println(userIdx);
//    }
//}
