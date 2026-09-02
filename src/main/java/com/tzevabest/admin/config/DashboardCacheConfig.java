package com.tzevabest.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Кэш метрик дашборда (T-13.1): результаты живут до 5 минут, инвалидация по расписанию (а не по
 * каждой заявке — иначе кэш бесполезен под нагрузкой).
 */
@Configuration
@EnableCaching
public class DashboardCacheConfig {

    @Bean
    CacheManager dashboardCacheManager() {
        return new ConcurrentMapCacheManager("dashboard");
    }

    /** Полная инвалидация кэша дашборда каждые 5 минут. */
    @Component
    @RequiredArgsConstructor
    static class DashboardCacheEvictor {
        private final CacheManager cacheManager;

        @Scheduled(fixedRateString = "${app.dashboard.cache-evict:PT5M}")
        void evict() {
            var cache = cacheManager.getCache("dashboard");
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
