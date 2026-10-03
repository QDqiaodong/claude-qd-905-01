<script setup>
import { computed, onMounted, ref } from 'vue'
import router from './router'
import api from './api'

import StockView from './views/StockView.vue'
import CuttingView from './views/CuttingView.vue'
import TemperingView from './views/TemperingView.vue'
import DeepProcessView from './views/DeepProcessView.vue'
import InspectionView from './views/InspectionView.vue'
import ReworkView from './views/ReworkView.vue'

const modules = [
  {
    key: 'stock',
    name: '原片与库位',
    desc: '批次 · 种类 · 厚度 · 区架层',
    children: [
      { label: '立体库位图', path: '/stock', query: {} },
      { label: '原片台账', path: '/stock', query: { view: 'list' } }
    ]
  },
  {
    key: 'cutting',
    name: '切割与优化',
    desc: '布局图 · 出片清单 · 利用率',
    children: [
      { label: '切割布局', path: '/cutting', query: {} },
      { label: '余料排行', path: '/cutting', query: { view: 'rank' } }
    ]
  },
  {
    key: 'tempering',
    name: '磨边与钢化',
    desc: '边型 · 温控隧道 · 破碎率',
    children: [
      { label: '钢化炉隧道', path: '/tempering', query: {} },
      { label: '磨边记录', path: '/tempering', query: { view: 'grinding' } }
    ]
  },
  {
    key: 'deep',
    name: '深加工工序',
    desc: '镀膜 · 中空 · 夹胶',
    children: [
      { label: '镀膜', path: '/deep', query: { type: 'COATING' } },
      { label: '中空', path: '/deep', query: { type: 'INSULATING' } },
      { label: '夹胶', path: '/deep', query: { type: 'LAMINATING' } }
    ]
  },
  {
    key: 'inspection',
    name: '检验与成品架',
    desc: '缺陷分布 · 判定 · 发货',
    children: [
      { label: '缺陷分布图', path: '/inspection', query: {} },
      { label: '成品架', path: '/inspection', query: { view: 'rack' } }
    ]
  },
  {
    key: 'rework',
    name: '返修闭环',
    desc: '隔离 · 处置 · 复检 · 放行',
    children: [
      { label: '返修工作区', path: '/rework', query: {} },
      { label: '待复检', path: '/rework', query: { status: 'PENDING_RECHECK' } }
    ]
  }
]

const route = computed(() => router.currentRoute.value)
const currentPath = computed(() => route.value?.path || '/stock')
const currentQuery = computed(() => route.value?.query || {})

// main.js 里没有 app.use(router)，这里直接驱动路由实例并用 <component :is> 渲染。
// 路由 → 组件的映射自己维护一份，不依赖 router 内部结构，最稳。
const viewMap = {
  '/stock': StockView,
  '/cutting': CuttingView,
  '/tempering': TemperingView,
  '/deep': DeepProcessView,
  '/inspection': InspectionView,
  '/rework': ReworkView
}

const currentView = computed(
  () => viewMap[currentPath.value]
    || route.value?.matched?.[0]?.components?.default
    || StockView
)

const overview = ref(null)

function go(item) {
  router.push({ path: item.path, query: item.query })
}

function isActive(mod) {
  return currentPath.value === '/' + mod.key
}

function isChildOn(child) {
  if (child.path !== currentPath.value) return false
  const q = currentQuery.value || {}
  const keys = Object.keys(child.query || {})
  return keys.length === Object.keys(q).length
    && keys.every((k) => String(q[k] ?? '') === String(child.query[k]))
}

async function loadOverview() {
  try {
    overview.value = await api.stats.overview()
  } catch (e) {
    overview.value = null
  }
}

onMounted(() => {
  loadOverview()
  // 路由没有 install，初始 currentRoute 是 START_LOCATION，这里按地址栏补一次导航
  const hash = window.location.hash.replace(/^#/, '')
  router.replace(hash.startsWith('/') && hash !== '/' ? hash : '/stock')
})
</script>

<template>
  <div class="app-shell">
    <!-- 顶栏 -->
    <header class="app-topbar glass">
      <div class="brand-mark"><span>G</span></div>
      <div>
        <div class="brand-title">玻璃深加工厂 · 原片与工序流水管理</div>
        <div class="brand-sub">原片 → 切割 → 磨边 → 钢化 → 镀膜/中空/夹胶 → 检验 → 成品架；判返修 ↺ 隔离 → 处置 → 复检 → 放行</div>
      </div>
      <div class="top-stats" v-if="overview">
        <div class="top-stat"><b>{{ overview.stockCount }}</b><span>原片批次</span></div>
        <div class="top-stat"><b>{{ overview.cuttingCount }}</b><span>切割单</span></div>
        <div class="top-stat"><b>{{ overview.temperingCount }}</b><span>钢化炉次</span></div>
        <div class="top-stat"><b>{{ overview.deepProcessCount }}</b><span>深加工批次</span></div>
        <div class="top-stat"><b>{{ overview.passCount }}</b><span>检验合格</span></div>
        <div class="top-stat"><b>{{ overview.rackCount }}</b><span>成品架</span></div>
        <div class="top-stat"><b :style="{ color: overview.reworkOpenCount > 0 ? '#ffb74d' : '#4dd6a8' }">{{ overview.reworkOpenCount }}</b><span>返修未闭环</span></div>
      </div>
    </header>

    <div class="app-body">
      <!-- 左侧「玻璃幕墙」导航：五块半透明玻璃板 -->
      <nav class="curtain-wall">
        <div class="wall-caption">PROCESS CURTAIN WALL</div>
        <div v-for="(mod, idx) in modules" :key="mod.key" class="wall-slot">
          <div
            class="wall-panel"
            :class="{ active: isActive(mod) }"
            @click="go(mod.children[0])"
          >
            <div class="sheen"></div>
            <div>
              <span class="wp-index">{{ idx + 1 }}</span>
              <span class="wp-name">{{ mod.name }}</span>
            </div>
            <div class="wp-desc">{{ mod.desc }}</div>
          </div>
          <div class="wall-children" v-if="isActive(mod)">
            <div
              v-for="child in mod.children"
              :key="child.label"
              class="wall-child"
              :class="{ on: isChildOn(child) }"
              @click.stop="go(child)"
            >{{ child.label }}</div>
          </div>
        </div>
      </nav>

      <!-- 内容区 -->
      <main class="app-content">
        <component :is="currentView" :key="currentPath" />
      </main>
    </div>
  </div>
</template>
