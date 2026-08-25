package org.team4.sol_server.domain.balance;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.team4.sol_server.error.CustomException;

@Repository
@RequiredArgsConstructor
@Log4j2
public class BalanceRedisRepository {
    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "user:balance:";

    /**
     * 입금 연산
     */
    public Long deposit(String userId, Long amount) {
        String redisKey = getKey(userId);

        // 💡 Redis는 키가 없으면 자동으로 0을 생성하므로, 연산 전에 존재 여부 사전 검증
        validateUserKeyExists(userId, redisKey);

        Long newBalance = redisTemplate.opsForValue().increment(redisKey, amount);
        if (newBalance == null) {
            throw new CustomException(BalanceErrorCode.REDIS_OPERATION_FAILED);
        }

        return newBalance;
    }

    /**
     * 출금 연산
     */
    public Long withdraw(String userId, Long amount) {
        String redisKey = getKey(userId);

        // 💡 사전 존재 여부 검증
        validateUserKeyExists(userId, redisKey);

        // Redis 동기 차감
        Long remainingBalance = redisTemplate.opsForValue().decrement(redisKey, amount);
        if (remainingBalance == null) {
            throw new CustomException(BalanceErrorCode.REDIS_OPERATION_FAILED);
        }

        // 잔액 부족 시 동기 롤백 (원복)
        if (remainingBalance < 0) {
            redisTemplate.opsForValue().increment(redisKey, amount);
            log.warn("[출금 거절 - 잔액 부족] User: {}, 요청액: {}", userId, amount);
            throw new CustomException(BalanceErrorCode.INSUFFICIENT_BALANCE);
        }

        return remainingBalance;
    }

    /**
     * Redis 키 존재 여부 검증 (Key Miss 시 USER_NOT_FOUND 예외 발생)
     */
    private void validateUserKeyExists(String userId, String redisKey) {
        Boolean exists = redisTemplate.hasKey(redisKey);
        if (Boolean.FALSE.equals(exists)) {
            log.warn("[Redis Key Miss] 존재하지 않는 유저 또는 초기화되지 않은 잔고 요청. User: {}", userId);
            throw new CustomException(BalanceErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 현재 잔고 조회 (Key Miss 시 null 반환)
     */
    public Long getBalance(String userId) {
        String redisKey = getKey(userId);
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("[Redis 잔고 파싱 실패] User: {}, Value: {}", userId, value);
            return null;
        }
    }

    /**
     * DB 기준 잔고를 Redis에 재적재 (워밍)
     */
    public void setBalance(String userId, long balance) {
        redisTemplate.opsForValue().set(getKey(userId), String.valueOf(balance));
    }

    private String getKey(String userId) {
        return KEY_PREFIX + userId;
    }

}
