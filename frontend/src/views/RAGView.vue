<template>
  <div class="rag-view">
    <!-- 顶部工具栏 -->
    <div class="rag-toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">📚 RAG 知识库管理</h2>
      </div>
      <div class="toolbar-right">
        <a-button type="primary" @click="showCreateModal = true">
          <template #icon><PlusOutlined /></template>
          新建知识库
        </a-button>
      </div>
    </div>

    <!-- 内容区域 -->
    <a-row :gutter="16" class="rag-content">
      <!-- 左侧：知识库管理 -->
      <a-col :span="8">
        <a-card class="sidebar-card" :bordered="false">
          <template #title>
            <div class="card-header">
              <span>知识库列表</span>
            </div>
          </template>

          <a-list
            :data-source="knowledgeBases"
            :locale="{ emptyText: '暂无知识库' }"
            class="kb-list"
          >
            <template #renderItem="{ item }">
              <a-list-item
                :class="['kb-item', { active: currentKB?.id === item.id }]"
                @click="selectKB(item)"
              >
                <a-list-item-meta>
                  <template #title>
                    <div class="kb-name">{{ item.name }}</div>
                  </template>
                  <template #description>
                    <div>{{ item.description || '暂无描述' }}</div>
                    <div style="margin-top: 4px">
                      <FileTextOutlined /> {{ item.documentCount }} 个文档
                    </div>
                  </template>
                </a-list-item-meta>
                <template #actions>
                  <a-popconfirm
                    title="确定要删除这个知识库吗？"
                    ok-text="确定"
                    cancel-text="取消"
                    @confirm="deleteKB(item.id)"
                  >
                    <a-button type="text" danger size="small">
                      <template #icon><DeleteOutlined /></template>
                    </a-button>
                  </a-popconfirm>
                </template>
              </a-list-item>
            </template>
          </a-list>

          <!-- 文档上传区 -->
          <a-divider v-if="currentKB" />
          <div v-if="currentKB" class="upload-section">
            <h4>📄 上传文档到 {{ currentKB.name }}</h4>
            <a-upload-dragger
              :before-upload="beforeFileUpload"
              :show-upload-list="false"
              accept=".pdf,.doc,.docx"
              :custom-request="customUpload"
            >
              <p class="ant-upload-drag-icon">
                <InboxOutlined style="font-size: 40px; color: #667eea" />
              </p>
              <p class="ant-upload-text">点击或拖拽文件到此处上传</p>
              <p class="ant-upload-hint">支持 PDF、Word 文档</p>
            </a-upload-dragger>

            <a-progress
              v-if="uploadProgress > 0 && uploadProgress < 100"
              :percent="uploadProgress"
              :show-info="true"
              style="margin-top: 12px"
            />
          </div>
        </a-card>
      </a-col>

      <!-- 右侧：简单问答测试 -->
      <a-col :span="16">
        <a-card class="chat-card" :bordered="false">
          <template #title>
            <div class="card-header">
              <span>🔍 问答测试</span>
              <a-tag v-if="currentKB" color="purple">
                <FolderOutlined /> {{ currentKB.name }}
              </a-tag>
            </div>
          </template>

          <div class="messages-container" ref="messagesContainer">
            <a-empty
              v-if="chatMessages.length === 0"
              description="选择知识库后，可以进行简单问答测试"
            >
              <template #image>
                <div class="empty-icon">🔍</div>
              </template>
            </a-empty>

            <div
              v-for="(msg, index) in chatMessages"
              :key="index"
              :class="['message-item', msg.role === 'user' ? 'user' : 'assistant']"
            >
              <a-avatar :size="40" class="message-avatar">
                {{ msg.role === 'user' ? '👤' : '🤖' }}
              </a-avatar>
              <div class="message-bubble">
                <div class="message-text">{{ msg.content }}</div>
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
                <span style="margin-left: 12px">搜索中...</span>
              </div>
            </div>
          </div>

          <div class="input-area">
            <a-textarea
              v-model:value="inputMessage"
              @pressEnter="handleEnter"
              :disabled="!currentKB"
              placeholder="输入问题测试知识库索引... (Enter 发送)"
              :rows="2"
              class="message-input"
            />
            <a-button
              type="primary"
              size="large"
              @click="sendMessage"
              :disabled="!inputMessage.trim() || isLoading || !currentKB"
              :loading="isLoading"
              class="send-button"
            >
              <template #icon><SendOutlined /></template>
              {{ isLoading ? '搜索中...' : '测试' }}
            </a-button>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 创建知识库弹窗 -->
    <a-modal
      v-model:open="showCreateModal"
      title="创建知识库"
      ok-text="创建"
      cancel-text="取消"
      @ok="createKB"
      :ok-button-props="{ disabled: !newKB.name.trim() }"
    >
      <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="newKB.name" placeholder="输入知识库名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea
            v-model:value="newKB.description"
            :rows="3"
            placeholder="输入知识库描述"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import {
  PlusOutlined,
  DeleteOutlined,
  SendOutlined,
  ClockCircleOutlined,
  LoadingOutlined,
  InboxOutlined,
  FileTextOutlined,
  FolderOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { chatApi, ragApi } from '../api'

const knowledgeBases = ref([])
const currentKB = ref(null)
const chatMessages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const sessionId = ref(null)
const messagesContainer = ref(null)

const showCreateModal = ref(false)
const newKB = ref({ name: '', description: '' })

const uploadProgress = ref(0)

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

// 加载知识库列表
const loadKnowledgeBases = async () => {
  try {
    const response = await ragApi.getAllKnowledgeBases()
    knowledgeBases.value = response.data
  } catch (error) {
    console.error('加载知识库失败:', error)
  }
}

// 选择知识库
const selectKB = (kb) => {
  currentKB.value = kb
  chatMessages.value = []
  sessionId.value = generateSessionId()
}

// 创建知识库
const createKB = async () => {
  if (!newKB.value.name.trim()) return

  try {
    await ragApi.createKnowledgeBase(newKB.value.name, newKB.value.description)
    showCreateModal.value = false
    newKB.value = { name: '', description: '' }
    await loadKnowledgeBases()
    message.success('知识库创建成功')
  } catch (error) {
    console.error('创建知识库失败:', error)
    message.error('创建知识库失败')
  }
}

// 删除知识库
const deleteKB = async (id) => {
  try {
    await ragApi.deleteKnowledgeBase(id)
    if (currentKB.value?.id === id) {
      currentKB.value = null
      chatMessages.value = []
    }
    await loadKnowledgeBases()
    message.success('知识库已删除')
  } catch (error) {
    console.error('删除知识库失败:', error)
    message.error('删除知识库失败')
  }
}

// 处理文件上传前的检查
const beforeFileUpload = (file) => {
  if (!currentKB.value) {
    message.warning('请先选择知识库')
    return false
  }
  
  const isValidType = file.type === 'application/pdf' || 
                      file.type === 'application/msword' || 
                      file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  
  if (!isValidType) {
    message.error('只支持 PDF、Word 文档！')
    return false
  }
  
  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    message.error('文件大小不能超过 50MB！')
    return false
  }
  
  return true
}

