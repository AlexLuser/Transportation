import { defineConfig } from 'vitepress'

export default defineConfig({
  title: '🚚 Transportation 物流系统',
  description: '智能物流系统工作原理详解 - 趣味互动版',
  lang: 'zh-CN',
  
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '系统详解', link: '/guide/' },
      { text: '算法天团', link: '/algorithms/' },
      { text: 'API 参考', link: '/api/' }
    ],

    sidebar: {
      '/guide/': [
        {
          text: '📖 系统工作原理',
          items: [
            { text: '角色介绍', link: '/guide/roles' },
            { text: '顾客下单', link: '/guide/order-creation' },
            { text: '商户发货', link: '/guide/merchant-ship' },
            { text: '中转站流水线', link: '/guide/hub-operations' },
            { text: '干线调度', link: '/guide/trunk-dispatch' },
            { text: '末端配送', link: '/guide/last-mile' },
            { text: '运输员日常', link: '/guide/driver-daily' }
          ]
        }
      ],
      '/algorithms/': [
        {
          text: '🧠 智能算法',
          items: [
            { text: 'MCMF 最小费用最大流', link: '/algorithms/mcmf' },
            { text: 'K-Means 聚类', link: '/algorithms/kmeans' },
            { text: 'A* 路径规划', link: '/algorithms/astar' },
            { text: 'LLM 智能策略', link: '/algorithms/llm' }
          ]
        }
      ],
      '/api/': [
        {
          text: '🔌 API 参考',
          items: [
            { text: '订单服务', link: '/api/orders' },
            { text: 'Hub 作业', link: '/api/hub' },
            { text: '调度服务', link: '/api/dispatch' },
            { text: '运输员服务', link: '/api/driver' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/AlexLuser/Transportation' }
    ],

    footer: {
      message: '基于 VitePress 构建',
      copyright: 'Copyright © 2026 Transportation'
    },

    lastUpdated: {
      text: '最后更新'
    }
  },

  vite: {
    ssr: {
      noExternal: ['mermaid']
    }
  }
})
