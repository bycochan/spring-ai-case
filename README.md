# 🤖 Spring AI Case - 企业级AI Agent工作流平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-Latest-blue.svg)](https://spring.io/projects/spring-ai)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.x-green.svg)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

企业级AI Agent工作流平台 - 基于Spring AI和Vue 3构建，支持可视化工作流编排、RAG知识库、MCP工具集成。提供单轮/多轮对话、智能Agent创建、节点级调试等功能，适用于智能客服、业务自动化等场景。

## ✨ 核心特性

### 🎯 多模式AI对话
- **单轮对话**：独立问答，无上下文依赖，适合快速查询
- **多轮对话**：保持对话历史，支持上下文理解，适合深度交互
- **智能Agent**：可配置个性化AI助手（系统提示词、开场白、头像、知识库）

### 📊 可视化工作流编排
- **拖拽式编辑器**：基于Vue Flow的直观可视化界面
- **丰富节点类型**：
  - 🚀 START节点 - 定义工作流输入参数
  - 🧠 LLM节点 - 调用大语言模型（支持MCP工具和知识库增强）
  - 🌐 HTTP节点 - 调用外部REST API
  - 🔀 IF节点 - 条件分支判断（支持多种运算符）
  - 🔁 LOOP节点 - 循环处理（forEach/while）
- **节点级调试**：单步执行，实时查看中间结果
- **智能参数映射**：JSONPath表达式引用节点输出（如`$.nodes.nodeId.output.field`）
- **一键发布**：工作流可发布为单轮/多轮对话Agent或MCP接口

### 📚 RAG知识库
- **文档管理**：支持上传PDF、TXT、Markdown等多种格式
- **向量化存储**：自动将文档转换为向量并存储
- **语义检索**：基于向量相似度的智能检索
- **知识增强对话**：结合文档内容提供精准答案
- **多知识库管理**：支持创建、切换、删除多个知识库

### 🛠️ MCP工具协议
- **服务器管理**：注册和管理MCP服务器
- **自动工具发现**：自动识别可用工具
- **Function Calling**：大模型自动选择并调用合适的工具
- **多传输协议**：支持HTTP和SSE（Server-Sent Events）
- **实时监控**：工具调用状态和结果实时反馈

## 🚀 快速开始

### 环境要求

- **Java**: 17+
- **Node.js**: 16+
- **Maven**: 3.8+
- **阿里云千问API Key**

### 1. 克隆项目

```bash
git clone https://github.com/yourusername/spring-ai-case.git
cd spring-ai-case
```

### 2. 配置后端

