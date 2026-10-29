import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

/**
 * axios 实例：自动带 token，自动剥掉统一返回体的外壳。
 * 后端约定 { code, msg, data }，code 为 0 才算成功。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.request.use((config) => {
  const user = useUserStore()
  if (user.token) {
    config.headers.Authorization = 'Bearer ' + user.token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data
      }
      if (body.code === 401) {
        const user = useUserStore()
        user.logout()
        if (router.currentRoute.value.name !== 'login') {
          router.push({ name: 'login' })
        }
      }
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return body
  },
  (error) => {
    const message = error?.response ? `HTTP ${error.response.status}` : (error.message || '网络异常')
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
