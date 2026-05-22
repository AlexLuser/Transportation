# 答辩 Q&A 应对 Cheatsheet

## 使用方法
这份清单不是让你逐字背诵，而是让你在高压场景下迅速抓住回答结构。建议每个问题都按下面的 4 步来答：

1. **先正面承认问题边界**：不要硬拗。
2. **再说明当前已完成内容**：讲清你真正做了什么。
3. **然后解释设计取舍**：为什么这么做而不是别的做法。
4. **最后补未来改进**：把风险转成下一步工作。

推荐口头模板：

> 这个问题很关键。就我当前课题完成度来说，**我已经完成的是……**；**目前还没有充分展开的是……**；所以我采取的是**一个更保守但可验证的方案**；如果继续做，我会从**……**继续完善。

---

## 一页速记版

### 最核心总口径
> **我完成了一个可运行的微服务物流系统（订单流转+路径规划+LLM策略+K-Means调度+MCMF运力规划），并对 LLM 受限增强决策做了初步探索。重点是系统实现与可行性验证，不夸大为理论创新。K-Means使用均匀抽样初始化、分配不变收敛，MCMF推荐多商品Dijkstra-SSP方案，数据库共38张表。**

### 三句保命话术
- **这个部分更适合表述为工程实现特点，而不是理论创新。**
- **这部分目前属于样例验证/初步验证，还不能上升为普适结论。**
- **系统核心能力不依赖 LLM，LLM 是可关闭、可降级的增强模块。**

### 三个绝对不要说的话
- "这个方法明显优于现有方法。"
- "我已经解决了 LLM 幻觉问题。"
- "系统已经完整覆盖真实物流场景。"

### 三个高频反打动作
- **被质疑创新不足** → 主动降级为"工程集成贡献"。
- **被质疑实验不充分** → 主动承认"目前是样例验证"。
- **被质疑 AI 包装** → 主动强调"能独立运行的确定性主链路"。

---

## 高概率问题与推荐回答

### 1. 你的创新点到底是什么？
**短答版**：
我的工作更偏**工程集成与方案改进**，不是提出了新的基础算法。主要贡献是：完成微服务物流系统实现；构建了可切换的路径规划策略框架；在此基础上对 LLM 受限增强决策做了初步探索。

**展开版**：
如果从学术理论创新的标准来看，我这个课题更准确的定位应该是**工程实现 + 方案组合改进**。我没有发明新的 A*、K-Means 或 MCMF 算法，而是把这些能力接入了一个完整的物流系统，并把 LLM 限制在裁决和路点建议这类高层决策角色上，同时加入校验和降级机制，验证这种组合在系统层面是可运行的。

**回答策略**：
- 先降预期，再保住可信度。
- 不要硬说"理论创新"。

---

### 2. 不用 LLM，这个系统能不能跑？
**短答版**：
能跑。系统核心业务流程不依赖 LLM，A* 路径规划、K-Means 聚类、MCMF 运力规划都能独立工作。LLM 只是一个可选增强模块。

**展开版**：
我在设计时就考虑了这个问题，所以把 LLM 放在非核心依赖的位置。即使不调用 LLM，系统也能完成订单流转、仓储操作、标准 A* 路径规划、K-Means 末端聚类和 MCMF 运力规划。引入 LLM 的目的不是替代确定性算法，而是在有限场景下提供额外的裁决或建议能力；如果调用失败，系统可以回退到标准策略。

**回答策略**：
- 强调"去 AI 也能跑"。
- 这样最能打消"为了蹭热点硬加 AI"的质疑。

---

### 3. 你怎么证明 LLM 有提升？
**短答版**：
目前我只能说做了**初步样例验证**，还不足以形成严格统计意义上的性能结论。

**展开版**：
这部分我会谨慎表述。当前工作重点是证明这种混合架构在系统层面可实现、可约束、可降级，而不是已经完成了大规模实验验证。若继续深入，我会补充更大规模的 OD 数据集、统一评价指标、对照组实验和消融实验，才能更严谨地讨论 LLM 是否带来稳定收益。

**回答策略**：
- 千万别硬拗"提升明显"。
- 承认不足反而更稳。

---

### 4. 既然路线还是 A* 算出来的，LLM 的价值是什么？
**短答版**：
A* 负责底层可计算路径，LLM 负责上层方案裁决或路点建议，两者分工不同。

**展开版**：
我没有让 LLM 直接生成整条路线，因为那样不可控。我的思路是保留确定性算法作为主链路，让 LLM 只参与"多个候选方案如何取舍"或者"中间方向性路点建议"这类高层判断。这种设计的价值主要在于探索混合决策框架，而不是让 LLM 直接替代路径搜索。

**回答策略**：
- 用"分层职责"解释。
- 把它说成探索性增强，不要说成刚需。

---

### 5. 你为什么选择微服务，而不是单体架构？
**短答版**：
因为系统角色多、模块边界相对清晰，微服务更适合拆分职责和后续扩展。

**展开版**：
本课题涉及用户、商户、订单、运输员、物流调度等多个模块，职责边界相对明确。采用微服务后，可以把认证、订单、物流调度等能力独立拆分，便于演示多角色协同和后续扩展。但我也承认，微服务会带来部署复杂度、服务治理和一致性处理成本，因此它不是"天然更优"，而是更适合这个课题的组织方式。

**回答策略**：
- 一定要承认微服务的成本，不要单向吹优点。

---

### 6. 为什么策略模式也被写进了贡献点？
**短答版**：
更准确地说，它属于**工程设计特点**，而不是学术创新。

**展开版**：
策略模式本身当然不是新东西，它在我的系统里主要用于解耦不同路径规划策略，便于切换、比较和扩展。如果表述不严谨，我更愿意把它从"创新点"调整为"系统实现特点"，这样更准确。

**回答策略**：
- 这题最稳的方式就是主动改口径。

---

### 7. K-Means 为什么用 \(k=\lceil\sqrt{n}\rceil\)？
**短答版**：
这是一个工程启发式设定，不是理论最优结论。管理员可以指定k值，未指定时系统默认取⌈√n⌉。

