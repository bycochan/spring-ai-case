<template>
  <div class="mcp-view">
    <!-- 顶部工具栏 -->
    <div class="mcp-toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">🔌 MCP 工具管理</h2>
      </div>
      <div class="toolbar-right">
        <a-button @click="loadAllTools" style="margin-right: 12px">
          <template #icon><ReloadOutlined /></template>
          刷新工具
        </a-button>
        <a-button type="primary" @click="showAddModal = true">
          <template #icon><PlusOutlined /></template>
          添加服务器
        </a-button>
      </div>
    </div>

    <!-- 内容区域 -->
    <a-row :gutter="0" class="mcp-content">
      <!-- 左侧：MCP 服务器管理 -->
      <a-col :span="7">
        <a-card class="server-card" :bordered="false">
          <template #title>
            <div class="card-header">
              <span>服务器列表</span>
            </div>
          </template>

          <a-list
            :data-source="servers"
            :locale="{ emptyText: '暂无 MCP 服务器' }"
            class="server-list"
          >
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta>
                  <template #title>
                    <div class="server-title">
                      <span>{{ item.name }}</span>
                      <a-switch
                        v-model:checked="item.enabled"
                        size="small"
                        @change="toggleServer(item)"
                      />
                    </div>
                  </template>
                  <template #description>
                    <div>{{ item.description }}</div>
                    <div class="server-url">{{ item.url }}</div>
                  </template>
                </a-list-item-meta>
                <template #actions>
                  <a-button
                    type="text"
                    size="small"
                    @click="loadTools(item.id)"
                  >
                    工具
                  </a-button>
                  <a-popconfirm
                    title="确定要删除此服务器吗？"
                    @confirm="removeServer(item.id)"
                  >
                    <a-button type="text" danger size="small">
                      <DeleteOutlined />
                    </a-button>
                  </a-popconfirm>
                </template>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </a-col>

      <!-- 右侧：工具列表和测试 -->
      <a-col :span="17">
        <a-card class="tools-card" :bordered="false">
          <template #title>
            <div class="card-header">
              <span>🛠️ 工具列表</span>
            </div>
          </template>

          <a-empty v-if="tools.length === 0" description="暂无可用工具">
            <template #image>
              <div class="empty-icon">🔧</div>
            </template>
          </a-empty>

          <a-collapse v-else :bordered="false" class="tools-collapse">
            <a-collapse-panel
              v-for="tool in tools"
              :key="tool.name"
              :header="tool.name"
            >
              <template #extra>
                <a-button
                  type="primary"
                  size="small"
                  @click.stop="openToolTest(tool)"
                >
                  测试
                </a-button>
              </template>
              <div class="tool-description">{{ tool.description }}</div>
              <a-divider />
              <div class="tool-schema">
                <strong>输入参数：</strong>
                <pre>{{ JSON.stringify(tool.inputSchema, null, 2) }}</pre>
              </div>
            </a-collapse-panel>
          </a-collapse>
        </a-card>
      </a-col>
    </a-row>

    <!-- 添加服务器弹窗 -->
    <a-modal
      v-model:open="showAddModal"
      title="添加 MCP 服务器"
      @ok="addServer"
      @cancel="resetAddForm"
    >
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="newServer.name" placeholder="服务器名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="newServer.description" placeholder="服务器描述" :rows="2" />
        </a-form-item>
        <a-form-item label="URL" required>
          <a-input v-model:value="newServer.url" placeholder="http://localhost:3000/mcp" />
        </a-form-item>
        <a-form-item label="传输类型">
          <a-select v-model:value="newServer.transportType">
            <a-select-option value="HTTP">HTTP</a-select-option>
            <a-select-option value="WEBSOCKET">WebSocket</a-select-option>
            <a-select-option value="STDIO">STDIO</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 工具测试弹窗 -->
    <a-modal
      v-model:open="showTestModal"
      title="测试工具"
      width="800px"
      @ok="testTool"
      @cancel="resetTestForm"
    >
      <a-form :label-col="{ span: 4 }">
        <a-form-item label="工具名称">
          <a-input :value="currentTool?.name" disabled />
        </a-form-item>
        <a-form-item label="参数 (JSON)">
          <a-textarea
            v-model:value="toolArguments"
            placeholder='{"key": "value"}'
            :rows="6"
            class="json-input"
          />
        </a-form-item>
      </a-form>
      <a-divider v-if="toolResult">执行结果</a-divider>
      <div v-if="toolResult" class="tool-result">
        <a-alert
          v-if="toolResult.success"
          message="执行成功"
          type="success"
          show-icon
        />
        <a-alert
          v-else
          message="执行失败"
          :description="toolResult.error"
          type="error"
          show-icon
        />
        <pre v-if="toolResult.result" class="result-content">{{ JSON.stringify(toolResult.result, null, 2) }}</pre>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { mcpApi } from '../api'

const servers = ref([])
const tools = ref([])
const showAddModal = ref(false)
const showTestModal = ref(false)
const currentTool = ref(null)
const toolArguments = ref('{}')
const toolResult = ref(null)

const newServer = ref({
  name: '',
  description: '',
  url: '',
  transportType: 'HTTP'
})

