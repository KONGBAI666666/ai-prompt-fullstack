// AI 试运行 API —— fetch + ReadableStream 手动解析 SSE 流
//
// 为什么不用 Axios / EventSource：
// - Axios：浏览器端基于 XMLHttpRequest，会等响应全部接收完才回调，
//   拿不到"边生成边接收"的流式效果
// - EventSource：浏览器原生 SSE 客户端，但只支持 GET 请求、
//   不能带 Authorization 自定义请求头，后端鉴权过不去
// - fetch + ReadableStream：POST + 自定义头 + 流式读取，三样全占
import { getToken } from '@/utils/auth'

/**
 * 流式试运行一条提示词
 *
 * @param {number} promptId 提示词 id
 * @param {string} input 用户的补充输入（可为空）
 * @param {object} handlers 回调集合：
 *   - onChunk(text)：收到一段增量文本（打字机追加渲染）
 *   - onDone()：正常结束
 *   - onError(msg)：出错（含后端业务错误，如未配置、超限）
 * @param {AbortSignal} signal 可选，用于中途停止生成
 */
export async function runAi(promptId, input, { onChunk, onDone, onError }, signal) {
  try {
    const resp = await fetch('/api/ai/run', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: getToken() || '',
      },
      body: JSON.stringify({ promptId, input: input || '' }),
      signal,
    })

    // 后端在建立 SSE 流之前抛的业务异常（未配置 / 超限 / 无权限）
    // 返回的是普通 JSON，Content-Type 不是 text/event-stream
    const contentType = resp.headers.get('Content-Type') || ''
    if (!resp.ok || !contentType.includes('text/event-stream')) {
      let msg = `请求失败（${resp.status}）`
      try {
        const body = await resp.json()
        msg = body.message || msg
      } catch { /* 响应体不是 JSON 就用默认提示 */ }
      onError(msg)
      return
    }

    // —— 流式读取 ——
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    // SSE 协议：事件之间用空行（\n\n）分隔
    // 网络分包可能把一个事件劈成两半，所以用 buffer 攒着，见到 \n\n 才算完整事件
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let sep
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        handleEvent(rawEvent, { onChunk, onDone, onError })
      }
    }
  } catch (e) {
    // AbortError：用户主动点"停止"，不算错误
    if (e.name !== 'AbortError') {
      onError('连接中断：' + e.message)
    }
  }
}

/**
 * 解析一个完整的 SSE 事件块
 * 后端约定每个事件的 data 是单行 JSON：{"t":"c","v":"文本"} / {"t":"done"} / {"t":"error","v":"原因"}
 */
function handleEvent(rawEvent, { onChunk, onDone, onError }) {
  // 事件块里可能有多行，只认 data: 开头的行（event:/id: 等其他字段忽略）
  for (const line of rawEvent.split('\n')) {
    if (!line.startsWith('data:')) continue
    const payload = line.slice(5).trim()
    if (!payload) continue
    try {
      const msg = JSON.parse(payload)
      if (msg.t === 'c' && msg.v) onChunk(msg.v)
      else if (msg.t === 'done') onDone()
      else if (msg.t === 'error') onError(msg.v || '生成失败')
    } catch {
      // 无法解析的行直接忽略，不让单个坏块中断整个流
    }
  }
}
