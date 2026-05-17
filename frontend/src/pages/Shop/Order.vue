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
                <el-table-column label="申报价值" width="110">
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
        <el-dialog
            v-model="detailVisible"
            title="订单详情"
            width="740px"
            align-center
            @closed="shopJourney = []"
        >
            <div v-if="detailData" class="detail-body">
                <!-- 订单基本信息 -->
                <el-descriptions title="基本信息" :column="2" border size="small">
                    <el-descriptions-item label="订单号" :span="2">{{ detailData.order.orderNo }}</el-descriptions-item>
                    <el-descriptions-item label="申报价值">¥{{ detailData.order.productAmount }}</el-descriptions-item>
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

                <!-- 订单承运物明细 -->
                <div class="items-section">
                    <div class="section-title">承运物明细</div>
                    <el-table :data="detailData.items" border size="small">
                        <el-table-column label="承运物名称" prop="productName" min-width="160" show-overflow-tooltip />
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

                <!-- 物流全程追踪（仅已发货以后的订单显示） -->
                <div v-if="detailData.order.orderStatus >= 2 && detailData.order.orderStatus !== 5" class="items-section">
                    <div class="section-title">物流全程追踪</div>
                    <div v-if="detailRouteLoading" class="map-loading">
                        <el-icon class="is-loading"><Loading /></el-icon> 路线加载中…
                    </div>
                    <LogisticsJourney
                        v-else
                        :segments="shopJourney"
                        :receiver-address="detailData.order?.receiverAddress"
                    />
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

        <!-- ===================== 发货弹窗（选择仓库版）===================== -->
        <el-dialog
            v-model="shipVisible"
            title="选择发货仓库"
            width="480px"
            align-center
            :close-on-click-modal="false"
        >
            <div class="ship-body" v-loading="shipLoading">
                <el-alert type="info" show-icon :closable="false" style="margin-bottom:14px">
                    <template #title>请选择本次订单的发货仓库</template>
                    <template #default>
                        系统将自动判断是否需要跨城干线运输，并进入统一调度流程。
                    </template>
                </el-alert>
                <el-descriptions :column="1" border size="small" style="margin-bottom:14px">
                    <el-descriptions-item label="订单号">{{ shipOrder?.orderNo ?? '-' }}</el-descriptions-item>
                </el-descriptions>
                <el-form :model="shipForm" label-width="90px" size="default">
                    <el-form-item label="发货仓库" required>
                        <el-select
                            v-model="shipForm.warehouseId"
                            placeholder="请选择发货仓库"
                            style="width:100%"
                            :loading="warehousesLoading"
                        >
                            <el-option
                                v-for="w in availableWarehouses"
                                :key="w.id"
                                :value="w.id"
                                :label="w.warehouseName + (w.city ? `（${w.city}）` : '')"
                            />
                        </el-select>
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <el-button @click="shipVisible = false" :disabled="shipLoading">取消</el-button>
                <el-button type="primary" :loading="shipLoading"
                           :disabled="!shipForm.warehouseId"
                           @click="handleConfirmShip">
                    确认发货
                </el-button>
            </template>
        </el-dialog>


    </div>
</template>

<script setup lang="ts" name="ShopOrder">
    import { ref, computed, onMounted } from 'vue';
    import { ElMessage } from 'element-plus';
    import { Search, Refresh, Loading } from '@element-plus/icons-vue';
    import { getShopOrders, getOrderDetail, updateOrderStatus } from '@/api/order';
    import { getMyShop, getWarehousesByShop, shipOrder as apiShipOrder } from '@/api/shop';
    import { getOrderJourney } from '@/api/logistics';
    import { useUserStore } from '@/stores/userStore';
    import LogisticsJourney from '@/components/LogisticsJourney.vue';

    const userStore = useUserStore();

    const ORDER_STATUSES = [
        { value: 0, label: '待支付' },
        { value: 1, label: '待发货' },
        { value: 2, label: '待揽收' },
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
    const shopJourney = ref<any[]>([]);
    const detailRouteLoading = ref(false);

    const openDetailDialog = async (row: any) => {
        detailVisible.value = true;
        detailData.value = null;
        shopJourney.value = [];
        try {
            const res = await getOrderDetail(row.id);
            detailData.value = res.data;
            const status = res.data?.order?.orderStatus ?? 0;
            if (status >= 2 && status !== 5) {
                detailRouteLoading.value = true;
                try {
                    const journeyRes = await getOrderJourney(row.id);
                    shopJourney.value = journeyRes.data ?? [];
                } catch {
                    shopJourney.value = [];
                } finally {
                    detailRouteLoading.value = false;
                }
            }
        } catch {
            detailVisible.value = false;
        }
    };

    // ==================== 发货弹窗（含仓库选择）====================
    const shipVisible = ref(false);
    const shipLoading = ref(false);
    const shipOrder   = ref<any>(null);
    const shipForm    = ref({ warehouseId: null as number | null });
    const availableWarehouses = ref<any[]>([]);
    const warehousesLoading = ref(false);

    const openShipDialog = async (order: any) => {
        shipOrder.value = order;
        shipForm.value.warehouseId = null;
        shipVisible.value = true;
        detailVisible.value = false;
        // 加载仓库列表
        warehousesLoading.value = true;
        try {
            if (!shopId.value) {
                availableWarehouses.value = [];
                return;
            }
            const res = await getWarehousesByShop(shopId.value);
            availableWarehouses.value = (res.data?.data ?? res.data ?? []).filter((w: any) => w.status === 1);
        } finally {
            warehousesLoading.value = false;
        }
    };

    const handleConfirmShip = async () => {
        if (!shipOrder.value || !shipForm.value.warehouseId) return;
        shipLoading.value = true;
        try {
            await apiShipOrder(shipOrder.value.id, shipForm.value.warehouseId);
            shipVisible.value = false;
            ElMessage.success('发货成功！已提交配送申请，系统将自动规划路线');
            fetchOrders();
        } catch (e: any) {
            ElMessage.error(typeof e === 'string' ? e : '操作失败，请稍后重试');
        } finally {
            shipLoading.value = false;
        }
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

    .ship-time-tip {
        margin-top: 6px;
        font-size: 12px;
        padding: 5px 10px;
        border-radius: 4px;
        line-height: 1.6;
    }
    .tip-warning { background: #fdf6ec; color: #e6a23c; border: 1px solid #f5dab1; }
    .tip-info    { background: #ecf5ff; color: #409eff; border: 1px solid #b3d8ff; }
    .tip-success { background: #f0f9eb; color: #67c23a; border: 1px solid #c2e7b0; }

    .warehouse-option {
        display: flex;
        flex-direction: column;
        line-height: 1.5;
        padding: 2px 0;
    }
    .w-name { font-weight: 500; }
    .w-addr { font-size: 12px; color: #909399; }

    .map-loading {
        display: flex;
        align-items: center;
        gap: 6px;
        height: 60px;
        justify-content: center;
        color: #909399;
        font-size: 13px;
    }

</style>
