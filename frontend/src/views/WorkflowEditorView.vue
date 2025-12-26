<template>
  <div class="workflow-editor">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <a-button type="text" @click="backToHome">
          <template #icon><ArrowLeftOutlined /></template>
          返回
        </a-button>
        <a-divider type="vertical" />
        <h2 class="workflow-title">{{ workflow.name || '未命名工作流' }}</h2>
      </div>
      <div class="toolbar-right">
        <a-button @click="showTestModal = true" style="margin-right: 12px">
          <template #icon><PlayCircleOutlined /></template>
          测试运行
        </a-button>
        <a-button @click="saveWorkflow" style="margin-right: 12px">
          <template #icon><SaveOutlined /></template>
          保存工作流
        </a-button>
        <a-button type="primary" @click="showPublishModal = true">
          <template #icon><RocketOutlined /></template>
          发布
        </a-button>
      </div>
    </div>

    <!-- 节点面板 -->
    <div class="node-panel">
      <h3>节点库</h3>
      <div class="node-list">
        <div
          v-for="nodeType in nodeTypes"
          :key="nodeType.type"
          class="node-item"
          draggable="true"
          @dragstart="onDragStart($event, nodeType)"
        >
          <component :is="nodeType.icon" />
          <span>{{ nodeType.label }}</span>
        </div>
      </div>
    </div>

    <!-- 工作流画布 -->
    <div class="flow-container" @drop="onDrop" @dragover.prevent>
      <VueFlow
        v-model="elements"
        :default-zoom="1"
        :min-zoom="0.2"
        :max-zoom="4"
        @node-click="onNodeClick"
        @edge-click="onEdgeClick"
        @connect="onConnect"
        :connection-mode="'loose'"
      >
        <Background />
        <Controls />
        <MiniMap />
      </VueFlow>
    </div>

    <!-- 节点配置面板 -->
    <a-drawer
      v-model:open="showNodeConfig"
      title="节点配置"
      placement="right"
      width="400"
    >
      <div v-if="selectedNode">
        <a-form layout="vertical">
          <a-form-item label="节点名称">
            <a-input v-model:value="selectedNode.data.label" />
          </a-form-item>
          <a-form-item label="节点描述">
            <a-textarea v-model:value="selectedNode.data.description" :rows="2" />
          </a-form-item>

          <!-- LLM节点配置 -->
          <template v-if="selectedNode.type === 'llm'">
            <a-divider>LLM配置</a-divider>
            <a-form-item label="系统提示词">
              <a-textarea v-model:value="selectedNode.data.config.systemPrompt" :rows="3" />
            </a-form-item>
            <a-form-item label="用户提示词模板">
              <a-textarea v-model:value="selectedNode.data.config.userPrompt" :rows="3" 
                placeholder="使用 {{变量名}} 引用输入参数" />
            </a-form-item>
            <a-form-item label="模型">
              <a-select v-model:value="selectedNode.data.config.model">
                <a-select-option value="qwen-turbo">qwen-turbo</a-select-option>
                <a-select-option value="qwen-plus">qwen-plus</a-select-option>
                <a-select-option value="qwen-max">qwen-max</a-select-option>
              </a-select>
            </a-form-item>
            
            <a-divider>增强功能</a-divider>
            <a-form-item label="启用MCP工具">
              <a-switch v-model:checked="selectedNode.data.config.enableMCP" />
              <span class="help-text" style="margin-left: 8px">调用已注册的MCP工具</span>
            </a-form-item>
            
            <a-form-item label="知识库">
              <a-select
                v-model:value="selectedNode.data.config.knowledgeBaseId"
                placeholder="选择知识库（可选）"
                allow-clear
                style="width: 100%"
              >
                <a-select-option value="">不使用知识库</a-select-option>
                <a-select-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
                  📚 {{ kb.name }} ({{ kb.documentCount }} 个文档)
                </a-select-option>
              </a-select>
              <div v-if="selectedNode.data.config.knowledgeBaseId" class="help-text success" style="margin-top: 4px">
                ✓ 将基于选定知识库回答问题
              </div>
            </a-form-item>
          </template>

          <!-- HTTP节点配置 -->
          <template v-if="selectedNode.type === 'http'">
            <a-divider>HTTP配置</a-divider>
            <a-form-item label="请求方法">
              <a-select v-model:value="selectedNode.data.config.method">
                <a-select-option value="GET">GET</a-select-option>
                <a-select-option value="POST">POST</a-select-option>
                <a-select-option value="PUT">PUT</a-select-option>
                <a-select-option value="DELETE">DELETE</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="URL">
              <a-input v-model:value="selectedNode.data.config.url" 
                placeholder="支持变量: {{变量名}}" />
            </a-form-item>
            <a-form-item label="请求体">
              <a-textarea v-model:value="selectedNode.data.config.body" :rows="4" 
                placeholder="JSON格式，支持变量" />
            </a-form-item>
          </template>

          <!-- IF节点配置 -->
          <template v-if="selectedNode.type === 'if'">
            <a-divider>IF条件配置</a-divider>
            <a-form-item label="左操作数">
              <a-input v-model:value="selectedNode.data.config.leftOperand" />
            </a-form-item>
            <a-form-item label="运算符">
              <a-select v-model:value="selectedNode.data.config.operator">
                <a-select-option value="==">等于 (==)</a-select-option>
                <a-select-option value="!=">不等于 (!=)</a-select-option>
                <a-select-option value=">">大于 (>)</a-select-option>
                <a-select-option value="<">小于 (<)</a-select-option>
                <a-select-option value=">=">大于等于 (>=)</a-select-option>
                <a-select-option value="<=">小于等于 (<=)</a-select-option>
                <a-select-option value="contains">包含</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="右操作数">
              <a-input v-model:value="selectedNode.data.config.rightOperand" />
            </a-form-item>
          </template>

          <!-- LOOP节点配置 -->
          <template v-if="selectedNode.type === 'loop'">
            <a-divider>循环配置</a-divider>
            <a-form-item label="循环类型">
              <a-select v-model:value="selectedNode.data.config.loopType">
                <a-select-option value="forEach">遍历数组</a-select-option>
                <a-select-option value="while">While循环</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="数组路径">
              <a-input v-model:value="selectedNode.data.config.arrayPath" 
                placeholder="要遍历的数组变量名" />
            </a-form-item>
            <a-form-item label="最大迭代次数">
              <a-input-number v-model:value="selectedNode.data.config.maxIterations" 
                :min="1" :max="1000" style="width: 100%" />
            </a-form-item>
          </template>

          <!-- START节点配置 -->
          <template v-if="selectedNode.type === 'start'">
            <a-divider>输入参数定义</a-divider>
            <a-alert 
              message="定义工作流的输入参数，这些参数将作为变量供后续节点使用" 
              type="info" 
              show-icon 
              style="margin-bottom: 12px"
            />
            <div class="param-definition">
              <div v-for="(param, index) in selectedNode.data.config.inputParams" :key="index" 
                class="param-item">
                <a-input 
                  v-model:value="param.name" 
                  placeholder="参数名" 
                  style="width: 35%" 
                />
                <a-select 
                  v-model:value="param.type" 
                  placeholder="类型"
                  style="width: 25%; margin-left: 8px"
                >
                  <a-select-option value="string">字符串</a-select-option>
                  <a-select-option value="number">数字</a-select-option>
                  <a-select-option value="boolean">布尔</a-select-option>
                  <a-select-option value="object">对象</a-select-option>
                  <a-select-option value="array">数组</a-select-option>
                </a-select>
                <a-input 
                  v-model:value="param.testValue" 
                  placeholder="调试时的测试值"
                  style="width: 33%; margin-left: 8px"
                />
                <a-button type="text" danger @click="removeInputParam(index)" 
                  style="margin-left: 4px">
                  <DeleteOutlined />
                </a-button>
              </div>
              <a-button @click="addInputParam" block style="margin-top: 8px">
                <template #icon><PlusOutlined /></template>
                添加输入参数
              </a-button>
            </div>
          </template>

          <!-- 输入映射 -->
          <a-divider>输入参数映射</a-divider>
          <div class="param-mapping">
            <div v-for="(mapping, index) in selectedNode.data.inputMapping" :key="index" 
              class="mapping-item">
              <a-input v-model:value="mapping.key" placeholder="参数名" style="width: 35%" />
              <a-select 
                v-model:value="mapping.value" 
                placeholder="选择参数源"
                style="width: 58%; margin-left: 8px"
                show-search
                allow-clear
              >
                <a-select-opt-group label="工作流输入">
                  <a-select-option value="$.input">完整输入对象</a-select-option>
                </a-select-opt-group>
                <a-select-opt-group label="前置节点输出">
                  <a-select-option 
                    v-for="node in getPreviousNodes(selectedNode.id)" 
                    :key="node.id"
                    :value="`$.nodes.${node.id}`"
                  >
                    {{ node.data.label }} ({{ node.type }})
                  </a-select-option>
                </a-select-opt-group>
                <a-select-opt-group label="调试结果" v-if="debugResults[selectedNode.id]">
                  <a-select-option 
                    v-for="(field, key) in getAvailableFields(debugResults[selectedNode.id])" 
                    :key="key"
                    :value="field"
                  >
                    {{ key }}: {{ getFieldPreview(debugResults[selectedNode.id], key) }}
                  </a-select-option>
                </a-select-opt-group>
              </a-select>
              <a-button type="text" danger @click="removeInputMapping(index)" 
                style="margin-left: 4px">
                <DeleteOutlined />
              </a-button>
            </div>
            <a-button @click="addInputMapping" block style="margin-top: 8px">
              <template #icon><PlusOutlined /></template>
              添加输入映射
            </a-button>
          </div>

          <!-- 调试执行 -->
          <a-divider>节点调试</a-divider>
          <a-button 
            @click="debugNode(selectedNode)" 
            block 
            type="dashed"
            :loading="debuggingNode === selectedNode.id"
          >
            <template #icon><PlayCircleOutlined /></template>
            调试执行此节点
          </a-button>
          <div v-if="debugResults[selectedNode.id]" class="debug-result">
            <a-alert 
              message="执行成功" 
              type="success" 
              show-icon 
              style="margin-top: 12px"
            />
            <pre class="debug-output">{{ JSON.stringify(debugResults[selectedNode.id], null, 2) }}</pre>
          </div>
        </a-form>
      </div>
    </a-drawer>

    <!-- 测试运行模态框 -->
    <a-modal
      v-model:open="showTestModal"
      title="测试工作流"
      width="600px"
      @ok="runTest"
    >
      <a-form layout="vertical">
        <a-form-item label="输入参数（JSON格式）">
          <a-textarea v-model:value="testInput" :rows="6" 
            placeholder='{"key": "value"}' />
        </a-form-item>
      </a-form>
      <div v-if="testResult">
        <a-divider>执行结果</a-divider>
        <a-descriptions bordered size="small">
          <a-descriptions-item label="状态">
            <a-tag :color="testResult.status === 'SUCCESS' ? 'green' : 'red'">
              {{ testResult.status }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="执行时间">
            {{ testResult.endTime - testResult.startTime }}ms
          </a-descriptions-item>
        </a-descriptions>
        <pre class="test-output">{{ JSON.stringify(testResult.nodeOutputs, null, 2) }}</pre>
      </div>
    </a-modal>

    <!-- 发布模态框 -->
    <a-modal
      v-model:open="showPublishModal"
      title="发布工作流"
      width="600px"
      @ok="publishWorkflow"
      okText="发布"
      cancelText="取消"
    >
      <a-form layout="vertical">
        <a-alert 
          message="请选择工作流的发布方式" 
          type="info" 
          show-icon 
          style="margin-bottom: 16px"
        />
        
        <a-form-item label="发布类型">
          <a-radio-group v-model:value="publishConfig.type" style="width: 100%">
            <a-radio value="single" style="display: block; margin-bottom: 12px">
              <div>
                <strong>单轮对话</strong>
                <div class="help-text">每次调用都是独立的，不保留历史上下文</div>
              </div>
            </a-radio>
            <a-radio value="multi" style="display: block; margin-bottom: 12px">
              <div>
                <strong>多轮对话</strong>
                <div class="help-text">保留对话历史，支持上下文理解</div>
              </div>
            </a-radio>
            <a-radio value="mcp" style="display: block">
              <div>
                <strong>MCP接口</strong>
                <div class="help-text">作为MCP工具接口对外暴露</div>
              </div>
            </a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="名称" required>
          <a-input v-model:value="publishConfig.name" placeholder="请输入名称" />
        </a-form-item>

        <a-form-item label="描述">
          <a-textarea v-model:value="publishConfig.description" :rows="3" 
            placeholder="简要描述该工作流的功能" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import {
  ArrowLeftOutlined,
  SaveOutlined,
  PlayCircleOutlined,
  ThunderboltOutlined,
  ApiOutlined,
  BranchesOutlined,
  SyncOutlined,
  PlaySquareOutlined,
  CheckCircleOutlined,
  PlusOutlined,
  DeleteOutlined,
  RocketOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { workflowApi, agentApi } from '../api'

const router = useRouter()
const route = useRoute()

// 工作流数据
const workflow = reactive({
  id: null,
  name: '新工作流',
  description: '',
  version: 1,
  nodes: [],
  edges: [],
  globalVariables: {}
})

// Vue Flow 元素
const elements = ref([])

// 节点类型定义
const nodeTypes = [
  { type: 'start', label: '开始', icon: PlaySquareOutlined, color: '#52c41a' },
  { type: 'llm', label: 'LLM节点', icon: ThunderboltOutlined, color: '#1890ff' },
  { type: 'http', label: 'HTTP请求', icon: ApiOutlined, color: '#722ed1' },
  { type: 'if', label: 'IF条件', icon: BranchesOutlined, color: '#fa8c16' },
  { type: 'loop', label: '循环', icon: SyncOutlined, color: '#13c2c2' },
  { type: 'end', label: '结束', icon: CheckCircleOutlined, color: '#f5222d' }
]

// 选中的节点
const selectedNode = ref(null)
const showNodeConfig = ref(false)

// 测试相关
const showTestModal = ref(false)
const testInput = ref('{\n  "message": "Hello"\n}')
const testResult = ref(null)

// 调试相关
const debugResults = ref({})
const debuggingNode = ref(null)

// 发布相关
const showPublishModal = ref(false)
const publishConfig = reactive({
  type: 'single',
  name: '',
  description: ''
})

// 知识库列表
const knowledgeBases = ref([])

// 拖拽开始
const onDragStart = (event, nodeType) => {
  event.dataTransfer.setData('application/vueflow', JSON.stringify(nodeType))
  event.dataTransfer.effectAllowed = 'move'
}

// 拖拽放置
const onDrop = (event) => {
  const type = JSON.parse(event.dataTransfer.getData('application/vueflow'))
  
  const position = {
    x: event.offsetX,
    y: event.offsetY
  }

  const newNode = {
    id: `node_${Date.now()}`,
    type: type.type,
    position,
    data: {
      label: type.label,
      description: '',
      config: type.type === 'start' ? { inputParams: [] } : {},
      inputMapping: []
    }
  }

  elements.value.push(newNode)
}

// 节点点击
const onNodeClick = ({ node }) => {
  selectedNode.value = node
  showNodeConfig.value = true
}

// 连线点击
const onEdgeClick = ({ edge }) => {
  console.log('Edge clicked:', edge)
}

// 连线创建
const onConnect = (params) => {
  const newEdge = {
    id: `edge_${Date.now()}`,
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle,
    targetHandle: params.targetHandle
  }
  elements.value.push(newEdge)
}

// 获取前置节点
const getPreviousNodes = (currentNodeId) => {
  // 查找所有指向当前节点的连线
  const incomingEdges = elements.value.filter(
    el => el.target === currentNodeId && el.source
  )
  
  // 获取源节点
  const previousNodeIds = incomingEdges.map(edge => edge.source)
  
  return elements.value.filter(
    el => previousNodeIds.includes(el.id) && !el.source
  )
}

// 获取可用字段
const getAvailableFields = (result) => {
  if (!result || typeof result !== 'object') return {}
  
  const fields = {}
  const flatten = (obj, prefix = '') => {
    for (const key in obj) {
      const path = prefix ? `${prefix}.${key}` : key
      if (typeof obj[key] === 'object' && obj[key] !== null && !Array.isArray(obj[key])) {
        flatten(obj[key], path)
      } else {
        fields[path] = `$.nodes.${selectedNode.value.id}.${path}`
      }
    }
  }
  flatten(result)
  return fields
}

// 获取字段预览
const getFieldPreview = (result, key) => {
  const keys = key.split('.')
  let value = result
  for (const k of keys) {
    value = value?.[k]
  }
  if (value === null || value === undefined) return 'null'
  if (typeof value === 'string') return value.substring(0, 20)
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value).substring(0, 20)
}

// 调试节点
const debugNode = async (node) => {
  debuggingNode.value = node.id
  try {
    // 构建调试工作流（只包含当前节点和其前置节点）
    const previousNodes = getPreviousNodes(node.id)
    const nodesToDebug = [...previousNodes, node]
    
    // 获取相关连线
    const nodeIds = nodesToDebug.map(n => n.id)
    const relevantEdges = elements.value.filter(
      el => el.source && nodeIds.includes(el.source) && nodeIds.includes(el.target)
    )
    
    const debugWorkflow = {
      name: '调试工作流',
      nodes: nodesToDebug.map(n => ({
        id: n.id,
        type: n.type.toUpperCase(),
        name: n.data.label,
        config: n.data.config || {},
        inputMapping: (n.data.inputMapping || []).reduce((acc, m) => {
          if (m.key && m.value) acc[m.key] = m.value
          return acc
        }, {})
      })),
      edges: relevantEdges.map(edge => ({
        id: edge.id,
        source: edge.source,
        target: edge.target
      }))
    }
    
    // 构建输入数据：从 START 节点获取测试值
    let input = {}
    const startNode = elements.value.find(el => el.type === 'start' && !el.source)
    if (startNode && startNode.data.config.inputParams) {
      // 使用 START 节点定义的测试值
      startNode.data.config.inputParams.forEach(param => {
        if (param.name && param.testValue) {
          try {
            // 尝试解析 JSON 格式的测试值
            if (param.type === 'object' || param.type === 'array') {
              input[param.name] = JSON.parse(param.testValue)
            } else if (param.type === 'number') {
              input[param.name] = Number(param.testValue)
            } else if (param.type === 'boolean') {
              input[param.name] = param.testValue === 'true'
            } else {
              input[param.name] = param.testValue
            }
          } catch (e) {
            // 解析失败，使用原始值
            input[param.name] = param.testValue
          }
        }
      })
    } else {
      // 如果没有 START 节点定义，使用默认测试输入
      try {
        input = JSON.parse(testInput.value)
      } catch (e) {
        input = { message: '测试消息' }
      }
    }
    
    const response = await workflowApi.testWorkflow(debugWorkflow, input)
    
    // 保存调试结果
    if (response.data.nodeOutputs && response.data.nodeOutputs[node.id]) {
      debugResults.value[node.id] = response.data.nodeOutputs[node.id]
      message.success('节点调试执行成功')
    } else {
      message.warning('节点未返回结果')
    }
  } catch (error) {
    console.error('调试节点失败:', error)
    message.error('调试失败: ' + error.message)
  } finally {
    debuggingNode.value = null
  }
}

// 添加输入映射
const addInputMapping = () => {
  if (!selectedNode.value.data.inputMapping) {
    selectedNode.value.data.inputMapping = []
  }
  selectedNode.value.data.inputMapping.push({ key: '', value: '' })
}

// 删除输入映射
const removeInputMapping = (index) => {
  selectedNode.value.data.inputMapping.splice(index, 1)
}

// 添加输入参数（START节点）
const addInputParam = () => {
  if (!selectedNode.value.data.config.inputParams) {
    selectedNode.value.data.config.inputParams = []
  }
  selectedNode.value.data.config.inputParams.push({ 
    name: '', 
    type: 'string', 
    testValue: '' 
  })
}

// 删除输入参数（START节点）
const removeInputParam = (index) => {
  selectedNode.value.data.config.inputParams.splice(index, 1)
}

// 保存工作流
const saveWorkflow = async () => {
  try {
    // 转换 elements 为工作流格式
    workflow.nodes = elements.value
      .filter(el => !el.source) // 过滤出节点
      .map(node => ({
        id: node.id,
        type: node.type.toUpperCase(),
        name: node.data.label,
        description: node.data.description,
        position: node.position,
        config: node.data.config || {},
        inputMapping: (node.data.inputMapping || []).reduce((acc, m) => {
          if (m.key && m.value) acc[m.key] = m.value
          return acc
        }, {})
      }))

    workflow.edges = elements.value
      .filter(el => el.source) // 过滤出连线
      .map(edge => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        sourceHandle: edge.sourceHandle,
        targetHandle: edge.targetHandle
      }))

    if (workflow.id) {
      await workflowApi.updateWorkflow(workflow.id, workflow)
      message.success('工作流已更新')
    } else {
      const response = await workflowApi.createWorkflow(workflow)
      workflow.id = response.data.id
      message.success('工作流已创建')
    }
  } catch (error) {
    console.error('保存工作流失败:', error)
    message.error('保存失败: ' + error.message)
  }
}