// 自定义上传
const customUpload = async ({ file, onProgress, onSuccess, onError }) => {
  if (!currentKB.value) {
    message.warning('请先选择知识库')
    onError(new Error('未选择知识库'))
    return
  }

  uploadProgress.value = 10
  onProgress({ percent: 10 })

  try {
    console.log('开始上传文件:', file.name, '到知识库:', currentKB.value.id)
    const response = await ragApi.uploadDocument(file, currentKB.value.id)
    console.log('上传响应:', response.data)
    
    uploadProgress.value = 100
    onProgress({ percent: 100 })
    onSuccess(response.data)

    setTimeout(() => {
      uploadProgress.value = 0
      loadKnowledgeBases()
    }, 1000)

    message.success('文档上传成功！')
  } catch (error) {
    console.error('上传失败:', error)
    const errorMsg = error.response?.data?.message || error.message || '未知错误'
    message.error('文档上传失败: ' + errorMsg)
    uploadProgress.value = 0
    onError(error)
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
  if (!inputMessage.value.trim() || isLoading.value || !currentKB.value) return

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''

  chatMessages.value.push({
    role: 'user',
    content: userMessage,
    timestamp: Date.now()
  })

  scrollToBottom()
  isLoading.value = true

  try {
    const response = await chatApi.sendMessage(sessionId.value, userMessage, currentKB.value.id)
    const data = response.data

    if (data.sessionId) {
      sessionId.value = data.sessionId
    }

    chatMessages.value.push({
      role: 'assistant',
      content: data.message,
      timestamp: Date.now()
    })

    scrollToBottom()
  } catch (error) {
    console.error('发送消息失败:', error)
    chatMessages.value.push({
      role: 'assistant',
      content: '抱歉，发送消息时出现错误: ' + (error.response?.data?.error || error.message),
      timestamp: Date.now()
    })
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  loadKnowledgeBases()
  sessionId.value = generateSessionId()
})
</script>

<style scoped>
.rag-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.rag-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 60px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 0 0 24px 24px;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.15);
  position: relative;
  z-index: 10;
}

