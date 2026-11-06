<template>
  <div class="agent-chat-view">
    <!-- 顶部工具栏 -->
    <div class="chat-toolbar">
      <div class="toolbar-left">
        <a-button type="text" @click="backToHome" class="back-btn">
          <template #icon><ArrowLeftOutlined /></template>
          返回主页
        </a-button>
        <a-divider type="vertical" style="border-color: rgba(255,255,255,0.3); height: 24px;" />
        <div class="agent-info" v-if="currentAgent">
          <span class="agent-avatar">{{ currentAgent.avatar }}</span>
          <div class="agent-details">
            <h2 class="agent-name">{{ currentAgent.name }}</h2>
            <p class="agent-desc">{{ currentAgent.description }}</p>
          </div>
        </div>
      </div>
      <div class="toolbar-right">
        <a-button @click="showHistoryDrawer = true" style="margin-right: 12px">
          <template #icon><HistoryOutlined /></template>
          历史记录
        </a-button>
        <a-button type="primary" @click="newConversation">
          <template #icon><PlusOutlined /></template>
          新对话
        </a-button>
      </div>
    </div>

    <!-- 对话内容区 -->
    <div class="chat-content">
      <div class="messages-container" ref="messagesContainer">
        <!-- 欢迎消息 -->
        <a-empty
          v-if="messages.length === 0"
          description=""
          style="margin-top: 100px"
        >
          <template #image>
            <div class="welcome-avatar">{{ currentAgent?.avatar || '🤖' }}</div>
          </template>
          <template #description>
            <div class="welcome-message">
              <h3>{{ currentAgent?.welcomeMessage || '你好！我是你的 AI 助手，有什么可以帮你的吗？' }}</h3>
              <div class="agent-capabilities" v-if="currentAgent">
                <a-tag v-if="currentAgent.enableMCP" color="green">
                  <ThunderboltOutlined /> MCP 工具
                </a-tag>
                <a-tag v-if="currentAgent.enableImageUpload" color="orange">
                  <PaperClipOutlined /> 附件对话
                </a-tag>
                <a-tag v-if="currentAgent.knowledgeBaseId" color="purple">
                  <BookOutlined /> 知识库支持
                </a-tag>
              </div>
            </div>
          </template>
        </a-empty>

        <!-- 消息列表 -->
        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message-item', msg.role === 'user' ? 'user' : 'assistant']"
        >
          <a-avatar :size="48" class="message-avatar">
            {{ msg.role === 'user' ? '👤' : currentAgent?.avatar || '🤖' }}
          </a-avatar>
          <div class="message-bubble">
            <div class="message-attachments" v-if="msg.attachments && msg.attachments.length">
              <a-image-preview-group>
                <a-image
                  v-for="att in imageAttachments(msg)"
                  :key="att.fileId"
                  :src="attachmentApi.attachmentUrl(att.fileId)"
                  :fallback="IMAGE_FALLBACK"
                  :width="120"
                  :height="90"
                  class="message-image"
                />
              </a-image-preview-group>
              <a
                v-for="att in documentAttachments(msg)"
                :key="att.fileId"
                class="message-doc-chip"
                :href="attachmentApi.attachmentUrl(att.fileId)"
                target="_blank"
                rel="noopener"
              >
                <FileTextOutlined />
                <span class="doc-chip-name">{{ att.fileName }}</span>
              </a>
            </div>
            <div class="message-text" v-if="msg.content">{{ msg.content }}</div>
            <div class="message-time">
              <ClockCircleOutlined />
              {{ formatTime(msg.timestamp) }}
            </div>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="isLoading" class="message-item assistant">
          <a-avatar :size="48" class="message-avatar">{{ currentAgent?.avatar || '🤖' }}</a-avatar>
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

      <!-- 输入区 -->
      <div class="input-area">
        <!-- 已上传附件暂存区（图片缩略图 / 文档文件名，一行展示） -->
        <div class="pending-images" v-if="attachmentEnabled && pendingAttachments.length > 0">
          <div v-for="att in pendingAttachments" :key="att.key" class="pending-image-item">
            <div class="pending-image-thumb" :title="att.fileName">
              <a-spin v-if="att.uploading" size="small" />
              <a-image
                v-else-if="att.isImage"
                :src="att.url"
                :fallback="IMAGE_FALLBACK"
                :width="72"
                :height="72"
                class="pending-image-img"
              />
              <div v-else class="pending-doc-chip">
                <FileTextOutlined class="doc-chip-icon" />
                <span class="doc-chip-name">{{ att.fileName }}</span>
              </div>
            </div>
            <a-button
              v-if="!att.uploading"
              size="small"
              shape="circle"
              danger
              class="pending-image-remove"
              @click="removeAttachment(att)"
            >
              <template #icon><CloseOutlined /></template>
            </a-button>
          </div>
        </div>
        <a-textarea
          v-model:value="inputMessage"
          @pressEnter="handleEnter"
          @paste="handlePaste"
          :disabled="isLoading"
          :placeholder="inputPlaceholder"
          :rows="3"
          class="message-input"
          :maxlength="2000"
        />
        <div class="input-buttons">
          <a-upload
            v-if="attachmentEnabled"
            :accept="UPLOAD_ACCEPT"
            :show-upload-list="false"
            :before-upload="handleUploadSelect"
            :disabled="isLoading || pendingAttachments.length >= MAX_ATTACHMENT_COUNT"
          >
            <a-tooltip title="上传附件（图片/PDF/Word/TXT，单个 ≤10MB，最多 5 个）">
              <a-button size="large" class="upload-button">
                <template #icon><PaperClipOutlined /></template>
              </a-button>
            </a-tooltip>
          </a-upload>
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
    </div>

    <!-- 历史记录抽屉 -->
    <a-drawer
      v-model:open="showHistoryDrawer"
      title="历史对话记录"
      placement="right"
      width="400"
    >
      <a-timeline>
        <a-timeline-item
          v-for="(msg, index) in messages"
          :key="index"
          :color="msg.role === 'user' ? '#667eea' : '#52c41a'"
        >
          <template #dot>
            <span class="timeline-icon">{{ msg.role === 'user' ? '👤' : currentAgent?.avatar || '🤖' }}</span>
          </template>
          <div class="history-message">
            <div class="history-header">
              <span class="history-role">{{ msg.role === 'user' ? '我' : currentAgent?.name || 'AI' }}</span>
              <span class="history-time">{{ formatTime(msg.timestamp) }}</span>
            </div>
            <div class="history-images" v-if="msg.attachments && msg.attachments.length">
              <img
                v-for="att in imageAttachments(msg)"
                :key="att.fileId"
                :src="attachmentApi.attachmentUrl(att.fileId)"
                class="history-image"
              />
              <a
                v-for="att in documentAttachments(msg)"
                :key="att.fileId"
                class="message-doc-chip context-doc-chip"
                :href="attachmentApi.attachmentUrl(att.fileId)"
                target="_blank"
                rel="noopener"
              >
                <FileTextOutlined />
                <span class="doc-chip-name">{{ att.fileName }}</span>
              </a>
            </div>
            <div class="history-content" v-if="msg.content">{{ msg.content }}</div>
          </div>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-if="messages.length === 0" description="暂无历史记录" />
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined,
  PlusOutlined,
  SendOutlined,
  ClockCircleOutlined,
  LoadingOutlined,
  HistoryOutlined,
  ThunderboltOutlined,
  PaperClipOutlined,
  FileTextOutlined,
  CloseOutlined,
  BookOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { chatApi, agentApi, attachmentApi } from '../api'
