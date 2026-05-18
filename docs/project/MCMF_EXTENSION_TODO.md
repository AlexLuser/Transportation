# MCMF 全国物流网络扩展 TODO

> 基于当前 Hub-and-Spoke 单城市系统，向全国多层级中转网络演进。  
> 核心算法：最小费用最大流（MCMF / SSP + SPFA）用于干线流量分配。

---

## 优先级说明

- 🔴 **P0**：算法核心，答辩必须能演示
- 🟡 **P1**：后端主链路，让数据真正流动起来
- 🟢 **P2**：前端可视化，演示加分项
- ⚪ **P3**：完善项，时间充裕再做

---

## 一、数据库层 🔴

### 新建表

- [ ] **P0** `national_hub` — 全国中转站（三级：全国枢纽/省级中心/城市配送中心）
  ```sql
  id, name, hub_level(0/1/2), province, city,
  latitude, longitude, max_capacity, current_load, status
  ```

- [ ] **P0** `hub_link` — Hub 间运输边（流网络的边）
  ```sql
  id, from_hub_id, to_hub_id,
  transport_mode(ROAD/RAIL/AIR),
  capacity_daily, cost_per_unit, distance_km, duration_hours, is_active
  ```
  - [ ] **P1** `hub_link` 追加字段（支持 LLM 费用校准对比）
    ```sql
    ALTER TABLE hub_link ADD COLUMN base_cost_per_unit DECIMAL(10,4)
        COMMENT '基准费用（LLM调整前的静态录入值，用于与动态调整后对比）';
    ```

- [ ] **P0** `flow_plan` — MCMF 流量规划单（每次规划一条记录）
  ```sql
  id, plan_date, total_demand, total_cost, status(PENDING/OPTIMIZING/DONE/FAILED), algorithm
  ```
  - [ ] **P1** `flow_plan` 追加字段（存储 LLM 分析结果）
    ```sql
    ALTER TABLE flow_plan ADD COLUMN llm_advice    TEXT    COMMENT 'LLM风险分析与调度建议文本';
    ALTER TABLE flow_plan ADD COLUMN llm_enhanced  TINYINT DEFAULT 0 COMMENT '是否使用LLM费用校准 0=否 1=是';
    ```

- [ ] **P0** `flow_plan_item` — 流量规划明细（每条边的分配流量）
  ```sql
  id, plan_id, from_hub_id, to_hub_id, link_id, flow_amount, edge_cost
  ```

- [ ] **P1** `inter_city_batch` — 跨城干线批次（由 flow_plan_item 驱动生成）
  ```sql
  id, flow_plan_id, from_hub_id, to_hub_id, transport_mode,
  planned_depart, actual_depart, status, item_count
  ```

### 修改现有表

- [ ] **P1** `warehouse` 追加字段
  ```sql
  ALTER TABLE warehouse ADD COLUMN affiliated_hub_id BIGINT
      COMMENT '归属城市级配送中心ID，关联 national_hub.id';
  ```

- [ ] **P1** `order_info` 追加字段
  ```sql
  ALTER TABLE order_info ADD COLUMN origin_hub_id        BIGINT;
  ALTER TABLE order_info ADD COLUMN dest_hub_id          BIGINT;
  ALTER TABLE order_info ADD COLUMN flow_plan_id         BIGINT;
  ALTER TABLE order_info ADD COLUMN inter_city_batch_id  BIGINT;
  ```

---

## 二、实体层（Entity）🔴

**路径：** `logistics-service/.../entity/`

- [ ] **P0** 新建 `NationalHub.java`
- [ ] **P0** 新建 `HubLink.java`
- [ ] **P0** 新建 `FlowPlan.java`
- [ ] **P0** 新建 `FlowPlanItem.java`
- [ ] **P1** 新建 `InterCityBatch.java`
- [ ] **P1** 修改 `shop-service/.../entity/Warehouse.java`，添加 `affiliatedHubId` 字段

---

## 三、Mapper 层 🟡

**路径：** `logistics-service/.../mapper/`

- [ ] **P1** 新建 `NationalHubMapper.java`（含 `selectNearestHub` 自定义查询）
- [ ] **P1** 新建 `HubLinkMapper.java`（含 `selectActiveLinks` 全量查询，用于构建图）
- [ ] **P0** 新建 `FlowPlanMapper.java`
- [ ] **P0** 新建 `FlowPlanItemMapper.java`（批量插入流量明细）
- [ ] **P1** 新建 `InterCityBatchMapper.java`

---

## 四、DTO 层 🟡

**路径：** `logistics-service/.../dto/`

- [ ] **P0** 新建 `McmfGraphDTO.java` — 构建图时的内存数据结构（节点列表 + 边列表）
- [ ] **P0** 新建 `McmfResultDTO.java` — MCMF 计算结果（总费用 + 每边流量）
- [ ] **P1** 新建 `EdgeCostCalibrationDTO.java` — LLM 费用校准结果（每条边的调整倍率 + LLM 理由）
  ```java
  Long linkId;          // hub_link.id
  double multiplier;    // 费用倍率，如 1.3 表示今日涨价 30%
  String reason;        // LLM 给出的调整原因
  boolean llmEnhanced;  // false=LLM调用失败已降级为倍率1.0
  ```
- [ ] **P1** 新建 `FlowPlanAdviceDTO.java` — LLM 规划结果解读
  ```java
  String bottleneckAnalysis;   // 瓶颈节点/边分析
  String costAnomalyWarning;   // 成本异常告警
  List<String> suggestions;    // 操作建议列表
  String summary;              // 自然语言摘要（写入 flow_plan.llm_advice）
  boolean llmEnhanced;
  ```
- [ ] **P1** 新建 `FlowPlanDetailDTO.java` — 规划单详情（含 item 列表，供前端展示）
- [ ] **P1** 新建 `InterCityBatchDTO.java` — 干线批次详情
- [ ] **P1** 新建 `NationalHubDTO.java` — Hub 信息展示（含层级、负载率）
- [ ] **P1** 新建 `NetworkTopologyDTO.java` — 全图拓扑（节点 + 边，供前端地图渲染）

---

## 五、算法核心（McmfService）🔴 最重要

**路径：** `logistics-service/.../service/`

### Service 接口

- [ ] **P0** 新建 `McmfService.java`
  ```java
  McmfResultDTO computeMinCostFlow(
      List<NationalHub> hubs,
      List<HubLink> links,
      Map<Long, Integer> supply   // 正=供给, 负=需求
  );
  ```

### Service 实现

- [ ] **P0** 新建 `McmfServiceImpl.java`

  核心数据结构（邻接表残差网络）：
  ```java
  int[] head, nxt, to, cap, cost;  // 正反向边
  int[] dist, inq, prevv, preve;   // SPFA辅助
  ```

  方法：
  - `addEdge(u, v, cap, cost)` — 加正向边+反向残差边
  - `spfa(s, t)` — Bellman-Ford队列优化，找最短费用增广路，返回是否可达
  - `computeMinCostFlow(...)` — 主循环：循环调用 spfa + 沿路增广，直到需求满足

  注意事项：
  - 节点编号从 0 开始，需将 Hub ID 映射到连续整数
  - 超级源点 S = nodeCount, 超级汇点 T = nodeCount+1
  - S 向每个供给节点连边（cap=供给量, cost=0）
  - 每个需求节点向 T 连边（cap=需求量, cost=0）
  - 中转节点（枢纽）无需特殊处理，自然满足流量守恒

---

## 六、其余 Service 层 🟡

**路径：** `logistics-service/.../service/`

### 新建接口

