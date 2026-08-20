package org.team4.sol_server.domain.balance;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.team4.sol_server.domain.balance.dto.BalanceApiRequest;
import org.team4.sol_server.domain.balance.dto.BalanceRequestDTO;
import org.team4.sol_server.error.CustomException;

@Service
@RequiredArgsConstructor
@Log4j2
public class BalanceService {
    private final KafkaBalanceProducerService kafkaBalanceProducerService;
    private final BalanceRedisRepository balanceRedisRepository;

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
     * 요청 금액 유효성 검증 (0원 이하 요청 차단)
     */
    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new CustomException(BalanceErrorCode.INVALID_AMOUNT);
        }
    }

}
