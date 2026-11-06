<template>
  <div class="home-view">
    <!-- 头部欢迎区 -->
    <div class="welcome-section">
      <div class="welcome-content">
        <h1 class="welcome-title">🤖 AI Agent 智能助手</h1>
        <p class="welcome-subtitle">创建你的专属 AI 助手，开启智能对话之旅</p>
      </div>
      <a-button type="primary" size="large" @click="showCreateModal = true" class="create-btn">
        <template #icon><PlusOutlined /></template>
        创建新 Agent
      </a-button>
    </div>

    <!-- Agent 卡片列表 -->
    <div class="agents-section">
      <div class="section-header">
        <h2>我的 Agent</h2>
        <a-tag color="blue">{{ agents.length }} 个助手</a-tag>
      </div>

      <a-empty v-if="agents.length === 0" description="还没有创建 Agent，点击上方按钮创建吧！">
        <template #image>
          <div class="empty-icon">🎭</div>
        </template>
      </a-empty>

      <div v-else class="agent-grid">
        <div 
          v-for="agent in agents" 
          :key="agent.id" 
          class="agent-card"
          @click="startChatWithAgent(agent)"
        >
          <div class="agent-card-header">
            <div class="agent-avatar">{{ agent.avatar }}</div>
            <div class="agent-actions">
              <a-button 
                type="text" 
                size="small" 
                @click.stop="editAgent(agent)"
                class="action-btn"
              >
                <EditOutlined />
              </a-button>
              <a-popconfirm
                title="确定要删除这个 Agent 吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="deleteAgent(agent.id)"
              >
                <a-button 
                  type="text" 
                  size="small" 
                  danger
                  @click.stop
                  class="action-btn"
                >
                  <DeleteOutlined />
                </a-button>
              </a-popconfirm>
            </div>
          </div>

          <div class="agent-card-body">
            <h3 class="agent-name">{{ agent.name }}</h3>
            <p class="agent-description">{{ agent.description }}</p>
            
            <div class="agent-tags">
              <a-tag v-if="agent.isDefault" color="blue">默认</a-tag>
              <a-tag v-if="agent.enableMCP" color="green">
                <ThunderboltOutlined /> MCP
              </a-tag>
              <a-tag v-if="agent.enableImageUpload" color="orange">
                <PictureOutlined /> 图片
              </a-tag>
              <a-tag v-if="agent.knowledgeBaseId" color="purple">
                <BookOutlined /> 知识库
              </a-tag>
              <a-tag v-if="agent.workflowId" color="cyan">
                📦 工作流
              </a-tag>
            </div>
          </div>

          <div class="agent-card-footer">
            <a-button type="primary" block @click="startChatWithAgent(agent)">
              <MessageOutlined /> 开始对话
            </a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑 Agent 模态框 -->
    <a-modal
      v-model:open="showCreateModal"
      :title="editingAgent ? '编辑 Agent' : '创建新 Agent'"
      @ok="handleSaveAgent"
      @cancel="handleCancelEdit"
      width="700px"
      :okText="editingAgent ? '保存' : '创建'"
      cancelText="取消"
    >
      <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 19 }">
        <a-form-item label="头像" required>
          <div class="emoji-selector">
            <div 
              v-for="emoji in availableEmojis" 
              :key="emoji"
              :class="['emoji-option', { selected: agentForm.avatar === emoji }]"
              @click="agentForm.avatar = emoji"
            >
              {{ emoji }}
            </div>
          </div>
        </a-form-item>

        <a-form-item label="名称" required>
          <a-input 
            v-model:value="agentForm.name" 
            placeholder="给你的 Agent 起个名字"
            :maxlength="20"
          />
        </a-form-item>

        <a-form-item label="描述">
          <a-textarea
            v-model:value="agentForm.description"
            :rows="2"
            placeholder="简单描述这个 Agent 的用途"
            :maxlength="100"
          />
        </a-form-item>

        <a-form-item label="系统提示词" required>
          <a-textarea
            v-model:value="agentForm.systemPrompt"
            :rows="4"
            placeholder="定义 Agent 的角色和行为，例如：你是一个专业的编程助手..."
            :maxlength="500"
          />
          <div style="margin-top:8px; display:flex; gap:8px; align-items:center;">
            <a-button :loading="generatingPrompt" :disabled="generatingPrompt" type="text" size="small" @click="generateSystemPromptClick">智能生成</a-button>
            <span class="help-text">根据名称/描述生成提示词（在未手动编辑时会自动填充）</span>
          </div>
        </a-form-item>

        <a-form-item label="开场白">
          <a-textarea
            v-model:value="agentForm.welcomeMessage"
            :rows="2"
            placeholder="Agent 的第一句问候语"
            :maxlength="200"
          />
          <div style="margin-top:8px; display:flex; gap:8px; align-items:center;">
            <a-button :loading="generatingWelcome" :disabled="generatingWelcome" type="text" size="small" @click="generateWelcomeMessageClick">智能生成</a-button>
            <span class="help-text">根据名称/描述生成开场白（在未手动编辑时会自动填充）</span>
          </div>
        </a-form-item>

        <a-divider>功能配置</a-divider>

        <a-form-item label="MCP 工具">
          <a-switch v-model:checked="agentForm.enableMCP" />
          <span class="help-text">启用后可调用已注册的 MCP 工具</span>
        </a-form-item>

        <a-form-item label="图片上传">
          <a-switch v-model:checked="agentForm.enableImageUpload" />
          <span class="help-text">开启后对话中可上传图片（单张 ≤10MB，最多 5 张）</span>
        </a-form-item>

        <a-form-item label="知识库">
          <a-select
            v-model:value="agentForm.knowledgeBaseId"
            placeholder="选择知识库（可选）"
            allow-clear
            style="width: 100%"
          >
            <a-select-option value="">不使用知识库</a-select-option>
            <a-select-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
              📚 {{ kb.name }} ({{ kb.documentCount }} 个文档)
            </a-select-option>
          </a-select>
          <div v-if="agentForm.knowledgeBaseId" class="help-text success">
            ✓ 将基于选定知识库回答问题
          </div>
        </a-form-item>

        <a-form-item label="设为默认">
          <a-switch v-model:checked="agentForm.isDefault" />
          <span class="help-text">默认 Agent 将在首次进入时自动加载</span>
        </a-form-item>

        <a-divider>工作流配置</a-divider>

        <a-form-item label="使用工作流">
          <a-select
            v-model:value="agentForm.workflowId"
            placeholder="选择工作流（可选）"
            allow-clear
            style="width: 100%"
          >
            <a-select-option value="">不使用工作流</a-select-option>
            <a-select-option v-for="(wf, id) in workflows" :key="id" :value="id">
              📦 {{ wf.name }}
            </a-select-option>
          </a-select>
          <div style="margin-top: 8px">
            <a-button type="link" @click="createWorkflow" size="small">
              <PlusOutlined /> 创建新工作流
            </a-button>
          </div>
          <div v-if="agentForm.workflowId" class="help-text success">
            ✓ Agent 将使用工作流进行编排执行
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  MessageOutlined,
  ThunderboltOutlined,
  PictureOutlined,
  BookOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { agentApi, ragApi, workflowApi } from '../api'

