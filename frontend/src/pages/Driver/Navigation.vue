<template>
    <div class="nav-page">
        <el-card shadow="never" class="main-card" v-loading="loadingList">
            <template #header>
                <div class="card-head">
                    <span class="title">配送路线</span>
                    <div v-if="inProgress.length" class="head-tools">
                        <span class="hint">路线由系统实时生成</span>
                        <el-select
                            v-model="selectedDeliveryId"
                            placeholder="选择进行中的配送"
                            style="width: 220px"
                            @change="loadRoute"
                        >
                            <el-option
                                v-for="d in inProgress"
                                :key="d.id"
                                :label="d.segmentType === 1 ? `干线任务 #${d.id}` : d.orderId ? `末端配送 #${d.orderId}` : `末端任务 #${d.id}`"
                                :value="d.id"
                            />
                        </el-select>
                    </div>
                </div>
            </template>

            <el-empty v-if="!loadingList && !inProgress.length" description="暂无进行中的配送，接单后可在此查看计划路线">
                <el-button type="primary" @click="$router.push('/driver/home/deliveries')">去配送管理</el-button>
            </el-empty>

            <template v-else-if="selectedDeliveryId">
                <el-alert
                    v-if="routeError"
                    :title="routeError"
                    type="warning"
                    show-icon
                    class="mb-16"
                />
                <div v-loading="loadingRoute">
                    <template v-if="routeDetail">
                        <!-- 多停靠提示 -->
                        <el-alert
                            v-if="routeDetail.totalStops > 1"
                            type="info" show-icon :closable="false"
                            style="margin-bottom:12px"
                        >
                            <template #title>
                                本次派送共 {{ routeDetail.totalStops }} 个停靠点，您正在配送第 {{ routeDetail.stopSequence }} 站的包裹
                            </template>
                        </el-alert>

                        <el-descriptions :column="2" border size="small" class="mb-16">
                            <el-descriptions-item label="物流单号">{{ routeDetail.route?.routeNo ?? '-' }}</el-descriptions-item>
                            <el-descriptions-item label="路线状态">{{ routeDetail.statusDesc ?? '-' }}</el-descriptions-item>
                            <el-descriptions-item label="出发地" :span="2">{{ routeDetail.route?.startAddress ?? '-' }}</el-descriptions-item>
                            <el-descriptions-item label="本单收货地" :span="2">
                                <template v-if="currentStop">
                                    <el-tag type="primary" size="small" style="margin-right:6px">
                                        第 {{ currentStop.stopSequence }} 站
                                    </el-tag>
                                    {{ currentStop.endAddress }}
                                    <span v-if="currentStop.receiverName" style="margin-left:8px;color:#909399;font-size:12px">
                                        {{ currentStop.receiverName }} {{ currentStop.receiverPhone }}
                                    </span>
                                </template>
                                <template v-else>
                                    {{ routeDetail.orderEndAddress ?? routeDetail.route?.endAddress ?? '-' }}
                                </template>
                            </el-descriptions-item>
                            <el-descriptions-item v-if="routeDetail.route?.estimatedArrivalTime" label="预计到达">
                                {{ formatDate(routeDetail.route.estimatedArrivalTime) }}
                            </el-descriptions-item>
                        </el-descriptions>

                        <!-- 司机视图：展示完整路线（含全部停靠点标记） -->
                        <div class="section-title" style="display:flex;align-items:center;gap:10px">
                            路线地图
                            <el-tag v-if="autoRefreshActive" type="success" size="small" effect="plain">
                                自动刷新中
                            </el-tag>
                            <el-button size="small" :loading="refreshingTracks" @click="manualRefreshTracks">
                                刷新轨迹
                            </el-button>
                        </div>
                        <RouteMap
                            :key="`${selectedDeliveryId}-${mapRefreshKey}`"
                            :start-lat="routeDetail.route?.startLatitude"
                            :start-lng="routeDetail.route?.startLongitude"
                            :end-lat="routeDetail.route?.endLatitude"
                            :end-lng="routeDetail.route?.endLongitude"
                            :current-lat="routeDetail.route?.currentLatitude"
                            :current-lng="routeDetail.route?.currentLongitude"
                            :planned-route="routeDetail.route?.plannedRoute"
                            :recent-tracks="routeDetail.recentTracks"
                            :start-label="routeDetail.route?.startAddress"
                            :end-label="routeDetail.route?.endAddress"
                            :extra-end-points="waypointMarkers"
                        />

                        <div v-if="nodes.length" class="section-title">途径节点</div>
                        <el-timeline v-if="nodes.length">
                            <el-timeline-item
                                v-for="n in nodes"
                                :key="n.id"
                                :timestamp="n.plannedArriveTime ? formatDate(n.plannedArriveTime) : `顺序 ${n.sequenceNo}`"
                                placement="top"
                            >
                                <div class="node-name">{{ n.nodeName }}</div>
                                <div class="node-addr">{{ n.nodeAddress }}</div>
                            </el-timeline-item>
                        </el-timeline>
                    </template>
                </div>
            </template>
        </el-card>
    </div>
