import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './index.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
// 顺序要求：先装 Pinia，路由守卫里才能 useAuthStore()
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
