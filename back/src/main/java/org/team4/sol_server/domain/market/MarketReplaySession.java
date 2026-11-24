package org.team4.sol_server.domain.market;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 장 상태 옆 Test 버튼용 시세 재생(Replay) 세션.
 *
 * - Kafka `stock_prices` 토픽(3 파티션)을 **재생 전용 신규 컨슈머 그룹**으로 구독
 *   (group.id = market-replay-{uuid} → 기존 컨슈머 그룹과 완전 격리, 기존 offset 무영향)
 * - enable.auto.commit=false 로 offset을 절대 커밋하지 않음(읽기 전용 재생)
 * - assign + seekToBeginning 으로 명시적으로 offset 0부터 읽음
 * - 프로듀서가 심은 `date` 필드로 요청 날짜(기본: 오늘) 수집분만 필터링
 * - 재생은 SSE로 배치(throttle) 전송하여 프론트 폭주 방지
 */
@Slf4j
public class MarketReplaySession implements Runnable {

    public static final String TOPIC = "stock_prices";
    private static final int PARTITION_COUNT = 3;
    private static final long MAX_PLAY_TIME_MS = 60_000L;  // 안전 상한: 최대 60초
    private static final int BATCH_SIZE = 20;              // 한 번에 묶어 보낼 이벤트 수 (실시간성 강화)
    private static final long BATCH_INTERVAL_MS = 30;      // 배치 전송 간격(속도 제한)

    private final ConsumerHolder consumerHolder;
    private final SseEmitter emitter;
    private final String replayDate;      // yyyy-MM-dd
    private final long batchIntervalMs;   // 배속 조절용
    private final Executor executor;
    private volatile boolean cancelled = false;

    public MarketReplaySession(SseEmitter emitter, String bootstrapServers,
                               String replayDate, long batchIntervalMs, Executor executor) {
        this.emitter = emitter;
        this.executor = executor;
        this.batchIntervalMs = batchIntervalMs;
        this.replayDate = (replayDate == null || replayDate.isBlank())
                ? LocalDate.now().toString() : replayDate;

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        // 재생 전용 그룹: 요청마다 고유 그룹 생성 → 커밋 이력/기존 그룹과 완전 분리
        props.put("group.id", "market-replay-" + UUID.randomUUID());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", "false"); // 재생은 offset을 절대 커밋하지 않음
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        this.consumerHolder = new ConsumerHolder(consumer, TOPIC, PARTITION_COUNT);
    }

    /** SSE 스트림 시작 (Controller가 호출) */
    public void start() {
        executor.execute(this);
    }

    public void cancel() {
        this.cancelled = true;
        consumerHolder.getConsumer().wakeup();
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        long sentCount = 0;
        KafkaConsumer<String, String> consumer = consumerHolder.getConsumer();
        try {
            consumerHolder.assignFromBeginning();

            sendEvent("replay_start", "{\"date\":\"" + replayDate + "\"}");

            List<String> batch = new ArrayList<>();
            LocalDate targetDate = LocalDate.parse(replayDate);
            long lastDataTime = System.currentTimeMillis();

            while (!cancelled && !Thread.currentThread().isInterrupted()) {
                var records = consumer.poll(Duration.ofMillis(200));
                boolean gotData = false;
                for (ConsumerRecord<String, String> record : records) {
                    gotData = true;
                    String key = record.key();
                    String value = record.value();

                    // ticker가 없거나 장 상태(-1) 메시지는 시세 재생 대상 제외
                    if (key == null || "-1".equals(key) || value == null) {
                        continue;
                    }

                    // 프로듀서가 심은 date 필드로 당일 데이터만 필터
                    if (isTargetDate(value, targetDate)) {
                        String enriched = value.startsWith("{")
                                ? "{\"ticker\":\"" + key + "\"," + value.substring(1)
                                : value;
                        batch.add(enriched);
                        sentCount++;
                    }
                    if (batch.size() >= BATCH_SIZE) {
                        flushBatch(batch);
                        batch.clear();
                        Thread.sleep(batchIntervalMs > 0 ? batchIntervalMs : BATCH_INTERVAL_MS);
                    }
                }
                if (gotData) lastDataTime = System.currentTimeMillis();

                // 데이터가 더 이상 안 오면(현재 시점까지 도달) 종료
                if (!gotData && System.currentTimeMillis() - lastDataTime > 3000) {
                    break;
                }

                // 안전 상한 도달
                if (System.currentTimeMillis() - start > MAX_PLAY_TIME_MS) {
                    log.warn("[Replay] 최대 재생 시간 도달 - 강제 종료. sent={}", sentCount);
                    break;
                }
            }

            if (!batch.isEmpty()) flushBatch(batch);

            sendEvent("replay_complete",
                    "{\"sent\":" + sentCount + ",\"elapsedMs\":" + (System.currentTimeMillis() - start) + "}");
            emitter.complete();
            log.info("[Replay] 재생 완료. date={}, sent={}, elapsed={}ms",
                    replayDate, sentCount, System.currentTimeMillis() - start);

        } catch (org.apache.kafka.common.errors.WakeupException e) {
            emitter.complete();
        } catch (Exception e) {
            log.error("[Replay] 재생 중 오류 발생. date={}", replayDate, e);
            sendEvent("replay_error",
                    "{\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}");
            emitter.completeWithError(e);
        } finally {
            consumer.close();
            log.info("[Replay] consumer closed.");
        }
    }

    private boolean isTargetDate(String value, LocalDate targetDate) {
        try {
            int idx = value.indexOf("\"date\"");
            if (idx < 0) return false;
            int colon = value.indexOf(':', idx);
            int q1 = value.indexOf('"', colon + 1);
            int q2 = value.indexOf('"', q1 + 1);
            if (colon < 0 || q1 < 0 || q2 < 0) return false;
            return targetDate.toString().equals(value.substring(q1 + 1, q2));
        } catch (Exception e) {
            return false;
        }
    }

    private void flushBatch(List<String> batch) {
        if (batch.isEmpty()) return;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < batch.size(); i++) {
            sb.append(batch.get(i));
            if (i < batch.size() - 1) sb.append(",");
        }
        sb.append("]");
        sendEvent("replay_batch", sb.toString());
    }

    private void sendEvent(String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.warn("[Replay] SSE 전송 실패(클라이언트 연결 종료 추정): {}", e.getMessage());
            cancelled = true;
            consumerHolder.getConsumer().wakeup();
        }
    }

    /** KafkaConsumer 래퍼 (테스트/캡슐화용) */
    public static class ConsumerHolder {
        private final KafkaConsumer<String, String> consumer;
        private final String topic;
        private final int partitionCount;

        public ConsumerHolder(KafkaConsumer<String, String> consumer, String topic, int partitionCount) {
            this.consumer = consumer;
            this.topic = topic;
            this.partitionCount = partitionCount;
        }

        public KafkaConsumer<String, String> getConsumer() { return consumer; }

        /** 파티션 전체 assign + offset 0(가장 오래된)으로 이동 */
        public void assignFromBeginning() {
            List<TopicPartition> partitions = new ArrayList<>();
            for (int p = 0; p < partitionCount; p++) {
                partitions.add(new TopicPartition(topic, p));
            }
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
        }
    }
}
