<template>
  <div v-if="thinking" class="thinking-block">
    <div class="thinking-header" @click="expanded = !expanded">
      <BulbOutlined class="thinking-icon" />
      <span class="thinking-title">{{ streaming ? '正在深度思考...' : '已深度思考（点击展开）' }}</span>
      <CaretRightOutlined :class="['thinking-caret', { expanded }]" />
    </div>
    <div v-show="expanded" class="thinking-content">{{ thinking }}</div>
  </div>
</template>

<script setup>
// 深度思考折叠区块：默认收起，与正文回复视觉区分，想看时展开
import { ref } from 'vue'
import { BulbOutlined, CaretRightOutlined } from '@ant-design/icons-vue'

defineProps({
  thinking: {
    type: String,
    default: ''
  },
  streaming: {
    type: Boolean,
    default: false
  }
})

const expanded = ref(false)
</script>

<style scoped>
.thinking-block {
  margin-bottom: 8px;
  border: 1px dashed #d9d9d9;
  border-radius: 10px;
  background: #fafafa;
  overflow: hidden;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  color: #8c8c8c;
  font-size: 13px;
}

.thinking-header:hover {
  color: #667eea;
}

.thinking-icon {
  font-size: 14px;
}

.thinking-title {
  flex: 1;
}

.thinking-caret {
  font-size: 12px;
  transition: transform 0.2s ease;
}

.thinking-caret.expanded {
  transform: rotate(90deg);
}

.thinking-content {
  padding: 8px 12px 12px;
  font-size: 13px;
  line-height: 1.7;
  color: #8c8c8c;
  white-space: pre-wrap;
  word-break: break-word;
  border-top: 1px dashed #ececec;
  max-height: 260px;
  overflow-y: auto;
}
</style>
