package com.fm.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

/**
 * 运输员位置上报 DTO
 * 运输员 App 周期性（建议每 10~30 秒）上报当前 GPS 位置
 */
@Data
@Schema(description = "运输员位置上报请求")
public class LocationUpdateDTO {

    @Schema(description = "物流路线ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long routeId;

    @Schema(description = "纬度", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double latitude;

    @Schema(description = "经度", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double longitude;

    @Schema(description = "海拔（米，可选）")
    private Double altitude;

    @Schema(description = "速度（km/h，可选）")
    private Double speed;

    @Schema(description = "方向角（0-360度，0=正北，可选）")
    private Double heading;

    @Schema(description = "GPS精度（米，可选）")
    private Double accuracy;

    @Schema(description = "当前位置描述（前端逆地理编码结果，可选）")
    private String address;

    @Schema(description = "客户端上报时间（毫秒时间戳，精确时间）")
    private Long trackTimeMs;
}

