<template>
    <div class="order-manage">

        <!-- 顶部筛选栏 -->
        <div class="toolbar">
            <div class="toolbar-left">
                <el-input
                    v-model="keyword"
                    placeholder="搜索订单号"
                    clearable
                    class="search-input"
                    @keyup.enter="handleSearch"
                    @clear="handleSearch"
                />
                <el-select v-model="statusFilter" placeholder="全部状态" clearable class="status-select" @change="handleSearch">
                    <el-option v-for="s in ORDER_STATUSES" :key="s.value" :label="s.label" :value="s.value" />
                </el-select>
                <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            </div>
            <el-button :icon="Refresh" @click="fetchOrders">刷新</el-button>
        </div>

        <!-- 订单表格 -->
        <el-card shadow="never" class="table-card">
            <el-table :data="filteredOrders" v-loading="loading" stripe>
                <el-table-column label="订单号" prop="orderNo" min-width="180" show-overflow-tooltip />
                <el-table-column label="商品金额" width="110">
                    <template #default="{ row }">
                        <span class="price-text">¥{{ row.productAmount }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="运费" width="90">
                    <template #default="{ row }">
                        <span>¥{{ row.shippingFee ?? '0.00' }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="总金额" width="110">
                    <template #default="{ row }">
                        <span class="price-text bold">¥{{ row.totalAmount }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="订单状态" width="100">
                    <template #default="{ row }">
                        <el-tag :type="orderStatusTag(row.orderStatus)" size="small">
                            {{ orderStatusLabel(row.orderStatus) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="支付状态" width="90">
                    <template #default="{ row }">
                        <el-tag :type="row.paymentStatus === 1 ? 'success' : 'warning'" size="small">
                            {{ row.paymentStatus === 1 ? '已支付' : '未支付' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="下单时间" width="160">
                    <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="160" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" @click="openDetailDialog(row)">详情</el-button>
                        <el-button
                            size="small"
                            type="primary"
                            :disabled="row.orderStatus !== 1 || row.paymentStatus !== 1"
                            @click="openShipDialog(row)"
                        >发货</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <el-empty v-if="!loading && filteredOrders.length === 0" description="暂无订单" />
        </el-card>

        <!-- ===================== 订单详情弹窗 ===================== -->
        <el-dialog v-model="detailVisible" title="订单详情" width="700px" align-center>
            <div v-if="detailData" class="detail-body">
                <!-- 订单基本信息 -->
                <el-descriptions title="基本信息" :column="2" border size="small">
                    <el-descriptions-item label="订单号" :span="2">{{ detailData.order.orderNo }}</el-descriptions-item>
                    <el-descriptions-item label="商品金额">¥{{ detailData.order.productAmount }}</el-descriptions-item>
                    <el-descriptions-item label="运费">¥{{ detailData.order.shippingFee ?? '0.00' }}</el-descriptions-item>
                    <el-descriptions-item label="总金额">¥{{ detailData.order.totalAmount }}</el-descriptions-item>
                    <el-descriptions-item label="订单状态">
                        <el-tag :type="orderStatusTag(detailData.order.orderStatus)" size="small">
                            {{ orderStatusLabel(detailData.order.orderStatus) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="支付状态">
                        <el-tag :type="detailData.order.paymentStatus === 1 ? 'success' : 'warning'" size="small">
                            {{ detailData.order.paymentStatus === 1 ? '已支付' : '未支付' }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="支付时间">{{ formatDate(detailData.order.paymentTime) }}</el-descriptions-item>
                    <el-descriptions-item label="发货时间">{{ formatDate(detailData.order.shippingTime) }}</el-descriptions-item>
                    <el-descriptions-item label="完成时间">{{ formatDate(detailData.order.completeTime) }}</el-descriptions-item>
                    <el-descriptions-item label="备注" :span="2">{{ detailData.order.remark || '-' }}</el-descriptions-item>
                    <el-descriptions-item v-if="detailData.order.cancelReason" label="取消原因" :span="2">
                        {{ detailData.order.cancelReason }}
                    </el-descriptions-item>
                </el-descriptions>

                <!-- 订单商品明细 -->
                <div class="items-section">
                    <div class="section-title">商品明细</div>
                    <el-table :data="detailData.items" border size="small">
                        <el-table-column label="商品名称" prop="productName" min-width="160" show-overflow-tooltip />
                        <el-table-column label="单价" width="100">
                            <template #default="{ row }">¥{{ row.productPrice }}</template>
                        </el-table-column>
                        <el-table-column label="数量" prop="quantity" width="80" align="center" />
                        <el-table-column label="小计" width="110">
                            <template #default="{ row }">
                                <span class="price-text">¥{{ row.subtotal }}</span>
                            </template>
                        </el-table-column>
                    </el-table>
                </div>
            </div>
            <template #footer>
                <el-button @click="detailVisible = false">关闭</el-button>
                <el-button
                    type="primary"
                    v-if="detailData && detailData.order.orderStatus === 1 && detailData.order.paymentStatus === 1"
                    @click="openShipDialog(detailData!.order)"
                >确认发货</el-button>
            </template>
        </el-dialog>

        <!-- ===================== 发货 & 选仓库弹窗 ===================== -->
        <el-dialog
            v-model="shipVisible"
            title="发货确认"
            width="520px"
            align-center
            :close-on-click-modal="false"
        >
            <div class="ship-body" v-loading="shipLoading">
                <el-alert
                    title="选择发货仓库后，系统将自动规划最优配送路线（LLM 智能决策）"
                    type="info"
                    show-icon
                    :closable="false"
                    class="ship-tip"
                />

                <el-form label-width="90px" class="ship-form">
                    <el-form-item label="订单号">
                        <el-text>{{ shipOrder?.orderNo ?? '-' }}</el-text>
                    </el-form-item>
                    <el-form-item label="发货仓库" required>
                        <el-select
                            v-model="selectedWarehouseId"
                            placeholder="请选择发货仓库"
                            style="width: 100%"
                            :loading="warehousesLoading"
                            @change="onWarehouseChange"
                        >
                            <el-option
                                v-for="w in warehouses"
                                :key="w.id"
                                :label="w.warehouseName"
                                :value="w.id"
                            >
                                <div class="warehouse-option">
                                    <span class="w-name">{{ w.warehouseName }}</span>
                                    <span class="w-addr">{{ w.detailAddress }}</span>
                                </div>
                            </el-option>
                        </el-select>
                    </el-form-item>
                    <el-form-item label="仓库地址" v-if="selectedWarehouse">
                        <el-text type="info">{{ selectedWarehouse.detailAddress }}</el-text>
                    </el-form-item>
                    <el-form-item label="仓库坐标" v-if="selectedWarehouse">
                        <el-text type="info">
                            {{ selectedWarehouse.latitude ?? '未录入' }}, {{ selectedWarehouse.longitude ?? '未录入' }}
                        </el-text>
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <el-button @click="shipVisible = false" :disabled="shipLoading">取消</el-button>
                <el-button
                    type="primary"
                    :loading="shipLoading"
                    :disabled="!selectedWarehouseId"
                    @click="handleConfirmShip"
                >确认发货 &amp; 规划路线</el-button>
            </template>
        </el-dialog>

        <!-- ===================== LLM 决策结果弹窗 ===================== -->
        <el-dialog
            v-model="llmResultVisible"
            title="路线规划完成 — LLM 决策结果"
            width="680px"
            align-center
        >
            <div v-if="llmResult" class="llm-body">

                <!-- 基本状态 -->
                <div class="llm-row">
                    <el-tag :type="llmResult.llmEnhanced ? 'success' : 'warning'" size="large">
                        {{ llmResult.llmEnhanced ? 'LLM 智能增强' : '纯 A* 兜底（LLM 调用失败）' }}
                    </el-tag>
                </div>

                <template v-if="llmResult.llmDecision">
                    <!-- 方案 A：LLM_JUDGE（多候选裁判） -->
                    <el-descriptions
                        v-if="isLlmJudgeDecision(llmResult.llmDecision)"
                        :column="2"
                        border
                        size="small"
                        class="llm-desc"
                    >
                        <el-descriptions-item label="选中候选路线">
                            第 {{ llmResult.llmDecision.selectedCandidate }} 条
                        </el-descriptions-item>
                        <el-descriptions-item label="置信度">
                            <el-tag
                                :type="confidenceTag(llmResult.llmDecision.confidenceLevel)"
                                size="small"
                            >{{ llmResult.llmDecision.confidenceLevel }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="调整后预计时长" :span="2">
                            {{ formatDuration(llmResult.llmDecision.adjustedDurationMs) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="摘要" :span="2">
                            {{ llmResult.llmDecision.summary || '-' }}
                        </el-descriptions-item>
                    </el-descriptions>

                    <!-- 方案 B：LLM_WAYPOINT（战略路点 + 分段 A*） -->
                    <el-descriptions
                        v-else-if="isLlmWaypointDecision(llmResult.llmDecision)"
                        :column="2"
                        border
                        size="small"
                        class="llm-desc"
                    >
                        <el-descriptions-item label="策略" :span="2">
                            {{ llmResult.llmDecision.strategy || '-' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="置信度">
                            <el-tag
                                :type="confidenceTag(llmResult.llmDecision.confidenceLevel)"
                                size="small"
                            >{{ llmResult.llmDecision.confidenceLevel || '-' }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="预估全程均速">
                            {{
                                llmResult.llmDecision.expectedSpeedKmh != null
                                    ? `${llmResult.llmDecision.expectedSpeedKmh} km/h`
                                    : '-'
                            }}
                        </el-descriptions-item>
                        <el-descriptions-item label="摘要" :span="2">
                            {{ llmResult.llmDecision.summary || '-' }}
                        </el-descriptions-item>
                        <el-descriptions-item
                            v-if="llmResult.llmDecision.waypoints?.length"
                            label="战略路点"
                            :span="2"
                        >
                            <ul class="waypoint-list">
                                <li
                                    v-for="(wp, wi) in llmResult.llmDecision.waypoints"
                                    :key="wi"
                                >
                                    {{ wp.label || '路点' }}（{{
                                        wp.lat != null && wp.lon != null
                                            ? `${Number(wp.lat).toFixed(4)}, ${Number(wp.lon).toFixed(4)}`
                                            : '坐标未返回'
                                    }}）
                                </li>
                            </ul>
                        </el-descriptions-item>
                    </el-descriptions>

                    <!-- 其它可解析结构 -->
                    <el-descriptions v-else :column="2" border size="small" class="llm-desc">
                        <el-descriptions-item label="置信度">
                            <el-tag
                                :type="confidenceTag(llmResult.llmDecision.confidenceLevel)"
                                size="small"
                            >{{ llmResult.llmDecision.confidenceLevel || '-' }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="摘要" :span="2">
                            {{ llmResult.llmDecision.summary || '-' }}
                        </el-descriptions-item>
                    </el-descriptions>

                    <div class="llm-section">
                        <div class="section-title">LLM 推理过程</div>
                        <div class="llm-reasoning">{{ llmResult.llmDecision.reasoning || '暂无推理说明' }}</div>
                    </div>

                    <div class="llm-section" v-if="llmResult.llmDecision.warnings?.length">
                        <div class="section-title">警告 / 风险提示</div>
                        <el-alert
                            v-for="(w, i) in llmResult.llmDecision.warnings"
                            :key="i"
                            :title="w"
                            type="warning"
                            show-icon
                            :closable="false"
                            class="llm-warning"
                        />
                    </div>
                </template>

                <el-alert
                    v-else
                    title="LLM 决策数据不可用，已回退至标准 A* 路线"
                    type="warning"
                    show-icon
                    :closable="false"
                />
            </div>
            <template #footer>
                <el-button type="primary" @click="llmResultVisible = false">知道了</el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts" name="ShopOrder">
    import { ref, computed, onMounted } from 'vue';
    import { ElMessage } from 'element-plus';
    import { Search, Refresh } from '@element-plus/icons-vue';
    import { getShopOrders, getOrderDetail, updateOrderStatus } from '@/api/order';
    import { getMyShop, getWarehouses, type Warehouse } from '@/api/shop';
    import { getAddressById } from '@/api/customer';
    import { createRoute } from '@/api/logistics';
    import { useUserStore } from '@/stores/userStore';

    const userStore = useUserStore();

    const ORDER_STATUSES = [
        { value: 0, label: '待支付' },
        { value: 1, label: '待发货' },
        { value: 2, label: '待揽件' },
        { value: 3, label: '派送中' },
        { value: 4, label: '已完成' },
        { value: 5, label: '已取消' },
    ];

    // ==================== 列表状态 ====================
    const loading = ref(false);
    const orders = ref<any[]>([]);
    const keyword = ref('');
    const statusFilter = ref<number | null>(null);
    const shopId = ref<number | null>(null);

    const filteredOrders = computed(() => {
        let list = orders.value;
        if (keyword.value) {
            list = list.filter(o => o.orderNo?.includes(keyword.value));
        }
        if (statusFilter.value !== null) {
            list = list.filter(o => o.orderStatus === statusFilter.value);
        }
        return list;
    });

    const fetchOrders = async () => {
        if (!shopId.value) return;
        loading.value = true;
        try {
            const res = await getShopOrders(shopId.value);
            orders.value = res.data ?? [];
        } finally {
            loading.value = false;
        }
    };

    const handleSearch = () => {
        // filteredOrders 是计算属性，前端过滤即可
    };

    // ==================== 获取当前商户 shopId ====================
    const initShopId = async () => {
        const userId = userStore.userInfo?.userId;
        if (!userId) return;
        try {
            const res = await getMyShop(userId);
            shopId.value = res.data?.id ?? null;
            if (shopId.value) fetchOrders();
        } catch {
            ElMessage.warning('获取商户信息失败，请先完善商铺资料');
        }
    };

    // ==================== 详情弹窗 ====================
    const detailVisible = ref(false);
    const detailData = ref<any>(null);

    const openDetailDialog = async (row: any) => {
        detailVisible.value = true;
        detailData.value = null;
        try {
            const res = await getOrderDetail(row.id);
            detailData.value = res.data;
        } catch {
            detailVisible.value = false;
        }
    };

    // ==================== 发货弹窗（选仓库） ====================
    const shipVisible = ref(false);
    const shipLoading = ref(false);
    const shipOrder = ref<any>(null);

    const warehouses = ref<Warehouse[]>([]);
    const warehousesLoading = ref(false);
    const selectedWarehouseId = ref<number | null>(null);
    const selectedWarehouse = computed(() =>
        warehouses.value.find(w => w.id === selectedWarehouseId.value) ?? null
    );

    const openShipDialog = async (order: any) => {
        shipOrder.value = order;
        selectedWarehouseId.value = null;
        shipVisible.value = true;
        detailVisible.value = false;

        if (warehouses.value.length === 0) {
            warehousesLoading.value = true;
            try {
                const res = await getWarehouses();
                warehouses.value = (res.data ?? []).filter((w: Warehouse) => w.status === 1);
            } catch {
                ElMessage.error('加载仓库列表失败');
            } finally {
                warehousesLoading.value = false;
            }
        }
    };

    const onWarehouseChange = () => {
        // 选仓库后无需额外操作，selectedWarehouse 计算属性自动更新
    };

    const handleConfirmShip = async () => {
        if (!shipOrder.value || !selectedWarehouseId.value) return;

        const order = shipOrder.value;
        const warehouse = selectedWarehouse.value;

        if (!warehouse) {
            ElMessage.error('仓库信息不存在，请刷新后重试');
            return;
        }
        if (!warehouse.latitude || !warehouse.longitude) {
            ElMessage.warning('所选仓库未录入经纬度，路线规划精度可能受影响');
        }

        shipLoading.value = true;
        try {
            // 1. 获取收货地址详情（含经纬度）
            let endAddress = '';
            let endLat: number | undefined;
            let endLng: number | undefined;
            let receiverName: string | undefined;
            let receiverPhone: string | undefined;

            if (order.addressId) {
                try {
                    const addrRes = await getAddressById(order.addressId);
                    const addr = addrRes.data;
                    if (addr) {
                        const parts = [addr.province, addr.city, addr.district, addr.detailAddress].filter(Boolean);
                        endAddress = parts.join('');
                        endLat = addr.latitude ?? undefined;
                        endLng = addr.longitude ?? undefined;
                        receiverName = addr.receiverName ?? undefined;
                        receiverPhone = addr.receiverPhone ?? undefined;
                    }
                } catch {
                    endAddress = '收货地址';
                }
            }

            const startParts = [warehouse.province, warehouse.city, warehouse.district, warehouse.detailAddress].filter(Boolean);
            const startAddress = startParts.join('') || warehouse.warehouseName;

            // 2. 先更新订单状态（创建配送记录等），再创建物流路线，避免与 order-service 重复调用 logistics 并发竞态
            await updateOrderStatus(order.id, 2);
            const routeRes = await createRoute({
                orderId: order.id,
                warehouseId: selectedWarehouseId.value,
                startAddress,
                startLatitude: warehouse.latitude ?? undefined,
                startLongitude: warehouse.longitude ?? undefined,
                endAddress: endAddress || '收货地址',
                endLatitude: endLat,
                endLongitude: endLng,
                receiverName,
                receiverPhone,
            });

            shipVisible.value = false;
            fetchOrders();

            // 3. 展示 LLM 决策结果（与 logistics CreateRouteResponseDTO 对齐）
            const raw = routeRes.data as {
                llmEnhanced?: boolean;
                llmDecision?: Record<string, unknown> | null;
            } | null;
            llmResult.value = raw
                ? {
                      llmEnhanced: raw.llmEnhanced ?? false,
                      llmDecision: raw.llmDecision ?? null,
                  }
                : null;
            llmResultVisible.value = true;

        } catch (e: any) {
            ElMessage.error(typeof e === 'string' ? e : '发货失败，请稍后重试');
        } finally {
            shipLoading.value = false;
        }
    };

    // ==================== LLM 结果弹窗 ====================
    const llmResultVisible = ref(false);
    const llmResult = ref<any>(null);

    const confidenceTag = (level: string | undefined) => {
        const map: Record<string, string> = { HIGH: 'success', MEDIUM: 'warning', LOW: 'danger' };
        return map[level ?? ''] ?? 'info';
    };

    /** LLM_JUDGE：多候选裁判 */
    const isLlmJudgeDecision = (d: any) =>
        d != null && d.selectedCandidate != null && d.selectedCandidate !== undefined;

    /** LLM_WAYPOINT：战略路点（与裁判方案字段不同） */
    const isLlmWaypointDecision = (d: any) =>
        d != null &&
        !isLlmJudgeDecision(d) &&
        (Boolean(d.strategy) || (Array.isArray(d.waypoints) && d.waypoints.length > 0));

    const formatDuration = (ms: number | null | undefined) => {
        if (!ms) return '-';
        const minutes = Math.round(ms / 60000);
        if (minutes < 60) return `${minutes} 分钟`;
        const h = Math.floor(minutes / 60);
        const m = minutes % 60;
        return m > 0 ? `${h} 小时 ${m} 分钟` : `${h} 小时`;
    };

    // ==================== 工具函数 ====================
    const orderStatusLabel = (status: number) =>
        ORDER_STATUSES.find(s => s.value === status)?.label ?? '-';

    const orderStatusTag = (status: number) => {
        const map: Record<number, string> = {
            0: 'warning',
            1: '',
            2: 'info',
            3: 'primary',
            4: 'success',
            5: 'info',
        };
        return map[status] ?? '';
    };

    const formatDate = (date: string | null | undefined) => {
        if (!date) return '-';
        return new Date(date).toLocaleString('zh-CN', { hour12: false });
    };

    onMounted(() => {
        initShopId();
    });
</script>

<style scoped>
    .order-manage {
        display: flex;
        flex-direction: column;
        gap: 16px;
    }

    .toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        background: #fff;
        padding: 14px 20px;
        border-radius: 8px;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
    }

    .toolbar-left {
        display: flex;
        gap: 10px;
        align-items: center;
    }

    .search-input { width: 220px; }
    .status-select { width: 120px; }
    .table-card { border-radius: 8px; }

    .price-text { color: #f56c6c; font-weight: 500; }
    .price-text.bold { font-weight: 700; }

    /* 订单详情弹窗 */
    .detail-body {
        display: flex;
        flex-direction: column;
        gap: 20px;
    }

    .section-title {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 10px;
        padding-left: 8px;
        border-left: 3px solid #409eff;
    }

    /* 发货弹窗 */
    .ship-body { display: flex; flex-direction: column; gap: 16px; }
    .ship-tip { border-radius: 6px; }
    .ship-form { padding-top: 4px; }
    .warehouse-option {
        display: flex;
        flex-direction: column;
        line-height: 1.5;
        padding: 2px 0;
    }
    .w-name { font-weight: 500; }
    .w-addr { font-size: 12px; color: #909399; }

    /* LLM 结果弹窗 */
    .llm-body {
        display: flex;
        flex-direction: column;
        gap: 18px;
    }
    .llm-row { display: flex; align-items: center; gap: 12px; }
    .llm-desc { margin-top: 4px; }
    .waypoint-list { margin: 0; padding-left: 1.2em; }
    .llm-section { display: flex; flex-direction: column; gap: 8px; }
    .llm-reasoning {
        background: #f5f7fa;
        border-radius: 6px;
        padding: 12px 14px;
        font-size: 13px;
        line-height: 1.8;
        color: #303133;
        white-space: pre-wrap;
        max-height: 220px;
        overflow-y: auto;
    }
    .llm-warning { border-radius: 6px; }
</style>
