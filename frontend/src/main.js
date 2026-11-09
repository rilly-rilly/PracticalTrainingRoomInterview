import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { restoreAuth } from './auth'
import './styles.css'

restoreAuth()

createApp(App).use(router).mount('#app')
