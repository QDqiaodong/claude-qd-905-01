<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import router from '../router'

const route = computed(() => router.currentRoute.value)

const STATUS = {
  ISOLATED: { label: '隔离', color: '#ff6b6b' },
  PROCESSING: { label: '处理中', color: '#ffb74d' },
  PENDING_RECHECK: { label: '待复检', color: '#4fc3f7' },
  RELEASED: { label: '已放行', color: '#4dd6a8' }
}
const TABS = [{ key: '', label: '全部' }, ...Object.entries(STATUS).map(([key, v]) => ({ key, label: v.label }))]

const EVENT_COLOR = {
  CREATE: '#ff6b6b', MERGE: '#b39ddb', APPEND: '#ffb74d', DISPOSE: '#4fc3f7',
  SUBMIT: '#7ea6ff', RECHECK: '#4dd6a8', CONFLICT: '#e4573d', SHIP_BLOCK: '#e4573d'
}

const METHODS = ['复磨', '重新钢化', '重新镀膜', '重新合片', '补胶', '其他']

const status = ref('')
const summary = ref({})
const batches = ref([])
const loading = ref(false)

function statusFromRoute() {
  const s = route.value?.query?.status || ''
  return STATUS[s] ? s : ''
}

async function load() {
  loading.value = true
  try {
    const [list, sum] = await Promise.all([
      api.rework.list(status.value || undefined),
      api.rework.summary()
    ])
    batches.value = list
    summary.value = sum
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function pick(key) {
  status.value = key
  load()
}

// 从导航「待复检」等入口进来时同步筛选
watch(() => route.value?.query?.status, () => {
  const s = statusFromRoute()
  if (s !== status.value) {
    status.value = s
    load()
  }
})

// ---------------- 时间线抽屉 ----------------

const drawer = ref(false)
const detail = ref(null)

async function openTimeline(row) {
  try {
    detail.value = await api.rework.detail(row.id)
    drawer.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

// ---------------- 处置 / 提交复检 / 复检判定 ----------------

const actionDialog = ref(false)
const actionKind = ref('dispose') // dispose | submit
const actionForm = ref({ method: '复磨', note: '', operator: '' })
const current = ref(null)

const ACTION_META = {
  dispose: { title: '登记处置（隔离 → 处理中）', showMethod: true },
  submit: { title: '提交复检（处理中 → 待复检）', showMethod: false }
}

function openAction(row, kind) {
  current.value = row
  actionKind.value = kind
  actionForm.value = { method: '复磨', note: '', operator: row.operator || '' }
  actionDialog.value = true
}

async function submitAction() {
  try {
    const id = current.value.id
    const body = { ...actionForm.value }
    if (actionKind.value === 'dispose') {
      await api.rework.dispose(id, body)
      ElMessage.success(`返修批次 ${current.value.reworkNo} 已登记处置，进入处理中`)
    } else {
      await api.rework.submitRecheck(id, body)
      ElMessage.success(`返修批次 ${current.value.reworkNo} 已提交复检`)
    }
    actionDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
    actionDialog.value = false
    await load()
  }
}

const recheckDialog = ref(false)
const recheckForm = ref({ verdict: 'PASS', inspector: '', note: '' })

function openRecheck(row) {
  current.value = row
  recheckForm.value = { verdict: 'PASS', inspector: '', note: '' }
  recheckDialog.value = true
}

async function submitRecheck() {
  try {
    await api.rework.recheck(current.value.id, { ...recheckForm.value })
    const pass = recheckForm.value.verdict === 'PASS'
    ElMessage.success(pass
      ? `返修批次 ${current.value.reworkNo} 复检合格，已放行`
      : `返修批次 ${current.value.reworkNo} 再判返修，已回到隔离`)
    recheckDialog.value = false
    await load()
  } catch (e) {
    // 并发冲突（409）：另一名检验员的结论已生效，这里明确提示并刷新到最新状态
    ElMessage.error(e.message)
    recheckDialog.value = false
    await load()
  }
}

onMounted(() => {
  status.value = statusFromRoute()
  load()
})
</script>

<template>
  <div>
    <div class="view-head">
      <div class="view-title">返修批次工作区 · 隔离 → 处理中 → 待复检 → 已放行</div>
      <div class="view-tip">检验判返修即建批隔离；只有复检合格才能放行，再判返修回到隔离；未闭环批次在成品架上架 / 发运时会被拦截</div>
    </div>

    <!-- 状态待办玻璃条 -->
    <div class="glass-bar">
      <span class="glass-bar-label">状态待办</span>
      <span
        v-for="t in TABS" :key="t.key" class="chip"
        :class="{ on: status === t.key }" @click="pick(t.key)"
      >
        {{ t.label }}
        <b v-if="t.key === ''">{{ summary.TOTAL || 0 }}</b>
        <b v-else>{{ summary[t.key] || 0 }}</b>
      </span>
      <span style="margin-left: auto" class="glass-bar-label">
        未闭环 {{ (summary.ISOLATED || 0) + (summary.PROCESSING || 0) + (summary.PENDING_RECHECK || 0) }} 批 · 已放行 {{ summary.RELEASED || 0 }} 批
      </span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <!-- 返修批次列表 -->
    <div class="glass glass-pad">
      <div class="section-title">返修批次（来源工序 · 涉及片数 · 缺陷 · 处置进度）</div>
      <el-table :data="batches" size="small" stripe v-loading="loading" max-height="460">
        <el-table-column prop="reworkNo" label="返修单号" width="140" />
        <el-table-column label="来源工序" width="90">
          <template #default="{ row }">{{ row.processTypeName }}</template>
        </el-table-column>
        <el-table-column label="批次号" width="110">
          <template #default="{ row }">{{ row.batchNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="关联单号" width="140">
          <template #default="{ row }">{{ row.orderNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="defectSummary" label="缺陷" width="110" show-overflow-tooltip />
        <el-table-column prop="sheetCount" label="涉及片数" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="rw-status" :style="{ color: STATUS[row.status]?.color, borderColor: STATUS[row.status]?.color }">
              {{ row.statusName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最新进展" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.note || '—' }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="经办" width="70" />
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openTimeline(row)">时间线</el-button>
            <el-button v-if="row.status === 'ISOLATED'" size="small" type="primary" @click="openAction(row, 'dispose')">登记处置</el-button>
            <el-button v-if="row.status === 'PROCESSING'" size="small" type="primary" @click="openAction(row, 'submit')">提交复检</el-button>
            <el-button v-if="row.status === 'PENDING_RECHECK'" size="small" type="warning" @click="openRecheck(row)">复检判定</el-button>
          </template>
        </el-table-column>
        <template #empty><span style="color:#8c98cc">暂无返修批次</span></template>
      </el-table>
    </div>

    <!-- 批次时间线抽屉 -->
    <el-drawer v-model="drawer" size="560px" :title="detail ? `批次时间线 · ${detail.batch.reworkNo}` : '批次时间线'">
      <div v-if="detail" class="tl-wrap">
        <div class="tl-head">
          <div class="tl-row">
            <span class="rw-status" :style="{ color: STATUS[detail.batch.status]?.color, borderColor: STATUS[detail.batch.status]?.color }">
              {{ detail.batch.statusName }}
            </span>
            <b>{{ detail.batch.processTypeName }}</b>
            <span>批次 {{ detail.batch.batchNo || '—' }}</span>
            <span>单号 {{ detail.batch.orderNo || '—' }}</span>
          </div>
          <div class="tl-row sub">
            缺陷 {{ detail.batch.defectSummary || '—' }} · 涉及 {{ detail.batch.sheetCount }} 片 · 经办 {{ detail.batch.operator || '—' }}
          </div>
          <div class="tl-row sub" v-if="detail.batch.note">进展：{{ detail.batch.note }}</div>
        </div>

        <div class="section-title" style="margin-top: 6px">流转与审计事件</div>
        <el-timeline class="tl-line">
          <el-timeline-item
            v-for="ev in detail.events" :key="ev.id"
            :color="EVENT_COLOR[ev.eventType] || '#8c98cc'" :timestamp="fmtTime(ev.createdAt)" placement="top"
          >
            <div class="ev-head">
              <b>{{ ev.eventTypeName }}</b>
              <span v-if="ev.fromStatus || ev.toStatus" class="ev-flow">
                {{ ev.fromStatusName || '—' }} → {{ ev.toStatusName || '—' }}
              </span>
              <span v-if="ev.verdict" class="ev-verdict">结论：{{ ev.verdictName }}</span>
              <span v-if="ev.operator" class="ev-op">{{ ev.operator }}</span>
            </div>
            <div v-if="ev.note" class="ev-note">{{ ev.note }}</div>
          </el-timeline-item>
        </el-timeline>

        <div class="section-title">归并的检验记录（{{ detail.records.length }} 条）</div>
        <el-table :data="detail.records" size="small" stripe>
          <el-table-column prop="inspectNo" label="检验单号" width="130" />
          <el-table-column label="缺陷" width="70">
            <template #default="{ row }">{{ { SCRATCH: '划伤', BUBBLE: '气泡', STONE: '结石', CHIP: '崩边', WAVE: '波形', STRESS: '应力斑' }[row.defectType] || row.defectType }}</template>
          </el-table-column>
          <el-table-column prop="sheetCount" label="片数" width="60" />
          <el-table-column label="批次号" width="100">
            <template #default="{ row }">{{ row.batchNo || '—' }}</template>
          </el-table-column>
          <el-table-column label="关联单号" width="130">
            <template #default="{ row }">{{ row.orderNo || '—' }}</template>
          </el-table-column>
          <el-table-column prop="inspector" label="检验员" width="70" />
        </el-table>
      </div>
    </el-drawer>

    <!-- 登记处置 / 提交复检 -->
    <el-dialog v-model="actionDialog" :title="ACTION_META[actionKind].title" width="480px">
      <el-form label-width="90px" size="small">
        <el-form-item label="返修批次">
          <b>{{ current?.reworkNo }}</b>
          <span style="margin-left: 10px; color: #8c98cc">{{ current?.processTypeName }} · {{ current?.defectSummary }}</span>
        </el-form-item>
        <el-form-item v-if="ACTION_META[actionKind].showMethod" label="处置方式">
          <el-select v-model="actionForm.method" style="width: 100%">
            <el-option v-for="m in METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置说明">
          <el-input v-model="actionForm.note" type="textarea" :rows="2" placeholder="处置措施、返修结果等（可空）" />
        </el-form-item>
        <el-form-item label="经办人">
          <el-input v-model="actionForm.operator" placeholder="署名（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="actionDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitAction">确认</el-button>
      </template>
    </el-dialog>

    <!-- 复检判定 -->
    <el-dialog v-model="recheckDialog" title="复检判定（待复检 → 已放行 / 隔离）" width="480px">
      <el-form label-width="90px" size="small">
        <el-form-item label="返修批次">
          <b>{{ current?.reworkNo }}</b>
          <span style="margin-left: 10px; color: #8c98cc">{{ current?.processTypeName }} · {{ current?.defectSummary }}</span>
        </el-form-item>
        <el-form-item label="复检结论">
          <el-radio-group v-model="recheckForm.verdict">
            <el-radio value="PASS">合格放行</el-radio>
            <el-radio value="REWORK">再判返修（回到隔离）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检验员">
          <el-input v-model="recheckForm.inspector" placeholder="署名（必填）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="recheckForm.note" type="textarea" :rows="2" placeholder="复检依据、遗留问题等（可空）" />
        </el-form-item>
        <el-form-item label=" ">
          <span style="font-size: 11px; color: #8c98cc">两人同时提交相反结论时只生效一个，未生效方会收到冲突提示并留痕</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="recheckDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitRecheck">提交结论</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.rw-status {
  display: inline-block; padding: 2px 10px; border-radius: 999px;
  font-size: 12px; font-weight: 700; border: 1px solid;
  background: rgba(255, 255, 255, 0.06);
}
.tl-wrap { padding: 0 4px; }
.tl-head {
  padding: 10px 12px; border-radius: 12px; margin-bottom: 12px;
  background: rgba(255, 255, 255, 0.07); border: 1px solid var(--line);
}
.tl-row { display: flex; align-items: center; gap: 12px; font-size: 13px; color: var(--ink); flex-wrap: wrap; }
.tl-row.sub { font-size: 11.5px; color: var(--ink-soft); margin-top: 6px; }
.tl-line { padding-left: 4px; }
.ev-head { display: flex; align-items: center; gap: 10px; font-size: 12.5px; flex-wrap: wrap; }
.ev-head b { color: #fff; }
.ev-flow { color: var(--glass-cyan); font-size: 11.5px; }
.ev-verdict { color: var(--glass-amber); font-size: 11.5px; }
.ev-op { color: var(--ink-dim); font-size: 11px; }
.ev-note { font-size: 11.5px; color: var(--ink-soft); margin-top: 4px; line-height: 1.6; }
:deep(.el-timeline-item__timestamp) { color: var(--ink-dim); }
:deep(.el-timeline-item__content) { color: var(--ink-soft); }
</style>
