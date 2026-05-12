<template>
  <div class="local-city-dispatch">
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">
          <el-icon style="margin-right:6px"><Promotion /></el-icon>
          城市末端调度
        </h2>
        <el-tag type="info" size="small" class="pool-count-tag">
          待调度：{{ poolItems.length }} 单
        </el-tag>
        <el-tag v-if="urgentDisplayCount > 0" type="danger" size="small" class="pool-count-tag">
          ⚡ {{ urgentDisplayCount }} 单急送
        </el-tag>
      </div>
      <div class="toolbar-right toolbar-filters">
        <el-select
          v-model="filterDestHubId"
          placeholder="全国收货 Hub（全部）"
          clearable
          filterable
          style="width:220px"
          @change="onDestHubFilterChange"
        >
          <el-option
            v-for="h in nationalHubOptions"
            :key="h.id"
            :label="nationalHubOptionLabel(h)"
            :value="h.id"
          />
        </el-select>
        <el-button :icon="Refresh" @click="loadPool" :loading="poolLoading">刷新</el-button>
        <el-button type="primary" :icon="MagicStick" @click="handlePreview"
                   :loading="previewLoading" :disabled="poolItems.length === 0">
          生成调度方案
        </el-button>
      </div>
    </div>

    <div class="main-layout">
      <el-card class="pool-card" shadow="never">
        <template #header>
          <div class="card-header-row">
            <span class="card-title">待调度订单</span>
            <span class="pool-hint text-muted" v-if="selectedRows.length > 0">
              已勾选 {{ selectedRows.length }} 单
            </span>
          </div>
        </template>

        <el-table ref="poolTableRef" :data="poolItems" size="small" v-loading="poolLoading" max-height="520"
                  @selection-change="handleSelectionChange" row-key="orderId">
          <el-table-column type="selection" width="42" />
          <el-table-column label="单号" prop="orderId" width="68" align="center" />
          <el-table-column label="来源" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.dispatchOriginType === 1" type="success" size="small" effect="plain">
                🚉 Hub到达
              </el-tag>
              <el-tag v-else type="info" size="small" effect="plain">
                🏭 仓库发货
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="收货Hub" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ destHubDisplayName(row.destHubId) }}
            </template>
          </el-table-column>
          <el-table-column label="收货地址" min-width="140">
            <template #default="{ row }">
              <div class="addr-cell">
                <el-tooltip :content="row.endAddress" placement="top" :show-after="400">
                  <span class="addr-text">{{ row.endAddress }}</span>
                </el-tooltip>
                <el-tooltip v-if="isOrderDisplayUrgent(row)" :content="row.remark" placement="right">
                  <el-tag type="danger" size="small" class="urgent-tag" effect="dark">⚡ 急送</el-tag>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="收货人" prop="receiverName" width="80" />
          <el-table-column label="备注" width="100">
            <template #default="{ row }">
              <el-tooltip v-if="row.remark" :content="row.remark" placement="top">
                <span class="remark-text">
                  {{ row.remark.length > 8 ? row.remark.slice(0, 8) + '…' : row.remark }}
                </span>
              </el-tooltip>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pool-footer">
          <el-alert v-if="poolItems.filter((o: any) => !o.endLat).length > 0"
                    :title="`${poolItems.filter((o: any) => !o.endLat).length} 个订单地址坐标不完整，将暂时跳过调度`"
                    type="warning" show-icon :closable="false" style="margin-top:8px" />
        </div>
      </el-card>

      <div class="preview-side">
        <el-card shadow="never" class="params-card">
          <template #header><span class="card-title">调度参数</span></template>
          <el-form :model="params" label-width="110px" size="small">
            <el-form-item label="配送方式">
              <el-switch v-model="params.useHub"
                         active-text="经分拨中心配送"
                         inactive-text="直接送达" />
            </el-form-item>
            <el-form-item label="计划发车时间">
              <el-date-picker v-model="params.plannedShipTime" type="datetime"
                              placeholder="不选则立即发车"
                              format="YYYY-MM-DD HH:mm"
                              value-format="YYYY-MM-DDTHH:mm:ss"
                              style="width:200px" />
            </el-form-item>
            <el-collapse class="adv-collapse" v-model="advOpen">
              <el-collapse-item title="高级设置" name="adv">
                <el-form-item label="手动分区数">
                  <el-input-number v-model="params.k" :min="1" :max="20"
                                   placeholder="留空=自动"
                                   controls-position="right" style="width:120px" />
                  <span class="tip-text">不填时系统自动计算最优分区</span>
                </el-form-item>
              </el-collapse-item>
            </el-collapse>
          </el-form>
        </el-card>

        <el-card shadow="never" class="preview-card" v-if="preview" v-loading="previewLoading">
          <template #header>
            <div class="card-header-row">
              <span class="card-title">调度方案预览</span>
            </div>
          </template>

          <el-descriptions :column="2" size="small" border class="summary-desc">
            <el-descriptions-item label="待调度总单数">{{ preview.totalOrders }} 单</el-descriptions-item>
            <el-descriptions-item label="建议批次数">{{ preview.suggestedBatchCount }}</el-descriptions-item>
            <el-descriptions-item label="划分区域数">{{ preview.kUsed }}</el-descriptions-item>
            <el-descriptions-item label="配送策略">{{ translateStrategy(preview.llmAdvice?.strategy) }}</el-descriptions-item>
          </el-descriptions>

          <p v-if="preview.llmAdvice?.reason" class="preview-reason text-muted" style="margin:8px 0;font-size:13px">
            {{ preview.llmAdvice.reason }}
          </p>

          <div class="cluster-list">
            <el-collapse v-model="autoExpandedClusters">
              <el-collapse-item
                v-for="(c, idx) in preview.clusters" :key="c.clusterId"
                :name="c.clusterId">
                <template #title>
                  <div class="cluster-title">
                    <el-tag size="small">批次 {{ (idx as number) + 1 }}</el-tag>
                    <span class="cluster-info">{{ c.orders?.length ?? 0 }} 单</span>
                    <el-tag size="small"
                            :type="batchOverrides[c.clusterId]?.useHub ? 'primary' : 'info'">
                      {{ batchOverrides[c.clusterId]?.useHub ? '经分拨中心' : '直达' }}
                    </el-tag>
                    <el-tag size="small" :type="urgencyTag(getBatchSuggestion(c.clusterId)?.urgency)">
                      {{ translateUrgency(getBatchSuggestion(c.clusterId)?.urgency) }}
                    </el-tag>
                    <el-tag v-if="autoOverriddenClusters.has(c.clusterId)"
                            type="warning" size="small">⚡ 已自动改为直送</el-tag>
                    <el-tag v-if="getBatchSuggestion(c.clusterId)?.urgency === 'HIGH'"
                            type="danger" size="small">🔴 高优</el-tag>
                  </div>
                </template>
                <div class="cluster-body">
                  <el-alert v-if="autoOverriddenClusters.has(c.clusterId)"
                            title="仅 1 单，走中转成本高于收益，已自动调整为直接送达。如需中转请手动勾选。"
                            type="warning" show-icon :closable="false"
                            style="margin-bottom:8px" />
                  <p class="cluster-note"
                     v-if="!autoOverriddenClusters.has(c.clusterId) && getBatchSuggestion(c.clusterId)?.note">
                    💡 {{ getBatchSuggestion(c.clusterId)?.note }}
                  </p>
                  <el-checkbox v-model="batchOverrides[c.clusterId].useHub"
                               label="经分拨中心配送" />
                  <el-table :data="c.orders" size="small" style="margin-top:8px">
                    <el-table-column label="订单" prop="orderId" width="80" />
                    <el-table-column label="收货地址" prop="endAddress" show-overflow-tooltip />
                    <el-table-column label="收货人" prop="receiverName" width="80" />
                    <el-table-column label="备注" min-width="100">
                      <template #default="{ row }">
                        <el-tooltip v-if="row.remark" :content="row.remark" placement="top">
                          <span class="remark-text">
                            {{ row.remark.length > 8 ? row.remark.slice(0,8)+'…' : row.remark }}
                          </span>
                        </el-tooltip>
                        <span v-else class="text-muted">—</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>

          <div class="execute-bar">
            <el-button type="success" size="large" :icon="Promotion"
                       :loading="executeLoading"
                       @click="handleExecute">
              确认执行（共 {{ preview.clusters?.length ?? 0 }} 个批次）
            </el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="result-card" v-if="executeResults.length > 0">
          <template #header><span class="card-title">✅ 调度已执行</span></template>
          <el-table :data="executeResults" size="small">
            <el-table-column label="批次ID" width="80" align="center">
              <template #default="{ row }">{{ row.batch?.id }}</template>
            </el-table-column>
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-tag type="success" size="small">已创建</el-tag>
                {{ row.batch?.totalOrders ?? 0 }} 单
              </template>
            </el-table-column>
            <el-table-column label="配送方式">
              <template #default="{ row }">{{ row.hub?.hubName ? `经${row.hub.hubName}中转` : '直接送达' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, MagicStick, Promotion } from '@element-plus/icons-vue'
