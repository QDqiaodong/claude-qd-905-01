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

// 出炉登记表单：结果只允许首次确认一次，锁定后只读
const dischargeForm = ref({ outputCount: 6, brokenCount: 0 })

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

// ---------------- 终态 / 只读判据（以后端 FurnaceVO 为准） ----------------
const finished = computed(() => furnace.value?.finished === true)
const resultLocked = computed(() => furnace.value?.resultLocked === true)
const awaitingBackfill = computed(() => furnace.value?.awaitingBackfill === true)
const canAdvance = computed(() => furnace.value?.canAdvance === true)
const canDischarge = computed(() => furnace.value?.canDischarge === true)
const readonlyParams = computed(() => finished.value)
const lockReason = computed(() => furnace.value?.lockReason || '')

const STAGE_NAMES = { HEATING: '加热段', QUENCH: '淬冷段', COOLING: '冷却段', DONE: '已出炉' }
const STAGE_TAG = { HEATING: 'danger', QUENCH: 'primary', COOLING: 'warning', DONE: 'success' }
const GLASS_NAMES = { FLOAT_CLEAR: '浮法白玻', ULTRA_CLEAR: '超白玻', TINTED: '有色玻璃', LOWE_BASE: 'Low-E 基片' }
function stageNameOf(s) {
  return STAGE_NAMES[s] || s || '-'
}
function stageTagOf(s) {
  return STAGE_TAG[s] || 'info'
}
function glassTypeName(t) {
  return GLASS_NAMES[t] || t || '-'
}
function ledgerRowClass({ row }) {
  if (row.id === batchId.value) return 'ledger-current'
  return row.resultLocked ? 'ledger-locked' : row.awaitingBackfill ? 'ledger-backfill' : ''
}

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
    dischargeForm.value.outputCount = furnace.value.outputCount || 6
    dischargeForm.value.brokenCount = furnace.value.brokenCount || 0
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function advance() {
  if (!batchId.value) return
  if (!canAdvance.value) {
    ElMessage.warning(lockReason.value || '该批次已完成，不可再推进')
    return
  }
  try {
    furnace.value = await api.process.advance(batchId.value)
    if (furnace.value.notice) {
      ElMessage.warning(furnace.value.notice)
    } else {
      ElMessage.success('推进到「' + furnace.value.stageName + '」')
    }
    await loadList()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function saveParams() {
  if (!batchId.value) return
  if (readonlyParams.value) {
    ElMessage.warning(lockReason.value || '该批次已完成，工艺参数只读')
    return
  }
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
  if (!canDischarge.value) {
    ElMessage.warning(lockReason.value || '该批次出炉结果已锁定，不可重复出炉')
    return
  }
  const out = dischargeForm.value.outputCount
  const broken = dischargeForm.value.brokenCount || 0
  if (!out || out <= 0) {
    ElMessage.warning('出炉片数必须为正整数')
    return
  }
  if (broken > out) {
    ElMessage.warning('破碎片数不得超过出炉片数')
    return
  }
  try {
    furnace.value = await api.process.discharge(batchId.value, out, broken)
    ElMessage.success(furnace.value.notice || '出炉登记完成')
    await loadList()
  } catch (e) {
    // 409：提交了不同片数，原结果保留 —— 重新拉取，避免表单里还留着想改写的数字
    ElMessage.error(e.message)
    await loadBatch(batchId.value)
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
      <div class="view-tip">加热段 → 淬冷段 → 冷却段 → 完成；温度必须在 620~720℃，破碎片数不得超过出炉片数；批次完成后只读，出炉结果只确认一次</div>
    </div>

    <!-- 批次选择玻璃条 -->
    <div class="glass-bar">
      <span class="glass-bar-label">钢化批次</span>
      <el-select v-model="batchId" size="small" style="width: 300px" @change="loadBatch">
        <el-option v-for="b in batches" :key="b.id"
                   :label="b.batchNo + ' · ' + b.furnaceNo + ' · ' + b.tempRange + ' · ' + stageNameOf(b.stage)"
                   :value="b.id" />
      </el-select>
      <span class="glass-bar-label" v-if="furnace">
        温控区间 {{ furnace.tempRange }}℃ · 当前炉段
        <el-tag size="small" :type="stageTagOf(furnace.stage)" effect="dark" style="margin-left: 4px">
          {{ furnace.stageName }}
        </el-tag>
      </span>
      <span style="margin-left: auto">
        <el-tooltip v-if="!canAdvance" :content="lockReason || '该批次已完成，不可再推进'" placement="top">
          <span><el-button size="small" type="primary" disabled>进炉推进</el-button></span>
        </el-tooltip>
        <el-button v-else size="small" type="primary" @click="advance">进炉推进</el-button>
        <el-tooltip v-if="!canDischarge" :content="lockReason || '出炉结果已锁定，不可重复出炉'" placement="top">
          <span><el-button size="small" disabled>出炉登记</el-button></span>
        </el-tooltip>
        <el-button v-else size="small" :type="awaitingBackfill ? 'warning' : 'default'" @click="discharge">
          {{ awaitingBackfill ? '补录出炉结果' : '出炉登记' }}
        </el-button>
      </span>
    </div>

    <!-- 终态 / 补录提示：明确不可继续操作的原因 -->
    <el-alert v-if="furnace && resultLocked" class="state-alert" type="success" show-icon :closable="false"
              :title="'最终出炉结果已锁定：出炉 ' + furnace.outputCount + ' 片 · 破碎 ' + (furnace.brokenCount || 0)
                + ' 片 · 合格 ' + furnace.goodCount + ' 片 · 破碎率 ' + furnace.brokenRate + '%'"
              :description="lockReason" />
    <el-alert v-else-if="furnace && awaitingBackfill" class="state-alert" type="warning" show-icon :closable="false"
              title="炉程已完成但出炉片数缺失（历史记录）"
              description="该批次是真实完成的批次，不会回到加热阶段重新生产；请在右侧补录一次出炉片数 / 破碎片数，补录后即锁定只读。" />
    <el-alert v-else-if="furnace" class="state-alert" type="info" show-icon :closable="false"
              :title="'批次在炉生产中，当前处于「' + furnace.stageName + '」，出炉结果只能首次确认一次'"
              description="加热 → 淬冷 → 冷却依次推进，完成后再登记出炉结果；刷新页面不会回退炉段。" />

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
            <el-slider :class="{ danger: tempOut }" v-model="params.heatTemp" :min="560" :max="780" :step="1" :disabled="readonlyParams" />
          </div>
          <div class="slider-row" :class="{ bad: secOut }">
            <div class="s-head"><span>加热时长</span>
              <b>{{ params.heatSeconds }} s</b>
              <em v-if="secOut">超出允许区间 90~600s</em>
              <em v-else class="ok">允许 90~600 s</em>
            </div>
            <el-slider :class="{ danger: secOut }" v-model="params.heatSeconds" :min="60" :max="700" :step="10" :disabled="readonlyParams" />
          </div>
          <div class="slider-row" :class="{ bad: qpOut }">
            <div class="s-head"><span>淬冷风压</span>
              <b>{{ params.quenchPressure }} kPa</b>
              <em v-if="qpOut">超出允许区间 1.5~6.5kPa</em>
              <em v-else class="ok">允许 1.5~6.5 kPa</em>
            </div>
            <el-slider :class="{ danger: qpOut }" v-model="params.quenchPressure" :min="0" :max="8" :step="0.1" :disabled="readonlyParams" />
          </div>
          <div class="slider-row">
            <div class="s-head"><span>工艺温控区间</span>
              <b>{{ tempRange.tempLow }} - {{ tempRange.tempHigh }} ℃</b>
              <em class="ok">该区间串由 temp_range 派生列拆合</em>
            </div>
            <div class="range-row">
              <el-input-number v-model="tempRange.tempLow" :min="600" :max="720" size="small" controls-position="right" :disabled="readonlyParams" />
              <span>—</span>
              <el-input-number v-model="tempRange.tempHigh" :min="600" :max="760" size="small" controls-position="right" :disabled="readonlyParams" />
              <el-button size="small" type="primary" :disabled="readonlyParams" @click="saveParams">保存参数</el-button>
              <span v-if="readonlyParams" class="readonly-note">批次已完成，参数只读</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：当炉统计 -->
      <div class="glass glass-pad">
        <div class="section-title">
          当炉统计
          <el-tag v-if="furnace && resultLocked" size="small" type="success" effect="plain" style="margin-left: 8px">最终出炉结果</el-tag>
          <el-tag v-else-if="furnace && awaitingBackfill" size="small" type="warning" effect="plain" style="margin-left: 8px">待补录（仅一次）</el-tag>
          <el-tag v-else-if="furnace" size="small" type="info" effect="plain" style="margin-left: 8px">在炉中</el-tag>
        </div>
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
            <text x="70" y="66" text-anchor="middle" font-size="22" font-weight="800" fill="#ffffff">
              {{ resultLocked ? donut.rate + '%' : '未登记' }}
            </text>
            <text x="70" y="86" text-anchor="middle" font-size="11" fill="#b9c4ee">破碎率</text>
          </svg>
          <div class="stat-cards">
            <div class="mini-card"><div class="k">出炉片数</div><div class="v">{{ furnace && furnace.outputCount ? furnace.outputCount : '-' }}</div></div>
            <div class="mini-card"><div class="k">破碎片数</div><div class="v" style="color:#ffb74d">{{ furnace && furnace.brokenCount != null && furnace.outputCount ? furnace.brokenCount : '-' }}</div></div>
            <div class="mini-card"><div class="k">完好片数</div><div class="v" style="color:#4dd6a8">{{ resultLocked ? furnace.goodCount : '-' }}</div></div>
            <div class="mini-card"><div class="k">颗粒度</div><div class="v">{{ furnace ? furnace.particleCount : '-' }}</div></div>
          </div>
        </div>

        <!-- 出炉结果登记：首次确认一次；已锁定只读；历史缺片数批次补录一次 -->
        <div class="discharge-box" :class="{ locked: resultLocked, backfill: awaitingBackfill }">
          <template v-if="resultLocked">
            <div class="discharge-title">最终出炉结果（只读，不可重复出炉）</div>
            <div class="discharge-readonly">
              出炉 <b>{{ furnace.outputCount }}</b> 片 · 破碎 <b>{{ furnace.brokenCount || 0 }}</b> 片 ·
              合格 <b>{{ furnace.goodCount }}</b> 片 · 破碎率 <b>{{ furnace.brokenRate }}%</b>
            </div>
            <div class="discharge-note">{{ lockReason }}</div>
          </template>
          <template v-else>
            <div class="discharge-title">{{ awaitingBackfill ? '补录出炉结果（历史完成批次，仅此一次）' : '出炉结果登记（首次确认即锁定）' }}</div>
            <div class="discharge-form">
              <span class="df-label">出炉片数</span>
              <el-input-number v-model="dischargeForm.outputCount" :min="1" :max="9999" size="small" controls-position="right" />
              <span class="df-label">破碎片数</span>
              <el-input-number v-model="dischargeForm.brokenCount" :min="0" :max="dischargeForm.outputCount || 9999" size="small" controls-position="right" />
              <el-button size="small" :type="awaitingBackfill ? 'warning' : 'primary'" @click="discharge">
                {{ awaitingBackfill ? '确认补录' : '确认出炉' }}
              </el-button>
            </div>
            <div class="discharge-note" v-if="awaitingBackfill">补录后结果立即锁定，不再接受重复提交；提交与真实结果不符的数字会被拒绝。</div>
          </template>
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

    <!-- 钢化批次台账：当前阶段 / 最终出炉结果 / 不可继续操作的原因 -->
    <div class="glass glass-pad ledger-glass">
      <div class="section-title">
        钢化批次台账
        <span class="ledger-sub">已完成批次只读：点行可切换上方温控隧道查看</span>
      </div>
      <el-table :data="batches" size="small" stripe highlight-current-row
                :row-class-name="ledgerRowClass" @row-click="(row) => loadBatch(row.id)" max-height="300">
        <el-table-column prop="batchNo" label="批次号" width="150" />
        <el-table-column prop="furnaceNo" label="炉号" width="70" />
        <el-table-column label="玻璃" width="90">
          <template #default="{ row }">{{ glassTypeName(row.glassType) }}</template>
        </el-table-column>
        <el-table-column label="当前阶段" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="stageTagOf(row.stage)" effect="dark">{{ stageNameOf(row.stage) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最终出炉结果" min-width="210">
          <template #default="{ row }">
            <template v-if="row.resultLocked">
              出炉 <b>{{ row.outputCount }}</b> · 破碎 <b style="color:#ffb74d">{{ row.brokenCount || 0 }}</b>
              · 合格 <b style="color:#4dd6a8">{{ row.goodCount }}</b> · 破碎率 <b>{{ row.brokenRate }}%</b>
            </template>
            <template v-else-if="row.awaitingBackfill">
              <el-tag size="small" type="warning">片数缺失 · 待补录一次</el-tag>
            </template>
            <template v-else>
              <span class="ledger-dim">在炉中，尚未出炉</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.resultLocked" size="small" type="success">已锁定只读</el-tag>
            <el-tag v-else-if="row.awaitingBackfill" size="small" type="warning">仅可补录一次</el-tag>
            <el-tag v-else size="small" type="info">可推进 / 出炉</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="不可继续操作的原因" min-width="260">
          <template #default="{ row }">
            <span class="lock-reason">{{ row.lockReason || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作员" width="70" />
      </el-table>
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
.state-alert { margin: 12px 0 0; border-radius: 12px; }
.readonly-note { font-size: 11px; color: var(--ink-dim); margin-left: 4px; }

.discharge-box {
  margin-top: 12px; padding: 10px 12px; border-radius: 11px;
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.14);
}
.discharge-box.locked { background: rgba(77, 214, 168, 0.08); border-color: rgba(77, 214, 168, 0.35); }
.discharge-box.backfill { background: rgba(255, 183, 77, 0.08); border-color: rgba(255, 183, 77, 0.4); }
.discharge-title { font-size: 12px; font-weight: 700; color: #fff; margin-bottom: 8px; }
.discharge-readonly { font-size: 13px; color: #e8eeff; }
.discharge-readonly b { color: #fff; font-size: 14px; }
.discharge-form { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.df-label { font-size: 12px; color: var(--ink-soft); }
.discharge-note { font-size: 11px; color: var(--ink-dim); margin-top: 7px; line-height: 1.5; }

.ledger-glass { margin-top: 14px; }
.ledger-sub { font-size: 11px; font-weight: 400; color: var(--ink-dim); margin-left: 10px; }
.ledger-dim { color: var(--ink-dim); font-size: 12px; }
.lock-reason { font-size: 11px; color: #ffd8a8; }
:deep(.ledger-current) { background-color: rgba(26, 35, 126, 0.35) !important; }
:deep(.ledger-locked td) { color: var(--ink-dim); }
:deep(.ledger-backfill) { background-color: rgba(255, 183, 77, 0.08) !important; }

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
