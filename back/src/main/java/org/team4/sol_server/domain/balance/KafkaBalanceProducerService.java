package org.team4.sol_server.domain.balance;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.team4.sol_server.domain.balance.dto.BalanceRequestDTO;
import org.team4.sol_server.domain.fail.FailedEvent;
import org.team4.sol_server.domain.fail.FailedEventRepository;
import org.team4.sol_server.error.CustomException;

import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Log4j2
public class KafkaBalanceProducerService {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final FailedEventRepository failedEventRepository;
    private final Executor kafkaFallbackExecutor;
    /**
    * function: 비동기 콜백 구조 CompletableFuture 이용해서
    *
    **/
    public void sendBalanceTransactionEvent(BalanceRequestDTO dto){
        try{
            String messageJson = objectMapper.writeValueAsString(dto);

            // topic, key, message
            kafkaTemplate.send("user_balance_transactions", dto.getUserId(), messageJson)
                    .whenCompleteAsync((result, exception) ->{
                       if(exception != null){
                           // 최종 실패 지점[카프카 브로커에서 문제 발생]: 실제로는 여기에 입출금 유실 복구 처리를 위한 로그 별도로 저장 하는 등의 치러 필요
                           // TODO: 여기에서 수행할 핵심 처리 2가지
                           // 1. failed_kafka_events 테이블 같은 백업 DB나 파일 로그에 원본 DTO/JSON을 덤프(저장)
                           log.error("[Kafka 최종 실패] 입출금 작업 유실 방지를 위해 DB 백업을 시도합니다. User: {}", dto.getUserId());
                           try{
                               // 에러 테이블에 저장 완료
                                FailedEvent failedEvent = FailedEvent.builder()
                                        .userId(dto.getUserId())
                                        .topic("user_balance_transactions")
                                        .payload(messageJson)
                                        .errorMessage(exception.getMessage())
                                        .build();
                                failedEventRepository.save(failedEvent);
                           } catch (Exception e){
                               // db까지 터진 경우 -> 디스크에 로그로 저장
                               log.error("[CRITICAL] DB 백업 실패 원본 주문 데이터 유실 방지용 콘솔 덤프: {}", messageJson, e);
                           }
                           // 2. 필요하다면 웹소켓을 통해 유저에게 "주문 실패" 알림 발송
                       }else{
                           // 성공 지점: 브로커로부터 ACK 받은 경우의 로그
                           log.info("Kafka 전송 성공 -> Topic: user_balance_transactions, Key(User): {}, Type: {}", dto.getUserId(), dto.getType());
                       }
                    }, kafkaFallbackExecutor); // 커스텀 쓰레드 풀 할당

            // 비동기로 카프카에 메시지 보내자마자 성공 여부와 상관 없이 200 응답 반환
            log.info("입출금 요청 접수 완료 (비동기 처리 진행 중) -> User: {}", dto.getUserId());

        } catch (Exception e){
            log.error("JSON 변환 중 예외 발생", e);
            throw new CustomException(BalanceErrorCode.INVALID_MAPPER);
        }
    }
}