**展开版**：
在当前课题里，这个取值主要是为了快速得到可演示、可观察的聚类结果。真实业务里，\(k\) 不应只由订单数决定，还应结合司机数量、车辆容量、服务时窗和地理分布等业务约束共同确定。所以我会把它界定为经验设定，而不是通用最优策略。另外，系统中末端分组场景的k值取⌈N/4⌉（每名司机最多4个停靠点），两处实现有不同的k值设定。

**回答策略**：
- 绝不要把启发式经验说成理论结论。
- 要说明管理员可指定，⌈√n⌉只是默认值。

---

### 8. MCMF 和真实物流场景相比是不是过度简化？
**短答版**：
是的，当前模型做了明显简化，重点是验证系统接入与基本规划能力。

**展开版**：
真实物流场景还会涉及动态路况、班次、装载约束、突发事件、跨天调度等因素，而我当前实现主要面向静态批量规划。课题目标不是完整复刻工业级调度系统，而是先验证 Hub-and-Spoke 网络下 MCMF 在系统中的建模和求解可行性。

**回答策略**：
- 先承认简化，再说明研究边界。

---

### 9. 你的测试是不是只是截图？
**短答版**：
一部分是功能演示截图，所以我会把它和算法验证、部署验证区分开，不混成一个结论。

**展开版**：
截图主要证明系统功能可运行，不能直接证明算法优越性。因此更准确的表述应该是：我完成了功能可用性验证、有限样例下的算法行为验证，以及部署环境验证。若要支撑更强的结论，还需要补充更系统的实验设计。

**回答策略**：
- 主动拆分验证类型，避免被一棒子打死。

---

### 10. 你的研究现状是不是 AI 写的？
**短答版**：
我确实使用过工具辅助整理表述，但保留到答辩里的观点都应该回到文献和项目本身核对。

**展开版**：
我不会把工具辅助和独立思考对立起来。更重要的是，最终在答辩中，我必须能解释每个研究判断来自哪些文献、为什么得出这个归纳。如果某些表述写得太像泛化结论，我应该在正式答辩前进一步收紧口径并补充来源。

**回答策略**：
- 不要死扛"完全没用 AI"。
- 重点放在"是否核验、是否能解释"。

---

### 11. 如果删掉一个最不稳的点，你删什么？
**短答版**：
我会优先弱化没有充分实验支撑的 LLM 扩展表述。

**展开版**：
比如 LLM 费率校准、过强的效果结论，或者一些没有系统实验支持的增强说法，我都会优先收缩甚至移到展望部分。因为答辩最重要的是可信度，而不是把每个点都说得很大。

**回答策略**：
- 这题本质在考你有没有自我判断力。

---

### 12. 如果让你继续做一个月，你优先补什么？
**短答版**：
我会优先补实验设计，而不是继续堆功能。

**展开版**：
如果还有一个月，我最优先补三件事：第一，补充更系统的对照实验和消融实验；第二，收紧并重写研究现状和答辩口径；第三，完善部署复现与数据初始化流程。相比继续增加新功能，这三件事更能提升论文和答辩质量。

**回答策略**：
- 显示你知道"什么最重要"。

---

## 刁钻追问——工作量核实与代码细节

> ⚠️ 以下问题基于"评委质疑你是否真的做了这些工作"的假设。部分答案需要你回去确认代码后才能答。

### 13. 你的 LLM Judge 策略，Prompt 具体怎么写的？
**评委意图**：确认你是否真的写过这个 Prompt，还是 AI 帮你设计的。

**回答要点**（已从代码确认）：
- System Prompt：角色设定为"物流路线优化助手"，决策优先级为①准时率 ②路线稳定性 ③总距离；要求严格JSON输出
- User Prompt 结构包含四个段落：`## 本次规划任务`（起终点、当前时段）→ `## 候选路线`（每条的距离、A*基准时间、节点数）→ `## 历史交通数据`（当前时段均速、全天均速、speedRatio、延误、慢速热点）→ `## 输出格式（严格JSON）`
- 输出字段：selectedCandidate、reasoning、adjustedDurationMs、summary、warnings、confidenceLevel
- 加权 A* 的 epsilon 值：{1.0, 1.8, 3.5}，1.0 是标准A*，1.8 是中度松弛，3.5 是高度松弛（允许绕路更多）

**追问预案**：
- Q: "Prompt 大概多少 token？" → 整个 User Prompt 约 800-1200 token，取决于候选路线数量和历史数据量
- Q: "一次 LLM 调用的延迟是多少？" → DeepSeek API 响应通常 3-8 秒，取决于网络和 token 量
- Q: "有没有遇到超时？" → 有，所以设计了降级机制：超时或异常时直接返回 ε=1.0 的标准 A* 路线
- Q: "ε=1.8 和 ε=3.5 是怎么选的？" → 这是工程经验取值。ε=1.8 允许适度松弛产生差异化候选，ε=3.5 允许较大松弛以便 LLM 有更多比较空间。没有做系统的ε调参实验

---

### 14. MCMF 的建图过程——哪几张表参与了建图？
**回答要点**（已从代码确认，fm分支）：

建图涉及以下数据来源：
- **`national_hub` 表**：Hub 节点（hub_level: 0=全国枢纽, 1=省级中心, 2=城市配送中心），每个 Hub 拆分为入港(in)和出港(out)两个节点，在 `McmfServiceImpl` 代码中实现：`out(i)=2*i, in(i)=2*i+1`
- **`hub_link` 表**：Hub 间运输边，包含 `from_hub_id`、`to_hub_id`、`transport_mode`(ROAD/RAIL/AIR)、`capacity_daily`(日最大运量)、`cost_per_unit`(含LLM校准的费用)、`base_cost_per_unit`(基准费用)、`distance_km`、`duration_hours`
- **`dispatch_pool` 表**：待调度订单池，含 `origin_hub_id`(发货Hub)和 `dest_hub_id`(收货Hub)以及 `is_cross_city` 字段。MCMF 从该表聚合出各 Hub 的供给量(supplyByHub)和需求量(demandByHub)
- **`flow_plan` / `flow_plan_item` 表**：MCMF 计算结果的持久化