// 测试运行
const runTest = async () => {
  try {
    const input = JSON.parse(testInput.value)
    
    // 构建测试工作流
    const testWorkflow = {
      ...workflow,
      nodes: elements.value
        .filter(el => !el.source)
        .map(node => ({
          id: node.id,
          type: node.type.toUpperCase(),
          name: node.data.label,
          config: node.data.config || {},
          inputMapping: (node.data.inputMapping || []).reduce((acc, m) => {
            if (m.key && m.value) acc[m.key] = m.value
            return acc
          }, {})
        })),
      edges: elements.value
        .filter(el => el.source)
        .map(edge => ({
          id: edge.id,
          source: edge.source,
          target: edge.target
        }))
    }

    const response = await workflowApi.testWorkflow(testWorkflow, input)
    testResult.value = response.data
    message.success('测试完成')
  } catch (error) {
    console.error('测试失败:', error)
    message.error('测试失败: ' + error.message)
  }
}

// 返回首页
const backToHome = () => {
  router.push('/')
}

// 发布工作流
const publishWorkflow = async () => {
  try {
    if (!publishConfig.name) {
      message.warning('请输入名称')
      return
    }
    
    // 先保存工作流
    await saveWorkflow()
    
    if (!workflow.id) {
      message.error('工作流保存失败')
      return
    }
    
    // 根据发布类型创建Agent或MCP接口
    if (publishConfig.type === 'mcp') {
      // TODO: 创建MCP接口
      message.success('MCP接口发布成功（功能待实现）')
    } else {
      // 从Workflow创建Agent
      const agentData = {
        name: publishConfig.name,
        description: publishConfig.description,
        type: publishConfig.type === 'multi' ? 'MULTI_TURN' : 'SINGLE_TURN'
      }
      
      await agentApi.publishFromWorkflow(workflow.id, agentData)
      message.success(`Agent创建成功：${publishConfig.type === 'multi' ? '多轮对话' : '单轮对话'}`)
    }
    
    showPublishModal.value = false
    // 跳转到首页
    setTimeout(() => {
      router.push('/')
    }, 1000)
  } catch (error) {
    console.error('发布失败:', error)
    message.error('发布失败: ' + error.message)
  }
}

