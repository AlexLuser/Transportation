package com.fm.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.logistics.dto.CreateRouteRequestDTO;
import com.fm.logistics.dto.CreateRouteResponseDTO;
import org.springframework.dao.DuplicateKeyException;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.entity.LogisticsNode;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.mapper.LogisticsNodeMapper;
import com.fm.logistics.mapper.LogisticsRouteMapper;
import com.fm.logistics.mapper.LogisticsTrackMapper;
import com.fm.logistics.dto.RouteResultDTO;
import com.fm.logistics.service.LogisticsRouteService;
import com.fm.logistics.service.RoutePlanningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Service
public class LogisticsRouteServiceImpl implements LogisticsRouteService {

    @Autowired
    private LogisticsRouteMapper logisticsRouteMapper;

    @Autowired
    private LogisticsNodeMapper logisticsNodeMapper;

    @Autowired
    private LogisticsTrackMapper logisticsTrackMapper;

    @Autowired
    private RoutePlanningService routePlanningService;

    @Autowired
    private com.fm.logistics.feign.DriverFeignClient driverFeignClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // ----------------------------------------------------------------
    //  创建路线
    // ----------------------------------------------------------------

    /**
     * 创建物流路线（幂等）
     *
     * 设计要点：
     *  1. LLM/A* 路线规划在事务外执行，避免长事务（规划可能耗时 5~30s）持有 DB 连接，
     *     从而消除并发场景下 MyBatis L1 缓存读旧快照导致补救 SELECT 仍返回 0 的问题。
     *  2. 实际 DB 写操作用 TransactionTemplate 包裹，事务窗口缩短为毫秒级，
     *     并发竞争概率极低；即便出现竞争，DuplicateKeyException 兜底后再次 SELECT
     *     使用全新 SqlSession（空缓存），可正确读到已提交数据。
     */
    @Override
    public CreateRouteResponseDTO createRoute(CreateRouteRequestDTO requestDTO) {
        // ── 1. 快速前置检查（无事务，不持有 DB 连接）──
        LogisticsRoute existing = logisticsRouteMapper.selectOne(
                new LambdaQueryWrapper<LogisticsRoute>().eq(LogisticsRoute::getOrderId, requestDTO.getOrderId()));
        if (existing != null) {
            return CreateRouteResponseDTO.existingRoute(existing);
        }

        // ── 2. 构建路线对象（无事务）──
        LogisticsRoute route = new LogisticsRoute();
        route.setRouteNo(generateRouteNo());
        route.setOrderId(requestDTO.getOrderId());
        route.setWarehouseId(requestDTO.getWarehouseId());
        route.setStartAddress(requestDTO.getStartAddress());
        route.setStartLatitude(requestDTO.getStartLatitude());
        route.setStartLongitude(requestDTO.getStartLongitude());
        route.setEndAddress(requestDTO.getEndAddress());
        route.setEndLatitude(requestDTO.getEndLatitude());
        route.setEndLongitude(requestDTO.getEndLongitude());
        route.setReceiverName(requestDTO.getReceiverName());
        route.setReceiverPhone(requestDTO.getReceiverPhone());
        route.setRouteStatus(0);

        // ── 3. 路径规划（耗时操作，在事务外执行，不阻塞 DB 连接）──
        RouteResultDTO planResult = null;
        if (route.getStartLatitude() != null && route.getStartLongitude() != null
                && route.getEndLatitude() != null && route.getEndLongitude() != null) {
            planResult = routePlanningService.planRoute(
                    route.getStartLatitude(), route.getStartLongitude(),
                    route.getEndLatitude(),   route.getEndLongitude()
            );
            if (planResult != null && planResult.isSuccess()) {
                route.setPlannedRoute(planResult.getRoutePoints().toJsonString());
                route.setEstimatedArrivalTime(
                        new Date(System.currentTimeMillis() + planResult.getDurationMs()));
            }
        }

        // ── 4. 短事务：仅包含 DB 写操作（毫秒级，全新 SqlSession，空缓存）──
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        final RouteResultDTO planResultFinal = planResult;
        return txTemplate.execute(status -> {
            // 事务内二次检查（全新 SqlSession，不受前置检查缓存影响）
            LogisticsRoute recheck = logisticsRouteMapper.selectOne(
                    new LambdaQueryWrapper<LogisticsRoute>().eq(LogisticsRoute::getOrderId, requestDTO.getOrderId()));
            if (recheck != null) {
                return CreateRouteResponseDTO.existingRoute(recheck);
            }

            try {
                logisticsRouteMapper.insert(route);
            } catch (DuplicateKeyException e) {
                // 极小并发窗口兜底：INSERT 前 clearLocalCache 已清空缓存，
                // 此处 SELECT 使用同一 SqlSession 的空缓存直接读库，可见已提交行
                LogisticsRoute committed = logisticsRouteMapper.selectOne(
                        new LambdaQueryWrapper<LogisticsRoute>().eq(LogisticsRoute::getOrderId, requestDTO.getOrderId()));
                if (committed != null) {
                    return CreateRouteResponseDTO.existingRoute(committed);
                }
                throw e;
            }

            createDefaultNodes(route);
            return CreateRouteResponseDTO.of(route, planResultFinal);
        });
    }

    // ----------------------------------------------------------------
    //  查询路线详情（三种方式，共用同一个组装方法）
    // ----------------------------------------------------------------

    @Override
    public RouteDetailDTO getRouteDetailById(Long routeId) {
        LogisticsRoute route = getRouteOrThrow(routeId);
        return buildRouteDetailDTO(route);
    }

