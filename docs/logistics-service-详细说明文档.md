# Logistics-Service 物流服务 — 完全初学者指南

> logistics-service 是本系统技术含量最高的模块，整合了图论算法（A*）、OSM 开放地图数据（GraphHopper）和大语言模型（DeepSeek/LLM）三大技术，实现智能化物流路线规划和实时轨迹追踪。

> **运行端口**：以 `logistics-service/src/main/resources/application.yml` 为准，当前为 **8087**（经网关统一访问 **`/api/logistics/**`**，无需前端直连该端口）。

---

## 目录

1. [这个模块是做什么的？](#1-这个模块是做什么的)
2. [目录结构](#2-目录结构)
3. [数据库设计](#3-数据库设计)
4. [核心架构：策略模式](#4-核心架构策略模式)
5. [路线规划技术栈](#5-路线规划技术栈)
6. [三种路线策略详解](#6-三种路线策略详解)
7. [历史数据驱动的智能决策](#7-历史数据驱动的智能决策)
8. [实时轨迹追踪](#8-实时轨迹追踪)
9. [路线管理接口](#9-路线管理接口)
10. [关键技术深度解析](#10-关键技术深度解析)

---

## 1. 这个模块是做什么的？

### 1.1 两大核心功能

**功能一：路线规划（Route Planning）**

当商户发货时，系统需要知道"从仓库到收货地址怎么走"：

```
仓库（上海浦东仓库，经纬度：31.23, 121.55）
        ↓
【logistics-service 规划路线】
  ① A*算法在路网图上搜索最短路径
  ② 或让 LLM 分析历史交通数据，决定是否绕行拥堵区域
  ③ 返回 GeoJSON 格式的路线坐标点序列
        ↓
收货地址（上海静安区，经纬度：31.23, 121.47）
```

**功能二：实时轨迹追踪（Track Tracking）**

运输员配送途中，APP 实时上报当前位置（GPS），系统记录完整轨迹：

```
运输员APP每隔30秒上报位置：
  GPS位置1 → 记录到 logistics_track 表
  GPS位置2 → 记录到 logistics_track 表
  ...
  
顾客查看物流时，看到：
  蓝色虚线 = 规划路线（静态）
  橙色实线 = 实际已走的轨迹（实时更新）
  蓝色图标 = 当前位置（最新GPS点）
```

### 1.2 接口总览

| 控制器 | 前缀 | 功能 |
|--------|------|------|
| LogisticsRouteController | `/api/logistics/routes` | 创建路线、按订单/路线ID/物流单号查询、绑定司机、更新状态、待出发列表、司机路线列表 |
| LogisticsTrackController | `/api/logistics/track` | 轨迹上报（`/location`）、最新点、最近 N 条、全量历史 |

**路线接口要点**：

- `POST /api/logistics/routes`：由 **order-service 在商户发货时**通过 Feign 调用（也可由前端在发货流程中显式调用，与订单状态更新配合）；成功体为 `CreateRouteResponseDTO`（内含 `route` + 本次规划的 `llmEnhanced` / `llmDecision`，后者**不入库**）。
- `GET /api/logistics/routes/no/{routeNo}`：按物流单号查进度；**控制器不校验 `userId`**，但经网关访问时**仍需有效 JWT**（`JwtAuthFilter` 将 `/api/logistics/routes/**` 限定为 customer/shop/driver/admin 之一）。
- `PUT /api/logistics/routes/{routeId}/status`：仅 **`admin` 或 `driver`** 可更新路线状态。
- `GET /api/logistics/routes/pending`：仅 **admin**，调度大屏待出发路线。
- `GET /api/logistics/routes/driver/{driverId}`：已登录用户查某司机的路线列表（业务侧再做归属校验）。

---

## 2. 目录结构

```
logistics-service/
├── pom.xml
└── src/main/
    ├── java/com/fm/logistics/
    │   ├── LogisticsServiceApplication.java       ← 启动类（端口以 application.yml 为准，当前为 8087）
    │   ├── config/
    │   │   ├── GraphHopperConfig.java             ← GraphHopper路网初始化
    │   │   ├── RoutingStrategyConfig.java         ← 路线策略切换配置
    │   │   ├── MyBatisPlusConfig.java             ← 分页插件
    │   │   └── SwaggerConfig.java
    │   ├── controller/
    │   │   ├── LogisticsRouteController.java      ← 路线接口
    │   │   └── LogisticsTrackController.java      ← 轨迹接口
    │   ├── service/
    │   │   ├── RouteStrategy.java                 ← 策略接口（重要！）
    │   │   ├── RoutePlanningService.java          ← 路线规划服务接口
    │   │   ├── RouteHistoryService.java           ← 历史数据服务接口
    │   │   ├── LogisticsRouteService.java
    │   │   ├── LogisticsTrackService.java
    │   │   └── impl/
    │   │       ├── AStarRouteStrategy.java        ← 策略实现1：纯A*算法
    │   │       ├── LlmJudgeRouteStrategy.java     ← 策略实现2：多候选+LLM裁判
    │   │       ├── LlmWaypointRouteStrategy.java  ← 策略实现3：LLM路点+A*分段
    │   │       ├── RoutePlanningServiceImpl.java  ← 路线规划入口
    │   │       ├── RouteHistoryServiceImpl.java   ← 历史数据聚合
    │   │       ├── LogisticsRouteServiceImpl.java ← 路线业务逻辑
    │   │       └── LogisticsTrackServiceImpl.java ← 轨迹业务逻辑
    │   ├── client/
    │   │   └── DeepSeekClient.java                ← 调用DeepSeek API的HTTP客户端
    │   ├── feign/
    │   │   └── DriverFeignClient.java             ← 调用driver-service
    │   ├── dto/
    │   │   ├── CreateRouteRequestDTO.java
    │   │   ├── CreateRouteResponseDTO.java          ← 创建路线 HTTP 响应（route + LLM 元数据）
    │   │   ├── RouteDetailDTO.java
    │   │   ├── RouteResultDTO.java
    │   │   ├── GeoJsonLineString.java
    │   │   ├── LlmDecisionResult.java
    │   │   ├── HistoryContextDTO.java
    │   │   └── LocationUpdateDTO.java
    │   └── entity/
    │       ├── LogisticsRoute.java                ← logistics_route表
    │       ├── LogisticsTrack.java                ← logistics_track表
    │       └── LogisticsNode.java                 ← logistics_node表（路线节点）
    └── resources/
        └── application.yml
```

---

## 3. 数据库设计

### 3.1 三张核心表

以下与仓库 `database/logistics.sql` 及实体类 `LogisticsRoute` / `LogisticsTrack` / `LogisticsNode` 一致（**不含**已废弃字段如 `planned_distance`、`route_strategy`、库内 `planned_duration`——策略由运行时 `routing.strategy` 决定，规划结果以 `planned_route` GeoJSON 为主）。

```sql
-- 物流路线表（每个订单一条路线记录）
CREATE TABLE `logistics_route` (
  `id`                    BIGINT NOT NULL AUTO_INCREMENT,
  `route_no`              VARCHAR(30) NOT NULL UNIQUE,  -- 物流单号，如 LR+时间戳+随机
  `order_id`              BIGINT NOT NULL UNIQUE,
  `delivery_id`           BIGINT DEFAULT NULL,            -- 接单后关联 order_delivery
  `driver_id`             BIGINT DEFAULT NULL,
  `warehouse_id`          BIGINT DEFAULT NULL,
  `start_address`         VARCHAR(300) NOT NULL,
  `end_address`           VARCHAR(300) NOT NULL,
  `start_lat` / `start_lng`   DOUBLE,                    -- 起点坐标（实体字段 startLatitude/startLongitude）
  `end_lat` / `end_lng`       DOUBLE,                    -- 终点坐标
  `current_lat` / `current_lng` DOUBLE,                -- 当前位置（随轨迹上报更新）
  `current_address`       VARCHAR(300),
  `last_track_time`       DATETIME,                      -- 最近一次 GPS 写入时间
  `route_status`          TINYINT NOT NULL DEFAULT 0,    -- 0待出发 1运输中 2已送达 3异常
  `estimated_arrival_time` DATETIME,
  `actual_arrival_time`   DATETIME,
  `planned_route`         LONGTEXT,                      -- GeoJSON LineString
  `receiver_name`         VARCHAR(50),
  `receiver_phone`        VARCHAR(20),
  `remark`                VARCHAR(500),
  `create_time`        DATETIME,
  `update_time`        DATETIME,
  PRIMARY KEY (`id`)
);

-- 实时轨迹表（运输员周期性上报）
CREATE TABLE `logistics_track` (
  `id`           BIGINT NOT NULL AUTO_INCREMENT,
  `route_id`     BIGINT NOT NULL,
  `driver_id`    BIGINT NOT NULL,
  `latitude`     DOUBLE NOT NULL,
  `longitude`    DOUBLE NOT NULL,
  `altitude`     DOUBLE,                 -- 海拔（米）
  `speed`        DOUBLE,                 -- 车速（km/h）
  `heading`      DOUBLE,                 -- 方向角（0=正北，顺时针；实体字段名 heading）
  `accuracy`     DOUBLE,                 -- GPS 精度（米）
  `address`      VARCHAR(300),           -- 逆地理描述（可选）
  `track_time`   DATETIME NOT NULL,      -- 服务端统一写入的上报时间
  `create_time`  DATETIME,               -- 服务端接收时间
  PRIMARY KEY (`id`)
);

-- 里程碑节点表（起点/途经点/目的地，用于时间线展示）
CREATE TABLE `logistics_node` (
  `id`         BIGINT NOT NULL AUTO_INCREMENT,
  `route_id`   BIGINT NOT NULL,
  `node_type`  TINYINT NOT NULL,         -- 0出发点 1途经点 2目的地
  `node_name`  VARCHAR(100) NOT NULL,
  `node_address` VARCHAR(300),
  `latitude`   DOUBLE,
  `longitude`  DOUBLE,
  `sequence_no` INT NOT NULL,            -- 顺序（实体字段 sequenceNo）
  ...
  PRIMARY KEY (`id`)
);
```

### 3.2 `planned_route` 字段的 GeoJSON 格式

`planned_route` 存储规划路线的完整坐标，是 GeoJSON LineString 格式的 JSON 字符串：

```json
{
  "type": "LineString",
  "coordinates": [
    [121.55, 31.23],   ← [经度, 纬度]（注意：GeoJSON是经度在前！）
    [121.54, 31.23],
    [121.52, 31.24],
    ...
    [121.47, 31.23]
  ]
}
```

**为什么用 GeoJSON 而不是简单的坐标数组？**

GeoJSON 是地理数据的国际标准（RFC 7946），前端 Leaflet 地图库可以直接读取并渲染，不需要额外转换。

---

## 4. 核心架构：策略模式

### 4.1 什么是策略模式（Strategy Pattern）？

**问题**：路线规划有多种算法，希望能根据配置灵活切换，不修改业务代码。

**策略模式的解决方案**：

```java
// 1. 定义策略接口（契约）
public interface RouteStrategy {
    String strategyName();
    RouteResultDTO plan(double startLat, double startLon,
                        double endLat, double endLon);
}

// 2. 多个具体策略实现
@Service
public class AStarRouteStrategy implements RouteStrategy { ... }
@Component
public class LlmJudgeRouteStrategy implements RouteStrategy { ... }
@Component
public class LlmWaypointRouteStrategy implements RouteStrategy { ... }

// 3. 调用者只依赖接口，不关心具体实现
@Service
public class RoutePlanningServiceImpl {
    @Autowired
    private RouteStrategy routeStrategy;  // 具体用哪个，由配置决定
    
    public RouteResultDTO plan(double... coords) {
        return routeStrategy.plan(coords); // 调用接口方法，具体实现透明
    }
}
```

**策略选择配置（RoutingStrategyConfig.java）**：

```java
@Configuration
public class RoutingStrategyConfig {

    @Value("${routing.strategy:A_STAR}")  // 从配置文件读取，默认A_STAR
    private String strategyName;

    @Autowired
    private AStarRouteStrategy aStarStrategy;
    @Autowired
    private LlmJudgeRouteStrategy llmJudgeStrategy;
    @Autowired
    private LlmWaypointRouteStrategy llmWaypointStrategy;

    @Bean
    @Primary  // 当有多个RouteStrategy Bean时，优先注入这个
    public RouteStrategy routeStrategy() {
        switch (strategyName) {
            case "LLM_JUDGE":    return llmJudgeStrategy;
            case "LLM_WAYPOINT": return llmWaypointStrategy;
            default:             return aStarStrategy;  // 默认A_STAR
        }
    }
}
```

**`application.yml` 中切换策略（一行配置即可）**：

```yaml
routing:
  strategy: A_STAR       # 纯A*算法（默认）
# strategy: LLM_JUDGE    # 多候选+LLM裁判
# strategy: LLM_WAYPOINT # LLM路点+A*分段
```

**策略模式的优势**：

- **开闭原则**：增加新策略时，只需新建实现类，不修改已有代码
- **可测试性**：每个策略可以独立单元测试
- **运行时切换**：只需修改配置文件，不需要重新编译代码

---

## 5. 路线规划技术栈

### 5.1 GraphHopper — OSM 路网图

**什么是 GraphHopper？**

GraphHopper 是一个开源的路线规划引擎，可以加载 OSM（OpenStreetMap，开放街道地图）数据，建立道路网络图，然后在上面运行路径规划算法。

**类比**：GraphHopper 就像把整个城市的地图（每条街道、路口、单行线）变成计算机能理解的图（Graph）数据结构，然后在图上查找最短路径。

**GraphHopperConfig.java 初始化流程**（当前工程使用 **GraphHopper 9.x**：`fastest` 等旧 weighting 已废弃，需用 **`custom` + `CustomModel`**；OSM 路径与缓存目录来自 `application.yml` 的 `graphhopper.osm-file`、`graphhopper.graph-cache`，车辆 profile 名来自 `graphhopper.vehicle`，默认 `car`）：

```java
@Configuration
public class GraphHopperConfig {

    @Value("${graphhopper.osm-file}")
    private String osmFile;
    @Value("${graphhopper.graph-cache}")
    private String graphCache;
    @Value("${graphhopper.vehicle}")
    private String vehicle;

    @Bean
    public GraphHopper graphHopper() {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation(graphCache);

        CustomModel carModel = new CustomModel()
            .setDistanceInfluence(90.0)
            .addToSpeed(Statement.If("true", Statement.Op.LIMIT, "car_average_speed"))
            .addToPriority(Statement.If("!car_access", Statement.Op.MULTIPLY, "0"));

        hopper.setProfiles(
            new Profile(vehicle)
                .setWeighting("custom")
                .setCustomModel(carModel)
        );
        hopper.setEncodedValuesString("car_access, car_average_speed");

        hopper.importOrLoad();
        return hopper;
    }
}
```

**配置示例**（`logistics-service/application.yml`）：

```yaml
graphhopper:
  osm-file: ./shanghai-260310.osm.pbf
  graph-cache: ./graph-cache
  vehicle: car
```

**图结构（Graph）的基本概念**：

```
路口A ─────── 路段(150m) ─────── 路口B
       ↖                         ↙
        └──────── 路段(200m) ────┘
```

- **节点（Node）**：路口（十字路口、丁字路口等）
- **边（Edge）**：路段（两个路口之间的道路）
- **权重（Weight）**：路段的距离或通行时间

最短路径问题：找到从起点节点到终点节点，权重之和最小的路径。

### 5.2 OSM 数据和图缓存

`graph-cache/` 目录中的文件是 GraphHopper 对 OSM 数据的预处理结果：

```
graph-cache/
├── edges          ← 所有路段的数据（长度、速度限制、方向等）
├── nodes          ← 所有路口的数据（坐标）
├── geometry       ← 路段的详细几何形状（不只是起终点，还有中间弯道）
├── location_index ← 空间索引（快速查找某坐标附近的路网节点）
├── properties     ← 图的元数据（版本、配置等）
└── edgekv_keys/edgekv_vals ← 额外属性键值对存储
```

**`location_index.findClosest(lat, lon, EdgeFilter.ALL_EDGES)`**：

空间索引的作用：给定一个经纬度坐标（可能不在路上，比如在建筑内部），找到最近的路网节点。

```java
Snap startSnap = locationIndex.findClosest(31.23, 121.55, EdgeFilter.ALL_EDGES);
// startSnap.getClosestNode() → 返回最近的路口节点ID
```

---

## 6. 三种路线策略详解

### 6.1 策略一：A*（A-Star）算法

**什么是 A* 算法？**

A* 是一种图搜索算法，在 Dijkstra 算法（向四面八方平等扩展）的基础上加入**启发函数**，优先向终点方向扩展，大幅减少搜索范围。

**核心公式**：

```
f(n) = g(n) + h(n)

其中：
  g(n) = 从起点到节点n的实际代价（已知，累计路程/时间）
  h(n) = 从节点n到终点的估计代价（启发值，用球面距离估算）
  f(n) = 总估计代价（优先搜索f值小的节点）
```

**启发函数（Haversine 球面距离）**：

```java
// 计算地球上两点的球面距离（考虑地球曲率）
private double haversine(double lat1, double lon1, double lat2, double lon2) {
    final double R = 6_371_000.0;  // 地球半径（米）

    double dLat = Math.toRadians(lat2 - lat1);  // 纬度差（转为弧度）
    double dLon = Math.toRadians(lon2 - lon1);  // 经度差（转为弧度）

    // 球面余弦公式（精确版本）
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
             + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
             * Math.sin(dLon / 2) * Math.sin(dLon / 2);

    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
```

**`Math.toRadians(degrees)`**：角度转弧度（三角函数需要弧度）。

`1 度 = π/180 弧度 ≈ 0.01745 弧度`

**A* 搜索过程**：

```java
// 优先队列（最小堆），按f值排序
PriorityQueue<int[]> openSet = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
// a[0] = 节点ID, a[1] = f值（double转long存储）

openSet.add(new int[]{startNode, 0});
Map<Integer, Double> gScore = new HashMap<>();  // 记录已知g值
Map<Integer, Integer> cameFrom = new HashMap<>();  // 记录来路（用于还原路径）
gScore.put(startNode, 0.0);

while (!openSet.isEmpty()) {
    int current = openSet.poll()[0];  // 取出f值最小的节点

    if (current == endNode) {
        // 找到终点！沿cameFrom反向还原路径
        return reconstructPath(cameFrom, current, nodeAccess);
    }

    // 遍历当前节点的所有相邻节点
    EdgeExplorer explorer = graph.createEdgeExplorer();
    EdgeIterator iter = explorer.setBaseNode(current);
    while (iter.next()) {
        int neighbor = iter.getAdjNode();
        double edgeLength = iter.getDistance();  // 路段实际长度（米）
        double tentativeG = gScore.get(current) + edgeLength;

        if (tentativeG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
            // 找到了更短的路径到达neighbor
            cameFrom.put(neighbor, current);
            gScore.put(neighbor, tentativeG);
            double h = haversine(nodeAccess.getLat(neighbor), nodeAccess.getLon(neighbor),
                                 nodeAccess.getLat(endNode), nodeAccess.getLon(endNode));
            double f = tentativeG + epsilon * h;  // epsilon=1.0时标准A*
            openSet.add(new int[]{neighbor, (int)(f)});
        }
    }
}
```

**Weighted A*（带权重的A*）**：

`f = g + epsilon * h`

- `epsilon = 1.0`：标准A*，保证找到最短路径
- `epsilon = 1.8`：偏向更激进地趋向终点，速度更快但可能非最优
- `epsilon = 3.5`：非常激进，生成与标准路线差异很大的备选路线

LLM_JUDGE 策略用不同 epsilon 生成3条候选路线，让 LLM 选最好的。

### 6.2 策略二：LLM_JUDGE（多候选+LLM裁判）

```
① 生成3条候选路线（epsilon=1.0/1.8/3.5）
② 查询历史交通数据（均速、延误、慢速热点）
③ 将3条候选路线 + 历史数据发给 DeepSeek
④ DeepSeek 分析哪条路线最优，返回 JSON 决策
⑤ 采用 DeepSeek 推荐的路线
⑥ 失败则降级为纯A*（epsilon=1.0）
```

**发给 LLM 的 Prompt 示例**：

```
你是物流路线优化专家。请从以下3条候选路线中选择最优路线...

候选路线1（A*最短路）：距离=8.3km，预计时间=12分钟
候选路线2（偏北路线）：距离=9.1km，预计时间=14分钟
候选路线3（外环绕行）：距离=11.2km，预计时间=15分钟

历史交通数据（周二 08:30）：
- 当前时段历史均速：18km/h（全天均速：32km/h）
- 速度比值：0.56（严重拥堵！）
- 慢速热点：(31.23, 121.51) 均速3.2km/h，出现47次，高峰:8:00
- 近7天异常次数：5次

请选择最优路线并解释原因（JSON格式）：
{"selected": 2, "reason": "候选路线2绕开了(31.23,121.51)慢速热点，虽然多1km但预计省4分钟"}
```

### 6.3 策略三：LLM_WAYPOINT（LLM路点+A*分段）

```
① 查询历史交通数据
② 将起终点 + 历史数据发给 DeepSeek
③ DeepSeek 决定是否需要插入绕路路点（0-2个）
④ 按 [起点→路点1→路点2→终点] 分段调用A*
⑤ 拼接各段坐标，汇总距离和时间
⑥ 失败则降级为直接A*
```

**LLM 的路点输出格式**：

```json
{
  "strategy": "绕行北侧",
  "reasoning": "speedRatio=0.56，慢速热点在直连路线上，建议绕行北横通道",
  "waypoints": [
    {"lat": 31.250, "lon": 121.510, "label": "北横通道入口附近"},
    {"lat": 31.245, "lon": 121.480, "label": "北横通道西出口"}
  ],
  "expectedSpeedKmh": 28,
  "confidenceLevel": "HIGH",
  "summary": "绕行北横通道，预计需要约16分钟"
}
```

**防幻觉机制**：

LLM 可能"幻觉"出不存在的坐标（比如在河流中间）：

```java
// 过滤掉不在上海范围的路点
private boolean isWithinShanghaiRegion(double lat, double lon) {
    return lat >= 30.6 && lat <= 31.9 && lon >= 120.8 && lon <= 122.2;
}
```

即使 LLM 给出了上海范围内的坐标，A* 也会自动把路点映射到最近的真实路网节点（`locationIndex.findClosest()`），消除"路点在建筑内"的问题。

---

## 7. 历史数据驱动的智能决策

### 7.1 RouteHistoryService 聚合历史数据

历史数据来自 **`logistics_track`**（轨迹点速度、时间）与 **`logistics_route`**（路线延误、异常状态），由 **`RouteHistoryServiceImpl.buildContext`** 组装为 `HistoryContextDTO`，供 LLM 策略使用。当前实现要点（详见源码）：

1. **按小时均速**：`trackMapper.selectAvgSpeedByHour()`，基于 `track_time` 过去 30 天，按 `HOUR(track_time)` 分组，得到各小时 `avgSpeed`、`sampleCount`；再算出当前小时与「全天列表平均」的速度比 `speedRatio`。
2. **延误统计**：`routeMapper` 查询路线延误（`queryDelay`），填充 `currentHourDelayMin` / `allDayDelayMin` 等。
3. **慢速热点**：以起终点中点为中心，±0.1° 约 10km 范围，调用 `selectSlowZones(minLat, maxLat, minLon, maxLon)`（见下节 SQL）。
4. **异常次数**：`routeMapper.selectExceptionStats(startLat, startLon)`，起点附近约 2km、近 7 天 `route_status=3` 的异常路线计数。
5. **上下文扩展字段**：`dayOfWeek`（中文星期）、`timePeriod`（早晚高峰等）、`speedSampleCount` 等与当前 `HistoryContextDTO` 一致。

### 7.2 慢速热点的空间聚合 SQL

与 **`LogisticsTrackMapper.selectSlowZones`** 一致（时间字段为 **`track_time`**，不是 `create_time`）：

```sql
SELECT ROUND(latitude, 2) AS latBucket,
       ROUND(longitude, 2) AS lonBucket,
       AVG(speed) AS avgSpeed,
       COUNT(*) AS occurrences,
       HOUR(MAX(track_time)) AS peakHour
FROM logistics_track
WHERE speed IS NOT NULL AND speed > 0
  AND track_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
  AND latitude  BETWEEN #{minLat} AND #{maxLat}
  AND longitude BETWEEN #{minLon} AND #{maxLon}
GROUP BY ROUND(latitude, 2), ROUND(longitude, 2)
HAVING AVG(speed) < 8 AND COUNT(*) >= 3
ORDER BY AVG(speed) ASC
LIMIT 5
```

**`ROUND(latitude, 2)` 的作用**：

将精确坐标聚合到约 **0.01°** 的网格（约 1km 量级），同一网格内多次慢速采样才参与（`COUNT(*) >= 3`），减少噪声点。

---

## 8. 实时轨迹追踪

### 8.1 上报轨迹接口

当前路径为 **`POST /api/logistics/track/location`**（**不是** 已废弃的 `/update`）。请求头需带网关注入的 **`userId`、`roleCode`**；**仅 `roleCode=driver`** 可上报。`LocationUpdateDTO` 必填：`routeId`、`latitude`、`longitude`；可选：`altitude`、`speed`、`heading`（方向角）、`accuracy`、`address`（逆地理描述，由客户端传入）等。

**`userId → driverId`（B6）**：控制器通过 Feign 调用 `driver-service`，将登录账号的 `userId` 转为 `driver_info.id`，再写入 `logistics_track.driver_id`，避免把 `userId` 误当司机主键。

```java
@PostMapping("/location")
public Result<LogisticsTrack> uploadLocation(
        @RequestHeader(value = "userId", required = false) String userIdHeader,
        @RequestHeader(value = "roleCode", required = false) String roleCode,
        @RequestBody LocationUpdateDTO locationUpdate) {
    // 校验 userId、roleCode=driver、经纬度与 routeId
    // userId → driverId → trackService.updateLocation(driverId, dto)
}
```

### 8.2 LogisticsTrackServiceImpl 核心逻辑

```java
@Override
@Transactional
public LogisticsTrack updateLocation(Long driverId, LocationUpdateDTO dto) {
    LogisticsTrack track = new LogisticsTrack();
    track.setRouteId(dto.getRouteId());
    track.setDriverId(driverId);
    track.setLatitude(dto.getLatitude());
    track.setLongitude(dto.getLongitude());
    track.setAltitude(dto.getAltitude());
    track.setSpeed(dto.getSpeed());
    track.setHeading(dto.getHeading());
    track.setAccuracy(dto.getAccuracy());
    track.setAddress(dto.getAddress());
    track.setTrackTime(new Date());   // 服务端统一赋值，不信任客户端时钟

    logisticsTrackMapper.insert(track);

    // 同步更新 logistics_route：当前位置 + last_track_time
    LogisticsRoute routeUpdate = new LogisticsRoute();
    routeUpdate.setId(dto.getRouteId());
    routeUpdate.setCurrentLatitude(dto.getLatitude());
    routeUpdate.setCurrentLongitude(dto.getLongitude());
    routeUpdate.setCurrentAddress(dto.getAddress());
    routeUpdate.setLastTrackTime(track.getTrackTime());
    logisticsRouteMapper.updateById(routeUpdate);

    return track;
}
```

### 8.3 查询轨迹

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/logistics/track/{routeId}/latest` | 最新一条轨迹 |
| GET | `/api/logistics/track/{routeId}/recent?limit=50` | 最近 N 条（默认 50，最大 200），**时间倒序** |
| GET | `/api/logistics/track/{routeId}/history` | **全量历史，时间正序**（回放） |

`RouteDetailDTO.recentTracks` 与最近列表一致（倒序）；前端 `RouteMap.vue` 对折线 **先 `reverse()` 再按时间正序连线**。

**前端绘制逻辑**（`RouteMap.vue`）：

```javascript
// 规划路线：GeoJSON [lng,lat] → Leaflet [lat,lng]
const latlngs = geo.coordinates.map((c) => [c[1], c[0]]);
L.polyline(latlngs, { color: '#409eff', weight: 3, dashArray: '8,5' }).addTo(map);

// 实际轨迹：后端 recentTracks 为时间倒序，需 reverse 后连线
const pts = [...props.recentTracks].reverse()
  .filter(t => t.latitude && t.longitude)
  .map(t => [t.latitude, t.longitude]);
L.polyline(pts, { color: '#e6a23c', weight: 3 }).addTo(map);
```

---

## 9. 路线管理接口

### 9.1 LogisticsRouteController 关键接口

**创建路线 `POST /api/logistics/routes`**

- **典型调用方**：`order-service` 在商户发货流程中通过 **OpenFeign** 调用；前端在「确认发货 & 规划路线」流程中也会调用（与 `PUT /api/orders/{id}/status` 配合，见前端 `Shop/Order.vue`）。
- **必填**：`orderId`、`warehouseId`、`startAddress`、`endAddress`。
- **可选**：`startLatitude`/`startLongitude`、`endLatitude`/`endLongitude`、`receiverName`、`receiverPhone`（有起终点坐标时路径规划更准）。
- **响应**：`Result<CreateRouteResponseDTO>`，其中 `data` 包含：
  - `route`：持久化后的 `LogisticsRoute`；
  - `llmEnhanced`：本次是否经过 LLM 策略增强（若命中已有路线等场景可为 `false`）；
  - `llmDecision`：**仅出现在本次 HTTP 响应中**，不入库（与 `RouteResultDTO` / `LlmDecisionResult` 字段一致）。

```java
@PostMapping
public Result<CreateRouteResponseDTO> createRoute(@RequestBody CreateRouteRequestDTO request) {
    CreateRouteResponseDTO body = routeService.createRoute(request);
    return Result.success(body);
}
```

**其他路线接口（节选）**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/order/{orderId}` | 按订单查详情，需 `userId` |
| GET | `/{routeId}` | 按路线 ID 查详情，需 `userId` |
| GET | `/no/{routeNo}` | 按物流单号查询（**无 userId 校验**；网关侧需已登录角色） |
| PUT | `/{routeId}/bind` | 接单绑定司机，`body`: `driverId`, `deliveryId`（可选） |
| PUT | `/{routeId}/status` | 更新路线状态，需 `userId` 且 **`roleCode` 为 admin 或 driver**，`body`: `status` |
| GET | `/pending` | 待出发路线列表，**仅 admin** |
| GET | `/driver/{driverId}` | 某司机的路线列表，需登录 |

### 9.2 RouteDetailDTO 结构

```java
@Data
public class RouteDetailDTO {
    private LogisticsRoute route;           // 路线主信息
    private List<LogisticsNode> nodes;      // 里程碑节点（按 sequenceNo）
    private List<LogisticsTrack> recentTracks; // 最近轨迹（倒序，默认最多 50 条）
    private String statusDesc;               // 路线状态中文描述
    private String driverName;               // 从 driver-service 填充，未绑定时为 null
    private String driverPhone;
}
```

---

## 10. 关键技术深度解析

### 10.1 DeepSeekClient — 调用大语言模型 API

配置项使用 **`deepseek.api-key`**、**`deepseek.api-url`**、**`deepseek.model`**（与 `application.yml` 一致）。模型名默认 `deepseek-chat`，请求体含 `max_tokens`（如 800）、`temperature=0.1`、`response_format=json_object`。`RestTemplate` 在构造函数中创建，并注入 **`ObjectMapper`** 供解析使用。

```java
@Value("${deepseek.api-url:https://api.deepseek.com/v1/chat/completions}")
private String apiUrl;
@Value("${deepseek.api-key:your-api-key-here}")
private String apiKey;
@Value("${deepseek.model:deepseek-chat}")
private String model;

@Autowired
public DeepSeekClient(ObjectMapper objectMapper) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(15000);
    this.restTemplate = new RestTemplate(factory);
    this.objectMapper = objectMapper;
}
```

**`temperature=0.1` 解释**：

LLM 的温度参数控制输出的随机性：
- `temperature=0`：每次输出完全相同（最确定）
- `temperature=1`：有较多随机性（更有创意但不稳定）
- `temperature=0.1`：接近确定性，但略有变化（适合结构化JSON输出）

**`response_format: json_object`**：

强制 LLM 输出合法的 JSON，避免 LLM 在 JSON 前后加入解释文字（如"好的，以下是我的决策：{...}"），导致 JSON 解析失败。

### 10.2 PriorityQueue（优先队列）在 A* 中的应用

```java
// 优先队列按f值升序排列（f值小的先处理）
PriorityQueue<int[]> openSet = new PriorityQueue<>(
    Comparator.comparingDouble(a -> a[1])  // 按数组第二个元素（f值）比较
);
```

**`PriorityQueue` 是什么？**

普通队列（Queue）是先进先出（FIFO）。优先队列（PriorityQueue）是按优先级出队，优先级最高（数值最小）的元素最先出队，不管插入顺序。

内部实现是**最小堆（Min-Heap）**数据结构：
- 插入：O(log n) 时间复杂度
- 取出最小：O(log n)

A* 用优先队列确保每次都处理当前 f 值最小的节点（最有希望的节点），而不是随机处理。

### 10.3 GeoJSON 坐标顺序的"坑"

```java
// GeoJSON 标准：坐标是 [经度, 纬度]（longitude first！）
// 这与直觉相反（我们习惯说"纬度31, 经度121"）

// GeoJsonLineString 内部存储 [lon, lat]
mergedRoute.addPoint(lat, lon);   // 方法参数是(lat, lon)
// 但内部存储时反转为 [lon, lat]：
// coordinates: [[121.55, 31.23], [121.54, 31.23], ...]

// Leaflet 地图库需要 [纬度, 经度]（与GeoJSON相反）
// 前端解析时需要交换：
const coords = geoJson.coordinates.map(c => [c[1], c[0]]);
//                                           ↑纬度  ↑经度
```

**为什么 GeoJSON 是经度在前？**

GeoJSON 遵循数学坐标系 (x, y) 的习惯，经度对应 x 轴（东西方向），纬度对应 y 轴（南北方向）。这是 GeoJSON 标准（RFC 7946）的规定，虽然容易混淆，但必须遵守。

### 10.4 策略模式中的 `@Primary` 注解

`RoutingStrategyConfig` 声明 **`@Bean @Primary RouteStrategy activeRouteStrategy(...)`**，根据 **`routing.strategy`** 在三个实现类实例中**三选一**返回。三个 `@Service`/`@Component` 实现类本身仍是 Bean；**带 `@Primary` 的是工厂方法产出的那一个**，供 `RoutePlanningServiceImpl` 等 `@Autowired RouteStrategy` 注入点使用。

---

## 附录：知识点速查表

| 知识点 | 说明 |
|--------|------|
| 策略模式 | `RouteStrategy`接口 + 3个实现 + 配置切换 |
| GraphHopper 9.x | OSM 路网；`custom` + `CustomModel`（非旧版 `fastest`） |
| A* 算法 | `f=g+h`，启发函数（Haversine球面距离）引导搜索 |
| Weighted A* | epsilon参数控制激进程度，生成不同候选路线 |
| LLM_JUDGE策略 | 3条候选路线+历史数据 → LLM选最优 |
| LLM_WAYPOINT策略 | LLM决定绕路路点 → A*分段执行 |
| 防幻觉机制 | 验证LLM给出的坐标在地图范围内，A*自动映射到路网 |
| DeepSeekClient | `RestTemplate`+`temperature=0.1`+强制JSON输出 |
| 历史数据聚合 | 按小时统计均速、慢速热点（0.01度网格） |
| GeoJSON LineString | 路线坐标序列，格式为[[经度,纬度],...]（经度在前！） |
| 实时轨迹 | `POST .../track/location`，`track_time` + `heading` 等 → 前端橙线 |
| `@Primary` | 多个同类型Bean时指定默认注入的那个 |
| 空间索引 | `locationIndex.findClosest()`将任意坐标映射到路网节点 |
| PriorityQueue | A*使用最小堆按f值排序，保证最优先扩展有希望的节点 |
| Haversine公式 | 球面距离计算，比勾股定理更精确（考虑地球曲率） |