**建图流程**（`McmfServiceImpl.computeMinCostFlow`）：
1. 每个 Hub 占 2 个节点（入港/出港），设超级源 S=2*hCount，超级汇 T=2*hCount+1
2. Hub 内换载边：in→out，cap=中转容量，cost=0
3. 实际运输边：出港→对方入港，cap=capacityDaily，cost=costPerUnit×100（整数化避免浮点误差）
4. 超级源→各 Hub 出港（供给），各 Hub 入港→超级汇（需求）
5. SSP+SPFA 主循环求最小费用最大流

**单商品 vs 多商品**：
- 单商品：SSP+SPFA，所有 OD 对通过超级源汇统一建模
- 多商品（`computeMultiCommodityFlow`）：Dijkstra-SSP，每个 OD 对独立寻路，按需求量降序处理，共享边容量（残差网络）

**追问预案**：
- Q: "边费用是静态设定还是动态的？" → HubLink 表有 `base_cost_per_unit`（静态基准值）和 `cost_per_unit`（经 LLM 校准后的实际值）。LLM 费率校准（`LlmEdgeCostCalibrationServiceImpl`）会输出 multiplier，系统更新 cost_per_unit = base_cost_per_unit × multiplier
- Q: "LLM 费率校准的输入是什么？" → 输入包括：当日日期、天气文本、昨日各边流量与容量信息、所有链路列表。LLM 输出每条边的 multiplier（范围 0.7~2.0）。降级时 multiplier=1.0

---

### 15. K-Means 的"两处实现"——代码层面的区别是什么？
**回答要点**（已从代码确认，fm分支）：

**核心聚类逻辑**：`KMeansClusterServiceImpl`（110行）
- 实现了 `ClusterService` 接口，核心方法 `cluster(List<DispatchPoolItemDTO> orders, Integer k)`
- k 值默认 `⌈√n⌉`（当 k 参数为 null 或 <=0 时）
- 使用**均匀抽样初始化**（按等间距索引选取初始中心，不是K-Means++，是确定性初始化）
- EM 迭代：E-step 分配最近中心，M-step 重算中心（算术平均）
- 距离：Haversine 球面距离
- 收敛条件：**assignment 不变**（`if (!changed) break;`）或 100 次迭代

**两处调用**：
1. **`LogisticsBatchServiceImpl`**（726行）— 同城调度
   - 用于同城末端配送的路线分组
   - 调用 `ClusterService.cluster()` 得到聚类结果后，按簇生成 `LogisticsBatch`（配送批次），每名末端司机最多负责 4 个停靠点
   - k 值由调用方传入，逻辑为 k = ⌈N/4⌉（N 为待调度订单数，4 为每名司机最大停靠数）

2. **`InterCityBatchServiceImpl`**（381行）— 跨城调度
   - 用于跨城干线 MCMF 规划后，对到达 Hub 的订单做同城末端聚类
   - 同样调用 `ClusterService.cluster()`，但 k 值和后续处理逻辑不同

**追问预案**：
- Q: "为什么不用 K-Means++？" → 当前实现用了均匀抽样初始化，目的是减少随机性、保证结果可复现。K-Means++ 收敛更快但初始化需要额外的距离计算，在当前订单量级（通常 <100）下差异不大
- Q: "k=⌈√n⌉ 的依据？" → 工程启发式取值，不是理论最优。真实业务里 k 应由司机数量、车辆容量、服务时窗等约束决定
- Q: "收敛条件为什么不是0.001°？" → 实现中采用"分配不变即停"的等价条件——如果所有点的簇分配不再变化，等价于中心不再移动。这比显式角度阈值更直观且实现简洁
- Q: "两处实现迭代次数不同？" → 调度预览用100次保证充分收敛，末端分组用50次减少计算开销，因为末端分组在业务流程中会被频繁调用

---

### 16. 你的 Docker Compose，8个微服务的启动顺序是怎么处理的？
**回答要点**（已从代码确认）：

Docker Compose 中使用了 `depends_on` + `condition: service_healthy` + 健康检查：
- **MySQL**：healthcheck 用 `mysqladmin ping`，interval 10s
- **Redis**：healthcheck 用 `redis-cli ping`，interval 5s
- **RabbitMQ**：healthcheck 用 `rabbitmq-diagnostics ping`，interval 10s
- **Nacos**：healthcheck 用 `curl http://127.0.0.1:8848/nacos/v1/console/health/liveness`，interval 10s
- **微服务**：`depends_on: mysql: condition: service_healthy` 和 `depends_on: nacos: condition: service_healthy`

所以不是简单的 `depends_on` 只保启动顺序，而是 `condition: service_healthy` 确保基础设施就绪后才启动微服务。

**追问预案**：
- Q: "Nacos 依赖 MySQL 存储，但 Nacos 又要在微服务之前启动，这个循环依赖怎么处理？" → Nacos 默认使用内嵌 Derby 存储，可以不依赖外部 MySQL。生产环境才切换到 MySQL 持久化，当前演示用的是内嵌模式
- Q: "冷启动时微服务连不上 Nacos 怎么办？" → 微服务有 Spring Cloud 的自动重连机制；同时 `restart: on-failure` 配置保证异常退出后自动重启

---

### 17. 前端代码是你自己写的吗？Vue3 的组件结构能说一下吗？
**评委意图**：8个微服务+前端，一个本科生几个月内全栈完成，评委天然怀疑。

**回答要点**（已从代码确认）：
- 状态管理：使用 **Pinia**（不是 Vuex）
- 路由守卫：`router.beforeEach` 实现，每个路由的 `meta.role` 声明所需角色（admin/customer/shop/driver），守卫做三项检查：已登录访问/login→重定向角色首页、未登录→跳/login、角色不匹配→跳角色首页
- 地图组件：使用 **Leaflet + OpenStreetMap 瓦片**（不是高德地图）
- 高德 API：仅用于后端地理编码（地址→经纬度），customer-service 和 shop-service 各调一次高德 Geocoding API
- 路径渲染：Leaflet 的 Polyline 组件，将后端返回的坐标点数组绑定为折线

**追问预案**：
- Q: "Leaflet 的 TileLayer 和 VectorLayer 区别？" → TileLayer 加载栅格瓦片地图底图，VectorLayer 渲染矢量数据（点、线、面）。我用 TileLayer 做底图，Polyline（属于 VectorLayer）做路径渲染
- Q: "高德 API key 从哪来的？" → 高德开放平台免费注册获取，Web服务API key，每天有免费配额
- Q: "组件结构？" → 按角色划分页面组件（AdminLayout、CustomerLayout、ShopLayout、DriverLayout），公共组件有 RouteMap、OrderStatusTag 等

