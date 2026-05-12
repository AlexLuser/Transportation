package com.fm.customer.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置
 *
 * 缓存范围（customer-service）：
 *   - customerByUser userId→customer 身份映射，TTL 30 min（Feign 热点）
 *   - customerById   顾客详情，TTL 30 min
 *   - addressById    地址详情，TTL 10 min（下单和配送流程高频读取）
 *   - addressList    顾客地址列表，TTL 5 min
 *   - amapGeo        高德地址编码结果，TTL 24 h（节省 API 调用）
 */
@EnableCaching
@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("customerByUser", base.entryTtl(Duration.ofMinutes(30)));
        configs.put("customerById",   base.entryTtl(Duration.ofMinutes(30)));
        configs.put("addressById",    base.entryTtl(Duration.ofMinutes(10)));
        configs.put("addressList",    base.entryTtl(Duration.ofMinutes(5)));
        configs.put("amapGeo",        base.entryTtl(Duration.ofHours(24)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
