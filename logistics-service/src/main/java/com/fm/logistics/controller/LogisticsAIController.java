package com.fm.logistics.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.logistics.dto.ai.*;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.service.LogisticsAIService;
import com.fm.logistics.service.LogisticsRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 物流 AI 对外接口 Controller（手动触发类）
 *
 * 说明：
 *   ETA 预测由 LLMService 在 GPS 上报时自动触发（每10次上报重算一次），
 *   结果直接写入 logistics_route.estimated_arrival_time 和 ai_analysis，
 *   不再作为独立 HTTP 端点暴露，避免接口职责混乱。
 *
 * 当前接口：
 * POST /api/logistics/ai/route-suggest          - 手动重新触发路线分析（路况突变时使用）
 * GET  /api/logistics/ai/anomaly/{routeId}      - 路线异常检测（含 LLM 自然语言解释）
 * POST /api/logistics/ai/dispatch-optimize      - 智能调度优化（预留）
 * POST /api/logistics/ai/nlp-query              - 自然语言查询（预留）
 * POST /api/logistics/ai/apply-route/{routeId}  - 将 AI 路线建议 GeoJSON 写入路线记录
 */
@Tag(name = "物流AI接口", description = "大模型驱动：路线重分析（含历史数据挖掘）、异常检测与解释、调度优化（预留）、自然语言查询（预留）")
@RestController
@RequestMapping("/api/logistics/ai")
public class LogisticsAIController {

    @Autowired
    private LogisticsAIService aiService;

    @Autowired
    private LogisticsRouteService routeService;

    /**
     * 路线规划建议
     * 传入出发地/目的地/车辆信息，获取 AI 推荐路线
     */
    @Operation(summary = "【AI预留】路线规划建议",
            description = "传入出发地、目的地、车辆信息及约束条件，获取 AI 推荐最优路线（GeoJSON格式）。" +
                    "当前为占位实现，接入大模型后可返回真实路线建议。")
    @PostMapping("/route-suggest")
    public Result<AIRouteSuggestResponseDTO> suggestRoute(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @RequestBody AIRouteSuggestRequestDTO request) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可调用AI路线规划接口");
        }
        AIRouteSuggestResponseDTO response = aiService.suggestRoute(request);
        return Result.success(response);
    }

    /**
     * 路线异常检测
     */
    @Operation(summary = "【AI预留】路线异常检测",
            description = "检测指定路线是否存在超时、信号丢失、路线偏离、速度异常等情况。" +
                    "当前基于规则检测，接入大模型后可对轨迹序列进行深度分析。")
    @GetMapping("/anomaly/{routeId}")
    public Result<AIAnomalyCheckResponseDTO> checkAnomaly(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "路线ID") @PathVariable Long routeId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        AIAnomalyCheckResponseDTO response = aiService.checkAnomaly(routeId);
        return Result.success(response);
    }

    /**
     * 智能调度优化
     */
    @Operation(summary = "【AI预留】智能调度优化",
            description = "传入待配送路线列表和可用运输员列表，AI 给出最优派单方案。" +
                    "当前为占位实现，接入 VRP 求解器或大模型后可返回真实调度方案。")
    @PostMapping("/dispatch-optimize")
    public Result<AIDispatchOptimizeResponseDTO> optimizeDispatch(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @RequestBody AIDispatchOptimizeRequestDTO request) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可调用智能调度接口");
        }
        AIDispatchOptimizeResponseDTO response = aiService.optimizeDispatch(request);
        return Result.success(response);
    }

    /**
     * 自然语言查询（买家、运输员、管理员均可使用）
     */
    @Operation(summary = "【AI预留】自然语言查询",
            description = "用自然语言提问物流相关问题，如'我的订单到哪了'、'还有多久到'。" +
                    "当前为占位实现，接入大模型（RAG架构）后可提供智能问答。")
    @PostMapping("/nlp-query")
    public Result<AINLPQueryResponseDTO> nlpQuery(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @RequestBody AINLPQueryRequestDTO request) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 将用户信息注入请求
        request.setUserId(Long.parseLong(userIdHeader));
        request.setUserRole(roleCode);
        AINLPQueryResponseDTO response = aiService.nlpQuery(request);
        return Result.success(response);
    }

    /**
     * 将 AI 建议路线应用到指定物流路线（保存 AI 推荐的 GeoJSON 路线）
     * 请求体：{"aiSuggestedRoute": "{...}", "aiAnalysis": "..."}
     */
    @Operation(summary = "【AI预留】应用AI路线建议",
            description = "将 AI 路线规划结果保存到物流路线记录中，供前端地图展示")
    @PostMapping("/apply-route/{routeId}")
    public Result<LogisticsRoute> applyAIRoute(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "路线ID") @PathVariable Long routeId,
            @RequestBody Map<String, String> body) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可应用AI路线建议");
        }
        String aiRoute = body.get("aiSuggestedRoute");
        String aiAnalysis = body.get("aiAnalysis");
        LogisticsRoute route = routeService.updateAIRoute(routeId, aiRoute, aiAnalysis);
        return Result.success(route);
    }
}

