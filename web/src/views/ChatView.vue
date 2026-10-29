<template>
  <div class="chat-page">
    <aside class="conversations">
      <div class="conv-head">
        <el-button type="primary" :icon="Plus" class="new-btn" @click="newConversation">
          新建会话
        </el-button>
        <el-button link class="batch-toggle" @click="toggleBatch">
          {{ batchMode ? '完成' : '批量' }}
        </el-button>
      </div>
      <el-scrollbar class="conv-scroll">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === conversationId }"
          @click="onConvClick(conv)"
        >
          <el-checkbox
            v-if="batchMode"
            :model-value="selectedIds.includes(conv.id)"
            @click.stop="toggleSelect(conv.id)"
          />
          <el-icon v-else><ChatLineSquare /></el-icon>
          <div class="conv-body">
            <div class="conv-title">{{ conv.title || '未命名会话' }}</div>
            <div class="conv-meta">{{ conv.messageCount || 0 }} 条 · {{ shortTime(conv.lastMessageTime) }}</div>
          </div>
          <el-icon v-if="!batchMode" class="conv-delete" title="删除会话" @click.stop="removeConversation(conv)">
            <Delete />
          </el-icon>
        </div>
        <div v-if="!conversations.length" class="conv-empty">还没有会话</div>
      </el-scrollbar>
      <div v-if="batchMode" class="batch-bar">
        <el-checkbox :model-value="allSelected" @change="toggleAll">全选</el-checkbox>
        <el-button type="danger" size="small" :disabled="!selectedIds.length" @click="removeSelected">
          删除({{ selectedIds.length }})
        </el-button>
      </div>
    </aside>

    <section class="chat-main">
      <div v-if="!kbStore.currentId" class="empty-tip">
        <el-empty description="请先在右上角选择或新建知识库">
          <el-button type="primary" @click="$router.push('/kb')">去新建知识库</el-button>
        </el-empty>
      </div>

      <template v-else>
        <el-scrollbar ref="scrollRef" class="messages">
          <div class="messages-inner">
            <div v-if="!messages.length" class="welcome">
              <h3>在「{{ kbStore.current?.name }}」里提问</h3>
              <p>答案只基于这个知识库里的文档，并会标注来源片段。</p>
              <div class="samples">
                <el-tag
                  v-for="sample in samples"
                  :key="sample"
                  class="sample"
                  effect="plain"
                  @click="question = sample"
                >
                  {{ sample }}
                </el-tag>
              </div>
            </div>

            <MessageItem v-for="(message, index) in messages" :key="index" :message="message" />
          </div>
        </el-scrollbar>

        <div class="composer">
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="输入问题，Enter 发送，Shift + Enter 换行"
            @keydown.enter="onEnter"
          />
          <div class="composer-actions">
            <el-button v-if="streaming" type="danger" plain @click="stop">停止生成</el-button>
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="streaming"
              :disabled="!question.trim()"
              @click="send"
            >
              发送
            </el-button>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Promotion } from '@element-plus/icons-vue'
import MessageItem from '@/components/MessageItem.vue'
import { createConversation, deleteConversation, deleteConversations, listConversations, listMessages, streamAsk } from '@/api/chat'
import { useKbStore } from '@/store/kb'

const kbStore = useKbStore()

const conversations = ref([])
const conversationId = ref(null)
const messages = ref([])
const question = ref('')
const streaming = ref(false)
const scrollRef = ref()

// 批量管理模式：开启后列表前出现勾选框，底部出现全选与删除
const batchMode = ref(false)
const selectedIds = ref([])
const allSelected = computed(
  () => conversations.value.length > 0 && selectedIds.value.length === conversations.value.length
)

const samples = ['这个知识库收录了哪些内容？', '请假流程是什么？', '总结一下主要规定']

const stream = { controller: null, buffer: '', timer: null }

watch(
  () => kbStore.currentId,
  () => {
    conversationId.value = null
    messages.value = []
    loadConversations()
  },
  { immediate: true }
)

onUnmounted(() => {
  stop()
  if (stream.timer) {
    clearTimeout(stream.timer)
  }
})

async function loadConversations() {
  if (!kbStore.currentId) {
    conversations.value = []
    return
  }
  conversations.value = await listConversations(kbStore.currentId)
}

async function newConversation() {
  if (!kbStore.currentId) {
    ElMessage.warning('请先选择知识库')
    return
  }
  const conv = await createConversation(kbStore.currentId, '新会话')
  await loadConversations()
  conversationId.value = conv.id
  messages.value = []
}

async function removeConversation(conv) {
  try {
    await ElMessageBox.confirm(`删除会话「${conv.title || '未命名会话'}」？聊天记录会一并删除。`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return // 用户取消
  }
  await deleteConversation(conv.id)
  ElMessage.success('已删除')
  // 删的是当前会话时，清空右侧消息区，避免继续往已删除的会话里提问
  if (conv.id === conversationId.value) {
    conversationId.value = null
    messages.value = []
  }
  await loadConversations()
}

function toggleBatch() {
  batchMode.value = !batchMode.value
  selectedIds.value = []
}

/** 批量模式下点整行就是勾选，不再切换会话 */
function onConvClick(conv) {
  if (batchMode.value) {
    toggleSelect(conv.id)
  } else {
    switchConversation(conv)
  }
}

function toggleSelect(id) {
  const index = selectedIds.value.indexOf(id)
  if (index >= 0) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(id)
  }
}

function toggleAll(checked) {
  selectedIds.value = checked ? conversations.value.map((item) => item.id) : []
}

