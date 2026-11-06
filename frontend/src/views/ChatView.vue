<template>
  <div class="chat-view">
    <!-- 顶部操作栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">💬 多轮对话</h2>
      </div>
      <div class="toolbar-right">
        <a-space :size="12">
          <a-tooltip title="启用 MCP 工具后，Agent 可以调用已注册的 MCP 工具">
            <div class="mcp-toggle">
              <a-switch v-model:checked="enableMCP" />
              <span class="mcp-label">启用 MCP</span>
            </div>
          </a-tooltip>
          <a-button @click="showAgentModal = true" type="default" size="large">
            <template #icon><UserOutlined /></template>
            新建对话
          </a-button>
          <a-button @click="showContextDrawer = !showContextDrawer" type="default" size="large">
            <template #icon><HistoryOutlined /></template>
            {{ showContextDrawer ? '隐藏上下文' : '查看上下文' }}
          </a-button>
          <a-popconfirm
            title="确定要清空当前会话历史吗？"
            ok-text="确定"
            cancel-text="取消"
            @confirm="clearHistory"
          >
            <a-button danger type="primary" size="large">
              <template #icon><DeleteOutlined /></template>
              清空历史
            </a-button>
          </a-popconfirm>
        </a-space>
      </div>
    </div>

    <!-- 主内容区域 -->
    <div class="content-wrapper">
      <!-- 对话卡片 -->
      <a-card class="chat-card" :bordered="false" :class="{ 'with-context': showContextDrawer }">
        <div class="messages-container" ref="messagesContainer">
          <a-empty v-if="messages.length === 0" description="开始新的对话吧！">
            <template #image>
              <div class="empty-icon">💭</div>
            </template>
          </a-empty>

          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['message-item', msg.role === 'user' ? 'user' : 'assistant']"
          >
            <a-avatar :size="40" class="message-avatar">
              {{ msg.role === 'user' ? '👤' : '🤖' }}
            </a-avatar>
            <div class="message-bubble">
              <div class="message-images" v-if="msg.images && msg.images.length">
                <a-image-preview-group>
                  <a-image
                    v-for="(fileId, imgIndex) in msg.images"
                    :key="imgIndex"
                    :src="attachmentApi.attachmentUrl(fileId)"
                    :fallback="IMAGE_FALLBACK"
                    :width="120"
                    :height="90"
                    class="message-image"
                  />
                </a-image-preview-group>
              </div>
              <div class="message-text" v-if="msg.content">{{ msg.content }}</div>
              <div class="message-time">
                <ClockCircleOutlined />
                {{ formatTime(msg.timestamp) }}
              </div>
            </div>
          </div>

          <div v-if="isLoading" class="message-item assistant">
            <a-avatar :size="40" class="message-avatar">🤖</a-avatar>
            <div class="message-bubble">
              <a-spin>
                <template #indicator>
                  <LoadingOutlined style="font-size: 24px" spin />
                </template>
              </a-spin>
              <span style="margin-left: 12px">思考中...</span>
            </div>
          </div>
        </div>

        <div class="input-area">
          <!-- 已上传图片暂存区（一行展示，点击可预览） -->
          <div class="pending-images" v-if="imageUploadEnabled && pendingImages.length > 0">
            <div v-for="img in pendingImages" :key="img.key" class="pending-image-item">
              <div class="pending-image-thumb">
                <a-spin v-if="img.uploading" size="small" />
                <a-image
                  v-else
                  :src="img.url"
                  :fallback="IMAGE_FALLBACK"
                  :width="72"
                  :height="72"
                  class="pending-image-img"
                />
              </div>
              <a-button
                v-if="!img.uploading"
                size="small"
                shape="circle"
                danger
                class="pending-image-remove"
                @click="removeImage(img)"
              >
                <template #icon><CloseOutlined /></template>
              </a-button>
            </div>
          </div>
          <div class="input-row">
            <a-upload
              v-if="imageUploadEnabled"
              accept="image/jpeg,image/png,image/gif,image/webp"
              :show-upload-list="false"
              :before-upload="handleUploadSelect"
              :disabled="isLoading || pendingImages.length >= MAX_IMAGE_COUNT"
            >
              <a-tooltip title="上传图片（单张 ≤10MB，最多 5 张，支持直接粘贴）">
                <a-button size="large" class="upload-button">
                  <template #icon><PictureOutlined /></template>
                </a-button>
              </a-tooltip>
            </a-upload>
            <a-textarea
              v-model:value="inputMessage"
              @pressEnter="handleEnter"
              @paste="handlePaste"
              :placeholder="inputPlaceholder"
              :rows="3"
              :disabled="isLoading"
              class="message-input"
            />
            <a-button
              type="primary"
              size="large"
              @click="sendMessage"
              :disabled="sendDisabled"
              :loading="isLoading"
              class="send-button"
            >
              <template #icon><SendOutlined /></template>
              {{ isLoading ? '发送中...' : '发送' }}
            </a-button>
          </div>
        </div>
      </a-card>

      <!-- 右侧上下文面板 -->
      <transition name="slide-fade">
        <div v-if="showContextDrawer" class="context-panel">
          <div class="context-header">
            <h3>📋 消息上下文</h3>
            <a-tag color="blue">{{ messages.length }} 条消息</a-tag>
          </div>
          <div class="context-content">
            <a-timeline>
              <a-timeline-item 
                v-for="(msg, index) in messages" 
                :key="index"
                :color="msg.role === 'user' ? 'blue' : 'green'"
              >
                <template #dot>
                  <span class="timeline-icon">{{ msg.role === 'user' ? '👤' : '🤖' }}</span>
                </template>
                <div class="context-message">
                  <div class="context-message-header">
                    <span class="context-role">{{ msg.role === 'user' ? '用户' : 'AI助手' }}</span>
                    <span class="context-time">{{ formatTime(msg.timestamp) }}</span>
                  </div>
                  <div class="context-images" v-if="msg.images && msg.images.length">
                    <img
                      v-for="(fileId, imgIndex) in msg.images"
                      :key="imgIndex"
                      :src="attachmentApi.attachmentUrl(fileId)"
                      class="context-image"
                    />
                  </div>
                  <div class="context-message-content" v-if="msg.content">{{ msg.content }}</div>
                </div>
              </a-timeline-item>
            </a-timeline>
            <a-empty v-if="messages.length === 0" description="暂无消息" />
          </div>
        </div>
      </transition>
    </div>
    
    <!-- Agent 选择模态框 -->
    <a-modal
      v-model:open="showAgentModal"
      title="配置 Agent 开始新对话"
      @ok="handleSelectAgent"
      @cancel="showAgentModal = false"
      width="800px"
      okText="开始对话"
      cancelText="取消"
    >
      <div class="config-section">
        <h4>1. 选择 Agent</h4>
        <div class="agent-list">
          <a-row :gutter="[16, 16]">
            <a-col 
              v-for="agent in agents" 
              :key="agent.id" 
              :span="12"
            >
              <div 
                :class="['agent-card', { 'selected': selectedAgentId === agent.id }]"
                @click="selectedAgentId = agent.id"
              >
                <div class="agent-header">
                  <span class="agent-avatar">{{ agent.avatar }}</span>
                  <div class="agent-info">
                    <div class="agent-name">{{ agent.name }}</div>
                    <a-tag v-if="agent.isDefault" color="blue" size="small">默认</a-tag>
                    <a-tag v-if="agent.enableMCP" color="green" size="small">MCP</a-tag>
                  </div>
                </div>
                <div class="agent-description">{{ agent.description }}</div>
              </div>
            </a-col>
          </a-row>
        </div>

        <a-divider />

        <h4>2. 选择知识库（可选）</h4>
        <a-select
          v-model:value="selectedKnowledgeBaseId"
          placeholder="不选择知识库则使用纯对话模式"
          allow-clear
          style="width: 100%"
        >
          <a-select-option value="">不使用知识库</a-select-option>
          <a-select-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
            📚 {{ kb.name }} ({{ kb.documentCount }} 个文档)
          </a-select-option>
        </a-select>
        <div v-if="selectedKnowledgeBaseId" style="margin-top: 8px">
          <a-alert 
            message="启用知识库后，AI 将基于知识库内容回答问题" 
            type="info" 
            show-icon 
          />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import {
  UserOutlined,
  DeleteOutlined,
  SendOutlined,
  ClockCircleOutlined,
  LoadingOutlined,
  HistoryOutlined,
  PictureOutlined,
  CloseOutlined
} from '@ant-design/icons-vue'
import { chatApi, agentApi, ragApi, attachmentApi } from '../api'
import { message } from 'ant-design-vue'
import { useImageUpload, MAX_IMAGE_COUNT, IMAGE_FALLBACK } from '../composables/useImageUpload'

