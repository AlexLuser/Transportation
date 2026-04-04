import { createApp } from 'vue'
import App from './App.vue'

import { createPinia } from 'pinia'

import {router} from './router'
// 第一步：引入 ElementPlus 和它的样式
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 第二步：引入中文语言包（让日期选择器、分页等组件显示中文）
import zhCn from 'element-plus/es/locale/lang/zh-cn'

const app = createApp(App)

const pinia = createPinia()

// 第三步：注册 ElementPlus，并指定使用中文
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
