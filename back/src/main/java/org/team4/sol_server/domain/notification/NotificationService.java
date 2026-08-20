package org.team4.sol_server.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final SseEmitterManager sseEmitterManager;

    /**
     * 특정 사용자의 실시간 알림 채널 구독을 처리합니다.
     * @param userId 구독을 요청한 유저 ID
     * @return SseEmitter 객체
     */
    public SseEmitter subscribe(String userId){
        log.info("[NotificationService] 유저 '{}'에 대한 알림 구독 비즈니스 로직 시작", userId);
        return sseEmitterManager.createEmitter(userId);
    }

    /**
     * 📊 현재 서버에 연결된 모든 활성 SSE 클라이언트(세션) 수를 가져옵니다.
     * 대시보드나 모니터링 시스템(Prometheus 등)에 지표를 노출하는 데 사용됩니다.
     */
    public int getActiveSessionsCount() {
        return sseEmitterManager.getActiveSessionsCount();
    }

}
