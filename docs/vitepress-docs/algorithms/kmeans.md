---
title: K-Means 聚类算法
---

# 🎯 K-Means：末端配送聚类算法

## 什么是 K-Means？

**K-Means = K均值聚类算法**

想象你是一个配送站长，手上有 100 个待配送订单，分散在城市各个地方。你有 5 个运输员空闲。你要决定：

1. **怎么把 100 个订单分成 5 组？**
2. **每组分配给一个运输员，每组的订单要尽量靠近（减少配送路程）？**

K-Means 帮你把地理上相近的订单聚成一簇，每簇分配给一个运输员！

## 🎬 算法流程可视化

<script setup>
import { ref } from 'vue'

const currentStep = ref(0)
const steps = [
  { title: '初始化', desc: '均匀抽样选择 K 个初始中心' },
  { title: 'E-Step', desc: '每个订单分配到最近的簇中心' },
  { title: 'M-Step', desc: '重新计算每个簇的中心（均值）' },
  { title: '收敛判断', desc: '检查是否有订单改变簇归属' },
  { title: '输出结果', desc: '返回 K 个簇及成员列表' }
]

function nextStep() {
  if (currentStep.value < steps.length - 1) {
    currentStep.value++
  }
}
</script>

<div style="background: #f5f7fa; padding: 1.5rem; border-radius: 8px; margin: 1rem 0;">
  <h4>算法执行过程：</h4>
  
  <div style="display: flex; justify-content: space-between; margin: 1rem 0;">
    <div 
      v-for="(step, idx) in steps" 
      :key="idx"
      :style="{
        padding: '0.5rem 1rem',
        background: idx === currentStep ? '#3498db' : (idx < currentStep ? '#2ecc71' : '#e0e0e0'),
        color: idx <= currentStep ? 'white' : '#666',
        borderRadius: '20px',
        fontSize: '0.9rem',
        transition: 'all 0.3s ease'
      }"
    >{{ step.title }}</div>
  </div>
  
  <div style="text-align: center; padding: 1rem; background: white; border-radius: 8px;">
    <div style="font-size: 1.2rem; font-weight: bold;">{{ steps[currentStep].title }}</div>
    <div style="color: #666; margin-top: 0.5rem;">{{ steps[currentStep].desc }}</div>
  </div>
  
  <div style="text-align: center; margin-top: 1rem;">
    <button 
      @click="nextStep"
      :disabled="currentStep === steps.length - 1"
      style="padding: 0.5rem 2rem; background: #3498db; color: white; border: none; border-radius: 4px; cursor: pointer;"
    >下一步 ➡️</button>
  </div>
</div>

## 📊 算法详解

### 1️⃣ 确定聚类数 K

```java
// 自动确定 K 值
if (k == null) {
    k = (int) Math.ceil(Math.sqrt(n));  // K = √(订单数)
}
k = Math.min(k, n);  // K 不能超过订单数
```

### 2️⃣ 均匀抽样初始化

**不是随机选！** 保证初始中心在数据分布上均匀覆盖：

```java
// 均匀抽取 K 个作为初始中心
for (int i = 0; i < k; i++) {
    int idx = (int) Math.round(i * (n - 1) / (k - 1.0));
    centers.add(items.get(idx));
}
```

### 3️⃣ 迭代（最多 100 次）

```
repeat until convergence or maxIterations:
    # E-step: 分配
    for each order:
        clusterId = argmin_i Haversine(order, center[i])
    
    # M-step: 更新
    for each cluster:
        center.lat = mean(all orders' lat in cluster)
        center.lng = mean(all orders' lng in cluster)
        
    # 收敛判断
    if no order changed cluster:
        break
```

### 4️⃣ Haversine 距离

```java
// 地球半径 6371 km
// 标准球面距离公式
d = 2R × arcsin(√(sin²((lat2-lat1)/2) + cos(lat1)×cos(lat2)×sin²((lon2-lon1)/2)))
```

> **注意**：簇中心重计算用的是经纬度算术平均，在小区域内误差可接受（城市级别）。

## 📊 输出结果

```java
// 返回类型：List<ClusterResultDTO>
public class ClusterResultDTO {
    private int clusterId;           // 簇 ID
    private double centerLat;        // 簇中心纬度
    private double centerLng;        // 簇中心经度
    private List<DispatchPoolDTO> members;  // 成员订单列表
    private int memberCount;         // 成员数量
}
```

## 🎯 实际应用场景

```
场景：上海浦东新区今天有 80 个待配送订单，5 个运输员空闲

K-Means 聚类结果：
  簇 0 (中心：31.2°N, 121.5°E): 18 个订单 → 分配给 张三
  簇 1 (中心：31.3°N, 121.6°E): 15 个订单 → 分配给 李四
  簇 2 (中心：31.1°N, 121.4°E): 22 个订单 → 分配给 王五
  簇 3 (中心：31.2°N, 121.7°E): 12 个订单 → 分配给 赵六
  簇 4 (中心：31.0°N, 121.5°E): 13 个订单 → 分配给 孙七

优势：
  1. 每个运输员的订单地理上集中，减少空驶路程
  2. 自动平衡各运输员的工作量
  3. 可以结合路径规划进一步优化
```

## 🧪 代码示例

```java
// 文件：KMeansClusterServiceImpl.java（110 行）

@Service
public class KMeansClusterServiceImpl implements ClusterService {
    
    @Override
    public List<ClusterResultDTO> cluster(List<DispatchPoolDTO> items, Integer k) {
        int n = items.size();
        if (k == null) k = (int) Math.ceil(Math.sqrt(n));
        k = Math.min(k, n);
        
        // 1. 均匀抽样初始化
        List<Coordinate> centers = initCenters(items, k);
        
        // 2. 迭代
        Map<Integer, List<DispatchPoolDTO>> clusters;
        int iter = 0;
        do {
            // E-step
            clusters = assignClusters(items, centers);
            
            // M-step
            centers = recomputeCenters(clusters);
            
            iter++;
        } while (!converged() && iter < MAX_ITER);
        
        // 3. 输出结果
        return buildResult(clusters, centers);
    }
}
```

## 📈 算法复杂度

| 部分 | 复杂度 | 说明 |
|------|--------|------|
| E-step | O(K × N) | 每个订单计算 K 个距离 |
| M-step | O(N) | 遍历所有订单重算中心 |
| 总复杂度 | O(I × K × N) | I 为迭代次数 |

**实际性能**：城市级别 1000 个订单，K=30，通常 10 次内收敛，耗时 < 100ms。

## 🎯 下一步

- 想了解路径规划？前往 [A* 算法](/algorithms/astar)
- 想了解干线调度？前往 [MCMF 算法](/algorithms/mcmf)
