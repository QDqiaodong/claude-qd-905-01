<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import router from '../router'

const route = computed(() => router.currentRoute.value)
const isRank = computed(() => (route.value?.query?.view || '') === 'rank')

const stocks = ref([])
const orders = ref([])
const rank = ref([])
const stockId = ref(null)
const pieces = ref([])
const preview = ref(null)
const layoutNo = ref('')
const cutter = ref('数控切割线 CTL-01')
const operator = ref('张伟')
const submitting = ref(false)

const SVG_W = 560

const currentStock = computed(() => stocks.value.find((s) => s.id === stockId.value) || null)

const svgBox = computed(() => {
  const len = preview.value?.sourceLengthMm || 2440
  const wid = preview.value?.sourceWidthMm || 1830
  const scale = SVG_W / len
  return { w: SVG_W, h: Math.round(wid * scale), scale }
})

function rectOf(r) {
  const s = svgBox.value.scale
  return { x: r.x * s, y: r.y * s, w: r.w * s, h: r.h * s }
}

const utilColor = computed(() => {
  const rate = preview.value?.utilizationRate || 0
  if (rate < 70) return 'linear-gradient(90deg, #ffb74d, #e4573d)'
  if (rate > 95) return 'linear-gradient(90deg, #4dd6a8, #2fb98a)'
  return 'linear-gradient(90deg, #1a237e, #4fc3f7)'
})

function addPiece() {
  pieces.value.push({ lengthMm: 800, widthMm: 600, quantity: 2, remark: '' })
}

function removePiece(idx) {
  pieces.value.splice(idx, 1)
}

let timer = null
function schedulePreview() {
  clearTimeout(timer)
  timer = setTimeout(runPreview, 220)
}