const router = useRouter()

const agents = ref([])
const knowledgeBases = ref([])
const workflows = ref({})
const showCreateModal = ref(false)
const editingAgent = ref(null)

const agentForm = ref({
  avatar: '🤖',
  name: '',
  description: '',
  systemPrompt: '',
  welcomeMessage: '',
  enableMCP: false,
  enableImageUpload: false,
  knowledgeBaseId: null,
  isDefault: false,
  workflowId: null
})

const availableEmojis = [
  '🤖', '👨‍💻', '👩‍💻', '🧑‍🎓', '👨‍🏫', '👩‍⚕️', 
  '🎨', '📊', '💼', '🔬', '📝', '🎯',
  '🌟', '💡', '🚀', '⚡', '🎭', '🎪'
]

// Helper: create system prompt from name/description
function makeSystemPrompt(name = '', description = '') {
  const n = (name || '').trim()
  const d = (description || '').trim()
  if (!n && !d) {
    return '你是一个高级的 AI 助手，擅长通用任务，回答要简洁、专业、友好。'
  }
  if (n && !d) {
    return `你是“${n}”，一位专业的 AI 助手。请根据用户意图提供准确、礼貌且实用的回复。`}
  if (!n && d) {
    return `你是一位专注于${d}的 AI 助手，回答要结合领域知识，语言通顺易懂。`}
  // both exist
  return `你是“${n}”，专注于${d}的 AI 助手。请在回答中体现专业性、清晰度与用户引导性，必要时给出示例和步骤。`
}

