<template>
    <div class="nav-page">
        <el-card shadow="never" class="main-card" v-loading="loadingList">
            <template #header>
                <div class="card-head">
                    <span class="title">配送路线</span>
                    <div v-if="inProgress.length" class="head-tools">
                        <span class="hint">数据来自物流服务</span>
                        <el-select
                            v-model="selectedOrderId"
                            placeholder="选择进行中的订单"
                            style="width: 220px"
                            @change="loadRoute"
                        >
                            <el-option
                                v-for="d in inProgress"
                                :key="d.id"
                                :label="`订单 #${d.orderId}`"
                                :value="d.orderId"
                            />
                        </el-select>
                    </div>
                </div>
            </template>

            <el-empty v-if="!loadingList && !inProgress.length" description="暂无进行中的配送，接单后可在此查看计划路线">
                <el-button type="primary" @click="$router.push('/driver/home/deliveries')">去配送管理</el-button>
            </el-empty>

            <template v-else-if="selectedOrderId">
                <el-alert
                    v-if="routeError"
                    :title="routeError"
                    type="warning"
                    show-icon
                    class="mb-16"
                />
                <div v-loading="loadingRoute">
                    <template v-if="routeDetail">
                        <el-descriptions :column="2" border size="small" class="mb-16">
                            <el-descriptions-item label="物流单号">{{ routeDetail.route?.routeNo ?? '-' }}</el-descriptions-item>
                            <el-descriptions-item label="路线状态">{{ routeDetail.statusDesc ?? '-' }}</el-descriptions-item>
                            <el-descriptions-item label="出发地" :span="2">{{ routeDetail.route?.startAddress ?? '-' }}</el-descriptions-item>
                            <el-descriptions-item label="目的地" :span="2">{{ routeDetail.route?.endAddress ?? '-' }}</el-descriptions-item>
                            <el-descriptions-item v-if="routeDetail.route?.estimatedArrivalTime" label="预计到达">
                                {{ formatDate(routeDetail.route.estimatedArrivalTime) }}
                            </el-descriptions-item>
                        </el-descriptions>

                        <!-- 与顾客端一致：Leaflet + OSM，规划线来自 logistics 存库的 GeoJSON（plannedRoute） -->
                        <div class="section-title">路线地图</div>
                        <RouteMap
                            :key="selectedOrderId"
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
    import { getRouteByOrderId } from '@/api/logistics';
    import RouteMap from '@/components/RouteMap.vue';

    const route = useRoute();
    const loadingList = ref(false);
    const loadingRoute = ref(false);
    const inProgress = ref<any[]>([]);
    const selectedOrderId = ref<number | null>(null);
    const routeDetail = ref<any>(null);
    const routeError = ref('');

    const nodes = computed(() => {
        const list = routeDetail.value?.nodes;
        if (!Array.isArray(list)) return [];
        return [...list].sort((a: any, b: any) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0));
    });

    const formatDate = (d: string | null | undefined) =>
        d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : '-';

    const loadRoute = async () => {
        if (!selectedOrderId.value) return;
        routeError.value = '';
        routeDetail.value = null;
        loadingRoute.value = true;
        try {
            const res = await getRouteByOrderId(selectedOrderId.value);
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
            inProgress.value = res.data ?? [];
            const q = route.query.orderId;
            const qid = q != null && q !== '' ? Number(q) : NaN;
            if (Number.isFinite(qid) && inProgress.value.some((d: any) => d.orderId === qid)) {
                selectedOrderId.value = qid;
            } else if (inProgress.value.length) {
                selectedOrderId.value = inProgress.value[0].orderId;
            }
            if (selectedOrderId.value) await loadRoute();
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
