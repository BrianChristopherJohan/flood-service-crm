package com.fyp.floodmonitoring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.net.URI;
import java.time.Duration;

/**
 * Cache configuration.
 *
 * When REDIS_URL is set, uses Redis.
 * When REDIS_URL is absent or blank, uses in-memory cache for local dev.
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private static final String[] CACHE_NAMES =
            {"analytics", "sensors", "blogs", "dashboard", "safety", "zones"};

    @Bean
    public CacheManager cacheManager(@Value("${REDIS_URL:}") String redisUrl) {
        if (redisUrl != null && !redisUrl.isBlank()) {
            RedisConnectionFactory factory = redisConnectionFactory(redisUrl);
            GenericJackson2JsonRedisSerializer serializer =
                    new GenericJackson2JsonRedisSerializer();

            RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(5))
                    .disableCachingNullValues()
                    .serializeValuesWith(
                            RedisSerializationContext.SerializationPair.fromSerializer(serializer));

            return RedisCacheManager.builder(factory)
                    .cacheDefaults(defaults)
                    .withCacheConfiguration("analytics",
                            defaults.entryTtl(Duration.ofMinutes(5)))
                    .withCacheConfiguration("sensors",
                            defaults.entryTtl(Duration.ofSeconds(30)))
                    .withCacheConfiguration("blogs",
                            defaults.entryTtl(Duration.ofMinutes(60)))
                    .withCacheConfiguration("dashboard",
                            defaults.entryTtl(Duration.ofMinutes(1)))
                    .withCacheConfiguration("safety",
                            defaults.entryTtl(Duration.ofMinutes(60)))
                    .withCacheConfiguration("zones",
                            defaults.entryTtl(Duration.ofMinutes(60)))
                    .build();
        }

        log.info("REDIS_URL is not configured; using in-memory cache for local dev");
        return new ConcurrentMapCacheManager(CACHE_NAMES);
    }

    private RedisConnectionFactory redisConnectionFactory(String redisUrl) {
        URI uri = URI.create(redisUrl.trim());
        String scheme = uri.getScheme();
        if (!"redis".equalsIgnoreCase(scheme) && !"rediss".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "REDIS_URL must start with redis:// or rediss:// when configured");
        }

        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration();
        server.setHostName(uri.getHost());
        server.setPort(uri.getPort() > 0 ? uri.getPort() : 6379);

        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int passwordStart = userInfo.indexOf(':');
            String password = passwordStart >= 0
                    ? userInfo.substring(passwordStart + 1)
                    : userInfo;
            if (!password.isBlank()) {
                server.setPassword(RedisPassword.of(password));
            }
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder client =
                LettuceClientConfiguration.builder();
        if ("rediss".equalsIgnoreCase(scheme)) {
            client.useSsl();
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(server, client.build());
        factory.afterPropertiesSet();
        return factory;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache GET error on '{}' key='{}': {} - proceeding without cache",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache PUT error on '{}' key='{}': {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache EVICT error on '{}' key='{}': {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache CLEAR error on '{}': {}", cache.getName(), ex.getMessage());
            }
        };
    }
}