async function removeSelected() {
  const ids = [...selectedIds.value]
  if (!ids.length) {
    return
  }
  try {
    await ElMessageBox.confirm(`删除选中的 ${ids.length} 个会话？聊天记录会一并删除。`, '批量删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  const deleted = await deleteConversations(ids)
  ElMessage.success(`已删除 ${deleted} 个会话`)
  // 当前会话被删掉时清空右侧
  if (ids.includes(conversationId.value)) {
    conversationId.value = null
    messages.value = []
  }
  selectedIds.value = []
  await loadConversations()
}

async function switchConversation(conv) {
  if (conv.id === conversationId.value) {
    return
  }
  conversationId.value = conv.id
  const history = await listMessages(conv.id)
  messages.value = history.map((item) => ({
    role: item.msgRole,
    content: item.content,
    references: parseReferences(item.refChunks),
    streaming: false
  }))
  scrollToBottom()
}

function parseReferences(raw) {
  if (!raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

function onEnter(event) {
  if (event.shiftKey) {
    return
  }
  event.preventDefault()
  send()
}

async function send() {
  const text = question.value.trim()
  if (!text || streaming.value) {
    return
  }
  if (!kbStore.currentId) {
    ElMessage.warning('请先选择知识库')
    return
  }

  messages.value.push({ role: 'USER', content: text, references: [], streaming: false })
  question.value = ''

  messages.value.push({ role: 'ASSISTANT', content: '', references: [], streaming: true, error: '' })
  // 必须取数组里的那一份（Vue 会给它包一层 Proxy）。
  // 如果直接持有 push 进去的原始对象，改 content 不会触发响应式，
  // 表现就是"生成过程中不出字，结束时才一次性冒出来"。
  const target = messages.value[messages.value.length - 1]
  streaming.value = true
  scrollToBottom()

  stream.buffer = ''
  stream.controller = new AbortController()

  try {
    await streamAsk(
      {
        kbId: kbStore.currentId,
        conversationId: conversationId.value,
        question: text
      },
      {
        onDelta: (delta) => {
          stream.buffer += delta
          scheduleFlush(target)
        },
        onReferences: (references) => {
          target.references = references || []
        },
        onDone: (payload) => {
          if (payload && payload.conversationId) {
            conversationId.value = payload.conversationId
          }
        }
      },
      stream.controller.signal
    )
  } catch (e) {
    if (e.name === 'AbortError') {
      target.error = '已停止生成'
    } else if (/network|fetch|load failed/i.test(e.message || '')) {
      // 浏览器原生的 fetch 报错文案各不相同，统一成用户能懂的提示
      target.error = '网络中断：后端可能正在重启，请稍后重试'
    } else {
      target.error = e.message || '生成失败'
    }
  } finally {
    flushNow(target)
    target.streaming = false
    streaming.value = false
    stream.controller = null
    scrollToBottom()
    loadConversations()
  }
}

function stop() {
  if (stream.controller) {
    stream.controller.abort()
  }
}

/** 每 60ms 刷一次 DOM，避免每个 token 都重渲染 Markdown */
function scheduleFlush(target) {
  if (stream.timer) {
    return
  }
  stream.timer = setTimeout(() => {
    stream.timer = null
    if (stream.buffer) {
      target.content += stream.buffer
      stream.buffer = ''
      scrollToBottom()
    }
  }, 60)
}

function flushNow(target) {
  if (stream.timer) {
    clearTimeout(stream.timer)
    stream.timer = null
  }
  if (stream.buffer) {
    target.content += stream.buffer
    stream.buffer = ''
  }
}

async function scrollToBottom() {
  await nextTick()
  const wrap = scrollRef.value?.wrapRef
  if (wrap) {
    wrap.scrollTop = wrap.scrollHeight
  }
}

function shortTime(value) {
  if (!value) {
    return ''
  }
  return String(value).slice(5, 16).replace('T', ' ')
}
</script>

<style scoped>
.chat-page {
  display: flex;
  gap: 14px;
  height: 100%;
  min-height: 0;
}

.conversations {
  flex: 0 0 230px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e3e9f0;
  border-radius: 8px;
  padding: 10px;
}

.conv-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.new-btn {
  flex: 1;
}

.batch-toggle {
  flex: none;
  font-size: 12.5px;
  color: #5b6b7c;
}

.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  margin-top: 6px;
  border-top: 1px solid #eef2f6;
}

.conv-scroll {
  flex: 1;
  min-height: 0;
}

.conv-item {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: #4a5a6b;
}

.conv-item:hover {
  background: #f2f7fc;
}

.conv-item.active {
  background: #e8f2fb;
  color: #0f4c81;
}

.conv-body {
  min-width: 0;
  flex: 1;
}

/* 删除按钮：默认隐藏，鼠标悬停时才出现，避免列表显得杂乱 */
.conv-delete {
  flex: none;
  align-self: center;
  color: #b6c2cf;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
}

.conv-item:hover .conv-delete {
  opacity: 1;
}

.conv-delete:hover {
  color: #e45656;
}

.conv-title {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-meta {
  font-size: 11.5px;
  color: #93a2b3;
}

.conv-empty {
  padding: 20px 0;
  text-align: center;
  font-size: 12.5px;
  color: #93a2b3;
}

.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e3e9f0;
  border-radius: 8px;
  overflow: hidden;
}

.messages {
  flex: 1;
  min-height: 0;
}

.messages-inner {
  padding: 18px 20px;
}

.empty-tip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.welcome {
  padding: 20px 4px 26px;
  color: #6b7a8d;
}

.welcome h3 {
  margin: 0 0 6px;
  color: #0f4c81;
  font-size: 16px;
}

.welcome p {
  margin: 0 0 14px;
  font-size: 13px;
}

.samples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sample {
  cursor: pointer;
}

.composer {
  border-top: 1px solid #e3e9f0;
  padding: 12px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
