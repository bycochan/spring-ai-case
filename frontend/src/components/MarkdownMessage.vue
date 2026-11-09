<template>
  <MarkdownRenderer
    :markdown="content"
    :enable-animate="streaming"
    :enable-latex="true"
    :allow-html="false"
    :code-max-height="'420px'"
    :code-x-render="codeXRender"
    class="markdown-message"
  />
</template>

<script setup>
// 统一的 Markdown 渲染组件：三个对话视图共用，保证样式与行为一致。
// GFM 渲染 + 代码高亮（shiki，代码块头部内置复制按钮）+ LaTeX 公式 + Mermaid/ECharts 图表（经 codeXRender 接管）；
// 默认不解析原始 HTML。streaming 为 true 时启用流式动画（增量淡入）。
import { provide, toRef } from 'vue'
import { MarkdownRenderer } from 'x-markdown-vue'
import 'x-markdown-vue/style'
import 'katex/dist/katex.min.css'
import MermaidBlock from './MermaidBlock.vue'
import EchartsBlock from './EchartsBlock.vue'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  streaming: {
    type: Boolean,
    default: false
  }
})

// 向 codeXRender 自定义代码块组件透传"是否流式生成中"（x-markdown-vue 的 code 块 props 不含该字段）
provide('chatStreaming', toRef(props, 'streaming'))

// 接管 mermaid 语言块：内置 mermaid 组件渲染失败时只输出空白且无降级提示，
// 改用自带容错与降级的 MermaidBlock（自定义渲染优先于内置分支）
// 接管 echarts 语言块：内容为纯 JSON option 的图表，交由 EchartsBlock 流式安全渲染
const codeXRender = { mermaid: MermaidBlock, echarts: EchartsBlock }
</script>

<style scoped>
.markdown-message {
  font-size: inherit;
  line-height: inherit;
  word-break: break-word;
}
</style>
