package org.team4.sol_server.domain.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE(Server-Sent Events) 사용자 세션을 스레드 안전하게 관리하는 컴포넌트입니다.
 * 1코어 환경에서 리소스 누수를 원천 봉쇄하기 위해 정교한 생명주기 콜백을 등록합니다.
 */
@Component
@Slf4j
public class SseEmitterManager {

    // 유저 ID를 Key로, 해당 유저의 SSE 연결 인스턴스(SseEmitter)를 Value로 매핑합니다.
    // 여러 스레드가 동시에 접근하더라도 데드락이나 동시성 예외 없이 안전한 ConcurrentHashMap을 사용합니다.
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 기본 타임아웃 세팅 (30분 = 1,800,000ms)
    private static final Long DEFAULT_TIMEOUT = 1800000L;

    /**
     * 클라이언트의 신규 SSE 연결을 수락하고 세션을 관리합니다.
     * 동일 유저가 새로운 탭을 열거나 재연결을 시도할 경우, 기존에 찌꺼기로 남은 세션을 안전하게 닫고 교체합니다.
     */
    public SseEmitter createEmitter(String userId) {
        // 1. 기존 세션이 남아있는지 확인하고, 있다면 메모리 누수 방지를 위해 강제 종료 후 제거합니다.
        emitters.computeIfPresent(userId, (key, oldEmitter) -> {
            log.info("[SSE] 유저 '{}'의 중복 연결 감지 - 기존 세션을 만료 처리합니다.", userId);
            try {
                oldEmitter.complete(); // 기존 연결을 닫음
            } catch (Exception e) {
                log.warn("[SSE] 기존 Emitter 종료 중 에러 발생 (무시 가능): {}", e.getMessage());
            }
            return null; // Map에서 제거 처리됨
        });

        // 2. 신규 SseEmitter 생성
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        // 3. Emitter 완료(Completion) 시 콜백 등록
        // 네트워크 종료, 브라우저 이탈 등으로 연결이 정상 종료되었을 때 호출됩니다.
        emitter.onCompletion(() -> {
            log.info("[SSE] 연결 완료 콜백 트리거 - 유저: {}", userId);
            // 중요: 유저 ID와 Emitter 인스턴스 둘 다 일치할 때만 Map에서 제거합니다 (인스턴스 동등성 비교).
            // 만약 유저가 재연결을 해서 신규 Emitter를 생성해 놨는데, 예전 Emitter의 만료 콜백이 불려서
            // 신규 세션까지 지워버리는 대참사를 방지합니다.
            emitters.remove(userId, emitter);
        });

        // 4. Emitter 타임아웃(Timeout) 시 콜백 등록
        // 타임아웃 발생 시 complete()를 호출하여 명시적으로 소켓을 닫아주어야 톰캣의 커넥터 리소스가 반환됩니다.
        emitter.onTimeout(() -> {
            log.info("[SSE] 연결 타임아웃 발생 - 유저: {}", userId);
            emitter.complete();
            emitters.remove(userId, emitter);
        });

        // 5. Emitter 에러(Error) 발생 시 콜백 등록
        // 네트워크 끊김 등의 하부 예외가 터지면 즉시 completeWithError로 세션을 회수합니다.
        emitter.onError((ex) -> {
            log.warn("[SSE] 연결 에러 콜백 트리거 - 유저: {}, 사유: {}", userId, ex.getMessage());
            emitter.completeWithError(ex);
            emitters.remove(userId, emitter);
        });

        // 6. 503 Service Unavailable 방지용 초기 더미 데이터 전송
        // Nginx Ingress나 클라이언트 브라우저가 첫 5초간 아무런 데이터를 받지 못하면
        // 연결이 유효하지 않은 것으로 간주하고 강제로 끊어버리는 현상을 막기 위함입니다.
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .id("init_" + System.currentTimeMillis())
                    .data("connected"));
            log.info("[SSE] 초기 더미 이벤트 송신 완료 - 유저: {}", userId);
        } catch (IOException e) {
            log.error("[SSE] 초기 더미 이벤트 송신 실패 - 즉시 세션을 폐기합니다: {}", userId);
            emitter.completeWithError(e);
            emitters.remove(userId, emitter);
            return emitter;
        }

        // 7. 안전해진 Emitter를 최종 저장소에 등록
        emitters.put(userId, emitter);
        log.info("[SSE] 신규 세션 등록 완료. 현재 활성화된 채널 수: {}", emitters.size());

        return emitter;
    }

    /**
     * 특정 유저에게 실시간 이벤트를 비동기로 안전하게 전송합니다.
     * AsyncConfig에서 튜닝한 Notification- 쓰레드 풀에서 전송 동작: tomcat 메인 쓰레드나, 카프카 쓰레드 블로킹 x
     */
    @Async
    public void send(String userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);

        if (emitter == null) {
            log.debug("[SSE] 알림 발송 실패 - 유저 '{}'가 오프라인 상태입니다.", userId);
            return;
        }

        log.info("[SSE] 알림 전송 시작 - 유저: {}, 스레드: {}", userId, Thread.currentThread().getName());
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .id(String.valueOf(System.currentTimeMillis()))
                    .data(data));
            log.info("[SSE] 실시간 이벤트 푸시 성공 - 수신자: {}, 이벤트: {}", userId, eventName);
        } catch (IOException e) {
            // 전송 실패 시 이미 소켓이 망가진 상태이므로, 즉시 메모리 테이블에서 털어내야
            // 다음 루프에서 무의미한 CPU 전송 낭비가 발생하지 않습니다.
            log.warn("[SSE] 전송 실패로 인한 세션 강제 회수 처리 - 유저: {}", userId);
            emitter.completeWithError(e);
            emitters.remove(userId, emitter);
        }
    }

    /**
     * 현재 서버에 실시간으로 접속해서 알림 스트림을 쥐고 있는 유저들의 총 채널 개수를 반환합니다.
     * 대시보드 모니터링용 메트릭으로 활용하기 좋습니다.
     */
    public int getActiveSessionsCount() {
        return emitters.size();
    }
}
