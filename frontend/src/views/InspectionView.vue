<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import router from '../router'

const route = computed(() => router.currentRoute.value)
const rackFirst = computed(() => (route.value?.query?.view || '') === 'rack')

const STAGES = [
  { key: '', label: '全部工序' },
  { key: 'CUTTING', label: '切割' },
  { key: 'GRINDING', label: '磨边' },
  { key: 'TEMPERING', label: '钢化' },
  { key: 'COATING', label: '镀膜' },
  { key: 'INSULATING', label: '中空' },
  { key: 'LAMINATING', label: '夹胶' }
]

const stage = ref('')
const map = ref(null)
const racks = ref([])
const rackStat = ref([])
const hover = ref(null)

const PLATE_W = 480
const plateH = computed(() => Math.round(PLATE_W * (1830 / 2440)))

const typeTotal = computed(() => {
  const stats = map.value?.typeStats || []
  return Math.max(1, stats.reduce((sum, s) => sum + s.count, 0))
})

function pointOf(d) {
  return {
    cx: (Number(d.posX) / 100) * PLATE_W,
    cy: (Number(d.posY) / 100) * plateH.value,
    r: Math.min(20, Math.max(4.5, Number(d.defectSizeMm) * 1.9 + 4))
  }
}

async function load() {
  try {
    map.value = await api.inspection.defectMap(stage.value || undefined)
    racks.value = await api.inspection.racks()
    rackStat.value = await api.inspection.rackStat()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function ship(row) {
  try {
    await api.inspection.ship(row.id)
    ElMessage.success(`成品架 ${row.rackNo} 已发货`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadRack(row) {
  try {
    await api.inspection.loadRack(row.id, {})
    ElMessage.success(`成品架 ${row.rackNo} 已上架`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const dialog = ref(false)
const form = ref({
  inspectNo: '', processType: 'TEMPERING', batchNo: '', orderNo: '',
  defectType: 'SCRATCH', defectSizeMm: 3, posX: 50, posY: 50, verdict: 'REWORK', inspector: '何斌', sheetCount: 1
})

const DEFECTS = [
  { key: 'SCRATCH', label: '划伤' }, { key: 'BUBBLE', label: '气泡' }, { key: 'STONE', label: '结石' },
  { key: 'CHIP', label: '崩边' }, { key: 'WAVE', label: '波形' }, { key: 'STRESS', label: '应力斑' }
]
const VERDICTS = [
  { key: 'PASS', label: '合格' }, { key: 'REWORK', label: '返修' }, { key: 'SCRAP', label: '报废' }
]

function openDialog(x, y) {
  if (x != null) {
    form.value.posX = Math.round((x / PLATE_W) * 1000) / 10
    form.value.posY = Math.round((y / plateH.value) * 1000) / 10
  }
  form.value.inspectNo = 'IR-' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + '-' + String(Math.floor(Math.random() * 900 + 100))
  dialog.value = true
}

async function submit() {
  try {
    await api.inspection.create({ ...form.value })
    ElMessage.success('检验记录已保存')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function onPlateClick(evt) {
  const rect = evt.currentTarget.getBoundingClientRect()
  const x = ((evt.clientX - rect.left) / rect.width) * PLATE_W
  const y = ((evt.clientY - rect.top) / rect.height) * plateH.value
  openDialog(x, y)
}

onMounted(load)

const verdictColor = { PASS: '#4dd6a8', REWORK: '#ffb74d', SCRAP: '#e4573d' }
const shipColor = { PENDING: '#8c98cc', READY: '#4fc3f7', SHIPPED: '#4dd6a8' }
const shipLabel = { PENDING: '待备货', READY: '待发货', SHIPPED: '已发货' }
// 共享质量状态（返修闭环）：隔离 / 处理中 / 待复检 拦截，已放行可继续
const qualityColor = { ISOLATED: '#ff6b6b', PROCESSING: '#ffb74d', PENDING_RECHECK: '#4fc3f7', RELEASED: '#4dd6a8' }
const qualityLabel = { ISOLATED: '返修隔离', PROCESSING: '返修处理中', PENDING_RECHECK: '待复检', RELEASED: '复检已放行' }
</script>

<template>
  <div>
    <div class="view-head">
      <div class="view-title">检验与成品架 · 缺陷分布</div>
      <div class="view-tip">玻璃板平面上按坐标打点：点大小按缺陷尺寸、颜色按缺陷类型；点击空白处可登记新缺陷</div>
    </div>

    <!-- 工序筛选玻璃条 -->
    <div class="glass-bar">
      <span class="glass-bar-label">工序环节</span>
      <span
        v-for="s in STAGES" :key="s.key" class="chip"
        :class="{ on: stage === s.key }" @click="stage = s.key; load()"
      >{{ s.label }}</span>
      <span style="margin-left: auto" class="glass-bar-label">
        共 {{ map ? map.total : 0 }} 条 · 合格 {{ map ? map.passCount : 0 }} / 返修 {{ map ? map.reworkCount : 0 }} / 报废 {{ map ? map.scrapCount : 0 }}
      </span>
      <el-button size="small" type="primary" @click="openDialog(null, null)">+ 检验登记</el-button>
    </div>

    <!-- 缺陷分布图 -->
    <div class="grid-2-wide" :class="{ reverse: rackFirst }">
      <div class="glass glass-pad">
        <div class="section-title">玻璃板缺陷分布图（2440 × 1830 mm）</div>
        <div class="plate-wrap">
          <svg :viewBox="`0 0 ${PLATE_W} ${plateH}`" class="plate" @click="onPlateClick">
            <defs>
              <linearGradient id="plateGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="rgba(160,205,255,0.34)" />
                <stop offset="45%" stop-color="rgba(255,255,255,0.12)" />
                <stop offset="100%" stop-color="rgba(110,150,255,0.26)" />
              </linearGradient>
            </defs>
            <rect x="0" y="0" :width="PLATE_W" :height="plateH" fill="url(#plateGrad)"
                  stroke="rgba(255,255,255,0.6)" stroke-width="1.6" rx="4" />
            <!-- 玻璃质感高光斜条 -->
            <polygon :points="`0,${plateH} ${PLATE_W * 0.42},0 ${PLATE_W * 0.62},0 ${PLATE_W * 0.2},${plateH}`"
                     fill="rgba(255,255,255,0.06)" />
            <g v-for="d in (map ? map.defects : [])" :key="d.id">
              <circle
                :cx="pointOf(d).cx" :cy="pointOf(d).cy" :r="pointOf(d).r + 3"
                :fill="d.color" fill-opacity="0.18"
              />
              <circle
                :cx="pointOf(d).cx" :cy="pointOf(d).cy" :r="pointOf(d).r"
                :fill="d.color" fill-opacity="0.92"
                stroke="rgba(255,255,255,0.9)" stroke-width="1.2"
                @mouseenter="hover = d" @mouseleave="hover = null"
              />
              <circle
                :cx="pointOf(d).cx - pointOf(d).r * 0.3" :cy="pointOf(d).cy - pointOf(d).r * 0.34"
                :r="Math.max(1.2, pointOf(d).r * 0.22)" fill="rgba(255,255,255,0.85)"
              />
            </g>
            <text x="8" y="16" font-size="11" fill="#8c98cc">0,0</text>
            <text :x="PLATE_W - 8" :y="plateH - 8" font-size="11" fill="#8c98cc" text-anchor="end">2440,1830</text>
          </svg>

          <div v-if="hover" class="tip-card">
            <div class="t-head" :style="{ background: hover.color }">{{ hover.defectTypeName }} · {{ hover.verdictName }}</div>
            <div class="t-row"><span>检验单</span><b>{{ hover.inspectNo }}</b></div>
            <div class="t-row"><span>工序</span><b>{{ hover.processTypeName }}</b></div>
            <div class="t-row"><span>缺陷尺寸</span><b>{{ hover.defectSizeMm }} mm</b></div>
            <div class="t-row"><span>坐标</span><b>{{ hover.posX }}% , {{ hover.posY }}%</b></div>
            <div class="t-row"><span>涉及片数</span><b>{{ hover.sheetCount }}</b></div>
            <div class="t-row"><span>检验员</span><b>{{ hover.inspector }}</b></div>
          </div>
        </div>
      </div>

      <!-- 缺陷类型占比条 -->
      <div class="glass glass-pad">
        <div class="section-title">缺陷类型占比</div>
        <div class="legend-row">
          <span v-for="s in (map ? map.typeStats : [])" :key="s.defectType" class="legend">
            <i :style="{ background: s.color }"></i>{{ s.defectTypeName }}
          </span>
        </div>
        <div class="ratio-list">
          <div v-for="s in (map ? map.typeStats : [])" :key="s.defectType" class="ratio-item">
            <div class="r-head">
              <span>{{ s.defectTypeName }}</span>
              <b>{{ s.count }} 次 · {{ Math.round((s.count / typeTotal) * 1000) / 10 }}%</b>
            </div>
            <div class="r-bar">
              <i :style="{ width: (s.count / typeTotal) * 100 + '%', background: s.color }"></i>
            </div>
            <div class="r-sub">平均缺陷尺寸 {{ s.avgSizeMm }} mm · 涉及 {{ s.sheetCount }} 片</div>
          </div>
          <div v-if="!(map && map.typeStats.length)" class="empty">暂无缺陷数据</div>
        </div>

        <el-divider content-position="left">判定分布</el-divider>
        <div class="verdict-row">
          <div class="v-item"><span class="dot" style="background:#4dd6a8"></span>合格<b>{{ map ? map.passCount : 0 }}</b></div>
          <div class="v-item"><span class="dot" style="background:#ffb74d"></span>返修<b>{{ map ? map.reworkCount : 0 }}</b></div>
          <div class="v-item"><span class="dot" style="background:#e4573d"></span>报废<b>{{ map ? map.scrapCount : 0 }}</b></div>
        </div>
      </div>
    </div>

    <!-- 成品架 -->
    <div class="glass glass-pad" style="margin-top: 14px">
      <div class="section-title">
        成品架 · 上架 / 发运读取返修闭环状态，未闭环批次自动拦截
        <span class="rack-stat-inline">
          <span v-for="s in rackStat" :key="s.shipStatus">
            {{ s.shipStatusName }} {{ s.rackCount }} 架 / {{ s.areaSqm }} ㎡
          </span>
        </span>
      </div>
      <el-table :data="racks" size="small" stripe max-height="300">
        <el-table-column prop="rackNo" label="架号" width="70" />
        <el-table-column prop="orderNo" label="订单号" width="120" />
        <el-table-column label="批次号" width="100">
          <template #default="{ row }">{{ row.batchNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="glassType" label="玻璃种类" width="96" />
        <el-table-column label="规格" width="140">
          <template #default="{ row }">{{ row.specText }}</template>
        </el-table-column>
        <el-table-column prop="sheetCount" label="片数" width="64" />
        <el-table-column label="面积㎡" width="80">
          <template #default="{ row }"><b style="color:#4fc3f7">{{ row.areaSqm }}</b></template>
        </el-table-column>
        <el-table-column label="承重" width="140">
          <template #default="{ row }">
            <div class="load-bar" :class="{ over: row.loadRate > 100 }">
              <i :style="{ width: Math.min(100, row.loadRate) + '%' }"></i>
            </div>
            <span class="load-text">{{ row.currentLoadKg }} / {{ row.maxLoadKg }} kg</span>
          </template>
        </el-table-column>
        <el-table-column label="质量状态" width="200">
          <template #default="{ row }">
            <template v-if="row.qualityStatus">
              <span :style="{ color: qualityColor[row.qualityStatus], fontWeight: 700 }">{{ qualityLabel[row.qualityStatus] }}</span>
              <div v-if="row.shipBlocked" class="block-reason">{{ row.blockReason }}</div>
            </template>
            <span v-else style="color:#8c98cc">无返修</span>
          </template>
        </el-table-column>
        <el-table-column label="发货状态" width="90">
          <template #default="{ row }">
            <span :style="{ color: shipColor[row.shipStatus], fontWeight: 700 }">{{ shipLabel[row.shipStatus] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" :disabled="row.shipBlocked" @click="loadRack(row)">上架</el-button>
            <el-button size="small" type="primary" :disabled="row.shipBlocked" @click="ship(row)">发货</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="检验登记" width="520px">
      <el-form label-width="100px" size="small">
        <el-form-item label="检验单号"><el-input v-model="form.inspectNo" /></el-form-item>
        <el-form-item label="工序环节">
          <el-select v-model="form.processType" style="width: 100%">
            <el-option v-for="s in STAGES.slice(1)" :key="s.key" :label="s.label" :value="s.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="缺陷类型">
          <el-select v-model="form.defectType" style="width: 100%">
            <el-option v-for="d in DEFECTS" :key="d.key" :label="d.label" :value="d.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="缺陷尺寸mm"><el-input-number v-model="form.defectSizeMm" :min="0.1" :max="100" :step="0.1" /></el-form-item>
        <el-form-item label="缺陷坐标">
          <div class="pos-row">
            <el-input-number v-model="form.posX" :min="0" :max="100" :step="0.5" /> %
            <el-input-number v-model="form.posY" :min="0" :max="100" :step="0.5" /> %
          </div>
        </el-form-item>
        <el-form-item label="判定">
          <el-select v-model="form.verdict" style="width: 100%">
            <el-option v-for="v in VERDICTS" :key="v.key" :label="v.label" :value="v.key" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.verdict === 'REWORK'" label=" ">
          <span style="font-size: 11px; color: #ffb74d">判返修将自动生成 / 并入返修隔离批次，进入「隔离 → 处置 → 复检 → 放行」闭环，未放行前成品架发运会被拦截</span>
        </el-form-item>
        <el-form-item label="批次/订单">
          <el-input v-model="form.batchNo" placeholder="原片批次" style="width: 48%" />
          <el-input v-model="form.orderNo" placeholder="关联单号" style="width: 48%; margin-left: 4%" />
        </el-form-item>
        <el-form-item label="涉及片数"><el-input-number v-model="form.sheetCount" :min="1" :max="999" /></el-form-item>
        <el-form-item label="检验员"><el-input v-model="form.inspector" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.plate-wrap { position: relative; }
.plate {
  width: 100%; height: auto; display: block; cursor: crosshair;
  border-radius: 10px;
  background: linear-gradient(150deg, rgba(10, 16, 60, 0.55), rgba(20, 28, 82, 0.3));
}
.tip-card {
  position: absolute; right: 10px; top: 10px; width: 200px;
  border-radius: 11px; overflow: hidden;
  background: rgba(12, 18, 62, 0.94); border: 1px solid rgba(255, 255, 255, 0.26);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.5);
}
.t-head { padding: 6px 10px; font-size: 12px; font-weight: 800; color: #fff; }
.t-row { display: flex; justify-content: space-between; font-size: 11px; padding: 4px 10px; color: var(--ink-soft); }
.t-row b { color: #fff; }

.legend-row { display: flex; flex-wrap: wrap; gap: 9px; margin-bottom: 10px; }
.legend { display: inline-flex; align-items: center; gap: 5px; font-size: 11px; color: var(--ink-soft); }
.legend i { width: 9px; height: 9px; border-radius: 3px; display: inline-block; }

.ratio-item { margin-bottom: 11px; }
.r-head { display: flex; justify-content: space-between; font-size: 12px; color: var(--ink-soft); }
.r-head b { color: #fff; }
.r-bar { height: 10px; border-radius: 6px; background: rgba(255, 255, 255, 0.12); overflow: hidden; margin: 4px 0 3px; box-shadow: inset 0 1px 3px rgba(0,0,0,.35); }
.r-bar i { display: block; height: 100%; border-radius: 6px; transition: width .35s ease; }
.r-sub { font-size: 10px; color: var(--ink-dim); }

.verdict-row { display: flex; gap: 14px; }
.v-item { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: var(--ink-soft); }
.v-item .dot { width: 9px; height: 9px; border-radius: 50%; }
.v-item b { color: #fff; font-size: 14px; }

.empty { font-size: 12px; color: var(--ink-dim); padding: 14px 0; }
.load-text { font-size: 10px; color: var(--ink-dim); }
.block-reason { font-size: 10px; color: #ff9d8a; line-height: 1.5; margin-top: 2px; }
.rack-stat-inline { margin-left: auto; display: flex; gap: 12px; font-size: 11px; color: var(--ink-dim); font-weight: 400; }
.pos-row { display: flex; align-items: center; gap: 6px; }
.grid-2-wide.reverse { display: flex; flex-direction: column; }
</style>
