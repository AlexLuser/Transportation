package com.fm.driver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置
 */
@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("运输员服务API文档")
                        .description("物流管理系统 - 运输员服务接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Transportation System")
                                .email("support@example.com")));
    }
}
