import {
  getDispatchPool,
  checkDispatchUrgency,
  previewDispatch,
  executeDispatch,
  getNationalHubs,
  type ExecuteBatchItem,
} from '@/api/logistics'
import { getWarehouses, type Warehouse } from '@/api/shop'

/** 与 dispatch_pool.dest_hub_id / national_hub.id 对应 */
const nationalHubOptions = ref<{ id: number; name?: string; city?: string; province?: string }[]>([])
const filterDestHubId = ref<number | undefined>(undefined)
const poolTableRef = ref<{ clearSelection: () => void } | null>(null)

const poolItems = ref<any[]>([])
const poolLoading = ref(false)
const warehouses = ref<Warehouse[]>([])
const selectedRows = ref<any[]>([])
const advOpen = ref<string[]>([])
const autoOverriddenClusters = ref<Set<number>>(new Set())
const urgentOrderIds = ref<Set<number>>(new Set())
const isOrderDisplayUrgent = (row: any) => urgentOrderIds.value.has(row.orderId)
const autoExpandedClusters = ref<number[]>([])

const params = reactive({
  k: null as number | null,
  useHub: true,
  plannedShipTime: null as string | null,
})

const preview = ref<any>(null)
const previewLoading = ref(false)
const batchOverrides = reactive<Record<number, { useHub: boolean }>>({})
const executeLoading = ref(false)
const executeResults = ref<any[]>([])

