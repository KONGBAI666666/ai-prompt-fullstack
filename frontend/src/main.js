import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus：UI 组件库，全局注册后所有页面可直接使用 el-xxx 组件
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