// 加载知识库列表
const loadKnowledgeBases = async () => {
  try {
    // TODO: 调用API加载知识库列表
    // const response = await knowledgeBaseApi.getList()
    // knowledgeBases.value = response.data
    
    // 模拟数据
    knowledgeBases.value = [
      { id: '1', name: '产品知识库', documentCount: 15 },
      { id: '2', name: '技术文档', documentCount: 28 },
      { id: '3', name: '常见问题', documentCount: 42 }
    ]
  } catch (error) {
    console.error('加载知识库失败:', error)
  }
}

// 初始化
onMounted(async () => {
  // 加载知识库列表
  await loadKnowledgeBases()
  
  const workflowId = route.query.workflowId
  if (workflowId) {
    try {
      const response = await workflowApi.getWorkflow(workflowId)
      Object.assign(workflow, response.data)
      
      // 转换为 Vue Flow 格式
      elements.value = [
        ...workflow.nodes.map(node => ({
          id: node.id,
          type: node.type.toLowerCase(),
          position: node.position,
          data: {
            label: node.name,
            description: node.description,
            config: node.config,
            inputMapping: Object.entries(node.inputMapping || {}).map(([k, v]) => ({
              key: k,
              value: v
            }))
          }
        })),
        ...workflow.edges.map(edge => ({
          id: edge.id,
          source: edge.source,
          target: edge.target
        }))
      ]
    } catch (error) {
      console.error('加载工作流失败:', error)
      message.error('加载工作流失败')
    }
  } else {
    // 新建工作流，添加默认的开始节点
    elements.value.push({
      id: 'start_1',
      type: 'start',
      position: { x: 100, y: 100 },
      data: { 
        label: '开始', 
        config: { inputParams: [] }, 
        inputMapping: [] 
      }
    })
  }
})
</script>

<style scoped>
.workflow-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.workflow-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.node-panel {
  position: fixed;
  left: 20px;
  top: 100px;
  width: 180px;
  background: white;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  z-index: 5;
}

.node-panel h3 {
  margin: 0 0 16px 0;
  font-size: 14px;
  font-weight: 600;
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f5f5;
  border-radius: 6px;
  cursor: move;
  transition: all 0.2s;
}

.node-item:hover {
  background: #e6f7ff;
  transform: translateX(4px);
}

.flow-container {
  flex: 1;
  position: relative;
}

.param-mapping {
  margin-top: 12px;
}

.param-definition {
  margin-top: 12px;
}

.mapping-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.param-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.test-output {
  margin-top: 12px;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 4px;
  max-height: 300px;
  overflow: auto;
  font-size: 12px;
}

.debug-result {
  margin-top: 12px;
}

.debug-output {
  margin-top: 8px;
  padding: 12px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
  max-height: 300px;
  overflow: auto;
  font-size: 12px;
  color: #389e0d;
}

.help-text {
  font-size: 12px;
  color: #999;
}

.help-text.success {
  color: #52c41a;
  font-weight: 500;
}
</style>