- [ ] **P1** `NationalNetworkService.java` — Hub/Link 的 CRUD + 拓扑查询
- [ ] **P1** `FlowPlanService.java` — 规划编排（读图→调MCMF→写结果→生成干线批次）
- [ ] **P1** `ShipmentRoutingService.java` — 订单归属Hub（geocoding → 分配最近city hub）
- [ ] **P1** `InterCityBatchService.java` — 干线批次生命周期（创建/发车/到达/触发末端）
- [ ] **P1** `LlmEdgeCostCalibrationService.java` — 【LLM切入点一】运行前动态校准边费用
  ```java
  /**
   * 根据今日上下文（日期、节假日、历史费用方差）
   * 为每条 hub_link 生成今日有效费用倍率
   * LLM 调用失败时全部降级为 1.0（费用不变）
   */
  List<EdgeCostCalibrationDTO> calibrate(List<HubLink> links, LocalDate planDate);
  ```
- [ ] **P1** `LlmFlowPlanAdviceService.java` — 【LLM切入点二】运行后解读规划结果
  ```java
  /**
   * 分析 MCMF 流量分配结果，识别瓶颈、成本异常、单点风险
   * LLM 调用失败时返回空建议，flow_plan.llm_advice 留空，不影响主流程
   */
  FlowPlanAdviceDTO advise(List<FlowPlanItem> items, List<NationalHub> hubs, List<HubLink> links);
  ```

### 新建实现

- [ ] **P1** `NationalNetworkServiceImpl.java`
- [ ] **P1** `FlowPlanServiceImpl.java`

  核心方法 `triggerDailyPlan()` 流程（含 LLM 切入点）：
  ```
  1. 统计各 city hub 当日供给/需求（查 order_info.origin/dest_hub_id）

  2. 从 hub_link 查所有激活边
  ┌─ 2a.【LLM切入点一】调用 LlmEdgeCostCalibrationService.calibrate()
  │       输入：所有激活边 + 今日日期
  │       LLM返回：每条边的费用倍率（如 L05铁路×1.3，因节前运力紧张）
  │       将 cost_per_unit × multiplier 作为今日有效费用
  │       失败降级：所有倍率=1.0，llm_enhanced=false
  └─ 2b. 用动态费用构建 McmfGraphDTO

  3. 调用 McmfService.computeMinCostFlow()（纯数学，LLM不介入）

  4. 将结果写入 flow_plan（llm_enhanced字段记录是否启用了LLM校准）
     批量写入 flow_plan_item

  ┌─ 4a.【LLM切入点二】调用 LlmFlowPlanAdviceService.advise()
  │       输入：flow_plan_item 明细（各边流量/容量占比）
  │       LLM返回：瓶颈分析 + 成本异常告警 + 操作建议
  │       将 summary 写入 flow_plan.llm_advice
  │       失败降级：llm_advice 留空，继续正常流程
  └─ 4b. 更新 flow_plan 记录

  5. 为每条有流量的边生成 inter_city_batch 记录
  ```

- [ ] **P1** `LlmEdgeCostCalibrationServiceImpl.java`

  实现要点（参照 `LlmJudgeRouteStrategy` 模式）：
  - 调用 `RouteHistoryService` 类似的历史数据服务，获取各边历史费用方差
  - 构建 Prompt：今日日期 + 各边基准费用 + 历史波动范围
  - 解析 LLM 返回的 JSON，提取每条边的 `multiplier`
  - `try-catch` 兜底：异常时返回所有边 multiplier=1.0

- [ ] **P1** `LlmFlowPlanAdviceServiceImpl.java`

  实现要点（参照 `GlobalDispatchAdviceServiceImpl` 模式）：
  - 计算各边负载率：`flow_amount / capacity_daily`
  - 构建 Prompt：高负载边列表（>80%）+ 成本超历史均值的边
  - 解析 LLM 返回的 JSON，提取 bottleneck/warning/suggestions
  - `try-catch` 兜底：异常时返回空 `FlowPlanAdviceDTO`

- [ ] **P1** `ShipmentRoutingServiceImpl.java`
- [ ] **P1** `InterCityBatchServiceImpl.java`

### 修改现有实现

- [ ] **P1** 修改 `LogisticsBatchServiceImpl.java`，新增方法：
  ```java
  void activateFromInterCityArrival(Long interCityBatchId, Long destHubId);
  ```
  在干线到达目标城市 Hub 后，触发现有末端 VRP 逻辑。

---

## 七、Controller 层 🟡

**路径：** `logistics-service/.../controller/`

### 新建 Controller

- [ ] **P1** 新建 `NationalNetworkController.java` — Hub/Link 拓扑管理
  ```
  GET  /api/logistics/national-network/hubs        查询Hub列表（可按层级过滤）
  POST /api/logistics/national-network/hubs        新增Hub
  PUT  /api/logistics/national-network/hubs/{id}   修改Hub
  GET  /api/logistics/national-network/links       查询运输边列表
  POST /api/logistics/national-network/links       新增运输边
  GET  /api/logistics/national-network/topology    全图拓扑JSON（供前端地图渲染）
  ```

- [ ] **P0** 新建 `FlowPlanController.java` — MCMF 规划结果查询（只读，不含批次生命周期）
  ```
  GET  /api/logistics/flow-plan/latest             查询最新规划结果摘要
  GET  /api/logistics/flow-plan/{id}               查询指定规划详情
  GET  /api/logistics/flow-plan/{id}/items         查询各边流量分配明细
  ```

### 扩展现有 Controller

- [ ] **P0** 扩展 `DispatchController.java` — 新增全国干线调度接口（不新建 Controller）

  > ⚠️ **不新建 `InterCityBatchController`**，避免与现有"智能调度"在 UI 上形成两套并列
  > 的批次管理，改为在 `DispatchController` 内以 `/dispatch/national/` 命名空间区分层级

  ```
  ── 阶段一：全国干线调度（新增）──────────────────────────────────
  GET  /api/logistics/dispatch/national/preview
       触发 MCMF + LLM费用校准，返回各城市间流量分配预览
       （不落库，供管理员审阅后决定是否执行）

  POST /api/logistics/dispatch/national/execute
       确认执行：MCMF结果落库(flow_plan)，生成 inter_city_batch 列表
       同时调用 LlmFlowPlanAdviceService 写 llm_advice

  GET  /api/logistics/dispatch/national/batches
       查询当日跨城干线批次列表（状态/起止Hub/件数/运输方式）

  POST /api/logistics/dispatch/national/{batchId}/depart
       标记发车：inter_city_batch.status → DEPARTED

  POST /api/logistics/dispatch/national/{batchId}/arrive
       标记到达：inter_city_batch.status → ARRIVED
       → 调用 InterCityBatchService.onArrival()
         → 将目标城市订单写入 dispatch_pool（status=PENDING）
         → 触发目标城市末端调度（现有 /dispatch/preview + /dispatch/execute 流程）

  ── 阶段二：城市末端调度（现有接口，完全不变）──────────────────
  GET  /api/logistics/dispatch/pool               （不变）
  GET  /api/logistics/dispatch/preview            （不变）
  POST /api/logistics/dispatch/execute            （不变）
  GET  /api/logistics/dispatch/check-urgency      （不变）
  ```

---

## 八、前端页面 🟢

**路径：** `frontend/src/pages/Admin/`

### 新建页面

- [ ] **P0** 新建 `NationalNetwork.vue` — 全国Hub网络地图（高德全国底图 + 节点/边渲染）
  - 节点按层级区分颜色（全国枢纽/省级/城市）
  - 边按运输模式区分（公路/铁路/航空）
  - 点击节点显示当日负载率和容量使用率

- [ ] **P0** 新建 `FlowPlan.vue` — MCMF规划结果展示（只读，调试/审计用）
  - 流量热力图（边的粗细表示流量大小）
  - 规划费用 vs 非优化费用对比
  - 各边流量明细表格（含 LLM 校准前/后费用对比列）
  - LLM AI 分析面板（瓶颈告警、成本异常、建议）

### 扩展现有页面