// Helper: create welcome message
function makeWelcomeMessage(name = '', description = '') {
  const n = (name || '').trim()
  const d = (description || '').trim()
  if (!n && !d) {
    return '你好！我是你的 AI 助手，很高兴为你提供帮助。请告诉我你想要做什么。'
  }
  if (n && !d) {
    return `你好，我是${n}，可以帮你完成多种任务。请告诉我当前需要我做什么。`}
  if (!n && d) {
    return `你好！我可以帮助你处理与${d}相关的问题，请告诉我你的需求。`}
  return `你好，我是${n}，我专注于${d}。有什么我可以帮你处理的事情吗？`
}

const generatingPrompt = ref(false)
const generatingWelcome = ref(false)

// Manual triggers used by template buttons
async function generateSystemPromptClick() {
  if (generatingPrompt.value) return
  generatingPrompt.value = true
  try {
    console.log('调用后端生成提示词:', { name: agentForm.value.name, description: agentForm.value.description })
    // try backend generation first
    const resp = await agentApi.generatePrompts(agentForm.value.name, agentForm.value.description)
    console.log('后端返回:', resp)
    if (resp?.data && (resp.data.systemPrompt || resp.data.welcomeMessage)) {
      if (resp.data.systemPrompt) agentForm.value.systemPrompt = resp.data.systemPrompt
      if (resp.data.welcomeMessage) agentForm.value.welcomeMessage = resp.data.welcomeMessage
      message.success('已从后端生成提示词')
    } else {
      // fallback to local
      agentForm.value.systemPrompt = makeSystemPrompt(agentForm.value.name, agentForm.value.description)
      message.success('已生成提示词（本地）')
    }
  } catch (err) {
    console.error('后端生成失败，详细错误:', err)
    console.error('错误响应:', err.response)
    console.error('错误状态:', err.response?.status)
    console.error('错误数据:', err.response?.data)
    agentForm.value.systemPrompt = makeSystemPrompt(agentForm.value.name, agentForm.value.description)
    const errorMsg = err.response?.status 
      ? `后端错误 (${err.response.status}): ${err.response?.data?.message || err.message}` 
      : '后端服务不可用，已使用本地生成'
    message.warning(errorMsg)
  } finally {
    generatingPrompt.value = false
  }
}

async function generateWelcomeMessageClick() {
  if (generatingWelcome.value) return
  generatingWelcome.value = true
  try {
    const resp = await agentApi.generatePrompts(agentForm.value.name, agentForm.value.description)
    if (resp?.data && (resp.data.welcomeMessage || resp.data.systemPrompt)) {
      if (resp.data.welcomeMessage) agentForm.value.welcomeMessage = resp.data.welcomeMessage
      if (resp.data.systemPrompt && !agentForm.value.systemPrompt) agentForm.value.systemPrompt = resp.data.systemPrompt
      message.success('已从后端生成开场白')
    } else {
      agentForm.value.welcomeMessage = makeWelcomeMessage(agentForm.value.name, agentForm.value.description)
      message.success('已生成开场白（本地）')
    }
  } catch (err) {
    console.warn('后端生成失败，回退至本地生成', err)
    agentForm.value.welcomeMessage = makeWelcomeMessage(agentForm.value.name, agentForm.value.description)
    message.warning('后端不可用，已使用本地生成')
  } finally {
    generatingWelcome.value = false
  }
}

// Auto-fill when user types name/description while creating (not editing)
watch([
  () => agentForm.value.name,
  () => agentForm.value.description
], ([newName, newDesc]) => {
  // only auto-fill when creating new agent (editingAgent is null)
  if (editingAgent.value) return
  // if systemPrompt empty, fill a suggestion (do not override if user already typed)
  if (!agentForm.value.systemPrompt || agentForm.value.systemPrompt.trim() === '') {
    agentForm.value.systemPrompt = makeSystemPrompt(newName, newDesc)
  }
  if (!agentForm.value.welcomeMessage || agentForm.value.welcomeMessage.trim() === '') {
    agentForm.value.welcomeMessage = makeWelcomeMessage(newName, newDesc)
  }
})

