---
title: Mermaid 测试页
---

# 🧪 Mermaid 测试页

## 测试1：流程图

<MermaidDiagram :code="`graph LR
    A[开始] --> B[处理]
    B --> C[结束]
    style A fill:#3498db,color:#fff
    style C fill:#2ecc71,color:#fff
`" />

## 测试2：序列图

<MermaidDiagram :code="`sequenceDiagram
    participant A as 客户端
    participant B as 服务器
    A->>B: 发送请求
    B-->>A: 返回响应
`" />

## 测试3：状态图

<MermaidDiagram :code="`stateDiagram-v2
    [*] --> 待支付
    待支付 --> 待发货: 支付
    待发货 --> 已完成: 签收
`" />

---

如果上面三个图表都显示正常，说明 Mermaid 组件工作正常！
