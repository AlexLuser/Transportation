package com.fm.common.geo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 高德地理编码 HTTP 客户端与配置绑定。
 */
@Configuration
@EnableConfigurationProperties(AmapProperties.class)
public class AmapGeocodingConfiguration {

    @Bean
    public RestTemplate amapRestTemplate(AmapProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }
}
