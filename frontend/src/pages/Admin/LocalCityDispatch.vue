<template>
  <div class="local-city-dispatch">

    <!-- 页面顶部标题行 -->
    <div class="page-header">
      <h2 class="page-title">
        <el-icon style="margin-right:6px"><Promotion /></el-icon>
        城市末端调度
      </h2>
      <div class="header-tags">
        <el-tag type="info" size="small">待调度：{{ poolItems.length }} 单</el-tag>
        <el-tag v-if="urgentDisplayCount > 0" type="danger" size="small">⚡ {{ urgentDisplayCount }} 单急送</el-tag>
      </div>
    </div>

    <!-- 主 Tab -->
    <el-tabs v-model="mainTab" class="main-tabs" @tab-change="handleMainTabChange">

      <!-- ========== Tab 1: 调度计划 ========== -->
      <el-tab-pane label="调度计划" name="plan">
        <div class="tab-toolbar">
          <el-select v-model="filterDestHubId" placeholder="全国收货 Hub（全部）"
                     clearable filterable style="width:220px" @change="onDestHubFilterChange">
            <el-option v-for="h in nationalHubOptions" :key="h.id"
                       :label="nationalHubOptionLabel(h)" :value="h.id" />
          </el-select>
          <el-button :icon="Refresh" @click="loadPool" :loading="poolLoading">刷新</el-button>
          <el-button type="primary" :icon="MagicStick" @click="handlePreview"
                     :loading="previewLoading" :disabled="poolItems.length === 0">
            生成调度方案
          </el-button>
        </div>

        <div class="main-layout">
          <!-- 左：待调度订单池 -->
          <el-card class="pool-card" shadow="never">
            <template #header>
              <div class="card-header-row">
                <span class="card-title">待调度订单</span>
                <span class="pool-hint text-muted" v-if="selectedRows.length > 0">
                  已勾选 {{ selectedRows.length }} 单
                </span>
              </div>
            </template>

            <el-table ref="poolTableRef" :data="poolItems" size="small"
                      v-loading="poolLoading" max-height="520"
                      @selection-change="handleSelectionChange" row-key="orderId">
              <el-table-column type="selection" width="42" />
              <el-table-column label="单号" prop="orderId" width="68" align="center" />
              <el-table-column label="来源" width="120">
                <template #default="{ row }">
                  <el-tag v-if="row.dispatchOriginType === 1" type="success" size="small" effect="plain">
                    🚉 Hub到达
                  </el-tag>
                  <el-tag v-else type="info" size="small" effect="plain">🏭 仓库发货</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="收货Hub" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ destHubDisplayName(row.destHubId) }}</template>
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
              <el-alert
                v-if="poolItems.filter((o: any) => !o.endLat).length > 0"
                :title="`${poolItems.filter((o: any) => !o.endLat).length} 个订单地址坐标不完整，将暂时跳过调度`"
                type="warning" show-icon :closable="false" style="margin-top:8px" />
            </div>
          </el-card>

          <!-- 右：参数 + 预览 -->
          <div class="preview-side">
            <el-card shadow="never" class="params-card">
              <template #header><span class="card-title">调度参数</span></template>
              <el-form :model="params" label-width="110px" size="small">
                <el-form-item label="配送方式">
                  <el-switch v-model="params.useHub"
                             active-text="经分拨中心配送" inactive-text="直接送达" />
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

              <p v-if="preview.llmAdvice?.reason" class="preview-reason text-muted">
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
                                type="warning" show-icon :closable="false" style="margin-bottom:8px" />
                      <p class="cluster-note"
                         v-if="!autoOverriddenClusters.has(c.clusterId) && getBatchSuggestion(c.clusterId)?.note">
                        💡 {{ getBatchSuggestion(c.clusterId)?.note }}
                      </p>
                      <el-checkbox v-model="batchOverrides[c.clusterId].useHub" label="经分拨中心配送" />
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
          </div>
        </div>
      </el-tab-pane>

      <!-- ========== Tab 2: 司机分配 ========== -->
      <el-tab-pane name="dispatch">
        <template #label>
          <span>
            司机分配
            <el-badge
              v-if="pendingDispatchCount > 0"
              :value="pendingDispatchCount"
              type="warning"
              style="margin-left:4px;vertical-align:middle" />
          </span>
        </template>

        <div class="tab-toolbar">
          <el-button :icon="Refresh" @click="loadPendingBatches" :loading="batchesLoading">刷新</el-button>
          <span class="result-hint">{{ batchesForDispatch.length }} 个批次待分配</span>
        </div>

        <el-card shadow="never" class="dispatch-tab-card" v-loading="batchesLoading">
          <el-alert
            title="请为每个批次指定干线司机（负责将货物运至分拨中心）和末端司机（货物到达分拨中心后自动通知接手配送）。"
            type="info" show-icon :closable="false" style="margin-bottom:16px" />

          <el-table
            :data="batchesForDispatch"
            row-key="id"
            :expand-row-keys="expandedRowKeys"
            @expand-change="handleBatchExpand"
            size="small"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="batch-expand-body" v-loading="batchDetailLoadingMap[row.id]">
                  <template v-if="batchDetailMap[row.id]">
                    <!-- 干线段 -->
                    <div class="segment-section" v-if="batchDetailMap[row.id].trunkRoute">
                      <div class="segment-label">
                        <el-tag type="primary" size="small">干线</el-tag>
                        <span class="segment-info">
                          {{ batchDetailMap[row.id].trunkRoute.route?.startAddress ?? '—' }}
                          →
                          {{ batchDetailMap[row.id].trunkRoute.route?.endAddress ?? '—' }}
                        </span>
                      </div>
                      <el-form label-width="90px" size="small" style="margin-top:10px">
                        <el-form-item label="指定司机" required>
                          <el-select v-model="batchForms[row.id].trunkDriverId"
                                     placeholder="选择干线司机" filterable style="width:220px">
                            <el-option v-for="d in driverOptions" :key="d.id"
                                       :label="`${d.realName || '未填写'} (${d.phone || '—'})`"
                                       :value="d.id" />
                          </el-select>
                        </el-form-item>
                        <el-form-item label="车辆（选填）">
                          <el-input v-model="batchForms[row.id].trunkVehicleId"
                                    placeholder="车辆ID（可留空）" style="width:160px" />
                        </el-form-item>
                      </el-form>
                    </div>

                    <!-- 末端段 -->
                    <div class="segment-section" style="margin-top:12px"
                         v-if="batchDetailMap[row.id].lastMileRoutes?.length">
                      <div class="segment-label">
                        <el-tag type="success" size="small">末端</el-tag>
                        <span class="segment-info">
                          共 {{ batchDetailMap[row.id].lastMileRoutes.length }} 条路线组
                        </span>
                        <span v-if="batchDetailMap[row.id].trunkRoute" class="text-muted">
                          （干线到站后自动生效，可暂不分配）
                        </span>
                        <span v-else class="text-muted">（直送批次，分配后立即生效）</span>
                      </div>
                      <el-table :data="batchDetailMap[row.id].lastMileRoutes" size="small" style="margin-top:10px">
                        <el-table-column label="路线组" width="70" align="center">
                          <template #default="{ $index }">组 {{ ($index as number) + 1 }}</template>
                        </el-table-column>
                        <el-table-column label="停靠点" width="70" align="center">
                          <template #default="{ row: r }">{{ r.route?.stopCount ?? 1 }} 个</template>
                        </el-table-column>
                        <el-table-column label="终点地址" show-overflow-tooltip>
                          <template #default="{ row: r }">{{ r.route?.endAddress }}</template>
                        </el-table-column>
                        <el-table-column label="指定司机" width="220">
                          <template #default="{ $index }">
                            <el-select
                              v-model="batchForms[row.id].lastMileDriverIds[$index]"
                              placeholder="选择末端司机（可选）"
                              filterable clearable size="small" style="width:200px">
                              <el-option v-for="d in driverOptions" :key="d.id"
                                         :label="`${d.realName || '未填写'} (${d.phone || '—'})`"
                                         :value="d.id" />
                            </el-select>
                          </template>
                        </el-table-column>
                      </el-table>
                    </div>

                    <!-- 提交按钮 -->
                    <div class="expand-footer">
                      <el-button type="primary" :loading="batchDispatchingMap[row.id]"
                                 @click="handleBatchDispatch(row)">
                        确认调度
                      </el-button>
                      <el-button @click="collapseRow(row.id)">取消</el-button>
                    </div>
                  </template>
                  <el-empty v-else-if="!batchDetailLoadingMap[row.id]" description="加载失败，请刷新重试" />
                </div>
              </template>
            </el-table-column>

            <el-table-column label="批次ID" prop="id" width="80" align="center" />
            <el-table-column label="创建时间" width="175">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="订单数" prop="totalOrders" width="80" align="center">
              <template #default="{ row }">{{ row.totalOrders ?? '—' }} 单</template>
            </el-table-column>
            <el-table-column label="配送方式" width="130">
              <template #default="{ row }">
                <el-tag v-if="row.useHub" type="primary" size="small" effect="plain">经分拨中心</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">直接送达</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="调度状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="dispatchedBatchIds.has(row.id)" type="success" size="small">已调度</el-tag>
                <el-tag v-else type="warning" size="small">待分配司机</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center">
              <template #default="{ row }">
                <el-button v-if="!dispatchedBatchIds.has(row.id)"
                           size="small" type="primary"
                           @click="toggleExpandRow(row)">
                  {{ expandedRowKeys.includes(String(row.id)) ? '收起' : '分配司机' }}
                </el-button>
                <el-tag v-else type="success" size="small">✓ 完成</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!batchesLoading && batchesForDispatch.length === 0"
                    description="暂无待分配批次。请先在「调度计划」中执行批次创建。" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
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
  adminDispatchBatch,
  listBatches,
  getBatchDetail,
  type ExecuteBatchItem,
} from '@/api/logistics'
import { getWarehouses, type Warehouse } from '@/api/shop'
import { getAllDriversForAdmin } from '@/api/driver'

