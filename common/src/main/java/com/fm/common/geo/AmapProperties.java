package com.fm.common.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 高德 Web 服务（地理编码）配置。
 */
@ConfigurationProperties(prefix = "amap")
public class AmapProperties {

    /** 是否启用地理编码（缺 key 时自动视为关闭） */
    private boolean enabled = true;

    /** 高德 Web 服务 Key（建议通过环境变量 AMAP_KEY 注入） */
    private String key = "";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 8000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
