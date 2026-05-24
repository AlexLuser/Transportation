<template>
    <div class="delivery-manage">

        <el-tabs v-model="activeTab" class="delivery-tabs" @tab-change="handleTabChange">

            <!-- ========== Tab 1: 我的任务 ========== -->
            <el-tab-pane label="我的任务" name="mine">
                <div class="tab-toolbar">
                    <el-select v-model="mineStatusFilter" placeholder="全部状态" clearable class="status-select"
                               @change="() => { minePage = 1; fetchMine(); }">
                        <el-option v-for="s in DELIVERY_STATUSES" :key="s.value" :label="s.label" :value="s.value" />
                    </el-select>
                    <el-button :icon="Refresh" @click="() => { minePage = 1; fetchMine(); }">刷新</el-button>
                </div>

                <el-card shadow="never" class="table-card" v-loading="mineLoading">
                    <el-table :data="mineList" stripe>
                        <el-table-column label="配送单号" prop="id" width="90" align="center" />
                        <el-table-column label="类型" width="100" align="center">
                            <template #default="{ row }">
                                <el-tag v-if="row.segmentType === 1" type="primary" size="small">干线运输</el-tag>
                                <el-tag v-else-if="row.segmentType === 2" type="success" size="small">末端配送</el-tag>
                                <span v-else>-</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="收货人" prop="receiverName" width="110">
                            <template #default="{ row }">
                                <span v-if="row.segmentType === 1" class="text-muted">干线任务</span>
                                <span v-else>{{ row.receiverName || '-' }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="配送地址" prop="deliveryAddress" min-width="180" show-overflow-tooltip />
                        <el-table-column label="状态" width="100">
                            <template #default="{ row }">
                                <el-tag :type="deliveryStatusTag(row.deliveryStatus)" size="small">
                                    {{ deliveryStatusLabel(row.deliveryStatus) }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="接单时间" width="160">
                            <template #default="{ row }">{{ formatDate(row.acceptTime) }}</template>
                        </el-table-column>
                        <el-table-column label="送达时间" width="160">
                            <template #default="{ row }">{{ formatDate(row.deliveryTime) }}</template>
                        </el-table-column>
                        <el-table-column label="操作" width="240" fixed="right">
                            <template #default="{ row }">
                                <el-button size="small" @click="openDetailDialog(row)">详情</el-button>
                                <el-button size="small" type="primary"
                                           v-if="row.deliveryStatus === 1"
                                           @click="handleUpdateStatus(row, 2)">
                                    开始运输
                                </el-button>
                                <!-- 干线司机：到达 Hub -->
                                <el-button size="small" type="warning"
                                           v-if="row.deliveryStatus === 2 && row.segmentType === 1"
                                           @click="handleArriveHub(row)">
                                    到达中转站
                                </el-button>
                                <!-- 末端单订单：确认送达 -->
                                <el-button size="small" type="success"
                                           v-if="row.deliveryStatus === 2 && row.segmentType !== 1 && !(row.segmentType === 2 && !row.orderId)"
                                           @click="handleUpdateStatus(row, 3)">
                                    确认送达
                                </el-button>
                                <!-- 末端多停靠：逐站确认 -->
                                <el-button size="small" type="primary" plain
                                           v-if="row.deliveryStatus === 2 && row.segmentType === 2 && !row.orderId"
                                           @click="openDetailDialog(row)">
                                    逐站送达
                                </el-button>
                                <el-button size="small" type="danger"
                                           v-if="row.deliveryStatus === 1"
                                           @click="openCancelDialog(row)">
                                    取消
                                </el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                    <div class="pagination-bar">
                        <el-pagination
                            v-model:current-page="minePage"
                            v-model:page-size="mineSize"
                            :total="mineTotal"
                            layout="total, prev, pager, next"
                            @current-change="fetchMine"
                        />
                    </div>
                    <el-empty v-if="!mineLoading && mineList.length === 0" description="暂无配送任务" />
                </el-card>
            </el-tab-pane>

            <!-- ========== Tab 2: 预调度任务 ========== -->
            <el-tab-pane name="preDispatched">
                <template #label>
                    <span>
                        预调度任务
                        <el-badge v-if="preDispatchedList.length > 0"
                                  :value="preDispatchedList.length"
                                  type="warning"
                                  style="margin-left:4px;vertical-align:middle" />
                    </span>
                </template>
                <div class="tab-toolbar">
                    <el-button :icon="Refresh" @click="fetchPreDispatched">刷新</el-button>
                    <span class="result-hint">{{ preDispatchedList.length }} 条待生效任务</span>
                </div>

                <el-card shadow="never" class="table-card" v-loading="preDispatchedLoading">
                    <el-alert
                        title="以下末端配送任务已由管理员为您预分配。干线货物到达分拨中心后，任务将自动转入「我的任务」，届时请及时处理。"
                        type="info" show-icon :closable="false" style="margin-bottom:12px" />

                    <el-table :data="preDispatchedList" stripe>
                        <el-table-column label="路线ID" prop="id" width="80" align="center" />
                        <el-table-column label="路线编号" prop="routeNo" width="160" />
                        <el-table-column label="停靠点数" width="80" align="center">
                            <template #default="{ row }">{{ row.stopCount ?? 1 }}</template>
                        </el-table-column>
                        <el-table-column label="目的地" prop="endAddress" min-width="200" show-overflow-tooltip />
                        <el-table-column label="当前状态" width="140" align="center">
                            <template #default>
                                <el-tag type="warning" size="small">等待干线到站</el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="创建时间" width="160">
                            <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
                        </el-table-column>
                    </el-table>

                    <el-empty v-if="!preDispatchedLoading && preDispatchedList.length === 0"
                              description="暂无预调度任务" />
                </el-card>
            </el-tab-pane>
        </el-tabs>

        <!-- ========== 配送详情弹窗 ========== -->
        <el-dialog v-model="detailVisible" title="配送详情" width="600px" align-center>
            <div v-if="detailRow">
                <el-descriptions :column="2" border size="small">
                    <el-descriptions-item label="配送单号">{{ detailRow.id }}</el-descriptions-item>
                    <el-descriptions-item label="关联订单">
                        <span v-if="detailRow.orderId">{{ detailRow.orderId }}</span>
                        <span v-else-if="detailRow.segmentType === 2">多停靠末端任务</span>
                        <span v-else>干线任务</span>
                    </el-descriptions-item>
                    <template v-if="detailRow.segmentType !== 1">
                        <el-descriptions-item label="收货人">{{ detailRow.receiverName || '-' }}</el-descriptions-item>
                        <el-descriptions-item label="收货电话">{{ detailRow.receiverPhone || '-' }}</el-descriptions-item>
                    </template>
                    <el-descriptions-item label="配送地址" :span="2">{{ detailRow.deliveryAddress }}</el-descriptions-item>
                    <el-descriptions-item label="配送状态">
                        <el-tag :type="deliveryStatusTag(detailRow.deliveryStatus)" size="small">
                            {{ deliveryStatusLabel(detailRow.deliveryStatus) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="使用车辆">{{ detailRow.vehicleId ?? '未绑定' }}</el-descriptions-item>
                    <el-descriptions-item label="接单时间">{{ formatDate(detailRow.acceptTime) }}</el-descriptions-item>
                    <el-descriptions-item label="取货时间">{{ formatDate(detailRow.pickupTime) }}</el-descriptions-item>
                    <el-descriptions-item label="送达时间">{{ formatDate(detailRow.deliveryTime) }}</el-descriptions-item>
                    <el-descriptions-item label="取消时间">{{ formatDate(detailRow.cancelTime) }}</el-descriptions-item>
                    <el-descriptions-item v-if="detailRow.cancelReason" label="取消原因" :span="2">
                        {{ detailRow.cancelReason }}
                    </el-descriptions-item>
                    <el-descriptions-item label="备注" :span="2">{{ detailRow.remark || '-' }}</el-descriptions-item>
                </el-descriptions>

                <!-- 多停靠路线：逐站送达列表 -->
                <template v-if="detailRow.segmentType === 2 && detailRow.routeId && detailStops.length > 1">
                    <div class="section-title" style="margin-top:16px">
                        停靠点详情（{{ detailStops.filter((s: any) => s.itemStatus === 2).length }}/{{ detailStops.length }} 已送达）
                    </div>
                    <el-alert v-if="detailRow.deliveryStatus === 2"
                              title="请逐站点击「此站已送达」，全部完成后配送单将自动关闭"
                              type="info" show-icon :closable="false"
                              style="margin-bottom:8px;font-size:12px" />
                    <div v-loading="stopsLoading">
                        <div v-for="stop in detailStops" :key="stop.orderId" class="stop-row">
                            <span class="stop-seq">{{ stop.stopSequence }}</span>
                            <div class="stop-info">
                                <div class="stop-addr">{{ stop.endAddress }}</div>
                                <div class="stop-recv">{{ stop.receiverName }} {{ stop.receiverPhone }}</div>
                            </div>
                            <el-tag v-if="stop.itemStatus === 2" type="success" size="small">已送达</el-tag>
                            <el-button
                                v-else-if="detailRow.deliveryStatus === 2"
                                size="small" type="primary"
                                :loading="completingStop === stop.orderId"
                                @click="handleCompleteStop(detailRow, stop.orderId)"
                            >此站已送达</el-button>
                            <el-tag v-else type="info" size="small">待配送</el-tag>
                        </div>
                    </div>
                </template>
            </div>
            <template #footer>
                <el-button
                    v-if="detailRow && (detailRow.deliveryStatus === 1 || detailRow.deliveryStatus === 2)"
                    type="primary"
                    @click="goNavigation(detailRow.id)"
                >
                    查看计划路线
                </el-button>
                <el-button @click="detailVisible = false">关闭</el-button>
            </template>
        </el-dialog>

        <!-- ========== 取消配送弹窗 ========== -->
        <el-dialog v-model="cancelVisible" title="取消配送" width="400px" align-center>
            <el-form label-width="80px">
                <el-form-item label="取消原因">
                    <el-input v-model="cancelReason" type="textarea" :rows="3" placeholder="请输入取消原因" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="cancelVisible = false">取消</el-button>
                <el-button type="danger" :loading="cancelSubmitting" @click="handleCancel">确认取消</el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts" name="DriverDelivery">
    import { ref, onMounted } from 'vue';
    import { useRouter } from 'vue-router';
    import { ElMessage, ElMessageBox } from 'element-plus';
    import { Refresh } from '@element-plus/icons-vue';
    import {
        getMyDeliveries, getMyDriverInfo,
        updateDeliveryStatus, cancelDelivery,
        completeDeliveryStop, getRouteStops,
    } from '@/api/driver';
    import { arriveAtHub, getPreDispatchedRoutes } from '@/api/logistics';

    const router = useRouter();
    const goNavigation = (deliveryId: number) => {
        detailVisible.value = false;
        router.push({ path: '/driver/home/navigation', query: { deliveryId: String(deliveryId) } });
    };

    const DELIVERY_STATUSES = [
        { value: 1, label: '已接单' }, { value: 2, label: '运输中' },
        { value: 3, label: '已送达' }, { value: 4, label: '已取消' },
    ];

    const activeTab = ref('mine');

    // ==================== 我的任务 ====================
    const mineLoading = ref(false);
    const mineList = ref<any[]>([]);
    const minePage = ref(1);
    const mineSize = ref(10);
    const mineTotal = ref(0);
    const mineStatusFilter = ref<number | null>(null);

    const fetchMine = async () => {
        mineLoading.value = true;
        try {
            const res = await getMyDeliveries({
                current: minePage.value, size: mineSize.value,
                status: mineStatusFilter.value ?? undefined,
            });
            mineList.value = res.data?.records ?? [];
            mineTotal.value = res.data?.total ?? 0;
        } finally {
            mineLoading.value = false;
        }
    };

    // ==================== 预调度任务 ====================
    const preDispatchedLoading = ref(false);
    const preDispatchedList = ref<any[]>([]);
    let myDriverId: number | null = null;

    const fetchPreDispatched = async () => {
        preDispatchedLoading.value = true;
        try {
            if (!myDriverId) {
                const infoRes = await getMyDriverInfo();
                console.log('[DEBUG][fetchPreDispatched] getMyDriverInfo:', infoRes.data);
                myDriverId = infoRes.data?.id ?? null;
            }
            console.log('[DEBUG][fetchPreDispatched] myDriverId=', myDriverId);
            if (myDriverId) {
                const res = await getPreDispatchedRoutes(myDriverId);
                console.log('[DEBUG][fetchPreDispatched] getPreDispatchedRoutes 结果:', res);
                preDispatchedList.value = res.data ?? [];
            }
        } catch (err: any) {
            console.error('[DEBUG][fetchPreDispatched] 失败:', err?.response?.data ?? err);
            ElMessage.error('预调度任务加载失败：' + (err?.response?.data?.message ?? err?.message ?? '未知错误'));
        } finally {
            preDispatchedLoading.value = false;
        }
    };

    const handleTabChange = (tab: string) => {
        if (tab === 'mine') fetchMine();
        else if (tab === 'preDispatched') fetchPreDispatched();
    };

    // ==================== 更新状态 ====================
    const handleUpdateStatus = async (row: any, status: number) => {
        const label = status === 2 ? '开始运输' : '确认送达';
        await ElMessageBox.confirm(`确认执行「${label}」操作？`, '操作确认', { type: 'info' });
        await updateDeliveryStatus(row.id, status);
        ElMessage.success(`操作成功：${label}`);
        fetchMine();
    };

    // ==================== 到达中转站（干线司机专用）====================
    const handleArriveHub = async (row: any) => {
        await ElMessageBox.confirm(
            `确认已到达分拨中心？\n确认后系统将自动激活本批次末端配送任务，末端司机将在「我的任务」中收到通知。`,
            '确认到达分拨中心', { type: 'warning', confirmButtonText: '确认到达' }
        );
        try {
            console.log('[DEBUG][handleArriveHub] 调用 arriveAtHub, deliveryId=', row.id, 'batchId=', row.batchId);
            const res = await arriveAtHub(row.id);
            console.log('[DEBUG][handleArriveHub] 返回:', res);
            ElMessage.success('已确认到达分拨中心，末端配送任务正在生成...');
            fetchMine();
        } catch (e: any) {
            console.error('[DEBUG][handleArriveHub] 失败:', e?.response?.data ?? e);
            ElMessage.error(e?.response?.data?.message || '操作失败');
        }
    };

    // ==================== 详情弹窗 ====================
    const detailVisible = ref(false);
    const detailRow = ref<any>(null);
    const detailStops = ref<any[]>([]);
    const stopsLoading = ref(false);
    const completingStop = ref<number | null>(null);

    const openDetailDialog = async (row: any) => {
        detailRow.value = row;
        detailStops.value = [];
        detailVisible.value = true;
        if (row.segmentType === 2 && row.routeId) {
            stopsLoading.value = true;
            try {
                const res = await getRouteStops(row.routeId);
                detailStops.value = res.data ?? [];
            } catch { /* 非致命 */ } finally {
                stopsLoading.value = false;
            }
        }
    };

    const handleCompleteStop = async (delivery: any, orderId: number) => {
        completingStop.value = orderId;
        try {
            const res = await completeDeliveryStop(delivery.id, orderId);
            const msg: string = res.data ?? '本站已送达';
            ElMessage.success(msg);
            const stopsRes = await getRouteStops(delivery.routeId);
            detailStops.value = stopsRes.data ?? [];
            if (msg.includes('配送单已自动关闭')) {
                detailVisible.value = false;
                fetchMine();
            }
        } catch {
            ElMessage.error('操作失败，请重试');
        } finally {
            completingStop.value = null;
        }
    };

    // ==================== 取消弹窗 ====================
    const cancelVisible = ref(false);
    const cancelRow = ref<any>(null);
    const cancelReason = ref('');
    const cancelSubmitting = ref(false);

    const openCancelDialog = (row: any) => { cancelRow.value = row; cancelReason.value = ''; cancelVisible.value = true; };

    const handleCancel = async () => {
        if (!cancelReason.value.trim()) { ElMessage.warning('请填写取消原因'); return; }
        cancelSubmitting.value = true;
        try {
            await cancelDelivery(cancelRow.value.id, cancelReason.value.trim());
            ElMessage.success('配送已取消');
            cancelVisible.value = false;
            fetchMine();
        } finally {
            cancelSubmitting.value = false;
        }
    };

    // ==================== 工具函数 ====================
    const deliveryStatusLabel = (s: number) =>
        ({ 1: '已接单', 2: '运输中', 3: '已送达', 4: '已取消' } as any)[s] ?? '-';
    const deliveryStatusTag = (s: number) =>
        ({ 1: '', 2: 'primary', 3: 'success', 4: 'danger' } as any)[s] ?? 'info';
    const formatDate = (d: string | null | undefined) =>
        d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : '-';

    onMounted(() => { fetchMine(); fetchPreDispatched(); });
</script>

<style scoped>
    .delivery-manage { display: flex; flex-direction: column; }

    .delivery-tabs { background: transparent; }

    :deep(.el-tabs__header) {
        background: #fff;
        border-radius: 8px 8px 0 0;
        padding: 0 20px;
        margin-bottom: 0;
        border-bottom: 1px solid #ebeef5;
    }

    .tab-toolbar {
        display: flex; align-items: center; gap: 10px;
        background: #fff; padding: 12px 20px;
        border: 1px solid #ebeef5;
        border-top: none;
    }

    .status-select { width: 120px; }
    .result-hint { font-size: 13px; color: #606266; margin-left: auto; }

    .table-card {
        border-radius: 0 0 8px 8px;
        border-top: none;
    }

    .pagination-bar { display: flex; justify-content: flex-end; padding: 16px 0 4px; }

    .text-muted { color: #c0c4cc; font-size: 12px; }

    /* 详情弹窗中的逐站列表 */
    .section-title { font-weight: 600; font-size: 13px; color: #303133; }
    .stop-row {
        display: flex; align-items: center; gap: 10px;
        padding: 8px 4px; border-bottom: 1px solid #f0f0f0;
    }
    .stop-row:last-child { border-bottom: none; }
    .stop-seq {
        background: #409eff; color: #fff; border-radius: 50%;
        width: 22px; height: 22px; display: flex; align-items: center;
        justify-content: center; font-size: 12px; flex-shrink: 0;
    }
    .stop-info { flex: 1; min-width: 0; }
    .stop-addr { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .stop-recv { font-size: 12px; color: #909399; }
</style>
