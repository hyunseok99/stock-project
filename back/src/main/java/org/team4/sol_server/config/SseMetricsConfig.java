package org.team4.sol_server.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.team4.sol_server.domain.notification.NotificationService;

@Configuration
public class SseMetricsConfig {

    @Bean
    public MeterBinder sseMetricsBinder(NotificationService notificationService) {
        return registry -> Gauge.builder("sse_active_connections", notificationService, NotificationService::getActiveSessionsCount)
                .description("현재 서버에 접속중인 실시간 sse 동시 접속자 수")
                .register(registry);
    }
}
