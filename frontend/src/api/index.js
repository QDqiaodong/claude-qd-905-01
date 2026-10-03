import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

// 后端统一返回 {ok, message, data}；HTTP 400 的业务异常也要把中文提示带出来
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error?.response?.data?.message || error?.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

async function call(promise) {
  const response = await promise
  const body = response.data
  if (!body || body.ok === false) {
    throw new Error((body && body.message) || '请求失败')
  }
  return body.data
}

const stock = {
  list: () => call(http.get('/stock/list')),
  filter: (params) => call(http.get('/stock/filter', { params })),
  warehouse: () => call(http.get('/stock/warehouse')),
  locations: () => call(http.get('/stock/locations')),
  get: (id) => call(http.get(`/stock/${id}`)),
  create: (data) => call(http.post('/stock', data)),
  update: (id, data) => call(http.put(`/stock/${id}`, data)),
  remove: (id) => call(http.delete(`/stock/${id}`)),
  move: (id, data) => call(http.post(`/stock/${id}/move`, data)),
  applySpec: (id, specText) => call(http.post(`/stock/${id}/spec`, JSON.stringify(specText), {
    headers: { 'Content-Type': 'text/plain' }
  }))
}

const cutting = {
  list: () => call(http.get('/cutting/list')),
  detail: (id) => call(http.get(`/cutting/${id}`)),
  preview: (data) => call(http.post('/cutting/preview', data)),
  create: (data) => call(http.post('/cutting', data)),
  status: (id, status) => call(http.post(`/cutting/${id}/status?status=${encodeURIComponent(status)}`)),
  remove: (id) => call(http.delete(`/cutting/${id}`)),
  remnantRank: () => call(http.get('/cutting/remnant-rank'))
}

const process = {
  grindingList: () => call(http.get('/process/grinding')),
  createGrinding: (data) => call(http.post('/process/grinding', data)),
  grindingStat: () => call(http.get('/process/grinding/stat')),
  temperingList: () => call(http.get('/process/tempering')),
  furnace: (id) => call(http.get(`/process/tempering/${id}`)),
  createTempering: (data) => call(http.post('/process/tempering', data)),
  updateTempering: (id, data) => call(http.put(`/process/tempering/${id}`, data)),
  advance: (id) => call(http.post(`/process/tempering/${id}/advance`)),
  discharge: (id, outputCount, brokenCount) =>
    call(http.post(`/process/tempering/${id}/discharge?outputCount=${outputCount}&brokenCount=${brokenCount || 0}`)),
  furnaceStat: () => call(http.get('/process/furnace-stat'))
}

const deep = {
  list: () => call(http.get('/deep/list')),
  listByType: (type) => call(http.get(`/deep/list/${type}`)),
  create: (data) => call(http.post('/deep', data)),
  stat: () => call(http.get('/deep/stat'))
}

const inspection = {
  list: (processType) => call(http.get('/inspection/list', { params: { processType } })),
  create: (data) => call(http.post('/inspection', data)),
  defectMap: (processType) => call(http.get('/inspection/defect-map', { params: { processType } })),
  defectStat: () => call(http.get('/inspection/defect-stat')),
  racks: () => call(http.get('/inspection/racks')),
  createRack: (data) => call(http.post('/inspection/racks', data)),
  loadRack: (id, data) => call(http.post(`/inspection/racks/${id}/load`, data || {})),
  ship: (id) => call(http.post(`/inspection/racks/${id}/ship`)),
  rackStat: () => call(http.get('/inspection/rack-stat'))
}

const rework = {
  list: (status) => call(http.get('/rework/list', { params: { status } })),
  summary: () => call(http.get('/rework/summary')),
  detail: (id) => call(http.get(`/rework/${id}`)),
  dispose: (id, data) => call(http.post(`/rework/${id}/dispose`, data || {})),
  submitRecheck: (id, data) => call(http.post(`/rework/${id}/submit-recheck`, data || {})),
  recheck: (id, data) => call(http.post(`/rework/${id}/recheck`, data))
}

const stats = {
  overview: () => call(http.get('/stats/overview')),
  glassType: () => call(http.get('/stats/glass-type')),
  verdict: () => call(http.get('/stats/verdict')),
  remnant: () => call(http.get('/stats/remnant')),
  edge: () => call(http.get('/stats/edge')),
  furnace: () => call(http.get('/stats/furnace')),
  deep: () => call(http.get('/stats/deep')),
  defect: () => call(http.get('/stats/defect')),
  rack: () => call(http.get('/stats/rack'))
}

export default { http, stock, cutting, process, deep, inspection, rework, stats }
