package com.fm.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 用户服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient  // 启用服务发现（Nacos）
@EnableFeignClients     // 启用Feign客户端（虽然user-service可能不需要调用其他服务，但保留以便后续扩展）
@MapperScan("com.fm.user.mapper")  // 扫描MyBatis Mapper接口
@ComponentScan(basePackages = {"com.fm.user", "com.fm.common"})  // 扫描user和common模块的组件
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