- [ ] **P0** 扩展 `Dispatch.vue` — 改为双 Tab，不新建 `InterCityBatch.vue`

  > ⚠️ **不新建 `InterCityBatch.vue`**，避免管理员在两个页面间切换产生割裂感

  ```
  Dispatch.vue
  ├── Tab 1：全国干线（新增）
  │   ├── [预览全国流量] 按钮
  │   │     → GET /dispatch/national/preview
  │   │     → 展示：各城市间建议流量卡片 + 全国地图预览
  │   ├── [确认执行] 按钮
  │   │     → POST /dispatch/national/execute
  │   │     → 展示：生成的跨城干线批次列表
  │   └── 跨城干线批次表格（状态/起止Hub/件数/运输方式）
  │         [标记发车] → POST /dispatch/national/{id}/depart
  │         [标记到达] → POST /dispatch/national/{id}/arrive
  │                      到达后：目标城市 Tab2 调度池自动刷新
  │
  └── Tab 2：城市末端（现有，完全不变）
        调度池 → K-Means预览 → LLM建议 → 执行调度
  ```

### 其他前端改动

- [ ] **P1** 修改 `frontend/src/api/logistics.ts`，新增 national dispatch 相关 API 调用函数
- [ ] **P1** 修改 `frontend/src/router/index.ts`，新增 `NationalNetwork` 和 `FlowPlan` 路由

---

## 九、全链路物流流程与状态机 🔴

> 本节定义从「顾客下单」到「签收完成」的完整状态流转，以及各服务/表在每个阶段的职责。
> 是开发和联调的核心依据，所有状态变更必须与此保持一致。

---

### 9.1 涉及的状态字段汇总

| 表 | 字段 | 所有状态 |
|---|---|---|
| `order_info` | `order_status` | 0=待支付 1=待发货 2=待揽件 3=派送中 4=已完成 5=已取消 |
| `logistics_batch` | `batch_status` | 0=待出发 1=干线运输中 2=已到中转站 3=末端派送中 4=全部完成 |
| `inter_city_batch` | `status` | CREATED→DEPARTED→ARRIVED→DISPATCHED |
| `logistics_route` | `route_status` | -1=待激活 0=待出发 1=运输中 2=已送达 3=异常 |
| `order_delivery` | `delivery_status` | 0=待接单 1=已接单 2=运输中 3=已送达 4=已取消 |
| `dispatch_pool` | `status` | 0=待调度 1=已调度 |

---

### 9.2 完整流程图（同城 vs 跨城）

```
                    ┌─────────────────────────────────────────────────┐
                    │              顾客下单并支付                       │
                    │  order_status: 0(待支付) → 1(待发货)             │
                    └────────────────────┬────────────────────────────┘
                                         │ 商户确认发货
                                         │ ShipmentRoutingService 分配 origin_hub_id / dest_hub_id
                                         ▼
                    ┌─────────────────────────────────────────────────┐
                    │  order_status → 2(待揽件)                        │
                    │  订单进入 dispatch_pool（status=0）               │
                    └────────────────────┬────────────────────────────┘
                                         │
                     ┌───────────────────┴───────────────────┐
                     │ 同城订单                                │ 跨城订单
                     │ origin_hub == dest_hub                 │ origin_hub ≠ dest_hub
                     ▼                                        ▼
        ┌──────────────────────────┐           ┌──────────────────────────────┐
        │  【阶段A】城市直接调度    │           │  【阶段B1】全国MCMF干线调度   │
        │  DispatchController      │           │  每日6:00自动 或 手动触发     │
        │  /dispatch/preview       │           │  LLM校准边费用               │
        │  K-Means + LLM建议       │           │  MCMF计算最优流量分配         │
        │  /dispatch/execute       │           │  生成 inter_city_batch        │
        │  创建 logistics_batch     │           │  LLM解读结果写 llm_advice     │
        │  batch_status → 0        │           └──────────────┬───────────────┘
        └──────────┬───────────────┘                          │
                   │                              ┌───────────▼───────────────┐
                   │                              │  【阶段B2】干线运输中      │
                   │                              │  管理员点[标记发车]        │
                   │                              │  inter_city_batch → DEPARTED│
                   │                              │  order_status → 3(派送中)  │
                   │                              └───────────┬───────────────┘
                   │                                          │
                   │                              ┌───────────▼───────────────┐
                   │                              │  【阶段B3】到达目标城市    │
                   │                              │  管理员点[标记到达]        │
                   │                              │  inter_city_batch → ARRIVED│
                   │                              │  订单写入目标城市          │
                   │                              │  dispatch_pool（status=0） │
                   │                              └───────────┬───────────────┘
                   │                                          │
                   │                              ┌───────────▼───────────────┐
                   │                              │  【阶段B4】目标城市调度    │
                   │                              │  目标城市管理员            │
                   │                              │  /dispatch/preview         │
                   │                              │  K-Means + LLM建议        │
                   │                              │  /dispatch/execute         │
                   │                              │  创建本地 logistics_batch  │
                   │                              └───────────┬───────────────┘
                   │                                          │
                   └──────────────────┬───────────────────────┘
                                      │ （同城/跨城最终在此汇合）
                                      ▼
              ┌─────────────────────────────────────────────────────────┐
              │  【阶段C】城市本地批次执行（现有流程，完全不变）            │
              │                                                          │
              │  VRP规划访问顺序                                          │
              │  LLM选Hub（或不走Hub直送）                               │
              │  创建干线路线 仓库→城市Hub                               │
              │    logistics_route segmentType=1, routeStatus=0         │
              │    order_delivery segmentType=1, deliveryStatus=0       │
              │    logistics_batch batch_status=0 → 1(干线运输中)       │
              │                                                          │
              │  司机接单干线 → 运输到Hub                                │
              │    delivery_status: 0→1→2                               │
              │    route_status: 0→1                                     │
              │                                                          │
              │  到Hub后 activateLastMileRoutes() 触发                  │
              │    batch_status → 2(已到中转站)                         │
              │    末端路线 routeStatus: -1 → 0(激活)                   │
              │    末端配送单 order_delivery 创建 deliveryStatus=0      │
              │                                                          │
              │  末端司机接单 → 逐站配送                                 │
              │    delivery_status: 0→1→2→3(已送达)                    │
              │    route_status: 0→1→2                                  │
              │    batch_status → 3(末端派送中)                         │
              │    order_status → 3(派送中) ← 此处如未更新需补充触发    │
              └──────────────────────┬──────────────────────────────────┘
                                     │ 所有停靠点签收完毕
                                     ▼
              ┌─────────────────────────────────────────────────────────┐
              │  【阶段D】完成                                            │
              │  batch_item.item_status → 2(已送达)                     │
              │  checkAndUpdateBatchStatus() → batch_status → 4         │
              │  order_status → 4(已完成)   ← ⚠️ 需补充此处触发逻辑    │
              └─────────────────────────────────────────────────────────┘
```

---

### 9.3 关键状态变更触发点（需补充/确认的代码逻辑）

- [ ] **P1** `order_status 2→3`（待揽件→派送中）的触发时机：
  - **同城**：`LogisticsBatchServiceImpl.createBatch()` 执行后，order-service 收到 Feign/MQ 通知更新
  - **跨城**：`InterCityBatchService.onDeparture()` 触发（干线发车时即视为已开始派送）
  - 当前代码中该触发是否已存在？→ **需确认** `driver-service` 中是否有回调

- [ ] **P1** `order_status 3→4`（派送中→已完成）的触发时机：
  - 末端司机标记最后一个停靠点送达后
  - `LogisticsBatchController.completeStop()` 执行后，检查到 `allDone=true`
  - → 应通过 Feign 调用 `order-service` 将 `order_status` 改为 4
  - 当前代码是否已实现？→ **需确认**

- [ ] **P1** 跨城订单 `dispatch_pool` 的隔离：
  - `DispatchController.getDispatchPool()` 当前返回所有待调度订单
  - 加 MCMF 后需过滤：
    - Tab1（全国干线）：`dest_hub_id ≠ 当前城市Hub` 的订单
    - Tab2（城市末端）：`dest_hub_id = 当前城市Hub` 或 `inter_city_batch 已到达` 的订单

---

### 9.4 前端各角色页面的流程展示

