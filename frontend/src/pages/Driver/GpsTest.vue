<template>
    <div class="gps-test-page">
        <el-card shadow="never" class="section-card">
            <template #header>
                <div class="card-head">
                    <span class="title">GPS 位置上报测试</span>
                    <el-tag type="warning" size="small" effect="plain">仅供测试</el-tag>
                </div>
            </template>

            <el-form label-width="90px" size="default">
                <!-- 选择配送单 -->
                <el-form-item label="选择配送">
                    <el-select
                        v-model="selectedDeliveryId"
                        placeholder="请选择进行中的配送任务"
                        :loading="loadingList"
                        style="width: 320px"
                        @change="handleDeliveryChange"
                    >
                        <el-option
                            v-for="d in inProgress"
                            :key="d.id"
                            :label="d.orderId ? `配送任务 #${d.orderId}` : `配送任务 #${d.id}`"
                            :value="d.id"
                        />
                    </el-select>
                    <el-button
                        text
                        :icon="RefreshRight"
                        :loading="loadingList"
                        style="margin-left: 8px"
                        @click="loadDeliveries"
                    >刷新</el-button>
                </el-form-item>
            </el-form>

            <!-- 路线信息预览 -->
            <template v-if="loadingRoute">
                <el-skeleton :rows="2" animated />
            </template>
            <template v-else-if="routeInfo">
                <el-descriptions :column="3" border size="small" class="route-info">
                    <el-descriptions-item label="物流单号">{{ routeInfo.routeNo ?? '-' }}</el-descriptions-item>
                    <el-descriptions-item label="路线 ID">{{ routeInfo.id }}</el-descriptions-item>
                    <el-descriptions-item label="路径点总数">{{ waypoints.length }} 个</el-descriptions-item>
                    <el-descriptions-item label="出发地" :span="3">{{ routeInfo.startAddress ?? '-' }}</el-descriptions-item>
                    <el-descriptions-item label="目的地" :span="3">{{ routeInfo.endAddress ?? '-' }}</el-descriptions-item>
                </el-descriptions>

                <el-alert
                    v-if="waypoints.length === 0"
                    type="warning"
                    show-icon
                    :closable="false"
                    title="该路线暂无规划路径坐标，无法进行测试"
                    style="margin-top: 12px"
                />
                <template v-else>
                    <div class="ctrl-row">
                        <el-form label-width="90px" size="default" inline>
                            <el-form-item label="上报间隔">
                                <el-input-number
                                    v-model="autoInterval"
                                    :min="1"
                                    :max="60"
                                    :step="1"
                                    :disabled="autoRunning"
                                    style="width: 120px"
                                />
                                <span class="unit-hint">秒/点</span>
                            </el-form-item>
                        </el-form>
                        <div class="ctrl-btns">
                            <el-button
                                :type="autoRunning ? 'danger' : 'primary'"
                                :loading="autoRunning && currentIndex === 0"
                                @click="toggleAuto"
                            >
                                {{ autoRunning ? '停止上报' : '开始自动上报' }}
                            </el-button>
                            <el-button :disabled="autoRunning" @click="resetProgress">重置进度</el-button>
                        </div>
                    </div>

                    <!-- 进度条 -->
                    <div class="progress-row">
                        <span class="progress-label">上报进度：{{ currentIndex }} / {{ waypoints.length }}</span>
                        <el-progress
                            :percentage="waypoints.length ? Math.round(currentIndex / waypoints.length * 100) : 0"
                            :status="currentIndex >= waypoints.length && waypoints.length > 0 ? 'success' : undefined"
                            style="flex: 1"
                        />
                    </div>
                    <div v-if="currentIndex > 0 && currentIndex <= waypoints.length" class="current-coord">
                        当前坐标：
                        <el-tag size="small" type="info">
                            lat {{ waypoints[currentIndex - 1]?.[0] }} / lng {{ waypoints[currentIndex - 1]?.[1] }}
                        </el-tag>
                    </div>
                </template>
            </template>
            <el-empty
                v-else-if="!loadingList && !selectedDeliveryId"
                description="请先选择一个进行中的配送任务"
                :image-size="60"
            />
        </el-card>

        <!-- 日志区 -->
        <el-card shadow="never" class="section-card log-card">
            <template #header>
                <div class="card-head">
                    <span class="title">上报日志</span>
                    <el-button size="small" text @click="clearLog">清空</el-button>
                </div>
            </template>
            <div class="log-list">
                <div v-if="!logs.length" class="log-empty">暂无记录，选择路线后点击「开始自动上报」</div>
                <div
                    v-for="(log, i) in logs"
                    :key="i"
                    :class="['log-item', log.success ? 'log-ok' : 'log-err']"
                >
                    <span class="log-time">{{ log.time }}</span>
                    <el-tag :type="log.success ? 'success' : 'danger'" size="small" class="log-badge">
                        {{ log.success ? '成功' : '失败' }}
                    </el-tag>
                    <span class="log-msg">{{ log.msg }}</span>
                </div>
            </div>
        </el-card>
    </div>