import { useAttachmentUpload, MAX_ATTACHMENT_COUNT, UPLOAD_ACCEPT, IMAGE_FALLBACK } from '../composables/useAttachmentUpload'

const route = useRoute()
const router = useRouter()

const currentAgent = ref(null)
const messages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const sessionId = ref(null)
const messagesContainer = ref(null)
const showHistoryDrawer = ref(false)

// 附件上传能力：跟随 Agent 的功能配置
const attachmentEnabled = computed(() => !!currentAgent.value?.enableImageUpload)

const {
  pendingAttachments,
  uploadingCount,
  hasReadyAttachments,
  removeAttachment,
  handlePaste,
  handleUploadSelect,
  getFileIds,
  restoreAttachments,
  clear: clearAttachments
} = useAttachmentUpload(attachmentEnabled)

// 气泡/历史抽屉中的附件渲染：图片走缩略图，文档走文件名链接
const imageAttachments = (msg) => (msg.attachments || []).filter(att => att.isImage)
const documentAttachments = (msg) => (msg.attachments || []).filter(att => !att.isImage)

// 输入框占位文案
const inputPlaceholder = computed(() =>
  attachmentEnabled.value
    ? '输入消息... (Enter 发送, Shift+Enter 换行，可直接粘贴图片)'
    : '输入消息... (Enter 发送, Shift+Enter 换行)'
)