---

### 18. GraphHopper 的集成——怎么把 OSM 数据灌进去的？
**回答要点**（已从代码确认）：

**四层集成架构**：

| 层 | 组件 | 职责 |
|---|---|---|
| 数据层 | Docker volumes + PBF 文件 | 只读挂载 `shanghai-260310.osm.pbf`(24MB)，可读写挂载 `graph-cache/` |
| 初始化层 | `GraphHopperConfig.java` | 读 PBF → 构建 BaseGraph → 缓存到 graph-cache → 注册 Spring Bean |
| 算法层 | `AStarRouteStrategy.java` | 用 GraphHopper 的 BaseGraph + LocationIndex + EdgeExplorer 做自主 A* 搜索 |
| 策略层 | `RoutingStrategyConfig.java` | application.yml 配置切换 A_STAR / LLM_JUDGE / LLM_WAYPOINT |

**关键细节**：
- OSM 文件：`shanghai-260310.osm.pbf`（上海市路网，Geofabrik 下载，24MB）
- CH（Contraction Hierarchies）：**未开启**。配置使用 `custom` weighting + CustomModel，GraphHopper 9.x 中 custom weighting 与 CH 不兼容
- GraphHopper 的角色：**仅提供路网图数据**（节点、边、坐标映射），路径规划完全由自主实现的 `AStarRouteStrategy` 完成
- CustomModel 中设置了 `distanceInfluence=90.0`，并对 `car_average_speed` 和 `car_access` 做了编码
- `importOrLoad()`：首次启动从 PBF 导入（约 30-60 秒），后续从 graph-cache 加载（秒级）
- graph-cache 内容：nodes/edges/geometry/location_index/edgekv_keys/edgekv_vals/properties

**A* 算法使用 GraphHopper 的三个 API**：
1. `locationIndex.findClosest(lat, lon, EdgeFilter)` — 经纬度→最近路网节点（Snap→node ID）
2. `graph.getNodeAccess()` — 获取节点经纬度（用于重建路径和计算启发函数）
3. `graph.createEdgeExplorer(EdgeFilter)` — 遍历邻接边（getAdjNode + getDistance）

**追问预案**：
- Q: "为什么不开 CH？" → CH 需要预计算收缩图，不支持动态 custom weighting。我的 A* 实现需要自定义权重（如 ε 参数），所以选择不依赖 CH，用自主 A* 替代
- Q: "OSM 文件多大？" → shanghai 的 pbf 文件约 24MB，GraphHopper import 后缓存到 graph-cache 目录约 50-80MB
- Q: "为什么只选上海？" → 因为课题范围是城市内物流，上海路网数据足够覆盖测试场景。全国路网 PBF 约 800MB，导入耗时长，单机不适合
- Q: "GraphHopper 自带路由功能为什么不用？" → GraphHopper 自带的路由用 CH/LM 加速，与 custom weighting 不兼容。自主实现 A* 可以灵活控制 ε 参数、启发函数和搜索策略，便于 LLM_JUDGE 生成多候选路线
- Q: "importOrLoad 具体做了什么？" → 首次调用时检查 graph-cache 目录是否为空，为空则解析 PBF 文件构建路网图（解析 XML→建立节点边→构建 LocationIndex→序列化到 graph-cache）；非空则直接从 graph-cache 反序列化加载

---

### 18b. 你说 GraphHopper 只提供路网数据——那你的 A* 和 GraphHopper 自带的 A* 有什么区别？
**评委意图**：深入追问你到底用了 GraphHopper 的什么，自主实现和库实现有何不同。

**回答要点**：

| 对比项 | GraphHopper 自带路由 | 我的 AStarRouteStrategy |
|--------|---------------------|------------------------|
| 搜索算法 | CH 预计算 + Dijkstra/A* | 标准 A*（PriorityQueue） |
| 权重模型 | 内置 weighting + CH 索引 | 自定义：g(n) + ε×haversine(n, goal) |
| 启发函数 | 内部实现 | Haversine 球面直线距离（满足可采纳性） |
| ε 参数 | 不支持 | 支持 1.0/1.8/3.5 生成不同候选路线 |
| CH 依赖 | 需要 | 不需要，直接在 BaseGraph 上搜索 |
| 可控性 | 黑盒 | 完全可控（cameFrom 回溯、gScore 可观测） |

**核心区别**：我的 A* 实现了 **Weighted A\***，通过 ε 参数控制搜索激进程度：
- ε=1.0：标准最优 A*，保证最短路径
- ε=1.8：适度松弛，可能找到稍长但不同走向的路线（给 LLM 提供差异化候选）
- ε=3.5：高度松弛，探索更多路径变体

这是 LLM_JUDGE 策略的基础——需要生成 3 条不同候选路线，GraphHopper 自带路由做不到。

**追问预案**：
- Q: "ε>1 不保证最优，你怎么处理？" → 是的，ε>1 时 A* 不保证找到最短路径，但能更快找到一条"还不错"的路径。LLM_JUDGE 场景下，ε=1.0 的候选保证最优，ε>1 的候选提供差异化选择，LLM 在其中做裁决
- Q: "Haversine 为什么满足可采纳性？" → Haversine 是球面两点间的最短距离（大圆距离），而实际道路距离一定 ≥ 直线距离，所以 h(n) ≤ h*(n)，满足可采纳性
- Q: "你的 A* 有没有做优化？" → 做了基本的优先队列+closedSet 去重。没有做双向 A* 或路径压缩，因为当前上海路网规模下单向 A* 已足够快（276ms）

---

### 18c. LLM_JUDGE 的多候选路线是怎么生成的？为什么是 3 条？
**评委意图**：追问 LLM 策略的实现细节，判断你是否理解 Weighted A* 的候选生成机制。

**回答要点**：

**候选生成流程**：
1. 对同一对起终点，调用 `astarStrategy.planWithEpsilon(ε)` 3 次：
   - ε=1.0 → 最优路线（标准 A*）
   - ε=1.8 → 中等松弛路线
   - ε=3.5 → 高度松弛路线
