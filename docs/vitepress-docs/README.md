# 📚 Transportation 物流系统 - 官方文档站

> 独立的文档说明网站，与前端的电商/物流平台完全分离

## 🌐 访问地址

**文档网站**：http://localhost:5173

**前端平台**：http://localhost (Docker Compose 部署)

## 🚀 快速启动

```bash
# 进入文档目录
cd /Users/miao/CodeBuddy/Transportation/docs/vitepress-docs

# 安装依赖（首次）
npm install

# 启动开发服务器
npm run docs:dev

# 构建生产版本
npm run docs:build

# 预览生产版本
npm run docs:preview
```

## 📁 项目结构

```
vitepress-docs/
├── .vitepress/          # VitePress 配置
├── guide/               # 系统详解文档
│   ├── roles.md        # 角色介绍
│   └── order-creation.md  # 下单流程
├── algorithms/          # 算法详解
│   ├── mcmf.md        # MCMF 算法
│   └── kmeans.md      # K-Means 算法
├── api/                 # API 参考
│   └── orders.md      # 订单服务 API
├── index.md            # 首页
├── vitepress.config.js # 配置文件
└── package.json        # 项目配置
```

## 🎯 特性

- ✅ 独立于前端项目，专属端口 5173
- ✅ 交互式组件（Vue 3）
- ✅ Mermaid 流程图自动渲染
- ✅ 深色模式支持
- ✅ 响应式设计
- ✅ 搜索功能
- ✅ 多语言支持（中文）

## 📝 文档编写

### 添加新页面

1. 在对应目录创建 `.md` 文件
2. 在 `vitepress.config.js` 的 `sidebar` 中添加链接
3. 保存后自动热更新

### Markdown 增强语法

```markdown
# 支持 Vue 组件
<script setup>
import { ref } from 'vue'
const count = ref(0)
</script>

<button @click="count++">点击了 {{ count }} 次</button>

# 支持 Mermaid 流程图
```mermaid
graph LR
    A --> B
```

# 支持代码高亮
```java
System.out.println("Hello World");
```
```

## 🔧 配置修改

编辑 `vitepress.config.js`：

```javascript
export default defineConfig({
  title: '🚚 Transportation 物流系统',
  description: '智能物流系统工作原理详解',
  themeConfig: {
    nav: [...],      // 顶部导航
    sidebar: {...},   // 侧边栏
    socialLinks: [...] // 社交链接
  }
})
```

## 📦 部署

### 构建静态文件

```bash
npm run docs:build
# 输出目录：.vitepress/dist
```

### 部署到服务器

将 `.vitepress/dist` 目录部署到任何静态文件服务器：

- GitHub Pages
- Vercel
- Netlify
- Nginx
- Apache

## 🆘 常见问题

### 端口被占用？

修改 `package.json` 中的启动脚本：

```json
{
  "scripts": {
    "docs:dev": "vitepress dev --port 5174"
  }
}
```

### 热更新不生效？

检查文件名是否符合 VuePress/VitePress 的命名规范，避免使用特殊字符。

## 📞 联系方式

- 项目地址：https://github.com/AlexLuser/Transportation
- 文档 Issue：https://github.com/AlexLuser/Transportation/issues

---

**注意**：本文档网站完全独立，不影响前端项目（运行在 http://localhost）