// 发送按钮禁用条件：加载中/附件上传中；无文字且无可发送附件
const sendDisabled = computed(() => {
  if (isLoading.value || uploadingCount.value > 0) return true
  const hasText = inputMessage.value.trim().length > 0
  return !(hasText || hasReadyAttachments.value)
})

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
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
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
    message.warning('附件上传中，请稍候')
    return
  }

  const userMessage = inputMessage.value.trim()
  const fileIds = getFileIds()
  const userMsgIndex = messages.value.length

  // 发送前保存暂存条目快照：用于气泡展示与发送失败后的恢复
  const savedAttachments = pendingAttachments.value.map(item => ({ ...item }))

  // 清空输入框（立即清空）
  inputMessage.value = ''

  // 添加用户消息（含附件）
  messages.value.push({
    role: 'user',
    content: userMessage,
    attachments: savedAttachments.filter(item => item.fileId),
    timestamp: Date.now()
  })

  // 清空附件暂存区
  clearAttachments()

  // 滚动到底部
  await nextTick()
  scrollToBottom()

  isLoading.value = true

  try {
    const response = await chatApi.sendMessage(
      sessionId.value,
      userMessage,
      currentAgent.value?.knowledgeBaseId || null,
      currentAgent.value?.enableMCP || false,
      currentAgent.value?.id || 'default',
      fileIds
    )

    const data = response.data

    // 更新会话ID
    if (data.sessionId) {
      sessionId.value = data.sessionId
    }

    // 添加助手回复
    const aiMessage = data.message || data.response || data.content || '没有收到回复内容'

    messages.value.push({
      role: 'assistant',
      content: aiMessage,
      timestamp: Date.now()
    })

    // 等待 DOM 更新后滚动
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('发送消息失败:', error)
    // 撤回未送达的用户消息，恢复输入与附件便于重试
    messages.value.splice(userMsgIndex, 1)
    inputMessage.value = userMessage
    restoreAttachments(savedAttachments)
    messages.value.push({
      role: 'assistant',
      content: '抱歉，发送消息时出现错误: ' + (error.response?.data?.error || error.message),
      timestamp: Date.now()
    })
    await nextTick()
    scrollToBottom()
  } finally {
    isLoading.value = false
  }
}

// 新建对话
const newConversation = () => {
  sessionId.value = generateSessionId()
  messages.value = []
  message.success('已开启新对话')
}

// 返回主页
const backToHome = () => {
  router.push('/')
}

// 加载 Agent 信息
const loadAgent = async (agentId) => {
  try {
    const response = await agentApi.getAgentById(agentId)
    currentAgent.value = response.data
  } catch (error) {
    console.error('加载 Agent 失败:', error)
    message.error('加载 Agent 信息失败')
  }
}

onMounted(async () => {
  sessionId.value = generateSessionId()
  
  const agentId = route.query.agentId
  if (agentId) {
    await loadAgent(agentId)
  } else {
    message.warning('未指定 Agent')
    router.push('/')
  }
})
</script>

<style scoped>
.agent-chat-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: linear-gradient(180deg, #f8f9fe 0%, #ffffff 100%);
}

