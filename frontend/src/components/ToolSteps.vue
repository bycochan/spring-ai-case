<template>
  <div v-if="tools && tools.length" class="tool-steps">
    <div v-for="(step, index) in tools" :key="index" class="tool-step">
      <div class="tool-step-header">
        <LoadingOutlined v-if="step.status === 'running'" spin class="tool-icon" />
        <ToolOutlined v-else class="tool-icon done" />
        <span class="tool-name">
          {{ step.status === 'running' ? `正在调用工具 ${step.tool}...` : step.tool }}
        </span>
      </div>
      <div v-if="step.status === 'done' && step.result !== undefined" class="tool-result">
        {{ formatResult(step.result) }}
      </div>
    </div>
  </div>
</template>

<script setup>
// MCP 工具调用过程展示：正在调用某工具 / 工具结果，让等待过程可理解
import { LoadingOutlined, ToolOutlined } from '@ant-design/icons-vue'

defineProps({
  tools: {
    type: Array,
    default: () => []
  }
})

// 结果统一以 JSON 文本展示，过长时截断
const formatResult = (result) => {
  const text = typeof result === 'string' ? result : JSON.stringify(result)
  return text.length > 400 ? text.slice(0, 400) + '…' : text
}
</script>

<style scoped>
.tool-steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.tool-step {
  border: 1px solid #e6e8f5;
  border-radius: 10px;
  background: #f7f8ff;
  padding: 8px 12px;
}

.tool-step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #4a4a6a;
}

.tool-icon {
  color: #667eea;
}

.tool-icon.done {
  color: #52c41a;
}

.tool-name {
  font-weight: 500;
}

.tool-result {
  margin-top: 6px;
  padding: 6px 8px;
  background: white;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #666;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 160px;
  overflow-y: auto;
}
</style>
