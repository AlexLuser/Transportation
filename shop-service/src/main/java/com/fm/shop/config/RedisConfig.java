package com.fm.shop.config;

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
 * 缓存范围（shop-service）：
 *   - product       商品详情，TTL 10 min（下单 Feign 热点路径）
 *   - warehouseById 仓库详情，TTL 60 min（极少变动）
 *   - warehouseAll  全量仓库列表，TTL 30 min
 *   - shopById      商户详情，TTL 30 min
 *   - shopByUser    userId→shopId 身份映射，TTL 30 min（Feign 热点）
 *   - amapGeo       高德地址编码结果，TTL 24 h（节省 API 调用）
 *   - mallProducts  商城商品列表（分页），TTL 2 min（C 端高频浏览，数据允许短暂延迟）
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
        configs.put("product",       base.entryTtl(Duration.ofMinutes(10)));
        configs.put("warehouseById", base.entryTtl(Duration.ofMinutes(60)));
        configs.put("warehouseAll",  base.entryTtl(Duration.ofMinutes(30)));
        configs.put("shopById",      base.entryTtl(Duration.ofMinutes(30)));
        configs.put("shopByUser",    base.entryTtl(Duration.ofMinutes(30)));
        configs.put("amapGeo",       base.entryTtl(Duration.ofHours(24)));
        configs.put("mallProducts",  base.entryTtl(Duration.ofMinutes(2)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
