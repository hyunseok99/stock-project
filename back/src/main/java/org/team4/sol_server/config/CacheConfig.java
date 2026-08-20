package org.team4.sol_server.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    // 로컬(JVM) 전용 CacheManager 등록
    @Bean(name = "localCacheManager")
    public CacheManager localCacheManager() {
        return new ConcurrentMapCacheManager("initStockDataCache");
    }
}