| 角色 | 页面 | 显示内容 |
|---|---|---|
| 顾客 | `Customer/Order.vue` | 订单状态卡片：待揽件/派送中/已完成；实时轨迹地图 |
| 商户 | `Shop/Order.vue` | 订单列表：已发货订单关联的批次号、当前批次状态 |
| 管理员 | `Admin/Dispatch.vue` | 双Tab：全国干线（MCMF）+ 城市末端（K-Means） |
| 管理员 | `Admin/NationalNetwork.vue` | 全国Hub网络地图 + 当日流量热力图 |
| 管理员 | `Admin/FlowPlan.vue` | MCMF规划明细 + LLM费用校准结果 + AI分析 |
| 管理员 | `Admin/Batches.vue` | 本地批次列表（现有，对应城市末端） |
| 司机 | `Driver/Delivery.vue` | 配送任务列表（干线/末端均在此，segmentType区分展示） |
| 司机 | `Driver/Navigation.vue` | 当前路线导航 + 多停靠点逐站确认 |

---

## 十、商家发货改造与仓库→MCMF衔接 🟡

> 当前商家发货弹窗是纯确认框，仓库在下单时自动选定，商家无法干预。
> 本节改造发货流程，使商家可选仓库，并在发货时完成 origin_hub_id / dest_hub_id 的写入，
> 打通仓库到 MCMF 全国调度的衔接链路。

---

### 10.1 数据库追加字段

- [ ] **P1** `dispatch_pool` 表追加字段（区分同城/跨城 + 跨城到达后的调度起点）
  ```sql
  ALTER TABLE dispatch_pool ADD COLUMN origin_hub_id       BIGINT   COMMENT '发货所在城市Hub';
  ALTER TABLE dispatch_pool ADD COLUMN dest_hub_id         BIGINT   COMMENT '收货所在城市Hub';
  ALTER TABLE dispatch_pool ADD COLUMN is_cross_city       TINYINT  DEFAULT 0 COMMENT '是否跨城 0=同城 1=跨城';
  ALTER TABLE dispatch_pool ADD COLUMN dispatch_origin_type TINYINT DEFAULT 0 COMMENT '调度起点类型 0=仓库 1=干线到达Hub';
  ALTER TABLE dispatch_pool ADD COLUMN dispatch_origin_lat  DOUBLE  COMMENT '实际调度起点纬度（type=1时有值）';
  ALTER TABLE dispatch_pool ADD COLUMN dispatch_origin_lng  DOUBLE  COMMENT '实际调度起点经度';
  ALTER TABLE dispatch_pool ADD COLUMN dispatch_origin_addr VARCHAR(200) COMMENT '实际调度起点地址';
  ```

---

### 10.2 后端：order-service 改造

- [ ] **P1** 新增 `ShipOrderRequestDTO.java`（`order-service/.../dto/`）
  ```java
  Long warehouseId;   // 商家选择的发货仓库
  ```

- [ ] **P1** `OrderController` 新增专用发货接口（替代原 `updateOrderStatus` 的发货场景）
  ```
  POST /api/orders/{orderId}/ship
  RequestBody: { warehouseId: Long }

  职责：
    1. 校验订单状态（orderStatus=1 且 paymentStatus=1）
    2. 委托 OrderService.shipOrder(orderId, warehouseId)
  ```

- [ ] **P1** `OrderServiceImpl` 新增 `shipOrder(orderId, warehouseId)` 方法
  ```
  执行流程：
    1. 校验：订单存在 + orderStatus=1 + paymentStatus=1
    2. 校验：调用 ShopFeignClient 确认所选仓库对该订单商品有库存
    3. 更新 order_info.warehouse_id = warehouseId（覆盖下单时自动选的）
    4. 调用 LogisticsFeignClient.assignHubs(warehouseId, endLat, endLng)
         → 返回 { originHubId, destHubId, isCrossCity }
    5. 更新 order_info.origin_hub_id / dest_hub_id
    6. 更新 order_info.order_status = 2, shipping_time = now()
    7. 发 AddToPoolMessage（追加 originHubId / destHubId / isCrossCity 字段）
  ```

- [ ] **P1** `order-service` 新增 `LogisticsFeignClient`（或复用已有的）
  ```java
  @FeignClient(name = "logistics-service")
  interface LogisticsFeignClient {
      @PostMapping("/api/logistics/routing/assign-hubs")
      HubAssignmentDTO assignHubs(@RequestBody AssignHubsRequestDTO req);
  }
  // AssignHubsRequestDTO: { warehouseId, endLat, endLng }
  // HubAssignmentDTO:     { originHubId, destHubId, isCrossCity }
  ```

---

### 10.3 后端：logistics-service 新增衔接接口

- [ ] **P1** 新增 `ShipmentRoutingController.java`（`logistics-service/.../controller/`）
  ```
  POST /api/logistics/routing/assign-hubs
  RequestBody: { warehouseId, endLat, endLng }

  内部逻辑（调用 ShipmentRoutingService）：
    Step1: SELECT affiliated_hub_id FROM warehouse WHERE id = warehouseId
               ⚠️ 检查点1：warehouse.affiliated_hub_id 必须已录入数据
    Step2: SELECT id FROM national_hub
               ORDER BY haversine(latitude, longitude, endLat, endLng) LIMIT 1
               ⚠️ 检查点2：national_hub 表必须有数据（见十一、数据准备）
    Step3: 比较 originHubId vs destHubId → 判断 isCrossCity
    Step4: 返回 { originHubId, destHubId, isCrossCity }
  ```

- [ ] **P1** `ShipmentRoutingServiceImpl` 实现上述逻辑（接口已在六、Service层定义）

---

### 10.4 前端：Shop/Order.vue 发货弹窗改造

- [ ] **P1** 发货弹窗增加仓库选择下拉框

  ```
  改造前：                         改造后：
  ┌──────────────────────┐         ┌──────────────────────────────────┐
  │ 确认备货完成          │         │ 确认发货                          │
  │ [提示：无需选仓库...] │         │ 订单号：ORD-001                   │
  │ 订单号：ORD-001       │         │                                  │
  │                      │   →     │ 选择发货仓库：                    │
  │ [取消] [确认]         │         │ [▼ 上海松江仓（库存42件）]        │
  └──────────────────────┘         │   上海松江仓（42件）✓             │
                                   │   上海浦东仓（0件）  禁选          │
                                   │   南京仓（18件）     ✓            │
                                   │                                  │
                                   │ 收货地：成都市武侯区...            │
                                   │ 📍 预计：跨城配送                 │ ← 可选
                                   │                                  │
                                   │ [取消] [确认发货]                 │
                                   └──────────────────────────────────┘
  ```

  **逻辑改动点：**
  - 打开弹窗时：`GET /api/shops/warehouses-with-stock?orderId=` 获取有库存仓库列表
  - 只展示 `stock > 0` 的仓库，`stock = 0` 的 `disabled`
  - 默认选中库存最多的仓库
  - 点确认：调用 `POST /api/orders/{orderId}/ship { warehouseId }`（新接口）

- [ ] **P1** `frontend/src/api/shop.ts` 新增：
  ```ts
  // 查询订单相关商品各仓库库存（供发货仓库选择）
  getWarehousesWithStock(orderId: number): Promise<WarehouseStockItem[]>
  // 商家发货（含仓库选择）
  shipOrder(orderId: number, warehouseId: number): Promise<void>
  ```

---

### 10.5 管理员调度页（Dispatch.vue Tab2）适配改动

> 当前 Tab2 仓库选择器**保留不变**，继续负责同城订单的过滤和 VRP 起点定位。
> 仅需做以下 4 处小改动，以兼容跨城干线到达后的订单。

- [ ] **P1** `dispatch_pool` 写入跨城到达订单时填充 `dispatch_origin_*` 字段
  — 在 `InterCityBatchService.onArrival()` 中：
  ```java
  pool.setDispatchOriginType(1);                    // 1=干线到达Hub
  pool.setDispatchOriginLat(destHub.getLatitude());
  pool.setDispatchOriginLng(destHub.getLongitude());
  pool.setDispatchOriginAddr(destHub.getName() + "（干线到达）");
  pool.setWarehouseId(null);                        // 无关联仓库
  ```

