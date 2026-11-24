package org.team4.sol_server.domain.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.team4.sol_server.domain.fail.FailedEvent;
import org.team4.sol_server.domain.fail.FailedEventRepository;
import org.team4.sol_server.domain.order.dto.OrderRequestDTO;
import org.team4.sol_server.error.CustomException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KafkaOrderProducerService 의 브로커 전달 실패 → failed_kafka_events 저장 로직 검증.
 *
 * - KafkaTemplate.send() 가 실패 Future 를 반환하는 경우(브로커 전달 실패):
 *     whenCompleteAsync 콜백에서 FailedEvent 가 저장되는지 검증
 * - 성공 시에는 저장하지 않는지 검증
 * - 직렬화 실패(동기 예외) 시 CustomException 이 발생하는지 검증 (현행 동작 문서화)
 */
@ExtendWith(MockitoExtension.class)
class KafkaOrderProducerServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FailedEventRepository failedEventRepository;

    /** whenCompleteAsync 의 비동기 실행을 즉시(인라인) 수행하기 위한 Executor 목 */
    @Mock
    private Executor kafkaFallbackExecutor;

    private KafkaOrderProducerService service;

    private OrderRequestDTO orderDto;

    @BeforeEach
    void setUp() {
        // @RequiredArgsConstructor 필드 순서와 동일하게 주입
        service = new KafkaOrderProducerService(
                kafkaTemplate, objectMapper, failedEventRepository, kafkaFallbackExecutor);

        orderDto = OrderRequestDTO.builder()
                .userId("guest_test")
                .ticker("005930")
                .orderType("BUY")
                .price(70000)
                .quantity(5)
                .build();
    }

    /** 목 Executor 가 전달받은 Runnable 을 즉시 실행하도록 설정 (비동기 → 동기 변환) */
    private void runExecutorInline() {
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(kafkaFallbackExecutor).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("브로커 전달 실패 시 FailedEvent 가 저장된다")
    void 브로커_전달_실패_시_FailedEvent_저장() throws Exception {
        // given
        String payload = "{\"userId\":\"guest_test\",\"ticker\":\"005930\",\"orderType\":\"BUY\"}";
        when(objectMapper.writeValueAsString(any(OrderRequestDTO.class))).thenReturn(payload);

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Broker not available"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        runExecutorInline();

        // when
        service.sendOrderEvent(orderDto);

        // then
        ArgumentCaptor<FailedEvent> captor = ArgumentCaptor.forClass(FailedEvent.class);
        verify(failedEventRepository, times(1)).save(captor.capture());

        FailedEvent saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("guest_test");
        assertThat(saved.getTopic()).isEqualTo("stock_orders");
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getErrorMessage()).contains("Broker not available");
    }

    @Test
    @DisplayName("브로커 전달 성공 시 FailedEvent 를 저장하지 않는다")
    void 브로커_전달_성공_시_FailedEvent_미저장() throws Exception {
        // given
        when(objectMapper.writeValueAsString(any(OrderRequestDTO.class))).thenReturn("{}");

        CompletableFuture<SendResult<String, String>> ok = new CompletableFuture<>();
        ok.complete(null); // result 는 성공 분기에서 사용하지 않음
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(ok);

        runExecutorInline();

        // when
        service.sendOrderEvent(orderDto);

        // then
        verify(failedEventRepository, never()).save(any(FailedEvent.class));
    }

    @Test
    @DisplayName("직렬화 실패(동기 예외) 시 CustomException 발생, FailedEvent 는 저장되지 않는다")
    void 직렬화_실패_시_CustomException_발생() throws Exception {
        // given: 현행 코드는 동기 catch 에서 CustomException 만 던지고 FailedEvent 를 저장하지 않음
        when(objectMapper.writeValueAsString(any(OrderRequestDTO.class)))
                .thenThrow(new JsonProcessingException("serialize fail") {});

        // when & then
        assertThatThrownBy(() -> service.sendOrderEvent(orderDto))
                .isInstanceOf(CustomException.class);

        verify(failedEventRepository, never()).save(any(FailedEvent.class));
    }
}