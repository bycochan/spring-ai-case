<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { 
  HomeOutlined,
  BookOutlined, 
  ApiOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const selectedKeys = ref(['home'])
const collapsed = ref(false)

watch(
  () => route.path,
  (newPath) => {
    if (newPath === '/') {
      selectedKeys.value = ['home']
    } else if (newPath === '/rag') {
      selectedKeys.value = ['rag']
    } else if (newPath === '/mcp') {
      selectedKeys.value = ['mcp']
    }
  },
  { immediate: true }
)
</script>

<template>
  <div id="app-container">
    <!-- 左侧菜单栏 -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      :trigger="null"
      collapsible
      class="sidebar"
      :width="240"
    >
      <div class="logo">
        <span class="logo-icon">🤖</span>
        <span v-if="!collapsed" class="logo-text">Spring AI Agent</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="inline"
        theme="dark"
        class="nav-menu"
      >
        <a-menu-item key="home" @click="$router.push('/')">
          <template #icon>
            <HomeOutlined />
          </template>
          <span>主页</span>
        </a-menu-item>
        <a-menu-item key="rag" @click="$router.push('/rag')">
          <template #icon>
            <BookOutlined />
          </template>
          <span>RAG 知识库</span>
        </a-menu-item>
        <a-menu-item key="mcp" @click="$router.push('/mcp')">
          <template #icon>
            <ApiOutlined />
          </template>
          <span>MCP 工具</span>
        </a-menu-item>
      </a-menu>
      <div class="collapse-trigger" @click="collapsed = !collapsed">
        <MenuUnfoldOutlined v-if="collapsed" />
        <MenuFoldOutlined v-else />
      </div>
    </a-layout-sider>
    
    <!-- 右侧内容区 -->
    <div class="content">
      <router-view />
    </div>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  height: 100%;
  overflow: hidden;
  /* removed rigid min-width to improve responsiveness */
  min-width: 320px;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background: #f0f2f5;
}

#app-container {
  height: 100vh;
  display: flex;
  flex-direction: row;
  width: 100%;
}

.ant-layout-sider {
  flex-shrink: 0;
}

/* 侧边栏样式 */
.sidebar {
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);
  position: relative;
  transition: all 0.2s;
  height: 100vh;
  overflow-y: auto;
}

.sidebar :deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: white;
  font-size: 20px;
  font-weight: 700;
  padding: 20px 16px;
  cursor: pointer;
  transition: all 0.3s;
  user-select: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo:hover {
  opacity: 0.95;
}

.logo-icon {
  font-size: 30px;
  line-height: 1;
}

.logo-text {
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.nav-menu {
  background: transparent;
  border: none;
  flex: 1;
  padding: 16px 0;
}

.nav-menu :deep(.ant-menu-item) {
  color: rgba(255, 255, 255, 0.95);
  font-weight: 500;
  font-size: 15px;
  border-radius: 8px;
  margin: 6px 12px;
  padding: 0 14px;
  height: 48px;
  line-height: 48px;
  transition: all 0.24s;
}

.nav-menu :deep(.ant-menu-item:hover) {
  color: white;
  background: rgba(255, 255, 255, 0.12);
}

.nav-menu :deep(.ant-menu-item-selected) {
  color: white;
  background: rgba(255, 255, 255, 0.2);
}

.nav-menu :deep(.ant-menu-item-selected::after) {
  border-right: 3px solid rgba(255,255,255,0.95);
}

.nav-menu :deep(.ant-menu-item .anticon) {
  font-size: 18px;
}

.collapse-trigger {
  color: white;
  text-align: center;
  padding: 14px;
  cursor: pointer;
  transition: all 0.3s;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 18px;
}

.collapse-trigger:hover {
  background: rgba(255, 255, 255, 0.06);
}

.content {
  padding: 16px 20px;
  height: 100vh;
  overflow: hidden;
  background: #f0f2f5;
  flex: 1;
}

/* responsive rules */
@media (max-width: 992px) {
  .ant-layout-sider {
    display: none; /* hide sidebar on smaller screens - keep simple */
  }
  #app-container {
    flex-direction: column;
  }
  .content {
    height: auto;
    padding: 12px;
  }
}

@media (min-width: 993px) and (max-width: 1280px) {
  .sidebar {
    width: 200px !important;
  }
}
</style>
