---
title: MCMF 最小费用最大流算法
---

# 🧠 MCMF：最小费用最大流算法

## 什么是 MCMF？

**MCMF = Minimum Cost Maximum Flow（最小费用最大流）**

想象你是一个物流调度员，有 10 个 Hub，今天每个 Hub 都有一堆包裹要发往其他 Hub。你要决定：

1. **每条线路发多少货？**（流量）
2. **怎么发最省钱？**（费用）

这就是一个**网络流问题**！

## 🎯 实际问题建模

```
问题：有 N 个 Hub，每个 Hub 有：
  - 供给量（要发出的包裹量）
  - 需求量（要接收的包裹量）
  - Hub 之间的线路有容量限制和运费

目标：在容量限制下，用最小总费用运输最多货物
```

### 建图过程

<div style="background: #f5f7fa; padding: 1.5rem; border-radius: 8px; margin: 1rem 0;">

#### 1️⃣ 拆点：每个 Hub 拆成两个节点

```
Hub(i) → 入港节点 in(i) 和 出港节点 out(i)
  - out(i) = 2*i
  - in(i) = 2*i + 1
  - 超级源 S = 2*hCount
  - 超级汇 T = 2*hCount + 1
```

#### 2️⃣ 建边

| 边类型 | 方向 | 容量 | 费用 |
|--------|------|------|------|
| Hub内换载 | in(i) → out(i) | max(总供给, 总需求) | 0 |
| 实际运输 | out(i) → in(j) | link.capacityDaily | costPerUnit × 100 |
| 超级源→Hub | S → out(i) | 该Hub供给量 | 0 |
| Hub→超级汇 | in(i) → T | 该Hub需求量 | 0 |
| 反向边 | 各正向边的反向 | 0 | -cost |

#### 3️⃣ SSP 主循环（逐次最短路径）

```java
while (SPFA 找到 S→T 的最短费用路径) {
    沿路径增广（推送瓶颈流量）
    更新残差网络
}

// 收集结果
遍历原始边，flow = 原始容量 - 残余容量
```

</div>

## 💡 为什么费用乘 100？

避免浮点误差！所有费用取整后用 `long[]` 存储，最终结果除以 100 还原为 `BigDecimal`。

## 🔍 单商品 vs 多商品模式

### 单商品模式

- **算法**：SSP + SPFA
- **特点**：所有 OD 对的供需在同一个网络中处理
- **缺陷**：可能出现"同一 Hub 供给与需求代数抵消"的建模缺陷

### 多商品模式

- **算法**：Dijkstra-SSP
- **特点**：每个 OD 对（Origin-Destination）有独立的供需，不会出现抵消问题
- **实现**：

```
1. 构建残差图：costAdj（费用邻接表）+ resCap（剩余容量，跨商品共享）

2. 按 OD 需求量降序处理（高需求优先占容量）：
   for each OD pair:
     while (该 OD 还有剩余需求):
       dijkstraOnResidual(originHub, destHub)  // 找最短费用路径
       bottleneck = 路径上最小残差
       增广 min(bottleneck, remaining) 单位流量
       更新共享残差容量

3. 汇总各边流量
```

## 📊 算法复杂度

| 模式 | 时间复杂度 | 适用场景 |
|------|-----------|----------|
| 单商品 SSP+SPFA | O(F × V × E) | 小规模网络 |
| 多商品 Dijkstra-SSP | O(OD × (V+E)logV) | 大规模网络 |

## 🎬 执行流程详解

```
flowPlanService.triggerManually(date, llmRef)
 │
 ├── Step 1: 收集各 Hub 的供给量/需求量
 │     → 遍历 dispatch_pool，按 originHubId/destHubId 汇总
 │
 ├── Step 2: LLM 费率校准（可选）
 │     → LlmEdgeCostCalibrationServiceImpl.calibrate()
 │     → 调用 DeepSeek API，传入各 HubLink 的基本信息
 │     → 返回校准后的 costPerUnit
 │
     ├── Step 3: 执行 MCMF 算法
     │     → 单商品模式: McmfServiceImpl.computeMinCostFlow()
     │     → 多商品模式: McmfServiceImpl.computeMultiCommodityFlow()
     │
     ├── Step 4: 保存规划结果
     │     → 写 flow_plan 表
     │     → 写 flow_plan_edge 表（各边流量）
     │
     └── Step 5: 创建运输批次
           → 遍历有流量的边，创建 InterCityBatch
           → 对每条边上的订单：
             → 创建 HubOutboundRecord（出库记录）
             → 更新 HubSortingRecord.sortResult = ASSIGNED
             → 写入目标端 dispatch_pool（dispatchOriginType=1，跨城到达待调度）
```

## 🧪 代码示例

```java
// 文件：McmfServiceImpl.java（456 行）

// 单商品模式核心代码
public MinCostFlowResult computeMinCostFlow(
        List<HubSupplyDemandDTO> supplyList,
        List<HubSupplyDemandDTO> demandList,
        List<HubLinkDTO> links) {
    
    int hCount = supplyList.size();
    int N = 2 * hCount + 2;  // 拆点 + S + T
    
    // 1. 建图
    long[][] cap = new long[N][N];
    long[][] cost = new long[N][N];
    
    // 2. SSP 主循环
    long totalFlow = 0;
    long totalCost = 0;
    
    while (true) {
        // SPFA 找最短增广路
        long[] dist = spfa(N, S, cap, cost);
        if (dist[T] == INF) break;  // 无增广路
        
        // 沿路径增广
        long aug = findBottleneck(S, T, cap, parent);
        augmentFlow(S, T, aug, cap, parent);
        
        totalFlow += aug;
        totalCost += aug * dist[T];
    }
    
    return new MinCostFlowResult(totalFlow, totalCost, edges);
}
```

## 🎯 实际效果

假设有 5 个 Hub，今天的调度需求：

| 从/到 | Hub A | Hub B | Hub C | Hub D | Hub E |
|-------|-------|-------|-------|-------|-------|
| **Hub A** | - | 50 单 | 30 单 | - | - |
| **Hub B** | - | - | - | 80 单 | - |
| **Hub C** | - | - | - | 20 单 | 10 单 |
| **Hub D** | - | - | - | - | 100 单 |
| **Hub E** | - | - | - | - | - |

MCMF 会自动计算：
- 每条线路分配多少流量
- 总运输费用最小是多少
- 是否满足所有供需

## 📚 参考资料

- [网络流算法详解](https://en.wikipedia.org/wiki/Minimum-cost_flow_problem)
- [SPFA 算法](https://en.wikipedia.org/wiki/Shortest_Path_Faster_Algorithm)
- [Dijkstra 算法](https://en.wikipedia.org/wiki/Dijkstra%27s_algorithm)

## 🎯 下一步

- 想了解末端配送怎么聚类？前往 [K-Means 算法](/algorithms/kmeans)
- 想了解路径规划？前往 [A* 算法](/algorithms/astar)