</template>

<script setup lang="ts">
    import { ref, onMounted, onUnmounted } from 'vue';
    import { ElMessage } from 'element-plus';
    import { RefreshRight } from '@element-plus/icons-vue';
    import { getInProgressDeliveries } from '@/api/driver';
    import { getRouteByOrderId, getRouteByRouteId, getLatestTrack, uploadLocation } from '@/api/logistics';

    const inProgress = ref<any[]>([]);
    const loadingList = ref(false);
    const loadingRoute = ref(false);
    const selectedDeliveryId = ref<number | undefined>(undefined);
    const routeInfo = ref<any>(null);
    /** 从 plannedRoute GeoJSON 解析出的 [lat, lng] 数组 */
    const waypoints = ref<[number, number][]>([]);

    const autoRunning = ref(false);
    const autoInterval = ref(5);
    const currentIndex = ref(0);
    let autoTimer: ReturnType<typeof setInterval> | null = null;

    interface LogEntry { time: string; success: boolean; msg: string }
    const logs = ref<LogEntry[]>([]);

    const addLog = (success: boolean, msg: string) => {
        const time = new Date().toLocaleTimeString('zh-CN', { hour12: false });
        logs.value.unshift({ time, success, msg });
        if (logs.value.length > 200) logs.value.pop();
    };

    /** 欧氏距离平方（用于找最近路径点，无需开方） */
    const distSq = (lat1: number, lng1: number, lat2: number, lng2: number) =>
        (lat1 - lat2) ** 2 + (lng1 - lng2) ** 2;

    /**
     * 查询后端最新轨迹点，在规划路径中找到距离最近的点的索引，
     * 返回该索引 + 1（即下一个待上报的点）。
     * 若后端尚无轨迹记录则返回 0。
     */
    const resumeIndexFromServer = async (routeId: number): Promise<number> => {
        try {
            const res = await getLatestTrack(routeId);
            const track = res.data;
            if (!track?.latitude || !track?.longitude || waypoints.value.length === 0) return 0;

            let minDist = Infinity;
            let minIdx = 0;
            waypoints.value.forEach(([lat, lng], i) => {
                const d = distSq(track.latitude, track.longitude, lat, lng);
                if (d < minDist) { minDist = d; minIdx = i; }
            });
            // 从最近点的下一个开始，避免重复上报同一坐标
            return Math.min(minIdx + 1, waypoints.value.length);
        } catch {
            // 后端返回错误通常代表"尚无轨迹"，从头开始即可
            return 0;
        }
    };

    const loadDeliveries = async () => {
        loadingList.value = true;
        try {
            const res = await getInProgressDeliveries();
            inProgress.value = (res.data ?? []).filter((d: any) => d.segmentType !== 1);
        } catch {
            inProgress.value = [];
        } finally {
            loadingList.value = false;
        }
    };

    const handleDeliveryChange = async (deliveryId: number) => {
        stopAuto();
        currentIndex.value = 0;
        routeInfo.value = null;
        waypoints.value = [];

        const delivery = inProgress.value.find(d => d.id === deliveryId);
        if (!delivery) return;

        loadingRoute.value = true;
        try {
            // 多停靠配送任务 orderId=null，改用 routeId 直接查
            const res = delivery.orderId
                ? await getRouteByOrderId(delivery.orderId)
                : await getRouteByRouteId(delivery.routeId);
            const detail = res.data;
            routeInfo.value = detail?.route ?? null;

            const planned: string | null | undefined = detail?.route?.plannedRoute;
            if (planned) {
                try {
                    const geo = JSON.parse(planned);
                    if (Array.isArray(geo.coordinates) && geo.coordinates.length > 0) {
                        // GeoJSON coordinates 格式为 [lng, lat]，转换为 [lat, lng]
                        waypoints.value = geo.coordinates.map((c: number[]) => [c[1], c[0]] as [number, number]);
                    }
                } catch {
                    ElMessage.warning('规划路线格式解析失败');
                }
            }

            // 查后端已上报的最新位置，自动恢复进度
            if (routeInfo.value?.id && waypoints.value.length > 0) {
                const resumed = await resumeIndexFromServer(routeInfo.value.id);
                currentIndex.value = resumed;
                if (resumed > 0 && resumed < waypoints.value.length) {
                    addLog(true, `检测到已上报 ${resumed} 个点，从第 ${resumed + 1} 个点继续`);
                }
            }
        } catch (e: any) {
            ElMessage.error(typeof e === 'string' ? e : '加载路线失败');
        } finally {
            loadingRoute.value = false;
        }
    };

    const doUploadAt = async (index: number): Promise<boolean> => {
        const pt = waypoints.value[index];
        if (!pt || !routeInfo.value?.id) return false;
        try {
            const res = await uploadLocation({
                routeId: routeInfo.value.id,
                latitude: pt[0],
                longitude: pt[1],
            });
            const track = res.data;
            addLog(true, `[${index + 1}/${waypoints.value.length}] lat=${pt[0]} lng=${pt[1]} → trackId=${track?.id ?? '?'}`);
            return true;
        } catch (e: any) {
            addLog(false, `[${index + 1}/${waypoints.value.length}] lat=${pt[0]} lng=${pt[1]} → ${typeof e === 'string' ? e : '上报失败'}`);
            return false;
        }
    };

    const toggleAuto = () => {
        if (autoRunning.value) {
            stopAuto();
            return;
        }
        if (!routeInfo.value?.id || waypoints.value.length === 0) {
            ElMessage.warning('请先选择有规划路径的配送单');
            return;
        }
        if (currentIndex.value >= waypoints.value.length) {
            ElMessage.info('已到达终点，请先重置进度');
            return;
        }
        autoRunning.value = true;
        // 立即上报当前点
        doUploadAt(currentIndex.value).then(() => currentIndex.value++);
        autoTimer = setInterval(async () => {
            if (currentIndex.value >= waypoints.value.length) {
                stopAuto();
                ElMessage.success('全部路径点已上报完毕');
                return;
            }
            await doUploadAt(currentIndex.value);
            currentIndex.value++;
        }, autoInterval.value * 1000);
    };

    const stopAuto = () => {
        autoRunning.value = false;
        if (autoTimer) { clearInterval(autoTimer); autoTimer = null; }
    };

    const resetProgress = () => {
        stopAuto();
        currentIndex.value = 0;
    };

    const clearLog = () => { logs.value = []; };

    onMounted(loadDeliveries);
    onUnmounted(stopAuto);
</script>

<style scoped>
    .gps-test-page { display: flex; flex-direction: column; gap: 16px; }
    .section-card { border-radius: 8px; }
    .card-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 10px;
    }
    .title { font-weight: 600; }

    .route-info { margin-bottom: 16px; }

    .ctrl-row {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
        margin: 16px 0 8px;
    }
    .ctrl-btns { display: flex; gap: 8px; }
    .unit-hint { margin-left: 6px; font-size: 13px; color: #909399; }

    .progress-row {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 8px;
    }
    .progress-label { font-size: 13px; color: #606266; white-space: nowrap; }

    .current-coord { font-size: 13px; color: #606266; margin-bottom: 4px; }

    .log-card { min-height: 160px; }
    .log-list {
        max-height: 300px;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 6px;
    }
    .log-empty {
        color: #909399;
        font-size: 13px;
        text-align: center;
        padding: 24px 0;
    }
    .log-item {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 13px;
        padding: 4px 8px;
        border-radius: 4px;
    }
    .log-ok { background: #f0f9eb; }
    .log-err { background: #fef0f0; }
    .log-time { color: #909399; white-space: nowrap; }
    .log-badge { flex-shrink: 0; }
    .log-msg { word-break: break-all; }
</style>
