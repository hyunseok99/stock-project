package org.team4.sol_server.domain.market;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 시세 재생(Replay) 전용 스레드 풀.
 * - 재생은 컨슈머 poll + SSE 전송을 블로킹 방식으로 수행하므로 별도 풀에서 처리
 * - 동시 재생 세션 제한: 최대 4개
 */
@Configuration
public class ReplayExecutorConfig {

    @Bean(name = "replayExecutor")
    public ThreadPoolTaskExecutor replayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("Replay-");
        executor.initialize();
        return executor;
    }
}
