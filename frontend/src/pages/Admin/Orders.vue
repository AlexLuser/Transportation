<template>
    <div class="admin-orders">

        <!-- 搜索栏 -->
        <div class="toolbar">
            <div class="toolbar-left">
                <!-- 搜索模式切换 -->
                <el-select v-model="searchMode" class="mode-select" @change="handleModeChange">
                    <el-option label="按订单号" value="orderNo" />
                    <el-option label="按商户ID" value="shopId" />
                </el-select>

                <el-input
                    v-model="searchInput"
                    :placeholder="searchMode === 'orderNo' ? '输入订单号' : '输入商户 ID'"
                    clearable
                    class="search-input"
                    @keyup.enter="handleSearch"
                    @clear="handleClear"
                />

                <el-select v-model="statusFilter" placeholder="全部状态" clearable class="status-select" @change="handleStatusChange">
                    <el-option v-for="s in ORDER_STATUSES" :key="s.value" :label="s.label" :value="s.value" />
                </el-select>

                <el-button type="primary" :icon="Search" @click="handleSearch" :loading="loading">查询</el-button>
                <el-button :icon="Refresh" @click="handleReset" :loading="loading">重置</el-button>
            </div>
            <span class="result-hint">共 <strong>{{ total }}</strong> 条订单</span>
        </div>

        <!-- 订单表格 -->
        <el-card shadow="never" class="table-card">
            <el-table :data="orders" v-loading="loading" stripe>
                <el-table-column label="订单号" prop="orderNo" min-width="190" show-overflow-tooltip />
                <el-table-column label="顾客ID" prop="customerId" width="90" align="center" />
                <el-table-column label="商户ID" prop="shopId" width="90" align="center" />
                <el-table-column label="商品金额" width="110">
                    <template #default="{ row }">
                        <span class="price-text">¥{{ row.productAmount }}</span>
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
                <el-table-column label="操作" width="90" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" @click="openDetail(row)">详情</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页（仅全量加载模式显示） -->
            <div class="pagination-bar" v-if="showPagination">
                <el-pagination
                    v-model:current-page="currentPage"
                    v-model:page-size="pageSize"
                    :page-sizes="[15, 30, 50]"
                    :total="total"
                    layout="total, sizes, prev, pager, next"
                    @current-change="fetchAllOrders"
                    @size-change="(val: number) => { pageSize = val; currentPage = 1; fetchAllOrders(); }"
                />
            </div>

            <el-empty v-if="!loading && orders.length === 0" description="暂无订单数据" :image-size="80" />
        </el-card>

        <!-- 订单详情弹窗 -->
        <el-dialog v-model="detailVisible" title="订单详情" width="700px" align-center>
            <div v-if="detailData" class="detail-body">
                <el-descriptions title="基本信息" :column="2" border size="small">
                    <el-descriptions-item label="订单号" :span="2">{{ detailData.order.orderNo }}</el-descriptions-item>
                    <el-descriptions-item label="顾客ID">{{ detailData.order.customerId }}</el-descriptions-item>
                    <el-descriptions-item label="商户ID">{{ detailData.order.shopId }}</el-descriptions-item>
                    <el-descriptions-item label="商品金额">¥{{ detailData.order.productAmount }}</el-descriptions-item>
                    <el-descriptions-item label="运费">¥{{ detailData.order.shippingFee ?? '0.00' }}</el-descriptions-item>
                    <el-descriptions-item label="总金额">¥{{ detailData.order.totalAmount }}</el-descriptions-item>
                    <el-descriptions-item label="订单状态">
                        <el-tag :type="orderStatusTag(detailData.order.orderStatus)" size="small">
                            {{ orderStatusLabel(detailData.order.orderStatus) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="支付时间">{{ formatDate(detailData.order.paymentTime) }}</el-descriptions-item>
                    <el-descriptions-item label="发货时间">{{ formatDate(detailData.order.shippingTime) }}</el-descriptions-item>
                    <el-descriptions-item label="备注" :span="2">{{ detailData.order.remark || '-' }}</el-descriptions-item>
                    <el-descriptions-item v-if="detailData.order.cancelReason" label="取消原因" :span="2">
                        {{ detailData.order.cancelReason }}
                    </el-descriptions-item>
                </el-descriptions>
                <div class="items-section">
                    <div class="section-title">商品明细</div>
                    <el-table :data="detailData.items" border size="small">
                        <el-table-column label="商品名称" prop="productName" min-width="160" show-overflow-tooltip />
                        <el-table-column label="单价" width="100">
                            <template #default="{ row }">¥{{ row.productPrice }}</template>
                        </el-table-column>
                        <el-table-column label="数量" prop="quantity" width="80" align="center" />
                        <el-table-column label="小计" width="110">
                            <template #default="{ row }"><span class="price-text">¥{{ row.subtotal }}</span></template>
                        </el-table-column>
                    </el-table>
                </div>
            </div>
            <template #footer>
                <el-button @click="detailVisible = false">关闭</el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts" name="AdminOrders">
    import { ref, onMounted } from 'vue';
    import { Search, Refresh } from '@element-plus/icons-vue';
    import { getAllOrders, getShopOrders, getOrderByNo, getOrderDetail } from '@/api/order';

    const ORDER_STATUSES = [
        { value: 0, label: '待支付' },
        { value: 1, label: '待发货' },
        { value: 2, label: '待揽件' },
        { value: 3, label: '派送中' },
        { value: 4, label: '已完成' },
        { value: 5, label: '已取消' },
    ];

    const loading = ref(false);
    const orders = ref<any[]>([]);
    const total = ref(0);
    const currentPage = ref(1);
    const pageSize = ref(15);

    // 搜索状态
    const searchMode = ref<'orderNo' | 'shopId'>('orderNo');
    const searchInput = ref('');
    const statusFilter = ref<number | null>(null);
    // 当前是否处于"全量分页"模式（未做特定搜索时）
    const showPagination = ref(true);

    // ==================== 数据加载 ====================

    /** 加载全部订单（分页） */
    const fetchAllOrders = async () => {
        loading.value = true;
        showPagination.value = true;
        try {
            const params: any = { current: currentPage.value, size: pageSize.value };
            if (statusFilter.value !== null) params.status = statusFilter.value;
            const res = await getAllOrders(params);
            orders.value = res.data?.records ?? [];
            total.value = res.data?.total ?? 0;
        } finally {
            loading.value = false;
        }
    };

    /** 按商户ID搜索 */
    const fetchByShopId = async (shopId: number) => {
        loading.value = true;
        showPagination.value = false;
        try {
            const res = await getShopOrders(shopId);
            let list: any[] = res.data ?? [];
            if (statusFilter.value !== null) {
                list = list.filter((o: any) => o.orderStatus === statusFilter.value);
            }
            orders.value = list;
            total.value = list.length;
        } finally {
            loading.value = false;
        }
    };

    /** 按订单号搜索 */
    const fetchByOrderNo = async (orderNo: string) => {
        loading.value = true;
        showPagination.value = false;
        try {
            const res = await getOrderByNo(orderNo);
            orders.value = res.data ? [res.data] : [];
            total.value = orders.value.length;
        } finally {
            loading.value = false;
        }
    };

    // ==================== 交互处理 ====================

    const handleSearch = () => {
        const val = searchInput.value.trim();
        if (!val) {
            // 无搜索词：回到全量模式
            currentPage.value = 1;
            fetchAllOrders();
            return;
        }
        if (searchMode.value === 'shopId') {
            const id = Number(val);
            if (!id || id <= 0) return;
            fetchByShopId(id);
        } else {
            fetchByOrderNo(val);
        }
    };

    const handleClear = () => {
        currentPage.value = 1;
        fetchAllOrders();
    };

    const handleModeChange = () => {
        searchInput.value = '';
        currentPage.value = 1;
        fetchAllOrders();
    };

    const handleStatusChange = () => {
        const val = searchInput.value.trim();
        if (!val) {
            currentPage.value = 1;
            fetchAllOrders();
        } else {
            handleSearch();
        }
    };

    const handleReset = () => {
        searchInput.value = '';
        statusFilter.value = null;
        currentPage.value = 1;
        fetchAllOrders();
    };

    // ==================== 详情弹窗 ====================

    const detailVisible = ref(false);
    const detailData = ref<any>(null);

    const openDetail = async (row: any) => {
        detailVisible.value = true;
        detailData.value = null;
        try {
            const res = await getOrderDetail(row.id);
            detailData.value = res.data;
        } catch { detailVisible.value = false; }
    };

    // ==================== 工具函数 ====================

    const orderStatusLabel = (s: number) => ORDER_STATUSES.find(x => x.value === s)?.label ?? '-';
    const orderStatusTag = (s: number) =>
        ({ 0: 'warning', 1: '', 2: 'info', 3: 'primary', 4: 'success', 5: 'danger' } as Record<number, string>)[s] ?? '';
    const formatDate = (d: string | null | undefined) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : '-';

    onMounted(() => fetchAllOrders());
</script>

<style scoped>
    .admin-orders { display: flex; flex-direction: column; gap: 16px; }

    .toolbar {
        display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;
        background: #fff; padding: 14px 20px; border-radius: 8px;
        box-shadow: 0 1px 4px rgba(0,0,0,0.08);
    }
    .toolbar-left { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .mode-select { width: 110px; }
    .search-input { width: 200px; }
    .status-select { width: 110px; }
    .result-hint { font-size: 13px; color: #606266; white-space: nowrap; }

    .table-card { border-radius: 8px; }
    .price-text { color: #f56c6c; font-weight: 500; }
    .price-text.bold { font-weight: 700; }
    .pagination-bar { display: flex; justify-content: flex-end; padding: 16px 0 4px; }

    .detail-body { display: flex; flex-direction: column; gap: 20px; }
    .section-title {
        font-size: 14px; font-weight: 600; color: #303133;
        margin-bottom: 10px; padding-left: 8px; border-left: 3px solid #7367f0;
    }
</style>
