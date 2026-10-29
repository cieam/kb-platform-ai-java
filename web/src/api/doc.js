import request from './request'

export const pageDoc = (params) => request.get('/doc/page', { params })

export const getDoc = (id) => request.get('/doc/' + id)

export const reparseDoc = (id) => request.post('/doc/' + id + '/reparse')

export const deleteDoc = (id) => request.delete('/doc/' + id)

/** 上传文档，onProgress 回调用于展示上传进度（0-100） */
export function uploadDoc(kbId, file, onProgress) {
  const form = new FormData()
  form.append('kbId', kbId)
  form.append('file', file)
  return request.post('/doc/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
  })
}
