<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import router from '../router'

const route = computed(() => router.currentRoute.value)
const isGrinding = computed(() => (route.value?.query?.view || '') === 'grinding')

const batches = ref([])
const furnace = ref(null)
const furnaceStat = ref([])
const grinding = ref([])
const grindingStat = ref([])
const batchId = ref(null)

const params = ref({ heatTemp: 685, heatSeconds: 240, quenchPressure: 3.2 })
const tempRange = ref({ tempLow: 660, tempHigh: 700 })

const TEMP_MIN = 620
const TEMP_MAX = 720
const SEC_MIN = 90
const SEC_MAX = 600
const QP_MIN = 1.5
const QP_MAX = 6.5

const tempOut = computed(() => params.value.heatTemp < TEMP_MIN || params.value.heatTemp > TEMP_MAX)
const secOut = computed(() => params.value.heatSeconds < SEC_MIN || params.value.heatSeconds > SEC_MAX)
const qpOut = computed(() => params.value.quenchPressure < QP_MIN || params.value.quenchPressure > QP_MAX)

const STAGE_POS = { HEATING: 8, QUENCH: 42, COOLING: 74, DONE: 95 }

const stagePos = computed(() => STAGE_POS[furnace.value?.stage] ?? 8)

const donut = computed(() => {
  const rate = furnace.value?.brokenRate || 0
  const c = 2 * Math.PI * 52
  return { dash: `${(rate / 100) * c} ${c}`, rate }
})