- [ ] **P1** `Dispatch.vue` — `filteredPool` 计算属性加一行
  ```typescript
  // 原逻辑：只显示选中仓库的订单
  // 新增：干线到达的订单（warehouseId=null）始终显示在池中
  return poolItems.value.filter(o =>
      o.warehouseId === params.warehouseId ||
      o.dispatchOriginType === 1   // 跨城干线到达，起点是目标Hub
  )
  ```

- [ ] **P1** `Dispatch.vue` — 订单池表格新增"来源"列（1列）
  ```html
  <el-table-column label="来源" width="110">
      <template #default="{ row }">
          <el-tag v-if="row.dispatchOriginType === 1" type="warning" size="small">
              🚄 干线到达
          </el-tag>
          <span v-else class="text-muted">🏭 {{ row.warehouseId ? '仓库' : '-' }}</span>
      </template>
  </el-table-column>
  ```

- [ ] **P1** `Dispatch.vue` — `handleExecute()` 中 VRP 起点坐标取值改造（约5行）
  ```typescript
  // 原：固定取 warehouse.latitude / longitude
  // 改：判断批次类型，干线到达批次用 Hub 坐标
  const isHubOrigin = c.orders.some((o: any) => o.dispatchOriginType === 1)
  const origin = isHubOrigin
      ? { lat: c.orders[0].dispatchOriginLat,
          lng: c.orders[0].dispatchOriginLng,
          addr: c.orders[0].dispatchOriginAddr }
      : { lat: warehouse.latitude  ?? 0,
          lng: warehouse.longitude ?? 0,
          addr: [warehouse.province, warehouse.city,
                 warehouse.district, warehouse.detailAddress]
                  .filter(Boolean).join('') || warehouse.warehouseName }

  // 构建 ExecuteBatchItem 时使用 origin.lat/lng/addr 替代原 warehouse 坐标
  ```

---

### 10.6 衔接检查点速查

| # | 检查项 | 位置 | 当前状态 |
|---|---|---|---|
| 1 | `warehouse.affiliated_hub_id` 字段存在且已录入 | `warehouse` 表 | ❌ 需 ALTER + 录数据 |
| 2 | `national_hub` 表有全国 Hub 数据 | `national_hub` 表 | ❌ 需建表 + 测试数据 |
| 3 | `order_info` 有 `origin_hub_id` / `dest_hub_id` 字段 | `order_info` 表 | ❌ 需 ALTER（见一、数据库层）|
| 4 | `dispatch_pool` 有7个新字段 | `dispatch_pool` 表 | ❌ 需 ALTER（见本节 10.1）|
| 5 | `LogisticsFeignClient` 在 order-service 注册 | `order-service/feign/` | ❌ 需新建 |
| 6 | `ShipmentRoutingController` 接口可被 Feign 调用 | `logistics-service` | ❌ 需新建 |
| 7 | `DispatchController` 调度池按 `is_cross_city` / `dest_hub_id` 过滤 | 现有代码 | ❌ 需改造 |
| 8 | `Dispatch.vue` Tab2 的 `filteredPool` / `handleExecute()` 适配 | 前端 | ❌ 见本节 10.5 |

---

## 十一、MCMF 手动触发与数据准备 🔴

> **默认使用手动触发**，方便演示时随时控制规划时机。
> 自动定时触发作为可选配置，生产环境才启用。

---

### 11.1 触发模式配置

- [ ] **P0** `logistics-service/application.yml` 修改为手动触发优先

  ```yaml
  mcmf:
    # 触发模式：manual=仅手动，auto=仅定时，both=两者并存
    # 演示/毕设场景使用 manual，部署上线时改为 auto 或 both
    trigger-mode: manual

    schedule:
      enabled: false            # 默认关闭定时自动触发
      cron: "0 0 6 * * ?"      # 若 trigger-mode=auto/both 时生效

    max-nodes: 500
    max-edges: 2000
  ```

- [ ] **P0** `FlowPlanServiceImpl` 支持手动触发入口
  ```java
  // 由 DispatchController.previewNationalDispatch() 调用
  // 不依赖定时器，直接同步执行并返回结果
  FlowPlanDetailDTO triggerManually(LocalDate planDate);
  ```

- [ ] **P1** 如需定时触发，在 `FlowPlanServiceImpl` 增加 `@Scheduled` 方法
  ```java
  @Scheduled(cron = "${mcmf.schedule.cron}")
  @ConditionalOnProperty(name = "mcmf.schedule.enabled", havingValue = "true")
  public void scheduledTrigger() {
      triggerManually(LocalDate.now());
  }
  ```

---

### 11.2 手动触发的前端入口

- [ ] **P0** `Dispatch.vue` Tab1（全国干线）顶部加"立即规划"按钮

  ```
  Tab1：全国干线
  ┌─────────────────────────────────────────────────────┐
  │  [立即规划]  规划日期：2026-04-27  [查看历史规划 ▼]  │  ← 操作栏
  ├─────────────────────────────────────────────────────┤
  │  规划状态：⏳ 待规划 / ✅ 已完成 / ❌ 失败           │
  │                                                     │
  │  点击[立即规划]后：                                  │
  │   1. 按钮变为 loading 状态                          │
  │   2. 调用 POST /dispatch/national/preview           │
  │   3. 显示 MCMF 流量分配预览（各城市间流量卡片）      │
  │   4. 显示 LLM 费用校准结果                          │
  │   5. 出现 [确认执行] 按钮                           │
  │      → POST /dispatch/national/execute              │
  │      → 生成干线批次列表，出现在下方表格中           │
  └─────────────────────────────────────────────────────┘
  ```

---

## 十二、跨城订单路线图展示 🟢

> **问题背景**：用户在 `Customer/Order.vue` 中看到路线地图的前提是 `getRouteByOrderId()` 能返回
> 对应的 `logistics_route` 记录。但跨城订单在干线运输阶段（阶段B2/B3）尚无末端路线，
> 导致 `orderStatus=3`（派送中）但地图显示"暂无物流信息"，空窗期可能持续数小时甚至数天。
>
> **解决方案**：引入 `segmentType=3`（跨城干线虚拟路线），在干线发车时由后端自动创建，
> 用 Hub→Hub 的简单直线作为 `plannedRoute`，前端识别后显示跨城专用展示样式。

---

### 12.1 为何干线段不能使用 GraphHopper 规划

| 限制项 | 说明 |
|---|---|
| OSM 数据范围 | 当前系统只加载了 `shanghai-xxx.osm.pbf`，GraphHopper 只能规划上海市内道路 |
| 运输方式 | 铁路（RAIL）/航空（AIR）是固定班次，不存在"路网"可规划 |
| 距离量级 | 全国干线距离 500-2000km，A* 算法不适用于此规模 |
| **结论** | 干线段**不做路网规划**，仅用起止 Hub 坐标表示，末端段沿用现有 VRP + A* 流程 |

---

### 12.2 数据库：`logistics_route` 新增 segmentType 枚举值

- [ ] **P1** 在 `logistics_route.segment_type` 字段注释中补充枚举值说明：
  ```sql
  -- segment_type 枚举值：
  -- 1 = 本地干线（仓库→城市Hub，GraphHopper规划）
  -- 2 = 末端配送（城市Hub→客户，GraphHopper规划）
  -- 3 = 跨城干线（全国Hub→Hub，直线虚拟路线，不经GraphHopper）
  ```

- [ ] **P1** `logistics_route` 表可选追加字段用于标识干线批次关联
  ```sql
  ALTER TABLE logistics_route
      ADD COLUMN inter_city_batch_id BIGINT NULL COMMENT '关联的跨城干线批次ID（segmentType=3时有值）';
  ```

---

