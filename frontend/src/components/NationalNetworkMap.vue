<template>
    <div ref="mapEl" class="national-map-container" />
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

interface Hub {
    id: number
    name: string
    city?: string
    latitude: number
    longitude: number
    hubLevel?: number
}

interface FlowEdge {
    fromHubId: number
    toHubId: number
    flowAmount: number
    loadRate?: number
    transportMode?: string
}

interface BatchLine {
    id: number
    fromHubId: number
    toHubId: number
    itemCount: number
    /** 无实单时展示的 MCMF 规划预估件数 */
    plannedCount?: number
    status: string   // CHAINED | CREATED | DEPARTED | ARRIVED
    batchNo?: string
    fromHubName?: string
    toHubName?: string
}

const props = defineProps<{
    hubs: Hub[]
    /** MCMF 算法边（可选，作为背景参考线） */
    edges?: FlowEdge[]
    /** 实时在途批次（主视图数据来源） */
    batches?: BatchLine[]
    activeBatchCounts?: Record<number, number>
}>()

const mapEl = ref<HTMLElement | null>(null)
let map: L.Map | null = null

const BATCH_COLORS: Record<string, string> = {
    CHAINED:   '#c0c4cc',  // 灰  – 预规划/待激活
    CREATED:   '#409eff',  // 蓝  – 待发车
    DEPARTED:  '#e6a23c',  // 橙  – 运输中
    ARRIVED:   '#67c23a',  // 绿  – 已到达（今日）
}

const hubRadius = (hubLevel: number | undefined) =>
    hubLevel === 0 ? 13 : hubLevel === 1 ? 9 : 7

/**
 * 同一条地理连线上的反向班次会重叠，用轻微弧线向两侧分开，便于区分有向边。
 */
function curvedLineLatLngs(from: Hub, to: Hub, fromHubId: number, toHubId: number): L.LatLngTuple[] {
    const dLat = to.latitude - from.latitude
    const dLng = to.longitude - from.longitude
    const len = Math.sqrt(dLat * dLat + dLng * dLng) || 1e-9
    const perpLat = -dLng / len
    const perpLng = dLat / len
    const sign = fromHubId < toHubId ? 1 : -1
    const bend = Math.min(0.42, len * 0.28) * sign
    const midLat = (from.latitude + to.latitude) / 2 + perpLat * bend
    const midLng = (from.longitude + to.longitude) / 2 + perpLng * bend
    return [
        [from.latitude, from.longitude],
        [midLat, midLng],
        [to.latitude, to.longitude],
    ]
}

/** 二次贝塞尔在 t=0.5 处的点，用于标签与箭头落点 */
function quadMid(a: L.LatLngTuple, c: L.LatLngTuple, b: L.LatLngTuple): L.LatLngTuple {
    return [
        0.25 * a[0] + 0.5 * c[0] + 0.25 * b[0],
        0.25 * a[1] + 0.5 * c[1] + 0.25 * b[1],
    ]
}

/** 把方向箭头画在弧线上方靠近目的端一侧 */
function drawArrowAlongCurve(a: L.LatLngTuple, c: L.LatLngTuple, b: L.LatLngTuple, color: string) {
    const t = 0.62
    const omt = 1 - t
    const lat = omt * omt * a[0] + 2 * omt * t * c[0] + t * t * b[0]
    const lng = omt * omt * a[1] + 2 * omt * t * c[1] + t * t * b[1]
    const t2 = Math.min(0.99, t + 0.04)
    const o2 = 1 - t2
    const lat2 = o2 * o2 * a[0] + 2 * o2 * t2 * c[0] + t2 * t2 * b[0]
    const lng2 = o2 * o2 * a[1] + 2 * o2 * t2 * c[1] + t2 * t2 * b[1]
    const angle = Math.atan2(lat2 - lat, lng2 - lng) * 180 / Math.PI
    const icon = L.divIcon({
        className: '',
        html: `<div style="width:0;height:0;border-top:5px solid transparent;border-bottom:5px solid transparent;border-left:11px solid ${color};transform:rotate(${-angle}deg)"></div>`,
        iconSize: [11, 10], iconAnchor: [5, 5],
    })
    L.marker([lat, lng], { icon, interactive: false }).addTo(map!)
}

