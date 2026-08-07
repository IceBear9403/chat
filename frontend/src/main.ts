import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant, { Lazyload } from 'vant'
import 'vant/lib/index.css'

import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Vant)
// 图片懒加载：滚动进入视口才加载，降低低端机流量与首屏压力
app.use(Lazyload, { lazyComponent: true })

app.mount('#app')
