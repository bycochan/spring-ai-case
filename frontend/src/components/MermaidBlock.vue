<template>
  <div class="mermaid-block">
    <!-- 渲染成功：注入经 DOMPurify 清洗后的 SVG -->
    <div
      v-if="rendered"
      class="mermaid-block__figure"
      v-html="rendered"
    />
    <!-- 渲染失败：优雅降级为源码展示（内置组件失败时只输出空白，故接管） -->
    <div v-else-if="failed" class="mermaid-block__fallback">
      <div class="mermaid-block__fallback-tip">
        <InfoCircleOutlined />
        <span>图表渲染失败，已回退为源码展示</span>
      </div>
      <pre class="mermaid-block__source">{{ source }}</pre>
    </div>
    <!-- 流式生成中 / 渲染中 -->
    <div v-else class="mermaid-block__loading">
      <LoadingOutlined spin />
      <span>{{ isStreaming ? '图表将在回复完成后渲染' : '图表渲染中...' }}</span>
    </div>
  </div>
</template>

<script setup>
// Mermaid 代码块渲染组件：经 x-markdown-vue 的 codeXRender 扩展点接管 mermaid 语言块。
// 内置 mermaid 组件的两个缺陷：渲染失败时仅输出空白 div 且无任何提示（dist index-D74kMJYB.js
// 中 error 分支直接清空内容）；渲染容器未挂载时静默放弃。本组件补齐降级与容错：
// - 流式生成中不渲染：半截语法必然 parse 失败，等回复完成后再统一渲染，避免闪烁
// - 渲染失败回退为源码展示，并给出明确提示
// - SVG 经 DOMPurify 清洗后再注入：mermaid 源码来自模型输出，securityLevel loose 下
//   click/HTML labels 可携带事件属性，直接 innerHTML 存在注入面
import { inject, onBeforeUnmount, ref, watch } from 'vue'
import { InfoCircleOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import DOMPurify from 'dompurify'

// 组件形态由 codeXRender 以「整个 props 对象」传入（含 raw/isDark 等），
// 未声明的属性不向下继承，避免脏 attrs 落到 DOM
defineOptions({ inheritAttrs: false })

const props = defineProps({
  // x-markdown-vue 代码块节点：{ content, language, inline, ... }
  raw: {
    type: Object,
    default: () => ({})
  },
  isDark: {
    type: Boolean,
    default: false
  }
})

// 外层是否流式生成中：MarkdownMessage 经 provide 透传（code 块 props 不含该字段）
const isStreaming = inject('chatStreaming', ref(false))

const rendered = ref('')
const failed = ref(false)
let renderTimer = null
let renderSeq = 0

const source = () => (props.raw?.content || '').trim()

async function renderMermaid() {
  const diagram = source()
  if (!diagram) {
    rendered.value = ''
    failed.value = false
    return
  }
  const seq = ++renderSeq
  try {
    const mermaid = (await import('mermaid')).default
    if (seq !== renderSeq) return
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'loose',
      theme: props.isDark ? 'dark' : 'default'
    })
    await mermaid.parse(diagram)
    const id = `mermaid-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const { svg } = await mermaid.render(id, diagram)
    if (seq !== renderSeq) return
    rendered.value = DOMPurify.sanitize(svg, {
      USE_PROFILES: { html: true, svg: true, svgFilters: true }
    })
    failed.value = false
  } catch (error) {
    if (seq !== renderSeq) return
    console.warn('Mermaid 图表渲染失败，已回退为源码展示:', error?.message || error)
    rendered.value = ''
    failed.value = true
  }
}

function scheduleRender() {
  if (renderTimer) clearTimeout(renderTimer)
  // 流式期间不渲染，等待回复完成后再统一渲染
  if (isStreaming.value) {
    rendered.value = ''
    failed.value = false
    return
  }
  renderTimer = setTimeout(renderMermaid, 300)
}

watch(
  () => [source(), isStreaming.value, props.isDark],
  scheduleRender,
  { immediate: true }
)

onBeforeUnmount(() => {
  if (renderTimer) clearTimeout(renderTimer)
  renderSeq++
})
</script>

<style scoped>
.mermaid-block {
  margin: 12px 0;
  border: 1px solid #e6e8f5;
  border-radius: 10px;
  background: #fcfcff;
  overflow: hidden;
}

.mermaid-block__figure {
  padding: 12px;
  overflow-x: auto;
  display: flex;
  justify-content: center;
}

.mermaid-block__figure :deep(svg) {
  max-width: 100%;
  height: auto;
}

.mermaid-block__loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 12px;
  color: #8c8c8c;
  font-size: 13px;
}

.mermaid-block__fallback-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff7e6;
  border-bottom: 1px solid #ffe7ba;
  color: #d48806;
  font-size: 13px;
}

.mermaid-block__source {
  margin: 0;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #595959;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
}
</style>