</template>

<script setup lang="ts">
    import { ref, computed, onMounted, onUnmounted } from 'vue';
    import { useRoute } from 'vue-router';
    import { getInProgressDeliveries, getRouteStops } from '@/api/driver';
    import { getRouteByOrderId, getRouteByRouteId } from '@/api/logistics';
    import RouteMap from '@/components/RouteMap.vue';

    const route = useRoute();
    const loadingList = ref(false);
    const loadingRoute = ref(false);
    const inProgress = ref<any[]>([]);
    /** 以 delivery.id 作为 select 的唯一标识，避免干线任务 orderId=null 的歧义 */
    const selectedDeliveryId = ref<number | null>(null);
    const routeDetail = ref<any>(null);
    const routeError = ref('');

    /** 多停靠路线的各站状态列表（按 stopSequence 升序），用于确定当前派送站点 */
    const routeStops = ref<any[]>([]);

    /**
     * 当前应派送站点：第一个 itemStatus !== 2（未送达）的停靠点。
     * 全部送达时为 null。
     */
    const currentStop = computed(() => {
        if (!routeStops.value.length) return null;
        return [...routeStops.value]
            .sort((a: any, b: any) => (a.stopSequence ?? 0) - (b.stopSequence ?? 0))
            .find((s: any) => s.itemStatus !== 2) ?? null;
    });

    /** 每次轨迹数据刷新后自增，触发 RouteMap 以新 key 重建，保证橙色已走路线同步更新 */
    const mapRefreshKey = ref(0);
    const refreshingTracks = ref(false);
    let trackRefreshTimer: ReturnType<typeof setInterval> | null = null;

    const autoRefreshActive = computed(() =>
        trackRefreshTimer !== null && routeDetail.value?.route?.routeStatus === 1
    );

    const nodes = computed(() => {
        const list = routeDetail.value?.nodes;
        if (!Array.isArray(list)) return [];
        return [...list].sort((a: any, b: any) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0));
    });

    /** 从 waypoints JSON 解析停靠点，作为地图 extraEndPoints 展示（司机需看全程所有停靠点） */
    const waypointMarkers = computed<{ lat: number; lng: number; label: string }[]>(() => {
        const wps = routeDetail.value?.route?.waypoints;
        if (!wps) return [];
        try {
            const parsed = JSON.parse(wps) as Array<{
                seq: number; lat?: number; lng?: number;
                address?: string; receiverName?: string;
            }>;
            return parsed
                .filter(wp => wp.lat && wp.lng)
                .map(wp => ({
                    lat: wp.lat as number,
                    lng: wp.lng as number,
                    label: `第 ${wp.seq} 站 · ${wp.receiverName ?? ''} ${wp.address ?? ''}`,
                }));
        } catch {
            return [];
        }
    });

    const formatDate = (d: string | null | undefined) =>
        d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : '-';

    /** 轻量级轨迹刷新：重新拉取路线详情（含最新 recentTracks），不清空当前地图 */
    /** 多停靠路线时拉取各站状态，供 currentStop 计算当前目标站 */
    const fetchStopsIfNeeded = async (routeId: number | null | undefined) => {
        if (!routeId) { routeStops.value = []; return; }
        const stopCount = routeDetail.value?.route?.stopCount ?? 1;
        if (stopCount <= 1) { routeStops.value = []; return; }
        try {
            const res = await getRouteStops(routeId);
            routeStops.value = res.data ?? [];
        } catch { routeStops.value = []; }
    };

    const fetchAndRefreshTracks = async (silent = false) => {
        if (!selectedDeliveryId.value) return;
        const delivery = inProgress.value.find((d: any) => d.id === selectedDeliveryId.value);
        if (!delivery) return;
        if (!silent) refreshingTracks.value = true;
        try {
            const res = delivery.orderId
                ? await getRouteByOrderId(delivery.orderId)
                : await getRouteByRouteId(delivery.routeId);
            const fresh = res.data ?? null;
            if (fresh) {
                routeDetail.value = fresh;
                mapRefreshKey.value++;   // 强制 RouteMap 用新 key 重建，使橙色轨迹立即更新
                // 同步刷新当前站状态
                await fetchStopsIfNeeded(fresh.route?.id);
            }
            // 若路线已送达则停止轮询
            if (fresh?.route?.routeStatus === 2) stopAutoRefresh();
        } catch { /* 静默失败，不影响已有地图 */ } finally {
            if (!silent) refreshingTracks.value = false;
        }
    };

    const manualRefreshTracks = () => fetchAndRefreshTracks(false);

    const stopAutoRefresh = () => {
        if (trackRefreshTimer) { clearInterval(trackRefreshTimer); trackRefreshTimer = null; }
    };

    const startAutoRefresh = () => {
        stopAutoRefresh();
        // 路线运输中(routeStatus=1)时每 8 秒静默刷新一次轨迹
        trackRefreshTimer = setInterval(() => {
            if (routeDetail.value?.route?.routeStatus === 1) {
                fetchAndRefreshTracks(true);
            } else {
                stopAutoRefresh();
            }
        }, 8000);
    };

    const loadRoute = async () => {
        if (!selectedDeliveryId.value) return;
        const delivery = inProgress.value.find((d: any) => d.id === selectedDeliveryId.value);
        if (!delivery) return;
        routeError.value = '';
        routeDetail.value = null;
        stopAutoRefresh();
        loadingRoute.value = true;
        try {
            let res;
            if (delivery.orderId) {
                // 普通末端路线：按订单 ID 查（支持 stopSequence 等扩展字段）
                res = await getRouteByOrderId(delivery.orderId);
            } else {
                // 干线任务 / 多停靠末端任务：orderId=null，改用 routeId 直接查
                res = await getRouteByRouteId(delivery.routeId);
            }
            routeDetail.value = res.data ?? null;
            mapRefreshKey.value++;
            // 多停靠路线时加载各站状态
            await fetchStopsIfNeeded(routeDetail.value?.route?.id);
            // 路线运输中时启动自动刷新
            if (routeDetail.value?.route?.routeStatus === 1) startAutoRefresh();
        } catch (e: any) {
            routeError.value = typeof e === 'string' ? e : '加载路线失败';
        } finally {
            loadingRoute.value = false;
        }
    };

    onUnmounted(stopAutoRefresh);

    onMounted(async () => {
        loadingList.value = true;
        try {
            const res = await getInProgressDeliveries();
            inProgress.value = res.data ?? [];
            // 优先按 deliveryId 参数预选（Delivery.vue 传过来的）
            const qDid = route.query.deliveryId;
            const qOid = route.query.orderId;
            const dIdNum = qDid != null && qDid !== '' ? Number(qDid) : NaN;
            const oIdNum = qOid != null && qOid !== '' ? Number(qOid) : NaN;
            if (Number.isFinite(dIdNum) && inProgress.value.some((d: any) => d.id === dIdNum)) {
                selectedDeliveryId.value = dIdNum;
            } else if (Number.isFinite(oIdNum)) {
                const match = inProgress.value.find((d: any) => d.orderId === oIdNum);
                if (match) selectedDeliveryId.value = match.id;
            }
            if (!selectedDeliveryId.value && inProgress.value.length) {
                selectedDeliveryId.value = inProgress.value[0].id;
            }
            if (selectedDeliveryId.value) await loadRoute();
        } finally {
            loadingList.value = false;
        }
    });
</script>

<style scoped>
    .nav-page { min-height: 360px; }
    .main-card { border-radius: 8px; }
    .card-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: 12px;
    }
    .title { font-weight: 600; }
    .head-tools { display: flex; align-items: center; gap: 12px; }
    .hint { font-size: 12px; color: #909399; }
    .mb-16 { margin-bottom: 16px; }
    .mt-16 { margin-top: 16px; }
    .section-title { font-weight: 600; margin: 8px 0 12px; }
    .node-name { font-weight: 500; }
    .node-addr { font-size: 13px; color: #606266; margin: 4px 0; }
</style>