const route = useRoute()
const messages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const sessionId = ref(null)
const messagesContainer = ref(null)
const enableMCP = ref(false)
const agents = ref([])
const selectedAgentId = ref(null)
const currentAgentId = ref(null)
const showAgentModal = ref(false)
const showContextDrawer = ref(false)
const knowledgeBases = ref([])
const selectedKnowledgeBaseId = ref(null)
const currentKnowledgeBaseId = ref(null)

// 图片上传能力：跟随当前 Agent 的功能配置（未指定时为 default Agent）
const imageUploadEnabled = computed(() => {
  const agentId = currentAgentId.value || 'default'
  const agent = agents.value.find(a => a.id === agentId)
  return !!agent?.enableImageUpload
})

const {
  pendingImages,
  uploadingCount,
  hasReadyImages,
  removeImage,
  handlePaste,
  handleUploadSelect,
  getFileIds,
  restoreImages,
  clear: clearPendingImages
} = useImageUpload(imageUploadEnabled)

// 输入框占位文案
const inputPlaceholder = computed(() =>
  imageUploadEnabled.value
    ? '输入消息... (Enter 发送, Shift+Enter 换行，可直接粘贴图片)'
    : '输入消息... (Enter 发送, Shift+Enter 换行)'
)

// 发送按钮禁用条件：加载中/图片上传中；无文字且无可发送图片
const sendDisabled = computed(() => {
  if (isLoading.value || uploadingCount.value > 0) return true
  const hasText = inputMessage.value.trim().length > 0
  return !(hasText || hasReadyImages.value)
})