// 加载 Agents
const loadAgents = async () => {
  try {
    const response = await agentApi.getAllAgents()
    agents.value = response.data
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

// 编辑 Agent
const editAgent = (agent) => {
  editingAgent.value = agent
  agentForm.value = {
    avatar: agent.avatar,
    name: agent.name,
    description: agent.description,
    systemPrompt: agent.systemPrompt,
    welcomeMessage: agent.welcomeMessage,
    enableMCP: agent.enableMCP,
    enableImageUpload: agent.enableImageUpload,
    knowledgeBaseId: agent.knowledgeBaseId || null,
    isDefault: agent.isDefault,
    workflowId: agent.workflowId || null
  }
  showCreateModal.value = true
}

// 保存 Agent
const handleSaveAgent = async () => {
  if (!agentForm.value.name.trim()) {
    message.warning('请输入 Agent 名称')
    return
  }
  if (!agentForm.value.systemPrompt.trim()) {
    message.warning('请输入系统提示词')
    return
  }

  try {
    let savedAgentId
    if (editingAgent.value) {
      // 更新
      await agentApi.updateAgent(editingAgent.value.id, agentForm.value)
      message.success('Agent 更新成功')
      savedAgentId = editingAgent.value.id
    } else {
      // 创建
      const response = await agentApi.createAgent(agentForm.value)
      message.success('Agent 创建成功')
      savedAgentId = response.data.id
    }
    
    showCreateModal.value = false
    resetForm()
    await loadAgents()
    
    // 如果是创建新 Agent，自动跳转到对话页面
    if (!editingAgent.value && savedAgentId) {
      router.push({
        path: '/agent-chat',
        query: {
          agentId: savedAgentId
        }
      })
    }
  } catch (error) {
    console.error('保存 Agent 失败:', error)
    message.error('保存失败: ' + (error.response?.data?.message || error.message))
  }
}

// 删除 Agent
const deleteAgent = async (id) => {
  try {
    await agentApi.deleteAgent(id)
    message.success('Agent 已删除')
    loadAgents()
  } catch (error) {
    console.error('删除 Agent 失败:', error)
    message.error('删除失败')
  }
}

// 取消编辑
const handleCancelEdit = () => {
  showCreateModal.value = false
  resetForm()
}

// 重置表单
const resetForm = () => {
  editingAgent.value = null
  agentForm.value = {
    avatar: '🤖',
    name: '',
    description: '',
    systemPrompt: '',
    welcomeMessage: '',
    enableMCP: false,
    enableImageUpload: false,
    knowledgeBaseId: null,
    isDefault: false,
    workflowId: null
  }
}

// 创建工作流
const createWorkflow = () => {
  router.push('/workflow')
}

// 加载工作流列表
const loadWorkflows = async () => {
  try {
    const response = await workflowApi.getAllWorkflows()
    workflows.value = response.data
  } catch (error) {
    console.error('加载工作流失败:', error)
  }
}

// 开始与 Agent 对话
const startChatWithAgent = (agent) => {
  // 跳转到 Agent 对话页面
  router.push({
    path: '/agent-chat',
    query: {
      agentId: agent.id
    }
  })
}

onMounted(() => {
  loadAgents()
  loadKnowledgeBases()
  loadWorkflows()
})
</script>

<style scoped>
.home-view {
  height: 100vh;
  background: linear-gradient(180deg, #f8f9fe 0%, #ffffff 100%);
  padding: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.welcome-section {
  background: var(--primary-gradient);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  padding: 40px 48px;
  margin-bottom: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: var(--shadow-md);
  position: relative;
  overflow: hidden;
}

.welcome-section::before {
  content: '';
  position: absolute;
  top: -40%;
  right: -10%;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.08) 0%, transparent 70%);
  border-radius: 50%;
}

.welcome-section::after {
  content: '';
  position: absolute;
  bottom: -20%;
  left: -4%;
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.06) 0%, transparent 70%);
  border-radius: 50%;
}

.welcome-content {
  flex: 1;
  position: relative;
  z-index: 1;
}

.welcome-title {
  font-size: 40px;
  font-weight: 800;
  margin: 0 0 8px 0;
  color: white;
  letter-spacing: -0.5px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.welcome-subtitle {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.95);
  margin: 0;
  font-weight: 400;
  letter-spacing: 0.2px;
}

