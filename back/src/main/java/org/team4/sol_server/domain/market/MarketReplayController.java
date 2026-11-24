package org.team4.sol_server.domain.market;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

/**
 * 장 상태 옆 Test 버튼용 시세 재생(Replay) SSE 컨트롤러.
 * - Kafka stock_prices 토픽을 재생 전용 컨슈머 그룹으로 읽어 오늘/지정일 수집분을 SSE로 스트리밍
 * - offset 커밋 없음(읽기 전용), 기존 컨슈머 그룹과 완전 격리
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketReplayController {

    private final ThreadPoolTaskExecutor replayExecutor;

    /**
     * GET /api/market/replay?date=2026-08-24&interval=30
     * - date: 재생할 날짜 (기본: 오늘)
     * - interval: 배치 전송 간격 ms (기본 30) → 재생 속도 조절용
     */
    @GetMapping(value = "/replay", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter replay(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "30") long interval) {

        SseEmitter emitter = new SseEmitter(0L); // 무제한 타임아웃(재생 종료 시 complete)

        Executor executor = replayExecutor;
        MarketReplaySession session = new MarketReplaySession(
                emitter, getBootstrapServers(), date, interval, executor);

        emitter.onCompletion(session::cancel);
        emitter.onTimeout(session::cancel);

        session.start();
        return emitter;
    }

    private String getBootstrapServers() {
        // KafkaConsumer는 KafkaConsumerConfig의 bootstrap.servers와 동일한 주소 사용
        return System.getenv().getOrDefault("SPRING_KAFKA_BOOTSTRAP_SERVERS", "broker:9094");
    }
}
