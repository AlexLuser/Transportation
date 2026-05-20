<template>
  <div class="flow-plan-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>全国运力规划</span>
          <el-button type="primary" size="small" :loading="loading" @click="loadLatest">
            加载最新规划
          </el-button>
        </div>
      </template>

      <el-empty v-if="!plan" description="暂无规划数据，请在「干线调度」页触发运力规划" />

      <template v-else>
        <el-alert type="info" show-icon :closable="false" style="margin-bottom:16px">
          以下为各线路<strong>预估运力分配方案</strong>，用于车辆运力与成本参考；
          实际可执行批次与实单数量以「全国干线 → 干线调度」页为准。
        </el-alert>
        <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="规划日期">{{ plan.plan?.planDate }}</el-descriptions-item>
          <el-descriptions-item label="参考需求量">{{ plan.plan?.totalDemand }} 件</el-descriptions-item>
          <el-descriptions-item label="预估总费用">¥{{ plan.plan?.totalCost }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(plan.plan?.status)" size="small">
              {{ statusLabel(plan.plan?.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-collapse v-model="llmOpen" class="llm-collapse">
          <el-collapse-item
            v-if="(plan.calibrations?.length ?? 0) > 0"
            title="线路费率校准"
            name="c"
          >
            <p class="hint">规划前对各线路的运输费率进行了智能校准，已参与本次规划。</p>
            <el-table :data="plan.calibrations" size="small" stripe>
              <el-table-column label="线路" min-width="170">
                <template #default="{ row: c }">{{ c.routeLabel || ('链路 #' + c.linkId) }}</template>
              </el-table-column>
              <el-table-column label="调整倍率" width="88" align="center">
                <template #default="{ row: c }">
                  {{ typeof c.multiplier === 'number' ? c.multiplier.toFixed(2) : c.multiplier }}
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="校准说明" min-width="200" show-overflow-tooltip />
              <el-table-column label="来源" width="86" align="center">
                <template #default="{ row: c }">
                  <el-tag v-if="c.llmEnhanced" type="success" size="small">智能</el-tag>
                  <el-tag v-else type="info" size="small">默认</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
          <el-collapse-item
            v-if="plan.llmFlowAdvice || plan.plan?.llmAdvice"
            title="规划分析建议"
            name="a"
          >
            <template v-if="plan.llmFlowAdvice">
              <p v-if="plan.llmFlowAdvice.bottleneckAnalysis" class="llm-block">
                <strong>瓶颈与负载</strong><br>{{ plan.llmFlowAdvice.bottleneckAnalysis }}
              </p>
              <p v-if="plan.llmFlowAdvice.costAnomalyWarning" class="llm-block">
                <strong>成本与异常</strong><br>{{ plan.llmFlowAdvice.costAnomalyWarning }}
              </p>
              <div v-if="plan.llmFlowAdvice.suggestions?.length" class="llm-block">
                <strong>优化建议</strong>
                <ul class="sug"><li v-for="(s, i) in plan.llmFlowAdvice.suggestions" :key="i">{{ s }}</li></ul>
              </div>
              <p v-if="plan.llmFlowAdvice.summary" class="llm-block"><strong>摘要</strong><br>{{ plan.llmFlowAdvice.summary }}</p>
            </template>
            <p v-else-if="plan.plan?.llmAdvice" class="text-muted llm-block" style="white-space:pre-wrap">
              {{ plan.plan.llmAdvice }}
            </p>
          </el-collapse-item>
        </el-collapse>

        <el-divider>各线路运力分配（仅供参考）</el-divider>
        <el-table :data="plan.items || []" stripe size="small">
          <el-table-column label="线路" min-width="200">
            <template #default="{ row }">{{ row.fromHubName }} → {{ row.toHubName }}</template>
          </el-table-column>
          <el-table-column label="运输方式" width="80">
            <template #default="{ row }">{{ transportModeLabel(row.transportMode) }}</template>
          </el-table-column>
          <el-table-column prop="flowAmount" label="预估运量" width="100" />
          <el-table-column prop="capacityDaily" label="日额定运量" width="100" />
          <el-table-column label="负载率" width="120">
            <template #default="{ row }">
              <el-progress
                :percentage="Math.round(row.loadRate * 100)"
                :status="row.loadRate > 0.8 ? 'exception' : row.loadRate > 0.6 ? 'warning' : ''"
                :stroke-width="8"
              />
            </template>
          </el-table-column>
          <el-table-column label="单价(元)" width="90">
            <template #default="{ row }">
              {{ row.edgeCost }}
              <span v-if="row.edgeCost !== row.baseCost" class="calibrated">(↑{{ row.baseCost }})</span>
            </template>
          </el-table-column>
          <el-table-column label="小计(元)" width="90">
            <template #default="{ row }">¥{{ row.totalCost }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getLatestFlowPlan } from '@/api/logistics'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const plan = ref<any>(null)
const llmOpen = ref<string[]>(['c', 'a'])

async function loadLatest() {
  loading.value = true
  try {
    const res = await getLatestFlowPlan()
    plan.value = res.data ?? null
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function statusTagType(status: string) {
  return { DONE: 'success', PENDING: 'info', OPTIMIZING: 'warning', FAILED: 'danger' }[status ?? ''] ?? ''
}

function statusLabel(status: string) {
  return { DONE: '已完成', PENDING: '待规划', OPTIMIZING: '规划中', FAILED: '规划失败' }[status ?? ''] ?? status ?? '-'
}

function transportModeLabel(mode?: string) {
  if (mode == null || mode === '') return '—'
  const m: Record<string, string> = { ROAD: '公路', RAIL: '铁路', AIR: '航空', SEA: '水运' }
  return m[mode] ?? '其它'
}

onMounted(loadLatest)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.calibrated { color: #999; font-size: 12px; }
.llm-collapse { margin-bottom: 12px; }
.hint { font-size: 12px; color: #909399; margin: 0 0 10px; }
.llm-block { font-size: 13px; color: #606266; line-height: 1.65; margin: 0 0 12px; }
.llm-block strong { color: #303133; }
.sug { margin: 6px 0 0; padding-left: 1.2em; }
.text-muted { color: #909399; }
</style>