### 12.3 后端：干线发车时自动创建 segmentType=3 虚拟路线

- [ ] **P1** `InterCityBatchService.onDeparture(Long batchId)` 中补充虚拟路线创建逻辑：
  ```java
  // 为该批次内所有订单创建跨城干线虚拟路线（segmentType=3）
  // plannedRoute = 简单 LineString GeoJSON（只有起点Hub和终点Hub两个坐标点）
  void createTrunkVirtualRoutes(InterCityBatch batch) {
      NationalHub from = nationalHubMapper.selectById(batch.getFromHubId());
      NationalHub to   = nationalHubMapper.selectById(batch.getToHubId());

      // 构造直线 GeoJSON（不调用 GraphHopper）
      String plannedRoute = buildStraightLineGeoJson(from, to);

      for (Long orderId : getOrderIdsByBatchId(batch.getId())) {
          LogisticsRoute route = new LogisticsRoute();
          route.setOrderId(orderId);
          route.setSegmentType(3);                     // 跨城干线虚拟
          route.setInterCityBatchId(batch.getId());
          route.setStartAddress(from.getName());
          route.setStartLatitude(from.getLatitude());
          route.setStartLongitude(from.getLongitude());
          route.setEndAddress(to.getName());
          route.setEndLatitude(to.getLatitude());
          route.setEndLongitude(to.getLongitude());
          route.setPlannedRoute(plannedRoute);          // Hub→Hub 直线
          route.setRouteStatus(1);                      // 运输中（直接激活）
          logisticsRouteMapper.insert(route);
      }
  }

  // 辅助方法：构造 GeoJSON LineString
  private String buildStraightLineGeoJson(NationalHub from, NationalHub to) {
      return """
          {"type":"LineString","coordinates":[[%f,%f],[%f,%f]]}
          """.formatted(from.getLongitude(), from.getLatitude(),
                        to.getLongitude(),   to.getLatitude());
  }
  ```

- [ ] **P1** `InterCityBatchService.onArrival(Long batchId)` 中将上述虚拟路线标记为完成：
  ```java
  // 到达时将 segmentType=3 的路线置为 routeStatus=2（已送达/已完成）
  logisticsRouteMapper.updateStatusByInterCityBatchId(batchId, 2);
  ```

---

### 12.4 前端：`Customer/Order.vue` 识别跨城干线段展示

- [ ] **P1** `Customer/Order.vue` 物流信息区块，新增跨城干线专用展示分支

  ```
  当前展示逻辑（修改前）：
    orderStatus=2 → "订单待揽件"
    orderStatus=3/4 → getRouteByOrderId() → 找到路线 → RouteMap

  修改后：
    orderStatus=2 → "订单待揽件"（不变）
    orderStatus=3/4：
      ├── 路线 segmentType=3（跨城干线中）→ 跨城展示组件（见下方）
      └── 路线 segmentType=1/2（末端配送）→ 现有 RouteMap 组件（不变）
  ```

  跨城干线展示样式：
  ```
  ┌─────────────────────────────────────┐
  │ 🚄 跨城干线运输中                    │
  ├─────────────────────────────────────┤
  │ 起点：上海配送中心（Hub）            │
  │ 终点：成都配送中心（Hub）            │
  │ 运输方式：铁路 RAIL                 │  ← 从 inter_city_batch 取
  │ 批次号：IB-003                      │
  │ ────────────────────── ──────────── │
  │  🔵上海 ─────────────────→ 🔴成都   │  ← 全国简单地图（高德）两点连线
  └─────────────────────────────────────┘
  ```

  关键代码改动点：
  - `getRouteByOrderId` 返回结果中增加 `segmentType` 字段（后端 RouteDetailDTO 已有此字段）
  - 在物流信息 `v-else-if` 分支中，用 `currentRoute.route?.segmentType === 3` 区分渲染
  - 跨城地图可复用高德 JS API，仅绘制两个 Hub 坐标之间的直线，**不调用路径规划**

- [ ] **P2** 跨城段地图：展示全国范围底图（高德），两 Hub 之间绘制弧线/直线连接

  ```typescript
  // 伪代码：在全国底图上绘制 Hub 连线
  const map = new AMap.Map('trunk-map', { zoom: 5, center: [中心坐标] })
  AMap.plugin('AMap.Polyline', () => {
      new AMap.Polyline({
          path: [[startLng, startLat], [endLng, endLat]],
          strokeColor: '#1677ff',
          strokeWeight: 3,
          strokeStyle: 'dashed'    // 虚线表示运输中
      }).setMap(map)
  })
  ```

---

### 12.5 前端：`Shop/Order.vue` 配送路线区块改造

> **现状**：`Shop/Order.vue` 第128行起已有"配送路线"区块，使用 `RouteMap` 组件展示
> （`orderStatus >= 2` 时调用 `getRouteByOrderId`）。跨城订单在干线运输阶段同样会
> 找不到路线记录，出现"暂无路线信息"的空白。

- [ ] **P1** `Shop/Order.vue` 配送路线区块增加 `segmentType=3` 分支

  改动位置：`Shop/Order.vue` 第133行 `<template v-else-if="detailRoute">` 内部

  ```
  改造前：
    <template v-else-if="detailRoute">
        <RouteMap :startLat="..." :endLat="..." ... />

  改造后：
    <template v-else-if="detailRoute">
        <!-- ① 跨城干线运输中 -->
        <div v-if="detailRoute.route?.segmentType === 3" class="trunk-transit-info">
            <el-alert type="info" show-icon :closable="false">
                <template #title>🚄 跨城干线运输中</template>
                <template #default>
                    {{ detailRoute.route?.startAddress }} → {{ detailRoute.route?.endAddress }}
                    <br/>批次号：{{ detailRoute.route?.interCityBatchId ?? '-' }}
                </template>
            </el-alert>
        </div>
        <!-- ② 末端配送（原有逻辑不变） -->
        <RouteMap v-else ... />
  ```

  > 商户无需看到全国地图弧线，简单展示"在途提示 + 起止城市"即可。

---

### 12.6 前端：`Admin/Orders.vue` 订单详情新增物流追踪区块

> **现状**：`Admin/Orders.vue` 详情弹窗（第89~127行）**完全没有物流路线**，
> 只显示基本信息和商品明细。管理员目前无法从订单维度看到物流状态。

