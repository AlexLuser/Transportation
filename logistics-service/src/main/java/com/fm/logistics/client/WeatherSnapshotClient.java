package com.fm.logistics.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 拉取「某日」多城市天气摘要（Open-Meteo Archive，无需 API Key）。
 * 失败时返回简短模拟文案，不阻断规划流程。
 */
@Component
public class WeatherSnapshotClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherSnapshotClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 北京、上海、广州 — 代表华北/华东/华南干线 */
    private static final double[][] CITY = {
            {39.9042, 116.4074},
            {31.2304, 121.4737},
            {23.1291, 113.2644},
    };

    private static final String[] CITY_NAMES = {"北京", "上海", "广州"};

    public WeatherSnapshotClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(6000);
        this.restTemplate = new RestTemplate(f);
    }

    /**
     * @param date 须为过去日期（与 archive API 一致）；返回文案<strong>不含具体日历日</strong>，便于模型按「当下情境」理解
     */
    public String summarizeForDate(LocalDate date) {
        if (date == null) {
            return "无日期，略。";
        }
        String ds = date.toString();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < CITY.length; i++) {
            double lat = CITY[i][0];
            double lon = CITY[i][1];
            String label = CITY_NAMES[i];
            try {
                String url = String.format(
                        "https://archive-api.open-meteo.com/v1/archive?latitude=%f&longitude=%f&start_date=%s&end_date=%s&daily=weather_code,precipitation_sum&timezone=Asia%%2FShanghai",
                        lat, lon, ds, ds);
                ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
                if (resp.getBody() == null || resp.getBody().isBlank()) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(resp.getBody());
                JsonNode daily = root.path("daily");
                int code = daily.path("weather_code").isArray() && daily.path("weather_code").size() > 0
                        ? daily.path("weather_code").get(0).asInt(-1) : -1;
                double precip = daily.path("precipitation_sum").isArray() && daily.path("precipitation_sum").size() > 0
                        ? daily.path("precipitation_sum").get(0).asDouble(0) : 0;
                parts.add(String.format("%s %s 日降水约%.1fmm", label, wmoShort(code), precip));
            } catch (Exception e) {
                log.debug("[Weather] {} {}: {}", label, ds, e.getMessage());
            }
        }
        if (parts.isEmpty()) {
            return mockWeather();
        }
        return String.join("；", parts) + "。";
    }

    private static String wmoShort(int code) {
        if (code < 0) {
            return "未知";
        }
        if (code == 0) {
            return "晴";
        }
        if (code <= 3) {
            return "多云";
        }
        if (code >= 51 && code <= 67) {
            return "有雨";
        }
        if (code >= 71 && code <= 77) {
            return "有雪";
        }
        if (code >= 80 && code <= 82) {
            return "阵雨";
        }
        if (code >= 95) {
            return "对流/雷暴风险";
        }
        if (code >= 45 && code <= 48) {
            return "雾或霾";
        }
        return "天气码" + code;
    }

    private static String mockWeather() {
        return "（天气接口不可用，以下为系统模拟）华北多云、华东阴到多云、华南局部小雨，无极端灾害性天气提示。";
    }
}
