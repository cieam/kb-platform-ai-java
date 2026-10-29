<template>
  <div class="login-page">
    <div class="brand">
      <h1>AI 知识库问答平台</h1>
      <p class="sub">上传文档 · 自动切分向量化 · 检索增强问答 · 答案可溯源</p>
      <ul class="points">
        <li><el-icon><Select /></el-icon> 混合检索：向量召回 + 关键词召回，RRF 融合</li>
        <li><el-icon><Lightning /></el-icon> SSE 流式输出，答案逐字返回</li>
        <li><el-icon><Link /></el-icon> 答案标注来源，可追溯到原文第几段</li>
        <li><el-icon><Lock /></el-icon> JWT 鉴权，知识库级数据隔离</li>
      </ul>
    </div>

    <el-card class="login-card" shadow="always">
      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginRef" :model="loginForm" :rules="loginRules" label-position="top">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="请输入密码"
                :prefix-icon="Lock"
                @keyup.enter="doLogin"
              />
            </el-form-item>
            <el-button type="primary" class="submit" :loading="loading" @click="doLogin">
              登 录
            </el-button>
            <div class="tip">演示账号：<b>admin</b> / <b>123456</b></div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form ref="registerRef" :model="registerForm" :rules="registerRules" label-position="top">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="registerForm.username" placeholder="3 - 32 个字符" :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="选填" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                show-password
                placeholder="6 - 32 个字符"
                :prefix-icon="Lock"
                @keyup.enter="doRegister"
              />
            </el-form-item>
            <el-button type="primary" class="submit" :loading="loading" @click="doRegister">
              注 册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useKbStore } from '@/store/kb'

const router = useRouter()
const userStore = useUserStore()
const kbStore = useKbStore()

const tab = ref('login')
const loading = ref(false)
const loginRef = ref()
const registerRef = ref()

const loginForm = reactive({ username: 'admin', password: '123456' })
const registerForm = reactive({ username: '', nickname: '', password: '' })

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '长度需在 3 到 32 之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度需在 6 到 32 之间', trigger: 'blur' }
  ]
}

async function doLogin() {
  await loginRef.value.validate()
  loading.value = true
  try {
    await userStore.login(loginForm)
    await kbStore.load()
    ElMessage.success('登录成功')
    router.push({ name: 'chat' })
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  await registerRef.value.validate()
  loading.value = true
  try {
    await userStore.register(registerForm)
    await kbStore.load()
    ElMessage.success('注册成功，已自动登录')
    router.push({ name: 'chat' })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 80px;
  background: linear-gradient(135deg, #0f4c81 0%, #1f6fb2 55%, #3f97d6 100%);
  padding: 24px;
}

.brand {
  color: #fff;
  max-width: 460px;
}

.brand h1 {
  font-size: 30px;
  margin: 0 0 12px;
  letter-spacing: 1px;
}

.sub {
  font-size: 14px;
  opacity: 0.85;
  margin-bottom: 24px;
}

.points {
  list-style: none;
  padding: 0;
  margin: 0;
}

.points li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
  font-size: 13.5px;
  opacity: 0.92;
}

.login-card {
  width: 360px;
  border-radius: 12px;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.tip {
  margin-top: 12px;
  font-size: 12px;
  color: #8593a5;
  text-align: center;
}

@media (max-width: 900px) {
  .login-page {
    flex-direction: column;
    gap: 28px;
  }

  .brand {
    text-align: center;
  }
}
</style>
