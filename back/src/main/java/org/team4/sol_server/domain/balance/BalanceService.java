package org.team4.sol_server.domain.balance;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team4.sol_server.domain.balance.dto.BalanceApiRequest;
import org.team4.sol_server.domain.balance.dto.BalanceRequestDTO;
import org.team4.sol_server.domain.user.User;
import org.team4.sol_server.domain.user.UserService;
import org.team4.sol_server.error.CustomException;

@Service
@RequiredArgsConstructor
@Log4j2
public class BalanceService {
    private final KafkaBalanceProducerService kafkaBalanceProducerService;
    private final BalanceRedisRepository balanceRedisRepository;
    private final UserService userService;

    /**
     * 입금 처리 (Redis 원자적 증가 -> Kafka 비동기 이벤트 발행)
     */
    public void deposit(BalanceApiRequest request) {
        validateAmount(request.getAmount());

        // 1. Redis 잔고 증가 (Key Miss 사전 검증 포함)
        Long newBalance = balanceRedisRepository.deposit(request.getUserId(), request.getAmount());

        // 2. 카프카 전송용 BalanceRequestDTO 생성
        BalanceRequestDTO dto = BalanceRequestDTO.create(
                request.getUserId(),
                BalanceRequestDTO.TransactionType.DEPOSIT,
                request.getAmount()
        );

        // 3. Kafka 비동기 이벤트 발행 (202 ACCEPTED 전송을 위한 비동기 처리)
        kafkaBalanceProducerService.sendBalanceTransactionEvent(dto);

        log.info("[입금 요청 접수 완료] User: {}, Amount: {}, NewBalance: {}",
                request.getUserId(), request.getAmount(), newBalance);
    }

    /**
     * 출금 처리 (Redis 원자적 차감 -> Kafka 비동기 이벤트 발행)
     */
    public void withdraw(BalanceApiRequest request) {
        validateAmount(request.getAmount());

        // 1. Redis 잔고 차감 (Key Miss 사전 검증 & 잔액 부족 롤백 포함)
        Long remainingBalance = balanceRedisRepository.withdraw(request.getUserId(), request.getAmount());

        // 2. 카프카 전송용 BalanceRequestDTO 생성
        BalanceRequestDTO dto = BalanceRequestDTO.create(
                request.getUserId(),
                BalanceRequestDTO.TransactionType.WITHDRAWAL,
                request.getAmount()
        );

        // 3. Kafka 비동기 이벤트 발행
        kafkaBalanceProducerService.sendBalanceTransactionEvent(dto);

        log.info("[출금 요청 접수 완료] User: {}, Amount: {}, RemainingBalance: {}",
                request.getUserId(), request.getAmount(), remainingBalance);
    }

    /**
     * 현재 원화 잔액 조회 (Redis 우선 → Key Miss 시 MariaDB fallback 후 Redis 워밍)
     *
     * - 입출금/주문 체결 시 Redis가 먼저 갱신되므로 실시간 소스는 Redis.
     * - Redis 키가 없으면 DB(users.balance) 기준으로 복구해 재적재한다.
     */
    @Transactional(readOnly = true)
    public long getBalance(String userId) {
        // 1) Redis 우선 조회
        Long cached = balanceRedisRepository.getBalance(userId);
        if (cached != null) {
            return cached;
        }

        // 2) DB fallback (미존재 유저면 USER_NOT_FOUND 예외)
        User user = userService.findById(userId);
        long dbBalance = user.getBalance();

        log.info("[잔액 조회 - Redis Miss] DB에서 복구 후 워밍. User: {}, Balance: {}", userId, dbBalance);

        // 3) Redis 워밍
        balanceRedisRepository.setBalance(userId, dbBalance);
        return dbBalance;
    }

    /**
     * 요청 금액 유효성 검증 (0원 이하 요청 차단)
     */
    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new CustomException(BalanceErrorCode.INVALID_AMOUNT);
        }
    }

}