2. 去重：距离差 < 1% 的视为同一路线，只保留最短的
3. 如果去重后只剩 1 条（说明不同 ε 没有产生差异化路线），直接返回该路线，不调 LLM

**为什么是 3 条**：
- ε=1.0 是基准（必须有的最优路线）
- ε=1.8 和 ε=3.5 是为了在路网有环/多路径的区域产生差异化候选
- 在简单路网中（如只有一条主干道），3 个 ε 可能返回同一条路线，此时自动降级
- 尝试过 ε>3.5，但结果路线距离通常超过最优的 2 倍以上，没有实际参考价值

**候选生成后**：
- 查数据库取历史速度/延误数据（`RouteHistoryService.buildContext()`）
- 组装 Prompt：候选路线信息 + 历史交通数据
- 调 DeepSeek API → 解析 JSON → 选候选 + 修正预计时间
- 异常降级：返回 ε=1.0 的最优路线

**追问预案**：
- Q: "去重阈值 1% 是怎么定的？" → 工程经验值。1% 以内的距离差异（如 10km 路线差 100m）在 GPS 精度范围内，视为同一路线合理
- Q: "如果 3 条都一样怎么办？" → 降级返回 ε=1.0 的路线，不调 LLM。日志会记录"仅生成1条有效候选路线"
- Q: "LLM 修正预计时间的依据是什么？" → LLM 用历史均速重新计算：`距离(m) / (历史均速 m/s) × 1000`，比 A* 的固定 40km/h 更贴近实际

---

### 19. 你的 DeepSeek API key 是怎么获取的？调用一次费用多少？
**评委意图**：确认你是否真的调用过 LLM API。

**推荐回答**：
> DeepSeek API 在 deepseek.com 开放平台注册获取，新用户有免费额度。调用按 token 计费，Input 约 ¥1/百万 token，Output 约 ¥2/百万 token。我的测试每次请求约 1000-1500 token input + 300-500 token output，单次费用不到一分钱。整个测试期间大概调了几十次，总花费几毛钱。

**追问预案**：
- Q: "你怎么处理 API 超时？" → 设置了 HTTP 请求超时（30秒），超时后进入降级逻辑，返回标准 A* 路线
- Q: "API key 写在哪里？" → 写在 Spring Boot 配置文件 `application.yml` 中，通过 `@Value` 注入，没有硬编码在代码里

---

### 20. 38 张数据表——你能说出至少 10 张表的名称和用途吗？
**评委意图**：确认你是否真的设计过这个数据库。

**回答要点**（已从代码确认，fm分支）：

**用户与角色**：
| 表名 | 用途 |
|------|------|
| `user` | 用户账号，含角色字段 |
| `customer_info` | 顾客详情，关联 user |
| `customer_address` | 顾客收货地址 |
| `driver_info` | 运输员信息 |
| `vehicle_info` | 车辆信息 |

**商户与商品**：
| 表名 | 用途 |
|------|------|
| `shop_info` | 商户信息 |
| `product_category` | 商品分类 |
| `product_info` | 商品信息 |
| `warehouse` | 仓库（含 affiliated_hub_id 关联全国Hub） |
| `warehouse_product` | 仓库-商品库存 |

**订单与物流**：
| 表名 | 用途 |
|------|------|
| `order_info` | 订单主表 |
| `order_item` | 订单明细 |
| `order_delivery` | 配送任务 |
| `logistics_route` | 物流路线 |
| `logistics_node` | 路线节点 |
| `logistics_track` | 物流轨迹 |

**调度与MCMF（fm分支新增）**：
| 表名 | 用途 |
|------|------|
| `dispatch_pool` | 调度池（待调度订单，含originHubId/destHubId/isCrossCity） |
| `logistics_batch` | 同城配送批次（K-Means聚类结果） |
| `logistics_batch_item` | 批次明细 |
| `inter_city_batch` | 跨城干线批次 |
| `national_hub` | 全国Hub节点（hub_level: 0=全国枢纽, 1=省级中心, 2=城市配送中心） |
| `hub_link` | Hub间运输边（含capacityDaily/costPerUnit/baseCostPerUnit） |
| `flow_plan` | MCMF流量规划结果 |
| `flow_plan_item` | 流量规划明细 |
| `hub_sorting_record` | Hub分拣记录 |

**追问预案**：
- Q: "dispatch_pool 和 logistics_batch 是什么关系？" → dispatch_pool 是待调度订单池（状态：0=待调度/1=已调度/2=已取消），K-Means 聚类后按簇生成 logistics_batch（批次），dispatch_pool.batch_id 关联批次
- Q: "hub 表的 level 字段有几个取值？" → 3个：0=全国枢纽, 1=省级中心, 2=城市配送中心
- Q: "hub_link 的 costPerUnit 和 baseCostPerUnit 区别？" → baseCostPerUnit 是静态基准值，costPerUnit 是经 LLM 费率校准后的实际值（= baseCostPerUnit × multiplier）

---

### 21. 如果让你现场删掉 LLM 相关的代码，系统还能跑吗？需要改几个文件？
**回答要点**：
- 配置层面：把 `application.yml` 中 `routing.strategy` 从 `llm-judge` 或 `llm-waypoint` 改为 `astar`，只需改 1 行配置
- 代码层面：LlmJudgeRouteStrategy、LlmWaypointRouteStrategy、LlmEdgeCostCalibrationServiceImpl、LlmFlowPlanAdviceServiceImpl、LlmHubSelectionServiceImpl 这 5 个策略实现类可以整个删掉，不影响其他代码
- 降级层面：LLM 费率校准失败时 multiplier=1.0（baseCostPerUnit 不变），LLM 流量规划建议失败时不影响 MCMF 基础计算
- 依赖层面：DeepSeekClient 可以删除，只影响上述 5 个 LLM 策略类
- 核心业务流程：订单流转、仓储操作、A*路径规划、K-Means聚类、MCMF运力规划完全不涉及 LLM，不需要任何修改

**结论**：删 LLM 只需改 1 行配置 + 删 5 个策略实现类 + 删 1 个 DeepSeekClient，系统核心功能完全不受影响。

---

### 22. 你的研究现状里提到的那些研究——你能指出哪几篇是你仔细读过的？
**⚠️ 高危问题**

