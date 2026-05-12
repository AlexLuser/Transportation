package com.fm.driver.config;

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
 * 缓存范围（driver-service）：
 *   - driverByUser           userId→driver 身份映射，TTL 10 min
 *   - driverById             司机详情，TTL 10 min
 *   - inProgressDeliveries   司机在途配送列表，TTL 10 s
 *                            DriverHome / Navigation / GpsTest 均会调用，GpsTest 存在 setInterval 轮询
 *                            10 s TTL 既能减轻数据库压力，又保证状态感知及时
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
        configs.put("driverByUser",           base.entryTtl(Duration.ofMinutes(10)));
        configs.put("driverById",             base.entryTtl(Duration.ofMinutes(10)));
        configs.put("inProgressDeliveries",   base.entryTtl(Duration.ofSeconds(10)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
