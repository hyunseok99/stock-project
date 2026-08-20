//package org.team4.sol_server;
//
//import org.junit.jupiter.api.Test;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//import org.team4.sol_server.domain.login.dto.OauthUserInfo;
//import org.team4.sol_server.domain.login.entity.User;
//import org.team4.sol_server.domain.login.repository.UserRepository;
//
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.mockito.Mockito.when;
//import static org.springframework.test.util.AssertionErrors.assertNotNull;
//
//public class UserRepositoryTest {
//    @Mock
//    private UserRepository userRepository;
//
//    @InjectMocks
//    private User user;
//
//    public UserRepositoryTest() {
//        MockitoAnnotations.openMocks(this);
//    }
//
//    @Test
//    void saveUser_ShouldNotHaveNullFields() {
//        OauthUserInfo userInfo = new OauthUserInfo(
//                "12345",
//                "TestUser",
//                "test@domain.com",
//                "M",
//                "12-25",
//                "1995"
//        );
//
//        User user = User.from(userInfo, "kakao");
//
//        when(userRepository.findByEmail("test@domain.com")).thenReturn(Optional.empty());
//        when(userRepository.save(user)).thenReturn(user);
//
//        User savedUser = userRepository.save(user);
//
//        assertNotNull(savedUser.getUsername(), "🚨 username이 null입니다!");
//        assertNotNull(savedUser.getEmail(), "🚨 email이 null입니다!");
//        assertEquals("TestUser", savedUser.getUsername());
//        assertEquals("test@domain.com", savedUser.getEmail());
//    }
//}