const urgentDisplayCount = computed(() => urgentOrderIds.value.size)

function getClusterOrigin(cluster: any) {
  const orders: any[] = cluster.orders ?? []
  const hubOrder = orders.find((o: any) => o.dispatchOriginType === 1)
  if (hubOrder) {
    return {
      warehouseId: null as number | null,
      warehouseLat: hubOrder.dispatchOriginLat ?? 0,
      warehouseLng: hubOrder.dispatchOriginLng ?? 0,
      warehouseAddress: hubOrder.dispatchOriginAddr ?? '干线到达Hub',
    }
  }
  const firstOrder = orders[0]
  const wh = firstOrder ? warehouses.value.find((w: Warehouse) => w.id === firstOrder.warehouseId) : null
  return {
    warehouseId: wh?.id ?? null as number | null,
    warehouseLat: wh?.latitude ?? 0,
    warehouseLng: wh?.longitude ?? 0,
    warehouseAddress: [wh?.province, wh?.city, wh?.district, wh?.detailAddress]
      .filter(Boolean).join('') || wh?.warehouseName || '',
  }
}

function nationalHubOptionLabel(h: { name?: string; city?: string; province?: string }) {
  const tail = [h.city, h.province].filter(Boolean).join(' ')
  return tail ? `${h.name || 'Hub'}（${tail}）` : (h.name || `Hub`)
}