- [ ] **P1** `Admin/Orders.vue` 详情弹窗新增"物流追踪"区块（位于商品明细之后）

  条件：`orderStatus >= 2 && orderStatus !== 5`（发货后、未取消）时显示

  ```html
  <!-- 物流追踪区块 -->
  <div v-if="detailData.order.orderStatus >= 2 && detailData.order.orderStatus !== 5"
       class="items-section">
      <div class="section-title">物流追踪</div>

      <div v-if="detailRouteLoading">加载中...</div>

      <template v-else-if="detailRoute">

          <!-- 阶段标签：管理员可看到完整阶段标识 -->
          <el-descriptions :column="2" border size="small" style="margin-bottom:8px">
              <el-descriptions-item label="物流阶段">
                  <el-tag v-if="detailRoute.route?.segmentType === 3" type="warning">
                      🚄 跨城干线运输中
                  </el-tag>
                  <el-tag v-else-if="detailRoute.route?.segmentType === 1" type="info">
                      🏭 本地干线
                  </el-tag>
                  <el-tag v-else type="success">
                      🛵 末端配送中
                  </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="物流单号">
                  {{ detailRoute.route?.routeNo ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="起点">
                  {{ detailRoute.route?.startAddress }}
              </el-descriptions-item>
              <el-descriptions-item label="终点">
                  {{ detailRoute.orderEndAddress ?? detailRoute.route?.endAddress }}
              </el-descriptions-item>
              <el-descriptions-item v-if="detailRoute.route?.segmentType === 3"
                                    label="干线批次">
                  {{ detailRoute.route?.interCityBatchId ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item v-if="detailRoute.driverName" label="配送员">
                  {{ detailRoute.driverName }}（{{ detailRoute.driverPhone }}）
              </el-descriptions-item>
          </el-descriptions>

          <!-- 城市地图（末端/本地干线时显示，跨城干线不显示） -->
          <RouteMap
              v-if="detailRoute.route?.segmentType !== 3 && detailMapReady"
              :startLat="detailRoute.route?.startLatitude"
              :startLng="detailRoute.route?.startLongitude"
              :endLat="detailRoute.orderEndLat ?? detailRoute.route?.endLatitude"
              :endLng="detailRoute.orderEndLng ?? detailRoute.route?.endLongitude"
              :plannedRoute="detailRoute.route?.plannedRoute"
              :startLabel="detailRoute.route?.startAddress"
              :endLabel="detailRoute.orderEndAddress ?? detailRoute.route?.endAddress"
          />

      </template>
      <el-empty v-else description="暂无物流信息" :image-size="48" />
  </div>
  ```

  **脚本改动（与 `Shop/Order.vue` 类似）：**
  ```typescript
  import { getRouteByOrderId } from '@/api/logistics'
  import RouteMap from '@/components/RouteMap.vue'

  const detailRoute = ref<any>(null)
  const detailRouteLoading = ref(false)
  const detailMapReady = ref(false)

  const openDetail = async (row: any) => {
      detailVisible.value = true
      detailMapReady.value = false
      detailRoute.value = null
      // 原有详情加载逻辑 ...
      if (row.orderStatus >= 2 && row.orderStatus !== 5) {
          detailRouteLoading.value = true
          try {
              const r = await getRouteByOrderId(row.id)
              detailRoute.value = r.data
          } finally {
              detailRouteLoading.value = false
          }
      }
      // 等 dialog 动画完成后再挂载地图
      setTimeout(() => { detailMapReady.value = true }, 300)
  }
  ```

---

### 12.7 前端：`Driver/Delivery.vue` 跨城干线的显示说明

> 干线司机在 `Driver/Delivery.vue` 中看到的是 `inter_city_batch` 相关的任务，
> **不是** `logistics_route`。当前 `segmentType=3` 的虚拟路线**不分配给任何司机**，
> 仅供顾客/商户/管理员端展示用。如需干线司机端展示，参照现有 `segmentType=1` 的处理方式扩展。

---

### 12.8 路线图展示时机总结（三角色对比）

| 阶段 | order_status | segmentType | 顾客看到 | 商户看到 | 管理员看到 |
|---|---|---|---|---|---|
| 待支付/待发货 | 0 / 1 | 无 | 无 | 无 | 无 |
| 待揽件 | 2 | 无 | "待揽件提示" | "待揽件提示" | 无物流信息 |
| **跨城干线运输中** | 3 | 3（虚拟路线） | "🚄 跨城干线中" + 全国两点连线 | "🚄 跨城干线中" + 起止城市提示 | 阶段标签 + 起止地址（无地图） |
| 城市末端配送 | 3 | 2（末端路线） | RouteMap 城市地图 + GPS轨迹 | RouteMap 城市地图 | RouteMap 城市地图 |
| 已完成 | 4 | 2（已完成） | RouteMap 完成状态 | RouteMap 完成状态 | RouteMap 完成状态 |

---

## 十三、测试数据准备 🔴

### 13.1 测试数据准备 SQL

- [ ] **P0** 新建 `database/national_hub_test.sql`
  ```sql
  -- 全国枢纽（level=0）
  INSERT INTO national_hub VALUES
  (1,'武汉全国枢纽',  0,'湖北','武汉', 30.5928, 114.3055, 10000, 0, 0, NOW(), NOW()),
  (2,'郑州全国枢纽',  0,'河南','郑州', 34.7466, 113.6253, 10000, 0, 0, NOW(), NOW());

  -- 城市配送中心（level=2）
  INSERT INTO national_hub VALUES
  (3, '上海配送中心', 2,'上海','上海', 31.2304, 121.4737, 5000, 0, 0, NOW(), NOW()),
  (4, '北京配送中心', 2,'北京','北京', 39.9042, 116.4074, 5000, 0, 0, NOW(), NOW()),
  (5, '广州配送中心', 2,'广东','广州', 23.1291, 113.2644, 5000, 0, 0, NOW(), NOW()),
  (6, '成都配送中心', 2,'四川','成都', 30.5723, 104.0665, 5000, 0, 0, NOW(), NOW()),
  (7, '西安配送中心', 2,'陕西','西安', 34.3416, 108.9398, 5000, 0, 0, NOW(), NOW()),
  (8, '深圳配送中心', 2,'广东','深圳', 22.5431, 114.0579, 5000, 0, 0, NOW(), NOW()),
  (9, '南京配送中心', 2,'江苏','南京', 32.0603, 118.7969, 5000, 0, 0, NOW(), NOW());

  -- Hub 间运输边
  INSERT INTO hub_link VALUES
  (1, 3, 1, 'ROAD', 500, 3.20, 3.20, 840,  10.0, 1, NOW(), NOW()),  -- 上海→武汉
  (2, 3, 9, 'ROAD', 500, 0.90, 0.90, 290,   4.0, 1, NOW(), NOW()),  -- 上海→南京
  (3, 3, 2, 'RAIL', 300, 2.80, 2.80, 800,   6.0, 1, NOW(), NOW()),  -- 上海→郑州
  (4, 4, 1, 'ROAD', 300, 3.80, 3.80, 1100, 13.0, 1, NOW(), NOW()),  -- 北京→武汉
  (5, 4, 2, 'RAIL', 400, 2.10, 2.10,  690,  6.0, 1, NOW(), NOW()),  -- 北京→郑州
  (6, 5, 1, 'ROAD', 400, 3.50, 3.50, 1000, 12.0, 1, NOW(), NOW()),  -- 广州→武汉
  (7, 5, 8, 'ROAD', 500, 0.60, 0.60,  140,  2.0, 1, NOW(), NOW()),  -- 广州→深圳
  (8, 1, 6, 'ROAD', 400, 2.60, 2.60,  800, 10.0, 1, NOW(), NOW()),  -- 武汉→成都
  (9, 1, 7, 'ROAD', 300, 2.40, 2.40,  680,  8.0, 1, NOW(), NOW()),  -- 武汉→西安
  (10,2, 7, 'RAIL', 300, 1.80, 1.80,  500,  5.0, 1, NOW(), NOW()),  -- 郑州→西安
  (11,2, 6, 'ROAD', 250, 2.20, 2.20, 1000, 12.0, 1, NOW(), NOW()),  -- 郑州→成都
  (12,2, 9, 'RAIL', 300, 2.00, 2.00,  700,  6.0, 1, NOW(), NOW());  -- 郑州→南京

  -- 绑定现有仓库到所在城市 Hub（以上海仓库为例）
  UPDATE warehouse SET affiliated_hub_id = 3 WHERE city = '上海' OR province = '上海';
  UPDATE warehouse SET affiliated_hub_id = 4 WHERE city = '北京' OR province = '北京';
  UPDATE warehouse SET affiliated_hub_id = 5 WHERE city = '广州';
  ```

- [ ] **P0** 将 `national_hub_test.sql` 加入 `docker-compose.yml` 初始化挂载
  ```yaml
  volumes:
    - ./database/all.sql:/docker-entrypoint-initdb.d/01_all.sql
    - ./database/logistics_history_test.sql:/docker-entrypoint-initdb.d/02_history.sql
    - ./database/national_hub_test.sql:/docker-entrypoint-initdb.d/03_national_hub.sql  # 新增
  ```

---

## 十四、其他配置与部署 ⚪

---

---

# 附：MCMF 调度结果示例

> 以下是一个完整的全国物流 MCMF 调度示例，展示算法如何在真实约束下求最优解。

## 场景描述

**日期：** 2026-04-27（某日全国订单汇总）  
**总需运输：** 1500件  
**触发方式：** 每日6:00自动调度，或管理员手动触发

---

## 流网络拓扑

