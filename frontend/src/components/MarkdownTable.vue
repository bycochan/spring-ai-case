<template>
  <div class="markdown-table-wrap">
    <!-- 成功提取表头与数据：a-table 增强渲染（边框/斑马纹/横向滚动） -->
    <a-table
      v-if="table.columns.length"
      class="markdown-table"
      :columns="columns"
      :data-source="table.rows"
      :pagination="false"
      :scroll="{ x: 'max-content' }"
      :row-class-name="(_, index) => (index % 2 === 1 ? 'markdown-table__row--odd' : '')"
      bordered
      size="small"
    />
    <!-- 未提取到表头（异常结构）：回退为原生 Markdown 表格渲染 -->
    <component :is="fallback" v-else />
  </div>
</template>

<script setup>
// Markdown 表格增强组件：配合 x-markdown-vue 的 #table 插槽，拦截 Markdown 中所有 GFM 表格，
// 从插槽暴露的 VNode 子树提取列名（thead/th）与行数据（tbody/tr/td），再用 a-table 渲染，
// 获得边框、斑马纹、横向滚动等完整能力（方案对齐官方"自定义表格（el-table 插槽）"示例，
// 表格组件按项目技术栈换用 Ant Design Vue）。
// 注意：与官方示例一致，单元格仅提取纯文本，行内 Markdown（粗体/链接）不保留格式。
import { computed } from 'vue'

const props = defineProps({
  // #table 插槽暴露的 children：返回原生 table 子树 VNode 数组的函数
  nodes: {
    type: Function,
    default: null
  }
})

// 递归提取 VNode 子树纯文本
function vnodeText(node) {
  if (node == null || node === false || node === true) return ''
  if (typeof node === 'string' || typeof node === 'number') return String(node)
  if (Array.isArray(node)) return node.map(vnodeText).join('')
  if (typeof node === 'object' && 'children' in node) return vnodeText(node.children)
  return ''
}

function findByTag(children, tag) {
  if (!children) return []
  const list = Array.isArray(children) ? children : [children]
  return list.filter((node) => node && typeof node === 'object' && node.type === tag)
}

function extractTableData(children) {
  const columns = []
  const rows = []
  const sections = Array.isArray(children) ? children : [children]
  for (const section of sections) {
    if (!section || typeof section !== 'object') continue
    if (section.type === 'thead') {
      const tr = findByTag(section.children, 'tr')[0]
      findByTag(tr?.children, 'th').forEach((th, i) => {
        const label = vnodeText(th.children).trim()
        // 空表头列（GFM 允许空头行）补默认列名，避免 dataIndex 冲突
        columns.push(label || `列${i + 1}`)
      })
    } else if (section.type === 'tbody') {
      const trs = findByTag(section.children, 'tr')
      trs.forEach((tr) => {
        const tds = findByTag(tr.children, 'td')
        const row = { key: String(rows.length) }
        tds.forEach((td, i) => {
          row[columns[i] ?? String(i)] = vnodeText(td.children)
        })
        rows.push(row)
      })
    }
  }
  return { columns, rows }
}

// 一次提取并缓存，避免模板里重复调用 children()（官方示例每列每行各调用一次）
const table = computed(() => {
  try {
    return extractTableData(props.nodes?.() || [])
  } catch {
    return { columns: [], rows: [] }
  }
})

const columns = computed(() =>
  table.value.columns.map((title) => ({ title, dataIndex: title, key: title }))
)

// 回退渲染：函数式组件原样输出原生表格 VNode
const fallback = () => props.nodes?.()
</script>

<style scoped>
.markdown-table-wrap {
  margin: 12px 0;
}

/* 斑马纹：与官方 el-table 示例的 stripe 对齐 */
.markdown-table :deep(tr.markdown-table__row--odd > td) {
  background: #fafafa;
}
</style>