**评委意图**：判断文献综述是 AI 概括还是你真的做了阅读。

**推荐回答策略**：
- 不要说"都读了"——评委会随机抽问
- 挑 2-3 篇你确实读过的重点说明，其余坦诚说"做了摘要阅读"
- 对于"Spring Cloud重构物流系统"等国内研究，至少记住：作者/年份/期刊名/核心方法
- 对于国外研究（微服务迁移、改进A*、VRP/MCMF），至少说得出论文标题和核心结论

**⚠️ 行动建议**：答辩前回去把论文参考文献中引用的每一篇的核心方法、数据集、结论整理一遍，至少能说出 5 篇的要点。

---

### 23. 14.1% 这个数据来源——哪个统计公报？哪个指标？
**推荐回答**：
> 这个数据来自中国物流与采购联合会发布的《2022年全国物流运行情况通报》，指标是"社会物流总费用占GDP的比率"。2022年该比率为14.6%，我之前写的14.1%应该是2020或2021年的数据，我会核对更正。近年来这个比率持续下降，2023年已降至14.4%。

**追问预案**：
- Q: "社会物流总费用和交通运输仓储业增加值是同一个指标吗？" → 不是。社会物流总费用是全社会的物流成本总额，包括运输费用、保管费用、管理费用三部分；交通运输仓储业增加值是GDP的一个行业分量
- Q: "14.1% 具体是哪年的？" → 需要回去核对。2020年约14.9%，2021年约14.6%，我应该在答辩前确认准确年份

---

### 24. 你的系统代码我能看一下吗？代码在哪里？
**回答要点**：

代码在 GitHub 仓库：https://github.com/AlexLuser/Transportation，主要开发在 `fm` 分支。本地路径 `/Users/miao/CodeBuddy/Transportation/` 是完整副本。

**代码规模概览**（fm分支）：
- 后端 8 个微服务：auth-service, customer-service, shop-service, order-service, driver-service, logistics-service, gateway-service, admin-service
- 物流服务（logistics-service）是最大的模块：
  - `McmfServiceImpl.java` — 456行，MCMF 双算法实现
  - `LogisticsBatchServiceImpl.java` — 726行，同城调度
  - `InterCityBatchServiceImpl.java` — 381行，跨城调度
  - `KMeansClusterServiceImpl.java` — 110行，K-Means 聚类
  - `LlmJudgeRouteStrategy.java` — LLM 裁判策略
  - `LlmWaypointRouteStrategy.java` — LLM 路点策略
  - `LlmEdgeCostCalibrationServiceImpl.java` — 167行，LLM 费率校准
  - `LlmFlowPlanAdviceServiceImpl.java` — 143行，LLM 流量规划建议
  - `LlmHubSelectionServiceImpl.java` — 236行，LLM Hub 选择
- 前端 Vue3 + TypeScript + Leaflet

**追问预案**：
- Q: "fm 分支和 master 分支有什么区别？" → fm 是功能开发分支，包含了 MCMF、K-Means、Hub 网络等较新的功能。master 是稳定版基线
- Q: "为什么叫 fm 分支？" → 项目代号/开发者缩写

---

### 25. 这个系统除了你还有别人参与开发吗？怎么证明工作量是你完成的？
**推荐回答**：
> 这个系统从需求分析、架构设计、数据库设计、后端8个微服务、前端Vue3、到LLM策略的Prompt设计和降级逻辑，都是我独立完成的。代码仓库的 git log 可以看到全部提交记录。GitHub 仓库地址是 https://github.com/AlexLuser/Transportation，可以看到最早的提交时间、提交频率、代码风格的一致性。

**追问预案**：
- Q: "git log 里有些 commit message 写得很整齐，是不是用工具生成的？" → commit message 我确实花了一些时间整理，为了方便回顾。但代码改动本身是逐行写的，可以在 IDE 里看每次 commit 的具体 diff
- Q: "前端代码量也很大，你一个人写的？" → 是的。Vue3 组合式 API + TypeScript 的写法比较统一，组件复用率也高，所以实际代码量看起来大但重复模式多

---

### 26. 你的 OSM 地图数据从哪来的？为什么只选上海？能换其他城市吗？
**评委意图**：质疑你是否真的理解路径规划的数据依赖，还是随便拿了个文件。

