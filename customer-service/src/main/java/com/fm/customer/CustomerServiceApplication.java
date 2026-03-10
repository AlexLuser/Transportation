package com.fm.customer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient  // 启用服务发现（Nacos）
@EnableFeignClients     // 启用Feign客户端
@MapperScan("com.fm.customer.mapper")  // 扫描MyBatis Mapper接口
@ComponentScan(basePackages = {"com.fm.customer", "com.fm.common"})  // 扫描customer和common模块的组件
public class CustomerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
