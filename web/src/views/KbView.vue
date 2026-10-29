<template>
  <div class="page">
    <div class="toolbar">
      <div>
        <h2>知识库</h2>
        <p class="desc">每个知识库是一份独立的文档集合，问答只在选中的知识库内检索。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建知识库</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" empty-text="还没有知识库，点右上角新建一个">
        <el-table-column prop="name" label="名称" min-width="160">
          <template #default="{ row }">
            <div class="name-cell">
              <span>{{ row.name }}</span>
              <el-tag v-if="row.id === kbStore.currentId" size="small" type="success" effect="plain">
                当前
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="scope" label="可见范围" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.scope === 'TEAM' ? 'warning' : 'info'" effect="plain">
              {{ row.scope === 'TEAM' ? '团队' : '私有' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="docCount" label="文档数" width="90" align="center" />
        <el-table-column label="切分参数" width="120" align="center">
          <template #default="{ row }">{{ row.chunkSize }} / {{ row.chunkOverlap }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="useIt(row)">设为当前</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑知识库' : '新建知识库'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：员工手册" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
            placeholder="这份知识库收录什么内容"
          />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-radio-group v-model="form.scope">
            <el-radio value="PRIVATE">私有</el-radio>
            <el-radio value="TEAM">团队</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="切片长度">
          <el-input-number v-model="form.chunkSize" :min="200" :max="2000" :step="100" />
          <span class="hint">字符</span>
        </el-form-item>
        <el-form-item label="重叠长度">
          <el-input-number v-model="form.chunkOverlap" :min="0" :max="500" :step="10" />
          <span class="hint">字符，建议为切片长度的 15% 左右</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createKb, deleteKb, pageKb, updateKb } from '@/api/kb'
import { useKbStore } from '@/store/kb'

const kbStore = useKbStore()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  name: '',
  description: '',
  scope: 'PRIVATE',
  chunkSize: 800,
  chunkOverlap: 150
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const result = await pageKb({ page: page.value, size: size.value })
    list.value = result.list || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(value) {
  page.value = value
  load()
}

function resetForm() {
  form.id = null
  form.name = ''
  form.description = ''
  form.scope = 'PRIVATE'
  form.chunkSize = 800
  form.chunkOverlap = 150
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name
  form.description = row.description
  form.scope = row.scope || 'PRIVATE'
  form.chunkSize = row.chunkSize || 800
  form.chunkOverlap = row.chunkOverlap || 150
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      id: form.id,
      name: form.name,
      description: form.description,
      scope: form.scope,
      chunkSize: form.chunkSize,
      chunkOverlap: form.chunkOverlap
    }
    if (form.id) {
      await updateKb(payload)
      ElMessage.success('已保存')
    } else {
      const created = await createKb(payload)
      kbStore.select(created.id)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await load()
    await kbStore.load()
  } finally {
    saving.value = false
  }
}

function useIt(row) {
  kbStore.select(row.id)
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除后「${row.name}」下的文档将不再参与问答，确定删除吗？`,
      '删除知识库',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  await deleteKb(row.id)
  ElMessage.success('已删除')
  await load()
  await kbStore.load()
}
</script>

<style scoped>
.page {
  height: 100%;
  overflow-y: auto;
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.toolbar h2 {
  margin: 0 0 4px;
  font-size: 17px;
  color: #0f4c81;
}

.desc {
  margin: 0;
  font-size: 12.5px;
  color: #8593a5;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.hint {
  margin-left: 8px;
  font-size: 12px;
  color: #8593a5;
}
</style>
