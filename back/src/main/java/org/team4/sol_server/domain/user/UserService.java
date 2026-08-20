package org.team4.sol_server.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team4.sol_server.error.CustomException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "user:balance:";
    private static final long INITIAL_BALANCE = 10000000L;
    /**
     * function: 게스트 유저 생성 및 redis에 신규 유저 정보 적재
     * param: String
     * 유저가 이미 존재하면 스킵, 없으면 저장
     **/
    @Transactional
    public void createGuestIfNotExist(String userId) {
        if (userRepository.existsById(userId)) {
            return;
        }

        User newUser = User.builder()
                .userId(userId)
                .balance(INITIAL_BALANCE)
                .build();

        userRepository.save(newUser);

        // redis 캐시에 신규 유저 정보 적재 (user:balance:{userId} = "10000000")
        redisTemplate.opsForValue()
            .setIfAbsent(KEY_PREFIX + userId, String.valueOf(INITIAL_BALANCE));
    }

    /**
     * function: 유저 단건 조회
     * param: String
     * return: 유저가 없으면 USER_NOT_FOUND 에러 리턴
     **/
    public User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

}
