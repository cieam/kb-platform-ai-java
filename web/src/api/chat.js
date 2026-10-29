import request from './request'
import { useUserStore } from '@/store/user'

export const createConversation = (kbId, title) =>
  request.post('/chat/conversation', null, { params: { kbId, title } })

export const listConversations = (kbId) => request.get('/chat/conversation', { params: { kbId } })

export const listMessages = (conversationId) => request.get('/chat/message', { params: { conversationId } })

/** 删除会话：后端会连消息和对话记忆一起清掉 */
export const deleteConversation = (conversationId) =>
  request.delete('/chat/conversation/' + conversationId)

/** 批量删除会话，返回实际删除条数 */
export const deleteConversations = (ids) =>
  request.delete('/chat/conversation/batch', { data: { ids } })

export const askOnce = (data) => request.post('/chat/ask', data)

/** 解析一段 SSE 报文 */
function dispatchEvent(raw, handlers) {
  let event = 'message'
  const dataLines = []
  raw.split('\n').forEach((line) => {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  })
  if (!dataLines.length) {
    return
  }
  let payload
  try {
    payload = JSON.parse(dataLines.join('\n'))
  } catch (e) {
    return
  }
  if (event === 'delta' && handlers.onDelta) {
    handlers.onDelta(payload.t || '')
  } else if (event === 'references' && handlers.onReferences) {
    handlers.onReferences(payload)
  } else if (event === 'done' && handlers.onDone) {
    handlers.onDone(payload)
  }
}

/**
 * 流式问答。
 * 用 fetch + ReadableStream 而不是 EventSource —— EventSource 只能发 GET，带不了请求体。
 */
export async function streamAsk(payload, handlers = {}, signal) {
  const user = useUserStore()
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + user.token
    },
    body: JSON.stringify(payload),
    signal
  })
  if (!response.ok || !response.body) {
    throw new Error('HTTP ' + response.status)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  for (;;) {
    const chunk = await reader.read()
    if (chunk.done) {
      break
    }
    buffer += decoder.decode(chunk.value, { stream: true })
    let index
    while ((index = buffer.indexOf('\n\n')) >= 0) {
      const raw = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      dispatchEvent(raw, handlers)
    }
  }
}
