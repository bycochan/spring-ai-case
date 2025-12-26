import axios from 'axios'

// 根据环境自动切换 API 地址
const API_BASE_URL = import.meta.env.PROD ? '' : 'http://localhost:8080'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 聊天 API
export const chatApi = {
  // 发送消息
  sendMessage(sessionId, message, knowledgeBaseId = null, enableMCP = false, agentId = null) {
    return api.post('/api/chat/message', {
      sessionId,
      message,
      knowledgeBaseId,
      enableMCP,
      agentId
    })
  },

  // 获取会话历史
  getHistory(sessionId) {
    return api.get(`/api/chat/history/${sessionId}`)
  },

  // 清除会话
  clearSession(sessionId) {
    return api.delete(`/api/chat/session/${sessionId}`)
  }
}

// RAG API
export const ragApi = {
  // 上传文档
  uploadDocument(file, knowledgeBaseId = null) {
    const formData = new FormData()
    formData.append('file', file)
    if (knowledgeBaseId) {
      formData.append('knowledgeBaseId', knowledgeBaseId)
    }
    
    return api.post('/api/rag/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // RAG 查询
  query(question, topK = 3) {
    return api.post('/api/rag/query', { question, topK })
  },

  // 获取所有知识库
  getAllKnowledgeBases() {
    return api.get('/api/rag/knowledge-bases')
  },

  // 创建知识库
  createKnowledgeBase(name, description) {
    return api.post('/api/rag/knowledge-base', { name, description })
  },

  // 删除知识库
  deleteKnowledgeBase(id) {
    return api.delete(`/api/rag/knowledge-base/${id}`)
  }
}

// MCP API
export const mcpApi = {
  // 注册 MCP 服务器
  registerServer(name, description, url, transportType = 'HTTP') {
    return api.post('/api/mcp/servers', {
      name,
      description,
      url,
      transportType
    })
  },

  // 获取所有服务器
  getAllServers() {
    return api.get('/api/mcp/servers')
  },

  // 删除服务器
  removeServer(serverId) {
    return api.delete(`/api/mcp/servers/${serverId}`)
  },

  // 启用/禁用服务器
  toggleServer(serverId, enabled) {
    return api.put(`/api/mcp/servers/${serverId}/toggle`, { enabled })
  },

  // 获取所有工具
  getAllTools() {
    return api.get('/api/mcp/tools')
  },

  // 获取服务器工具
  getServerTools(serverId) {
    return api.get(`/api/mcp/servers/${serverId}/tools`)
  },

  // 调用工具
  callTool(serverId, toolName, args) {
    return api.post('/api/mcp/tools/call', {
      serverId,
      toolName,
      arguments: args
    })
  }
}

// Agent API
export const agentApi = {
  // 获取所有 Agent
  getAllAgents() {
    return api.get('/api/agents')
  },

  // 根据 ID 获取 Agent
  getAgentById(id) {
    return api.get(`/api/agents/${id}`)
  },

  // 创建 Agent
  createAgent(agent) {
    return api.post('/api/agents', agent)
  },

  // 更新 Agent
  updateAgent(id, agent) {
    return api.put(`/api/agents/${id}`, agent)
  },

  // 删除 Agent
  deleteAgent(id) {
    return api.delete(`/api/agents/${id}`)
  },

  // 生成系统提示词与开场白（后端/LLM 支持）
  // 请求体: { name: string, description: string }
  // 返回示例: { systemPrompt: '...', welcomeMessage: '...' }
  generatePrompts(name, description) {
    return api.post('/api/agents/generate-prompt', { name, description })
  },

  // 从Workflow发布为Agent
  publishFromWorkflow(workflowId, agentData) {
    return api.post('/api/agents/publish-from-workflow', {
      workflowId,
      ...agentData
    })
  }
}

// Workflow API
export const workflowApi = {
  // 获取所有工作流
  getAllWorkflows() {
    return api.get('/api/workflows')
  },

  // 根据ID获取工作流
  getWorkflow(id) {
    return api.get(`/api/workflows/${id}`)
  },

  // 创建工作流
  createWorkflow(workflow) {
    return api.post('/api/workflows', workflow)
  },

  // 更新工作流
  updateWorkflow(id, workflow) {
    return api.put(`/api/workflows/${id}`, workflow)
  },

  // 删除工作流
  deleteWorkflow(id) {
    return api.delete(`/api/workflows/${id}`)
  },

  // 执行工作流
  executeWorkflow(id, input) {
    return api.post(`/api/workflows/${id}/execute`, input)
  },

  // 测试工作流
  testWorkflow(workflow, input) {
    return api.post('/api/workflows/test', { workflow, input })
  }
}

export default api
