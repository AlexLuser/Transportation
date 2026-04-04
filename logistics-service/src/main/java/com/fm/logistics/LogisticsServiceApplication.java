package com.fm.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 物流服务启动类
 * 负责运输路线管理、实时轨迹追踪
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients     // B6：启用 Feign，用于调用 driver-service 做 userId→driverId 转换
@ComponentScan(basePackages = {"com.fm.logistics", "com.fm.common"})  // 扫描 common 模块组件
public class LogisticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogisticsServiceApplication.class, args);
    }
}
