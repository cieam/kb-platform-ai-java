import MarkdownIt from 'markdown-it'

// html: false —— 不渲染原始 HTML，避免模型输出里的标签被执行
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

export function renderMarkdown(text) {
  return md.render(text || '')
}
