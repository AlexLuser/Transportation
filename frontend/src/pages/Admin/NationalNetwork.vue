<template>
  <div class="national-network-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>全国物流网络拓扑</span>
          <el-button type="primary" size="small" :loading="loading" @click="loadTopology">刷新</el-button>
        </div>
      </template>

      <!-- 网络统计摘要 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <el-statistic title="全国枢纽" :value="hubCountByLevel(0)" suffix="个" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="省级中心" :value="hubCountByLevel(1)" suffix="个" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="城市配送中心" :value="hubCountByLevel(2)" suffix="个" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="运输边" :value="topology?.links?.length || 0" suffix="条" />
        </el-col>
      </el-row>

      <!-- Hub 列表 -->
      <el-table :data="topology?.hubs || []" stripe size="small" style="margin-bottom:16px">
        <el-table-column prop="name" label="名称" width="160" />
        <el-table-column label="层级" width="100">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.hubLevel)" size="small">
              {{ levelLabel(row.hubLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="city" label="城市" width="80" />
        <el-table-column label="当前负载（等待发车）" min-width="190">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:6px">
              <el-progress
                :percentage="Math.round((row.currentPendingLoadRate || 0) * 100)"
                :status="(row.currentPendingLoadRate||0) > 0.8 ? 'exception' : (row.currentPendingLoadRate||0) > 0.6 ? 'warning' : ''"
                :stroke-width="8"
                style="flex:1"
              />
              <span style="white-space:nowrap;font-size:12px;color:#606266;min-width:68px">
                {{ row.currentPendingLoad ?? 0 }} / {{ row.maxCapacity ?? '—' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="今日预计最大负载" min-width="190">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:6px">
              <el-progress
                :percentage="Math.round((row.todayMaxLoadRate || 0) * 100)"
                :status="(row.todayMaxLoadRate||0) > 0.8 ? 'exception' : (row.todayMaxLoadRate||0) > 0.6 ? 'warning' : ''"
                :stroke-width="8"
                color="#409eff"
                style="flex:1"
              />
              <span style="white-space:nowrap;font-size:12px;color:#606266;min-width:68px">
                {{ row.todayMaxLoad ?? 0 }} / {{ row.maxCapacity ?? '—' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最大容量" width="90" align="center">
          <template #default="{ row }">{{ row.maxCapacity ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : row.status === 1 ? 'warning' : 'danger'" size="small">
              {{ ['正常', '满载', '关闭'][row.status] ?? '未知' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- Hub 间运输边列表 -->
      <el-divider>运输线路</el-divider>
      <el-table :data="topology?.links || []" stripe size="small">
        <el-table-column label="线路" min-width="200">
          <template #default="{ row }">
            {{ hubName(row.fromHubId) }} → {{ hubName(row.toHubId) }}
          </template>
        </el-table-column>
        <el-table-column prop="transportMode" label="方式" width="70" />
        <el-table-column label="费用(元/件)" width="110">
          <template #default="{ row }">
            <span>{{ row.costPerUnit }}</span>
            <span v-if="row.costPerUnit !== row.baseCostPerUnit" class="calibrated">
              (基准:{{ row.baseCostPerUnit }})
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="capacityDaily" label="日容量(件)" width="100" />
        <el-table-column prop="distanceKm" label="距离(km)" width="90" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">
              {{ row.isActive ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getNationalTopology } from '@/api/logistics'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const topology = ref<any>(null)

async function loadTopology() {
  loading.value = true
  try {
    const res = await getNationalTopology()
    // request 拦截器已解包为 Result：{ code, message, data }
    topology.value = res.data ?? null
  } catch {
    ElMessage.error('加载拓扑失败')
  } finally {
    loading.value = false
  }
}

function hubCountByLevel(level: number) {
  return (topology.value?.hubs || []).filter((h: any) => h.hubLevel === level).length
}

function hubName(hubId: number) {
  const hub = (topology.value?.hubs || []).find((h: any) => h.id === hubId)
  return hub?.name ?? `中转站#${hubId}`
}

function levelLabel(level: number) {
  return ['全国枢纽', '省级中心', '城市配送'][level] ?? '未知'
}

function levelTagType(level: number) {
  return ['danger', 'warning', 'success'][level] ?? ''
}

onMounted(loadTopology)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.calibrated { color: #999; font-size: 12px; margin-left: 4px; }
</style>