// ──────── 公共 ────────────────────────────────────────────────────
const mainTab = ref<'plan' | 'dispatch'>('plan')
const formatDate = (d: string | null | undefined) =>
  d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : '—'

function handleMainTabChange(tab: string) {
  if (tab === 'dispatch') loadPendingBatches()
}

// ──────── Tab 1：调度计划 ─────────────────────────────────────────
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

const urgentDisplayCount = computed(() => urgentOrderIds.value.size)

function nationalHubOptionLabel(h: { name?: string; city?: string; province?: string }) {
  const tail = [h.city, h.province].filter(Boolean).join(' ')
  return tail ? `${h.name || 'Hub'}（${tail}）` : (h.name || 'Hub')
}

const hubNameById = computed(() => {
  const m: Record<number, string> = {}
  for (const h of nationalHubOptions.value) m[h.id] = nationalHubOptionLabel(h)
  return m
})

function destHubDisplayName(id: number | null | undefined) {
  if (id == null) return '—'
  return hubNameById.value[id] ?? `#${id}`
}

function onDestHubFilterChange() {
  selectedRows.value = []
  poolTableRef.value?.clearSelection?.()
  loadPool()
}

function handleSelectionChange(rows: any[]) { selectedRows.value = rows }

function getBatchSuggestion(clusterId: number) {
  return preview.value?.llmAdvice?.batchSuggestions?.find((s: any) => s.clusterId === clusterId)
}