function renderMap() {
    if (!mapEl.value) return
    if (map) { map.remove(); map = null }

    // OpenStreetMap 标准底图（常规彩色路网，与国内常见电子地图观感接近；无需密钥）
    map = L.map(mapEl.value, { zoomControl: true })
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        subdomains: 'abc',
        maxZoom: 19,
    }).addTo(map)

    const hubMap: Record<number, Hub> = {}
    props.hubs.forEach(h => { hubMap[h.id] = h })

    const bounds: L.LatLngTuple[] = []

    // ── 1. MCMF 算法参考背景线（细、浅灰，可选）──
    if (props.edges && props.edges.length > 0) {
        props.edges.forEach(edge => {
            const from = hubMap[edge.fromHubId], to = hubMap[edge.toHubId]
            if (!from || !to) return
            L.polyline(
                [[from.latitude, from.longitude], [to.latitude, to.longitude]],
                { color: '#c8d0da', weight: 1.5, opacity: 0.5, dashArray: '4 4' }
            ).addTo(map!)
        })
    }

    // ── 2. 实时批次线（主视图）──
    const batchesToDraw = props.batches ?? []
    batchesToDraw.forEach(batch => {
        const from = hubMap[batch.fromHubId], to = hubMap[batch.toHubId]
        if (!from || !to) return
        const color = BATCH_COLORS[batch.status] ?? '#909399'
        const isDeparted = batch.status === 'DEPARTED'
        const isChained = batch.status === 'CHAINED'
        const weight = isDeparted ? 6 : (batch.status === 'ARRIVED' || isChained) ? 2 : 5
        const dash = isDeparted ? '10 5' : batch.status === 'ARRIVED' ? '6 4' : isChained ? '4 6' : undefined

        const a: L.LatLngTuple = [from.latitude, from.longitude]
        const b: L.LatLngTuple = [to.latitude, to.longitude]
        const latlngs = curvedLineLatLngs(from, to, batch.fromHubId, batch.toHubId)
        const ctrl = latlngs[1]

        // 白色描边（略粗，提高线路在浅色底图上的轮廓）
        L.polyline(latlngs, { color: '#fff', weight: weight + 5, opacity: 0.92, lineJoin: 'round' }).addTo(map!)
        const line = L.polyline(latlngs, { color, weight, opacity: 0.98, dashArray: dash, lineJoin: 'round' }).addTo(map!)
        const { count: displayCount, isPlanned } = batchDisplayCount(batch)
        const countDesc = isPlanned ? `预估 <b>${displayCount}</b> 件` : `<b>${displayCount}</b> 件`
        line.bindPopup(`
            <div style="min-width:180px">
              <b style="font-size:14px">${batch.fromHubName || from.city || from.name} → ${batch.toHubName || to.city || to.name}</b><br>
              <span style="color:${color};font-weight:600">${statusLabel(batch.status)}</span>
              &nbsp;·&nbsp; ${countDesc}<br>
              <span style="font-size:11px;color:#909399">${batch.batchNo ?? ''}</span>
            </div>`)

        drawArrowAlongCurve(a, ctrl, b, color)

        const [midLat, midLng] = quadMid(a, ctrl, b)
        const countText = isPlanned ? `预估 ${displayCount} 件` : `${displayCount} 件`
        const labelIcon = L.divIcon({
            className: 'national-map-batch-label-host',
            html: `<div class="national-map-batch-label" style="--edge-color:${color}">${countText}</div>`,
            iconSize: [48, 20],
            iconAnchor: [24, 10],
        })
        L.marker([midLat, midLng], { icon: labelIcon, interactive: false }).addTo(map!)

        bounds.push(a, b)
    })

    // ── 3. Hub 节点 ──
    props.hubs.forEach(hub => {
        const activeBatches = props.activeBatchCounts?.[hub.id] ?? 0
        const hasBatch = (props.batches ?? []).some(b => (b.fromHubId === hub.id || b.toHubId === hub.id) && b.status !== 'ARRIVED')
        const hasMcmf  = (props.edges ?? []).some(e => e.fromHubId === hub.id || e.toHubId === hub.id)
        const fillColor = hasBatch ? '#409eff' : hasMcmf ? '#a0cfff' : '#dcdfe6'
        const r = hubRadius(hub.hubLevel)

        const circle = L.circleMarker([hub.latitude, hub.longitude], {
            radius: r, fillColor,
            color: hasBatch ? '#0d6efd' : '#8a9bb3',
            weight: 2, opacity: 1, fillOpacity: 0.9,
        }).addTo(map!)
        circle.bindPopup(`
            <b>${hub.name}</b><br>
            ${hub.city ? `城市：${hub.city}<br>` : ''}
            等级：${hub.hubLevel === 0 ? '全国枢纽' : '城市配送中心'}<br>
            在途批次：<b>${activeBatches}</b>`)

        // 活跃批次数角标
        if (activeBatches > 0) {
            const badge = L.divIcon({
                className: '',
                html: `<div style="background:#f56c6c;color:#fff;border-radius:50%;width:15px;height:15px;line-height:15px;text-align:center;font-size:10px;font-weight:700;box-shadow:0 1px 3px rgba(0,0,0,.4)">${activeBatches}</div>`,
                iconSize: [15, 15], iconAnchor: [-r + 1, r],
            })
            L.marker([hub.latitude, hub.longitude], { icon: badge, interactive: false }).addTo(map!)
        }

        // 城市名标签（白色描边，确保可读性）
        const labelIcon = L.divIcon({
            className: '',
            html: `<div style="font-size:11px;font-weight:700;color:#1d2129;white-space:nowrap;text-shadow:0 0 3px #fff,0 0 3px #fff,0 0 3px #fff">${hub.city || hub.name}</div>`,
            iconAnchor: [-r - 2, -2],
        })
        L.marker([hub.latitude, hub.longitude], { icon: labelIcon, interactive: false }).addTo(map!)

        if (!bounds.some(b => b[0] === hub.latitude)) {
            bounds.push([hub.latitude, hub.longitude])
        }
    })

    // ── 4. 图例 ──
    const LegendControl = L.Control.extend({
        onAdd() {
            const div = L.DomUtil.create('div')
            div.innerHTML = `
              <div style="background:rgba(255,255,255,0.94);padding:8px 12px;border-radius:8px;font-size:12px;box-shadow:0 2px 8px rgba(0,0,0,.18);line-height:1.9">
                <div style="font-weight:700;margin-bottom:3px;font-size:13px">实时物流状态</div>
                <div><span style="display:inline-block;width:26px;height:2px;background:#c0c4cc;vertical-align:middle;border-radius:2px;margin-right:5px;border-top:2px dashed #c0c4cc"></span>预规划批次（预估件数）</div>
                <div><span style="display:inline-block;width:26px;height:4px;background:#409eff;vertical-align:middle;border-radius:2px;margin-right:5px"></span>待发车批次</div>
                <div><span style="display:inline-block;width:26px;height:4px;background:#e6a23c;vertical-align:middle;border-radius:2px;margin-right:5px;border-bottom:2px dashed #e6a23c"></span>运输中批次</div>
                <div><span style="display:inline-block;width:26px;height:4px;background:#67c23a;vertical-align:middle;border-radius:2px;margin-right:5px"></span>已到达批次</div>
                <div><span style="display:inline-block;width:26px;height:2px;background:#c8d0da;vertical-align:middle;border-radius:2px;margin-right:5px;border-top:2px dashed #c8d0da"></span>规划参考线路</div>
                <div style="margin-top:3px"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#409eff;margin-right:5px;vertical-align:middle"></span>有在途批次 Hub</div>
                <div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#dcdfe6;border:1.5px solid #8a9bb3;margin-right:5px;vertical-align:middle"></span>空闲 Hub</div>
              </div>`
            return div
        }
    })
    new LegendControl({ position: 'bottomright' }).addTo(map)

    if (bounds.length >= 2) {
        map.fitBounds(bounds, { padding: [36, 36] })
    } else {
        map.setView([35, 105], 4)
    }
}

