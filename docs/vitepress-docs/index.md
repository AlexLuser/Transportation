---
home: true
title: Transportation 物流系统
hero:
  name: "🚚 Transportation"
  text: "智能物流系统工作原理"
  tagline: 跟着包裹一起体验物流奇幻漂流 ✨
  image:
    src: /logo.png
    alt: Transportation Logo
  actions:
    - theme: brand
      text: 开始探索
      link: /guide/roles
    - theme: alt
      text: 查看算法
      link: /algorithms/mcmf

features:
  - icon: 📦
    title: 订单全生命周期
    details: 从下单到签收，体验包裹的奇幻漂流旅程
  - icon: 🧠
    title: 智能算法调度
    details: MCMF、K-Means、A* 等算法协同优化物流效率
  - icon: 🚛
    title: 五阶段流水线
    details: 揽收→入库→分拣→出库→干线，标准化中转站作业
  - icon: 🎯
    title: 交互式演示
    details: 可视化算法执行过程，理解物流调度原理
---

<script setup>
import { ref } from 'vue'

const showStory = ref(false)
const currentStep = ref(0)

const steps = [
  { icon: '🛒', title: '下单', desc: '顾客在电商平台下单' },
  { icon: '📦', title: '发货', desc: '商户打包，系统分配Hub' },
  { icon: '🚛', title: '揽收', desc: '运输员取件，入库中转站' },
  { icon: '🔀', title: '分拣', desc: '智能分拣，规划干线路线' },
  { icon: '✈️', title: '干线', desc: 'MCMF算法优化跨城运输' },
  { icon: '🚚', title: '配送', desc: 'K-Means聚类，末端配送' },
  { icon: '🎉', title: '签收', desc: '包裹安全送达！' }
]

function startJourney() {
  showStory.value = true
  currentStep.value = 0
  const timer = setInterval(() => {
    if (currentStep.value < steps.length - 1) {
      currentStep.value++
    } else {
      clearInterval(timer)
    }
  }, 1500)
}
</script>

## 🎬 包裹的奇幻漂流

<div style="text-align: center; margin: 2rem 0;">
  <button 
    v-if="!showStory" 
    @click="startJourney"
    style="padding: 1rem 2rem; font-size: 1.2rem; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border: none; border-radius: 8px; cursor: pointer;"
  >
    🚀 点击观看包裹旅程
  </button>
</div>

<div v-if="showStory" style="max-width: 800px; margin: 2rem auto;">
  <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem;">
    <div 
      v-for="(step, idx) in steps" 
      :key="idx"
      :style="{
        textAlign: 'center',
        opacity: idx <= currentStep ? 1 : 0.3,
        transform: idx === currentStep ? 'scale(1.1)' : 'scale(1)',
        transition: 'all 0.5s ease'
      }"
    >
      <div style="font-size: 2rem;">{{ step.icon }}</div>
      <div style="font-size: 0.8rem; margin-top: 0.5rem;">{{ step.title }}</div>
    </div>
  </div>
  
  <div style="text-align: center; padding: 2rem; background: #f5f7fa; border-radius: 8px;">
    <div style="font-size: 3rem;">{{ steps[currentStep].icon }}</div>
    <div style="font-size: 1.5rem; margin: 1rem 0;">{{ steps[currentStep].title }}</div>
    <div style="color: #666;">{{ steps[currentStep].desc }}</div>
  </div>
</div>

## 🎯 快速导航

| 你想了解... | 前往... |
|------------|--------|
| 系统有哪些角色？ | [角色介绍](/guide/roles) |
| 订单怎么创建的？ | [顾客下单](/guide/order-creation) |
| Hub中转站怎么运作？ | [中转站流水线](/guide/hub-operations) |
| MCMF算法是什么？ | [算法详解](/algorithms/mcmf) |
| API怎么调用？ | [API参考](/api/orders) |

## 💡 小贴士

- 📖 **侧边栏**有完整的文档结构，方便跳转
- 🔍 **搜索框**可以快速查找内容
- 🌙 **深色模式**保护你的眼睛
- 📱 **移动端适配**，随时随地学习

---

<div style="text-align: center; color: #999; margin-top: 3rem;">
  用 ❤️ 和 Vue 3 + VitePress 制作<br>
  项目地址：<a href="https://github.com/AlexLuser/Transportation">GitHub</a>
</div>
