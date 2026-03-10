package com.fm.logistics.dto;

import com.fm.logistics.entity.LogisticsNode;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.entity.LogisticsTrack;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

/**
 * 物流路线详情 DTO（买家/运输员查询使用）
 */
@Data
@Schema(description = "物流路线详情")
public class RouteDetailDTO {

    @Schema(description = "路线主信息")
    private LogisticsRoute route;

    @Schema(description = "里程碑节点列表（按 sequenceNo 升序）")
    private List<LogisticsNode> nodes;

    @Schema(description = "最近的轨迹点（最新 N 条，用于轨迹回放）")
    private List<LogisticsTrack> recentTracks;

    @Schema(description = "当前位置描述（冗余字段，方便前端直接展示）")
    private String currentPositionDesc;

    @Schema(description = "路线状态描述")
    private String statusDesc;
}

