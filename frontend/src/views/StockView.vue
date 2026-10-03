<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import router from '../router'

const GLASS_TYPES = [
  { key: 'FLOAT_CLEAR', label: '浮法白玻' },
  { key: 'ULTRA_CLEAR', label: '超白玻' },
  { key: 'TINTED', label: '有色玻璃' },
  { key: 'LOWE_BASE', label: 'Low-E 基片' }
]
const THICKNESS = [4, 5, 6, 8, 10, 12, 15, 19]

const route = computed(() => router.currentRoute.value)
const isList = computed(() => (route.value?.query?.view || '') === 'list')

const warehouse = ref(null)
const stocks = ref([])
const loading = ref(false)

const typeFilter = ref('')
const thickFilter = ref(null)

const drawer = ref(false)
const current = ref(null)
const specInput = ref('')
const moveTo = ref(null)
const locations = ref([])

function rgba(hex, alpha) {
  const h = (hex || '#4fa8d8').replace('#', '')
  const n = parseInt(h.length === 3 ? h.split('').map((c) => c + c).join('') : h, 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}

function blockStyle(b) {
  return {
    background: `linear-gradient(140deg, ${rgba(b.color, 0.62)}, ${rgba(b.color, 0.22)} 60%, ${rgba('#ffffff', 0.10)})`,
    borderColor: rgba(b.color, 0.85)
  }
}

const zones = computed(() => warehouse.value?.zones || [])

function filteredBlocks(level) {
  return (level.blocks || []).filter((b) => {
    if (typeFilter.value && b.glassType !== typeFilter.value) return false
    if (thickFilter.value && b.thicknessMm !== thickFilter.value) return false
    return true
  })
}

const stat = computed(() => {
  let batches = 0
  let sheets = 0
  let area = 0
  for (const z of zones.value) {
    for (const r of z.racks || []) {
      for (const lv of r.levels || []) {
        for (const b of filteredBlocks(lv)) {
          batches++
          sheets += b.sheetCount || 0
          area += b.totalAreaSqm || 0
        }
      }
    }
  }
  return { batches, sheets, area: Math.round(area * 100) / 100 }
})

async function load() {
  loading.value = true
  try {
    warehouse.value = await api.stock.warehouse()
    stocks.value = await api.stock.list()
    if (!locations.value.length) locations.value = await api.stock.locations()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openBlock(b) {
  current.value = b
  specInput.value = b.specText
  moveTo.value = b.levelId
  drawer.value = true
}

async function applySpec() {
  if (!current.value) return
  try {
    const saved = await api.stock.applySpec(current.value.id, specInput.value)
    ElMessage.success(`规格串已反解：${saved.specText} → ${saved.thicknessMm}mm ${saved.lengthMm}×${saved.widthMm}`)
    current.value = { ...current.value, ...saved }
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function moveStock() {
  if (!current.value) return
  try {
    await api.stock.move(current.value.id, { locationId: moveTo.value })
    ElMessage.success('上架成功')
    drawer.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch([typeFilter, thickFilter], () => {})
onMounted(load)
</script>

<template>
  <div>
    <div class="view-head">
      <div class="view-title">原片与库位 · 立体货架</div>
      <div class="view-tip">区 → 架 → 层，点玻璃块查看批次详情；规格串由厚度 + 长×宽 派生</div>
    </div>

    <!-- 顶部筛选玻璃条 -->
    <div class="glass-bar">
      <span class="glass-bar-label">玻璃种类</span>
      <span class="chip" :class="{ on: typeFilter === '' }" @click="typeFilter = ''">全部</span>
      <span v-for="t in GLASS_TYPES" :key="t.key" class="chip" :class="{ on: typeFilter === t.key }"
            @click="typeFilter = t.key">{{ t.label }}</span>
      <span class="glass-bar-label" style="margin-left: 10px">厚度</span>
      <span class="chip" :class="{ on: thickFilter === null }" @click="thickFilter = null">全部</span>
      <span v-for="t in THICKNESS" :key="t" class="chip" :class="{ on: thickFilter === t }"
            @click="thickFilter = t">{{ t }}mm</span>
      <span style="margin-left: auto" class="glass-bar-label">
        命中 {{ stat.batches }} 批 / {{ stat.sheets }} 片 / {{ stat.area }} ㎡
      </span>
    </div>

    <!-- 台账视图 -->
    <div v-if="isList" class="glass glass-pad">
      <div class="section-title">原片台账</div>
      <el-table :data="stocks" size="small" max-height="520" stripe>
        <el-table-column prop="batchNo" label="批次号" width="130" />
        <el-table-column prop="glassType" label="玻璃种类" width="110" />
        <el-table-column prop="specText" label="规格串（派生列）" width="160" />
        <el-table-column prop="thicknessMm" label="厚度mm" width="80" />
        <el-table-column prop="lengthMm" label="长mm" width="80" />
        <el-table-column prop="widthMm" label="宽mm" width="80" />
        <el-table-column prop="sheetCount" label="片数" width="70" />
        <el-table-column prop="transmittance" label="透光率%" width="90" />
        <el-table-column prop="supplier" label="供应商" width="110" />
        <el-table-column prop="inboundDate" label="入厂日期" width="110" />
        <el-table-column label="总面积㎡" width="100">
          <template #default="{ row }">{{ row.totalAreaSqm }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 立体图视图 -->
    <div v-else v-loading="loading">
      <div v-for="zone in zones" :key="zone.zoneCode" class="glass glass-pad zone-block">
        <div class="section-title">
          {{ zone.zoneName }}（{{ zone.zoneCode }} 区）· {{ zone.racks.length }} 架
        </div>
        <div class="rack-row">
          <div v-for="rack in zone.racks" :key="rack.rackCode" class="rack">
            <div class="rack-code">{{ rack.rackCode }}</div>
            <div class="levels">
              <div v-for="lv in rack.levels" :key="lv.id" class="level-cell">
                <div class="lv-head">
                  <span>L{{ lv.levelNo }}</span>
                  <span class="lv-sheet">{{ lv.sheetCount }} 片</span>
                </div>
                <div class="load-bar" :class="{ over: lv.loadRate > 100 }">
                  <i :style="{ width: Math.min(100, lv.loadRate) + '%' }"></i>
                </div>
                <div class="lv-load">{{ lv.currentLoadKg }} / {{ lv.maxLoadKg }} kg</div>
                <div class="blocks">
                  <div
                    v-for="b in filteredBlocks(lv)"
                    :key="b.id"
                    class="stock-block"
                    :style="blockStyle(b)"
                    @click="openBlock(b)"
                  >
                    <span class="b-thick">{{ b.thicknessMm }}mm</span>
                    <span class="b-spec">{{ b.lengthMm }}×{{ b.widthMm }}</span>
                    <span class="b-batch">{{ b.batchNo }}</span>
                  </div>
                  <div v-if="!filteredBlocks(lv).length" class="empty-cell">空位</div>
                </div>
              </div>
            </div>
            <div class="rack-base"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 批次详情抽屉 -->
    <el-drawer v-model="drawer" :title="current ? '原片批次 ' + current.batchNo : ''" size="440px">
      <div v-if="current" class="drawer-body">
        <div class="detail-hero" :style="blockStyle(current)">
          <div class="hero-spec">{{ current.specText }}</div>
          <div class="hero-type">{{ current.glassTypeName }} · {{ current.sheetCount }} 片</div>
        </div>

        <div class="detail-grid">
          <div class="mini-card"><div class="k">厚度</div><div class="v">{{ current.thicknessMm }} mm</div></div>
          <div class="mini-card"><div class="k">透光率</div><div class="v">{{ current.transmittance }} %</div></div>
          <div class="mini-card"><div class="k">总面积</div><div class="v">{{ current.totalAreaSqm }} ㎡</div></div>
          <div class="mini-card"><div class="k">整批重量</div><div class="v">{{ current.weightKg }} kg</div></div>
        </div>

        <div class="detail-row"><span>供应商</span><b>{{ current.supplier }}</b></div>
        <div class="detail-row"><span>入厂日期</span><b>{{ current.inboundDate }}</b></div>
        <div class="detail-row"><span>规格长×宽</span><b>{{ current.lengthMm }} × {{ current.widthMm }} mm</b></div>

        <el-divider content-position="left">规格串反解（@Access 派生属性）</el-divider>
        <div class="spec-row">
          <el-input v-model="specInput" size="small" placeholder="如 8mm 2140×1650" />
          <el-button size="small" type="primary" @click="applySpec">解析写回</el-button>
        </div>
        <div class="hint">库里 spec_text 存的是拼接串，getter 由厚度+长宽拼出，setter 反解析并改写三个字段。</div>

        <el-divider content-position="left">上架到库位</el-divider>
        <div class="spec-row">
          <el-select v-model="moveTo" size="small" placeholder="选择库位" style="flex: 1">
            <el-option
              v-for="loc in locations" :key="loc.id"
              :label="loc.rackCode + '-' + loc.levelNo + ' 层（余 ' + loc.remainingLoadKg + 'kg）'"
              :value="loc.id"
            />
          </el-select>
          <el-button size="small" type="primary" @click="moveStock">上架</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.zone-block { margin-bottom: 14px; }
.rack-row { display: flex; gap: 14px; overflow-x: auto; padding-bottom: 6px; }
.rack { flex: 0 0 auto; width: 210px; }
.rack-code {
  text-align: center; font-size: 12px; font-weight: 700; color: #fff;
  padding: 3px 0; border-radius: 7px; margin-bottom: 7px;
  background: rgba(255, 255, 255, 0.12); border: 1px solid rgba(255, 255, 255, 0.28);
}
.levels { display: flex; flex-direction: column; gap: 8px; }
.level-cell {
  position: relative;
  padding: 7px 8px 9px;
  border-radius: 9px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.09), rgba(10, 16, 60, 0.28));
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.26), 0 4px 12px rgba(4, 8, 40, 0.35);
  min-height: 74px;
}
.lv-head { display: flex; justify-content: space-between; font-size: 11px; color: var(--ink-soft); }
.lv-sheet { color: #fff; font-weight: 700; }
.lv-load { font-size: 10px; color: var(--ink-dim); margin-top: 3px; }
.blocks { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 7px; }
.stock-block {
  position: relative;
  flex: 1 1 78px;
  min-height: 46px;
  border-radius: 7px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  padding: 5px 6px;
  cursor: pointer;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6), 0 3px 8px rgba(0, 0, 0, 0.3);
  transition: transform .18s ease, box-shadow .18s ease;
}
.stock-block::after {
  content: ''; position: absolute; inset: 0; pointer-events: none;
  background: linear-gradient(118deg, rgba(255,255,255,0) 34%, rgba(255,255,255,0.42) 48%, rgba(255,255,255,0) 62%);
  mix-blend-mode: screen;
}
.stock-block:hover { transform: translateY(-2px); box-shadow: 0 8px 18px rgba(0, 0, 0, 0.42), inset 0 1px 0 rgba(255,255,255,.7); }
.b-thick { display: block; font-size: 13px; font-weight: 800; color: #fff; text-shadow: 0 1px 3px rgba(0,0,0,.55); }
.b-spec { display: block; font-size: 10px; color: rgba(255, 255, 255, 0.92); }
.b-batch { display: block; font-size: 9px; color: rgba(255, 255, 255, 0.7); }
.empty-cell { font-size: 11px; color: var(--ink-dim); padding: 12px 4px; }
.rack-base {
  height: 7px; margin-top: 5px; border-radius: 3px;
  background: linear-gradient(90deg, rgba(150,180,255,.4), rgba(150,180,255,.15));
  box-shadow: 0 4px 10px rgba(0,0,0,.35);
}
.drawer-body { padding: 0 4px; }
.detail-hero {
  position: relative; overflow: hidden;
  border-radius: 14px; padding: 18px 16px; margin-bottom: 12px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.6), 0 10px 24px rgba(0,0,0,.35);
}
.detail-hero::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(115deg, rgba(255,255,255,0) 28%, rgba(255,255,255,.34) 44%, rgba(255,255,255,0) 60%);
}
.hero-spec { font-size: 22px; font-weight: 800; color: #fff; text-shadow: 0 2px 6px rgba(0,0,0,.5); }
.hero-type { font-size: 12px; color: rgba(255,255,255,.9); margin-top: 4px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; margin-bottom: 12px; }
.detail-row {
  display: flex; justify-content: space-between; font-size: 13px;
  padding: 7px 2px; border-bottom: 1px dashed rgba(255, 255, 255, 0.14);
}
.detail-row span { color: var(--ink-dim); }
.spec-row { display: flex; gap: 8px; align-items: center; }
.spec-row .el-input { flex: 1; }
.hint { font-size: 11px; color: var(--ink-dim); margin-top: 7px; line-height: 1.6; }
</style>