function translateStrategy(strategy?: string): string {
  const map: Record<string, string> = {
    MULTI_HUB: '多分拨中心配送', SINGLE_HUB: '单分拨中心配送',
    DIRECT: '全程直接送达', HUB: '经分拨中心配送', MIXED: '混合配送',
  }
  return strategy ? (map[strategy] ?? strategy) : '—'
}

function translateUrgency(urgency?: string): string {
  return ({ HIGH: '紧急', MEDIUM: '正常', LOW: '宽松' } as any)[urgency ?? ''] ?? '正常'
}

function urgencyTag(urgency?: string) {
  return ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' } as any)[urgency ?? ''] ?? 'info'
}

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

const loadNationalHubOptions = async () => {
  try {
    const res = await getNationalHubs()
    nationalHubOptions.value = res.data ?? []
  } catch { /* 非致命 */ }
}

const handlePreview = async () => {
  const targetRows = selectedRows.value.length > 0 ? selectedRows.value : poolItems.value
  if (targetRows.length === 0) { ElMessage.warning('调度池暂无待调度订单'); return }
  const orderIds = targetRows.map((r: any) => r.orderId as number)
  previewLoading.value = true
  preview.value = null
  try {
    const res = await previewDispatch(
      params.k ?? undefined, undefined, orderIds,
      undefined, undefined, !params.useHub, filterDestHubId.value,
    )
    preview.value = res.data
    autoOverriddenClusters.value = new Set<number>()
    for (const c of (res.data?.clusters ?? [])) {
      const sug = getBatchSuggestion(c.clusterId)
      // 跨城到达（dispatchOriginType=1）与同城订单遵循相同策略：
      // useHub 由全局开关/LLM 建议决定，不因货源为全国 Hub 而强制直送
      let useHub = sug?.useHub ?? params.useHub
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

const handleExecute = async () => {
  const clusters: any[] = preview.value?.clusters ?? []
  if (clusters.length === 0) { ElMessage.warning('没有可执行的批次'); return }
  executeLoading.value = true
  try {
    const batches: ExecuteBatchItem[] = clusters.map(c => {
      const orderIds = c.orders.map((o: any) => o.orderId)
      const orderItems = c.orders.map((o: any) => ({
        orderId: o.orderId, endLat: o.endLat, endLng: o.endLng,
        endAddress: o.endAddress, receiverName: o.receiverName, receiverPhone: o.receiverPhone,
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
    const created = res.data ?? []
    ElMessage.success(`调度成功！共创建 ${created.length} 个批次，请切换到「司机分配」分配运输员。`)
    preview.value = null
    await loadPool()
    // 自动跳转到司机分配 Tab 并刷新列表
    mainTab.value = 'dispatch'
    await loadPendingBatches()
  } catch (e: any) {
    ElMessage.error(typeof e === 'string' ? e : '调度执行失败，请稍后重试')
  } finally {
    executeLoading.value = false
  }
}

// ──────── Tab 2：司机分配 ─────────────────────────────────────────
const driverOptions = ref<any[]>([])
const batchesForDispatch = ref<any[]>([])
const batchesLoading = ref(false)
const dispatchedBatchIds = ref<Set<number>>(new Set())

const pendingDispatchCount = computed(
  () => batchesForDispatch.value.filter(b => !dispatchedBatchIds.value.has(b.id)).length
)

// 展开行 key（el-table 要求字符串）
const expandedRowKeys = ref<string[]>([])

// 缓存批次详情（batchId → BatchDetailDTO）
const batchDetailMap = reactive<Record<number, any>>({})
const batchDetailLoadingMap = reactive<Record<number, boolean>>({})

// 每个批次的调度表单
const batchForms = reactive<Record<number, {
  trunkDriverId: number | null
  trunkVehicleId: string | number
  lastMileDriverIds: (number | null)[]
}>>({})

// 每个批次正在提交的状态
const batchDispatchingMap = reactive<Record<number, boolean>>({})

async function loadPendingBatches() {
  batchesLoading.value = true
  try {
    const res = await listBatches({ batchStatus: 0 })
    batchesForDispatch.value = res.data ?? []
  } catch {
    ElMessage.error('加载批次列表失败')
  } finally {
    batchesLoading.value = false
  }
  // 确保司机列表已加载
  if (driverOptions.value.length === 0) loadDriverOptions()
}

async function loadDriverOptions() {
  try {
    const res = await getAllDriversForAdmin()
    driverOptions.value = res.data ?? []
  } catch { /* 非致命 */ }
}

function initBatchForm(batchId: number, lastMileCount: number) {
  if (!batchForms[batchId]) {
    batchForms[batchId] = {
      trunkDriverId: null,
      trunkVehicleId: '',
      lastMileDriverIds: Array(lastMileCount).fill(null),
    }
  } else {
    // 末端数量可能变化，补齐
    while (batchForms[batchId].lastMileDriverIds.length < lastMileCount) {
      batchForms[batchId].lastMileDriverIds.push(null)
    }
  }
}

async function handleBatchExpand(row: any, expandedRows: any[]) {
  const batchId: number = row.id
  const isExpanding = expandedRows.some(r => r.id === batchId)
  if (!isExpanding) return

  // 加载批次详情（缓存）
  if (!batchDetailMap[batchId]) {
    batchDetailLoadingMap[batchId] = true
    try {
      const res = await getBatchDetail(batchId)
      batchDetailMap[batchId] = res.data
      const lastMileCount = res.data?.lastMileRoutes?.length ?? 0
      initBatchForm(batchId, lastMileCount)
    } catch {
      ElMessage.error('加载批次详情失败')
    } finally {
      batchDetailLoadingMap[batchId] = false
    }
  }
}

function toggleExpandRow(row: any) {
  const key = String(row.id)
  const idx = expandedRowKeys.value.indexOf(key)
  if (idx >= 0) expandedRowKeys.value.splice(idx, 1)
  else expandedRowKeys.value.push(key)
}

function collapseRow(batchId: number) {
  const key = String(batchId)
  const idx = expandedRowKeys.value.indexOf(key)
  if (idx >= 0) expandedRowKeys.value.splice(idx, 1)
}

async function handleBatchDispatch(row: any) {
  const batchId: number = row.id
  const form = batchForms[batchId]
  const detail = batchDetailMap[batchId]
  // 仅 Hub 中转批次（存在干线路线）才强制要求干线司机
  const hasTrunk = !!detail?.trunkRoute
  if (hasTrunk && !form?.trunkDriverId) {
    ElMessage.warning('请选择干线司机')
    return
  }

  const lastMileRoutes: any[] = batchDetailMap[batchId]?.lastMileRoutes ?? []
  const lastMileAssignments = lastMileRoutes
    .map((r: any, i: number) => ({
      routeId: r.route?.id as number,
      driverId: form.lastMileDriverIds[i] as number,
    }))
    .filter(a => a.routeId && a.driverId)

  const payload = {
    trunkDriverId: form.trunkDriverId,
    trunkVehicleId: form.trunkVehicleId ? Number(form.trunkVehicleId) : undefined,
    lastMileAssignments,
  }
  console.log('[DEBUG][handleBatchDispatch] batchId=', batchId, 'payload=', JSON.stringify(payload))

  batchDispatchingMap[batchId] = true
  try {
    const res = await adminDispatchBatch(batchId, payload)
    console.log('[DEBUG][handleBatchDispatch] 接口返回:', res)
    dispatchedBatchIds.value = new Set([...dispatchedBatchIds.value, batchId])
    collapseRow(batchId)
    ElMessage.success('调度完成！干线司机已接单，末端司机将在货物到达分拨中心后自动收到任务。')
  } catch (e: any) {
    console.error('[DEBUG][handleBatchDispatch] 调度失败:', e?.response?.data ?? e)
    ElMessage.error('调度失败：' + (e?.response?.data?.message ?? e?.message ?? '请稍后重试'))
  } finally {
    batchDispatchingMap[batchId] = false
  }
}

onMounted(() => {
  loadNationalHubOptions()
  loadPool()
  loadWarehouses()
})
</script>

<style scoped>
.local-city-dispatch {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  padding: 14px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
}

.header-tags { display: flex; gap: 6px; }

/* ── Tab 容器 ── */
.main-tabs { background: transparent; }

:deep(.el-tabs__header) {
  background: #fff;
  border-radius: 8px 8px 0 0;
  padding: 0 20px;
  margin-bottom: 0;
  border-bottom: 1px solid #ebeef5;
}

.tab-toolbar {
  display: flex; align-items: center; gap: 10px;
  background: #fff; padding: 12px 20px;
  border: 1px solid #ebeef5;
  border-top: none;
}
.result-hint { font-size: 13px; color: #606266; margin-left: auto; }

/* ── Tab 1: 主布局 ── */
.main-layout {
  display: grid;
  grid-template-columns: 460px 1fr;
  gap: 16px;
  padding: 16px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-top: none;
  border-radius: 0 0 8px 8px;
}

.pool-card, .preview-side { overflow-y: auto; }
.preview-side { display: flex; flex-direction: column; gap: 14px; }

.card-header-row { display: flex; align-items: center; justify-content: space-between; }
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
.preview-reason { margin: 8px 0; font-size: 13px; }
.cluster-list { margin-top: 8px; }
.cluster-title { display: flex; align-items: center; gap: 8px; width: 100%; }
.cluster-info { color: #606266; font-size: 13px; flex: 1; }
.cluster-body { padding: 4px 0 8px; }
.cluster-note {
  font-size: 13px; color: #606266; margin: 4px 0 8px;
  padding: 6px 10px; background: #f0f9eb; border-radius: 4px;
}
.addr-cell { display: flex; align-items: center; gap: 4px; overflow: hidden; }
.addr-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: default; }
.urgent-tag { flex-shrink: 0; cursor: default; }
.remark-text { font-size: 12px; color: #909399; cursor: default; }
.text-muted { color: #c0c4cc; font-size: 12px; }
.pool-hint { font-size: 12px; color: #606266; }
.execute-bar {
  display: flex; align-items: center; gap: 12px; margin-top: 14px; padding-top: 14px;
  border-top: 1px solid #ebeef5;
}

/* ── Tab 2: 司机分配 ── */
.dispatch-tab-card {
  border-radius: 0 0 8px 8px;
  border-top: none;
}

.batch-expand-body {
  padding: 16px 24px;
  background: #fafafa;
}

.segment-section {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
}

.segment-label { display: flex; align-items: center; gap: 8px; font-weight: 500; }
.segment-info { font-size: 13px; color: #606266; flex: 1; }

.expand-footer {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #ebeef5;
}
</style>
