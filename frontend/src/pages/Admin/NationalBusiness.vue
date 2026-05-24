<template>
  <div class="national-business">
    <el-tabs v-model="activeTab" type="card" class="ops-tabs">
      <el-tab-pane label="干线调度" name="trunk" lazy>
        <NationalTrunkDispatch />
      </el-tab-pane>
      <el-tab-pane label="全国网络" name="network" lazy>
        <NationalNetwork />
      </el-tab-pane>
      <el-tab-pane label="运力规划" name="flow" lazy>
        <FlowPlan />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NationalTrunkDispatch from './NationalTrunkDispatch.vue'
import NationalNetwork from './NationalNetwork.vue'
import FlowPlan from './FlowPlan.vue'

const VALID = new Set(['trunk', 'network', 'flow'])
const route = useRoute()
const router = useRouter()

function tabFromQuery(): string {
  const t = route.query.tab as string
  return t && VALID.has(t) ? t : 'trunk'
}

const activeTab = ref(tabFromQuery())

onMounted(() => {
  activeTab.value = tabFromQuery()
})

watch(
  () => route.query.tab,
  () => {
    activeTab.value = tabFromQuery()
  }
)

watch(activeTab, (name) => {
  if (route.query.tab === name) return
  router.replace({ path: route.path, query: { ...route.query, tab: name } })
})
</script>

<style scoped>
.national-business { min-height: 100%; }
.ops-tabs :deep(.el-tabs__content) {
  padding-top: 12px;
}
</style>