// 加载服务器列表
const loadServers = async () => {
  try {
    const response = await mcpApi.getAllServers()
    servers.value = response.data
  } catch (error) {
    console.error('加载服务器列表失败:', error)
    message.error('加载服务器列表失败')
  }
}

// 添加服务器
const addServer = async () => {
  if (!newServer.value.name || !newServer.value.url) {
    message.warning('请填写必填项')
    return
  }

  try {
    await mcpApi.registerServer(
      newServer.value.name,
      newServer.value.description,
      newServer.value.url,
      newServer.value.transportType
    )
    showAddModal.value = false
    resetAddForm()
    await loadServers()
    await loadAllTools()
    message.success('服务器添加成功')
  } catch (error) {
    console.error('添加服务器失败:', error)
    message.error('添加服务器失败: ' + (error.response?.data?.error || error.message))
  }
}

// 重置添加表单
const resetAddForm = () => {
  newServer.value = {
    name: '',
    description: '',
    url: '',
    transportType: 'HTTP'
  }
}

// 删除服务器
const removeServer = async (serverId) => {
  try {
    await mcpApi.removeServer(serverId)
    await loadServers()
    await loadAllTools()
    message.success('服务器删除成功')
  } catch (error) {
    console.error('删除服务器失败:', error)
    message.error('删除服务器失败')
  }
}

// 切换服务器状态
const toggleServer = async (server) => {
  try {
    await mcpApi.toggleServer(server.id, server.enabled)
    message.success(`服务器已${server.enabled ? '启用' : '禁用'}`)
  } catch (error) {
    console.error('切换服务器状态失败:', error)
    server.enabled = !server.enabled
    message.error('切换服务器状态失败')
  }
}

// 加载所有工具
const loadAllTools = async () => {
  try {
    const response = await mcpApi.getAllTools()
    tools.value = response.data
  } catch (error) {
    console.error('加载工具列表失败:', error)
    message.error('加载工具列表失败')
  }
}

// 加载指定服务器的工具
const loadTools = async (serverId) => {
  try {
    const response = await mcpApi.getServerTools(serverId)
    tools.value = response.data
    message.success('工具列表已刷新')
  } catch (error) {
    console.error('加载工具列表失败:', error)
    message.error('加载工具列表失败')
  }
}

// 打开工具测试
const openToolTest = (tool) => {
  currentTool.value = tool
  toolArguments.value = '{}'
  toolResult.value = null
  showTestModal.value = true
}

// 测试工具
const testTool = async () => {
  try {
    const args = JSON.parse(toolArguments.value)
    const response = await mcpApi.callTool(
      currentTool.value.serverId,
      currentTool.value.name,
      args
    )
    toolResult.value = response.data
  } catch (error) {
    console.error('调用工具失败:', error)
    toolResult.value = {
      success: false,
      error: error.response?.data?.error || error.message
    }
  }
}

// 重置测试表单
const resetTestForm = () => {
  currentTool.value = null
  toolArguments.value = '{}'
  toolResult.value = null
}

onMounted(() => {
  loadServers()
  loadAllTools()
})
</script>

<style scoped>
.mcp-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.mcp-toolbar {
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

.toolbar-right :deep(.ant-btn) {
  height: 44px;
  padding: 0 24px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  backdrop-filter: blur(10px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toolbar-right :deep(.ant-btn:hover) {
  background: rgba(255, 255, 255, 0.25);
  border-color: rgba(255, 255, 255, 0.4);
  color: white;
  transform: translateY(-1px);
}

.toolbar-right :deep(.ant-btn-primary) {
  background: white;
  border: none;
  color: #667eea;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.toolbar-right :deep(.ant-btn-primary:hover) {
  background: white;
  color: #667eea;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  transform: translateY(-1px);
}

.mcp-content {
  flex: 1;
  overflow: hidden;
  background: linear-gradient(180deg, #f8f9fe 0%, #ffffff 100%);
  padding: 24px 60px 32px;
}

.server-card,
.tools-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border-radius: 20px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  overflow: hidden;
  transition: all 0.3s ease;
}

.server-card:hover,
.tools-card:hover {
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.12);
  transform: translateY(-2px);
}

.server-card :deep(.ant-card-head),
.tools-card :deep(.ant-card-head) {
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 24px;
  min-height: 56px;
}

.server-card :deep(.ant-card-head-title),
.tools-card :deep(.ant-card-head-title) {
  padding: 16px 0;
}

.server-card :deep(.ant-card-body),
.tools-card :deep(.ant-card-body) {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
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
}

.server-list {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.server-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.server-url {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.empty-icon {
  font-size: 96px;
  margin-bottom: 20px;
  opacity: 0.4;
}

.tools-collapse {
  background: transparent;
}

.tools-collapse :deep(.ant-collapse-item) {
  margin-bottom: 12px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
}

.tool-description {
  color: #666;
  margin-bottom: 8px;
}

.tool-schema {
  background: linear-gradient(135deg, #f8f9fe 0%, #f5f7fa 100%);
  padding: 16px;
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.tool-schema pre {
  margin: 8px 0 0 0;
  font-size: 13px;
  color: #333;
}

.json-input {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
}

.tool-result {
  margin-top: 16px;
}

.result-content {
  margin-top: 12px;
  background: #f5f7fa;
  padding: 16px;
  border-radius: 6px;
  max-height: 300px;
  overflow-y: auto;
  font-size: 13px;
}
</style>