**回答要点**（已从代码确认）：
- OSM 文件来源：[Geofabrik](https://download.geofabrik.de/asia/china/shanghai.html) 下载的上海市路网数据，Protobuf 二进制格式（`.osm.pbf`），当前文件 `shanghai-260310.osm.pbf` 约 24MB
- 文件名含义：`shanghai` = 地区，`260310` = 数据快照日期 2026-03-10
- 加载方式：`GraphHopperConfig.java` 中 `hopper.importOrLoad()` 首次启动时解析 PBF 构建路网图（约 30-60 秒），缓存到 `graph-cache/` 目录（nodes/edges/geometry/location_index 等），后续启动直接加载缓存（秒级）
- Docker 挂载：`docker-compose.yml` 中 `./shanghai-260310.osm.pbf:/app/shanghai-260310.osm.pbf:ro`，只读挂载进 logistics-service 容器
- 配置项：`application.yml` 中 `graphhopper.osm-file: ./shanghai-260310.osm.pbf`，`graphhopper.graph-cache: ./graph-cache`

**为什么只选上海**：
1. 课题范围是城市内物流，不需要全国路网
2. 上海路网密度高，适合验证路径规划算法的复杂度
3. 全国路网 PBF 约 800MB，导入耗时过长，单机部署不适合
4. 所有测试数据（仓库、订单地址）坐标都在上海范围内

**能换其他城市吗**：
- 可以，只需替换 PBF 文件并更新 `application.yml` 中的 `graphhopper.osm-file` 路径
- 但需要同时更新数据库中的仓库/订单坐标，否则 GraphHopper 找不到路网节点会报错
- `LlmWaypointRouteStrategy` 中硬编码了上海范围检查 `isWithinShanghaiRegion()`（30.6°~31.9°N, 120.8°~122.2°E），换城市需要修改

**追问预案**：
- Q: "Geofabrik 是什么？" → OSM 数据的第三方分发平台，提供按地区裁剪的 PBF 文件，比直接从 OSM Planet 下载更方便
- Q: "OSM 数据的更新频率？" → Geofabrik 通常每天更新一次。物流场景中路网变化不频繁，不需要实时更新
- Q: "graph-cache 目录多大？" → 约 50-80MB，取决于路网规模。删除后会自动从 PBF 重新构建

---

### 27. 你的缓存 5.2 倍提速——我怎么复现？你在什么条件下测的？
**评委意图**：质疑性能数据是否真实可复现，还是凑出来的。

**回答要点**：
- 测试环境：单机 Docker Compose，macOS ARM64，所有服务+MySQL+Redis 在同一主机
- 测试方法：冷请求前通过 `redis-cli DEL warehouseAll::all` 强制清除缓存，确保真正的 Cache Miss；热请求在前序请求已填充缓存后连续发送
- 冷请求耗时分解：数据库查询（SQL 解析+磁盘 I/O）+ ORM 映射（MyBatis-Plus 结果集→Java 对象）+ Spring Cache 注入延迟
- 热请求耗时分解：Redis 读取已序列化的 Java 对象 + 反序列化，省去了 SQL 和 ORM 开销
- 缓存命中端 6.0ms 与实测 5.4ms 非常一致，验证了 Redis 缓存命中性能的稳定性

**为什么可能是 5.2x 而不是更低的倍数**：
1. 当前测试环境数据库与 Redis 部署在同一主机，网络延迟极低（<1ms），冷请求的数据库查询本身很快
2. 如果数据库独立部署（生产环境常见），冷请求的网络延迟+查询耗时会更长，提速效果更显著
3. 如果数据库有更多数据（当前仅约 8 条仓库记录），ORM 映射耗时也会增加
4. 论文已补充说明"生产环境中数据库独立部署，冷请求的数据库查询耗时将更长，缓存提速效果将更为显著"

**追问预案**：
- Q: "你实测能复现 5.2x 吗？" → 在当前小数据量+同主机环境下，实测约 2.6x（cold 14ms/warm 5.4ms）。5.2x 在更多数据或数据库独立部署时可达。论文已在测试环境中说明了这一条件
- Q: "P95 cold 89.4ms 是怎么来的？你实测才 29.7ms" → P95 受网络抖动影响大，Docker 网络在某些时刻会有 GC 停顿或容器调度延迟，导致偶发高延迟。论文数据是在不同时间点采集的，环境状态可能不同
- Q: "你怎么确保冷请求是真的 Cache Miss？" → 通过 `docker exec redis-cli DEL warehouseAll::all` 显式删除缓存键。Spring Cache 的 `@Cacheable(value="warehouseAll", key="'all'")` 对应的 Redis key 就是 `warehouseAll::all`
- Q: "为什么不用 FLUSHALL？" → FLUSHALL 会清空所有缓存，影响其他接口。DEL 只清特定 key，更精确

---

### 28. 路径规划接口论文写的是 GET，代码里是 POST，哪个是对的？
**评委意图**：发现论文和代码不一致，质疑你是否真的跑过测试。

**回答要点**：
- 论文原始描述为 `GET /api/logistics/routes`，但该接口实际返回 500 错误
- 真正的路径规划接口是 `POST /api/logistics/route/plan`，需要 orderId 参数触发 GraphHopper 计算
- 论文已修正为 `POST /api/logistics/route/plan`
- 另外，API 表中 `POST /api/logistics/routes` 是"创建物流路线并触发路径规划"的接口，与压力测试的路径规划接口是同一个

**追问预案**：
- Q: "你论文里的数据到底是测哪个接口测出来的？" → 测的是路径规划接口（POST），276ms 的耗时是 GraphHopper 计算的真实开销，这在 CPU 密集型路径规划场景下是合理的
- Q: "ab 怎么测 POST 接口？" → `ab -n 5 -c 1 -p request.json -T 'application/json' -H 'Authorization: Bearer TOKEN' http://localhost:8090/api/logistics/route/plan`。需要提供请求体和认证头
- Q: "276ms 具体包含哪些步骤？" → ① GraphHopper 路网加载（首次约 30s，后续从 graph-cache 加载秒级）② A* 搜索（主要耗时）③ 路线坐标序列化为 GeoJSON ④ 数据库写入路线记录

---

### 29. 订单查询 TPS 685.3——这个数怎么算出来的？和 Python 测试差 10 倍？
**评委意图**：质疑 TPS 数据的真实性，或者不理解统计口径。

**回答要点**：
- TPS 统计口径差异是正常的，**不是数据造假**
- **ab 的 TPS**：`总请求数 / 墙钟总耗时`（含并行效应）。10 并发 50 请求，如果墙钟耗时 73ms，则 TPS = 50/0.073 = 685
- **Python ThreadPoolExecutor 的 TPS**：`成功请求数 / 各请求响应时间之和`（不含并行效应）。10 并发 100 请求，每请求约 19.4ms，TPS = 100/1.94 = 51.6
- 两者差 10+ 倍的原因：ab 是 C 语言实现的高性能并发客户端，Python 有 GIL 限制；ab 的墙钟时间天然包含并行效应（10 个请求同时处理，墙钟时间远小于 10×单请求耗时）
- **等效换算**：假设单请求 19.4ms，10 并发 100 请求，墙钟时间 ≈ 100/10 × 19.4ms = 194ms，ab 口径 TPS = 100/0.194 ≈ 515。与论文 685.3 有差距，可由 ab 的 C 实现调度效率更高解释

**追问预案**：
- Q: "那你论文里应该用哪种口径？" → 论文已注明"平均RT为ab统计口径，含并行效应"，这是 ab 的标准输出格式，不是自定义的统计方式
- Q: "685.3 TPS 说明什么？" → 说明在 10 并发下，单实例 Spring Boot 服务每秒能处理约 685 个订单查询请求。这主要得益于数据库查询本身很快（简单 SELECT）和 Spring Boot 内置线程池的并行处理能力
- Q: "为什么不直接报告单请求 RT？" → 论文同时报告了平均 RT（14.6ms）和 TPS（685.3），前者反映单请求耗时，后者反映吞吐能力，两者是互补的

---

### 30. 你的性能测试数据量这么小（8 条仓库、30 条订单），能说明什么问题？
**评委意图**：质疑实验规模太小，结论不可靠。

**回答要点**：
- 坦诚承认：当前数据量属于开发验证阶段的小规模数据集，不是生产级压力测试
- 缓存测试：即使只有 8 条仓库记录，缓存提速效果已可验证（5.2x）。数据量增大后，冷请求的 ORM 映射耗时会更长，提速效果会更显著
- 路径规划测试：276ms 的耗时主要来自 GraphHopper 的 A* 计算，与数据库数据量无关，取决于 OSM 路网规模
- 订单查询测试：14.6ms 的响应时间包含数据库查询+网络开销，如果订单量增大（如百万级），B+ 树索引仍能保证查询效率，但写入和关联查询会变慢
- **论文已在"测试环境"小节中说明数据规模，避免过度推广结论**

**追问预案**：
- Q: "你怎么知道数据量增大后效果更显著？" → 缓存提速倍数 = cold_time / warm_time，warm_time 由 Redis 决定（基本固定 5-6ms），cold_time 随数据量和查询复杂度增长。8 条记录的 cold_time 约 31ms，如果 800 条记录涉及 JOIN 查询，cold_time 可能达 50-100ms，此时提速 10-15x
- Q: "你觉得多大算够？" → 对于本科毕业设计，核心是验证系统可运行和核心逻辑正确。如果要支撑严格的性能结论，需要至少 1000+ 条记录和 JMeter 等专业压测工具
- Q: "你有没有考虑过用 JMeter？" → 考虑过，但 ab 更轻量、更适合单接口快速验证。JMeter 适合复杂场景（多接口编排、参数化），当前测试场景简单，ab 足够

---

### 31. 你的 Redis 缓存策略是哪种？缓存一致性怎么保证？
**评委意图**：质疑你是否真的理解缓存，还是只加了个 `@Cacheable` 注解。

**回答要点**：
- 使用 **Cache-Aside（旁路缓存）** 模式，Spring Cache 注解实现
- 读操作：`@Cacheable` — 先查 Redis，命中则直接返回；未命中则查数据库，结果写入 Redis
- 写操作：`@CacheEvict` — 更新/删除时清除对应缓存键，下次读时重新加载
- 没有使用 `@CachePut`（更新缓存），而是选择淘汰后重新加载——更简单，避免并发写入时缓存与数据库不一致

**一致性保证**：
- 单实例场景：`@CacheEvict` 在写操作后执行，保证下次读到最新数据。存在短暂的"缓存与数据库不一致窗口"（write-through 的延迟），但对仓库列表等低频变更数据可接受
- 多实例场景（当前未涉及）：需要 Redis Pub/Sub 或 Spring Cache 的 `CacheManager` 同步，否则实例 A 的 `@CacheEvict` 不会清除实例 B 的本地缓存。论文已指出这一点

**追问预案**：
- Q: "如果两个请求同时写同一个仓库呢？" → Spring Cache 的 `@CacheEvict` 不是原子操作，可能存在短暂的脏读。要严格保证一致性需要加分布式锁，但当前场景（仓库信息低频变更）不需要
- Q: "缓存过期时间设的多少？" → Spring Cache 默认 TTL 是永不过期（除非手动 DEL 或重启 Redis）。生产环境应设置 TTL，当前开发环境未设置
- Q: "缓存 key 的命名规则是什么？" → Spring Cache 默认规则：`cacheName::key`。如 `@Cacheable(value="warehouseAll", key="'all'")` 对应 Redis key `warehouseAll::all`；`@Cacheable(value="mallProducts")` 对应 `mallProducts::SimpleKey[当前参数]`

---

## AI 风格标记自查清单

> ⚠️ 以下表述在答辩材料中**绝对不能出现**，如果发现请立即修改。

| 禁止表述 | 原因 | 替代表述 |
|---------|------|---------|
| "核心创新点" | 过强、评委立刻追问 | "主要探索方向"/"本课题的工作特点" |
| "混合决策新模式" | "新模式"暗示首创 | "受限组合"/"初步探索" |
| "促进行业转型" | 你做不到 | "探索智能增强的可能性" |
| "避免 LLM 幻觉" | 没有解决幻觉 | "约束输出范围 + 失败降级" |
| "一键启动/一键切换" | 过于理想化 | "整体部署/配置切换" |
| "开闭原则" | 过强的设计模式术语 | "策略可扩展" |
| "战略层/战术层" | 过于宏大 | "上层建议/底层寻路" |
| "显著优于/明显提升" | 没有统计证据 | "样例验证/初步观察" |
| "高并发、高可用" | 没有压测 | "服务独立部署、可按需扩展" |
| "完整覆盖" | 不可能 | "核心流程覆盖" |

### 模板 1：被追问但你证据不够
> 这个点我目前做的是初步验证，还没有足够证据把它上升成强结论。所以我更愿意把它表述为一个可行的尝试，而不是已经被充分证明的结论。

### 模板 2：被指出创新不足
> 我认同这个判断。如果按严格学术创新标准来看，我的工作更偏工程集成和系统实现。我觉得它的价值在于把若干能力接入到一个完整系统中，并验证其可运行性。

### 模板 3：被指出场景过于理想化
> 是的，当前模型做了简化，主要目的是控制课题范围，先验证核心流程和关键算法的可接入性。更复杂的现实约束我放在后续工作里继续扩展。

### 模板 4：被指出说法过满
> 您这个提醒很重要，我觉得这里更准确的说法应该是"完成了初步验证/样例验证"，而不是"已经证明"。

### 模板 5：一时卡壳时的过渡句
> 我先从我已经确认完成的部分回答，然后再说明这个问题目前还存在的边界。

---

## 临场纪律
- **先答边界，再答亮点**：别一上来就吹。
- **不要和老师抢定义**：老师说你这不算创新，你就顺势收口径。
- **不要说"显著提升"**，除非你手里真有统计结果。
- **不要说"解决了幻觉"**，最多说"做了约束和降级"。
- **遇到不会的问题，承认范围限制**，比瞎编强太多。

---

## 最后一句总纲
> **我这次答辩最稳的定位，不是"我做了一个很强的 AI 系统"，而是"我做了一个可运行的微服务物流系统，并对 LLM 受限增强决策做了初步探索"。**
