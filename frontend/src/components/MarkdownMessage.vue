<template>
  <MarkdownRenderer
    :markdown="content"
    :enable-animate="streaming"
    :enable-latex="true"
    :enable-mermaid="true"
    :allow-html="false"
    :code-max-height="'420px'"
    :code-x-render="codeXRender"
    class="markdown-message"
  >
    <!-- GFM 表格经 #table 插槽拦截：提取 thead/tbody 数据渲染为 a-table（官方 el-table 插槽方案） -->
    <template #table="{ children }">
      <MarkdownTable :nodes="children" />
    </template>
  </MarkdownRenderer>
</template>

<script setup>
// 统一的 Markdown 渲染组件：三个对话视图共用，保证样式与行为一致。
// GFM 渲染 + 代码高亮（shiki，代码块头部内置复制按钮）+ LaTeX 公式。
// Mermaid 走 x-markdown-vue 内置渲染（官方推荐路径：enable-mermaid，内部自带串行渲染队列、
// suppressErrorRendering 失败抑制与容器渲染，工具栏支持图表/源码切换）；
// GFM 表格经 #table 插槽拦截，提取数据渲染为 a-table（官方 el-table 插槽方案的 Ant Design Vue 实现）；
// ECharts 图表经 codeXRender 接管：内容为纯 JSON option，交由 EchartsBlock 流式安全渲染。
// 默认不解析原始 HTML。streaming 为 true 时启用流式动画（增量淡入）。
import { provide, toRef } from 'vue'
import { MarkdownRenderer } from 'x-markdown-vue'
import 'x-markdown-vue/style'
import 'katex/dist/katex.min.css'
import MarkdownTable from './MarkdownTable.vue'
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

// 仅接管 echarts 语言块；mermaid 保持官方内置渲染（enable-mermaid）。此前以 codeXRender
// 接管 mermaid 需自行实现 initialize/render 全流程，真实会话中仍出现渲染失败，故回退官方路径
const codeXRender = { echarts: EchartsBlock }
</script>

<style scoped>
.markdown-message {
  font-size: inherit;
  line-height: inherit;
  word-break: break-word;
}
</style>
