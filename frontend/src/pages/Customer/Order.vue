<template>
    <div class="order-container" v-loading="loading">
        <div class="order-header-bar">
            <el-tabs v-model="activeTab" style="flex:1">
                <el-tab-pane label="全部订单" name="all"/>
                <el-tab-pane label="待支付" name="0"/>
                <el-tab-pane label="待发货" name="1"/>
                <el-tab-pane label="待揽件" name="2"/>
                <el-tab-pane label="派送中" name="3"/>
                <el-tab-pane label="待签收" name="6"/>
                <el-tab-pane label="已完成" name="4"/>
                <el-tab-pane label="已取消" name="5"/>
            </el-tabs>
            <el-button :icon="Refresh" size="small" :loading="loading" @click="fetchOrders" style="margin-bottom:4px">刷新</el-button>
        </div>
        <div class="order-list">
            <el-empty v-if="filteredOrders.length === 0" description="暂无订单" class="empty-state" />
            <template v-else>
                <div class="order-item" v-for="order in filteredOrders" :key="order.id">
                    <div class="order-header">
                        <div class="order-info">
                            <span class="order-number">订单号：{{ order.orderNo }}</span>
                            <span class="order-time">下单时间：{{ formatDate(order.createTime) }}</span>
                        </div>
                        <div class="order-status">
                            <el-tag :type="orderStatusTagType(order.orderStatus)">{{ orderStatusText(order.orderStatus) }}</el-tag>
                        </div>
                    </div>
                    <div class="order-body">
                        <div class="amount-details">
                            <span>商品金额：¥{{ order.productAmount }}</span>
                            <span>运费：¥{{ order.shippingFee }}</span>
                        </div>
                        <div class="amount-total">
                            合计：<span class="total-value">¥{{ order.totalAmount }}</span>
                        </div>
                    </div>
                    <div class="order-footer">
                        <template v-if="order.orderStatus === 0">
                            <el-button type="primary" @click="handlePay(order.id)">立即支付</el-button>
                            <el-button type="danger" plain @click="handleCancel(order.id)">取消订单</el-button>
                        </template>
                        <template v-else-if="order.orderStatus === 1">
                            <el-button type="danger" plain @click="handleCancel(order.id)">取消订单</el-button>
                        </template>
                        <template v-else-if="order.orderStatus === 6">
                            <el-button type="success" @click="handleSign(order.id)">确认签收</el-button>
                        </template>
                        <span v-else-if="order.orderStatus === 4" class="complete-time">
                            完成时间：{{ formatDate(order.completeTime) }}
                        </span>
                        <span v-else-if="order.orderStatus === 5" class="cancel-reason">
                            取消原因：{{ order.cancelReason || '用户主动取消' }}
                        </span>
                        <el-button plain size="small" @click="openDetail(order)">查看详情</el-button>
                    </div>
                </div>
            </template>
        </div>

        <!-- 订单详情弹窗 -->
        <el-dialog
            v-model="detailVisible"
            title="订单详情"
            width="680px"
            :close-on-click-modal="false"
            destroy-on-close
        >
            <div v-if="currentDetail" v-loading="detailLoading" class="detail-body">
                <!-- 基本信息 -->
                <div class="detail-section">
                    <div class="section-title">基本信息</div>
                    <el-descriptions :column="2" border size="small">
                        <el-descriptions-item label="订单号">{{ currentDetail.order?.orderNo }}</el-descriptions-item>
                        <el-descriptions-item label="订单状态">
                            <el-tag :type="orderStatusTagType(currentDetail.order?.orderStatus)">
                                {{ orderStatusText(currentDetail.order?.orderStatus) }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="下单时间">{{ formatDate(currentDetail.order?.createTime) }}</el-descriptions-item>
                        <el-descriptions-item label="支付时间">{{ formatDate(currentDetail.order?.paymentTime) }}</el-descriptions-item>
                        <el-descriptions-item label="商品金额">¥{{ currentDetail.order?.productAmount }}</el-descriptions-item>
                        <el-descriptions-item label="运费">¥{{ currentDetail.order?.shippingFee }}</el-descriptions-item>
                        <el-descriptions-item label="订单总额" :span="2">
                            <span class="detail-total">¥{{ currentDetail.order?.totalAmount }}</span>
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentDetail.order?.remark" label="备注" :span="2">
                            {{ currentDetail.order.remark }}
                        </el-descriptions-item>
                        <el-descriptions-item v-if="currentDetail.order?.orderStatus === 5" label="取消原因" :span="2">
                            {{ currentDetail.order.cancelReason || '用户主动取消' }}
                        </el-descriptions-item>
                    </el-descriptions>
                </div>

                <!-- 商品列表 -->
                <div class="detail-section">
                    <div class="section-title">商品清单</div>
                    <el-table :data="currentDetail.items ?? []" border size="small">
                        <el-table-column label="商品图片" width="80" align="center">
                            <template #default="{ row }">
                                <el-image
                                    v-if="row.productImage"
                                    :src="row.productImage"
                                    style="width: 50px; height: 50px; object-fit: cover;"
                                    fit="cover"
                                />
                                <span v-else class="no-image">无图</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="productName" label="商品名称" min-width="140" />
                        <el-table-column prop="productPrice" label="单价" width="90" align="right">
                            <template #default="{ row }">¥{{ row.productPrice }}</template>
                        </el-table-column>
                        <el-table-column prop="quantity" label="数量" width="70" align="center" />
                        <el-table-column prop="subtotal" label="小计" width="100" align="right">
                            <template #default="{ row }">¥{{ row.subtotal }}</template>
                        </el-table-column>
                    </el-table>
                </div>

                <!-- 物流信息：全程追踪时间线 -->
                <div
                    v-if="currentDetail.order?.orderStatus >= 2"
                    class="detail-section"
                >
                    <div class="section-title">物流全程追踪</div>
                    <el-empty
                        v-if="currentDetail.order?.orderStatus === 2 && (!orderJourney || orderJourney.length === 0)"
                        description="订单待揽件，物流信息将在发货后更新"
                        :image-size="60"
                    />
                    <LogisticsJourney
                        v-else
                        :segments="orderJourney ?? []"
                        :receiver-address="currentDetail.order?.receiverAddress || currentDetail.address?.detailAddress"
                    />
                </div>
            </div>

            <template #footer>
                <div class="detail-footer">
                    <el-button
                        v-if="currentDetail?.order?.orderStatus === 5"
                        type="danger"
                        plain
                        @click="handleDelete(currentDetail.order.id)"
                    >删除订单</el-button>
                    <el-button @click="detailVisible = false">关闭</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts" name="CustomerOrder">
    import { ref, computed, onMounted, watch } from 'vue';
    import { getCustomerOrders, cancelOrder, payOrder, getOrderDetail, deleteOrder, signOrder } from '@/api/order';
    import { getOrderJourney } from '@/api/logistics';
    import { ElMessage, ElMessageBox } from 'element-plus';
    import { Refresh } from '@element-plus/icons-vue';
    import LogisticsJourney from '@/components/LogisticsJourney.vue';

    const loading = ref(false);
    const orders = ref<any[]>([]);
    const activeTab = ref('all');

    const detailVisible = ref(false);
    const detailLoading = ref(false);
    const currentDetail = ref<any>(null);
    const orderJourney  = ref<any[]>([]);

    const filteredOrders = computed(() => {
        if (activeTab.value === 'all') return orders.value;
        return orders.value.filter(o => String(o.orderStatus) === activeTab.value);
    });

    const fetchOrders = async () => {
        loading.value = true;
        try {
            const res = await getCustomerOrders();
            orders.value = res.data ?? [];
        } finally {
            loading.value = false;
        }
    };

    const openDetail = async (order: any) => {
        detailVisible.value = true;
        detailLoading.value = true;
        currentDetail.value = null;
        orderJourney.value = [];
        try {
            const res = await getOrderDetail(order.id);
            currentDetail.value = res.data;

            // status >= 2（待揽件及之后）尝试拉取全程物流追踪
            if (order.orderStatus >= 2) {
                try {
                    const journeyRes = await getOrderJourney(order.id);
                    orderJourney.value = journeyRes.data ?? [];
                } catch {
                    // 物流信息不存在时不报错
                }
            }
        } catch {
            ElMessage.error('获取订单详情失败');
            detailVisible.value = false;
        } finally {
            detailLoading.value = false;
        }
    };

    const handleDelete = async (orderId: number) => {
        try {
            await ElMessageBox.confirm(
                '删除后该订单将不再显示，确认删除？',
                '删除订单',
                { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
            );
            await deleteOrder(orderId);
            ElMessage.success('订单已删除');
            detailVisible.value = false;
            fetchOrders();
        } catch {
            // 用户取消
        }
    };

    const orderStatusTagType = (status: number) => {
        const map: Record<number, string> = {
            0: 'warning',
            1: 'primary',
            2: 'info',
            3: 'primary',
            6: 'warning',
            4: 'success',
            5: 'danger',
        };
        return map[status] ?? 'info';
    };

    const orderStatusText = (status: number) => {
        const map: Record<number, string> = {
            0: '待支付',
            1: '待发货',
            2: '待揽件',
            3: '派送中',
            6: '待签收',
            4: '已完成',
            5: '已取消',
        };
        return map[status] ?? '未知';
    };

    const handleSign = async (orderId: number) => {
        try {
            await ElMessageBox.confirm(
                '确认已收到商品？确认后订单将标记为已完成。',
                '确认签收',
                { confirmButtonText: '确认签收', cancelButtonText: '取消', type: 'success' }
            );
            await signOrder(orderId);
            ElMessage.success('签收成功，感谢您的购买！');
            fetchOrders();
        } catch {
            // 用户取消
        }
    };

    const routeStatusType = (status: number) => {
        const map: Record<number, string> = { 0: 'info', 1: 'primary', 2: 'success', 3: 'danger' };
        return map[status] ?? 'info';
    };

    const routeStatusText = (status: number) => {
        const map: Record<number, string> = { 0: '待出发', 1: '运输中', 2: '已送达', 3: '运输异常' };
        return map[status] ?? '未知';
    };

    const formatDate = (date: string) => {
        if (!date) return '-';
        return new Date(date).toLocaleString('zh-CN', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
        });
    };

    const handlePay = async (orderId: number) => {
        try {
            await ElMessageBox.confirm('确认支付该订单？', '支付确认', {
                confirmButtonText: '确认支付',
                cancelButtonText: '取消',
                type: 'warning'
            });
            await payOrder(orderId);
            ElMessage.success('支付成功');
            fetchOrders();
        } catch {
            // 用户取消弹窗，不处理
        }
    };

    const handleCancel = async (orderId: number) => {
        try {
            const { value: reason } = await ElMessageBox.prompt(
                '请输入取消原因（选填）',
                '取消订单',
                {
                    confirmButtonText: '确认取消',
                    cancelButtonText: '返回',
                    inputPlaceholder: '请输入取消原因',
                    inputType: 'textarea',
                    confirmButtonClass: 'el-button--danger',
                }
            );
            await cancelOrder(orderId, reason ?? '');
            ElMessage.success('订单已取消');
            fetchOrders();
        } catch {
            // 用户点击"返回"，不处理
        }
    };

    onMounted(() => {
        fetchOrders();
    });
</script>

<style scoped>
    .order-container {
        display: flex;
        flex-direction: column;
        gap: 16px;
    }

    .order-header-bar {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: 8px;
    }

    .order-list {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .empty-state {
        padding: 60px 0;
        background: #fff;
        border-radius: 8px;
    }

    /* 订单卡片 */
    .order-item {
        background: #fff;
        border-radius: 8px;
        border: 1px solid #e4e7ed;
        overflow: hidden;
        transition: box-shadow 0.2s;
    }

    .order-item:hover {
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    }

    /* 卡片头部 */
    .order-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 12px 16px;
        background: #f8f9fa;
        border-bottom: 1px solid #e4e7ed;
    }

    .order-info {
        display: flex;
        align-items: center;
        gap: 16px;
    }

    .order-number {
        font-size: 13px;
        color: #606266;
        font-weight: 500;
    }

    .order-time {
        font-size: 12px;
        color: #909399;
    }

    /* 卡片中部 */
    .order-body {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 16px;
        border-bottom: 1px solid #f0f2f5;
    }

    .amount-details {
        display: flex;
        gap: 24px;
        font-size: 13px;
        color: #909399;
    }

    .amount-total {
        font-size: 13px;
        color: #606266;
    }

    .amount-total .total-value {
        font-size: 20px;
        font-weight: bold;
        color: #f56c6c;
        margin-left: 4px;
    }

    /* 卡片底部 */
    .order-footer {
        display: flex;
        justify-content: flex-end;
        align-items: center;
        padding: 10px 16px;
        gap: 8px;
        min-height: 52px;
    }

    .cancel-reason {
        font-size: 12px;
        color: #909399;
    }

    .complete-time {
        font-size: 12px;
        color: #67c23a;
    }

    /* 详情弹窗 */
    .detail-body {
        display: flex;
        flex-direction: column;
        gap: 20px;
        max-height: 65vh;
        overflow-y: auto;
        padding-right: 4px;
    }

    .detail-section {
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    .section-title {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
        padding-left: 8px;
        border-left: 3px solid #409eff;
    }

    .detail-total {
        font-size: 18px;
        font-weight: bold;
        color: #f56c6c;
    }

    .no-image {
        font-size: 11px;
        color: #c0c4cc;
    }

    .logistics-info {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .detail-footer {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
    }
</style>
