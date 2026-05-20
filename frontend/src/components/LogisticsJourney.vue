<template>
    <div class="journey-wrap">
        <div v-if="!segments || segments.length === 0" class="journey-empty">
            <el-empty description="暂无物流信息" :image-size="56" />
        </div>
        <template v-else>

            <!-- ① 全程汇总地图（始终展示） -->
            <div class="journey-map-section">
                <div class="journey-map-header">
                    <span class="journey-map-title">🗺️ 全程物流路线</span>
                    <div class="journey-map-legend">
                        <span class="legend-dot" style="background:#67c23a"></span>发货地
                        <span class="legend-line" style="background:#ff7b00"></span>已走路程
                        <span class="legend-line" style="background:#1677ff"></span>预计路线
                        <span class="legend-star">★</span>中转站
                        <span class="legend-dot" style="background:#f56c6c"></span>收货地
                    </div>
                </div>
                <RouteMap
                    :start-lat="mapData.startLat"
                    :start-lng="mapData.startLng"
                    :start-label="mapData.startLabel"
                    :end-lat="mapData.endLat"
                    :end-lng="mapData.endLng"
                    :end-label="mapData.endLabel"
                    :current-lat="mapData.currentLat"
                    :current-lng="mapData.currentLng"
                    :hubs="mapData.hubs"
                    :segments="mapData.routeSegments"
                    :recent-tracks="mapData.recentTracks"
                    :map-height="420"
                />
            </div>

            <!-- ② 物流时间线 -->
            <el-timeline class="journey-timeline">
                <el-timeline-item
                    v-for="(seg, idx) in segments"
                    :key="idx"
                    :color="timelineColor(seg.route?.routeStatus)"
                    :hollow="isWaiting(seg.route?.routeStatus)"
                    size="large"
                    placement="top"
                >
                    <template #default>
                        <div class="seg-card" :class="{ 'seg-active': isActive(seg.route?.routeStatus) }">
                            <!-- 标题行 -->
                            <div class="seg-header">
                                <span class="seg-icon">{{ segmentIcon(seg.route?.segmentType, seg.route?.startAddress) }}</span>
                                <span class="seg-title">{{ segmentTitle(seg) }}</span>
                                <el-tag
                                    :type="statusTagType(seg.route?.routeStatus)"
                                    size="small"
                                    effect="plain"
                                    class="seg-status-tag"
                                >{{ seg.statusDesc || routeStatusText(seg.route?.routeStatus) }}</el-tag>
                            </div>

                            <!-- 起讫地址 -->
                            <div class="seg-route-info">
                                <span class="seg-addr">{{ seg.route?.startAddress }}</span>
                                <span class="seg-arrow">→</span>
                                <span class="seg-addr">{{ seg.orderEndAddress || seg.route?.endAddress }}</span>
                            </div>

                            <!-- 详细信息 -->
                            <div class="seg-meta">
                                <span v-if="seg.driverName" class="meta-item">
                                    🚚 {{ seg.driverName }}
                                    <template v-if="seg.driverPhone">（{{ seg.driverPhone }}）</template>
                                </span>
                                <span v-if="seg.totalStops && seg.totalStops > 1" class="meta-item">
                                    📍 第 {{ seg.stopSequence }} / {{ seg.totalStops }} 站
                                </span>
                                <span v-if="seg.route?.createTime" class="meta-item meta-time">
                                    {{ formatTime(seg.route.createTime) }}
                                </span>
                            </div>
                        </div>
                    </template>
                </el-timeline-item>

                <!-- 末尾：收货地址 -->
                <el-timeline-item
                    v-if="receiverAddress"
                    :color="allDelivered ? '#67c23a' : '#c0c4cc'"
                    :hollow="!allDelivered"
                    size="large"
                    placement="top"
                >
                    <div class="seg-card seg-final">
                        <div class="seg-header">
                            <span class="seg-icon">🏠</span>
                            <span class="seg-title">收货地址</span>
                            <el-tag :type="allDelivered ? 'success' : 'info'" size="small" effect="plain">
                                {{ allDelivered ? '已签收' : '等待收货' }}
                            </el-tag>
                        </div>
                        <div class="seg-route-info">
                            <span class="seg-addr">{{ receiverAddress }}</span>
                        </div>
                    </div>
                </el-timeline-item>
            </el-timeline>
        </template>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import RouteMap from '@/components/RouteMap.vue'

