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
    }>()

    const mapEl = ref<HTMLElement | null>(null)
    let map: L.Map | null = null

    const hasCoordinates = computed(() =>
        (props.startLat && props.startLng) || (props.endLat && props.endLng)
    )

    const circleIcon = (color: string, size = 12) =>
        L.divIcon({
            className: '',
            html: `<div style="width:${size}px;height:${size}px;border-radius:50%;background:${color};border:3px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.35)"></div>`,
            iconSize: [size, size],
            iconAnchor: [size / 2, size / 2],
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
                .bindPopup(`<b>发货地</b><br>${props.startLabel ?? ''}`)
                .addTo(map)
            bounds.push([props.startLat, props.startLng])
        }

        if (props.endLat && props.endLng) {
            L.marker([props.endLat, props.endLng], { icon: circleIcon('#f56c6c', 14) })
                .bindPopup(`<b>收货地</b><br>${props.endLabel ?? ''}`)
                .addTo(map)
            bounds.push([props.endLat, props.endLng])
        }

        if (props.currentLat && props.currentLng) {
            L.marker([props.currentLat, props.currentLng], { icon: circleIcon('#409eff', 16) })
                .bindPopup('<b>当前位置</b>')
                .addTo(map)
            bounds.push([props.currentLat, props.currentLng])
        }

        // 规划路线（蓝色虚线）
        if (props.plannedRoute) {
            try {
                const geo = JSON.parse(props.plannedRoute)
                if (geo.coordinates?.length > 1) {
                    const latlngs: L.LatLngTuple[] = geo.coordinates.map((c: number[]) => [c[1], c[0]])
                    L.polyline(latlngs, { color: '#409eff', weight: 3, opacity: 0.55, dashArray: '8,5' }).addTo(map)
                }
            } catch { /* ignore */ }
        }

        // 实际轨迹（橙色实线）：后端返回时间倒序，需 reverse 转为时间正序再画线
        if (props.recentTracks && props.recentTracks.length > 1) {
            const pts: L.LatLngTuple[] = [...props.recentTracks]
                .reverse()
                .filter(t => t.latitude && t.longitude)
                .map(t => [t.latitude, t.longitude])
            if (pts.length > 1) {
                L.polyline(pts, { color: '#e6a23c', weight: 3, opacity: 0.85 }).addTo(map)
            }
        }

        // 图例：使用 L.Control.extend 创建自定义控件，避免直接调用 L.control() 的类型问题
        const LegendControl = L.Control.extend({
            onAdd() {
                const div = L.DomUtil.create('div', 'map-legend')
                div.innerHTML = `
                    <div style="background:#fff;padding:8px 10px;border-radius:6px;font-size:12px;box-shadow:0 1px 5px rgba(0,0,0,.2);line-height:1.8">
                        <div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#67c23a;margin-right:5px"></span>发货地</div>
                        <div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#f56c6c;margin-right:5px"></span>收货地</div>
                        <div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#409eff;margin-right:5px"></span>当前位置</div>
                        <div><span style="display:inline-block;width:24px;height:3px;background:#409eff;margin-right:5px;vertical-align:middle;opacity:.55"></span>规划路线</div>
                        <div><span style="display:inline-block;width:24px;height:3px;background:#e6a23c;margin-right:5px;vertical-align:middle"></span>实际轨迹</div>
                    </div>`
                return div
            }
        })
        new LegendControl({ position: 'bottomright' }).addTo(map)

        // 自动缩放到合适范围
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
    height: 340px;
    width: 100%;
    border-radius: 6px;
    border: 1px solid #e4e7ed;
    overflow: hidden;
}
</style>
