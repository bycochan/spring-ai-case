import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import AgentChatView from '../views/AgentChatView.vue'
import ChatView from '../views/ChatView.vue'
import RAGView from '../views/RAGView.vue'
import MCPView from '../views/MCPView.vue'
import WorkflowEditorView from '../views/WorkflowEditorView.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: HomeView
  },
  {
    path: '/agent-chat',
    name: 'AgentChat',
    component: AgentChatView
  },
  {
    path: '/chat',
    name: 'Chat',
    component: ChatView
  },
  {
    path: '/rag',
    name: 'RAG',
    component: RAGView
  },
  {
    path: '/mcp',
    name: 'MCP',
    component: MCPView
  },
  {
    path: '/workflow',
    name: 'WorkflowEditor',
    component: WorkflowEditorView
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