async function loadBatch(id) {
  batchId.value = id
  try {
    furnace.value = await api.process.furnace(id)
    params.value.heatTemp = furnace.value.heatTemp || 685
    params.value.heatSeconds = furnace.value.heatSeconds || 240
    params.value.quenchPressure = Number(furnace.value.quenchPressure || 3.2)
    tempRange.value.tempLow = furnace.value.tempLow || 660
    tempRange.value.tempHigh = furnace.value.tempHigh || 700
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function advance() {
  if (!batchId.value) return
  try {
    furnace.value = await api.process.advance(batchId.value)
    ElMessage.success('推进到「' + furnace.value.stageName + '」')
    await loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function saveParams() {
  if (!batchId.value) return
  try {
    await api.process.updateTempering(batchId.value, {
      heatTemp: params.value.heatTemp,
      heatSeconds: params.value.heatSeconds,
      quenchPressure: params.value.quenchPressure,
      tempLow: tempRange.value.tempLow,
      tempHigh: tempRange.value.tempHigh
    })
    ElMessage.success('工艺参数已保存')
    await loadList()
    await loadBatch(batchId.value)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadList() {
  batches.value = await api.process.temperingList()
}

async function discharge() {
  if (!batchId.value) return
  const out = furnace.value?.outputCount || 6
  const broken = furnace.value?.brokenCount || 0
  try {
    furnace.value = await api.process.discharge(batchId.value, out, broken)
    ElMessage.success('出炉登记完成')
    await loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- 磨边 ----------------
const gDialog = ref(false)
const gForm = ref({ recordNo: '', machine: '双边磨边机 MB-01', edgeType: 'FLAT', wheelModel: 'DZ-120#', feedSpeed: 4.5, pieceCount: 8, chipCount: 0, chipNote: '', operator: '张伟' })
const EDGE_TYPES = [
  { key: 'FLAT', label: '直边', range: '1.0~8.0' },
  { key: 'ROUND', label: '圆边', range: '0.8~6.0' },
  { key: 'BEVEL', label: '斜边', range: '0.6~4.5' },
  { key: 'DUCK', label: '鸭嘴边', range: '0.5~3.5' }
]

async function submitGrinding() {
  try {
    await api.process.createGrinding(gForm.value)
    ElMessage.success('磨边记录已保存')
    gDialog.value = false
    grinding.value = await api.process.grindingList()
    grindingStat.value = await api.process.grindingStat()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  try {
    await loadList()
    furnaceStat.value = await api.process.furnaceStat()
    grinding.value = await api.process.grindingList()
    grindingStat.value = await api.process.grindingStat()
    if (batches.value.length) await loadBatch(batches.value[0].id)
  } catch (e) {
    ElMessage.error(e.message)
  }
})
</script>

<template>
  <div>
    <div class="view-head">
      <div class="view-title">磨边与钢化 · 钢化炉温控隧道</div>
      <div class="view-tip">加热段 → 淬冷段 → 冷却段；温度必须在 620~720℃，破碎片数不得超过出炉片数</div>
    </div>

    <!-- 批次选择玻璃条 -->
    <div class="glass-bar">
      <span class="glass-bar-label">钢化批次</span>
      <el-select v-model="batchId" size="small" style="width: 300px" @change="loadBatch">
        <el-option v-for="b in batches" :key="b.id" :label="b.batchNo + ' · ' + b.furnaceNo + ' · ' + b.tempRange" :value="b.id" />
      </el-select>
      <span class="glass-bar-label" v-if="furnace">
        温控区间 {{ furnace.tempRange }}℃ · 当前炉段 {{ furnace.stageName }}
      </span>
      <span style="margin-left: auto">
        <el-button size="small" type="primary" @click="advance">进炉推进</el-button>
        <el-button size="small" @click="discharge">出炉登记</el-button>
      </span>
    </div>

    <div class="grid-2">
      <!-- 左：隧道 -->
      <div class="glass glass-pad">
        <div class="section-title">炉体分段 · 玻璃片运行轨迹</div>

        <div class="tunnel">
          <div class="tunnel-track">
            <div class="seg heating"><span class="seg-name">加热段</span><span class="seg-val">{{ params.heatTemp }}℃ / {{ params.heatSeconds }}s</span></div>
            <div class="seg junction"></div>
            <div class="seg quench"><span class="seg-name">淬冷段</span><span class="seg-val">风压 {{ params.quenchPressure }} kPa</span></div>
            <div class="seg junction"></div>
            <div class="seg cooling"><span class="seg-name">冷却段</span><span class="seg-val">波形度 {{ furnace ? furnace.warpage : '-' }} mm/m</span></div>
          </div>

          <!-- 炉内玻璃片 -->
          <div class="sheet-runner" :style="{ left: stagePos + '%' }">
            <div class="glass-sheet"></div>
            <div class="glass-sheet"></div>
            <div class="glass-sheet"></div>
          </div>
        </div>

        <!-- 三个参数滑块 -->
        <div class="slider-block">
          <div class="slider-row" :class="{ bad: tempOut }">
            <div class="s-head"><span>加热温度</span>
              <b>{{ params.heatTemp }} ℃</b>
              <em v-if="tempOut">超出允许区间 620~720℃</em>
              <em v-else class="ok">允许 620~720 ℃</em>
            </div>
            <el-slider :class="{ danger: tempOut }" v-model="params.heatTemp" :min="560" :max="780" :step="1" />
          </div>
          <div class="slider-row" :class="{ bad: secOut }">
            <div class="s-head"><span>加热时长</span>
              <b>{{ params.heatSeconds }} s</b>
              <em v-if="secOut">超出允许区间 90~600s</em>
              <em v-else class="ok">允许 90~600 s</em>
            </div>
            <el-slider :class="{ danger: secOut }" v-model="params.heatSeconds" :min="60" :max="700" :step="10" />
          </div>
          <div class="slider-row" :class="{ bad: qpOut }">
            <div class="s-head"><span>淬冷风压</span>
              <b>{{ params.quenchPressure }} kPa</b>
              <em v-if="qpOut">超出允许区间 1.5~6.5kPa</em>
              <em v-else class="ok">允许 1.5~6.5 kPa</em>
            </div>
            <el-slider :class="{ danger: qpOut }" v-model="params.quenchPressure" :min="0" :max="8" :step="0.1" />
          </div>
          <div class="slider-row">
            <div class="s-head"><span>工艺温控区间</span>
              <b>{{ tempRange.tempLow }} - {{ tempRange.tempHigh }} ℃</b>
              <em class="ok">该区间串由 temp_range 派生列拆合</em>
            </div>
            <div class="range-row">
              <el-input-number v-model="tempRange.tempLow" :min="600" :max="720" size="small" controls-position="right" />
              <span>—</span>
              <el-input-number v-model="tempRange.tempHigh" :min="600" :max="760" size="small" controls-position="right" />
              <el-button size="small" type="primary" @click="saveParams">保存参数</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：当炉统计 -->
      <div class="glass glass-pad">
        <div class="section-title">当炉统计</div>
        <div class="stat-wrap">
          <svg viewBox="0 0 140 140" class="donut">
            <circle cx="70" cy="70" r="52" fill="none" stroke="rgba(255,255,255,0.12)" stroke-width="14" />
            <circle cx="70" cy="70" r="52" fill="none" stroke="url(#donutGrad)" stroke-width="14"
                    stroke-linecap="round" :stroke-dasharray="donut.dash" transform="rotate(-90 70 70)" />
            <defs>
              <linearGradient id="donutGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#ffb74d" />
                <stop offset="100%" stop-color="#e4573d" />
              </linearGradient>
            </defs>
            <text x="70" y="66" text-anchor="middle" font-size="24" font-weight="800" fill="#ffffff">{{ donut.rate }}%</text>
            <text x="70" y="86" text-anchor="middle" font-size="11" fill="#b9c4ee">破碎率</text>
          </svg>
          <div class="stat-cards">
            <div class="mini-card"><div class="k">出炉片数</div><div class="v">{{ furnace ? furnace.outputCount : 0 }}</div></div>
            <div class="mini-card"><div class="k">破碎片数</div><div class="v" style="color:#ffb74d">{{ furnace ? furnace.brokenCount : 0 }}</div></div>
            <div class="mini-card"><div class="k">完好片数</div><div class="v" style="color:#4dd6a8">{{ furnace ? furnace.goodCount : 0 }}</div></div>
            <div class="mini-card"><div class="k">颗粒度</div><div class="v">{{ furnace ? furnace.particleCount : '-' }}</div></div>
          </div>
        </div>

        <el-divider content-position="left">按炉号统计</el-divider>
        <el-table :data="furnaceStat" size="small" stripe max-height="200">
          <el-table-column prop="furnaceNo" label="炉号" width="80" />
          <el-table-column prop="batchCount" label="炉次" width="70" />
          <el-table-column prop="outputCount" label="出炉" width="70" />
          <el-table-column prop="brokenCount" label="破碎" width="70" />
          <el-table-column label="破碎率" width="90">
            <template #default="{ row }">{{ row.brokenRate }}%</template>
          </el-table-column>
          <el-table-column prop="avgHeatTemp" label="均温℃" />
        </el-table>
      </div>
    </div>

    <!-- 磨边记录子功能 -->
    <div v-if="isGrinding" class="glass glass-pad" style="margin-top: 14px">
      <div class="section-title">
        磨边记录 · 边型进给校验
        <el-button size="small" style="margin-left: auto" @click="gDialog = true">+ 新建磨边记录</el-button>
      </div>
      <div class="edge-stat">
        <div v-for="s in grindingStat" :key="s.edgeType" class="edge-item">
          <div class="e-name">{{ s.edgeTypeName }}</div>
          <div class="e-bar"><i :style="{ width: Math.min(100, s.chipRate * 12) + '%' }"></i></div>
          <div class="e-val">加工 {{ s.pieceCount }} 片 · 崩边 {{ s.chipCount }} 片</div>
        </div>
      </div>
      <el-table :data="grinding" size="small" stripe max-height="240">
        <el-table-column prop="recordNo" label="记录单号" width="170" />
        <el-table-column prop="batchNo" label="原片批次" width="120" />
        <el-table-column prop="machine" label="磨边机" width="150" />
        <el-table-column prop="edgeType" label="边型" width="80" />
        <el-table-column prop="wheelModel" label="磨轮型号" width="100" />
        <el-table-column prop="feedSpeed" label="进给m/min" width="100" />
        <el-table-column prop="pieceCount" label="加工片数" width="90" />
        <el-table-column prop="chipCount" label="崩边片数" width="90" />
        <el-table-column label="崩边率" width="80">
          <template #default="{ row }">{{ row.chipRate }}%</template>
        </el-table-column>
        <el-table-column prop="chipNote" label="崩边记录" />
        <el-table-column prop="operator" label="操作员" width="80" />
      </el-table>
    </div>

    <el-dialog v-model="gDialog" title="新建磨边记录" width="520px">
      <el-form label-width="96px" size="small">
        <el-form-item label="记录单号"><el-input v-model="gForm.recordNo" placeholder="如 EG-20240601-009" /></el-form-item>
        <el-form-item label="磨边机"><el-input v-model="gForm.machine" /></el-form-item>
        <el-form-item label="边型">
          <el-select v-model="gForm.edgeType" style="width: 100%">
            <el-option v-for="e in EDGE_TYPES" :key="e.key" :label="e.label + '（进给 ' + e.range + '）'" :value="e.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="磨轮型号"><el-input v-model="gForm.wheelModel" /></el-form-item>
        <el-form-item label="进给速度"><el-input-number v-model="gForm.feedSpeed" :min="0" :max="10" :step="0.1" /></el-form-item>
        <el-form-item label="加工片数"><el-input-number v-model="gForm.pieceCount" :min="1" :max="500" /></el-form-item>
        <el-form-item label="崩边片数"><el-input-number v-model="gForm.chipCount" :min="0" :max="500" /></el-form-item>
        <el-form-item label="崩边记录"><el-input v-model="gForm.chipNote" /></el-form-item>
        <el-form-item label="操作员"><el-input v-model="gForm.operator" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="gDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitGrinding">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tunnel { position: relative; padding: 30px 0 20px; }
.tunnel-track {
  position: relative; display: flex; height: 78px; border-radius: 12px; overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.28);
  box-shadow: inset 0 2px 12px rgba(0, 0, 0, 0.45), 0 8px 20px rgba(4, 8, 40, 0.45);
}
.seg { flex: 1 1 0; position: relative; display: flex; flex-direction: column; justify-content: center; align-items: center; }
.seg.heating { background: linear-gradient(180deg, #ff8a50, #e4573d 55%, #8e2b1c); }
.seg.quench { background: linear-gradient(180deg, #4fc3f7, #1976d2 60%, #123a63); }
.seg.cooling { background: linear-gradient(180deg, #90a4ff, #3949ab 60%, #1a237e); }
.seg.junction { flex: 0 0 34px; }
.seg.junction:first-of-type { background: linear-gradient(90deg, #8e2b1c, #1976d2); }
.seg.junction:last-of-type { background: linear-gradient(90deg, #123a63, #3949ab); }
.seg-name { font-size: 13px; font-weight: 800; color: #fff; text-shadow: 0 1px 4px rgba(0,0,0,.6); }
.seg-val { font-size: 11px; color: rgba(255,255,255,.92); margin-top: 3px; }

.sheet-runner {
  position: absolute; top: 34px; display: flex; gap: 7px;
  transition: left 1.15s cubic-bezier(.4, .1, .2, 1);
  transform: translateX(-50%);
}
.glass-sheet {
  width: 34px; height: 58px; border-radius: 4px;
  background: linear-gradient(140deg, rgba(255,255,255,0.72), rgba(140,190,255,0.34) 55%, rgba(255,255,255,0.14));
  border: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow: 0 4px 12px rgba(0,0,0,.45), inset 0 1px 0 rgba(255,255,255,.9);
  position: relative; overflow: hidden;
}
.glass-sheet::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(115deg, rgba(255,255,255,0) 34%, rgba(255,255,255,.75) 50%, rgba(255,255,255,0) 64%);
}

.slider-block { margin-top: 16px; }
.slider-row { padding: 9px 12px; border-radius: 11px; margin-bottom: 9px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.12); }
.slider-row.bad { background: rgba(228, 87, 61, 0.14); border-color: rgba(228, 87, 61, 0.45); }
.s-head { display: flex; align-items: baseline; gap: 9px; font-size: 12px; color: var(--ink-soft); margin-bottom: 3px; }
.s-head b { font-size: 15px; color: #fff; }
.s-head em { font-style: normal; font-size: 11px; color: #ffb74d; margin-left: auto; }
.s-head em.ok { color: var(--ink-dim); }
.range-row { display: flex; align-items: center; gap: 8px; }

.stat-wrap { display: flex; gap: 14px; align-items: center; }
.donut { width: 140px; height: 140px; flex: 0 0 auto; }
.stat-cards { flex: 1 1 auto; display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }

.edge-stat { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 12px; }
.edge-item { padding: 8px 10px; border-radius: 10px; background: rgba(255,255,255,0.07); border: 1px solid rgba(255,255,255,0.14); }
.e-name { font-size: 12px; font-weight: 700; color: #fff; }
.e-bar { height: 6px; border-radius: 4px; background: rgba(255,255,255,0.14); overflow: hidden; margin: 6px 0 4px; }
.e-bar i { display: block; height: 100%; background: linear-gradient(90deg, #ffb74d, #e4573d); }
.e-val { font-size: 10px; color: var(--ink-dim); }
</style>