async function runPreview() {
  if (!stockId.value || !pieces.value.length) {
    preview.value = null
    return
  }
  try {
    preview.value = await api.cutting.preview({ stockId: stockId.value, pieces: pieces.value })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function pickStock(id) {
  stockId.value = id
  const stock = stocks.value.find((s) => s.id === id)
  if (stock && !pieces.value.length) {
    pieces.value = [
      { lengthMm: 1200, widthMm: 900, quantity: 2, remark: '' },
      { lengthMm: 800, widthMm: 600, quantity: 3, remark: '' }
    ]
  }
  runPreview()
}

async function submit() {
  if (!stockId.value) return ElMessage.warning('请选择原片批次')
  if (!pieces.value.length) return ElMessage.warning('请添加出片规格')
  submitting.value = true
  try {
    const saved = await api.cutting.create({
      stockId: stockId.value,
      layoutNo: layoutNo.value,
      cutter: cutter.value,
      operator: operator.value,
      pieces: pieces.value
    })
    ElMessage.success(`切割单 ${saved.orderNo} 已生成，利用率 ${saved.utilizationRate}%`)
    await loadOrders()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function loadOrders() {
  orders.value = await api.cutting.list()
}

onMounted(async () => {
  try {
    stocks.value = await api.stock.list()
    rank.value = await api.cutting.remnantRank()
    await loadOrders()
    if (stocks.value.length) pickStock(stocks.value[0].id)
  } catch (e) {
    ElMessage.error(e.message)
  }
})

watch(pieces, schedulePreview, { deep: true })
</script>

<template>
  <div>
    <div class="view-head">
      <div class="view-title">切割与优化 · 布局可视化</div>
      <div class="view-tip">右侧改出片规格即时重算利用率与余料；出片面积超原片面积会拦截</div>
    </div>

    <!-- 余料排行子功能 -->
    <div v-if="isRank" class="glass glass-pad" style="margin-bottom: 14px">
      <div class="section-title">原片余料排行（可二次套裁）</div>
      <el-table :data="rank" size="small" stripe>
        <el-table-column prop="orderNo" label="切割单号" width="180" />
        <el-table-column prop="batchNo" label="原片批次" width="130" />
        <el-table-column prop="glassTypeName" label="玻璃种类" width="110" />
        <el-table-column prop="remnantAreaSqm" label="余料面积㎡" width="110" />
        <el-table-column prop="utilizationRate" label="利用率%" width="100" />
        <el-table-column label="余料占比">
          <template #default="{ row }">
            <div class="rank-bar"><i :style="{ width: Math.min(100, 100 - Number(row.utilizationRate)) + '%' }"></i></div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 原片选择玻璃条 -->
    <div class="glass-bar">
      <span class="glass-bar-label">原片批次</span>
      <el-select v-model="stockId" size="small" style="width: 320px" @change="pickStock">
        <el-option
          v-for="s in stocks" :key="s.id"
          :label="s.batchNo + ' · ' + s.specText + ' · ' + s.sheetCount + '片'"
          :value="s.id"
        />
      </el-select>
      <span class="glass-bar-label" v-if="currentStock">
        {{ currentStock.glassType }} · 单版 {{ (currentStock.lengthMm * currentStock.widthMm / 1000000).toFixed(3) }} ㎡
      </span>
      <span style="margin-left: auto" class="glass-bar-label">
        余料 {{ preview ? preview.remnantAreaSqm : 0 }} ㎡ · 规格 {{ preview ? preview.remnantSpec : '-' }}
      </span>
    </div>

    <div class="grid-2">
      <!-- 左：SVG 切割布局图 -->
      <div class="glass glass-pad">
        <div class="section-title">切割布局图{{ preview ? ' · ' + (preview.layoutNo || preview.batchNo) : '' }}</div>
        <div class="layout-wrap">
          <svg :viewBox="`0 0 ${svgBox.w} ${svgBox.h}`" class="layout-svg">
            <defs>
              <pattern id="remnantHatch" width="9" height="9" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                <rect width="9" height="9" fill="rgba(255,255,255,0.06)"></rect>
                <line x1="0" y1="0" x2="0" y2="9" stroke="rgba(255,183,77,0.55)" stroke-width="2.2"></line>
              </pattern>
              <linearGradient id="sheetGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="rgba(150,200,255,0.30)" />
                <stop offset="50%" stop-color="rgba(255,255,255,0.10)" />
                <stop offset="100%" stop-color="rgba(120,150,255,0.22)" />
              </linearGradient>
              <linearGradient id="pieceGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="rgba(79,195,247,0.62)" />
                <stop offset="100%" stop-color="rgba(26,35,126,0.72)" />
              </linearGradient>
            </defs>

            <!-- 原片矩形 -->
            <rect x="0" y="0" :width="svgBox.w" :height="svgBox.h"
                  fill="url(#sheetGrad)" stroke="rgba(255,255,255,0.55)" stroke-width="1.5" />

            <template v-if="preview">
              <g v-for="(r, i) in preview.layout" :key="i">
                <rect
                  v-if="r.type === 'REMNANT'"
                  :x="rectOf(r).x" :y="rectOf(r).y" :width="rectOf(r).w" :height="rectOf(r).h"
                  fill="url(#remnantHatch)" stroke="rgba(255,183,77,0.6)" stroke-width="1"
                />
                <g v-else>
                  <rect
                    :x="rectOf(r).x" :y="rectOf(r).y" :width="rectOf(r).w" :height="rectOf(r).h"
                    fill="url(#pieceGrad)" stroke="rgba(255,255,255,0.8)" stroke-width="1.2" rx="2"
                  />
                  <rect
                    :x="rectOf(r).x" :y="rectOf(r).y" :width="rectOf(r).w" :height="Math.min(rectOf(r).h, 6)"
                    fill="rgba(255,255,255,0.45)"
                  />
                  <text
                    v-if="rectOf(r).w > 46 && rectOf(r).h > 16"
                    :x="rectOf(r).x + rectOf(r).w / 2" :y="rectOf(r).y + rectOf(r).h / 2 + 4"
                    text-anchor="middle" font-size="11" fill="#ffffff" font-weight="700"
                  >{{ r.label }}</text>
                </g>
              </g>
            </template>
            <text v-else :x="svgBox.w / 2" :y="svgBox.h / 2" text-anchor="middle" fill="#8c98cc" font-size="13">
              请添加出片规格
            </text>
          </svg>
        </div>

        <!-- 利用率条 -->
        <div class="util-block">
          <div class="util-head">
            <span>利用率</span>
            <b :style="{ color: preview && preview.utilizationRate < 70 ? '#ffb74d' : (preview && preview.utilizationRate > 95 ? '#4dd6a8' : '#4fc3f7') }">
              {{ preview ? preview.utilizationRate : 0 }}%
            </b>
            <span class="util-split">出片 {{ preview ? preview.outputAreaSqm : 0 }} ㎡ / 原片 {{ preview ? preview.sourceAreaSqm : 0 }} ㎡</span>
          </div>
          <div class="util-track">
            <i :style="{ width: Math.min(100, preview ? preview.utilizationRate : 0) + '%', background: utilColor }"></i>
            <span class="mark warn"></span>
            <span class="mark good"></span>
          </div>
          <div class="util-legend"><span>0</span><span class="warn-t">70% 警戒</span><span class="good-t">95% 优秀</span><span>100%</span></div>
          <div v-if="preview && preview.overflow" class="util-error">{{ preview.message }}</div>
        </div>
      </div>

      <!-- 右：出片规格清单 -->
      <div class="glass glass-pad">
        <div class="section-title">
          出片规格清单
          <el-button size="small" style="margin-left: auto" @click="addPiece">+ 添加规格</el-button>
        </div>

        <div class="piece-head">
          <span>长 mm</span><span>宽 mm</span><span>数量</span><span>面积㎡</span><span></span>
        </div>
        <div class="piece-list">
          <div v-for="(p, i) in pieces" :key="i" class="piece-row">
            <el-input-number v-model="p.lengthMm" :min="1" :max="6000" :step="10" size="small" controls-position="right" />
            <el-input-number v-model="p.widthMm" :min="1" :max="4000" :step="10" size="small" controls-position="right" />
            <el-input-number v-model="p.quantity" :min="1" :max="200" :step="1" size="small" controls-position="right" />
            <span class="piece-area">
              {{ ((p.lengthMm * p.widthMm * p.quantity) / 1000000).toFixed(4) }}
            </span>
            <el-button size="small" type="danger" plain @click="removePiece(i)">删</el-button>
          </div>
          <div v-if="!pieces.length" class="piece-empty">暂无出片规格，点右上角添加</div>
        </div>

        <el-divider content-position="left">生成切割单</el-divider>
        <div class="form-grid">
          <el-input v-model="layoutNo" size="small" placeholder="布局图号（可留空）" />
          <el-input v-model="cutter" size="small" placeholder="切割机" />
          <el-input v-model="operator" size="small" placeholder="操作员" />
          <el-button size="small" type="primary" :loading="submitting" @click="submit">生成切割单</el-button>
        </div>
        <div class="hint">规则：出片面积合计不得超过原片面积，超出会报错并给出超出面积。</div>
      </div>
    </div>

    <!-- 已有切割单 -->
    <div class="glass glass-pad" style="margin-top: 14px">
      <div class="section-title">切割单台账（利用率由出片面积 / 原片面积派生）</div>
      <el-table :data="orders" size="small" stripe max-height="240">
        <el-table-column prop="orderNo" label="切割单号" width="180" />
        <el-table-column prop="batchNo" label="原片批次" width="120" />
        <el-table-column prop="layoutNo" label="布局图号" width="110" />
        <el-table-column prop="outputCount" label="出片数" width="80" />
        <el-table-column prop="sourceAreaSqm" label="原片㎡" width="90" />
        <el-table-column prop="outputAreaSqm" label="出片㎡" width="90" />
        <el-table-column prop="utilizationRate" label="利用率%" width="100">
          <template #default="{ row }">
            <span :style="{ color: Number(row.utilizationRate) < 70 ? '#ffb74d' : (Number(row.utilizationRate) > 95 ? '#4dd6a8' : '#4fc3f7'), fontWeight: 700 }">
              {{ row.utilizationRate }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remnantSpec" label="余料规格" width="110" />
        <el-table-column prop="cutter" label="切割机" />
        <el-table-column prop="operator" label="操作员" width="80" />
        <el-table-column prop="status" label="状态" width="90" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.layout-wrap {
  border-radius: 12px; padding: 10px;
  background: linear-gradient(150deg, rgba(10, 16, 60, 0.55), rgba(20, 28, 82, 0.35));
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.24);
}
.layout-svg { width: 100%; height: auto; display: block; max-height: 340px; }

.util-block { margin-top: 14px; }
.util-head { display: flex; align-items: baseline; gap: 10px; font-size: 12px; color: var(--ink-soft); }
.util-head b { font-size: 20px; }
.util-split { margin-left: auto; color: var(--ink-dim); }
.util-track {
  position: relative; height: 13px; margin-top: 7px; border-radius: 8px; overflow: hidden;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.35);
}
.util-track i { display: block; height: 100%; transition: width .35s ease; }
.util-track .mark { position: absolute; top: 0; bottom: 0; width: 2px; background: rgba(255, 255, 255, 0.65); }
.util-track .warn { left: 70%; }
.util-track .good { left: 95%; }
.util-legend { display: flex; justify-content: space-between; font-size: 10px; color: var(--ink-dim); margin-top: 4px; }
.util-error {
  margin-top: 8px; padding: 7px 10px; border-radius: 9px; font-size: 12px; color: #ffd7cf;
  background: rgba(228, 87, 61, 0.22); border: 1px solid rgba(228, 87, 61, 0.5);
}

.piece-head, .piece-row {
  display: grid; grid-template-columns: 1fr 1fr 1fr 78px 44px; gap: 7px; align-items: center;
}
.piece-head { font-size: 11px; color: var(--ink-dim); padding: 0 2px 6px; }
.piece-list { max-height: 260px; overflow: auto; }
.piece-row { margin-bottom: 7px; }
.piece-area { font-size: 12px; color: #4fc3f7; font-weight: 700; text-align: center; }
.piece-empty { font-size: 12px; color: var(--ink-dim); padding: 12px 2px; }

.form-grid { display: grid; grid-template-columns: 1fr 1fr 1fr auto; gap: 8px; }
.hint { font-size: 11px; color: var(--ink-dim); margin-top: 8px; line-height: 1.6; }
.rank-bar { height: 7px; border-radius: 5px; background: rgba(255,255,255,0.12); overflow: hidden; }
.rank-bar i { display: block; height: 100%; background: linear-gradient(90deg, #ffb74d, #e4573d); }
</style>