    @Override
    public RouteDetailDTO getRouteDetailByRouteNo(String routeNo) {
        LogisticsRoute route = logisticsRouteMapper.selectOne(
                new LambdaQueryWrapper<LogisticsRoute>().eq(LogisticsRoute::getRouteNo, routeNo));
        if (route == null) throw new BusinessException(ResultCode.FAIL.getCode(), "路线不存在");
        return buildRouteDetailDTO(route);
    }

    @Override
    public RouteDetailDTO getRouteDetailByOrderId(Long orderId) {
        LogisticsRoute route = logisticsRouteMapper.selectOne(
                new LambdaQueryWrapper<LogisticsRoute>().eq(LogisticsRoute::getOrderId, orderId));
        if (route == null) throw new BusinessException(ResultCode.FAIL.getCode(), "路线不存在");
        return buildRouteDetailDTO(route);
    }

    // ----------------------------------------------------------------
    //  状态变更
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public LogisticsRoute bindDriver(Long routeId, Long driverId, Long deliveryId) {
        LogisticsRoute route = getRouteOrThrow(routeId);
        route.setDriverId(driverId);
        route.setDeliveryId(deliveryId);
        route.setRouteStatus(1);          // 绑定运输员后状态变为：运输中
        logisticsRouteMapper.updateById(route);
        return route;
    }

    @Override
    @Transactional
    public LogisticsRoute updateRouteStatus(Long routeId, Integer status) {
        LogisticsRoute route = getRouteOrThrow(routeId);
        route.setRouteStatus(status);
        if (status == 2) {
            route.setActualArrivalTime(new Date()); // 已送达：记录实际到达时间
        }
        logisticsRouteMapper.updateById(route);
        return route;
    }

    // ----------------------------------------------------------------
    //  列表查询
    // ----------------------------------------------------------------

    @Override
    public List<LogisticsRoute> getPendingRoutes() {
        return logisticsRouteMapper.selectList(
                new LambdaQueryWrapper<LogisticsRoute>()
                        .eq(LogisticsRoute::getRouteStatus, 0)
                        .orderByAsc(LogisticsRoute::getCreateTime));
    }

    @Override
    public List<LogisticsRoute> getRoutesByDriverId(Long driverId) {
        return logisticsRouteMapper.selectList(
                new LambdaQueryWrapper<LogisticsRoute>()
                        .eq(LogisticsRoute::getDriverId, driverId)
                        .orderByDesc(LogisticsRoute::getCreateTime));
    }

    // ----------------------------------------------------------------
    //  私有方法
    // ----------------------------------------------------------------

    /** 查路线，不存在则抛异常（复用，避免重复代码） */
    private LogisticsRoute getRouteOrThrow(Long routeId) {
        LogisticsRoute route = logisticsRouteMapper.selectById(routeId);
        if (route == null) throw new BusinessException(ResultCode.FAIL.getCode(), "路线不存在");
        return route;
    }

    /** 组装路线详情 DTO（路线 + 节点列表 + 近期轨迹 + 状态描述 + 配送员信息） */
    private RouteDetailDTO buildRouteDetailDTO(LogisticsRoute route) {
        RouteDetailDTO dto = new RouteDetailDTO();
        dto.setRoute(route);
        dto.setNodes(logisticsNodeMapper.selectList(
                new LambdaQueryWrapper<LogisticsNode>()
                        .eq(LogisticsNode::getRouteId, route.getId())
                        .orderByAsc(LogisticsNode::getSequenceNo)));
        dto.setRecentTracks(logisticsTrackMapper.selectLatestTracks(route.getId(), 50));
        dto.setStatusDesc(getStatusDesc(route.getRouteStatus()));

        // 填充配送员姓名和电话（路线已绑定运输员时）
        if (route.getDriverId() != null) {
            try {
                com.fm.common.result.Result<java.util.Map<String, Object>> driverResult =
                        driverFeignClient.getDriverByDriverId(route.getDriverId());
                if (driverResult != null && driverResult.getCode() == 200 && driverResult.getData() != null) {
                    java.util.Map<String, Object> d = driverResult.getData();
                    dto.setDriverName(d.get("realName") != null ? d.get("realName").toString() : null);
                    dto.setDriverPhone(d.get("phone") != null ? d.get("phone").toString() : null);
                }
            } catch (Exception e) {
                // 获取配送员信息失败不影响主流程
                System.err.println("获取配送员信息失败: " + e.getMessage());
            }
        }

        return dto;
    }

    /** 创建路线时自动插入出发节点和目的地节点 */
    private void createDefaultNodes(LogisticsRoute route) {
        LogisticsNode startNode = new LogisticsNode();
        startNode.setRouteId(route.getId());
        startNode.setNodeType(0);
        startNode.setNodeName("出发仓库");
        startNode.setNodeAddress(route.getStartAddress());
        startNode.setLatitude(route.getStartLatitude());
        startNode.setLongitude(route.getStartLongitude());
        startNode.setSequenceNo(0);
        startNode.setNodeStatus(0);
        logisticsNodeMapper.insert(startNode);

        LogisticsNode endNode = new LogisticsNode();
        endNode.setRouteId(route.getId());
        endNode.setNodeType(2);
        endNode.setNodeName("收货地址");
        endNode.setNodeAddress(route.getEndAddress());
        endNode.setLatitude(route.getEndLatitude());
        endNode.setLongitude(route.getEndLongitude());
        endNode.setSequenceNo(99);
        endNode.setNodeStatus(0);
        logisticsNodeMapper.insert(endNode);
    }

    /** 路线状态转中文描述 */
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

    /** 生成路线编号：LR + yyyyMMddHHmmss + 4位随机数 */
    private String generateRouteNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 10000);
        return "LR" + time + String.format("%04d", rand);
    }
}