interface RouteInfo {
    id?: number
    routeNo?: string
    segmentType?: number   // 0=普通, 1=干线段, 2=末端段, 3=跨城干线(Hub-Hub)
    routeStatus?: number   // -1=待激活, 0=待出发, 1=运输中, 2=已送达
    startAddress?: string
    startLatitude?: number
    startLongitude?: number
    endAddress?: string
    endLatitude?: number
    endLongitude?: number
    currentLatitude?: number
    currentLongitude?: number
    currentAddress?: string
    plannedRoute?: string
    createTime?: string
    interCityBatchId?: number
}

interface Segment {
    route?: RouteInfo
    statusDesc?: string
    driverName?: string
    driverPhone?: string
    recentTracks?: any[]
    stopSequence?: number
    totalStops?: number
    orderEndAddress?: string
    orderEndLat?: number
    orderEndLng?: number
}

interface RouteSegment {
    plannedRoute: string
    type: 1 | 2
    label?: string
}

interface HubPoint {
    lat: number
    lng: number
    name: string
}

const props = defineProps<{
    segments: Segment[]
    /** 最终收货地址（来自订单信息） */
    receiverAddress?: string
}>()

// ── 全程汇总地图数据 ──────────────────────────────────────────

const mapData = computed(() => {
    const segs = props.segments ?? []
    if (!segs.length) return {
        startLat: undefined, startLng: undefined, startLabel: undefined,
        endLat: undefined, endLng: undefined, endLabel: undefined,
        currentLat: undefined, currentLng: undefined,
        hubs: [] as HubPoint[], routeSegments: [] as RouteSegment[], recentTracks: undefined
    }

    const routeSegments: RouteSegment[] = []
    const hubs: HubPoint[] = []
    const seenHubs = new Set<string>()

    let startLat: number | undefined, startLng: number | undefined, startLabel: string | undefined
    let endLat: number | undefined, endLng: number | undefined, endLabel: string | undefined
    let currentLat: number | undefined, currentLng: number | undefined
    let recentTracks: any[] | undefined

    segs.forEach((seg, idx) => {
        const r = seg.route
        if (!r) return

        // 全程起点：第一段的起点
        if (idx === 0 && r.startLatitude && r.startLongitude) {
            startLat = r.startLatitude
            startLng = r.startLongitude
            startLabel = r.startAddress
        }

        // 全程终点：最后一段的终点（优先取订单收货地址）
        if (idx === segs.length - 1) {
            const eLat = seg.orderEndLat ?? r.endLatitude
            const eLng = seg.orderEndLng ?? r.endLongitude
            if (eLat && eLng) {
                endLat = eLat
                endLng = eLng
                endLabel = seg.orderEndAddress ?? r.endAddress
            }
        } else {
            // 中间段的终点 = 中转站（Hub）标记
            if (r.endLatitude && r.endLongitude) {
                const key = `${r.endLatitude.toFixed(4)},${r.endLongitude.toFixed(4)}`
                if (!seenHubs.has(key)) {
                    seenHubs.add(key)
                    hubs.push({ lat: r.endLatitude, lng: r.endLongitude, name: r.endAddress ?? '中转站' })
                }
            }
        }

        // 路线段（有 plannedRoute 才能画线）；传入 routeStatus 供地图区分颜色
        if (r.plannedRoute) {
            const segType: 1 | 2 = r.segmentType === 2 ? 2 : 1
            routeSegments.push({ plannedRoute: r.plannedRoute, type: segType, label: segmentTitle(seg), routeStatus: r.routeStatus })
        }

        // 当前位置：取正在运输中的段
        if (r.routeStatus === 1) {
            if (r.currentLatitude && r.currentLongitude) {
                currentLat = r.currentLatitude
                currentLng = r.currentLongitude
            }
            if (seg.recentTracks?.length) {
                recentTracks = seg.recentTracks
            }
        }
    })

    return { startLat, startLng, startLabel, endLat, endLng, endLabel, currentLat, currentLng, hubs, routeSegments, recentTracks }
})