.toolbar-left {
  display: flex;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: white;
  letter-spacing: -0.4px;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.toolbar-right :deep(.ant-btn-primary) {
  height: 44px;
  padding: 0 32px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 22px;
  background: white;
  color: #667eea;
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toolbar-right :deep(.ant-btn-primary:hover) {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  transform: translateY(-1px);
  color: #667eea;
  background: white;
}

.rag-content {
  flex: 1;
  overflow: hidden;
  background: linear-gradient(180deg, #f8f9fe 0%, #ffffff 100%);
  padding: 24px 60px 32px;
}

.sidebar-card,
.chat-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border-radius: 20px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  overflow: hidden;
  transition: all 0.3s ease;
}

.sidebar-card:hover,
.chat-card:hover {
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.12);
  transform: translateY(-2px);
}

.sidebar-card :deep(.ant-card-head),
.chat-card :deep(.ant-card-head) {
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 24px;
  min-height: 56px;
}

.sidebar-card :deep(.ant-card-head-title),
.chat-card :deep(.ant-card-head-title) {
  padding: 16px 0;
}

.sidebar-card :deep(.ant-card-body),
.chat-card :deep(.ant-card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #333;
  font-size: 16px;
  font-weight: 600;
  gap: 12px;
  width: 100%;
}

.card-header > span {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-header :deep(.ant-btn) {
  flex-shrink: 0;
  font-size: 14px;
}

.card-header :deep(.ant-tag) {
  font-size: 15px;
  padding: 6px 16px;
}

.kb-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  min-height: 0;
  max-height: calc(100vh - 380px);
}

.kb-list::-webkit-scrollbar {
  width: 8px;
}

.kb-list::-webkit-scrollbar-track {
  background: transparent;
}

.kb-list::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 4px;
}

.kb-list::-webkit-scrollbar-thumb:hover {
  background: #b0b0b0;
}

.kb-list :deep(.ant-list-item) {
  padding: 18px;
  border-radius: 16px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: white;
  position: relative;
  overflow: hidden;
}

.kb-list :deep(.ant-list-item::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
  transform: scaleY(0);
  transition: transform 0.3s ease;
}

.kb-list :deep(.ant-list-item:hover) {
  background: #f8f9fe;
  border-color: rgba(102, 126, 234, 0.2);
  transform: translateX(4px);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.12);
}

.kb-list :deep(.ant-list-item:hover::before) {
  transform: scaleY(1);
}

.kb-item.active :deep(.ant-list-item) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-color: transparent;
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.3);
  transform: translateX(0);
}

.kb-item.active :deep(.ant-list-item::before) {
  transform: scaleY(0);
}

.kb-item.active :deep(.ant-list-item-meta-title),
.kb-item.active :deep(.ant-list-item-meta-description) {
  color: white;
}

.kb-name {
  font-weight: 600;
  font-size: 16px;
}

.kb-list :deep(.ant-list-item-meta-description) {
  font-size: 14px;
}

.upload-section {
  padding: 16px 20px 20px;
  background: #fafafa;
  border-top: 1px solid #e8e8e8;
  max-height: 240px;
  overflow-y: auto;
}

.upload-section h4 {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.upload-section :deep(.ant-upload-drag) {
  border-radius: 16px;
  border: 2px dashed #d9d9d9;
  background: white;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 20px 12px;
}

.upload-section :deep(.ant-upload-drag:hover) {
  border-color: #667eea;
  background: #f8f9fe;
  transform: scale(1.01);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.1);
}

.upload-section :deep(.ant-upload-text) {
  font-size: 14px;
}

.upload-section :deep(.ant-upload-hint) {
  font-size: 12px;
}

.upload-section :deep(.ant-upload-drag-icon) {
  margin-bottom: 8px;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 32px 40px;
  background: transparent;
  min-height: 0;
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

.empty-icon {
  font-size: 96px;
  margin-bottom: 20px;
  opacity: 0.4;
}

.message-item {
  display: flex;
  gap: 16px;
  margin-bottom: 28px;
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
  width: 44px;
  height: 44px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  font-size: 24px;
  border: 2px solid #f0f0f0;
}

.message-item.user .message-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-color: transparent;
}

.message-bubble {
  max-width: 70%;
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

.message-item.assistant .message-text::before {
  display: none;
}

.message-item.user .message-text::before {
  display: none;
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
  padding: 0 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.input-area {
  flex-shrink: 0;
  padding: 20px 24px;
  background: white;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  display: flex;
  gap: 16px;
  align-items: flex-end;
}

.message-input {
  flex: 1;
  font-size: 15px;
  border-radius: 12px;
  border: 1.5px solid #e8e8e8;
  transition: all 0.3s ease;
}

.message-input:focus-within {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.message-input :deep(textarea) {
  font-size: 15px;
  line-height: 1.6;
  padding: 12px 16px;
}

.send-button {
  height: 52px;
  font-size: 15px;
  font-weight: 600;
  padding: 0 32px;
  border-radius: 16px;
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

/* 上下文卡片样式 */
.context-card {
  height: 100%;
}

.context-card :deep(.ant-card-body) {
  padding: 0;
  height: calc(100% - 70px);
}

.context-content {
  height: 100%;
  overflow-y: auto;
  padding: 20px;
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
  font-size: 18px;
  display: inline-block;
  width: 28px;
  height: 28px;
  line-height: 28px;
  text-align: center;
  background: white;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.context-message {
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-top: -8px;
  margin-bottom: 12px;
}

.context-message-header {
  margin-bottom: 6px;
}

.context-role {
  font-weight: 600;
  font-size: 13px;
  color: #333;
}

.context-time {
  font-size: 11px;
  color: #999;
  margin-bottom: 6px;
}

.context-message-content {
  font-size: 12px;
  color: #666;
  line-height: 1.5;
  word-break: break-word;
  max-height: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
}
</style>
