<template>
    <div>
        <div v-if="hasCoordinates" ref="mapEl" class="map-container"></div>
        <el-empty v-else description="暂无地图信息（地址未配置坐标）" :image-size="60" />
    </div>
</template>

<script setup lang="ts">
    import { ref, computed, onMounted, onUnmounted } from 'vue'
    import L from 'leaflet'
    import 'leaflet/dist/leaflet.css'

    interface Track {
        latitude: number
        longitude: number
        trackTime?: string
    }

    /** Hub 坐标，用于在地图上显示中转站标记 */
    interface HubPoint {
        lat: number
        lng: number
        name: string
        address?: string
    }

    /** 多段路线（Hub-and-Spoke 模式），type=1 干线，type=2 末端 */
    interface RouteSegment {
        plannedRoute: string  // GeoJSON LineString JSON 字符串
        type: 1 | 2           // 1=干线(蓝色), 2=末端(绿色)
        label?: string
        /** -1=待激活, 0=待出发, 1=运输中, 2=已送达；缺省视为运输中 */
        routeStatus?: number
    }

    const props = defineProps<{
        startLat?: number | null
        startLng?: number | null
        endLat?: number | null
        endLng?: number | null
        currentLat?: number | null
        currentLng?: number | null
        plannedRoute?: string | null
        recentTracks?: Track[]
        startLabel?: string
        endLabel?: string
        /** Hub-and-Spoke：中转站列表（可选） */
        hubs?: HubPoint[]
        /** Hub-and-Spoke：多段路线（可选，传入后忽略 plannedRoute 单段） */
        segments?: RouteSegment[]
        /** 额外的终点标记（批次模式下多个客户地址） */
        extraEndPoints?: { lat: number; lng: number; label: string }[]
        /** 地图高度（px），默认 340 */
        mapHeight?: number
    }>()

    const mapEl = ref<HTMLElement | null>(null)
    let map: L.Map | null = null

    const hasCoordinates = computed(() =>
        (props.startLat && props.startLng) || (props.endLat && props.endLng)
        || (props.hubs && props.hubs.length > 0)
        || (props.segments && props.segments.length > 0)
        || (props.extraEndPoints && props.extraEndPoints.length > 0)
    )

    const circleIcon = (color: string, size = 12) =>
        L.divIcon({
            className: '',
            html: `<div style="width:${size}px;height:${size}px;border-radius:50%;background:${color};border:3px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.35)"></div>`,
            iconSize: [size, size],
            iconAnchor: [size / 2, size / 2],
        })

    const starIcon = (color: string) =>
        L.divIcon({
            className: '',
            html: `<div style="font-size:20px;color:${color};text-shadow:0 1px 4px rgba(0,0,0,0.4);line-height:1">★</div>`,
            iconSize: [20, 20],
            iconAnchor: [10, 10],
        })

    onMounted(() => {
        if (!hasCoordinates.value || !mapEl.value) return

        map = L.map(mapEl.value, { zoomControl: true })
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
            maxZoom: 18,
        }).addTo(map)

        const bounds: L.LatLngTuple[] = []

        if (props.startLat && props.startLng) {
            L.marker([props.startLat, props.startLng], { icon: circleIcon('#67c23a', 14) })
                .bindPopup(`<b>发货地（仓库）</b><br>${props.startLabel ?? ''}`)
                .addTo(map)
            bounds.push([props.startLat, props.startLng])
        }

        if (props.endLat && props.endLng) {
            L.marker([props.endLat, props.endLng], { icon: circleIcon('#f56c6c', 14) })
                .bindPopup(`<b>收货地</b><br>${props.endLabel ?? ''}`)
                .addTo(map)
            bounds.push([props.endLat, props.endLng])
        }

        // 批次模式：多个终点
        if (props.extraEndPoints?.length) {
            props.extraEndPoints.forEach((p, i) => {
                L.marker([p.lat, p.lng], { icon: circleIcon('#f56c6c', 12) })
                    .bindPopup(`<b>收货地 ${i + 1}</b><br>${p.label}`)
                    .addTo(map!)
                bounds.push([p.lat, p.lng])
            })
        }

        if (props.currentLat && props.currentLng) {
            L.marker([props.currentLat, props.currentLng], { icon: circleIcon('#409eff', 16) })
                .bindPopup('<b>当前位置</b>')
                .addTo(map)
            bounds.push([props.currentLat, props.currentLng])
        }

        // Hub 中转站标记（★ 金色五角星）
        if (props.hubs?.length) {
            props.hubs.forEach(hub => {
                L.marker([hub.lat, hub.lng], { icon: starIcon('#e6a23c') })
                    .bindPopup(`<b>★ 中转站</b><br>${hub.name}<br>${hub.address ?? ''}`)
                    .addTo(map!)
                bounds.push([hub.lat, hub.lng])
            })
        }

        // 多段路线（Hub-and-Spoke 模式）
        // 颜色规则（统一适用于干线和末端）：
        //   已送达(status=2)  → 橙色实线（走过的路程）
        //   运输中(status=1)  → 蓝色粗实线（当前路段）
        //   待出发(status=0/-1/undefined) → 蓝色虚线（预计路线）
        if (props.segments?.length) {
            props.segments.forEach(seg => {
                try {
                    const geo = JSON.parse(seg.plannedRoute)
                    if (geo.coordinates?.length > 1) {
                        const latlngs: L.LatLngTuple[] = geo.coordinates.map((c: number[]) => [c[1], c[0]])
                        latlngs.forEach(p => bounds.push(p))

                        if (seg.routeStatus === 2) {
                            // 已走过 → 橙色实线
                            L.polyline(latlngs, { color: '#ffffff', weight: 9, opacity: 0.5 }).addTo(map!)
                            L.polyline(latlngs, { color: '#ff7b00', weight: 5, opacity: 0.9 }).addTo(map!)
                        } else {
                            // 预计路线（含待出发、运输中）→ 蓝色实线
                            L.polyline(latlngs, { color: '#ffffff', weight: 10, opacity: 0.6 }).addTo(map!)
                            L.polyline(latlngs, { color: '#1677ff', weight: 6, opacity: 0.9 }).addTo(map!)
                        }
                    }
                } catch { /* ignore */ }
            })
        } else if (props.plannedRoute) {
            // 单段路线（兼容原有模式）
            try {
                const geo = JSON.parse(props.plannedRoute)
                if (geo.coordinates?.length > 1) {
                    const latlngs: L.LatLngTuple[] = geo.coordinates.map((c: number[]) => [c[1], c[0]])
                    L.polyline(latlngs, { color: '#ffffff', weight: 10, opacity: 0.6 }).addTo(map)
                    L.polyline(latlngs, { color: '#1677ff', weight: 6, opacity: 0.9 }).addTo(map)
                }
            } catch { /* ignore */ }
        }

        // 实际轨迹（橙色，覆盖在路线上层）
        if (props.recentTracks && props.recentTracks.length > 1) {
            const pts: L.LatLngTuple[] = [...props.recentTracks]
                .reverse()
                .filter(t => t.latitude && t.longitude)
                .map(t => [t.latitude, t.longitude])
            if (pts.length > 1) {
                L.polyline(pts, { color: '#ffffff', weight: 10, opacity: 0.6 }).addTo(map)
                L.polyline(pts, { color: '#ff7b00', weight: 6, opacity: 0.95 }).addTo(map)
            }
        }

        // 图例
        const hasSegments = props.segments && props.segments.length > 0
        const LegendControl = L.Control.extend({
            onAdd() {
                const div = L.DomUtil.create('div', 'map-legend')
                div.innerHTML = `
                    <div style="background:#fff;padding:8px 10px;border-radius:6px;font-size:12px;box-shadow:0 1px 5px rgba(0,0,0,.2);line-height:1.8">
                        <div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#67c23a;margin-right:5px"></span>发货地</div>
                        <div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#f56c6c;margin-right:5px"></span>收货地</div>
                        <div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#409eff;margin-right:5px"></span>当前位置</div>
                        ${hasSegments ? `<div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#e6a23c;margin-right:5px"></span>中转站(Hub)</div>` : ''}
                        <div><span style="display:inline-block;width:24px;height:4px;background:#ff7b00;margin-right:5px;vertical-align:middle;border-radius:2px"></span>已走路程</div>
                        <div><span style="display:inline-block;width:24px;height:4px;background:#1677ff;margin-right:5px;vertical-align:middle;border-radius:2px"></span>预计路线</div>
                    </div>`
                return div
            }
        })
        new LegendControl({ position: 'bottomright' }).addTo(map)

        if (bounds.length === 1) {
            map.setView(bounds[0], 13)
        } else if (bounds.length > 1) {
            map.fitBounds(bounds, { padding: [40, 40] })
        }
    })

    onUnmounted(() => {
        map?.remove()
        map = null
    })
</script>

<style scoped>
.map-container {
    height: v-bind('`${props.mapHeight ?? 340}px`');
    width: 100%;
    border-radius: 6px;
    border: 1px solid #e4e7ed;
    overflow: hidden;
}
</style>
