<template>
    <div class="nav-page">
        <el-card shadow="never" class="main-card" v-loading="loadingList">
            <template #header>
                <div class="card-head">
                    <span class="title">末端配送路线</span>
                    <div v-if="inProgress.length" class="head-tools">
                        <span class="hint">路线由系统实时生成</span>
                        <el-select
                            v-model="selectedDeliveryId"
                            placeholder="选择进行中的配送任务"
                            style="width: 220px"
                            @change="loadRoute"
                        >
                            <el-option
                                v-for="d in inProgress"
                                :key="d.id"
                                :label="d.orderId ? `配送任务 #${d.orderId}` : `配送任务 #${d.id}`"
                                :value="d.id"
                            />
                        </el-select>
                    </div>
                </div>
            </template>

            <el-empty v-if="!loadingList && !inProgress.length" description="暂无进行中的配送任务，承接任务后可在此查看计划路线">
                <el-button type="primary" @click="$router.push('/driver/home/deliveries')">去配送任务</el-button>
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
                                {{ routeDetail.orderEndAddress ?? routeDetail.route?.endAddress ?? '-' }}
                            </el-descriptions-item>
                            <el-descriptions-item v-if="routeDetail.route?.estimatedArrivalTime" label="预计到达">
                                {{ formatDate(routeDetail.route.estimatedArrivalTime) }}
                            </el-descriptions-item>
                        </el-descriptions>

                        <!-- 司机视图：展示完整路线（含全部停靠点标记） -->
                        <div class="section-title">路线地图</div>
                        <RouteMap
                            :key="selectedDeliveryId"
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
    import { ref, computed, onMounted } from 'vue';
    import { useRoute } from 'vue-router';
    import { getInProgressDeliveries } from '@/api/driver';
    import { getRouteByOrderId, getRouteByRouteId } from '@/api/logistics';
    import RouteMap from '@/components/RouteMap.vue';

    const route = useRoute();
    const loadingList = ref(false);
    const loadingRoute = ref(false);
    const inProgress = ref<any[]>([]);
    /** 以 delivery.id 作为 select 的唯一标识，避免多停靠任务 orderId=null 的歧义 */
    const selectedDeliveryId = ref<number | null>(null);
    const routeDetail = ref<any>(null);
    const routeError = ref('');

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

    const loadRoute = async () => {
        if (!selectedDeliveryId.value) return;
        const delivery = inProgress.value.find((d: any) => d.id === selectedDeliveryId.value);
        if (!delivery) return;
        routeError.value = '';
        routeDetail.value = null;
        loadingRoute.value = true;
        try {
            let res;
            if (delivery.orderId) {
                // 普通末端路线：按订单 ID 查（支持 stopSequence 等扩展字段）
                res = await getRouteByOrderId(delivery.orderId);
            } else {
                // 多停靠末端任务：orderId=null，改用 routeId 直接查
                res = await getRouteByRouteId(delivery.routeId);
            }
            routeDetail.value = res.data ?? null;
        } catch (e: any) {
            routeError.value = typeof e === 'string' ? e : '加载路线失败';
        } finally {
            loadingRoute.value = false;
        }
    };

    onMounted(async () => {
        loadingList.value = true;
        try {
            const res = await getInProgressDeliveries();
            inProgress.value = (res.data ?? []).filter((d: any) => d.segmentType !== 1);
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
