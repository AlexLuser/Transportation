<template>
    <div class="batches-container">
        <el-card>
            <template #header>
                <span>配送批次管理</span>
            </template>

            <el-table :data="batches" stripe v-loading="loading" style="width:100%">
                <el-table-column prop="batchNo" label="批次编号" width="200" />
                <el-table-column prop="totalOrders" label="订单数" width="80" align="center" />
                <el-table-column label="状态" width="120" align="center">
                    <template #default="{ row }">
                        <el-tag :type="batchStatusType(row.batchStatus)" size="small">
                            {{ batchStatusLabel(row.batchStatus) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="配送方式" width="110" align="center">
                    <template #default="{ row }">
                        <el-tag :type="row.useHub ? 'primary' : 'info'" size="small">
                            {{ row.useHub ? '经分拨中心' : '直接送达' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="vrpAlgorithm" label="排线方案" width="160" />
                <el-table-column label="总距离" width="110" align="right">
                    <template #default="{ row }">
                        {{ row.totalDistance ? (row.totalDistance / 1000).toFixed(2) + ' km' : '-' }}
                    </template>
                </el-table-column>
                <el-table-column prop="createTime" label="创建时间" min-width="170" />
                <el-table-column label="操作" width="160" align="center">
                    <template #default="{ row }">
                        <el-button size="small" type="primary" @click="openDetail(row)">详情</el-button>
                        <el-button size="small" type="success" :icon="MapLocation" @click="openBatchMap(row)">地图</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- ══ 批次详情抽屉 ══ -->
        <el-drawer v-model="drawerVisible" :title="'批次详情：' + selectedBatch?.batchNo" size="65%">
            <div v-if="batchDetail" class="detail-content">

                <!-- 分拨中心信息 -->
                <el-descriptions title="分拨中心信息" :column="2" border v-if="batchDetail.hub" class="mb-16">
                    <el-descriptions-item label="名称">{{ batchDetail.hub.name }}</el-descriptions-item>
                    <el-descriptions-item label="区域">{{ batchDetail.hub.region }}</el-descriptions-item>
                    <el-descriptions-item label="地址" :span="2">{{ batchDetail.hub.address }}</el-descriptions-item>
                </el-descriptions>

                <!-- 优化后配送顺序 -->
                <el-card class="mb-16" v-if="batchDetail.items?.length">
                    <template #header><span>优化后配送顺序</span></template>
                    <el-timeline>
                        <el-timeline-item
                            v-for="item in batchDetail.items"
                            :key="item.id"
                            :timestamp="`第 ${item.visitSequence} 站`"
                            placement="top">
                            <p><strong>订单编号：</strong>{{ item.orderId }}</p>
                            <p><strong>地址：</strong>{{ item.endAddress }}</p>
                            <p><strong>收货人：</strong>{{ item.receiverName }} {{ item.receiverPhone }}</p>
                            <el-tag size="small" :type="itemStatusType(item.itemStatus)">
                                {{ itemStatusLabel(item.itemStatus) }}
                            </el-tag>
                        </el-timeline-item>
                    </el-timeline>
                </el-card>

                <!-- 干线路线 -->
                <el-card class="mb-16" v-if="batchDetail.trunkRoute">
                    <template #header>
                        <div class="card-head-row">
                            <span>干线路线（仓库 → 分拨中心）</span>
                            <div class="head-actions">
                                <el-tag type="primary" size="small">干线段</el-tag>
                                <el-button size="small" :icon="MapLocation" type="primary" plain
                                    @click="openRouteMap(batchDetail.trunkRoute, '干线路线')">查看地图</el-button>
                            </div>
                        </div>
                    </template>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="路线编号">{{ batchDetail.trunkRoute.route?.routeNo }}</el-descriptions-item>
                        <el-descriptions-item label="状态">
                            <el-tag :type="routeStatusType(batchDetail.trunkRoute.route?.routeStatus)" size="small">
                                {{ routeStatusLabel(batchDetail.trunkRoute.route?.routeStatus) }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="起点" :span="2">{{ batchDetail.trunkRoute.route?.startAddress }}</el-descriptions-item>
                        <el-descriptions-item label="终点" :span="2">{{ batchDetail.trunkRoute.route?.endAddress }}</el-descriptions-item>
                    </el-descriptions>
                </el-card>

                <!-- 末端配送路线列表 -->
                <el-card v-if="batchDetail.lastMileRoutes?.length">
                    <template #header>
                        <div class="card-head-row">
                            <span>末端配送路线（共 {{ batchDetail.lastMileRoutes.length }} 条，覆盖 {{ batchDetail.batch?.totalOrders }} 单）</span>
                            <div class="head-actions">
                                <el-button size="small" :icon="MapLocation" type="success" plain
                                    @click="openBatchMap(selectedBatch)">全批次地图</el-button>
                            </div>
                        </div>
                    </template>
                    <el-table :data="batchDetail.lastMileRoutes" size="small" stripe>
                        <el-table-column label="路线编号" prop="route.routeNo" min-width="160" />
                        <el-table-column label="分组" width="70" align="center">
                            <template #default="{ row }">
                                <el-tag size="small" type="info">第 {{ (row.route?.groupIndex ?? 0) + 1 }} 组</el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="停靠" width="60" align="center">
                            <template #default="{ row }">
                                <el-tag :type="(row.route?.stopCount ?? 1) > 1 ? 'warning' : 'info'" size="small">
                                    {{ row.route?.stopCount ?? 1 }} 个
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="停靠明细" min-width="200">
                            <template #default="{ row }">
                                <template v-if="row.route?.stopCount > 1 && row.route?.waypoints">
                                    <div v-for="wp in parseWp(row.route.waypoints)" :key="wp.seq" class="wp-row">
                                        <el-tag size="small" type="info" style="margin-right:4px">{{ wp.seq }}</el-tag>
                                        <span class="wp-addr">{{ wp.address }}</span>
                                        <span class="wp-recv">{{ wp.receiverName }}</span>
                                    </div>
                                </template>
                                <span v-else class="text-muted">{{ row.route?.endAddress }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="状态" width="90" align="center">
                            <template #default="{ row }">
                                <el-tag :type="routeStatusType(row.route?.routeStatus)" size="small">
                                    {{ routeStatusLabel(row.route?.routeStatus) }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" width="90" align="center" fixed="right">
                            <template #default="{ row }">
                                <el-button size="small" :icon="MapLocation" type="success" plain
                                    @click="openRouteMap(row, `第 ${(row.route?.groupIndex ?? 0) + 1} 组末端路线`)">
                                    地图
                                </el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-card>
            </div>
            <div v-else class="loading-placeholder">
                <el-skeleton :rows="8" animated />
            </div>
        </el-drawer>

        <!-- ══ 全批次路线地图弹窗 ══ -->
        <el-dialog v-model="batchMapVisible" :title="batchMapTitle" width="860px" align-center destroy-on-close>
            <div v-loading="batchMapLoading" class="map-dialog-body">
                <RouteMap
                    v-if="!batchMapLoading && batchMapData"
                    :start-lat="batchMapData.startLat"
                    :start-lng="batchMapData.startLng"
                    :start-label="batchMapData.startLabel"
                    :hubs="batchMapData.hubs"
                    :segments="batchMapData.segments"
                    :extra-end-points="batchMapData.extraEndPoints"
                    style="height:520px"
                />
                <el-empty v-else-if="!batchMapLoading" description="暂无路线坐标数据" :image-size="80" />
            </div>
            <div class="map-legend">
                <span class="legend-item trunk">━━ 干线（仓库→分拨中心）</span>
                <span class="legend-item last">━━ 末端配送</span>
                <span class="legend-item hub">★ 分拨中心</span>
                <span class="legend-item dest">● 收货点</span>
            </div>
        </el-dialog>

        <!-- ══ 单条路线地图弹窗 ══ -->
        <el-dialog v-model="routeMapVisible" :title="routeMapTitle" width="760px" align-center destroy-on-close>
            <div class="map-dialog-body" v-if="routeMapData">
                <RouteMap
                    :start-lat="routeMapData.startLat"
                    :start-lng="routeMapData.startLng"
                    :start-label="routeMapData.startLabel"
                    :end-lat="routeMapData.endLat"
                    :end-lng="routeMapData.endLng"
                    :end-label="routeMapData.endLabel"
                    :planned-route="routeMapData.plannedRoute"
                    :extra-end-points="routeMapData.waypoints"
                    style="height:460px"
                />
                <!-- 多停靠站点明细 -->
                <div v-if="routeMapData.waypointList?.length" class="waypoint-detail">
                    <div class="section-title">停靠点明细</div>
                    <div v-for="wp in routeMapData.waypointList" :key="wp.seq" class="wp-detail-row">
                        <el-tag type="primary" size="small" class="wp-num">第 {{ wp.seq }} 站</el-tag>
                        <span class="wp-detail-addr">{{ wp.address }}</span>
                        <span class="wp-detail-recv">{{ wp.receiverName }}</span>
                        <span class="wp-detail-phone">{{ wp.receiverPhone }}</span>
                    </div>
                </div>
            </div>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { MapLocation } from '@element-plus/icons-vue';
import { getBatchDetail } from '@/api/logistics';
import request from '@/utils/request';
import RouteMap from '@/components/RouteMap.vue';

// ── 批次列表 ──
const batches = ref<any[]>([]);
const loading = ref(false);

const fetchBatches = async () => {
    loading.value = true;
    try {
        const res = await request({ url: '/logistics/batches', method: 'GET' });
        batches.value = res.data || [];
    } catch {
        ElMessage.error('加载批次列表失败');
    } finally {
        loading.value = false;
    }
};

// ── 批次详情抽屉 ──
const drawerVisible = ref(false);
const selectedBatch = ref<any>(null);
const batchDetail = ref<any>(null);

const openDetail = async (row: any) => {
    selectedBatch.value = row;
    batchDetail.value = null;
    drawerVisible.value = true;
    try {
        const res = await getBatchDetail(row.id);
        batchDetail.value = res.data;
    } catch {
        ElMessage.error('加载批次详情失败');
    }
};

// ── 全批次地图弹窗 ──
const batchMapVisible = ref(false);
const batchMapLoading = ref(false);
const batchMapTitle = ref('');
const batchMapData = ref<any>(null);

const openBatchMap = async (row: any) => {
    if (!row) return;
    batchMapTitle.value = `批次路线总览 — ${row.batchNo ?? row.id}`;
    batchMapVisible.value = true;

    // 若抽屉已加载该批次详情，直接复用；否则重新请求
    if (batchDetail.value && selectedBatch.value?.id === row.id) {
        batchMapData.value = buildBatchMapData(batchDetail.value);
        return;
    }

    batchMapLoading.value = true;
    batchMapData.value = null;
    try {
        const res = await getBatchDetail(row.id);
        batchMapData.value = buildBatchMapData(res.data);
    } catch {
        ElMessage.error('加载路线数据失败');
    } finally {
        batchMapLoading.value = false;
    }
};

/** 将批次详情转为 RouteMap 所需的数据结构 */
const buildBatchMapData = (detail: any) => {
    const trunk  = detail?.trunkRoute?.route;
    const lastMiles: any[] = detail?.lastMileRoutes ?? [];
    const hub    = detail?.hub;

    // 起点：优先干线起点（经 Hub 模式）；直接送达时取末端路线的起点（即仓库）
    const firstLastMileRoute = lastMiles[0]?.route;
    const startLat  = trunk?.startLatitude  ?? firstLastMileRoute?.startLatitude  ?? null;
    const startLng  = trunk?.startLongitude ?? firstLastMileRoute?.startLongitude ?? null;
    const startLabel = trunk?.startAddress  ?? firstLastMileRoute?.startAddress   ?? '仓库';

    // Hub 标记
    const hubs = hub?.latitude && hub?.longitude
        ? [{ lat: hub.latitude, lng: hub.longitude, name: hub.name, address: hub.address }]
        : [];

    // 多段路线：干线（type=1）+ 末端各条（type=2）
    const segments: { plannedRoute: string; type: 1 | 2; label?: string }[] = [];
    if (trunk?.plannedRoute) {
        segments.push({ plannedRoute: trunk.plannedRoute, type: 1, label: '干线' });
    }
    lastMiles.forEach((lm: any, idx: number) => {
        if (lm.route?.plannedRoute) {
            segments.push({ plannedRoute: lm.route.plannedRoute, type: 2, label: `末端 ${idx + 1}` });
        }
    });

    // 收货点：末端路线的所有停靠点（waypoints > 1 时展开，否则取 endLat/endLng）
    const extraEndPoints: { lat: number; lng: number; label: string }[] = [];
    lastMiles.forEach((lm: any, idx: number) => {
        const route = lm.route;
        if (!route) return;
        if (route.stopCount > 1 && route.waypoints) {
            const wps = parseWp(route.waypoints);
            wps.forEach((wp: any) => {
                if (wp.lat && wp.lng) {
                    extraEndPoints.push({ lat: wp.lat, lng: wp.lng, label: `${wp.receiverName} (${wp.address})` });
                }
            });
        } else if (route.endLatitude && route.endLongitude) {
            extraEndPoints.push({
                lat: route.endLatitude, lng: route.endLongitude,
                label: `${route.receiverName ?? ''} ${route.endAddress ?? `末端组 ${idx + 1}`}`,
            });
        }
    });

    return { startLat, startLng, startLabel, hubs, segments, extraEndPoints };
};

// ── 单条路线地图弹窗 ──
const routeMapVisible = ref(false);
const routeMapTitle = ref('');
const routeMapData = ref<any>(null);

const openRouteMap = (routeDetail: any, title: string) => {
    const route = routeDetail?.route ?? routeDetail;
    routeMapTitle.value = title;

    const waypointList = route?.waypoints ? parseWp(route.waypoints) : [];

    // 多停靠：用 extraEndPoints 展示各停靠点（若有 lat/lng）
    const waypoints = waypointList
        .filter((wp: any) => wp.lat && wp.lng)
        .map((wp: any) => ({ lat: wp.lat, lng: wp.lng, label: `${wp.seq}. ${wp.receiverName}` }));

    routeMapData.value = {
        startLat:    route?.startLatitude,
        startLng:    route?.startLongitude,
        startLabel:  route?.startAddress,
        endLat:      route?.endLatitude,
        endLng:      route?.endLongitude,
        endLabel:    route?.endAddress,
        plannedRoute: route?.plannedRoute,
        waypoints,
        waypointList,
    };
    routeMapVisible.value = true;
};

// ── 工具函数 ──
const batchStatusLabel = (s: number) => ({ 0:'待出发', 1:'干线运输中', 2:'已到分拨中心', 3:'配送中', 4:'全部完成' }[s] ?? '未知');
const batchStatusType  = (s: number) => (['info','warning','primary','success','success'][s] ?? 'info') as any;
const itemStatusLabel  = (s: number) => ({ 0:'待激活', 1:'配送中', 2:'已送达' }[s] ?? '未知');
const itemStatusType   = (s: number) => (['warning','primary','success'][s] ?? 'info') as any;
const routeStatusLabel = (s: number) => ({ [-1]:'待激活', 0:'待出发', 1:'运输中', 2:'已送达', 3:'异常' }[s] ?? '未知');
const routeStatusType  = (s: number) => (s === -1 ? 'warning' : s === 0 ? 'info' : s === 1 ? 'primary' : s === 2 ? 'success' : 'danger') as any;

const parseWp = (json: string) => {
    try { return JSON.parse(json) as Array<{seq:number;orderId:number;address:string;receiverName:string;receiverPhone?:string;lat?:number;lng?:number}>; }
    catch { return []; }
};

onMounted(fetchBatches);
</script>

<style scoped>
.batches-container { padding: 4px; }
.detail-content { padding: 8px; display: flex; flex-direction: column; gap: 16px; }
.mb-16 { margin-bottom: 16px; }

/* 卡片头部两端对齐 */
.card-head-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
}
.head-actions { display: flex; align-items: center; gap: 8px; }

.loading-placeholder { padding: 20px; }

/* 停靠点行（抽屉内） */
.wp-row { display: flex; align-items: center; gap: 4px; font-size: 12px; padding: 1px 0; }
.wp-addr { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wp-recv { color: #909399; flex-shrink: 0; }
.text-muted { color: #c0c4cc; font-size: 12px; }

/* 地图弹窗 */
.map-dialog-body {
    border-radius: 8px;
    overflow: hidden;
    min-height: 200px;
}

/* 图例 */
.map-legend {
    display: flex;
    gap: 20px;
    padding: 10px 4px 0;
    font-size: 13px;
    color: #606266;
    flex-wrap: wrap;
}
.legend-item { display: flex; align-items: center; gap: 4px; }
.trunk { color: #1677ff; font-weight: 600; }
.last  { color: #67c23a; font-weight: 600; }
.hub   { color: #e6a23c; font-weight: 600; }
.dest  { color: #f56c6c; font-weight: 600; }

/* 路线弹窗内停靠点明细 */
.section-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    padding: 12px 0 8px;
    border-bottom: 1px solid #ebeef5;
    margin-bottom: 8px;
}
.waypoint-detail { padding: 0 4px; }
.wp-detail-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 0;
    border-bottom: 1px dashed #f0f2f5;
    font-size: 13px;
}
.wp-num { flex-shrink: 0; }
.wp-detail-addr { flex: 1; color: #303133; }
.wp-detail-recv { color: #606266; flex-shrink: 0; }
.wp-detail-phone { color: #909399; flex-shrink: 0; font-size: 12px; }
</style>