.chat-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 60px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 0 0 24px 24px;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.15);
  position: relative;
  z-index: 10;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  color: white;
  font-size: 14px;
}

.back-btn:hover {
  color: white;
  background: rgba(255, 255, 255, 0.1);
}

.agent-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-avatar {
  font-size: 40px;
  line-height: 1;
}

.agent-details {
  display: flex;
  flex-direction: column;
}

.agent-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: white;
  line-height: 1.2;
}

.agent-desc {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.2;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.toolbar-right :deep(.ant-btn) {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  backdrop-filter: blur(10px);
}

.toolbar-right :deep(.ant-btn:hover) {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.4);
  color: white;
}

.toolbar-right :deep(.ant-btn-primary) {
  background: white;
  border-color: white;
  color: #667eea;
}

.toolbar-right :deep(.ant-btn-primary:hover) {
  background: rgba(255, 255, 255, 0.9);
  border-color: rgba(255, 255, 255, 0.9);
  color: #667eea;
}

.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0 60px 32px;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 32px 0;
  background: transparent;
  border-radius: 0;
  margin-bottom: 20px;
  box-shadow: none;
}

.messages-container::-webkit-scrollbar {
  width: 6px;
}

.messages-container::-webkit-scrollbar-track {
  background: transparent;
}

.messages-container::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
}

.messages-container::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.25);
}

.welcome-avatar {
  font-size: 96px;
  line-height: 1;
  margin-bottom: 20px;
  filter: drop-shadow(0 4px 16px rgba(102, 126, 234, 0.2));
}

.welcome-message h3 {
  font-size: 26px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 16px 0;
  letter-spacing: -0.3px;
}

.agent-capabilities {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.message-item {
  display: flex;
  gap: 18px;
  margin-bottom: 32px;
  padding: 0 40px;
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
  padding: 16px 20px;
  border-radius: 20px 20px 20px 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.7;
  font-size: 15px;
  position: relative;
  border: 1px solid rgba(0, 0, 0, 0.05);
  color: #1a1a1a;
}

.message-item.user .message-bubble {
  align-items: flex-end;
}

.message-item.user .message-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 20px 20px 4px 20px;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.25);
}

.message-time {
  font-size: 13px;
  color: #999;
  margin-top: 8px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.input-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: white;
  padding: 20px 24px;
  border-radius: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.input-buttons {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  align-items: center;
}

.input-buttons :deep(span.ant-upload) {
  margin-right: auto;
}

.upload-button {
  border-radius: 16px;
  font-size: 20px;
  padding: 0 18px;
  height: 44px;
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

/* 历史抽屉图片缩略图 */
.history-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.history-image {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

/* 暂存区中的文档附件 */
.pending-doc-chip {
  width: 72px;
  height: 72px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: #fafafa;
}

.pending-doc-chip .doc-chip-icon {
  font-size: 20px;
  color: #667eea;
}

/* 消息气泡/历史抽屉中的文档附件 */
.message-doc-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 220px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  background: white;
  color: #333;
  font-size: 13px;
  text-decoration: none;
}

.message-doc-chip:hover {
  border-color: #667eea;
  color: #667eea;
}

.doc-chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-doc-chip {
  padding: 4px 8px;
  font-size: 12px;
}

.message-input {
  flex: 1;
  resize: none;
  border-radius: 12px;
  font-size: 15px;
  border: 1.5px solid #e8e8e8;
  transition: all 0.3s ease;
}

.message-input:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.send-button {
  height: auto;
  border-radius: 16px;
  padding: 0 32px;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.send-button:hover:not(:disabled) {
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.4);
  transform: translateY(-1px);
}

.send-button:active:not(:disabled) {
  transform: translateY(0);
}

/* 历史记录样式 */
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

.history-message {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.history-role {
  font-weight: 600;
  font-size: 14px;
  color: #333;
}

.history-time {
  font-size: 12px;
  color: #999;
}

.history-content {
  font-size: 13px;
  color: #666;
  line-height: 1.6;
  word-break: break-word;
}
</style>
