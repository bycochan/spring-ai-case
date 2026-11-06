# AGENTS.md

企业级 AI Agent 工作流平台：Spring Boot 3.4.5 + Spring AI 1.1.2（智谱 GLM）后端，Vue 3 + Vite 前端，单仓库，Maven 统一构建。后端代码在 `src/main/java/com/tes/springai`，前端在 `frontend/`。

## 常用命令

Windows cmd 环境。后端运行需要 API key，经启动参数传入（密钥只走启动参数或环境变量，不写入仓库文件）。

```bat
:: 后端开发运行（复用已构建的 frontend/dist，跳过前端构建）
mvn spring-boot:run -Dskip.npm=true -Dskip.installnodenpm=true -Dspring-boot.run.jvmArguments=-Dzhipuai-api-key=<你的key>

:: 前端开发（Vite 端口 5173，/api 代理到后端 8089）
cd frontend && npm run dev

:: 全量构建（自动安装 Node v22.12.0、npm install、build，dist 复制进 jar 的 static/）
mvn clean package
```

参考 `start-server.cmd`（后台启动，日志写入 `boot.log`）。

## 事实速览（以代码为准，README 部分内容已过时）

- 大模型是智谱 GLM（`spring-ai-starter-model-zhipuai`，模型 `glm-5.3-flash`）。README 中"阿里云千问 / DASHSCOPE_API_KEY"的描述已过时，勿照抄。
- 后端端口 **8089**（`src/main/resources/application.yml`），README 写的 8080 已过时。前端生产模式由后端同源提供（`SpaController` + `static/`）。
- Milvus 向量库默认关闭（`milvus.enabled: false`）。涉及 RAG 的功能需开启该开关并运行本地 Milvus（localhost:19530）。
- 无数据库：Agent、会话历史、知识库、工作流、MCP 服务器注册等全部状态存于内存 `ConcurrentHashMap`，重启即失。新增功能默认沿用内存存储，除非任务明确要求持久化。
- `src/test/` 有少量 `@SpringBootTest` 集成测试（`ConversationAgentServiceTest`、`VectorStoreServiceTest`）：依赖通过 `@MockBean` 替换，运行需传入占位 API key（`mvn test -Dzhipuai-api-key=dummy`）。改动 `chat` 等方法签名时需同步更新测试调用。

## 代码地图

后端 `com.tes.springai`（Lombok `@Slf4j`/`@Builder`，jakarta 注解）：

- `controller/` — REST API：`/api/chat`、`/api/agents`、`/api/workflows`、`/api/rag`、`/api/mcp`、`/api/mcp-servers`；`SpaController` 兜底前端路由
- `service/` — 业务逻辑；`service/workflow/` 是工作流引擎（`WorkflowEngine` + `NodeExecutor` 接口 + `executors/` 各节点执行器）
- `model/` — POJO，分 `workflow/`、`mcp/` 子包
- `config/` — 装配：CORS（`WebConfig`）、Milvus（`MilvusConfig`）、执行器注册（`WorkflowConfig`）

前端 `frontend/src`（Vue 3 组合式 API + Ant Design Vue + Vue Flow）：

- `views/` — 六个页面：`HomeView`、`ChatView`、`AgentChatView`、`WorkflowEditorView`、`RAGView`、`MCPView`
- `api/index.js` — 所有 axios 调用集中于此，生产模式同源、开发模式 `http://localhost:8089`
- `router/` — 路由；`components/` — 公共组件

## 添加工作流节点类型

1. `model/workflow/WorkflowNode.NodeType` 枚举加新类型
2. 在 `service/workflow/executors/` 新建执行器，实现 `NodeExecutor`（`execute` + `getSupportedType`）
3. 后端注册是自动的：`WorkflowConfig` 收集全部 `NodeExecutor` Bean 装入 `Map<NodeType, NodeExecutor>`，引擎按类型分发，无需手工登记
4. 前端 `WorkflowEditorView.vue` 增加该节点的画布节点与配置面板

节点间参数映射用 JSONPath 引用上游输出（`$.nodes.<nodeId>.output.<field>`），节点输出保持 JSON 友好结构。

## 约定

- 代码注释、UI 文案、模型提示词均为中文，新增代码保持一致。
- Controller 保持薄，业务逻辑放 Service；模型对象用 Lombok Builder 构造。
- 前端所有接口调用走 `frontend/src/api/index.js`，组件内不直接使用 axios。
