package org.team4.sol_server.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> getSubscribe(@PathVariable String userId){
        log.info("[Spring SSE Controller] 신규 구독 HTTP 요청 수신 - 유저 ID: {}", userId);

        SseEmitter emitter = notificationService.subscribe(userId);

        // traefik용 버퍼링 방지용 헤더 설정 + nginx 프록시 버퍼링 방지 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cache-Control", "no-cache, no-transform"); //
        headers.set("X-Accel-Buffering", "no");
        headers.set("Connection", "keep-alive");

        return new ResponseEntity<>(emitter, headers, HttpStatus.OK);
    }

    /**
     * 대시보드가 주기적으로 현재 서버의 실시간 동시 접속자 수(SSE 세션 수)를 조회해 갈 수 있는 가벼운 API입니다.
     * React 대시보드 화면에서 단발성 fetch(Polling)로 부르거나, Prometheus 같은 모니터링 수집기가 사용하기 적합합니다.
     * @return 현재 활성 SSE 커넥션 개수 (Integer)
     */
    @GetMapping("/active-count")
    public ResponseEntity<Integer> getActiveSessionsCount() {
        int count = notificationService.getActiveSessionsCount();
        return ResponseEntity.ok(count);
    }
}
