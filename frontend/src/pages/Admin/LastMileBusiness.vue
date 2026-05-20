<template>
  <div class="lastmile-business">
    <el-tabs v-model="activeTab" type="card" class="ops-tabs">
      <el-tab-pane label="城市末端调度" name="city" lazy>
        <LocalCityDispatch />
      </el-tab-pane>
      <el-tab-pane label="配送批次" name="batches" lazy>
        <Batches />
      </el-tab-pane>
      <el-tab-pane label="中转站管理" name="hubs" lazy>
        <Hubs />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LocalCityDispatch from './LocalCityDispatch.vue'
import Batches from './Batches.vue'
import Hubs from './Hubs.vue'

const VALID = new Set(['city', 'batches', 'hubs'])
const route = useRoute()
const router = useRouter()

function tabFromQuery(): string {
  const t = route.query.tab as string
  return t && VALID.has(t) ? t : 'city'
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
.lastmile-business { min-height: 100%; }
.ops-tabs :deep(.el-tabs__content) {
  padding-top: 12px;
}
</style>
