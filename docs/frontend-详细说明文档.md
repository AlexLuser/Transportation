# Frontend 前端应用 — 完全初学者指南

> 本文档面向**完全没有前端开发经验**的读者，从零开始讲解前端的每一行代码背后的原理、用到的技术，以及页面如何与后端服务交互。建议按顺序从头阅读。

---

## 目录

1. [这个模块是做什么的？](#1-这个模块是做什么的)
2. [基础概念预备知识](#2-基础概念预备知识)
3. [技术栈全览](#3-技术栈全览)
4. [项目目录结构](#4-项目目录结构)
5. [核心配置文件详解](#5-核心配置文件详解)
6. [全局状态管理（Pinia Store）](#6-全局状态管理pinia-store)
7. [路由系统详解（Vue Router）](#7-路由系统详解vue-router)
8. [HTTP 请求封装（Axios）](#8-http-请求封装axios)
9. [API 接口模块详解](#9-api-接口模块详解)
10. [登录页详解（Login.vue）](#10-登录页详解loginvue)
11. [各角色页面详解](#11-各角色页面详解)
12. [地图组件深度解析（RouteMap.vue）](#12-地图组件深度解析routemapvue)
13. [完整业务流程数据流动](#13-完整业务流程数据流动)
14. [Vue 模板语法参考手册](#14-vue-模板语法参考手册)
15. [常见问题 Q&A](#15-常见问题-qa)

---

## 1. 这个模块是做什么的？

### 1.1 前端的角色类比

想象你走进一家银行：
- **大堂（前端）**：你看到的柜台界面、取号机、等候区——这就是前端
- **后台系统（后端）**：柜员电脑里的账务系统——这就是后端
- **银行规章（数据库）**：规定哪些操作合法——这就是数据库

前端的职责：
1. **展示数据**：把银行账户余额（从后端取到的数据）显示在屏幕上
2. **收集用户输入**：填写转账金额表单
3. **发送请求**：点击"转账"按钮，发送 HTTP 请求给后端
4. **处理响应**：显示"转账成功"或"余额不足"

### 1.2 本系统前端的四大界面

```
┌─────────────────────────────────────────────────────────┐
│                   智能物流管理系统                        │
│                   登录页（Login.vue）                     │
└────────────┬────────────────────────────────────────────┘
             │ 根据角色(roleCode)跳转
    ┌────────┴──────────────────────────────────┐
    │                                           │
    ▼                                           ▼
管理员后台（admin）              顾客端（customer）
  数据大盘 / 订单管理             商城浏览 / 我的订单（含地图）
  用户管理 / 商品管理             个人信息 / 下单
    │                                           │
    ▼                                           ▼
商户端（shop）                   运输员端（driver）
  商品管理 / 订单发货             配送大厅 / 我的配送
  仓库管理 / 库存管理             导航地图 / 车辆管理
```

---

## 2. 基础概念预备知识

### 2.1 什么是网页？HTML、CSS、JavaScript

- **HTML（HyperText Markup Language）**：定义页面**结构**（有哪些元素）
- **CSS（Cascading Style Sheets）**：定义页面**样式**（元素长什么样）
- **JavaScript（JS）**：定义页面**行为**（用户操作后做什么）

```html
<!-- HTML：结构 -->
<button id="btn">点我</button>

/* CSS：样式 */
#btn { background: blue; color: white; }

// JavaScript：行为
document.getElementById('btn').onclick = () => alert('你点击了按钮');
```

### 2.2 什么是单页应用（SPA）？

**传统多页应用**：每次点击链接，浏览器向服务器请求一个完整的 HTML 页面，有明显的页面"刷新白屏"。

**单页应用（Single Page Application, SPA）**：整个应用只有**一个 HTML 文件**（`index.html`），页面切换通过 JavaScript 动态替换内容，没有白屏，体验更流畅。

```
index.html（唯一的HTML文件）
     ↓
main.ts（JavaScript入口，创建Vue应用）
     ↓
Vue Router（根据URL显示不同的Vue组件）
```

本项目就是 SPA。打开 `index.html`，内容只有一个 `<div id="app">`，所有界面都是 Vue 动态生成的。

### 2.3 什么是组件（Component）？

组件是可以**复用的 UI 单元**。就像搭积木：

```
整个页面 = 积木堆叠
           ├── 导航栏组件（NavBar.vue）
           ├── 订单列表组件（OrderList.vue）
           │      ├── 订单卡片组件（OrderCard.vue）
           │      ├── 订单卡片组件
           │      └── 订单卡片组件
           └── 分页组件（Pagination.vue）
```

`RouteMap.vue` 是本项目中的地图组件，在顾客订单页和运输员导航页都用到了它，只需要开发一次，重复使用。

### 2.4 什么是响应式（Reactivity）？

"响应式"是 Vue 最核心的特性：**数据改变，页面自动更新**，不需要手动操作 DOM。

```javascript
// 传统 JavaScript（命令式，繁琐）
let count = 0;
document.getElementById('count').textContent = count;  // 手动同步

function increment() {
    count++;
    document.getElementById('count').textContent = count;  // 每次都要手动更新DOM
}

// Vue（声明式，自动同步）
const count = ref(0);  // 声明响应式数据
// <p>{{ count }}</p>  模板中引用，自动同步
function increment() {
    count.value++;  // 只改数据，Vue自动更新页面
}
```

### 2.5 什么是异步操作？Promise 和 async/await

**同步操作**：一步完成后才能进行下一步（像排队）

**异步操作**：发起请求后不等待，继续做其他事，请求完成时收到通知（像取号等叫号）

```javascript
// 问题：HTTP请求需要时间（网络延迟），不能阻塞页面
// 解决方案：Promise + async/await

// Promise：代表"未来的值"（像外卖订单）
const orderPromise = fetch('/api/orders');  // 立即返回Promise，不等待

// async/await：让异步代码看起来像同步代码
async function fetchOrders() {
    const res = await fetch('/api/orders');  // await：暂停在这里等结果
    const data = await res.json();            // 拿到结果后继续
    console.log(data);                        // 这里data一定有值
}
```

---

## 3. 技术栈全览

### 3.1 完整技术栈表

| 技术 | 版本 | 用途 | 类比 |
|------|------|------|------|
| **Vue 3** | ^3.4 | 前端框架，组件化开发 | 建筑图纸 |
| **TypeScript** | ^5.2 | 类型安全的 JavaScript | 带语法检查的编程语言 |
| **Vite** | ^5.0 | 构建工具，开发服务器 | 施工脚手架 |
| **Vue Router 4** | ^4.0 | 路由管理（URL→组件） | 楼层导航 |
| **Pinia** | ^2.1 | 全局状态管理 | 整栋楼共用的储物间 |
| **Axios** | ^1.6 | HTTP 请求库 | 快递公司 |
| **Element Plus** | ^2.4 | UI 组件库（按钮/表格/弹窗等） | 现成的家具 |
| **Leaflet** | ^1.9 | 地图渲染库 | 地图画板 |
| **OpenStreetMap** | - | 地图瓦片数据源（免费） | 地图数据提供商 |

### 3.2 依赖关系图

```
Browser（浏览器）
    └── index.html
           └── main.ts（应用入口）
                  ├── createApp（Vue核心）
                  ├── createPinia（状态管理）
                  ├── createRouter（路由）
                  └── ElementPlus（UI组件）

用户操作
    └── Vue 组件（.vue文件）
           ├── 读写 Pinia Store（全局状态）
           ├── 调用 API 函数（api/*.ts）
           │      └── Axios（发 HTTP 请求）
           │             └── 网关（localhost:8083）→ 各微服务
           └── Leaflet（地图渲染，仅RouteMap.vue）
```

---

## 4. 项目目录结构

```
frontend/
├── index.html              ← 唯一的 HTML 文件，Vue 挂载点
├── package.json            ← 依赖声明（相当于 Maven 的 pom.xml）
├── tsconfig.json           ← TypeScript 编译器配置
├── vite.config.ts          ← Vite 构建 + 开发代理配置
└── src/                    ← 所有源代码
    ├── main.ts             ← 应用入口（注册插件、挂载到DOM）
    ├── App.vue             ← 根组件（只有 <router-view />）
    │
    ├── router/
    │   └── index.ts        ← 路由配置（URL映射 + 守卫）
    │
    ├── stores/
    │   └── userStore.ts    ← Pinia 全局状态（token + userInfo）
    │
    ├── utils/
    │   ├── request.ts      ← Axios 封装（拦截器）
    │   └── common.ts       ← 通用工具函数
    │
    ├── api/                ← 各服务 API 调用函数
    │   ├── auth.ts         ← 认证：login / logout
    │   ├── customer.ts     ← 顾客：个人信息、收货地址
    │   ├── shop.ts         ← 商铺：商铺/商品/仓库
    │   ├── mall.ts         ← 商城：商品浏览（顾客端）
    │   ├── order.ts        ← 订单：创建/查询/状态变更
    │   ├── driver.ts       ← 运输员：配送任务/车辆/个人信息
    │   └── logistics.ts    ← 物流：路线/轨迹
    │
    ├── components/
    │   └── RouteMap.vue    ← 地图组件（Leaflet，最复杂的组件）
    │
    └── pages/              ← 所有页面组件
        ├── Login.vue       ← 登录页
        ├── AdminHome.vue   ← 管理员布局（侧边栏+router-view）
        ├── CustomerHome.vue ← 顾客布局
        ├── ShopHome.vue    ← 商户布局
        ├── DriverHome.vue  ← 运输员布局
        ├── Admin/
        │   ├── Dashboard.vue ← 数据大盘（统计数字/图表）
        │   ├── Orders.vue    ← 全局订单管理
        │   ├── Users.vue     ← 用户管理
        │   ├── Goods.vue     ← 商品管理
        │   └── System.vue    ← 系统信息
        ├── Customer/
        │   ├── Product.vue   ← 商城浏览（含下单入口）
        │   ├── Order.vue     ← 我的订单（含物流地图）★最复杂
        │   ├── Profile.vue   ← 个人信息/收货地址管理
        │   ├── CreateOrder.vue ← 购物车下单流程
        │   └── ShopPage.vue  ← 单个商铺详情页
        ├── Shop/
        │   ├── Product.vue   ← 商品管理（上下架/价格）
        │   ├── Order.vue     ← 商户订单管理（含发货）
        │   ├── Profile.vue   ← 商铺信息管理
        │   └── Stock.vue     ← 库存管理
        └── Driver/
            ├── Delivery.vue  ← 配送任务管理（待接单+我的配送）★
            ├── Navigation.vue ← 导航地图（查看规划路线）
            ├── Vehicle.vue   ← 车辆管理
            └── Profile.vue   ← 个人信息管理
```

---

## 5. 核心配置文件详解

### 5.1 vite.config.ts — 构建与代理

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
    plugins: [vue()],  // 让 Vite 能理解 .vue 文件

    resolve: {
        alias: {
            '@': path.resolve(__dirname, 'src')
            // 路径别名：@/api/auth 等价于 src/api/auth
            // 好处：不用写 ../../api/auth 这样的相对路径
        }
    },

    server: {
        port: 5173,  // 前端开发服务器端口

        proxy: {
            // 所有 /api 开头的请求，代理转发到网关
            '/api': {
                target: 'http://localhost:8083',
                changeOrigin: true
                // changeOrigin: 修改请求头的 Host 为 target，
                // 让服务器以为请求来自同源，解决跨域问题
            }
        }
    }
})
```

**开发时请求流向**：

```
浏览器（localhost:5173）
    ↓ 发送请求 /api/auth/login
Vite 开发服务器（代理）
    ↓ 转发到 http://localhost:8083/api/auth/login
网关（localhost:8083）
    ↓ JWT 验证 + 路由转发
auth-service（localhost:8082）
    ↓ 验证密码，返回 Token
（响应原路返回到浏览器）
```

### 5.2 main.ts — 应用启动

```typescript
import { createApp } from 'vue'                // Vue 核心
import { createPinia } from 'pinia'            // 状态管理
import ElementPlus from 'element-plus'         // UI 组件库
import 'element-plus/dist/index.css'           // UI 样式（必须引入）
import App from './App.vue'                    // 根组件
import { router } from './router'              // 路由

// 1. 创建 Vue 应用实例（以 App.vue 为根组件）
const app = createApp(App)

// 2. 安装插件（顺序很重要：Pinia 要在 Router 之前，因为 Router 守卫用到 Pinia）
app.use(createPinia())  // 启用全局状态管理
app.use(router)         // 启用路由
app.use(ElementPlus)    // 全局注册所有 Element Plus 组件

// 3. 挂载到 index.html 中的 <div id="app">
app.mount('#app')
```

**`App.vue` — 根组件**（最简单的组件）：

```vue
<template>
    <router-view />  <!-- 路由出口：当前路由对应的页面在这里渲染 -->
</template>
```

整个应用的"骨架"，路由切换时只替换这里的内容。

### 5.3 tsconfig.json — TypeScript 配置

```json
{
  "compilerOptions": {
    "target": "ES2020",        // 编译到 ES2020 标准
    "useDefineForClassFields": true,
    "module": "ESNext",        // 使用 ESM 模块系统
    "lib": ["ES2020", "DOM"],  // 包含浏览器 DOM 类型定义
    "paths": {
      "@/*": ["./src/*"]       // 路径别名（配合vite.config.ts中的alias）
    },
    "strict": true             // 严格模式（开启更多类型检查）
  }
}
```

---

## 6. 全局状态管理（Pinia Store）

### 6.1 为什么需要全局状态？

**问题**：用户登录后，很多页面都需要知道"当前用户是谁"（用户名、角色、Token）。

如果通过组件间传参（`props`/`emit`）层层传递，会非常繁琐：

```
Login.vue 登录成功 → AdminHome.vue → Dashboard.vue → 某个子子组件
                                                             ↑ 它需要 userId，但要经过3层传参
```

**Pinia 的解决方案**：全局共享"仓库"，任何组件都能直接存取。

```
Login.vue 登录成功 → userStore.setUser(token, userInfo)
                                    ↑
Dashboard.vue ─── userStore.userInfo.userId  （直接访问，不需要传参）
Orders.vue ────── userStore.token             （直接访问）
Profile.vue ───── userStore.userInfo.roleCode （直接访问）
```

### 6.2 userStore.ts 完整解析

```typescript
import { defineStore } from 'pinia'

// 定义 UserInfo 类型（TypeScript 接口）
interface UserInfo {
    userId: number;    // 数字类型
    username: string;  // 字符串类型
    roleCode: string;  // "admin" | "customer" | "shop" | "driver"
    roleName: string;  // "管理员" | "顾客用户" | "商户用户" | "运输员"
}

// defineStore：创建一个 Pinia Store（全局仓库）
// 第一个参数 'user'：仓库唯一 ID，Pinia 内部用来区分不同仓库
export const useUserStore = defineStore('user', {

    // state：仓库中存储的数据（相当于类的字段）
    // 必须是一个函数，原因：确保多次调用时每次得到新的初始值（避免引用共享问题）
    state: () => ({
        // 从 localStorage 恢复，原因：刷新页面时内存清空，localStorage 持久化
        token: localStorage.getItem('token') || null,
        // localStorage.getItem('token') 返回字符串或 null
        // || null：如果返回 null（没有存储过），值为 null

        userInfo: JSON.parse(
            localStorage.getItem('userInfo') || 'null'
        ) as UserInfo | null
        // localStorage 只能存字符串，对象需要 JSON.stringify 存入、JSON.parse 读出
        // 'null' 是字符串"null"，JSON.parse('null') 得到 JavaScript 的 null
        // as UserInfo | null：TypeScript 类型断言，告诉编译器"这个值的类型"
    }),

    // actions：修改 state 的方法（相当于类的方法）
    // 注意：直接修改 state（this.xxx = ...）是允许的，Pinia 不像 Vuex 需要 mutations
    actions: {

        // 登录成功时调用：保存 token 和用户信息
        setUser(token: string, userInfo: UserInfo) {
            this.token = token
            this.userInfo = userInfo
            // 同时写入 localStorage（持久化，刷新页面不丢失）
            localStorage.setItem('token', token)
            localStorage.setItem('userInfo', JSON.stringify(userInfo))
            // JSON.stringify：将对象转为 JSON 字符串
            // {"userId":1,"username":"admin","roleCode":"admin","roleName":"管理员"}
        },

        // 登出时调用：清除所有状态
        logout() {
            this.token = null
            this.userInfo = null
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
        }
    }
})
```

### 6.3 在组件中使用 Store

```typescript
// 任何 .vue 文件中
import { useUserStore } from '@/stores/userStore'

const userStore = useUserStore()  // 获取 Store 实例（单例，全局共用）

// 读取状态（直接访问属性）
const token = userStore.token           // 'eyJhbGci...' 或 null
const roleCode = userStore.userInfo?.roleCode  // 'customer' 或 undefined（?.可选链）

// 调用 actions（修改状态）
userStore.setUser(token, userInfo)  // 登录后
userStore.logout()                   // 登出后
```

**`?.` 可选链操作符详解**：

```typescript
userStore.userInfo?.roleCode

// 等价于：
if (userStore.userInfo !== null && userStore.userInfo !== undefined) {
    return userStore.userInfo.roleCode;
} else {
    return undefined;  // 而不是报错
}

// 如果不用 ?. 直接访问：
userStore.userInfo.roleCode  // 当 userInfo 为 null 时，TypeError: Cannot read properties of null
```

---

## 7. 路由系统详解（Vue Router）

### 7.1 路由配置完整解析

```typescript
// router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/userStore'

// 角色首页映射：登录后跳转到哪里
export const roleHomeMap: Record<string, string> = {
    admin:    '/admin/home',    // Record<string, string>：键和值都是字符串的对象类型
    customer: '/customer/home',
    shop:     '/shop/home',
    driver:   '/driver/home'
}

const routes = [
    // ===== 公开页面 =====
    {
        path: '/login',
        component: () => import('@/pages/Login.vue')
        // () => import(...)：动态导入（懒加载）
        // 只有用户访问 /login 时，才下载 Login.vue 的 JS 代码
        // 好处：减小初始包大小，提升首次加载速度
    },

    // ===== 管理员区域 =====
    {
        path: '/admin/home',
        component: () => import('@/pages/AdminHome.vue'),
        meta: { role: 'admin' },   // 元数据：标记此路由需要 admin 角色
        redirect: '/admin/home/dashboard',  // 访问 /admin/home 时自动跳转
        children: [  // 嵌套子路由
            {
                path: 'dashboard',     // 相对路径，完整路径：/admin/home/dashboard
                component: () => import('@/pages/Admin/Dashboard.vue'),
                meta: { role: 'admin' }
            },
            { path: 'orders',  component: () => import('@/pages/Admin/Orders.vue'),  meta: { role: 'admin' } },
            { path: 'users',   component: () => import('@/pages/Admin/Users.vue'),   meta: { role: 'admin' } },
            { path: 'goods',   component: () => import('@/pages/Admin/Goods.vue'),   meta: { role: 'admin' } },
            { path: 'system',  component: () => import('@/pages/Admin/System.vue'),  meta: { role: 'admin' } }
        ]
    },

    // ===== 顾客区域 =====
    {
        path: '/customer/home',
        component: () => import('@/pages/CustomerHome.vue'),
        meta: { role: 'customer' },
        redirect: '/customer/home/products',
        children: [
            { path: 'products',   component: () => import('@/pages/Customer/Product.vue')     },
            { path: 'orders',     component: () => import('@/pages/Customer/Order.vue')       },
            { path: 'profile',    component: () => import('@/pages/Customer/Profile.vue')     },
            { path: 'shop-order', component: () => import('@/pages/Customer/CreateOrder.vue') },
            { path: 'shop/:id',   component: () => import('@/pages/Customer/ShopPage.vue')    }
            // :id 是路由参数，/customer/home/shop/123 中的 123 可以通过 useRoute().params.id 获取
        ]
    },

    // ===== 商户区域 =====
    {
        path: '/shop/home',
        component: () => import('@/pages/ShopHome.vue'),
        meta: { role: 'shop' },
        redirect: '/shop/home/products',
        children: [
            { path: 'products', component: () => import('@/pages/Shop/Product.vue') },
            { path: 'orders',   component: () => import('@/pages/Shop/Order.vue')   },
            { path: 'profile',  component: () => import('@/pages/Shop/Profile.vue') },
            { path: 'stock',    component: () => import('@/pages/Shop/Stock.vue')   }
        ]
    },

    // ===== 运输员区域 =====
    {
        path: '/driver/home',
        component: () => import('@/pages/DriverHome.vue'),
        meta: { role: 'driver' },
        redirect: '/driver/home/deliveries',
        children: [
            { path: 'navigation', component: () => import('@/pages/Driver/Navigation.vue') },
            { path: 'deliveries', component: () => import('@/pages/Driver/Delivery.vue')   },
            { path: 'vehicles',   component: () => import('@/pages/Driver/Vehicle.vue')    },
            { path: 'profile',    component: () => import('@/pages/Driver/Profile.vue')    }
        ]
    },

    { path: '/', redirect: '/login' },               // 根路径重定向
    { path: '/:pathMatch(.*)*', redirect: '/login' } // 404 页面重定向
    // /:pathMatch(.*)*：匹配所有未定义的路径
]

export const router = createRouter({
    history: createWebHistory(),  // HTML5 历史模式（URL 美观，无 # 号）
    routes
})
```

### 7.2 导航守卫（beforeEach）详解

导航守卫在每次路由跳转前执行，是实现**权限控制**的核心机制：

```typescript
router.beforeEach((to, _from, next) => {
    // to：目标路由对象
    // _from：来源路由（下划线前缀表示不使用该参数，避免lint警告）
    // next：函数，调用它才能完成导航

    const userStore = useUserStore()
    const token = userStore.token
    const roleCode = userStore.userInfo?.roleCode

    // ===== 规则1：访问登录页 =====
    if (to.path === '/login') {
        if (token && roleCode) {
            // 已登录：强制跳到角色首页（防止已登录用户再次看到登录页）
            next(roleHomeMap[roleCode])
        } else {
            // 未登录：正常显示登录页
            next()
        }
        return  // 必须 return，否则会继续执行下面的代码
    }

    // ===== 规则2：访问需要登录的页面 =====
    if (!token) {
        // 未登录：跳转到登录页
        next('/login')
        return
    }

    // ===== 规则3：角色越权检查 =====
    const requiredRole = to.meta.role as string | undefined
    if (requiredRole && roleCode && requiredRole !== roleCode) {
        // 例：顾客（customer）访问 /admin/home（要求 admin 角色）
        // → 跳回顾客自己的首页
        next(roleHomeMap[roleCode])
        return
    }

    // ===== 所有检查通过：允许访问 =====
    next()
})
```

**导航守卫执行流程示例**：

```
场景：未登录用户访问 /customer/home/orders

beforeEach 执行：
  to.path = '/customer/home/orders'（不是/login，跳过规则1）
  token = null（未登录）
  → next('/login')
  
用户被跳转到登录页
```

```
场景：运输员登录后，尝试访问 /admin/home

beforeEach 执行：
  to.path = '/admin/home'（不是/login，跳过规则1）
  token = 'eyJ...'（已登录，跳过规则2）
  requiredRole = 'admin'（from meta.role）
  roleCode = 'driver'（运输员）
  requiredRole !== roleCode → next(roleHomeMap['driver']) = '/driver/home'
  
运输员被跳转回自己的首页
```

### 7.3 嵌套路由的渲染逻辑

```
访问：/customer/home/orders

渲染层次：
App.vue
  └── <router-view /> ──渲染── CustomerHome.vue（匹配 /customer/home）
                                   └── <router-view /> ──渲染── Customer/Order.vue（匹配 /orders 子路由）
```

`CustomerHome.vue` 提供了侧边栏布局，`<router-view />` 区域显示当前子路由的内容。点击侧边栏菜单时，只有 `<router-view />` 区域的内容会变换，侧边栏本身不重新渲染（高效！）。

---

## 8. HTTP 请求封装（Axios）

### 8.1 为什么要封装 Axios？

直接使用原始 Axios 的问题：
1. 每次请求都要手动加 `Authorization` 头（Token）
2. 每次都要判断响应的 `code` 是否为 200
3. 401 错误（Token过期）需要手动处理跳转

封装 Axios 后，所有这些逻辑只写一次：

### 8.2 request.ts 逐行解析

```typescript
import axios from 'axios'
import { ElMessage } from 'element-plus'   // 弹出消息提示
import { useUserStore } from '@/stores/userStore'
import { router } from '@/router'

// 创建 Axios 实例（带默认配置）
const request = axios.create({
    baseURL: '/api',  // 所有请求路径自动加上 /api 前缀
    // 例：request.get('/auth/login') 实际请求 /api/auth/login
    // 再经过 vite.config 代理转发到 http://localhost:8083/api/auth/login
    timeout: 5000     // 5秒超时，超时抛出 ECONNABORTED 错误
})

// ==================== 请求拦截器 ====================
// 每次发送请求前执行（自动注入 Token）
request.interceptors.request.use(
    config => {
        // config：本次请求的配置对象（包含 url, method, headers, data 等）
        const userStore = useUserStore()
        if (userStore.token) {
            // 在请求头中注入 JWT Token
            config.headers.Authorization = `Bearer ${userStore.token}`
            // 模板字符串：`Bearer ` + token 的值
            // 标准格式：Authorization: Bearer eyJhbGci...
        }
        return config  // 必须返回 config，否则请求不会发出
    },
    error => {
        // 请求配置出错时（极少见，如序列化失败）
        return Promise.reject(error)
    }
)

// ==================== 响应拦截器 ====================
// 收到响应后执行（统一处理错误）
request.interceptors.response.use(
    // ① 正常响应（HTTP状态码 2xx）
    response => {
        const res = response.data  // 后端返回的 Result<T> 对象
        // res = { code: 200, message: 'success', data: {...}, timestamp: 1700000000 }

        if (res.code !== 200) {
            // 业务错误（HTTP 200 但业务码不是 200）
            // 例：code=4001 用户名密码错误
            ElMessage.error(res.message || '操作失败')
            // ElMessage.error：右上角弹出红色错误提示，3秒后自动消失
            return Promise.reject(res.message)
            // 让调用方的 catch 块知道请求失败了
        }
        return res  // 成功：返回整个 Result<T> 对象
        // 调用方 const result = await someApi() 拿到的就是 res
        // result.data 是真正的数据
    },

    // ② 异常响应（HTTP状态码非 2xx，如 401/403/500）
    error => {
        if (error.response?.status === 401) {
            // 401 Unauthorized：Token 无效或已过期
            ElMessage.error('未授权，请先登录')
            const userStore = useUserStore()
            userStore.logout()          // 清除本地存储的 token 和 userInfo
            router.replace('/login')    // 跳转到登录页
            // replace 而不是 push：不保留当前页到历史记录（防止用户点返回回到当前失效页面）
        } else {
            ElMessage.error(error.response?.data.message || '操作失败')
        }
        return Promise.reject(error)  // 继续抛出，让调用方的 catch 捕获
    }
)

export default request  // 导出封装好的实例
```

### 8.3 如何使用封装的 request

```typescript
// api/order.ts
import request from '@/utils/request'

// 获取我的订单列表
export function getCustomerOrders() {
    return request.get('/orders/my')
    // 实际请求：GET /api/orders/my
    // 自动加上 Authorization 头
    // 自动处理 401 跳转
}

// 创建订单（POST，带请求体）
export function createOrder(data: CreateOrderRequest) {
    return request.post('/orders', data)
    // data 自动序列化为 JSON 请求体
}

// 更新订单状态（PUT，带路径参数和请求体）
export function updateOrderStatus(id: number, orderStatus: number) {
    return request.put(`/orders/${id}/status`, { orderStatus })
    // 路径参数：模板字符串 `.../${id}/...`
    // 请求体：{ orderStatus: 2 }
}
```

**在组件中调用**：

```typescript
const orders = ref<any[]>([])

async function fetchOrders() {
    try {
        const res = await getCustomerOrders()
        // res 是 Result<List<Order>> 对象
        // res.data 是订单数组
        orders.value = res.data ?? []
        // ?? []：如果 res.data 是 null/undefined，使用空数组
    } catch (error) {
        // 错误已由拦截器显示给用户，这里可以选择不处理
        console.error(error)
    }
}
```

---

## 9. API 接口模块详解

### 9.1 auth.ts — 认证接口

```typescript
import request from '@/utils/request'

export function login(data: { username: string; password: string }) {
    return request.post('/auth/login', data)
    // 响应：Result<LoginResponseDTO>
    // result.data.token: string（JWT令牌）
    // result.data.userInfo: { userId, username, roleCode, roleName }
    // result.data.expiration: number（有效期秒数，7200）
}

export function logout() {
    return request.post('/auth/logout')
    // 响应：Result<Void>（无数据，仅表示登出成功）
    // 注意：JWT是无状态的，真正的登出是前端删除token
}
```

### 9.2 logistics.ts — 物流接口（地图相关）

当前 `src/api/logistics.ts` 仅封装 **`getRouteByOrderId`**、**`createRoute`**（与 `Shop/Order.vue` 发货流程、`Customer/Order.vue` / `Driver/Navigation.vue` 查路线一致）。统一响应为 **`Result`**，`data` 为后端返回体（创建路线时为 **`CreateRouteResponseDTO`**：`route`、`llmEnhanced`、`llmDecision`，后两者**不入库**）。

```typescript
import request from '@/utils/request'

export const getRouteByOrderId = (orderId: number) => {
    return request({ url: `/logistics/routes/order/${orderId}`, method: 'GET' })
}

export interface CreateRouteRequest {
    orderId: number
    warehouseId: number
    startAddress: string
    startLatitude?: number
    startLongitude?: number
    endAddress: string
    endLatitude?: number
    endLongitude?: number
    receiverName?: string
    receiverPhone?: string
}

export const createRoute = (data: CreateRouteRequest) => {
    return request({ url: '/logistics/routes', method: 'POST', data })
}
```

**轨迹上报与轨迹查询**：后端路径为 **`POST /api/logistics/track/location`**（仅 `roleCode=driver`），以及 **`GET .../track/{routeId}/latest|recent|history`**。若需在前端调用，可自行在 `api/logistics.ts` 或 `api/driver.ts` 中增加封装；字段名与后端 **`LocationUpdateDTO`** 一致（`heading` 表示方向角，**不是** `direction`）。

**地图组件 `RouteMap.vue`**：规划线为蓝色虚线；实际轨迹为橙色实线；`recentTracks` 为**时间倒序**，组件内 **`.reverse()`** 后再连线。

### 9.3 customer.ts — 顾客接口（新增地址查询）

```typescript
// 根据地址ID获取地址详情（含经纬度，用于物流路线规划终点坐标）
// 调用时机：商户发货弹窗确认后，根据 order.addressId 查询收货坐标
export function getAddressById(addressId: number) {
    return request.get(`/customers/address/${addressId}`)
    // 响应：Result<Address>
    // address.latitude: number   — 收货地纬度
    // address.longitude: number  — 收货地经度
    // address.receiverName: string
    // address.receiverPhone: string
    // address.detailAddress: string
    // address.province / city / district: string
}
```

> **为何需要此接口？**  
> `getOrderDetail` 返回的订单数据只包含 `addressId`（地址外键），不包含地址的经纬度坐标。
> 路线规划 `createRoute` 需要目的地经纬度才能触发 A* 计算，因此发货前必须先用 `addressId`
> 查出完整地址信息，再组装 `CreateRouteRequest`。

---

## 10. 登录页详解（Login.vue）

### 10.1 完整代码解析

```vue
<template>
    <!-- 登录容器：全屏高度，背景图片 -->
    <div class="login-container">
        <!-- el-card：Element Plus 卡片组件，带阴影的白色方块 -->
        <el-card>
            <h2>智能物流管理系统</h2>

            <!-- el-form：表单容器 -->
            <el-form>
                <!-- el-form-item：表单项（包含 label 和输入框） -->
                <el-form-item label-width="100px" label="用户名">
                    <!-- v-model="username"：双向绑定，输入框内容 ↔ username 变量 -->
                    <el-input v-model="username" placeholder="请输入用户名" />
                </el-form-item>

                <el-form-item label-width="100px" label="密码">
                    <!-- type="password"：密码输入框（显示圆点）-->
                    <!-- show-password：右侧显示"眼睛"图标，点击切换明文/密文 -->
                    <el-input v-model="password" show-password placeholder="请输入密码" type="password" />
                </el-form-item>

                <el-form-item>
                    <!-- type="primary"：蓝色按钮 -->
                    <!-- @click="handleLogin"：点击事件，调用 handleLogin 函数 -->
                    <!-- :loading="loading"：动态绑定，loading=true 时显示转圈动画 -->
                    <el-button type="primary" @click="handleLogin" style="width: 100%;" :loading="loading">
                        登录
                    </el-button>
                </el-form-item>
            </el-form>
        </el-card>
    </div>
</template>

<script setup lang="ts" name="Login">
    import { ref } from 'vue'
    import { router, roleHomeMap } from '@/router'
    import { login } from '@/api/auth'
    import { useUserStore } from '@/stores/userStore'
    import { ElMessage } from 'element-plus'

    // ref()：创建基本类型的响应式数据
    // ref('') 等价于 { value: '' }，修改时用 username.value = '张三'
    const username = ref('')    // 绑定用户名输入框
    const password = ref('')    // 绑定密码输入框
    const loading = ref(false)  // 控制按钮加载状态

    const userStore = useUserStore()  // 获取 Pinia Store

    // async 函数：包含 await 的函数必须声明为 async
    const handleLogin = async () => {
        // ① 前端校验（避免无意义的网络请求）
        if (!username.value || !password.value) {
            ElMessage.error('请输入用户名和密码')
            // ElMessage.error：右上角弹出红色提示框
            return  // 提前返回，不发请求
        }

        loading.value = true  // 按钮变成加载状态（防止重复点击）

        try {
            // ② 调用 API（等待请求完成）
            const res = await login({
                username: username.value,
                password: password.value
            })
            // res 结构：{ code: 200, message: 'success', data: { token, userInfo, expiration } }
            // res.data.token：JWT字符串
            // res.data.userInfo：{ userId, username, roleCode, roleName }

            // ③ 保存登录信息到 Pinia + localStorage
            userStore.setUser(res.data.token, res.data.userInfo)

            // ④ 根据角色跳转到对应首页
            router.replace(roleHomeMap[res.data.userInfo.roleCode])
            // replace 替代 push：防止用户登录后还能点击"返回"回到登录页
        } catch (error) {
            // 错误已由响应拦截器的 ElMessage.error 显示，这里静默处理
            console.error(error)
        } finally {
            // finally：无论成功失败都执行（恢复按钮状态）
            loading.value = false
        }
    }
</script>

<style scoped>
    /* scoped：这里的样式只作用于本组件，不影响其他组件 */
    .login-container {
        display: flex;           /* 弹性布局 */
        justify-content: flex-end; /* 水平方向：右对齐（卡片在右侧） */
        align-items: center;     /* 垂直方向：居中 */
        height: 100vh;           /* 占满整个视口高度（vh = viewport height） */
        padding-right: 10%;      /* 右边留10%间距，不紧贴边缘 */
        background-image: url('@/assets/images/LoginBackground.png');
        background-size: cover;  /* 背景图片覆盖整个区域 */
        background-position: center;
    }

    /* :deep() 穿透 scoped，修改 Element Plus 组件内部样式 */
    :deep(.el-card) {
        background-color: rgba(255, 255, 255, 0.92);  /* 半透明白色 */
        backdrop-filter: blur(10px);  /* 毛玻璃效果 */
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);  /* 阴影 */
        width: 400px;
        border-radius: 8px;
    }
</style>
```

### 10.2 登录数据流全程追踪

```
1. 用户输入用户名"admin"，密码"123456"
   v-model 双向绑定：username.value = 'admin', password.value = '123456'

2. 用户点击"登录"按钮
   @click 触发 handleLogin()

3. handleLogin() 调用 login({ username: 'admin', password: '123456' })

4. 请求拦截器执行：
   userStore.token 为 null（未登录），不添加 Authorization 头
   
5. 发送 HTTP 请求：
   POST /api/auth/login
   Content-Type: application/json
   {"username":"admin","password":"123456"}

6. Vite 代理转发到：
   POST http://localhost:8083/api/auth/login

7. 网关（8083）处理：
   /api/auth/login 在白名单 → 直接放行（不验证Token）
   路由到 auth-service（8082）

8. auth-service 处理：
   验证密码 → 生成 JWT → 返回 LoginResponseDTO

9. 网关原样返回响应到前端：
   { code: 200, data: { token: "eyJ...", userInfo: {...}, expiration: 7200 } }

10. 响应拦截器执行：
    res.code === 200 → 放行，返回 res

11. handleLogin() 中的 await 得到 res：
    userStore.setUser(res.data.token, res.data.userInfo)
    localStorage.setItem('token', 'eyJ...')
    localStorage.setItem('userInfo', '{"userId":1,"roleCode":"admin",...}')

12. 路由跳转：
    router.replace(roleHomeMap['admin']) = '/admin/home'
    beforeEach 守卫：已登录，/admin/home 要求 admin 角色，匹配 → next()
    
13. AdminHome.vue 渲染（管理员首页）
```

---

## 11. 各角色页面详解

### 11.1 顾客订单页（Customer/Order.vue）— 最复杂的页面

这个页面包含：Tab 切换、订单列表、详情弹窗、物流地图，是前端最综合的页面。

**核心数据结构**：

```typescript
const loading = ref(false)        // 列表加载状态
const orders = ref<any[]>([])     // 全部订单列表
const activeTab = ref('all')      // 当前 Tab（'all'/'0'/'1'/.../）

const detailVisible = ref(false)  // 详情弹窗是否显示
const detailLoading = ref(false)  // 详情加载状态
const currentDetail = ref<any>(null)  // 当前查看的订单详情（含商品列表）
const currentRoute = ref<any>(null)   // 当前订单的物流路线（含轨迹）

// 计算属性：根据 activeTab 过滤订单
const filteredOrders = computed(() => {
    if (activeTab.value === 'all') return orders.value  // 全部
    return orders.value.filter(o => String(o.orderStatus) === activeTab.value)
    // String(o.orderStatus)：将数字状态码转为字符串（因为Tab的name是字符串）
})
```

**`computed` 计算属性**：

```typescript
// computed：基于其他响应式数据计算出的衍生值
// 特点：当依赖的数据变化时自动重新计算；结果会缓存，依赖不变时不重复计算
const filteredOrders = computed(() => {
    // 依赖：orders.value 和 activeTab.value
    // 当用户切换Tab时，activeTab.value变化 → filteredOrders 自动重新计算 → 页面自动更新
    if (activeTab.value === 'all') return orders.value
    return orders.value.filter(o => String(o.orderStatus) === activeTab.value)
})
```

**打开订单详情（同时加载物流信息）**：

```typescript
const openDetail = async (order: any) => {
    detailVisible.value = true   // 先显示弹窗（内容加载中）
    detailLoading.value = true
    currentDetail.value = null   // 清空旧数据
    currentRoute.value = null

    try {
        // 并发发起两个请求（提升性能）
        // 实际这里是串行的，先拿订单详情，再按需拿物流
        
        // ① 获取订单详情（含商品列表）
        const res = await getOrderDetail(order.id)
        currentDetail.value = res.data  // { order: {...}, items: [...] }

        // ② 条件加载物流信息（只有待揽件/派送中/已完成才有物流路线）
        if (order.orderStatus === 2 || order.orderStatus === 3 || order.orderStatus === 4) {
            try {
                const routeRes = await getRouteByOrderId(order.id)
                currentRoute.value = routeRes.data
                // currentRoute.data = {
                //   route: { id, startLat, startLng, endLat, endLng, plannedRoute（GeoJSON字符串）, currentLat, currentLng },
                //   recentTracks: [ {latitude, longitude, createTime}, ... ]（时间倒序，最新在前）
                // }
            } catch {
                // 物流信息不存在时静默处理（catch块为空表示忽略错误）
            }
        }
    } catch {
        ElMessage.error('获取订单详情失败')
        detailVisible.value = false  // 获取失败则关闭弹窗
    } finally {
        detailLoading.value = false  // 无论成功失败都停止加载动画
    }
}
```

**状态映射函数（状态码 → 显示文字/颜色）**：

```typescript
// 订单状态文字
const orderStatusText = (status: number) => {
    const map: Record<number, string> = {
        0: '待支付', 1: '待发货', 2: '待揽件',
        3: '派送中', 4: '已完成', 5: '已取消',
    }
    return map[status] ?? '未知'  // ?? ：空值合并，当map[status]为undefined时返回'未知'
}

// Element Plus Tag 颜色类型
const orderStatusTagType = (status: number) => {
    const map: Record<number, string> = {
        0: 'warning',  // 黄色：待支付
        1: 'primary',  // 蓝色：待发货
        2: 'info',     // 灰色：待揽件
        3: 'primary',  // 蓝色：派送中
        4: 'success',  // 绿色：已完成
        5: 'danger',   // 红色：已取消
    }
    return map[status] ?? 'info'
}
```

**日期格式化**：

```typescript
const formatDate = (date: string) => {
    if (!date) return '-'  // 空值处理
    return new Date(date).toLocaleString('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit'
    })
    // 输入：'2024-01-15T12:30:00'
    // 输出：'2024/01/15 12:30'
}
```

**取消订单（带输入框的弹窗）**：

```typescript
const handleCancel = async (orderId: number) => {
    try {
        // ElMessageBox.prompt：带文本框的确认弹窗
        const { value: reason } = await ElMessageBox.prompt(
            '请输入取消原因（选填）',  // 提示文字
            '取消订单',                // 弹窗标题
            {
                confirmButtonText: '确认取消',
                cancelButtonText: '返回',
                inputPlaceholder: '请输入取消原因',
                inputType: 'textarea',
                confirmButtonClass: 'el-button--danger',  // 确认按钮变红
            }
        )
        // 解构赋值：{ value: reason }
        // ElMessageBox.prompt 的返回值是 { value: 用户输入的文字, action: 'confirm' }
        // 这里把 result.value 重命名为 reason

        await cancelOrder(orderId, reason ?? '')
        // reason ?? ''：用户没输入时，reason 为 null，用空字符串代替
        
        ElMessage.success('订单已取消')
        fetchOrders()  // 刷新订单列表
    } catch {
        // 用户点击"返回"（取消弹窗），ElMessageBox 会 reject
        // catch 捕获后静默处理（不显示错误）
    }
}
```

### 11.2 运输员配送页（Driver/Delivery.vue）

配送页分两个 Tab：

**Tab1：待接单大厅**——显示所有待揽件的配送任务，运输员可以选择接单。

**Tab2：我的配送**——显示该运输员历史的所有配送记录，可以更新状态。

**接单对话框关键逻辑**：

```typescript
// 接单操作
const handleAccept = async () => {
    acceptSubmitting.value = true
    try {
        // 调用 acceptDelivery API
        await acceptDelivery(
            acceptDeliveryRow.value.id,   // 配送记录ID
            selectedVehicleId.value       // 选择的车辆ID（可为空）
        )
        ElMessage.success('接单成功！')
        acceptVisible.value = false
        
        // 同时刷新两个Tab的数据（接单后从待接单大厅消失，出现在我的配送中）
        fetchPending()
        fetchMine()
    } finally {
        acceptSubmitting.value = false
    }
}

// 更新配送状态（运输中/已送达）
const handleUpdateStatus = async (row: any, newStatus: number) => {
    const statusText = newStatus === 2 ? '开始运输' : '确认送达'
    
    await ElMessageBox.confirm(
        `确认${statusText}？`,
        '状态更新',
        { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
    
    await updateDeliveryStatus(row.id, { status: newStatus, remark: '' })
    ElMessage.success(`已${statusText}`)
    fetchMine()  // 刷新我的配送列表
}
```

### 11.3 商户订单页（Shop/Order.vue）

商户最关键的操作是**发货**。改造后的发货流程分三步：商家选择发货仓库 → 前端并行调用「更新订单状态」和「创建物流路线（触发 LLM 决策）」→ 弹出 LLM 决策结果面板。

#### 发货弹窗流程

```typescript
// ① 点击"发货"按钮：打开仓库选择弹窗
const openShipDialog = async (order: any) => {
    shipOrder.value = order
    selectedWarehouseId.value = null
    shipVisible.value = true

    // 首次打开时加载仓库列表（只取 status=1 启用中的仓库）
    if (warehouses.value.length === 0) {
        const res = await getWarehouses()
        warehouses.value = (res.data ?? []).filter((w: Warehouse) => w.status === 1)
    }
}

// ② 商家在弹窗中选择仓库，点击"确认发货 & 规划路线"
const handleConfirmShip = async () => {
    const order = shipOrder.value
    const warehouse = selectedWarehouse.value   // 由 computed 自动映射

    // 先查收货地址详情（含经纬度），order.addressId 是地址外键
    const addrRes = await getAddressById(order.addressId)
    const addr = addrRes.data
    const endAddress = [addr.province, addr.city, addr.district, addr.detailAddress].join('')

    // 并行执行两个请求，节省等待时间
    const [, routeRes] = await Promise.all([
        // 请求1：更新订单状态为"待揽件"(2)
        updateOrderStatus(order.id, 2),
        // 请求2：调用 logistics-service 创建路线（触发 LLM_JUDGE/LLM_WAYPOINT 策略）
        createRoute({
            orderId: order.id,
            warehouseId: selectedWarehouseId.value,
            startAddress: warehouse.detailAddress,
            startLatitude: warehouse.latitude,
            startLongitude: warehouse.longitude,
            endAddress,
            endLatitude: addr.latitude,
            endLongitude: addr.longitude,
            receiverName: addr.receiverName,
            receiverPhone: addr.receiverPhone,
        }),
    ])

    // ③ 展示 LLM 决策结果弹窗（routeRes.data 包含 llmDecision）
    llmResult.value = routeRes.data
    llmResultVisible.value = true
}
```

#### LLM 决策结果展示

`createRoute` 的响应体（`RouteResultDTO`）包含 `llmDecision` 对象，前端弹窗中展示以下字段：

| 字段 | 说明 | 展示方式 |
|------|------|----------|
| `llmEnhanced` | 是否成功调用 LLM（false 表示 A* 兜底） | 绿/橙标签 |
| `llmDecision.selectedCandidate` | LLM 选中第几条候选路线（1/2/3） | 文字 |
| `llmDecision.confidenceLevel` | 置信度：HIGH/MEDIUM/LOW | 绿/橙/红标签 |
| `llmDecision.adjustedDurationMs` | 调整后预计时长（毫秒） | 自动转换为分钟/小时 |
| `llmDecision.summary` | LLM 决策摘要 | 文字 |
| `llmDecision.reasoning` | LLM 推理原文 | 可滚动文本区域 |
| `llmDecision.warnings` | 风险提示列表 | 橙色警告条列表 |

> **注意**：`llmDecision` 字段只存在于 `createRoute` 的响应中，不会持久化到数据库。
> 后续调用 `getRouteByOrderId` 查询已有路线时，不会再包含此字段。

---

## 12. 地图组件深度解析（RouteMap.vue）

`RouteMap.vue` 是本项目技术难度最高的前端组件，集成了 Leaflet 地图库，同时展示：
- 🟢 绿色圆点：发货地（仓库）
- 🔴 红色圆点：收货地
- 🔵 蓝色圆点：运输员当前位置
- 蓝色虚线：规划路线（A*算法规划的理论路线）
- 橙色实线：实际轨迹（GPS实际走过的路）

### 12.1 Props 接口定义

```typescript
const props = defineProps<{
    startLat?: number | null    // 发货地纬度（如 31.23）
    startLng?: number | null    // 发货地经度（如 121.55）
    endLat?: number | null      // 收货地纬度
    endLng?: number | null      // 收货地经度
    currentLat?: number | null  // 当前位置纬度（实时更新）
    currentLng?: number | null  // 当前位置经度
    plannedRoute?: string | null    // GeoJSON字符串（规划路线坐标点序列）
    recentTracks?: Track[]          // 最近的GPS轨迹记录
    startLabel?: string             // 发货地弹窗文字
    endLabel?: string               // 收货地弹窗文字
}>()
// ? 表示可选参数，| null 表示也可以传null
// TypeScript 联合类型：number | null 表示"要么是数字，要么是null"
```

### 12.2 有效性检查

```typescript
const hasCoordinates = computed(() =>
    (props.startLat && props.startLng) || (props.endLat && props.endLng)
)
// 如果没有任何坐标，显示 "暂无地图信息" 的空状态
// 而不是显示一张空地图（体验更好）

// 模板中：
// <div v-if="hasCoordinates" ref="mapEl" class="map-container"></div>
// <el-empty v-else description="暂无地图信息（地址未配置坐标）" />
```

### 12.3 自定义图标

```typescript
// 创建圆形 div 图标（避免使用 Leaflet 默认的图钉图标）
const circleIcon = (color: string, size = 12) =>
    L.divIcon({
        className: '',  // 不使用默认的 CSS 类（避免默认样式干扰）
        html: `<div style="
            width:${size}px;
            height:${size}px;
            border-radius:50%;          /* 圆形 */
            background:${color};
            border:3px solid #fff;      /* 白色描边，提高对比度 */
            box-shadow:0 2px 6px rgba(0,0,0,0.35)  /* 阴影，让图标浮起来 */
        "></div>`,
        iconSize: [size, size],          // 图标整体大小
        iconAnchor: [size/2, size/2],   // 锚点：图标中心对准坐标点
    })

// 使用：
L.marker([lat, lng], { icon: circleIcon('#67c23a', 14) })  // 绿色，14px
L.marker([lat, lng], { icon: circleIcon('#f56c6c', 14) })  // 红色
L.marker([lat, lng], { icon: circleIcon('#409eff', 16) })  // 蓝色，稍大
```

### 12.4 地图初始化（onMounted）

```typescript
onMounted(() => {
    // onMounted：组件的 DOM 已经渲染完成，此时可以安全操作 DOM
    if (!hasCoordinates.value || !mapEl.value) return
    // mapEl.value 是 <div ref="mapEl"> 的真实 DOM 元素

    // 创建 Leaflet 地图实例
    map = L.map(mapEl.value, { zoomControl: true })
    // zoomControl: true：显示左上角的+/-缩放按钮

    // 加载 OpenStreetMap 地图瓦片
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap',
        maxZoom: 18,  // 最大缩放级别（越大越详细，18级可以看到建筑轮廓）
    }).addTo(map)

    const bounds: L.LatLngTuple[] = []  // 用于最后自动缩放到合适范围
```

### 12.5 添加标记和弹窗

```typescript
    // 发货地标记（绿色）
    if (props.startLat && props.startLng) {
        L.marker([props.startLat, props.startLng], { icon: circleIcon('#67c23a', 14) })
            .bindPopup(`<b>发货地</b><br>${props.startLabel ?? ''}`)
            // bindPopup：点击标记时弹出信息框
            // 模板字符串中可以写 HTML（<b>加粗</b>，<br>换行）
            .addTo(map)
        bounds.push([props.startLat, props.startLng])
        // 把这个坐标加入边界数组，最后用来调整地图视野
    }
```

### 12.6 绘制规划路线

```typescript
    // 规划路线（蓝色虚线）
    if (props.plannedRoute) {
        try {
            // plannedRoute 是 JSON 字符串，需要先解析
            const geo = JSON.parse(props.plannedRoute)
            // geo = { type: "LineString", coordinates: [[121.55, 31.23], [121.54, 31.23], ...] }
            
            if (geo.coordinates?.length > 1) {
                // GeoJSON 坐标格式是 [经度, 纬度]（longitude first！）
                // Leaflet 需要 [纬度, 经度]（latitude first）
                // 必须做转换！
                const latlngs: L.LatLngTuple[] = geo.coordinates.map(
                    (c: number[]) => [c[1], c[0]] as L.LatLngTuple
                    //                 ↑纬度  ↑经度  交换顺序！
                )

                L.polyline(latlngs, {
                    color: '#409eff',    // 蓝色
                    weight: 3,           // 线宽 3px
                    opacity: 0.55,       // 55% 透明度（规划路线比实际轨迹淡一些）
                    dashArray: '8,5'     // 虚线：8px 实线 + 5px 间隔，循环
                }).addTo(map)
            }
        } catch { /* 解析失败时静默忽略 */ }
    }
```

### 12.7 绘制实际轨迹

```typescript
    // 实际轨迹（橙色实线）
    if (props.recentTracks && props.recentTracks.length > 1) {
        // 关键！后端返回的轨迹是时间倒序（最新的在前面）
        // 绘制路线需要时间正序（从起点到终点）
        // [...props.recentTracks]：先创建副本（避免修改原数组）
        const pts: L.LatLngTuple[] = [...props.recentTracks]
            .reverse()  // 反转数组（倒序 → 正序）
            .filter(t => t.latitude && t.longitude)  // 过滤掉坐标为空的记录
            .map(t => [t.latitude, t.longitude])      // 转换格式（轨迹已经是[纬度,经度]）

        if (pts.length > 1) {  // 至少需要2个点才能画线
            L.polyline(pts, {
                color: '#e6a23c',  // 橙色（#e6a23c 是 Element Plus 的 warning 颜色）
                weight: 3,
                opacity: 0.85
            }).addTo(map)
        }
    }
```

### 12.8 自定义图例控件

```typescript
    // 创建图例（地图右下角的说明）
    const LegendControl = L.Control.extend({
        // L.Control.extend：Leaflet 的继承机制（基于原型链，非ES6 class）
        onAdd() {
            // onAdd：控件被添加到地图时调用，必须返回 DOM 元素
            const div = L.DomUtil.create('div', 'map-legend')
            // L.DomUtil.create('div', 'className')：创建并返回 DOM 元素
            
            div.innerHTML = `
                <div style="background:#fff;padding:8px 10px;border-radius:6px;font-size:12px;...">
                    <!-- 图例项：圆点 + 文字 -->
                    <div>
                        <span style="display:inline-block;width:10px;height:10px;border-radius:50%;
                                     background:#67c23a;margin-right:5px"></span>发货地
                    </div>
                    <!-- ...其他图例项 -->
                </div>`
            return div
        }
    })
    new LegendControl({ position: 'bottomright' }).addTo(map)
    // position: 'bottomright'：控件显示在地图右下角
```

### 12.9 自动调整视野范围

```typescript
    // 根据所有坐标点，自动调整地图视野（让所有标记都可见）
    if (bounds.length === 1) {
        map.setView(bounds[0], 13)  // 只有一个点，以该点为中心，缩放13级
    } else if (bounds.length > 1) {
        map.fitBounds(bounds, { padding: [40, 40] })
        // fitBounds：调整视野刚好包含所有 bounds 中的坐标
        // padding: [40, 40]：上下/左右各留 40px 的空白（不让标记紧贴边缘）
    }
```

### 12.10 销毁地图（onUnmounted）

```typescript
onUnmounted(() => {
    // onUnmounted：组件卸载时（用户切换到其他页面时）执行
    map?.remove()  // 销毁 Leaflet 地图实例，释放内存
    map = null     // 清除引用，帮助垃圾回收
})
// 如果不销毁，切换页面后地图实例仍在内存中，多次切换会造成内存泄漏
```

---

## 13. 完整业务流程数据流动

### 13.1 顾客下单完整链路

```
① 顾客点击商品"加入购物车/立即购买"
   Customer/Product.vue → router.push('/customer/home/shop-order?shopId=1&productId=10')

② 顾客填写下单信息（收货地址、数量）
   Customer/CreateOrder.vue

③ 点击"提交订单"
   api/order.ts → POST /api/orders
   { shopId: 1, addressId: 3, items: [{productId:10, quantity:2}] }

④ 网关转发到 order-service
   order-service:
     验证地址(→customer-service)
     验证商品+扣库存(→shop-service)
     创建订单记录
     返回 OrderDetailDTO

⑤ 前端收到成功响应
   ElMessage.success('下单成功')
   router.push('/customer/home/orders')
```

### 13.2 商户发货完整链路

```
① 商户在订单列表看到"待发货"的订单
   Shop/Order.vue → 筛选 orderStatus=1 且 paymentStatus=1 的订单

② 商户点击"发货"按钮
   openShipDialog(row) → 弹出"发货确认"弹窗
   弹窗自动加载启用中的仓库列表（status=1），展示仓库名称 + 地址

③ 商户选择发货仓库，点击"确认发货 & 规划路线"
   handleConfirmShip()

④ 前端串行/并行发起 3 个请求：
   a. getAddressById(order.addressId)
      → GET /api/customers/address/{addressId}
      → 获取收货地址详情（省/市/区/详细地址 + 经纬度 + 收货人信息）

   b. updateOrderStatus(orderId, 2)
      → PUT /api/orders/{id}/status  { orderStatus: 2 }
      → order-service 验证状态(必须是待发货=1)
        调 driver-service 创建配送记录（进入待接单大厅）

   c. createRoute({ orderId, warehouseId, startAddress/Lat/Lng, endAddress/Lat/Lng, ... })
      → POST /api/logistics/routes
      → logistics-service 根据 routing.strategy 配置决定策略：
          LLM_JUDGE：A* 生成 3 条候选 → 历史数据 + DeepSeek LLM 裁判选最优
          LLM_WAYPOINT：LLM 先决定绕行路点 → A* 分段规划
          A_STAR：纯 A* 算法（无 LLM）
      → 路线存入 logistics_route 表
      → 响应体包含 llmDecision（仅本次响应，不持久化）

   注：b 和 c 通过 Promise.all 并行执行，节省等待时间

⑤ 前端收到两个请求的响应
   关闭发货弹窗，刷新订单列表（状态变为"待揽件"）
   弹出"LLM 决策结果"面板，展示：
     - llmEnhanced（是否成功调用 LLM）
     - selectedCandidate（选中第几条路线）
     - confidenceLevel（置信度 HIGH/MEDIUM/LOW）
     - reasoning（LLM 推理原文，可滚动）
     - warnings（风险提示列表）
```

### 13.3 运输员接单完整链路

```
① 运输员在"待接单大厅"看到新的配送任务
   Driver/Delivery.vue（Tab1：待接单大厅）

② 运输员点击"接单"
   openAcceptDialog(row) → 弹出接单对话框（选择车辆）

③ 确认接单
   acceptDelivery(deliveryId, vehicleId) → POST /api/drivers/deliveries/{id}/accept

④ driver-service 处理：
   更新 order_delivery（driverId, vehicleId, status=1）
   调 order-service 更新订单状态（→派送中=3）
   调 logistics-service 绑定司机到物流路线

⑤ 前端收到成功响应
   ElMessage.success('接单成功！')
   fetchPending()（待接单大厅减少一条）
   fetchMine()（我的配送增加一条）
```

### 13.4 顾客查看物流地图

```
① 顾客打开订单详情
   openDetail(order) → getOrderDetail(orderId)

② 判断订单状态（待揽件=2 / 派送中=3 / 已完成=4）
   if (order.orderStatus === 2 || 3 || 4)

③ 获取物流路线
   getRouteByOrderId(orderId) → GET /api/logistics/routes/order/{orderId}
   返回 RouteDetailDTO：
   {
     route: { plannedRoute: GeoJSON 字符串,
              startLatitude / startLongitude, endLatitude / endLongitude,
              currentLatitude / currentLongitude, routeNo, statusDesc, ... },
     nodes: [ { sequenceNo, nodeName, nodeAddress, ... } ],
     recentTracks: [ { latitude, longitude, trackTime, ... }, ... ]  // 时间倒序
     driverName / driverPhone: 可选
   }

④ 传入 RouteMap.vue 组件（props 为驼峰：startLat、recentTracks 等）
   <RouteMap
     :start-lat / :start-lng   (仓库坐标)
     :end-lat / :end-lng       (收货坐标)
     :current-lat / :current-lng (运输员当前位置)
     :planned-route            (GeoJSON 字符串)
     :recent-tracks            (后端倒序；组件内 reverse 后画橙色线)
   />

⑤ RouteMap.vue 渲染：
   - Leaflet 加载 OpenStreetMap 瓦片
   - 绘制绿点（仓库）、红点（收货地）、蓝点（当前位置）
   - 绘制蓝色虚线（规划路线，解析GeoJSON）
   - 绘制橙色实线（实际轨迹，reverse后按时间正序连线）
   - 自动缩放到合适视野
```

---

## 14. Vue 模板语法参考手册

本项目中用到的所有 Vue 模板指令：

### 14.1 数据绑定

```html
<!-- 文本插值 -->
<p>{{ order.orderNo }}</p>
<!-- 输出 order.orderNo 的值，当值变化时自动更新 -->

<!-- 属性绑定（动态属性） -->
<el-button :type="btnType">提交</el-button>
<!-- :type 等价于 v-bind:type，将 btnType 变量的值绑定到 type 属性 -->

<!-- 双向绑定 -->
<el-input v-model="username" />
<!-- 输入框内容 ↔ username 变量，双向同步 -->
<!-- 等价于：:value="username" @input="username = $event" -->
```

### 14.2 条件渲染

```html
<!-- v-if：条件为真时渲染（DOM中存在/不存在） -->
<div v-if="showDetail">详情内容</div>

<!-- v-else-if / v-else：配合 v-if 使用 -->
<el-tag v-if="status === 0">待支付</el-tag>
<el-tag v-else-if="status === 4">已完成</el-tag>
<el-tag v-else>其他状态</el-tag>

<!-- v-show：条件为真时显示（DOM始终存在，只是CSS display切换） -->
<!-- 适合频繁切换的场景（比v-if性能好，因为不销毁DOM） -->
<div v-show="isVisible">内容</div>
```

### 14.3 列表渲染

```html
<!-- v-for：循环渲染 -->
<div v-for="order in orders" :key="order.id">
    <!-- :key 必须提供，帮助 Vue 高效更新列表（类似数据库主键） -->
    {{ order.orderNo }}
</div>

<!-- 带索引的循环 -->
<div v-for="(item, index) in items" :key="index">
    {{ index + 1 }}. {{ item.name }}
</div>
```

### 14.4 事件处理

```html
<!-- @click：点击事件（v-on:click 的缩写） -->
<el-button @click="handleSubmit">提交</el-button>

<!-- 带参数 -->
<el-button @click="deleteOrder(order.id)">删除</el-button>

<!-- 键盘事件 -->
<el-input @keyup.enter="handleSearch" />
<!-- .enter 修饰符：只在按 Enter 键时触发 -->

<!-- 表单提交（阻止默认行为） -->
<form @submit.prevent="handleSubmit">
<!-- .prevent：阻止表单默认的页面刷新行为 -->
```

### 14.5 模板引用

```html
<!-- ref 属性：获取 DOM 元素引用 -->
<div ref="mapEl" class="map-container"></div>

<!-- script 中访问 -->
const mapEl = ref<HTMLElement | null>(null)
// 组件挂载后，mapEl.value 就是真实的 DOM 元素
```

### 14.6 插槽（Slot）

```html
<!-- Element Plus 中大量使用插槽自定义内容 -->
<el-table-column label="状态" width="100">
    <template #default="{ row }">
        <!-- #default：具名插槽，default 是表格列的默认插槽 -->
        <!-- { row }：解构插槽传入的数据，row 是当前行的数据 -->
        <el-tag :type="orderStatusTagType(row.orderStatus)">
            {{ orderStatusText(row.orderStatus) }}
        </el-tag>
    </template>
</el-table-column>
```

---

## 15. 常见问题 Q&A

### Q1：前端修改代码后，页面会自动更新吗？

**A**：是的！Vite 的热模块替换（HMR）功能在开发时会监听文件变化，保存后浏览器自动更新，不需要手动刷新。

### Q2：为什么要用 TypeScript 而不是 JavaScript？

**A**：TypeScript 在编写代码时就能发现类型错误（比如把字符串传给需要数字的地方），而不是等运行时才报错。对大型项目来说，类型系统大大减少了 bug。本项目使用 `lang="ts"` 声明组件使用 TypeScript。

### Q3：`ref()` 和 `reactive()` 什么时候用哪个？

**A**：
- `ref()`：适合**基本类型**（`number`、`string`、`boolean`）和**整体替换**的场景
  ```typescript
  const count = ref(0)         // 数字
  const name = ref('')          // 字符串
  const list = ref<any[]>([])  // 数组（会整体替换 list.value = newArray）
  ```
- `reactive()`：适合**对象**，需要修改内部属性（而不是整体替换）
  ```typescript
  const form = reactive({ username: '', password: '' })
  form.username = '张三'  // 修改内部属性
  ```

### Q4：`v-if` 和 `v-show` 有什么区别？

**A**：
- `v-if="false"`：DOM 元素被完全移除（不存在）；切换时会销毁/创建组件，开销较大
- `v-show="false"`：DOM 元素存在，只是 `display: none`；切换时只改 CSS，开销小

何时用 `v-if`：内容切换不频繁，第一次渲染可能不显示（减少初始渲染量）
何时用 `v-show`：内容频繁切换（如 Tab 切换）

### Q5：地图加载很慢怎么办？

**A**：地图瓦片来自 OpenStreetMap 的公共服务器，国内网络访问可能较慢。解决方案：
1. 配置瓦片代理缓存（Nginx）
2. 使用国内地图服务（高德/百度地图 SDK）替换 Leaflet + OSM

### Q6：`async/await` 报错怎么处理？

**A**：所有 `await` 应该放在 `try-catch` 中，但可以简化处理：

```typescript
// 方式1：每个操作单独 try-catch（精确控制）
const handleSomething = async () => {
    try {
        await someApi()
    } catch (err) {
        console.error(err)  // 拦截器已处理，这里只记录日志
    }
}

// 方式2：静默处理（拦截器已显示错误，catch 为空）
const handleSomething = async () => {
    try {
        await someApi()
        ElMessage.success('操作成功')  // 成功后的操作
    } catch {
        // 拦截器已经 ElMessage.error 了，这里不处理
    }
}
```

### Q7：为什么有些函数要加 `const` 有些要加 `function`？

**A**：在 Vue 3 的 `<script setup>` 中，两种写法都可以，无本质区别：

```typescript
// 箭头函数（const）
const handleLogin = async () => { ... }

// 普通函数（function）
async function handleLogin() { ... }
```

区别：普通函数有变量提升（可以在声明前调用），箭头函数没有。在 `<script setup>` 中通常混用，按个人习惯。

---

## 附录：知识点速查表

| 知识点 | 说明 | 示例 |
|--------|------|------|
| `ref(value)` | 响应式基本类型 | `const count = ref(0); count.value++` |
| `reactive(obj)` | 响应式对象 | `const form = reactive({name:''})` |
| `computed(() => ...)` | 计算属性（自动缓存） | `const filtered = computed(() => list.value.filter(...))` |
| `watch(source, cb)` | 监听响应式数据变化 | `watch(count, (newVal) => console.log(newVal))` |
| `onMounted(cb)` | 组件挂载后执行 | `onMounted(() => initMap())` |
| `onUnmounted(cb)` | 组件卸载时执行 | `onUnmounted(() => map?.remove())` |
| `defineProps<T>()` | 声明组件的 props | `const props = defineProps<{name: string}>()` |
| `v-model` | 双向绑定 | `<input v-model="name" />` |
| `v-if` / `v-else` | 条件渲染 | `<div v-if="show">内容</div>` |
| `v-for` + `:key` | 列表渲染 | `<div v-for="item in list" :key="item.id">` |
| `@click` | 点击事件 | `<button @click="fn">` |
| `:prop` | 动态属性绑定 | `<el-button :type="type">` |
| `?.` | 可选链（防止null报错） | `user?.name` |
| `??` | 空值合并（null/undefined时取默认值） | `name ?? '未知'` |
| `async/await` | 异步操作同步写法 | `const res = await fetch(url)` |
| `Promise.reject()` | 手动创建失败的 Promise | 拦截器中 `return Promise.reject(error)` |
| `localStorage` | 浏览器持久化存储 | `localStorage.setItem('k', 'v')` |
| `JSON.stringify()` | 对象→JSON字符串 | `JSON.stringify({name:'张三'})` |
| `JSON.parse()` | JSON字符串→对象 | `JSON.parse('{"name":"张三"}')` |
| `Record<K,V>` | TypeScript键值对类型 | `Record<string, number>` |
| `L.map()` | 创建 Leaflet 地图 | `const map = L.map(el)` |
| `L.tileLayer()` | 加载地图瓦片 | `L.tileLayer('https://...').addTo(map)` |
| `L.polyline()` | 绘制折线 | `L.polyline([[lat,lng],...]).addTo(map)` |
| `L.marker()` | 添加标记点 | `L.marker([lat,lng]).addTo(map)` |
| `.bindPopup()` | 标记点击弹窗 | `marker.bindPopup('发货地')` |
| `map.fitBounds()` | 自动调整地图视野 | `map.fitBounds([[31.2,121.4],[31.3,121.6]])` |
| GeoJSON坐标顺序 | `[经度,纬度]`（注意：经度在前！） | `[[121.55, 31.23]]` |
| Leaflet坐标顺序 | `[纬度,经度]`（与GeoJSON相反！） | `[[31.23, 121.55]]` |
