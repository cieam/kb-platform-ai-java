<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">
        <el-icon :size="20"><Reading /></el-icon>
        <span>AI 知识库</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能问答</span>
        </el-menu-item>
        <el-menu-item index="/kb">
          <el-icon><Collection /></el-icon>
          <span>知识库</span>
        </el-menu-item>
        <el-menu-item index="/doc">
          <el-icon><Document /></el-icon>
          <span>文档管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="picker">
          <span class="picker-label">当前知识库</span>
          <el-select
            v-model="kbStore.currentId"
            placeholder="请先新建知识库"
            style="width: 240px"
            @change="onKbChange"
          >
            <el-option
              v-for="kb in kbStore.list"
              :key="kb.id"
              :label="`${kb.name}（${kb.docCount} 篇）`"
              :value="kb.id"
            />
          </el-select>
          <!-- 已经在知识库页面时隐藏：点了也是跳当前页，看起来会像“没反应” -->
          <el-button v-if="route.name !== 'kb'" link type="primary" @click="$router.push('/kb')">
            管理
          </el-button>
          <el-tag v-if="!kbStore.list.length" type="warning" effect="light" size="small">
            还没有知识库，请先到「知识库」新建
          </el-tag>
        </div>

        <div class="user">
          <el-icon><User /></el-icon>
          <span class="nickname">{{ userStore.nickname || '未登录' }}</span>
          <el-button link type="danger" @click="onLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useKbStore } from '@/store/kb'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const kbStore = useKbStore()

const activeMenu = computed(() => route.path)

onMounted(() => {
  kbStore.load()
})

function onKbChange(id) {
  kbStore.select(id)
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  userStore.logout()
  ElMessage.success('已退出')
  router.push({ name: 'login' })
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #0f4c81;
  color: #fff;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.menu {
  flex: 1;
  border-right: none;
  background: transparent;
  --el-menu-text-color: #cfe0ef;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: #ffffff;
}

.menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.16);
  border-right: 3px solid #7fc4ff;
}

.header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e3e9f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.picker {
  display: flex;
  align-items: center;
  gap: 10px;
}

.picker-label {
  font-size: 13px;
  color: #6b7a8d;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #5b6b7d;
}

.nickname {
  font-size: 13px;
}

.main {
  padding: 16px;
  overflow: hidden;
}
</style>
