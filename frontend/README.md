# Spring AI Agent - Vue 前端

## 项目结构

```
frontend/
├── src/
│   ├── api/           # API 接口封装
│   ├── router/        # 路由配置
│   ├── views/         # 页面组件
│   │   ├── ChatView.vue      # 多轮对话页面
│   │   └── RAGView.vue       # RAG 知识库页面
│   ├── App.vue        # 主应用组件
│   └── main.js        # 入口文件
├── vite.config.js     # Vite 配置
└── package.json
```

## 快速开始

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

前端将运行在 `http://localhost:5173`

### 3. 启动后端服务

在项目根目录：

```bash
mvn spring-boot:run
```

后端将运行在 `http://localhost:8080`

## 功能说明

### 多轮对话 (`/`)

- 支持与 AI 进行多轮对话
- 自动保持会话上下文
- 新建对话和清空历史功能

### RAG 知识库 (`/rag`)

- 创建和管理知识库
- 上传文档（支持 PDF、Word）
- 基于知识库的智能问答
- 支持拖拽上传

## 技术栈

- **Vue 3** - 前端框架
- **Vite** - 构建工具
- **Vue Router** - 路由管理
- **Axios** - HTTP 客户端

## 构建生产版本

```bash
npm run build
```

构建产物将输出到 `dist/` 目录。
