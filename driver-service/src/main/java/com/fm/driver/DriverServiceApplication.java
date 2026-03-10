package com.fm.driver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient  // 启用服务发现（Nacos）
@EnableFeignClients     // 启用Feign客户端
@MapperScan("com.fm.driver.mapper")  // 扫描MyBatis Mapper接口
@ComponentScan(basePackages = {"com.fm.driver", "com.fm.common"})  // 扫描driver和common模块的组件
public class DriverServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DriverServiceApplication.class, args);
    }
}

