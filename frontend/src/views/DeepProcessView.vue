<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import router from '../router'

const route = computed(() => router.currentRoute.value)
const processType = computed(() => (route.value?.query?.type || 'COATING'))

const TYPES = [
  { key: 'COATING', label: '镀膜' },
  { key: 'INSULATING', label: '中空' },
  { key: 'LAMINATING', label: '夹胶' }
]

const records = ref([])
const stat = ref([])
const selectedId = ref(null)
const dialog = ref(false)

const form = ref({
  recordNo: '', processType: 'COATING', batchNo: '', glassType: 'LOWE_BASE', thicknessMm: 6, pieceCount: 40,
  filmSystem: '单银 Low-E', targetMaterial: '银 Ag', filmThicknessNm: 120, emissivity: 0.15,
  spacer: '铝条 12A', gasType: 'ARGON', dewPoint: -52,
  pvbThickness: 0.38, autoclaveTemp: 135, autoclavePressure: 1.2, autoclaveMinutes: 90,
  operator: '周涛'
})

const current = computed(() => records.value.find((r) => r.id === selectedId.value) || records.value[0] || null)

const bands = computed(() => {
  const r = current.value
  if (!r) return []
  const glass = '#4fa8d8'
  if (processType.value === 'COATING') {
    return [
      { label: '玻璃基片', value: `${r.thicknessMm}mm · ${r.glassTypeName || r.glassType}`, color: glass, h: 54 },
      { label: '膜层', value: `${r.filmThicknessNm}nm · ${r.filmSystem}`, color: '#d4a72c', h: 22 },
      { label: '靶材 / 辐射率', value: `${r.targetMaterial} · ε=${r.emissivity}`, color: '#7e57c2', h: 34 }
    ]
  }
  if (processType.value === 'INSULATING') {
    const ok = Number(r.dewPoint) <= -40
    return [
      { label: '外层玻璃', value: `${r.thicknessMm}mm · ${r.glassTypeName || r.glassType}`, color: glass, h: 48 },
      { label: '间隔条 + 气体', value: `${r.spacer} · ${r.gasType === 'ARGON' ? '氩气' : (r.gasType === 'KRYPTON' ? '氪气' : '空气')}`, color: '#5ec8b8', h: 34 },
      { label: '露点', value: `${r.dewPoint}℃ ${ok ? '合格' : '不合格（高于 -40℃）'}`, color: ok ? '#4dd6a8' : '#e4573d', h: 30 },
      { label: '内层玻璃', value: `${r.thicknessMm}mm`, color: glass, h: 48 }
    ]
  }
  return [
    { label: '玻璃', value: `${r.thicknessMm}mm · ${r.glassTypeName || r.glassType}`, color: glass, h: 52 },
    { label: 'PVB 胶片', value: `${r.pvbThickness}mm`, color: '#ffb74d', h: 26 },
    { label: '玻璃', value: `${r.thicknessMm}mm`, color: glass, h: 52 },
    { label: '高压釜', value: `${r.autoclaveTemp}℃ / ${r.autoclavePressure}MPa / ${r.autoclaveMinutes}min`, color: '#7e57c2', h: 34 }
  ]
})

const rules = computed(() => ({
  COATING: '膜层厚度 20~500nm；辐射率 0.020~0.900',
  INSULATING: '露点高于 -40℃ 判不合格',
  LAMINATING: 'PVB 厚度须与玻璃厚度匹配；高压釜 120~150℃ / 1.0~1.4MPa'
}[processType.value]))

