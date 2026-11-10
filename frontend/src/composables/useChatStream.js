import { reactive, ref } from 'vue'
import { chatApi } from '../api'

/**
 * 流式对话组合式函数：把 SSE 事件流折叠为响应式状态，供三个对话视图复用。
 *
 * 用法：视图创建空的助手消息对象传入 target，事件增量直接写入该对象
 * （content 正文 / thinking 思考 / tools 工具过程），实现"先建空消息、增量追加"的打字机模式。
 * 完成后通过返回值拿到 done 元数据；流内 error 事件统一转换为异常抛出，
 * 视图只需一个 catch 分支处理 HTTP 错误与流内错误。
 *
 * 响应性注意：视图把消息对象 push 进响应式数组后，局部引用仍是原始对象，
 * 直接在其上增量写入不会触发视图更新。本函数内部先 reactive() 化再写入
 * （Vue 对同一对象返回同一代理），保证每个增量都能驱动渲染。
 */
export function useChatStream() {
  const isStreaming = ref(false)
  // 最近一次流式错误（HTTP 错误或流内 error 事件的中文提示）
  const streamingError = ref(null)
  let abortController = null

  /**
   * 发起流式对话
   *
   * @param {object} params 发送参数：sessionId / message / knowledgeBaseId / enableMCP / agentId / fileIds
   * @param {object} target 绑定的助手消息对象（content/thinking/tools/streaming 增量写入）
   * @param {object} callbacks 可选回调：onDone(完成元数据)、onUpdate(每帧刷入后触发，用于滚动)
   * @returns {Promise<object|null>} done 元数据；用户中断时返回 null
   */
  async function start(params, target, callbacks = {}) {
    if (isStreaming.value) return null

    isStreaming.value = true
    streamingError.value = null
    abortController = new AbortController()

    // 经 reactive 化后再写入，增量修改才会触发视图更新
    const targetState = target ? reactive(target) : null

    // 先建空助手消息，增量追加
    if (targetState) {
      targetState.content = ''
      targetState.thinking = ''
      targetState.tools = []
      targetState.streaming = true
    }

    // 渲染节流：模型增量是逐 token 的细粒度事件，逐条刷入会导致每 token 重渲染一次；
    // 先入缓冲，按动画帧合并刷入，保证流式观感的同时避免渲染风暴
    const pending = { content: '', thinking: '' }
    let flushScheduled = false
    const flush = () => {
      flushScheduled = false
      if (!targetState) return
      if (pending.content) {
        targetState.content += pending.content
        pending.content = ''
      }
      if (pending.thinking) {
        targetState.thinking += pending.thinking
        pending.thinking = ''
      }
      callbacks.onUpdate?.()
    }
    const scheduleFlush = () => {
      if (flushScheduled) return
      flushScheduled = true
      requestAnimationFrame(flush)
    }

    // 单条事件折叠进绑定对象（delta/thinking 入缓冲待刷，结构化事件立即生效）
    const handleEvent = (event) => {
      switch (event.type) {
        case 'delta':
          if (targetState && event.content) {
            pending.content += event.content
            scheduleFlush()
          }
          break
        case 'thinking':
          // 思考增量与正文视觉区分，由 ThinkingBlock 展示
          if (targetState && event.content) {
            pending.thinking += event.content
            scheduleFlush()
          }
          break
        case 'tool_call':
          if (targetState) {
            targetState.tools = targetState.tools || []
            targetState.tools.push({ tool: event.tool, arguments: event.arguments, status: 'running' })
            callbacks.onUpdate?.()
          }
          break
        case 'tool_result': {
          if (targetState) {
            targetState.tools = targetState.tools || []
            // 结果回填到最近一次同名调用（未结束时），否则独立记录
            const step = [...targetState.tools].reverse()
              .find(t => t.tool === event.tool && t.status === 'running')
            if (step) {
              step.result = event.result
              step.status = 'done'
            } else {
              targetState.tools.push({ tool: event.tool, result: event.result, status: 'done' })
            }
            callbacks.onUpdate?.()
          }
          break
        }
        case 'done': {
          const meta = {
            sessionId: event.sessionId,
            conversationRound: event.conversationRound,
            welcomeMessage: event.welcomeMessage,
            agentId: event.agentId
          }
          callbacks.onDone?.(meta)
          return meta
        }
        case 'error':
          // 错误由 start 统一抛出
          break
        default:
          // 未知事件类型忽略，保证协议向后兼容
          break
      }
      return null
    }

    let streamError = null
    let doneMeta = null

    try {
      await chatApi.sendMessageStream(params, {
        signal: abortController.signal,
        onEvent: (event) => {
          const meta = handleEvent(event)
          if (event.type === 'error') {
            streamError = event.error || '回复生成失败，请稍后重试'
          } else if (event.type === 'done') {
            doneMeta = meta
          }
        }
      })

      // 流结束后把未刷出的缓冲一次性落盘
      if (flushScheduled) flush()

      // 流内 error 事件统一抛出，与 HTTP 错误共用视图的 catch 分支
      if (streamError) {
        streamingError.value = streamError
        throw new Error(streamError)
      }
      return doneMeta
    } catch (error) {
      // 用户主动停止：把已缓冲的增量刷出来，保留中断时刻的内容，不视为错误
      if (error.name === 'AbortError') {
        if (flushScheduled) flush()
        return null
      }
      // 网络层异常（fetch 失败等）给出统一中文提示
      if (/failed to fetch|network/i.test(error.message || '')) {
        streamingError.value = '网络连接异常，请稍后重试'
        throw new Error(streamingError.value)
      }
      streamingError.value = error.message || '发送消息时出现错误，请稍后重试'
      throw error
    } finally {
      isStreaming.value = false
      if (targetState) targetState.streaming = false
      abortController = null
    }
  }

  /**
   * 停止生成：中断请求，已生成的部分内容保留在页面上
   */
  function stop() {
    abortController?.abort()
  }

  return {
    isStreaming,
    streamingError,
    start,
    stop
  }
}
