import { createRouter, createWebHashHistory } from 'vue-router'

// 注意：main.js 里没有 app.use(router)，路由实例由 App.vue 直接驱动，
// 所以这里全部用静态 import —— <component :is> 拿到的是组件对象本身。
import StockView from '../views/StockView.vue'
import CuttingView from '../views/CuttingView.vue'
import TemperingView from '../views/TemperingView.vue'
import DeepProcessView from '../views/DeepProcessView.vue'
import InspectionView from '../views/InspectionView.vue'
import ReworkView from '../views/ReworkView.vue'

const routes = [
  { path: '/', redirect: '/stock' },
  { path: '/stock', name: 'stock', component: StockView },
  { path: '/cutting', name: 'cutting', component: CuttingView },
  { path: '/tempering', name: 'tempering', component: TemperingView },
  { path: '/deep', name: 'deep', component: DeepProcessView },
  { path: '/inspection', name: 'inspection', component: InspectionView },
  { path: '/rework', name: 'rework', component: ReworkView },
  { path: '/:pathMatch(.*)*', redirect: '/stock' }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
