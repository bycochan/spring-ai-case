<template>
  <div class="echarts-block">
    <!-- 渲染成功：chartEl 始终保留在 DOM（v-show 显隐），保证 echarts 实例绑定的容器不被分支切换销毁 -->
    <div v-show="rendered" ref="chartEl" class="echarts-block__figure" />
    <!-- 渲染失败：优雅降级为源码展示 -->
    <div v-if="failed" class="echarts-block__fallback">
      <div class="echarts-block__fallback-tip">
        <InfoCircleOutlined />
        <span>图表渲染失败，已回退为源码展示</span>
      </div>
      <pre class="echarts-block__source">{{ source() }}</pre>
    </div>
    <!-- 流式生成中 / 渲染中 -->
    <div v-if="!rendered && !failed" class="echarts-block__loading">
      <LoadingOutlined spin />
      <span>{{ isStreaming ? '图表将在回复完成后渲染' : '图表渲染中...' }}</span>
    </div>
  </div>
</template>

<script setup>
// ECharts 代码块渲染组件：经 x-markdown-vue 的 codeXRender 扩展点接管 echarts 语言块，
// 与 MermaidBlock 同构（流式期间不渲染、失败回退源码、序号守卫防竞态），差异点：
// - 内容为纯 JSON 的 ECharts option，JSON.parse 直出，不做任何 JS 求值（安全基线同"不解析原始 HTML"）
// - echarts 需要真实 DOM 与实例生命周期：nextTick 后 init、卸载时 dispose、容器尺寸变化时 resize
// - 库体积较大，动态 import 控制首包体积
import { inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { InfoCircleOutlined, LoadingOutlined } from '@ant-design/icons-vue'

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

const chartEl = ref(null)
const rendered = ref(false)
const failed = ref(false)
let chart = null
let chartTheme = null
let renderTimer = null
let renderSeq = 0
let resizeObserver = null

const source = () => (props.raw?.content || '').trim()

const theme = () => (props.isDark ? 'dark' : 'default')

function setFailed(reason) {
  rendered.value = false
  failed.value = true
  console.warn('ECharts 图表渲染失败，已回退为源码展示:', reason)
}

async function renderChart() {
  const text = source()
  if (!text) {
    // 空内容：复位为占位态，并推进守卫废弃在途渲染
    renderSeq++
    rendered.value = false
    failed.value = false
    return
  }
  const seq = ++renderSeq
  let option
  try {
    option = JSON.parse(text)
    if (!option || typeof option !== 'object' || Array.isArray(option)) {
      throw new Error('配置必须是 JSON 对象')
    }
  } catch (error) {
    if (seq !== renderSeq) return
    setFailed(error?.message || error)
    return
  }
  // 先切到成功态让容器显示出来：display:none 的容器宽高为 0，直接 init 会得到 0 尺寸画布
  rendered.value = true
  failed.value = false
  await nextTick()
  if (seq !== renderSeq || !chartEl.value) return
  try {
    // echarts 为纯命名导出、无 default，取模块命名空间整体使用
    const echarts = await import('echarts')
    if (seq !== renderSeq) return
    // 主题只在 init 时生效：主题变化需销毁重建
    if (chart && chartTheme !== theme()) {
      chart.dispose()
      chart = null
    }
    if (!chart) {
      chart = echarts.init(chartEl.value, theme())
      chartTheme = theme()
    }
    // notMerge 全量替换：代码块内容更新时不残留上一次的系列配置
    chart.setOption(option, { notMerge: true })
  } catch (error) {
    if (seq !== renderSeq) return
    setFailed(error?.message || error)
  }
}

function scheduleRender() {
  if (renderTimer) clearTimeout(renderTimer)
  // 流式期间不渲染，等待回复完成后再统一渲染
  if (isStreaming.value) {
    rendered.value = false
    failed.value = false
    return
  }
  renderTimer = setTimeout(renderChart, 300)
}

watch(
  () => [source(), isStreaming.value, props.isDark],
  scheduleRender,
  { immediate: true }
)

onMounted(() => {
  // 容器尺寸变化（窗口缩放、侧栏折叠）时自适应重绘
  resizeObserver = new ResizeObserver(() => chart?.resize())
  if (chartEl.value) resizeObserver.observe(chartEl.value)
})

onBeforeUnmount(() => {
  if (renderTimer) clearTimeout(renderTimer)
  renderSeq++
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.echarts-block {
  margin: 12px 0;
  border: 1px solid #e6e8f5;
  border-radius: 10px;
  background: #fcfcff;
  overflow: hidden;
}

.echarts-block__figure {
  width: 100%;
  height: 320px;
}

.echarts-block__loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 12px;
  color: #8c8c8c;
  font-size: 13px;
}

.echarts-block__fallback-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff7e6;
  border-bottom: 1px solid #ffe7ba;
  color: #d48806;
  font-size: 13px;
}

.echarts-block__source {
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