function statusLabel(status: string) {
    const m: Record<string, string> = {
        CHAINED: '预规划',
        CREATED: '待发车',
        DEPARTED: '运输中',
        ARRIVED: '已到达',
        DISPATCHED: '已调度',
    }
    return m[status] ?? '进行中'
}

/** 实单优先；无实单时用规划预估件数 */
function batchDisplayCount(batch: BatchLine) {
    const actual = Number(batch.itemCount) || 0
    if (actual > 0) return { count: actual, isPlanned: false }
    const planned = Number(batch.plannedCount) || 0
    if (planned > 0) return { count: planned, isPlanned: true }
    return { count: 0, isPlanned: false }
}

onMounted(() => renderMap())

watch(() => [props.hubs, props.edges, props.batches, props.activeBatchCounts], () => {
    renderMap()
}, { deep: true })

onUnmounted(() => { map?.remove(); map = null })
</script>

<style scoped>
.national-map-container {
    height: 520px;
    width: 100%;
    border-radius: 8px;
    border: 1px solid #e4e7ed;
    overflow: hidden;
    box-shadow: 0 2px 8px rgba(0, 0, 0, .08);
}
</style>
<!-- Leaflet divIcon 内容在运行时插入，不受 scoped 约束 -->
<style>
.national-map-batch-label-host {
    background: transparent !important;
    border: none !important;
}
.national-map-batch-label {
    padding: 1px 6px;
    font-size: 11px;
    font-weight: 700;
    line-height: 1.35;
    color: #1a1a1a;
    background: rgba(255, 255, 255, 0.92);
    border: 1px solid var(--edge-color);
    border-radius: 6px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.18);
    white-space: nowrap;
}
</style>
