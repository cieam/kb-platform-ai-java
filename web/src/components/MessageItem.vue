<template>
  <div class="message" :class="message.role === 'USER' ? 'is-user' : 'is-assistant'">
    <div class="avatar">
      <el-icon v-if="message.role === 'USER'"><User /></el-icon>
      <el-icon v-else><MagicStick /></el-icon>
    </div>

    <div class="body">
      <div v-if="message.role === 'USER'" class="plain">{{ message.content }}</div>

      <template v-else>
        <div
          class="markdown-body"
          :class="{ 'streaming-cursor': message.streaming }"
          v-html="html"
        ></div>

        <div v-if="message.error" class="error">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ message.error }}</span>
        </div>

        <div v-if="references.length" class="references">
          <div class="ref-title">
            <el-icon><Link /></el-icon>
            <span>引用来源（{{ references.length }} 段）</span>
          </div>
          <div v-for="(ref, index) in references" :key="index" class="ref-item">
            <el-tag size="small" type="primary" effect="plain">[{{ index + 1 }}]</el-tag>
            <span class="ref-file">{{ ref.fileName }}</span>
            <span class="ref-meta">第 {{ (ref.chunkNo || 0) + 1 }} 段</span>
            <el-tooltip v-if="ref.score != null" placement="top">
              <template #content>
                <div style="max-width: 420px; line-height: 1.6">
                  <div style="margin-bottom: 6px; opacity: 0.75">
                    融合得分：向量与关键词两路召回的排名加权结果，只用于排序，不是余弦相似度
                  </div>
                  {{ ref.content }}
                </div>
              </template>
              <span class="ref-meta">融合得分 {{ Number(ref.score).toFixed(3) }}</span>
            </el-tooltip>
          </div>
        </div>

        <div v-if="!message.streaming && message.content" class="actions">
          <el-button link size="small" @click="copy">
            <el-icon><CopyDocument /></el-icon>
            <span style="margin-left: 4px">复制</span>
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  message: { type: Object, required: true }
})

const html = computed(() => renderMarkdown(props.message.content))

const references = computed(() => props.message.references || [])

async function copy() {
  try {
    await navigator.clipboard.writeText(props.message.content || '')
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('当前浏览器不支持自动复制')
  }
}
</script>

<style scoped>
.message {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.is-user {
  flex-direction: row-reverse;
}

.avatar {
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: #1f6fb2;
}

.is-assistant .avatar {
  background: #0f4c81;
}

.body {
  max-width: calc(100% - 60px);
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e3e9f0;
  box-shadow: 0 1px 2px rgba(15, 76, 129, 0.04);
}

.is-user .body {
  background: #1f6fb2;
  border-color: #1f6fb2;
  color: #fff;
}

.plain {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

.error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: #b03a2b;
  font-size: 13px;
}

.references {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #dfe6ee;
  font-size: 12px;
  color: #5b6b7d;
}

.ref-title {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #0f4c81;
  font-weight: 600;
  margin-bottom: 6px;
}

.ref-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
}

.ref-file {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ref-meta {
  color: #8593a5;
}

.actions {
  margin-top: 6px;
  text-align: right;
}
</style>
