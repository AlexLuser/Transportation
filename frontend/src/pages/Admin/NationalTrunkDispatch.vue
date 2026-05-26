<template>
  <div class="national-trunk-dispatch">
    <el-card shadow="never">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">全国干线规划与批次</span>
          <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
            <el-date-picker
              v-model="llmAsOfDate"
              type="date"
              placeholder="模拟今日（可选）"
              value-format="YYYY-MM-DD"
              clearable
              style="width:188px"
            />
            <el-tooltip
              content="仅决定费用校准模型眼中的「今日」；订单与天气/负载仍按系统真实数据，且不会向模型说明这些参考信息的日期来源。"
              placement="top"
            >
              <span class="text-muted" style="font-size:12px;max-width:220px">模拟今日</span>
            </el-tooltip>
            <el-button type="primary" :icon="MagicStick"
                       :loading="planLoading" @click="handleTriggerPlan">
              立即规划
            </el-button>
            <el-button :icon="Refresh" :loading="batchLoading" @click="loadNationalBatches">
              刷新批次
            </el-button>
          </div>
        </div>
      </template>

      <el-divider>干线批次与实单</el-divider>
      <el-table
        :data="nationalBatches"
        size="small"
        stripe
        v-loading="batchLoading"
        row-key="id"
      >
        <el-table-column type="expand" width="44">
          <template #default="{ row }">
            <div class="batch-expand" v-if="row.orders && row.orders.length > 0">
              <el-table :data="row.orders" size="small" border class="order-lines-inner">
                <el-table-column prop="orderNo" label="订单号" min-width="160" show-overflow-tooltip />
                <el-table-column label="批次起点" min-width="220">
                  <template #default="{ row: o }">
                    {{ o.originHubName }} → {{ o.destHubName }}
                  </template>
                </el-table-column>
                <el-table-column label="当前状态" width="100" align="center">
                  <template #default="{ row: o }">
                    <el-tag size="small">{{ trunkOrderStatusLabel(o.orderStatus) }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <el-empty v-else description="本批无绑定实单" :image-size="56" />
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次号" width="200" show-overflow-tooltip />
        <el-table-column label="本段运输边" min-width="180">
          <template #default="{ row }">
            <span class="dir-edge">{{ row.fromHubName || '?' }} → {{ row.toHubName || '?' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="方式" width="72">
          <template #default="{ row }">{{ transportModeLabel(row.transportMode) }}</template>
        </el-table-column>
        <el-table-column prop="itemCount" label="实单数" width="72" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="batchStatusType(row.status)" size="small">{{ batchStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'CREATED'"
              type="warning" size="small"
              :disabled="!row.itemCount"
              @click="handleDepart(row.id)"
            >发车</el-button>
            <el-button
              v-if="row.status === 'DEPARTED'"
              type="success" size="small"
              :disabled="!row.itemCount"
              @click="handleArrive(row.id)"
            >标记到达</el-button>
            <el-tag v-if="row.status === 'ARRIVED'" type="success" size="small">已到达</el-tag>
            <el-tag v-if="row.status === 'DISPATCHED'" type="info" size="small">已调度</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!batchLoading && nationalBatches.length === 0"
        description="尚无干线批次。请先执行「立即规划」或检查当日是否有可绑定的跨城待揽件订单。"
      />

      <el-divider style="margin-top:20px">全国物流实时网络</el-divider>
      <p class="section-hint" style="margin-bottom:8px">
        地图展示干线批次与规划线路（<span style="color:#c0c4cc">■ 预规划</span>
        <span style="color:#409eff">■ 待发车</span>
        <span style="color:#e6a23c">■ 运输中</span>
        <span style="color:#67c23a">■ 已到达</span>）。
        尚未激活的预规划段显示 MCMF 预估件数；有实单后显示实单数。
      </p>
      <div v-loading="topologyLoading || batchLoading">
        <el-empty
          v-if="!topologyLoading && !batchLoading && nationalBatches.length === 0 && (!topology?.hubs?.length)"
          description="暂无在途批次，网络地图将在首次规划后显示"
          :image-size="60"
        />
        <NationalNetworkMap
          v-else
          :hubs="topology?.hubs ?? []"
          :edges="networkMapEdges"
          :batches="networkMapBatches"
          :active-batch-counts="networkMapActiveBatchCounts"
        />
      </div>

      <template v-if="latestPlan">
        <el-descriptions
          :column="3" border size="small" class="plan-meta" style="margin-top:12px"
        >
          <el-descriptions-item label="最近规划日期">{{ latestPlan.plan?.planDate }}</el-descriptions-item>
          <el-descriptions-item label="实单合计">{{ nationalRealOrderTotal }} 单</el-descriptions-item>
          <el-descriptions-item label="规划件数合计">{{ latestPlan.plan?.totalDemand }} 件</el-descriptions-item>
          <el-descriptions-item label="预估总费用">¥{{ latestPlan.plan?.totalCost }}</el-descriptions-item>
          <el-descriptions-item label="规划状态">
            <el-tag :type="planStatusType(latestPlan.plan?.status)" size="small">
              {{ planStatusLabel(latestPlan.plan?.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-collapse v-model="mcmfCollapse" class="mcmf-collapse">
          <el-collapse-item
            v-if="(latestPlan.calibrations?.length ?? 0) > 0"
            title="线路费率校准"
            name="llm-calib"
          >
            <p class="llm-panel-hint">以下为规划前对各运输边的费用倍率估算，已参与当次 MCMF 求解。</p>
            <el-table :data="latestPlan.calibrations" size="small" stripe>
              <el-table-column label="线路" min-width="170">
                <template #default="{ row: c }">
                  {{ c.routeLabel || ('链路 #' + c.linkId) }}
                </template>
              </el-table-column>
              <el-table-column label="费用倍率" width="88" align="center">
                <template #default="{ row: c }">
                  {{ typeof c.multiplier === 'number' ? c.multiplier.toFixed(2) : c.multiplier }}
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="说明" min-width="200" show-overflow-tooltip />
              <el-table-column label="来源" width="86" align="center">
                <template #default="{ row: c }">
                  <el-tag v-if="c.llmEnhanced" type="success" size="small">模型</el-tag>
                  <el-tag v-else type="info" size="small">默认</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
          <el-collapse-item
            v-if="latestPlan.llmFlowAdvice || latestPlan.plan?.llmAdvice"
            title="规划分析建议"
            name="llm-advice"
          >
            <template v-if="latestPlan.llmFlowAdvice">
              <p v-if="latestPlan.llmFlowAdvice.bottleneckAnalysis" class="llm-text-block">
                <strong>瓶颈与负载</strong><br>
                {{ latestPlan.llmFlowAdvice.bottleneckAnalysis }}
              </p>
              <p v-if="latestPlan.llmFlowAdvice.costAnomalyWarning" class="llm-text-block">
                <strong>成本与异常提示</strong><br>
                {{ latestPlan.llmFlowAdvice.costAnomalyWarning }}
              </p>
              <div v-if="latestPlan.llmFlowAdvice.suggestions?.length" class="llm-text-block">
                <strong>操作建议</strong>
                <ul class="llm-suggestion-list">
                  <li v-for="(s, i) in latestPlan.llmFlowAdvice.suggestions" :key="i">{{ s }}</li>
                </ul>
              </div>
              <p v-if="latestPlan.llmFlowAdvice.summary" class="llm-text-block llm-summary">
                <strong>摘要</strong><br>
                {{ latestPlan.llmFlowAdvice.summary }}
              </p>
              <el-tag v-if="latestPlan.llmFlowAdvice.llmEnhanced === false" type="warning" size="small" style="margin-top:6px">
                本次解读为降级结果（模型不可用或解析失败）
              </el-tag>
            </template>
            <p v-else-if="latestPlan.plan?.llmAdvice" class="llm-text-block" style="white-space:pre-wrap">
              {{ latestPlan.plan.llmAdvice }}
            </p>
          </el-collapse-item>
          <el-collapse-item title="各线路流量与负载参考" name="mcmf">
            <el-table :data="latestPlan.items || []" size="small" stripe>
              <el-table-column label="线路" min-width="200">
                <template #default="{ row }">{{ row.fromHubName }} → {{ row.toHubName }}</template>
              </el-table-column>
              <el-table-column label="方式" width="72">
                <template #default="{ row }">{{ transportModeLabel(row.transportMode) }}</template>
              </el-table-column>
              <el-table-column prop="flowAmount" label="边流量" width="80" />
              <el-table-column prop="capacityDaily" label="日容量" width="80" />
              <el-table-column label="流/容" width="100">
                <template #default="{ row }">
                  <el-progress
                    :percentage="Math.round(row.loadRate * 100)"
                    :status="row.loadRate > 0.8 ? 'exception' : ''"
                    :stroke-width="8"
                  />
                </template>
              </el-table-column>
              <el-table-column label="小计(元)" width="90">
                <template #default="{ row }">¥{{ row.totalCost }}</template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, MagicStick } from '@element-plus/icons-vue'
import {
  triggerNationalPlan,
  getNationalBatches,
  departBatch,
  arriveBatch,
  getLatestFlowPlan,
  getNationalTopology
} from '@/api/logistics'
import NationalNetworkMap from '@/components/NationalNetworkMap.vue'

const planLoading = ref(false)
const llmAsOfDate = ref<string | null>(null)
const batchLoading = ref(false)
const latestPlan = ref<any>(null)
const nationalBatches = ref<any[]>([])
const mcmfCollapse = ref<string[]>([])

const topologyLoading = ref(false)
const topology = ref<{ hubs: any[], links: any[] } | null>(null)

const networkMapEdges = computed(() =>
  (latestPlan.value?.items ?? [])
    .filter((it: any) => (it.flowAmount ?? 0) > 0)
    .map((it: any) => ({
      fromHubId: it.fromHubId,
      toHubId: it.toHubId,
      flowAmount: it.flowAmount,
      loadRate: it.loadRate ?? 0,
      transportMode: it.transportMode ?? '',
    }))
)

/** MCMF 规划边流量：批次尚未激活（CHAINED）时用于地图展示预估件数 */
const planFlowByEdge = computed(() => {
  const map: Record<string, number> = {}
  for (const it of latestPlan.value?.items ?? []) {
    const amount = Number(it.flowAmount) || 0
    if (amount > 0) {
      map[`${it.fromHubId}-${it.toHubId}`] = amount
    }
  }
  return map
})

const networkMapActiveBatchCounts = computed(() => {
  const counts: Record<number, number> = {}
  for (const b of nationalBatches.value) {
    if (b.status === 'CREATED' || b.status === 'DEPARTED') {
      if (b.fromHubId) counts[b.fromHubId] = (counts[b.fromHubId] ?? 0) + 1
    }
  }
  return counts
})

const networkMapBatches = computed(() =>
  nationalBatches.value.map((b: any) => {
    const itemCount = Number(b.itemCount) || 0
    const plannedCount = planFlowByEdge.value[`${b.fromHubId}-${b.toHubId}`] ?? 0
    return {
      id: b.id,
      fromHubId: b.fromHubId,
      toHubId: b.toHubId,
      itemCount,
      plannedCount: itemCount > 0 ? undefined : plannedCount,
      status: b.status,
      batchNo: b.batchNo,
      fromHubName: b.fromHubName,
      toHubName: b.toHubName,
    }
  })
)

const nationalRealOrderTotal = computed(() =>
  nationalBatches.value.reduce((s, b) => s + (Number(b.itemCount) || 0), 0)
)

async function loadTopology() {
  topologyLoading.value = true
  try {
    const res = await getNationalTopology()
    topology.value = res.data ?? null
  } catch { /* 静默 */ } finally {
    topologyLoading.value = false
  }
}

function trunkOrderStatusLabel(status: number | null | undefined) {
  if (status == null) return '—'
  const m: Record<number, string> = {
    0: '待支付', 1: '待发货', 2: '待揽件', 3: '派送中', 4: '已完成', 5: '已取消'
  }
  return m[status] ?? `状态${status}`
}

async function handleTriggerPlan() {
  planLoading.value = true
  try {
    const res = await triggerNationalPlan(undefined, llmAsOfDate.value ?? undefined)
    latestPlan.value = res.data ?? null
    ElMessage.success('干线规划已完成')
    loadNationalBatches()
  } catch (e: any) {
    ElMessage.error('规划异常: ' + (e.message || ''))
  } finally {
    planLoading.value = false
  }
}

async function loadNationalBatches() {
  batchLoading.value = true
  try {
    const res = await getNationalBatches()
    nationalBatches.value = res.data ?? []
  } finally {
    batchLoading.value = false
  }
}

async function handleDepart(batchId: number) {
  try {
    await departBatch(batchId)
    ElMessage.success('已标记发车')
    loadNationalBatches()
  } catch { ElMessage.error('操作失败') }
}

async function handleArrive(batchId: number) {
  try {
    await arriveBatch(batchId)
    ElMessage.success('已标记到达：最终目的地订单已入末端调度池，中转订单已自动衔接下一跳批次')
    loadNationalBatches()
  } catch { ElMessage.error('操作失败') }
}

function planStatusType(status: string) {
  return ({ DONE: 'success', PENDING: 'info', OPTIMIZING: 'warning', FAILED: 'danger' } as any)[status] ?? ''
}

function planStatusLabel(status: string | undefined) {
  if (status == null || status === '') return '—'
  const m: Record<string, string> = {
    DONE: '已完成', PENDING: '待执行', OPTIMIZING: '计算中', FAILED: '未成功',
  }
  return m[status] ?? '处理中'
}

function batchStatusType(status: string) {
  return ({ CHAINED: 'primary', CREATED: 'info', DEPARTED: 'warning', ARRIVED: 'success', DISPATCHED: '' } as any)[status] ?? ''
}

function batchStatusLabel(status: string) {
  const m: Record<string, string> = {
    CHAINED: '待衔接', CREATED: '待发车', DEPARTED: '运输中', ARRIVED: '已到达', DISPATCHED: '已调度',
  }
  return m[status] ?? '进行中'
}

function transportModeLabel(mode: string | null | undefined) {
  if (mode == null || mode === '') return '—'
  const m: Record<string, string> = { ROAD: '公路', RAIL: '铁路', AIR: '航空', SEA: '水运' }
  return m[mode] ?? '其它'
}

onMounted(() => {
  loadNationalBatches()
  loadTopology()
  getLatestFlowPlan().then(res => {
    latestPlan.value = res.data ?? null
  }).catch(() => {})
})
</script>

<style scoped>
.national-trunk-dispatch { display: flex; flex-direction: column; gap: 0; }
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title { font-weight: 600; color: #303133; }
.text-muted { color: #c0c4cc; font-size: 12px; }
.section-hint {
  font-size: 12px;
  color: #909399;
  margin: 0 0 10px;
  line-height: 1.5;
}
.batch-expand {
  padding: 8px 12px 12px 48px;
  background: #fafafa;
}
.order-lines-inner { max-width: 900px; }
.dir-edge { font-weight: 500; }
.mcmf-collapse { margin-top: 8px; }
.llm-panel-hint {
  font-size: 12px;
  color: #909399;
  margin: 0 0 10px;
  line-height: 1.5;
}
.llm-text-block {
  font-size: 13px;
  color: #606266;
  line-height: 1.65;
  margin: 0 0 12px;
}
.llm-text-block strong { color: #303133; }
.llm-summary { margin-bottom: 0; }
.llm-suggestion-list { margin: 6px 0 0; padding-left: 1.2em; }
</style>