### 节点表（national_hub）

```
节点ID  名称            层级   城市    供给/需求
──────────────────────────────────────────────────
H01    上海城市配送中心   2    上海    +600件（供给）
H02    北京城市配送中心   2    北京    +400件（供给）
H03    广州城市配送中心   2    广州    +500件（供给）
H04    武汉全国枢纽      0    武汉     0件（纯中转）
H05    郑州全国枢纽      0    郑州     0件（纯中转）
H06    成都城市配送中心   2    成都    -400件（需求）
H07    西安城市配送中心   2    西安    -300件（需求）
H08    深圳城市配送中心   2    深圳    -400件（需求）
H09    南京城市配送中心   2    南京    -400件（需求）
```

**验证：** 总供给 = 600+400+500 = 1500，总需求 = 400+300+400+400 = 1500 ✓

---

### 边表（hub_link）

```
边ID  起点  终点  运输方式  日容量  单价(元/件)  里程(km)  时长(h)
───────────────────────────────────────────────────────────────
L01  H01  H04   ROAD     500     3.2         840      10
L02  H01  H09   ROAD     500     0.9         290       4    ← 上海→南京（近）
L03  H01  H05   RAIL     300     2.8         800       6
L04  H02  H04   ROAD     300     3.8        1100      13
L05  H02  H05   RAIL     400     2.1         690       6    ← 北京→郑州（铁路）
L06  H02  H07   ROAD     200     2.5         900      11
L07  H03  H04   ROAD     400     3.5        1000      12
L08  H03  H08   ROAD     500     0.6         140       2    ← 广州→深圳（近）
L09  H04  H06   ROAD     400     2.6         800      10
L10  H04  H07   ROAD     300     2.4         680       8
L11  H04  H08   ROAD     300     3.1        1100      14
L12  H05  H06   ROAD     250     2.2        1000      12
L13  H05  H07   RAIL     300     1.8         500       5    ← 郑州→西安（铁路）
L14  H05  H09   RAIL     300     2.0         700       6
```

---

## MCMF 算法执行过程（SSP + SPFA）

算法构建带超级源点 S、超级汇点 T 的残差网络，循环找最短费用增广路。

### 增广路记录

```
轮次  增广路径              单位费用       流量     本轮费用
────────────────────────────────────────────────────────
 1   S→H03→H08→T          0.6元/件      400件    240元
     （广州→深圳 直达，最便宜，一次满足全部深圳需求）

 2   S→H01→H09→T          0.9元/件      400件    360元
     （上海→南京 直达，次便宜，一次满足全部南京需求）

 3   S→H02→H05→H07→T      3.9元/件      300件   1170元
     （北京→郑州[铁路]→西安[铁路]，满足全部西安需求）

 4   S→H02→H05→H06→T      4.3元/件      100件    430元
     （北京剩余→郑州→成都，偏贵但北京库存必须消化）

 5   S→H01→H05→H06→T      5.0元/件      150件    750元
     （上海→郑州[铁路]→成都，ZZ→CD边剩余容量150件）

 6   S→H01→H04→H06→T      5.8元/件       50件    290元
     （上海剩余→武汉→成都，上海库存已耗尽）

 7   S→H03→H04→H06→T      6.1元/件      100件    610元
     （广州剩余→武汉→成都，满足成都最后100件需求）
```

### 验证各节点流量守恒

```
H01（上海）  出流 = 400(L02) + 150(L03) + 50(L01) = 600 = 供给 ✓
H02（北京）  出流 = 400(L05) = 400 = 供给 ✓（剩余容量由ZZ两条路分）
H03（广州）  出流 = 400(L08) + 100(L07) = 500 = 供给 ✓
H04（武汉）  入流 = 50(L01) + 100(L07) = 150；出流 = 150(L09) 守恒 ✓
H05（郑州）  入流 = 400(L05) + 150(L03) = 550；出流 = 300(L13)+250(L12) = 550 守恒 ✓
H06（成都）  入流 = 100+150+50+100 = 400 = 需求 ✓
H07（西安）  入流 = 300(L13) = 300 = 需求 ✓
H08（深圳）  入流 = 400(L08) = 400 = 需求 ✓
H09（南京）  入流 = 400(L02) = 400 = 需求 ✓
```

---

## 最终调度结果（FlowPlan + FlowPlanItem）

### flow_plan 记录

```json
{
  "id": 1001,
  "planDate": "2026-04-27",
  "totalDemand": 1500,
  "totalCost": 3850.00,
  "status": "DONE",
  "algorithm": "MCMF_SSP_SPFA",
  "createdTime": "2026-04-27T06:00:03"
}
```

### flow_plan_item 明细

```
item_id  from      to      mode   流量(件)  单价    小计
───────────────────────────────────────────────────────
  1      广州      深圳    ROAD    400      0.60   240.00元
  2      上海      南京    ROAD    400      0.90   360.00元
  3      北京      郑州    RAIL    400      2.10   840.00元
  4      郑州      西安    RAIL    300      1.80   540.00元
  5      郑州      成都    ROAD    250      2.20   550.00元
  6      上海      郑州    RAIL    150      2.80   420.00元
  7      上海      武汉    ROAD     50      3.20   160.00元
  8      武汉      成都    ROAD    150      2.60   390.00元
  9      广州      武汉    ROAD    100      3.50   350.00元

                            合计  1500件         3850.00元
```

> **注意：** item 3和4 是连续路径（北京→郑州→西安），在后续生成 inter_city_batch 时
> 对应两段独立批次：「北京→郑州批次」和「郑州→西安批次」，各自由不同司机/货运负责。

---

## 与非优化方案的对比

### 非优化方案（就近贪心）

```
路径                    流量    单价    小计
广州→武汉→成都          400    6.10   2440元  ← 没利用广州→深圳近距优势
上海→武汉→西安          300    5.60   1680元  ← 绕远
北京→郑州→南京          400    4.10   1640元
广州→武汉→深圳          100    6.60    660元
上海→南京               300    0.90    270元

非优化总费用：6690元
```

### 效果对比

```
MCMF 优化总费用：  3850元
非优化贪心费用：   6690元
节省费用：         2840元（节省 42.4%）
```

---

## 由此生成的干线批次（inter_city_batch）

```
batch_id  起点  终点  模式  件数  计划发车时间         状态
────────────────────────────────────────────────────────
IB-001   广州  深圳  ROAD   400  2026-04-27 08:00    待发车
IB-002   上海  南京  ROAD   400  2026-04-27 08:00    待发车
IB-003   北京  郑州  RAIL   400  2026-04-27 09:00    待发车
IB-004   郑州  西安  RAIL   300  2026-04-27 15:00    待发车（等IB-003到达）
IB-005   郑州  成都  ROAD   250  2026-04-27 15:00    待发车（等IB-003到达）
IB-006   上海  郑州  RAIL   150  2026-04-27 08:00    待发车
IB-007   上海  武汉  ROAD    50  2026-04-27 08:00    待发车
IB-008   武汉  成都  ROAD   150  2026-04-27 18:00    待发车（等IB-007到达）
IB-009   广州  武汉  ROAD   100  2026-04-27 08:00    待发车
```

### 批次到达后触发末端

```
IB-001 到达深圳 → 触发 LogisticsBatchService.activateFromInterCityArrival()
                 → 深圳城市配送中心执行 VRP 规划
                 → 生成 N 条末端 logistics_route（Hub→各客户）
                 → 分配给深圳本地司机配送
```

---

## 答辩时的一句话总结

> "系统将全国物流网络建模为容量有向图，以各城市配送中心为节点、运输链路为边，
> 用 **SSP（逐次最短路）+ SPFA** 算法求解最小费用最大流，
> 在本示例中对比贪心方案节省了 **42% 的运输成本**，
> 末端派送则沿用原有 **VRP + Hub-and-Spoke** 架构，
> 形成『干线流量全局优化（MCMF）+ 末端路径局部优化（VRP）』的两级调度体系。"
