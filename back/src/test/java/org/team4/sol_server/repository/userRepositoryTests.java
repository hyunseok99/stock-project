//package org.team4.sol_server.repository;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.team4.sol_server.domain.login.entity.User;
//import org.team4.sol_server.domain.login.repository.UserRepository;
//import org.team4.sol_server.domain.user.entity.stamp_User;
//
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//
//@SpringBootTest
//public class userRepositoryTests {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    // 테스트 전에 실행될 메서드
//    @BeforeEach
//    public void setUp() {
//        // 더미 데이터 생성
//        User user1 = stamp_User.builder()
//                .userEmail("user1@example.com")
//                .userName("홍길동")
//                .userGender('0') // 남성
//                .personalInvestor(3) // 저위험
//                .birthday("1990-05-10")
//                .birthYear("1990")
//                .loginType("kakao")
//                .jwtToken("abdsklfji312safdsa4124")
//                .age(30)
//                .job(5)
//                .build();
//
//        stamp_User user2 = stamp_User.builder()
//                .userEmail("user2@example.com")
//                .userName("김영희")
//                .userGender('1') // 여성
//                .personalInvestor(8)
//                .birthday("1999-05-10")
//                .birthYear("1999")
//                .loginType("kakao")
//                .jwtToken("abdsklfji3124124")
//                .age(20)
//                .job(4)
//                .build();
//
//        stamp_User user3 = stamp_User.builder()
//                .userEmail("user3@example.com")
//                .userName("이철수")
//                .userGender('0') // 남성
//                .personalInvestor(13) // 저위험
//                .birthday("2000-05-10")
//                .birthYear("2000")
//                .loginType("kakao")
//                .jwtToken("abdsklfji3124124")
//                .age(20)
//                .job(1)
//                .build();
//
//        stamp_User user4 = stamp_User.builder()
//                .userEmail("user4@example.com")
//                .userName("이순신")
//                .userGender('0') // 남성
//                .personalInvestor(3) // 저위험
//                .birthday("1990-05-10")
//                .birthYear("1990")
//                .loginType("kakao")
//                .jwtToken("abdsklfji3124124")
//                .age(30)
//                .job(1)
//                .build();
//
//        stamp_User user5 = stamp_User.builder()
//                .userEmail("user5@example.com")
//                .userName("고영희")
//                .userGender('1') // 여성
//                .personalInvestor(7) // 중위험
//                .birthday("1985-11-23")
//                .birthYear("1985")
//                .loginType("naver")
//                .jwtToken("adsflk3uoi19018590r312")
//                .age(40)
//                .job(2)
//                .build();
//
//        stamp_User user6 = stamp_User.builder()
//                .userEmail("user6@example.com")
//                .userName("이철수")
//                .userGender('0') // 남성
//                .personalInvestor(12) // 고위험
//                .birthday("1975-01-15")
//                .birthYear("1975")
//                .loginType("naver")
//                .jwtToken("asldkjfiavuxiozcjvocxzvho221314")
//                .age(50)
//                .job(3)
//                .build();
//
//        // 더미 데이터 저장
//        userRepository.save(user1);
//        userRepository.save(user2);
//        userRepository.save(user3);
//        userRepository.save(user4);
//        userRepository.save(user5);
//        userRepository.save(user6);
//    }
//
//    // 테스트 메서드: 사용자 데이터가 잘 저장되었는지 확인
//    @Test
//    public void testSaveAndFindUsers() {
//        // 저장된 모든 사용자 조회
//        assertNotNull(userRepository.findAll());
//    }
//}