// 切换 Agent 后若新 Agent 未开启图片上传，清空暂存区并释放服务端附件
watch(imageUploadEnabled, (enabled) => {
  if (!enabled && pendingImages.value.length > 0) {
    pendingImages.value.forEach(img => {
      if (img.fileId) attachmentApi.deleteAttachment(img.fileId).catch(() => {})
    })
    clearPendingImages()
  }
})

// 加载所有 Agent
const loadAgents = async () => {
  try {
    const response = await agentApi.getAllAgents()
    agents.value = response.data
    // 默认选中第一个 Agent
    if (agents.value.length > 0) {
      selectedAgentId.value = agents.value[0].id
    }
  } catch (error) {
    console.error('加载 Agent 列表失败:', error)
    message.error('加载 Agent 列表失败')
  }
}

// 加载知识库列表
const loadKnowledgeBases = async () => {
  try {
    const response = await ragApi.getAllKnowledgeBases()
    knowledgeBases.value = response.data
  } catch (error) {
    console.error('加载知识库列表失败:', error)
  }
}

// 处理选择 Agent
const handleSelectAgent = () => {
  if (!selectedAgentId.value) {
    message.warning('请选择一个 Agent')
    return
  }
  
  // 创建新对话
  sessionId.value = generateSessionId()
  currentAgentId.value = selectedAgentId.value
  currentKnowledgeBaseId.value = selectedKnowledgeBaseId.value || null
  messages.value = []
  showAgentModal.value = false
  
  // 显示开场白
  const selectedAgent = agents.value.find(a => a.id === selectedAgentId.value)
  if (selectedAgent && selectedAgent.welcomeMessage) {
    messages.value.push({
      role: 'assistant',
      content: selectedAgent.welcomeMessage,
      timestamp: Date.now()
    })
    scrollToBottom()
  }
  
  // 如果 Agent 启用了 MCP，自动开启 MCP 开关
  if (selectedAgent && selectedAgent.enableMCP) {
    enableMCP.value = true
  }
  
  let successMessage = `已切换到 ${selectedAgent.name}`
  if (currentKnowledgeBaseId.value) {
    const selectedKB = knowledgeBases.value.find(kb => kb.id === currentKnowledgeBaseId.value)
    if (selectedKB) {
      successMessage += ` + 知识库: ${selectedKB.name}`
    }
  }
  message.success(successMessage)
}

// 生成会话ID
const generateSessionId = () => {
  return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
}

