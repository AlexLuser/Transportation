<template>
    <div class="route-map-wrap">
        <div
            v-if="layerInteraction && layerControls.length > 1"
            class="layer-panel"
            @click.stop
        >
            <div class="layer-panel-title">路线图层</div>
            <label
                v-for="layer in layerControls"
                :key="layer.id"
                class="layer-row"
                :class="{ 'layer-row--active': focusedSegmentId === layer.id }"
            >
                <input
                    type="checkbox"
                    v-model="layer.visible"
                    @change="applySegmentVisuals"
                />
                <span class="layer-swatch" :style="{ background: layer.color }"></span>
                <span class="layer-label">{{ layer.label }}</span>
            </label>
            <p class="layer-hint">悬停高亮 · 点击置顶</p>
        </div>
        <div v-if="hasCoordinates" ref="mapEl" class="map-container"></div>
        <el-empty v-else description="暂无地图信息（地址未配置坐标）" :image-size="60" />
    </div>
</template>

<script setup lang="ts">
    import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
    import L from 'leaflet'
    import 'leaflet/dist/leaflet.css'

    interface Track {
        latitude: number
        longitude: number
        trackTime?: string
    }

    interface HubPoint {
        lat: number
        lng: number
        name: string
        address?: string
    }

    interface RouteSegment {
        id?: string
        plannedRoute: string
        type: 1 | 2
        label?: string
        routeStatus?: number
    }

    interface SegmentLineEntry {
        id: string
        label: string
        color: string
        halo: L.Polyline
        line: L.Polyline
        visible: boolean
        completed: boolean
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
        hubs?: HubPoint[]
        segments?: RouteSegment[]
        extraEndPoints?: { lat: number; lng: number; label: string }[]
        mapHeight?: number
        /** 批次总览等重叠场景：图层开关 + 悬停高亮 + 点击置顶 */
        layerInteraction?: boolean
        /** 简化图例：不区分干线/末端（顾客视图） */
        simpleLegend?: boolean
    }>()

    const mapEl = ref<HTMLElement | null>(null)
    let map: L.Map | null = null
    let resizeObserver: ResizeObserver | null = null
    let segmentLineRegistry: SegmentLineEntry[] = []

    const layerControls = ref<Array<{ id: string; label: string; color: string; visible: boolean }>>([])
    const focusedSegmentId = ref<string | null>(null)
    let hoveredSegmentId: string | null = null

    const isValidCoord = (lat?: number | null, lng?: number | null) =>
        lat != null && lng != null && Number.isFinite(lat) && Number.isFinite(lng)

    const hasCoordinates = computed(() =>
        isValidCoord(props.startLat, props.startLng) || isValidCoord(props.endLat, props.endLng)
        || (props.hubs && props.hubs.length > 0)
        || (props.segments && props.segments.length > 0)
        || (props.extraEndPoints && props.extraEndPoints.length > 0)
        || !!props.plannedRoute
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

    const parseLineLatLngs = (plannedRoute: string): L.LatLngTuple[] => {
        try {
            const geo = JSON.parse(plannedRoute)
            if (!geo.coordinates?.length) return []
            return geo.coordinates.map((c: number[]) => [c[1], c[0]] as L.LatLngTuple)
        } catch {
            return []
        }
    }

    const segmentColor = (seg: RouteSegment, completed: boolean) =>
        completed ? '#ff7b00' : (seg.type === 1 ? '#1677ff' : '#67c23a')

    const scheduleInvalidate = () => {
        if (!map) return
        nextTick(() => {
            requestAnimationFrame(() => {
                map?.invalidateSize({ animate: false })
                setTimeout(() => map?.invalidateSize({ animate: false }), 120)
                setTimeout(() => map?.invalidateSize({ animate: false }), 320)
            })
        })
    }

    const applySegmentVisuals = () => {
        const focus = focusedSegmentId.value
        const hover = hoveredSegmentId
        const dimOthers = !!(focus || hover)

        segmentLineRegistry.forEach(entry => {
            const ctrl = layerControls.value.find(c => c.id === entry.id)
            const visible = ctrl?.visible ?? entry.visible
            entry.visible = visible

            if (!visible) {
                entry.halo.setStyle({ opacity: 0 })
                entry.line.setStyle({ opacity: 0 })
                return
            }

            const highlighted = entry.id === focus || entry.id === hover
            const dimmed = dimOthers && !highlighted
            const lineOpacity = dimmed ? 0.22 : 0.92
            const haloOpacity = dimmed ? 0.12 : (entry.completed ? 0.5 : 0.6)
            const weight = highlighted ? (entry.completed ? 7 : 8) : (entry.completed ? 5 : 6)
            const haloWeight = highlighted ? weight + 4 : weight + 3

            entry.halo.setStyle({ opacity: haloOpacity, weight: haloWeight })
            entry.line.setStyle({ opacity: lineOpacity, weight })
        })
    }

    const bindSegmentInteraction = (entry: SegmentLineEntry) => {
        const onEnter = () => {
            hoveredSegmentId = entry.id
            applySegmentVisuals()
        }
        const onLeave = () => {
            if (hoveredSegmentId === entry.id) hoveredSegmentId = null
            applySegmentVisuals()
        }
        const onClick = (e: L.LeafletMouseEvent) => {
            L.DomEvent.stopPropagation(e)
            focusedSegmentId.value = focusedSegmentId.value === entry.id ? null : entry.id
            entry.halo.bringToFront()
            entry.line.bringToFront()
            applySegmentVisuals()
        }

        entry.line.on('mouseover', onEnter)
        entry.line.on('mouseout', onLeave)
        entry.line.on('click', onClick)
        entry.halo.on('mouseover', onEnter)
        entry.halo.on('mouseout', onLeave)
        entry.halo.on('click', onClick)
    }

    const addSegmentPolylines = (seg: RouteSegment, index: number) => {
        if (!map) return
        const latlngs = parseLineLatLngs(seg.plannedRoute)
        if (latlngs.length <= 1) return

        const completed = seg.routeStatus === 2
        const color = segmentColor(seg, completed)
        const id = seg.id ?? `seg-${index}`
        const label = seg.label ?? (seg.type === 1 ? '干线' : `末端 ${index + 1}`)

        const halo = L.polyline(latlngs, {
            color: '#ffffff',
            weight: completed ? 9 : 10,
            opacity: completed ? 0.5 : 0.6,
            lineJoin: 'round',
        }).addTo(map)
        const line = L.polyline(latlngs, {
            color,
            weight: completed ? 5 : 6,
            opacity: 0.9,
            lineJoin: 'round',
        }).addTo(map)

        if (props.layerInteraction) {
            line.bindPopup(`<b>${label}</b>`)
        }

        const entry: SegmentLineEntry = {
            id,
            label,
            color,
            halo,
            line,
            visible: true,
            completed,
        }
        segmentLineRegistry.push(entry)

        if (props.layerInteraction) {
            bindSegmentInteraction(entry)
        }
    }

    const renderMap = () => {
        if (!hasCoordinates.value || !mapEl.value) return

        segmentLineRegistry = []
        layerControls.value = []
        focusedSegmentId.value = null
        hoveredSegmentId = null

        if (map) {
            map.remove()
            map = null
        }

        map = L.map(mapEl.value, { zoomControl: true })
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
            maxZoom: 18,
        }).addTo(map)

        if (props.layerInteraction) {
            map.on('click', () => {
                focusedSegmentId.value = null
                applySegmentVisuals()
            })
        }

        const bounds: L.LatLngTuple[] = []

        if (isValidCoord(props.startLat, props.startLng)) {
            L.marker([props.startLat!, props.startLng!], { icon: circleIcon('#67c23a', 14) })
                .bindPopup(`<b>发货地（仓库）</b><br>${props.startLabel ?? ''}`)
                .addTo(map)
            bounds.push([props.startLat!, props.startLng!])
        }

        if (isValidCoord(props.endLat, props.endLng)) {
            L.marker([props.endLat!, props.endLng!], { icon: circleIcon('#f56c6c', 14) })
                .bindPopup(`<b>收货地</b><br>${props.endLabel ?? ''}`)
                .addTo(map)
            bounds.push([props.endLat!, props.endLng!])
        }

        if (props.extraEndPoints?.length) {
            props.extraEndPoints.forEach((p, i) => {
                if (!isValidCoord(p.lat, p.lng)) return
                L.marker([p.lat, p.lng], { icon: circleIcon('#f56c6c', 12) })
                    .bindPopup(`<b>收货地 ${i + 1}</b><br>${p.label}`)
                    .addTo(map!)
                bounds.push([p.lat, p.lng])
            })
        }

        if (isValidCoord(props.currentLat, props.currentLng)) {
            L.marker([props.currentLat!, props.currentLng!], { icon: circleIcon('#409eff', 16) })
                .bindPopup('<b>当前位置</b>')
                .addTo(map)
            bounds.push([props.currentLat!, props.currentLng!])
        }

        if (props.hubs?.length) {
            props.hubs.forEach(hub => {
                if (!isValidCoord(hub.lat, hub.lng)) return
                L.marker([hub.lat, hub.lng], { icon: starIcon('#e6a23c') })
                    .bindPopup(`<b>★ 中转站</b><br>${hub.name}<br>${hub.address ?? ''}`)
                    .addTo(map!)
                bounds.push([hub.lat, hub.lng])
            })
        }

        if (props.segments?.length) {
            props.segments.forEach((seg, index) => {
                const latlngs = parseLineLatLngs(seg.plannedRoute)
                if (latlngs.length <= 1) return
                latlngs.forEach(p => bounds.push(p))
                addSegmentPolylines(seg, index)
            })

            if (props.layerInteraction && segmentLineRegistry.length > 1) {
                layerControls.value = segmentLineRegistry.map(e => ({
                    id: e.id,
                    label: e.label,
                    color: e.color,
                    visible: true,
                }))
                applySegmentVisuals()
            }
        } else if (props.plannedRoute) {
            const latlngs = parseLineLatLngs(props.plannedRoute)
            if (latlngs.length > 1) {
                latlngs.forEach(p => bounds.push(p))
                L.polyline(latlngs, { color: '#ffffff', weight: 10, opacity: 0.6 }).addTo(map)
                L.polyline(latlngs, { color: '#1677ff', weight: 6, opacity: 0.9 }).addTo(map)
            }
        }

        if (props.recentTracks && props.recentTracks.length > 1) {
            const pts: L.LatLngTuple[] = [...props.recentTracks]
                .reverse()
                .filter(t => isValidCoord(t.latitude, t.longitude))
                .map(t => [t.latitude, t.longitude])
            if (pts.length > 1) {
                pts.forEach(p => bounds.push(p))
                L.polyline(pts, { color: '#ffffff', weight: 10, opacity: 0.6 }).addTo(map)
                L.polyline(pts, { color: '#ff7b00', weight: 6, opacity: 0.95 }).addTo(map)
            }
        }

        const hasSegments = props.segments && props.segments.length > 0
        const showLegend = !props.layerInteraction || !hasSegments
        if (showLegend) {
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
                        ${hasSegments && !props.simpleLegend
                            ? `<div><span style="display:inline-block;width:24px;height:4px;background:#1677ff;margin-right:5px;vertical-align:middle;border-radius:2px"></span>干线预计</div>
                               <div><span style="display:inline-block;width:24px;height:4px;background:#67c23a;margin-right:5px;vertical-align:middle;border-radius:2px"></span>末端预计</div>`
                            : hasSegments
                                ? `<div><span style="display:inline-block;width:24px;height:4px;background:#1677ff;margin-right:5px;vertical-align:middle;border-radius:2px"></span>预计路线</div>`
                                : `<div><span style="display:inline-block;width:24px;height:4px;background:#1677ff;margin-right:5px;vertical-align:middle;border-radius:2px"></span>预计路线</div>`}
                    </div>`
                    return div
                },
            })
            new LegendControl({ position: 'bottomright' }).addTo(map)
        }

        if (bounds.length === 1) {
            map.setView(bounds[0], 13)
        } else if (bounds.length > 1) {
            map.fitBounds(bounds, { padding: [40, 40] })
        } else {
            map.setView([31.23, 121.47], 11)
        }

        scheduleInvalidate()
    }

    onMounted(() => {
        renderMap()
        if (mapEl.value && typeof ResizeObserver !== 'undefined') {
            resizeObserver = new ResizeObserver(() => map?.invalidateSize({ animate: false }))
            resizeObserver.observe(mapEl.value)
        }
    })

    watch(
        () => [
            props.startLat, props.startLng, props.endLat, props.endLng,
            props.currentLat, props.currentLng, props.plannedRoute,
            props.hubs, props.segments, props.extraEndPoints, props.recentTracks,
            props.layerInteraction,
        ],
        () => renderMap(),
        { deep: true },
    )

    onUnmounted(() => {
        resizeObserver?.disconnect()
        resizeObserver = null
        segmentLineRegistry = []
        map?.remove()
        map = null
    })
</script>

<style scoped>
.route-map-wrap {
    position: relative;
    width: 100%;
}

.layer-panel {
    position: absolute;
    top: 10px;
    left: 10px;
    z-index: 1000;
    background: rgba(255, 255, 255, 0.96);
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    padding: 10px 12px;
    font-size: 12px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
    max-width: 200px;
}

.layer-panel-title {
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;
}

.layer-row {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 6px;
    cursor: pointer;
    user-select: none;
}

.layer-row--active .layer-label {
    font-weight: 600;
    color: #303133;
}

.layer-swatch {
    width: 14px;
    height: 4px;
    border-radius: 2px;
    flex-shrink: 0;
}

.layer-label {
    color: #606266;
    line-height: 1.4;
}

.layer-hint {
    margin: 6px 0 0;
    font-size: 11px;
    color: #909399;
    line-height: 1.4;
}

.map-container {
    height: v-bind('`${props.mapHeight ?? 340}px`');
    width: 100%;
    border-radius: 6px;
    border: 1px solid #e4e7ed;
    overflow: hidden;
}
</style>
