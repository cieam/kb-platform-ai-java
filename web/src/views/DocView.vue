<template>
  <div class="page">
    <el-alert
      v-if="!kbStore.currentId"
      type="warning"
      :closable="false"
      show-icon
      title="请先选择或新建一个知识库"
      description="文档必须归属于某个知识库，问答也只在选中的知识库内检索。"
      style="margin-bottom: 14px"
    />

    <el-card shadow="never" class="upload-card">
      <template #header>
        <div class="card-header">
          <span>上传文档</span>
          <span class="sub">当前知识库：{{ kbStore.current?.name || '未选择' }}</span>
        </div>
      </template>

      <el-upload
        drag
        action="#"
        :show-file-list="false"
        :http-request="doUpload"
        :disabled="!kbStore.currentId"
        accept=".pdf,.doc,.docx,.md,.markdown,.txt"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">把文件拖到这里，或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF / Word / Markdown / TXT，单文件不超过 50MB。
            上传后后台自动解析、切分、向量化，状态变成 SUCCESS 就可以问答了。
          </div>
        </template>
      </el-upload>

      <el-progress v-if="uploading" :percentage="progress" :stroke-width="6" style="margin-top: 12px" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>文档列表（{{ total }}）</span>
          <div>
            <el-tag v-if="polling" size="small" type="warning" effect="plain">解析中，自动刷新…</el-tag>
            <el-button link type="primary" :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" empty-text="还没有文档">
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80" align="center" />
        <el-table-column label="大小" width="100" align="right">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 'FAILED'" placement="top" :content="row.errorMsg || '解析失败'">
              <el-tag size="small" type="danger" effect="light">FAILED</el-tag>
            </el-tooltip>
            <el-tag v-else size="small" :type="statusType(row.status)" effect="light">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片数" width="90" align="center" />
        <el-table-column label="耗时" width="100" align="right">
          <template #default="{ row }">{{ row.parseCostMs ? (row.parseCostMs / 1000).toFixed(1) + ' s' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status === 'PARSING'" @click="reparse(row)">
              重新解析
            </el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > size"
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { deleteDoc, pageDoc, reparseDoc, uploadDoc } from '@/api/doc'
import { useKbStore } from '@/store/kb'

const kbStore = useKbStore()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const uploading = ref(false)
const progress = ref(0)

let timer = null
const polling = ref(false)

watch(
  () => kbStore.currentId,
  () => {
    page.value = 1
    load()
  }
)

load()

onUnmounted(() => stopPolling())

async function load() {
  if (!kbStore.currentId) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await pageDoc({ kbId: kbStore.currentId, page: page.value, size: size.value })
    list.value = result.list || []
    total.value = result.total || 0
    syncPolling()
  } finally {
    loading.value = false
  }
}

function onPageChange(value) {
  page.value = value
  load()
}

/** 有文档还在解析就开轮询，全都结束就关掉 */
function syncPolling() {
  const busy = list.value.some((doc) => doc.status === 'PENDING' || doc.status === 'PARSING')
  if (busy && !timer) {
    timer = setInterval(load, 2000)
  } else if (!busy && timer) {
    stopPolling()
  }
  polling.value = !!timer
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  polling.value = false
}

async function doUpload(options) {
  if (!kbStore.currentId) {
    ElMessage.warning('请先选择知识库')
    options.onError(new Error('未选择知识库'))
    return
  }
  uploading.value = true
  progress.value = 0
  try {
    const doc = await uploadDoc(kbStore.currentId, options.file, (value) => {
      progress.value = value
    })
    options.onSuccess(doc)
    ElMessage.success('上传成功，正在后台解析')
    page.value = 1
    await load()
    await kbStore.load()
  } catch (e) {
    options.onError(e)
  } finally {
    uploading.value = false
  }
}

async function reparse(row) {
  await reparseDoc(row.id)
  ElMessage.success('已重新提交解析')
  await load()
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除「${row.fileName}」会同时清理它的向量数据，确定删除吗？`,
      '删除文档',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  await deleteDoc(row.id)
  ElMessage.success('已删除')
  await load()
  await kbStore.load()
}

function statusType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PARSING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function formatSize(bytes) {
  const value = Number(bytes || 0)
  if (value < 1024) return value + ' B'
  if (value < 1024 * 1024) return (value / 1024).toFixed(1) + ' KB'
  return (value / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.page {
  height: 100%;
  overflow-y: auto;
}

.upload-card {
  margin-bottom: 14px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header .sub {
  font-size: 12.5px;
  color: #8593a5;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
