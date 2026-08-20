package org.team4.sol_server.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {
    @Bean(name = "kafkaFallbackExecutor")
    public Executor kafkaFallbackExecutor(MeterRegistry meterRegistry){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);        // 기본 스레드 수
        executor.setMaxPoolSize(20);       // 최대 스레드 수
        executor.setQueueCapacity(500);    // 대기 큐 크기
        executor.setThreadNamePrefix("KafkaFallback-");
        executor.initialize();

        // 🟢 1. Active 스레드 수 (NaN 절대 발생 안 함)
        Gauge.builder("executor_active_threads", executor, ThreadPoolTaskExecutor::getActiveCount)
                .tag("name", "kafkaFallbackExecutor")
                .description("Active threads count")
                .register(meterRegistry);

        // 🟢 2. 대기 큐 작업 수
        Gauge.builder("executor_queued_tasks", executor, exec -> exec.getThreadPoolExecutor().getQueue().size())
                .tag("name", "kafkaFallbackExecutor")
                .description("Queued tasks count")
                .register(meterRegistry);
        return executor;
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(30); // 알림은 동시에 많이 나갈 수 있으므로 더 크게 설정
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(2000); // 알림이 밀릴 수 있으므로 큐를 넉넉히 지정
        executor.setThreadNamePrefix("Notification-");
        executor.initialize();

        // 🟢 1. Active 스레드 수
        Gauge.builder("executor_active_threads", executor, ThreadPoolTaskExecutor::getActiveCount)
                .tag("name", "notificationExecutor")
                .description("Active threads count")
                .register(meterRegistry);

        // 🟢 2. 대기 큐 작업 수
        Gauge.builder("executor_queued_tasks", executor, exec -> exec.getThreadPoolExecutor().getQueue().size())
                .tag("name", "notificationExecutor")
                .description("Queued tasks count")
                .register(meterRegistry);
        return executor;
    }
}