const hubNameById = computed(() => {
  const m: Record<number, string> = {}
  for (const h of nationalHubOptions.value) {
    m[h.id] = nationalHubOptionLabel(h)
  }
  return m
})

function destHubDisplayName(id: number | null | undefined) {
  if (id == null) return '—'
  return hubNameById.value[id] ?? `#${id}`
}

async function loadNationalHubOptions() {
  try {
    const res = await getNationalHubs()
    nationalHubOptions.value = res.data ?? []
  } catch { /* 非致命 */ }
}

function onDestHubFilterChange() {
  selectedRows.value = []
  poolTableRef.value?.clearSelection?.()
  loadPool()
}

onMounted(() => {
  loadNationalHubOptions()
  loadPool()
  loadWarehouses()
})

const loadPool = async () => {
  poolLoading.value = true
  try {
    const res = await getDispatchPool(filterDestHubId.value)
    poolItems.value = res.data ?? []
  } catch {
    ElMessage.error('加载待调度订单失败')
  } finally {
    poolLoading.value = false
  }
  checkUrgency()
}

const checkUrgency = async () => {
  if (poolItems.value.every(o => !o.remark)) return
  try {
    const res = await checkDispatchUrgency(filterDestHubId.value)
    urgentOrderIds.value = new Set<number>(res.data ?? [])
  } catch { /* 静默 */ }
}

const loadWarehouses = async () => {
  try {
    const res = await getWarehouses()
    warehouses.value = (res.data ?? []).filter((w: Warehouse) => w.status === 1)
  } catch { /* ignore */ }
}

const handleSelectionChange = (rows: any[]) => {
  selectedRows.value = rows
}

const translateStrategy = (strategy?: string): string => {
  const map: Record<string, string> = {
    MULTI_HUB: '多分拨中心配送',
    SINGLE_HUB: '单分拨中心配送',
    DIRECT: '全程直接送达',
    HUB: '经分拨中心配送',
    MIXED: '混合配送',
  }
  return strategy ? (map[strategy] ?? strategy) : '—'
}

const translateUrgency = (urgency?: string): string => {
  const map: Record<string, string> = { HIGH: '紧急', MEDIUM: '正常', LOW: '宽松' }
  return map[urgency ?? ''] ?? '正常'
}

const urgencyTag = (urgency?: string) => {
  const map: Record<string, string> = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }
  return map[urgency ?? ''] ?? 'info'
}

const handlePreview = async () => {
  const targetRows = selectedRows.value.length > 0 ? selectedRows.value : poolItems.value
  if (targetRows.length === 0) {
    ElMessage.warning('调度池暂无待调度订单')
    return
  }
  const orderIds = targetRows.map((r: any) => r.orderId as number)
  previewLoading.value = true
  preview.value = null
  executeResults.value = []
  try {
    const res = await previewDispatch(
      params.k ?? undefined,
      undefined,
      orderIds,
      undefined,
      undefined,
      !params.useHub,
      filterDestHubId.value,
    )
    preview.value = res.data
    autoOverriddenClusters.value = new Set<number>()
    for (const c of (res.data?.clusters ?? [])) {
      const sug = getBatchSuggestion(c.clusterId)
      const hasHubOrigin = (c.orders ?? []).some((o: any) => o.dispatchOriginType === 1)
      let useHub = hasHubOrigin ? false : (sug?.useHub ?? params.useHub)
      if (useHub && (c.orders?.length ?? 0) <= 1) {
        useHub = false
        autoOverriddenClusters.value.add(c.clusterId)
      }
      batchOverrides[c.clusterId] = { useHub }
    }
    autoExpandedClusters.value = [...autoOverriddenClusters.value]
  } catch {
    ElMessage.error('生成调度方案失败，请稍后重试')
  } finally {
    previewLoading.value = false
  }
}

const getBatchSuggestion = (clusterId: number) => {
  return preview.value?.llmAdvice?.batchSuggestions?.find((s: any) => s.clusterId === clusterId)
}

