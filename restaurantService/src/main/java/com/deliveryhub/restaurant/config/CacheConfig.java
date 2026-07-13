package com.deliveryhub.restaurant.config;

import com.deliveryhub.restaurant.mealdb.MealDbHttp;
import com.deliveryhub.restaurant.mealdb.MealDbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache {} is unavailable on read, going to the source: {}", cache.getName(), exception.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache {} is unavailable on write: {}", cache.getName(), exception.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache {} is unavailable on evict: {}", cache.getName(), exception.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache {} is unavailable on clear: {}", cache.getName(), exception.toString());
            }
        };
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer externalApiCaches(MealDbProperties mealDb) {
        return builder -> builder.withCacheConfiguration(MealDbHttp.CACHE, RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(mealDb.cacheTtl())
                .disableCachingNullValues()
                .prefixCacheNameWith("restaurant:")
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.string())));
    }
}