// ── 颜色与状态工具 ──────────────────────────────────────────

function timelineColor(routeStatus?: number): string {
    if (routeStatus == null) return '#c0c4cc'
    if (routeStatus === 2) return '#67c23a'
    if (routeStatus === 1) return '#e6a23c'
    if (routeStatus === -1) return '#c0c4cc'
    return '#409eff'
}

function isWaiting(routeStatus?: number): boolean {
    return routeStatus == null || routeStatus === -1 || routeStatus === 0
}

function isActive(routeStatus?: number): boolean {
    return routeStatus === 1
}

function statusTagType(routeStatus?: number): string {
    if (routeStatus === 2)  return 'success'
    if (routeStatus === 1)  return 'warning'
    if (routeStatus === -1) return 'info'
    return 'primary'
}

function routeStatusText(routeStatus?: number): string {
    const m: Record<number, string> = { [-1]: '等待激活', 0: '待出发', 1: '运输中', 2: '已送达', 3: '运输异常' }
    return routeStatus != null ? (m[routeStatus] ?? `状态${routeStatus}`) : '未开始'
}

function segmentIcon(segmentType?: number, startAddress?: string): string {
    if (segmentType === 3) return '🚄'
    if (segmentType === 1) {
        // 全国Hub→本地分拨中心（跨城到达后城市内转运）vs 仓库→本地中转站
        return startAddress?.includes('干线到达') ? '🔄' : '🏭'
    }
    if (segmentType === 2) return '🚚'
    return '📦'
}

function segmentTitle(seg: Segment): string {
    const t = seg.route?.segmentType
    if (t === 3) return '跨城干线运输'
    if (t === 1) {
        // 区分：全国枢纽→本地分拨中心（跨城到达后） vs 发货仓库→本地中转站（同城批次）
        return seg.route?.startAddress?.includes('干线到达')
            ? '城市内转运（枢纽→分拨中心）'
            : '干线配送（仓库→中转站）'
    }
    if (t === 2) return '末端配送（中转站→收货地）'
    return '物流配送'
}

function formatTime(t?: string): string {
    if (!t) return ''
    return new Date(t).toLocaleString('zh-CN', { hour12: false })
}

const allDelivered = computed(() => {
    if (!props.segments?.length) return false
    const last = props.segments[props.segments.length - 1]
    return last.route?.routeStatus === 2
})
</script>

<style scoped>
.journey-wrap {
    padding: 4px 0;
}
.journey-empty {
    padding: 16px 0;
}

/* ── 全程地图区 ── */
.journey-map-section {
    margin-bottom: 16px;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    overflow: hidden;
}
.journey-map-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    background: #f5f7fa;
    border-bottom: 1px solid #e4e7ed;
}
.journey-map-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
}
.journey-map-legend {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    color: #606266;
}
.legend-dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
}
.legend-line {
    display: inline-block;
    width: 18px;
    height: 4px;
    border-radius: 2px;
    flex-shrink: 0;
}
.legend-star {
    color: #e6a23c;
    font-size: 13px;
    line-height: 1;
}

/* ── 时间线区 ── */
.journey-timeline {
    padding: 0 4px;
}

.seg-card {
    background: #fafafa;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    padding: 10px 14px;
    transition: border-color 0.2s;
}
.seg-card.seg-active {
    border-color: #e6a23c;
    background: #fffbf5;
}
.seg-card.seg-final {
    background: #f0f9eb;
    border-color: #b3e19d;
}

.seg-header {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 5px;
}
.seg-icon {
    font-size: 16px;
    line-height: 1;
}
.seg-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    flex: 1;
}
.seg-status-tag {
    flex-shrink: 0;
}

.seg-route-info {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: #606266;
    margin-bottom: 5px;
}
.seg-addr {
    max-width: 220px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.seg-arrow {
    color: #c0c4cc;
    flex-shrink: 0;
}

.seg-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    font-size: 12px;
    color: #909399;
}
.meta-item {
    display: flex;
    align-items: center;
}
.meta-time {
    color: #c0c4cc;
}
</style>
