<template>
  <div ref="container" class="mermaid-wrapper">
    <div v-if="loading" class="mermaid-loading">图表加载中...</div>
  </div>
</template>

<script setup>
import { ref, onMounted, defineProps } from 'vue'

const props = defineProps({
  code: { type: String, required: true },
  theme: { type: String, default: 'default' }
})

const container = ref(null)
const loading = ref(true)

onMounted(async () => {
  // 动态导入 mermaid，避免 SSR 问题
  const mermaid = (await import('mermaid')).default
  
  mermaid.initialize({
    startOnLoad: false,
    theme: props.theme,
    securityLevel: 'loose',
    logLevel: 'fatal',
    flowchart: { useMaxWidth: true, htmlLabels: true, curve: 'basis' },
    sequence: { useMaxWidth: true, wrap: true },
    stateDiagram: { useMaxWidth: true }
  })

  try {
    loading.value = true
    const id = 'mermaid-' + Math.random().toString(36).substr(2, 9)
    const { svg } = await mermaid.render(id, props.code.trim())
    container.value.innerHTML = svg
    loading.value = false
  } catch (e) {
    console.error('Mermaid render error:', e)
    container.value.innerHTML = `<pre style="color:#e74c3c;background:#fff5f5;padding:1rem;border-radius:8px;">图表渲染失败: ${e.message}\n\n原始代码:\n${props.code}</pre>`
    loading.value = false
  }
})
</script>

<style scoped>
.mermaid-wrapper {
  margin: 1.5rem 0;
  padding: 1rem;
  background: var(--vp-c-bg-soft);
  border-radius: 8px;
  text-align: center;
  overflow-x: auto;
}
.mermaid-wrapper :deep(svg) {
  max-width: 100%;
  height: auto;
}
.mermaid-loading {
  padding: 2rem;
  color: #666;
  font-style: italic;
}
</style>
