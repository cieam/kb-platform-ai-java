import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    // 开发时把 /api 代理到后端，避免跨域
    proxy: {
      // 单体应用默认 8080；要和微服务项目同时跑时，把单体换成 8085 启动，
      // 然后 $env:VITE_API_TARGET='http://localhost:8085' 再 pnpm dev
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // 直接构建到后端工程的静态资源目录，打包时前后端一起进 jar
    // （web 已并入后端仓库，所以相对路径少了一层）
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500
  }
})