const handleExecute = async () => {
  const clusters: any[] = preview.value?.clusters ?? []
  if (clusters.length === 0) {
    ElMessage.warning('没有可执行的批次')
    return
  }
  executeLoading.value = true
  try {
    const batches: ExecuteBatchItem[] = clusters.map(c => {
      const orderIds = c.orders.map((o: any) => o.orderId)
      const orderItems = c.orders.map((o: any) => ({
        orderId: o.orderId,
        endLat: o.endLat,
        endLng: o.endLng,
        endAddress: o.endAddress,
        receiverName: o.receiverName,
        receiverPhone: o.receiverPhone,
      }))
      const override = batchOverrides[c.clusterId]
      const sug = getBatchSuggestion(c.clusterId)
      const origin = getClusterOrigin(c)
      const hasCrossCity = (c.orders ?? []).some((o: any) => o.dispatchOriginType === 1)
      return {
        orderIds,
        warehouseId: origin.warehouseId ?? 0,
        warehouseLat: origin.warehouseLat,
        warehouseLng: origin.warehouseLng,
        warehouseAddress: origin.warehouseAddress,
        useHub: override?.useHub ?? params.useHub,
        isCrossCity: hasCrossCity || undefined,
        hubId: sug?.suggestedHubId ?? undefined,
        plannedShipTime: params.plannedShipTime ?? undefined,
        orderItems,
      }
    })
    const res = await executeDispatch(batches)
    executeResults.value = res.data ?? []
    ElMessage.success(`调度成功！共创建 ${executeResults.value.length} 个批次`)
    preview.value = null
    await loadPool()
  } catch (e: any) {
    ElMessage.error(typeof e === 'string' ? e : '调度执行失败，请稍后重试')
  } finally {
    executeLoading.value = false
  }
}
</script>

<style scoped>
.local-city-dispatch {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 14px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}
.toolbar-left { display: flex; align-items: center; gap: 10px; }
.toolbar-right { display: flex; gap: 10px; align-items: center; }
.toolbar-filters { flex-wrap: wrap; }
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
}
.pool-count-tag { font-size: 13px; }
.main-layout {
  display: grid;
  grid-template-columns: 460px 1fr;
  gap: 16px;
  flex: 1;
  min-height: 0;
}
.pool-card, .preview-side { overflow-y: auto; }
.preview-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title { font-weight: 600; color: #303133; }
.pool-footer { margin-top: 6px; }
.params-card :deep(.el-form-item) { margin-bottom: 10px; }
.tip-text { margin-left: 8px; font-size: 12px; color: #909399; }
.adv-collapse { border: none; margin-top: -4px; }
.adv-collapse :deep(.el-collapse-item__header) {
  font-size: 12px; color: #909399; border: none; height: 28px;
}
.adv-collapse :deep(.el-collapse-item__wrap) { border: none; }
.summary-desc { margin-bottom: 4px; }
.cluster-list { margin-top: 8px; }
.cluster-title {
  display: flex; align-items: center; gap: 8px; width: 100%;
}
.cluster-info { color: #606266; font-size: 13px; flex: 1; }
.cluster-body { padding: 4px 0 8px; }
.cluster-note {
  font-size: 13px; color: #606266; margin: 4px 0 8px;
  padding: 6px 10px; background: #f0f9eb; border-radius: 4px;
}
.addr-cell { display: flex; align-items: center; gap: 4px; overflow: hidden; }
.addr-text {
  flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: default;
}
.urgent-tag { flex-shrink: 0; cursor: default; }
.remark-text { font-size: 12px; color: #909399; cursor: default; }
.text-muted { color: #c0c4cc; font-size: 12px; }
.pool-hint { font-size: 12px; color: #606266; }
.execute-bar {
  display: flex; align-items: center; gap: 12px; margin-top: 14px; padding-top: 14px;
  border-top: 1px solid #ebeef5;
}
</style>
