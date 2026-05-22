---
title: 角色介绍 - 物流世界的四大主角
---

# 🎭 角色介绍：物流世界的四大主角

想象这是一个大型物流主题 RPG 游戏，有四个职业各司其职！

<script setup>
import { ref } from 'vue'

const selectedRole = ref(null)
const roles = [
  {
    id: 'customer',
    name: '顾客',
    icon: '🧑💼',
    title: '任务发布者',
    desc: '下单买买买，等快递',
    skills: ['下单', '支付', '催快递', '签收'],
    color: '#3498db'
  },
  {
    id: 'shop',
    name: '商户',
    icon: '🏪',
    title: '装备供应商',
    desc: '管理仓库、打包发货',
    skills: ['库存管理', '创建运单', '发货', 'WMS管理'],
    color: '#2ecc71'
  },
  {
    id: 'driver',
    name: '运输员',
    icon: '🚚',
    title: '冒险执行者',
    desc: '接单、开车、送货',
    skills: ['路径规划', '状态更新', 'GPS导航', '签收确认'],
    color: '#e74c3c'
  },
  {
    id: 'admin',
    name: '管理员',
    icon: '🎯',
    title: '世界管理者',
    desc: '调度中心、Hub作业台',
    skills: ['MCMF干线规划', 'K-Means聚类', '中转站管理', '全局监控'],
    color: '#9b59b6'
  }
]

function selectRole(id) {
  selectedRole.value = selectedRole.value === id ? null : id
}
</script>

## 🎮 角色卡片

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1.5rem; margin: 2rem 0;">

<div 
  v-for="role in roles" 
  :key="role.id"
  @click="selectRole(role.id)"
  :style="{
    border: '2px solid ' + (selectedRole === role.id ? role.color : '#e0e0e0'),
    borderRadius: '12px',
    padding: '1.5rem',
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    transform: selectedRole === role.id ? 'translateY(-4px)' : 'none',
    boxShadow: selectedRole === role.id ? '0 8px 24px rgba(0,0,0,0.15)' : '0 2px 8px rgba(0,0,0,0.1)',
    background: selectedRole === role.id ? role.color + '10' : 'white'
  }"
>
  <div style="font-size: 3rem; text-align: center;">{{ role.icon }}</div>
  <h3 style="text-align: center; margin: 0.5rem 0;">{{ role.name }}</h3>
  <div style="text-align: center; color: #666; font-size: 0.9rem;">{{ role.title }}</div>
  <p style="text-align: center; margin: 0.8rem 0; font-size: 0.95rem;">{{ role.desc }}</p>
  
  <div v-if="selectedRole === role.id" style="margin-top: 1rem; padding-top: 1rem; border-top: '1px solid #e0e0e0';">
    <div style="font-weight: bold; margin-bottom: 0.5rem;">🎯 技能树：</div>
    <div style="display: flex; flex-wrap: wrap; gap: 0.5rem;">
      <span 
        v-for="skill in role.skills" 
        :key="skill"
        style="background: ' + role.color + '20'; color: ' + role.color + '; padding: 0.3rem 0.8rem; border-radius: 20px; font-size: 0.85rem;"
      >{{ skill }}</span>
    </div>
  </div>
</div>

</div>

## 🗺️ 订单的奇幻漂流路线

### B2C 模式（最常见的冒险路线）

<MermaidDiagram :code="`graph LR
    A[顾客下单] --> B[商户发货]
    B --> C[分配 Hub]
    C --> D[入调度池]
    D --> E[揽收入仓]
    E --> F[入库]
    F --> G[分拣]
    G --> H[MCMF 干线规划]
    H --> I[出库]
    I --> J[干线运输]
    J --> K[入目标城市调度池]
    K --> L[K-Means 末端聚类]
    L --> M[创建配送批次]
    M --> N[运输员承接]
    N --> O[取件]
    O --> P[签收]
    style A fill:#3498db,color:#fff
    style P fill:#2ecc71,color:#fff
    style H fill:#9b59b6,color:#fff
    style L fill:#e74c3c,color:#fff
`" />

### C2C 个人寄件（快捷通道）

<MermaidDiagram :code="`graph LR
    A[顾客发起寄件] --> B[支付]
    B --> C[自动分配 Hub]
    C --> D[入调度池]
    D --> E[...后续同上]
    style A fill:#3498db,color:#fff
    style C fill:#f39c12,color:#fff
`" />

## 📊 角色权限矩阵

| 功能 | 顾客 | 商户 | 运输员 | 管理员 |
|------|------|------|--------|--------|
| 浏览商品 | ✅ | ❌ | ❌ | ❌ |
| 下单/寄件 | ✅ | ❌ | ❌ | ❌ |
| 管理库存 | ❌ | ✅ | ❌ | ❌ |
| 发货 | ❌ | ✅ | ❌ | ❌ |
| 接单配送 | ❌ | ❌ | ✅ | ❌ |
| 更新配送状态 | ❌ | ❌ | ✅ | ❌ |
| Hub 作业 | ❌ | ❌ | ❌ | ✅ |
| MCMF 调度 | ❌ | ❌ | ❌ | ✅ |
| K-Means 聚类 | ❌ | ❌ | ❌ | ✅ |

## 🎯 下一步

- 想了解顾客怎么下单？前往 [顾客下单](/guide/order-creation)
- 想了解商户怎么发货？前往 [商户发货](/guide/merchant-ship)
- 想了解智能算法？前往 [算法天团](/algorithms/mcmf)