在 `src/main/resources/application.yml` 中配置阿里云API Key：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:your-api-key-here}
```

或通过环境变量设置：

```bash
export DASHSCOPE_API_KEY=your-api-key-here
```

### 3. 安装前端依赖

```bash
cd frontend
npm install
```

### 4. 启动开发环境

**后端启动**（包含前端）：
```bash
mvn spring-boot:run
```

**或仅前端开发**：
```bash
cd frontend
npm run dev
```

### 5. 访问应用

- 前端界面：http://localhost:5173 (开发模式) 或 http://localhost:8080 (生产模式)
- 后端API：http://localhost:8080/api

## 📖 使用指南

### 创建Agent

1. 在首页点击"新建Agent"
2. 填写Agent信息：
   - 名称和描述
   - 选择头像（emoji）
   - 配置系统提示词和开场白
   - 可选：关联知识库
   - 可选：启用MCP工具
3. 保存后即可在对话列表中使用

### 创建工作流

1. 点击"创建工作流"进入编辑器
2. 从左侧面板拖拽节点到画布
3. 连接节点形成工作流
4. 配置每个节点：
   - START节点：定义输入参数和测试值
   - LLM节点：配置提示词、选择模型、启用MCP/知识库
   - HTTP节点：配置URL、请求方法、请求体
   - IF/LOOP节点：配置条件和循环逻辑
5. 配置参数映射：使用JSONPath引用前置节点输出
6. 调试节点：单独测试每个节点的执行结果
7. 测试整体工作流
8. 发布为Agent或MCP接口

### 管理知识库

1. 进入"知识库"页面
2. 创建新知识库并命名
3. 上传文档文件
4. 系统自动向量化并存储
5. 在Agent或LLM节点中选择该知识库

### 配置MCP工具

1. 进入"MCP工具"页面
2. 注册MCP服务器：
   - 填写服务器名称和URL
   - 选择传输协议（HTTP/SSE）
3. 启用服务器
4. 系统自动发现可用工具
5. 在对话或LLM节点中启用MCP即可使用

## 🏗️ 技术架构

### 后端技术栈

```
Spring Boot 3.4.5
├── Spring AI (阿里云千问集成)
├── Vector Store (向量数据库)
├── OkHttp (HTTP客户端)
├── Gson (JSON处理)
├── JSONPath (数据提取)
└── Lombok (代码简化)
```

### 前端技术栈

```
Vue 3 + Vite
├── Ant Design Vue (UI组件库)
├── Vue Flow (工作流可视化)
├── Vue Router (路由管理)
├── Axios (HTTP客户端)
└── Markdown-it (Markdown渲染)
```

### 项目结构

```
spring-ai-case/
├── src/main/java/com/tes/springai/
│   ├── controller/          # REST API控制器
│   │   ├── AgentController.java
│   │   ├── ChatController.java
│   │   ├── RAGController.java
│   │   ├── MCPController.java
│   │   └── WorkflowController.java
│   ├── service/             # 业务逻辑层
│   │   ├── workflow/        # 工作流引擎
│   │   │   ├── WorkflowEngine.java
│   │   │   └── executors/   # 节点执行器
│   │   ├── AgentService.java
│   │   ├── ChatService.java
│   │   └── MCPService.java
│   ├── model/               # 数据模型
│   │   ├── Agent.java
│   │   ├── workflow/        # 工作流模型
│   │   └── mcp/             # MCP模型
│   └── config/              # 配置类
├── frontend/                # Vue前端项目
│   ├── src/
│   │   ├── views/           # 页面组件
│   │   ├── components/      # 公共组件
│   │   ├── api/             # API接口定义
│   │   └── router/          # 路由配置
│   └── package.json
└── pom.xml                  # Maven配置
```

## 🔌 API文档

### Chat API

- `POST /api/chat/message` - 发送消息
- `GET /api/chat/history/{sessionId}` - 获取会话历史
- `DELETE /api/chat/session/{sessionId}` - 清除会话

### Agent API

- `GET /api/agents` - 获取所有Agent
- `POST /api/agents` - 创建Agent
- `PUT /api/agents/{id}` - 更新Agent
- `DELETE /api/agents/{id}` - 删除Agent
- `POST /api/agents/publish-from-workflow` - 从工作流发布Agent

### Workflow API

- `GET /api/workflows` - 获取所有工作流
- `POST /api/workflows` - 创建工作流
- `PUT /api/workflows/{id}` - 更新工作流
- `POST /api/workflows/{id}/execute` - 执行工作流
- `POST /api/workflows/test` - 测试工作流

### RAG API

- `POST /api/rag/upload` - 上传文档
- `POST /api/rag/query` - RAG查询
- `GET /api/rag/knowledge-bases` - 获取所有知识库
- `POST /api/rag/knowledge-base` - 创建知识库

### MCP API

- `GET /api/mcp/servers` - 获取所有MCP服务器
- `POST /api/mcp/servers` - 注册MCP服务器
- `GET /api/mcp/tools` - 获取所有工具
- `POST /api/mcp/tools/call` - 调用工具

## 🎨 应用场景

### 智能客服
- 基于企业知识库的自动问答
- 多轮对话理解用户意图
- 自动调用业务系统API查询信息

### 业务流程自动化
- 可视化编排复杂业务流程
- 条件分支和循环处理
- 集成外部系统和工具

### 企业知识管理
- 文档智能检索与问答
- 知识沉淀与复用
- 跨部门知识共享

### 多工具协作
- 集成各类MCP工具
- 大模型自动选择合适工具
- 复杂任务自动化执行

## 🛠️ 开发指南

### 添加新的节点类型

1. 在 `WorkflowNode.NodeType` 枚举中添加新类型
2. 实现 `NodeExecutor` 接口
3. 在前端 `WorkflowEditorView.vue` 中添加节点配置UI
4. 在 `WorkflowEngine` 中注册执行器

### 扩展MCP工具

1. 实现MCP协议的服务器端
2. 在系统中注册MCP服务器
3. 工具自动被发现并可用

### 自定义知识库

1. 实现 `VectorStore` 接口
2. 在配置中注入自定义实现
3. 上传文档自动使用新的存储

## 📦 构建部署

### 开发环境构建

```bash
# 构建前端
cd frontend
npm run build

# 构建后端（包含前端资源）
cd ..
mvn clean package
```

### 生产环境部署

```bash
# 运行JAR包
java -jar target/spring-ai-case-1.0.0.jar

# 或使用Docker
docker build -t spring-ai-case .
docker run -p 8080:8080 -e DASHSCOPE_API_KEY=your-key spring-ai-case
```

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交Pull Request

## 📄 许可证

本项目采用 Apache 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件

## 🙏 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - Spring生态的AI框架
- [Vue Flow](https://vueflow.dev/) - Vue 3工作流编辑器
- [Ant Design Vue](https://antdv.com/) - 企业级UI组件库
- [阿里云千问](https://dashscope.aliyun.com/) - 大语言模型API

## 📞 联系方式

- 项目主页：https://github.com/yourusername/spring-ai-case
- 问题反馈：https://github.com/yourusername/spring-ai-case/issues

---

⭐ 如果这个项目对你有帮助，请给一个Star！
