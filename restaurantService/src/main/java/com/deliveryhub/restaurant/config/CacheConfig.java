package com.deliveryhub.restaurant.config;

import com.deliveryhub.restaurant.dto.MenuItemView;
import com.deliveryhub.restaurant.dto.RestaurantView;
import com.deliveryhub.restaurant.foodfacts.FoodFactsHttp;
import com.deliveryhub.restaurant.foodfacts.FoodFactsProperties;
import com.deliveryhub.restaurant.mealdb.MealDbHttp;
import com.deliveryhub.restaurant.mealdb.MealDbProperties;
import com.deliveryhub.restaurant.service.CatalogCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(CatalogCacheProperties.class)
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
    public RedisCacheManagerBuilderCustomizer externalApiCaches(MealDbProperties mealDb, FoodFactsProperties foodFacts) {
        return builder -> builder
                .withCacheConfiguration(MealDbHttp.CACHE, externalApiCache(mealDb.cacheTtl()))
                .withCacheConfiguration(FoodFactsHttp.CACHE, externalApiCache(foodFacts.cacheTtl()));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer catalogCaches(CatalogCacheProperties properties) {
        TypeFactory types = TypeFactory.createDefaultInstance();
        JavaType restaurantList = types.constructCollectionType(List.class, RestaurantView.class);
        JavaType menuList = types.constructCollectionType(List.class, MenuItemView.class);
        return builder -> builder
                .withCacheConfiguration(CatalogCache.RESTAURANTS,
                        catalogCache(properties.ttl(), new JacksonJsonRedisSerializer<>(restaurantList)))
                .withCacheConfiguration(CatalogCache.RESTAURANT,
                        catalogCache(properties.ttl(), new JacksonJsonRedisSerializer<>(RestaurantView.class)))
                .withCacheConfiguration(CatalogCache.MENU,
                        catalogCache(properties.ttl(), new JacksonJsonRedisSerializer<>(menuList)));
    }

    private static RedisCacheConfiguration catalogCache(Duration ttl, RedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("restaurant:")
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }

    private static RedisCacheConfiguration externalApiCache(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("restaurant:")
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.string()));
    }
}
