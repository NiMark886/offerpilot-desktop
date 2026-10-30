import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { waitForDesktopBackend } from './api/base'
import './styles/main.css'

function mount() {
  createApp(App)
    .use(createPinia())
    .use(router)
    .use(ElementPlus)
    .mount('#app')
}

waitForDesktopBackend()
  .catch((error) => console.error(error))
  .finally(mount)
