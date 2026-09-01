import './assets/main.css'
import './assets/theme.css'

import { createApp } from 'vue'
// Element Plus：UI 组件库，全局注册后所有页面可直接使用 el-xxx 组件
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// EP 官方暗黑模式变量：html 带 dark class 时所有组件自动切深色
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'

const app = createApp(App)

// 全局注册 EP 图标，模板里可直接写 <el-icon><User /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