async function load() {
  try {
    records.value = await api.deep.listByType(processType.value)
    selectedId.value = records.value.length ? records.value[0].id : null
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openDialog() {
  form.value.processType = processType.value
  form.value.recordNo = 'DP-' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + '-' + String(Math.floor(Math.random() * 900 + 100))
  dialog.value = true
}

async function submit() {
  try {
    await api.deep.create({ ...form.value })
    ElMessage.success('工序记录已保存')
    dialog.value = false
    await load()
    stat.value = await api.deep.stat()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await load()
  try { stat.value = await api.deep.stat() } catch (e) { /* ignore */ }
})

watch(processType, load)
</script>

<template>
  <div>
    <div class="view-head">
      <div class="view-title">深加工工序 · 玻璃剖面与参数</div>
      <div class="view-tip">{{ rules }}</div>
    </div>

    <!-- 工序切换玻璃条 -->
    <div class="glass-bar">
      <span class="glass-bar-label">工序类型</span>
      <span
        v-for="t in TYPES" :key="t.key" class="chip"
        :class="{ on: processType === t.key }"
        @click="router.push({ path: '/deep', query: { type: t.key } })"
      >{{ t.label }}</span>
      <span style="margin-left: auto">
        <el-button size="small" type="primary" @click="openDialog">+ 投产登记</el-button>
      </span>
    </div>

    <div class="grid-2">
      <!-- 左：玻璃剖面 -->
      <div class="glass glass-pad">
        <div class="section-title">玻璃结构剖面 · {{ current ? current.recordNo : '-' }}</div>
        <div class="section-wrap">
          <div
            v-for="(b, i) in bands" :key="i"
            class="layer"
            :style="{ height: b.h + 'px', background: `linear-gradient(120deg, ${b.color}cc, ${b.color}55 62%, ${b.color}22)`, borderColor: b.color }"
          >
            <div class="layer-sheen"></div>
            <div class="layer-body">
              <span class="l-name">{{ b.label }}</span>
              <span class="l-val">{{ b.value }}</span>
            </div>
          </div>
          <div v-if="!bands.length" class="empty">暂无该工序记录</div>
        </div>

        <div class="stat-strip">
          <div v-for="s in stat" :key="s.processType" class="mini-card">
            <div class="k">{{ s.processTypeName }} · {{ s.recordCount }} 批</div>
            <div class="v">{{ s.pieceCount }} 片</div>
            <div class="load-bar"><i :style="{ width: s.passRate + '%' }"></i></div>
            <div class="k" style="margin-top: 4px">合格率 {{ s.passRate }}%</div>
          </div>
        </div>
      </div>

      <!-- 右：参数记录 -->
      <div class="glass glass-pad">
        <div class="section-title">工序参数记录</div>
        <el-table
          :data="records" size="small" stripe max-height="420"
          highlight-current-row @current-change="(row) => selectedId = row ? row.id : null"
        >
          <el-table-column prop="recordNo" label="记录单号" width="150" />
          <el-table-column prop="batchNo" label="批次" width="110" />
          <el-table-column v-if="processType === 'COATING'" prop="filmSystem" label="膜系" width="100" />
          <el-table-column v-if="processType === 'COATING'" prop="filmThicknessNm" label="膜厚nm" width="80" />
          <el-table-column v-if="processType === 'COATING'" prop="emissivity" label="辐射率" width="80" />
          <el-table-column v-if="processType === 'INSULATING'" prop="spacer" label="间隔条" width="100" />
          <el-table-column v-if="processType === 'INSULATING'" prop="gasType" label="气体" width="70" />
          <el-table-column v-if="processType === 'INSULATING'" label="露点℃" width="80">
            <template #default="{ row }">
              <span :style="{ color: Number(row.dewPoint) <= -40 ? '#4dd6a8' : '#e4573d', fontWeight: 700 }">{{ row.dewPoint }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="processType === 'LAMINATING'" prop="pvbThickness" label="PVB" width="70" />
          <el-table-column v-if="processType === 'LAMINATING'" prop="autoclaveTemp" label="釜温℃" width="80" />
          <el-table-column v-if="processType === 'LAMINATING'" prop="autoclavePressure" label="压力MPa" width="90" />
          <el-table-column prop="pieceCount" label="片数" width="70" />
          <el-table-column label="判定" width="80">
            <template #default="{ row }">
              <span :style="{ color: row.result === 'PASS' ? '#4dd6a8' : '#e4573d', fontWeight: 700 }">{{ row.resultName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="operator" label="操作员" width="80" />
        </el-table>
        <div class="hint" v-if="current">{{ current.remark }}</div>
      </div>
    </div>

    <el-dialog v-model="dialog" title="深加工投产登记" width="560px">
      <el-form label-width="110px" size="small">
        <el-form-item label="记录单号"><el-input v-model="form.recordNo" /></el-form-item>
        <el-form-item label="原片批次"><el-input v-model="form.batchNo" placeholder="如 YP-2402-01" /></el-form-item>
        <el-form-item label="玻璃厚度"><el-input-number v-model="form.thicknessMm" :min="4" :max="19" /></el-form-item>
        <el-form-item label="投产片数"><el-input-number v-model="form.pieceCount" :min="1" :max="500" /></el-form-item>

        <template v-if="processType === 'COATING'">
          <el-form-item label="膜系"><el-input v-model="form.filmSystem" /></el-form-item>
          <el-form-item label="靶材"><el-input v-model="form.targetMaterial" /></el-form-item>
          <el-form-item label="膜层厚度nm"><el-input-number v-model="form.filmThicknessNm" :min="20" :max="500" /></el-form-item>
          <el-form-item label="辐射率"><el-input-number v-model="form.emissivity" :min="0.02" :max="0.9" :step="0.01" /></el-form-item>
        </template>

        <template v-if="processType === 'INSULATING'">
          <el-form-item label="间隔条"><el-input v-model="form.spacer" /></el-form-item>
          <el-form-item label="气体">
            <el-select v-model="form.gasType" style="width: 100%">
              <el-option label="空气" value="AIR" /><el-option label="氩气" value="ARGON" /><el-option label="氪气" value="KRYPTON" />
            </el-select>
          </el-form-item>
          <el-form-item label="露点℃"><el-input-number v-model="form.dewPoint" :min="-80" :max="0" /></el-form-item>
        </template>

        <template v-if="processType === 'LAMINATING'">
          <el-form-item label="PVB 厚度">
            <el-select v-model="form.pvbThickness" style="width: 100%">
              <el-option v-for="p in [0.38, 0.76, 1.14, 1.52]" :key="p" :label="p + ' mm'" :value="p" />
            </el-select>
          </el-form-item>
          <el-form-item label="釜温℃"><el-input-number v-model="form.autoclaveTemp" :min="100" :max="170" /></el-form-item>
          <el-form-item label="压力MPa"><el-input-number v-model="form.autoclavePressure" :min="0.5" :max="2" :step="0.05" /></el-form-item>
          <el-form-item label="时长min"><el-input-number v-model="form.autoclaveMinutes" :min="10" :max="300" /></el-form-item>
        </template>

        <el-form-item label="操作员"><el-input v-model="form.operator" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submit">投产登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section-wrap {
  border-radius: 12px; padding: 12px; display: flex; flex-direction: column; gap: 4px;
  background: linear-gradient(160deg, rgba(10, 16, 60, 0.6), rgba(20, 28, 82, 0.35));
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.24);
  min-height: 300px;
}
.layer {
  position: relative; overflow: hidden; border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6), 0 3px 10px rgba(0, 0, 0, 0.35);
  transition: transform .2s ease;
  cursor: default;
}
.layer:hover { transform: scale(1.01); }
.layer-sheen {
  position: absolute; inset: 0; pointer-events: none;
  background: linear-gradient(112deg, rgba(255,255,255,0) 32%, rgba(255,255,255,0.38) 46%, rgba(255,255,255,0) 62%);
  mix-blend-mode: screen;
}
.layer-body { position: relative; height: 100%; display: flex; align-items: center; justify-content: space-between; padding: 0 14px; }
.l-name { font-size: 12px; font-weight: 800; color: #fff; text-shadow: 0 1px 3px rgba(0,0,0,.6); }
.l-val { font-size: 12px; color: rgba(255,255,255,.95); }
.empty { color: var(--ink-dim); font-size: 12px; padding: 20px; text-align: center; }
.stat-strip { display: grid; grid-template-columns: repeat(3, 1fr); gap: 9px; margin-top: 12px; }
.stat-strip .k { font-size: 10px; }
.stat-strip .v { font-size: 15px; }
.hint { font-size: 11px; color: var(--ink-dim); margin-top: 9px; line-height: 1.6; }
</style>