.create-btn {
  height: 52px;
  padding: 0 28px;
  font-size: 15px;
  border-radius: 26px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  background: white;
  border: none;
  color: var(--primary-500);
  font-weight: 700;
  position: relative;
  z-index: 1;
  transition: all 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.create-btn:hover {
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.16);
  transform: translateY(-2px) scale(1.01);
  color: var(--primary-600);
}

.agents-section {
  background: var(--surface);
  border-radius: 0;
  padding: 32px 48px;
  box-shadow: none;
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
}

.section-header h2 {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
  color: #333;
}

.empty-icon {
  font-size: 96px;
  margin-bottom: 20px;
  opacity: 0.28;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 18px;
  max-height: none;
}

.agent-card {
  background: var(--surface);
  border-radius: var(--radius-lg);
  padding: 20px;
  box-shadow: var(--shadow-sm);
  transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  border: 1px solid rgba(0, 0, 0, 0.04);
  position: relative;
  overflow: hidden;
}

.agent-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--primary-gradient);
  transform: scaleX(0);
  transition: transform 0.32s ease;
}

.agent-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 12px 32px rgba(102, 126, 234, 0.12);
  border-color: rgba(102, 126, 234, 0.12);
}

.agent-card:hover::before {
  transform: scaleX(1);
}

.agent-card:active { transform: translateY(-2px); }

.agent-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.agent-avatar {
  font-size: 44px;
  line-height: 1;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.08));
}

.agent-actions { display: flex; gap: 6px; }

.action-btn { opacity: 0.6; transition: all 0.18s; border-radius: 8px; }
.action-btn:hover { opacity: 1; background: rgba(102, 126, 234, 0.06); }

.agent-card-body { margin-bottom: 16px; }

.agent-name { font-size: 18px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-color); }

.agent-description { font-size: 13px; color: var(--muted-color); line-height: 1.6; margin: 0 0 10px 0; min-height: 36px; }

.agent-tags { display: flex; gap: 8px; flex-wrap: wrap; }

.agent-card-footer { margin-top: 12px; padding-top: 12px; border-top: 1px solid #f4f4f4; }

.agent-card-footer .ant-btn { height: 44px; border-radius: 22px; font-weight: 600; font-size: 14px; transition: all 0.24s; }
.agent-card-footer .ant-btn-primary { background: var(--primary-gradient); border: none; box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2); }
.agent-card-footer .ant-btn-primary:hover { box-shadow: 0 6px 16px rgba(102, 126, 234, 0.28); transform: translateY(-1px); }
.agent-card-footer .ant-btn-default { border: 1.2px solid #ececec; background: white; }
.agent-card-footer .ant-btn-default:hover { border-color: var(--primary-500); color: var(--primary-500); background: rgba(102, 126, 234, 0.04); }

/* 模态框样式 */
.emoji-selector { display: grid; grid-template-columns: repeat(auto-fit, minmax(36px, 1fr)); gap: 8px; padding: 8px; background: #f5f7fa; border-radius: 8px; }

.emoji-option { font-size: 26px; text-align: center; padding: 8px; cursor: pointer; border-radius: 8px; transition: all 0.18s; border: 2px solid transparent; }
.emoji-option:hover { background: #eef3ff; transform: scale(1.06); }
.emoji-option.selected { background: var(--primary-gradient); border-color: var(--primary-500); box-shadow: 0 2px 8px rgba(102, 126, 234, 0.18); }

.help-text { display: block; font-size: 12px; color: #9aa3b2; margin-top: 6px; }
.help-text.success { color: #52c41a; }

:deep(.ant-form-item-label > label) { font-weight: 600; }

:deep(.ant-divider) { margin: 20px 0; border-color: #ececec; }

/* responsive tweaks */
@media (max-width: 992px) {
  .welcome-section { padding: 28px 20px; border-radius: 0 0 18px 18px; }
  .welcome-title { font-size: 28px; }
  .welcome-subtitle { font-size: 14px; }
  .create-btn { height: 44px; padding: 0 18px; font-size: 14px; }
  .agents-section { padding: 20px; }
  .agent-grid { grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
}

@media (max-width: 480px) {
  .welcome-section { flex-direction: column; gap: 12px; align-items: flex-start; padding: 20px; }
  .create-btn { width: 100%; }
  .agent-grid { grid-template-columns: 1fr; }
  .home-view { padding-bottom: 24px; }
}
</style>
