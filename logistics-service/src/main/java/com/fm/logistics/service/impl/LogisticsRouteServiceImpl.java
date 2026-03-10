package com.fm.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.logistics.dto.CreateRouteRequestDTO;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.dto.llm.HistoricalStats;
import com.fm.logistics.dto.llm.LLMRouteAnalysisResult;
import com.fm.logistics.entity.LogisticsNode;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.entity.LogisticsTrack;
import com.fm.logistics.mapper.LogisticsNodeMapper;
import com.fm.logistics.mapper.LogisticsRouteMapper;
import com.fm.logistics.mapper.LogisticsTrackMapper;
import com.fm.logistics.service.HistoricalAnalysisService;
import com.fm.logistics.service.LLMService;
import com.fm.logistics.service.LogisticsRouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class LogisticsRouteServiceImpl extends ServiceImpl<LogisticsRouteMapper, LogisticsRoute>
        implements LogisticsRouteService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsRouteServiceImpl.class);

    @Autowired
    private LogisticsRouteMapper routeMapper;

    @Autowired
    private LogisticsNodeMapper nodeMapper;

    @Autowired
    private LogisticsTrackMapper trackMapper;

    @Autowired
    private LLMService llmService;

    @Autowired
    private HistoricalAnalysisService historicalAnalysisService;

    @Override
    @Transactional
    public LogisticsRoute createRoute(CreateRouteRequestDTO request) {
        // 幂等检查：同一订单只创建一条路线
        LambdaQueryWrapper<LogisticsRoute> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(LogisticsRoute::getOrderId, request.getOrderId());
        LogisticsRoute existing = routeMapper.selectOne(existWrapper);
        if (existing != null) {
            return existing;
        }

        // 创建路线
        LogisticsRoute route = new LogisticsRoute();
        route.setRouteNo(generateRouteNo());
        route.setOrderId(request.getOrderId());
        route.setWarehouseId(request.getWarehouseId());
        route.setStartAddress(request.getStartAddress());
        route.setStartLat(request.getStartLat());
        route.setStartLng(request.getStartLng());
        route.setEndAddress(request.getEndAddress());
        route.setEndLat(request.getEndLat());
        route.setEndLng(request.getEndLng());
        route.setReceiverName(request.getReceiverName());
        route.setReceiverPhone(request.getReceiverPhone());
        route.setRouteStatus(0);  // 待出发
        routeMapper.insert(route);

        // 自动创建起始节点和终止节点
        createDefaultNodes(route);

        // 异步触发 LLM 分析（不阻塞路线创建主流程）
        triggerLLMRouteAnalysis(route);

        return route;
    }

    /**
     * 异步触发 LLM 路线分析
     * 步骤：
     *   1. 聚合历史数据（HistoricalAnalysisService）
     *   2. 调用 LLMService.analyzeRoute()（含 Prompt 构建和 HTTP 调用）
     *   3. 将分析结果写回 logistics_route.estimated_arrival_time 和 ai_analysis
     *
     * 使用 @Async 异步执行：LLM 响应可能需要 5-30s，不应阻塞订单发货流程。
     * 若 LLM 不可用，降级结果也会写入，确保字段不为空。
     */
    @Async
    public void triggerLLMRouteAnalysis(LogisticsRoute route) {
        try {
            // 1. 获取出发小时（用于时段特征分析）
            int departHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

            // 2. 聚合历史数据
            HistoricalStats historical = historicalAnalysisService
                    .analyzeByDestination(route.getEndAddress(), departHour);
            log.info("[LLM] 路线 {} 历史分析完成：{}条样本，均值{}分钟",
                    route.getRouteNo(), historical.getSampleCount(),
                    (int) historical.getAvgDurationMinutes());

            // 3. 调用 LLM 分析（含 Prompt 构建，支持降级）
            LLMRouteAnalysisResult result = llmService.analyzeRoute(route, historical);

            // 4. 将结果写回路线记录
            LogisticsRoute update = new LogisticsRoute();
            update.setId(route.getId());
            update.setEstimatedArrivalTime(result.getEstimatedArrivalTime());

            // 构建 AI 分析摘要（存储在 ai_analysis 字段，可供前端展示）
            String aiAnalysis = buildAiAnalysisJson(result, historical);
            update.setAiAnalysis(aiAnalysis);

            routeMapper.updateById(update);
            log.info("[LLM] 路线 {} AI 分析结果已写入：预计到达 {}，风险等级 {}，isFallback={}",
                    route.getRouteNo(), result.getEstimatedArrivalTime(),
                    result.getRiskLevel(), result.isFallback());

        } catch (Exception e) {
            log.error("[LLM] 路线 {} 异步分析失败: {}", route.getRouteNo(), e.getMessage(), e);
        }
    }

    /**
     * 将 LLM 分析结果序列化为 JSON 字符串，存入 ai_analysis 字段
     */
    private String buildAiAnalysisJson(LLMRouteAnalysisResult result, HistoricalStats historical) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"estimatedDurationMinutes\":").append(result.getEstimatedDurationMinutes()).append(",");
        sb.append("\"riskLevel\":").append(result.getRiskLevel()).append(",");
        sb.append("\"riskFactors\":").append(listToJson(result.getRiskFactors())).append(",");
        sb.append("\"recommendation\":\"").append(escapeJson(result.getRecommendation())).append("\",");
        sb.append("\"historicalSamples\":").append(historical.getSampleCount()).append(",");
        sb.append("\"historicalAvgMinutes\":").append((int) historical.getAvgDurationMinutes()).append(",");
        sb.append("\"historicalDelayRate\":").append(String.format("%.2f", historical.getDelayRate())).append(",");
        sb.append("\"isFallback\":").append(result.isFallback());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public RouteDetailDTO getRouteDetailByOrderId(Long orderId) {
        LambdaQueryWrapper<LogisticsRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsRoute::getOrderId, orderId);
        LogisticsRoute route = routeMapper.selectOne(wrapper);
        if (route == null) {
            return null;
        }
        return buildRouteDetailDTO(route);
    }

    @Override
    public RouteDetailDTO getRouteDetailById(Long routeId) {
        LogisticsRoute route = routeMapper.selectById(routeId);
        if (route == null) {
            return null;
        }
        return buildRouteDetailDTO(route);
    }

    @Override
    public RouteDetailDTO getRouteDetailByRouteNo(String routeNo) {
        LambdaQueryWrapper<LogisticsRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsRoute::getRouteNo, routeNo);
        LogisticsRoute route = routeMapper.selectOne(wrapper);
        if (route == null) {
            return null;
        }
        return buildRouteDetailDTO(route);
    }

    @Override
    @Transactional
    public LogisticsRoute bindDriver(Long routeId, Long driverId, Long deliveryId) {
        LogisticsRoute route = routeMapper.selectById(routeId);
        if (route == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "物流路线不存在");
        }
        route.setDriverId(driverId);
        route.setDeliveryId(deliveryId);
        route.setRouteStatus(1);  // 运输中
        routeMapper.updateById(route);
        return route;
    }

    @Override
    @Transactional
    public LogisticsRoute updateRouteStatus(Long routeId, Integer routeStatus) {
        LogisticsRoute route = routeMapper.selectById(routeId);
        if (route == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "物流路线不存在");
        }
        route.setRouteStatus(routeStatus);
        if (routeStatus == 2) {  // 已送达
            route.setActualArrivalTime(new Date());
        }
        routeMapper.updateById(route);
        return route;
    }

    @Override
    @Transactional
    public LogisticsRoute updatePlannedRoute(Long routeId, String plannedRouteGeoJson) {
        LogisticsRoute route = routeMapper.selectById(routeId);
        if (route == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "物流路线不存在");
        }
        route.setPlannedRoute(plannedRouteGeoJson);
        routeMapper.updateById(route);
        return route;
    }

    @Override
    @Transactional
    public LogisticsRoute updateAIRoute(Long routeId, String aiSuggestedRouteGeoJson, String aiAnalysis) {
        LogisticsRoute route = routeMapper.selectById(routeId);
        if (route == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "物流路线不存在");
        }
        route.setAiSuggestedRoute(aiSuggestedRouteGeoJson);
        route.setAiAnalysis(aiAnalysis);
        routeMapper.updateById(route);
        return route;
    }

    @Override
    public List<LogisticsRoute> getPendingRoutes() {
        LambdaQueryWrapper<LogisticsRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsRoute::getRouteStatus, 0)
                .orderByAsc(LogisticsRoute::getCreateTime);
        return routeMapper.selectList(wrapper);
    }

    @Override
    public List<LogisticsRoute> getRoutesByDriverId(Long driverId) {
        LambdaQueryWrapper<LogisticsRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsRoute::getDriverId, driverId)
                .orderByDesc(LogisticsRoute::getCreateTime);
        return routeMapper.selectList(wrapper);
    }

    // ---------- 私有方法 ----------

    /**
     * 构建路线详情 DTO
     */
    private RouteDetailDTO buildRouteDetailDTO(LogisticsRoute route) {
        RouteDetailDTO dto = new RouteDetailDTO();
        dto.setRoute(route);

        // 查询节点（按顺序）
        LambdaQueryWrapper<LogisticsNode> nodeWrapper = new LambdaQueryWrapper<>();
        nodeWrapper.eq(LogisticsNode::getRouteId, route.getId())
                .orderByAsc(LogisticsNode::getSequenceNo);
        dto.setNodes(nodeMapper.selectList(nodeWrapper));

        // 查询最近 50 条轨迹
        dto.setRecentTracks(trackMapper.selectLatestTracks(route.getId(), 50));

        // 当前位置描述
        dto.setCurrentPositionDesc(route.getCurrentAddress() != null ?
                route.getCurrentAddress() : route.getStartAddress());

        // 状态描述
        dto.setStatusDesc(getStatusDesc(route.getRouteStatus()));

        return dto;
    }

    /**
     * 自动创建出发节点和目的地节点
     */
    private void createDefaultNodes(LogisticsRoute route) {
        // 出发节点（仓库）
        LogisticsNode startNode = new LogisticsNode();
        startNode.setRouteId(route.getId());
        startNode.setNodeType(0);
        startNode.setNodeName("出发仓库");
        startNode.setNodeAddress(route.getStartAddress());
        startNode.setLatitude(route.getStartLat());
        startNode.setLongitude(route.getStartLng());
        startNode.setSequenceNo(0);
        startNode.setNodeStatus(0);
        nodeMapper.insert(startNode);

        // 目的地节点
        LogisticsNode endNode = new LogisticsNode();
        endNode.setRouteId(route.getId());
        endNode.setNodeType(2);
        endNode.setNodeName("收货地址");
        endNode.setNodeAddress(route.getEndAddress());
        endNode.setLatitude(route.getEndLat());
        endNode.setLongitude(route.getEndLng());
        endNode.setSequenceNo(99);
        endNode.setNodeStatus(0);
        nodeMapper.insert(endNode);
    }

    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待出发";
            case 1 -> "运输中";
            case 2 -> "已送达";
            case 3 -> "运输异常";
            default -> "未知";
        };
    }

    private String generateRouteNo() {
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 10000);
        return "LR" + dateTime + String.format("%04d", random);
    }

    private String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