// 格式化时间
const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 处理 Enter 键
const handleEnter = (e) => {
  if (!e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

// 发送消息
const sendMessage = async () => {
  if (sendDisabled.value) return
  if (uploadingCount.value > 0) {
    message.warning('图片上传中，请稍候')
    return
  }

  const userMessage = inputMessage.value.trim()
  const fileIds = getFileIds()
  const userMsgIndex = messages.value.length

  // 添加用户消息（含图片附件）
  messages.value.push({
    role: 'user',
    content: userMessage,
    images: fileIds,
    timestamp: Date.now()
  })

  // 清空输入框与图片暂存区
  inputMessage.value = ''
  clearPendingImages()

  scrollToBottom()
  isLoading.value = true

  try {
    const response = await chatApi.sendMessage(
      sessionId.value,
      userMessage,
      currentKnowledgeBaseId.value,
      enableMCP.value,
      currentAgentId.value,
      fileIds
    )
    const data = response.data

    // 更新会话ID
    if (data.sessionId) {
      sessionId.value = data.sessionId
    }

    // 更新 Agent ID
    if (data.agentId) {
      currentAgentId.value = data.agentId
    }

    // 添加助手回复
    messages.value.push({
      role: 'assistant',
      content: data.message,
      timestamp: Date.now()
    })

    scrollToBottom()
  } catch (error) {
    console.error('发送消息失败:', error)
    // 撤回未送达的用户消息，恢复输入与附件便于重试
    messages.value.splice(userMsgIndex, 1)
    inputMessage.value = userMessage
    restoreImages(fileIds)
    messages.value.push({
      role: 'assistant',
      content: '抱歉，发送消息时出现错误: ' + (error.response?.data?.error || error.message),
      timestamp: Date.now()
    })
  } finally {
    isLoading.value = false
  }
}

// 新建对话
const newConversation = () => {
  sessionId.value = generateSessionId()
  messages.value = []
}

// 清空历史
const clearHistory = async () => {
  if (!sessionId.value || messages.value.length === 0) return

  try {
    await chatApi.clearSession(sessionId.value)
    messages.value = []
  } catch (error) {
    console.error('清空历史失败:', error)
  }
}

onMounted(async () => {
  await loadAgents()
  await loadKnowledgeBases()
  sessionId.value = generateSessionId()
  
  // 从 URL 参数初始化配置
  const { agentId, knowledgeBaseId, enableMCP: mcpParam } = route.query
  
  if (agentId) {
    // 使用指定的 Agent
    currentAgentId.value = agentId
    const agent = agents.value.find(a => a.id === agentId)
    
    if (agent) {
      // 显示开场白
      if (agent.welcomeMessage) {
        messages.value.push({
          role: 'assistant',
          content: agent.welcomeMessage,
          timestamp: Date.now()
        })
      }
      
      // 设置 MCP
      if (mcpParam === '1' || agent.enableMCP) {
        enableMCP.value = true
      }
      
      // 设置知识库
      if (knowledgeBaseId) {
        currentKnowledgeBaseId.value = knowledgeBaseId
      }
      
      message.success(`欢迎使用 ${agent.name}`)
    }
  } else {
    // 使用默认 Agent
    currentAgentId.value = 'default'
  }
})
</script>

<style scoped>
.chat-view {
  height: 100vh;
  width: 100%;
  display: flex;
  flex-direction: column;
  padding: 0;
  gap: 0;
  overflow: hidden;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 60px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.toolbar-left {
  display: flex;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: white;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.mcp-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  backdrop-filter: blur(10px);
}

.mcp-label {
  font-size: 14px;
  color: white;
  font-weight: 500;
}

.content-wrapper {
  flex: 1;
  display: flex;
  gap: 0;
  min-height: 0;
  background: #f0f2f5;
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  box-shadow: none;
  border-radius: 0;
  overflow: hidden;
  min-height: 0;
  transition: all 0.3s ease;
}

.chat-card.with-context {
  flex: 0 0 65%;
}

.chat-card :deep(.ant-card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
  height: 100%;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 32px 40px;
  background: #f5f7fa;
  min-height: 0;
}

.messages-container::-webkit-scrollbar {
  width: 8px;
}

.messages-container::-webkit-scrollbar-track {
  background: transparent;
}

.messages-container::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 4px;
}

.messages-container::-webkit-scrollbar-thumb:hover {
  background: #b0b0b0;
}

.empty-icon {
  font-size: 96px;
  margin-bottom: 20px;
  opacity: 0.4;
}

.message-item {
  display: flex;
  gap: 18px;
  margin-bottom: 32px;
  animation: fadeIn 0.4s ease;
}

.message-item.user {
  flex-direction: row-reverse;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(15px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-avatar {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  font-size: 26px;
  border: 2px solid #f0f0f0;
}

.message-item.user .message-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-color: transparent;
}

.message-bubble {
  max-width: 75%;
  display: flex;
  flex-direction: column;
}

.message-text {
  background: white;
  padding: 18px 24px;
  border-radius: 18px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.8;
  font-size: 17px;
  position: relative;
}

.message-item.assistant .message-text::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 14px;
  width: 0;
  height: 0;
  border-top: 10px solid transparent;
  border-bottom: 10px solid transparent;
  border-right: 10px solid white;
}

.message-item.user .message-text::before {
  content: '';
  position: absolute;
  right: -10px;
  top: 14px;
  width: 0;
  height: 0;
  border-top: 10px solid transparent;
  border-bottom: 10px solid transparent;
  border-left: 10px solid #667eea;
}

.message-item.user .message-bubble {
  align-items: flex-end;
}

.message-item.user .message-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.message-time {
  font-size: 14px;
  color: #999;
  margin-top: 10px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.input-area {
  flex-shrink: 0;
  padding: 20px 40px 24px;
  background: white;
  border-top: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.input-row {
  display: flex;
  gap: 18px;
  align-items: flex-end;
}

.upload-button {
  height: 56px;
  width: 56px;
  border-radius: 10px;
  font-size: 20px;
}

/* 已上传图片暂存区 */
.pending-images {
  display: flex;
  flex-wrap: nowrap;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.pending-image-item {
  position: relative;
  flex-shrink: 0;
}

.pending-image-thumb {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
}

.pending-image-thumb :deep(.ant-image),
.pending-image-thumb :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.pending-image-remove {
  position: absolute;
  top: -8px;
  right: -8px;
  z-index: 2;
}

/* 消息气泡中的图片 */
.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}

.message-item.user .message-images {
  justify-content: flex-end;
}

.message-images :deep(.ant-image img) {
  object-fit: cover;
  border-radius: 8px;
}

/* 上下文面板图片缩略图 */
.context-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.context-image {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

.message-input {
  flex: 1;
  font-size: 17px;
}

.message-input :deep(textarea) {
  font-size: 17px;
  line-height: 1.6;
  padding: 14px 18px;
}

.send-button {
  height: 56px;
  font-size: 17px;
  padding: 0 36px;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.send-button:hover {
  box-shadow: 0 4px 14px rgba(102, 126, 234, 0.4);
  transform: translateY(-2px);
}

.agent-list {
  max-height: 500px;
  overflow-y: auto;
  padding: 10px;
}

.agent-card {
  padding: 16px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  background: #fafafa;
  height: 100%;
}

.agent-card:hover {
  border-color: #667eea;
  background: #f5f7ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);
}

.agent-card.selected {
  border-color: #667eea;
  background: linear-gradient(135deg, #f5f7ff 0%, #e8ecff 100%);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.25);
}

.agent-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.agent-avatar {
  font-size: 36px;
  line-height: 1;
}

.agent-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.agent-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.agent-description {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}

.config-section h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.config-section .ant-divider {
  margin: 24px 0;
}

/* 上下文面板样式 */
.context-panel {
  flex: 0 0 35%;
  background: white;
  border-radius: 0;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.context-header {
  padding: 20px 24px;
  border-bottom: 1px solid #e8e8e8;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.context-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: white;
}

.context-header .ant-tag {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  font-weight: 500;
}

.context-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.context-content::-webkit-scrollbar {
  width: 6px;
}

.context-content::-webkit-scrollbar-track {
  background: transparent;
}

.context-content::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 3px;
}

.context-content::-webkit-scrollbar-thumb:hover {
  background: #b0b0b0;
}

.timeline-icon {
  font-size: 20px;
  display: inline-block;
  width: 32px;
  height: 32px;
  line-height: 32px;
  text-align: center;
  background: white;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.context-message {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-top: -8px;
}

.context-message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.context-role {
  font-weight: 600;
  font-size: 14px;
  color: #333;
}

.context-time {
  font-size: 12px;
  color: #999;
}

.context-message-content {
  font-size: 13px;
  color: #666;
  line-height: 1.6;
  word-break: break-word;
  max-height: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
}

/* 动画效果 */
.slide-fade-enter-active {
  transition: all 0.3s ease;
}

.slide-fade-leave-active {
  transition: all 0.3s ease;
}

.slide-fade-enter-from {
  transform: translateX(20px);
  opacity: 0;
}

.slide-fade-leave-to {
  transform: translateX(20px);
  opacity: 0;
}
</style>
