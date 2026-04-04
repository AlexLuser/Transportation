package com.fm.common.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 高德地理编码 HTTP 调用（<a href="https://lbs.amap.com/api/webservice/guide/api/georegeo">地理编码</a>）。
 * 失败时返回 empty，不抛异常，避免影响地址/仓库保存主流程。
 */
@Service
public class AmapGeocodingService {

    private static final Logger log = LoggerFactory.getLogger(AmapGeocodingService.class);

    private static final String GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo";

    private final AmapProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AmapGeocodingService(
            AmapProperties properties,
            @Qualifier("amapRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 将省市区与详细地址拼接后请求高德地理编码。
     *
     * @param city 可选，传入城市名（或 adcode）有助于提高命中率，对应高德 city 参数
     */
    public Optional<GeoPoint> geocode(String province, String city, String district, String detailAddress, String cityHint) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getKey())) {
            return Optional.empty();
        }
        String full = buildFullAddress(province, city, district, detailAddress);
        if (!StringUtils.hasText(full)) {
            return Optional.empty();
        }
        String cityParam = StringUtils.hasText(cityHint) ? cityHint.trim() : city;

        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(GEOCODE_URL)
                .queryParam("address", full)
                .queryParam("key", properties.getKey());
        if (StringUtils.hasText(cityParam)) {
            b.queryParam("city", cityParam);
        }
        URI uri = b.encode(StandardCharsets.UTF_8).build().toUri();

        try {
            String body = restTemplate.getForObject(uri, String.class);
            if (!StringUtils.hasText(body)) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("高德地理编码失败: status={}, info={}", root.path("status").asText(), root.path("info").asText());
                return Optional.empty();
            }
            JsonNode geocodes = root.path("geocodes");
            if (!geocodes.isArray() || geocodes.size() == 0) {
                return Optional.empty();
            }
            String location = geocodes.get(0).path("location").asText();
            if (!StringUtils.hasText(location)) {
                return Optional.empty();
            }
            String[] parts = location.split(",");
            if (parts.length != 2) {
                return Optional.empty();
            }
            double lng = Double.parseDouble(parts[0].trim());
            double lat = Double.parseDouble(parts[1].trim());
            return Optional.of(new GeoPoint(lat, lng));
        } catch (Exception e) {
            log.warn("高德地理编码异常: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 与 {@link #geocode(String, String, String, String, String)} 相同，city 同时作为 cityHint */
    public Optional<GeoPoint> geocode(String province, String city, String district, String detailAddress) {
        return geocode(province, city, district, detailAddress, city);
    }

    public static String buildFullAddress(String province, String city, String district, String detailAddress) {
        StringBuilder sb = new StringBuilder();
        append(sb, province);
        append(sb, city);
        append(sb, district);
        append(sb, detailAddress);
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            sb.append(part.trim());
        }
    }
}
