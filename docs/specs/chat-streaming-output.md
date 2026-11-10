# Spec：聊天 AI 输出改为流式输出 + 前端流式 Markdown 渲染

状态：本地草稿。搬运到 GitHub Issues 时打 `ready-for-agent` 标签（无需额外 triage）。
背景词汇：见 `CONTEXT.md`（会话 / 消息 / 附件 / fileId 词汇沿用）；遵循 ADR 0001（附件体系与 RAG 文档体系分离），流式路径原样沿用附件语义。

## Problem Statement

聊天用户（普通聊天、Agent 对话、知识库问答三个页面）发送消息后，页面长时间空白等待，直到模型生成完整回复才一次性显示；回复越长，等待越久，且没有任何"正在生成"的过程反馈。同时回复以纯文本展示，模型输出的 Markdown（标题、列表、表格、代码块、公式）全部以原文符号裸露，观感差且代码不可读。模型若开启深度思考，思考过程完全不可见。

## Solution

后端把对话链路的模型输出改为 SSE 流式：新增一个流式端点，模型每产出一个增量文本就实时推送一条事件；深度思考内容、MCP 工具调用与结果、会话元数据、错误都以结构化事件表达。旧同步端点保留不动。

前端引入 Vue3 流式 Markdown 渲染插件（`x-markdown-vue`，与 `@ant-design/x-markdown` 能力对齐的 Vue 生态实现），三个对话页面统一改为"先建空消息、增量追加"的打字机模式：回复边生成边以 Markdown 渲染（代码高亮、数学公式），思考过程以可折叠区块展示，提供"停止生成"按钮，MCP 工具调用过程可视化。

## User Stories

1. As a 聊天用户，I want 回复逐字流式地出现在页面上，so that 不用盯着空白等待完整回复
2. As a 聊天用户，I want 回复按 Markdown 渲染（标题、列表、表格、粗体、链接），so that 阅读结构与排版一目了然
3. As a 聊天用户，I want 代码块带语法高亮，so that 代码内容清晰可读
4. As a 聊天用户，I want 代码块支持一键复制，so that 能直接取用模型给出的代码
5. As a 聊天用户，I want 数学公式（行内与块级）正确渲染，so that 不必阅读 LaTeX 原文
6. As a 聊天用户，I want 回复生成过程中点击"停止生成"立即中断，so that 对跑偏的回复不必等它生成完
7. As a 聊天用户，I want 流式过程中连接断开或出错时看到明确的中文错误提示，so that 我知道这次回复不完整、可以重发
8. As a 聊天用户，I want 新会话仍然收到欢迎语，so that 开场体验不因流式而缺失
9. As a 聊天用户，I want 加载历史会话时旧消息同样按 Markdown 渲染，so that 新旧消息观感一致
10. As a Agent 用户，I want 与 Agent 的对话同样流式输出，so that 三个对话页面体验一致
11. As a Agent 用户，I want Agent 调用 MCP 工具时看到"正在调用某工具 / 工具结果"的过程展示，so that 明白回复为什么需要等待
12. As a Agent 用户，I want MCP 工具结果回喂后的最终回答仍流式输出，so that 过程与结论衔接自然
13. As a 知识库问答用户，I want 基于知识库提问时回答流式输出，so that 长答案的等待体验改善
14. As a 知识库问答用户，I want 知识库检索行为与引用逻辑保持不变，so that 流式改造不影响检索准确性
15. As a 开启深度思考的用户，I want 模型思考过程以可折叠区块展示（默认收起），so that 想看时能展开、平时不干扰
16. As a 开启深度思考的用户，I want 思考内容与正文回复在视觉上明确区分，so that 不把思考过程误当答案
17. As a 开发者，I want 后端只有一条流式链路新缝（对话服务的流式变体方法），so that 测试面与维护面最小
18. As a 开发者，I want 旧同步端点原样保留，so that 现有调用方与既有测试零改动可用
19. As a 开发者，I want 前端流式请求与事件解析集中在一处封装，so that 三个视图复用同一实现而非各自手写
20. As a 开发者，I want Markdown 渲染组件统一封装一处，so that 样式与行为在三视图间一致
21. As a 开发者，I want 前端新增依赖保持最小（渲染器必装、代码高亮与公式插件引入、图表插件暂缓），so that 打包体积与升级风险可控
22. As a 开发者，I want 测试沿用现有离线模式（占位 API key + Mock 外部依赖），so that CI 与本地均可运行
23. As a 运维者，I want 会话历史仍只存内存、重启即清，so that 不引入持久化与清理负担
24. As a 运维者，I want 流式响应在开发代理与生产同源两种部署下都可用，so that 部署形态不构成限制

## Implementation Decisions

- **改造范围**：仅对话链路（POST `/api/chat/message`）——普通聊天、Agent 对话、知识库问答三个前端页面共用该端点，一处改造三处受益。工作流 LLM 节点、独立 RAG 问答端点、提示词生成端点维持同步。
- **后端唯一新缝**：对话服务新增流式变体方法（入参与现有 chat 方法同构），内部复用既有的 Prompt 构建、附件注入、RAG 分支、会话历史管理逻辑，仅把阻塞式模型调用替换为流式调用；RAG 知识库分支相应提供流式变体。模型流式 API 采用 `ChatModel.stream(Prompt)` 返回增量流。
- **新端点**：新增 POST `/api/chat/message/stream`，SSE（`text/event-stream`）。Controller 保持薄，返回响应式流类型；Servlet 栈无需引入 WebFlux 依赖（reactor 为现有模型 starter 的传递依赖，参考项目已在同栈验证可行）。旧同步端点及其错误语义原样保留。
- **事件协议**（SSE data 为 JSON，`type` 字段区分）：
  - `delta`：正文增量文本（`content`）；
  - `thinking`：深度思考增量文本（`content`），来源于模型增量元数据中的 reasoning 字段；
  - `tool_call`：MCP 工具调用（`tool`、`arguments`）；
  - `tool_result`：工具执行结果（`tool`、`result`）；
  - `done`：流结束元数据，对齐现有响应字段（`sessionId`、`conversationRound`、`welcomeMessage`、`agentId`）；
  - `error`：流内错误（`error`，中文提示）——SSE 响应提交后无法再改状态码，错误必须在流内表达。
- **MCP 工具调用循环**：流式下先缓冲第一轮完整输出 → 沿用现有正则协议检测工具调用 → 检测到则发 `tool_call`/`tool_result` 事件，二次模型调用改为流式下发；未检测到则把缓冲内容作为 `delta` 下发。有工具调用时用户会先等第一轮缓冲完成，属于接受的体验折衷。
- **深度思考**：从流式增量的输出元数据提取思考文本，以 `thinking` 事件推送；思考内容不写入会话历史（多轮上下文仍只含正文），空白思考增量跳过。
- **历史一致性**：助手消息在流正常结束后写入会话历史；沿用"空白回复不入历史"的现有语义。客户端中断时已生成的部分文本不入历史（与刷新丢失语义一致，重启即清的内存存储不受影响）。
- **异步与超时**：配置文件显式增加 MVC 异步请求超时（当前未配置，长回复会撞容器默认异步超时）；流取消/完成/异常/超时路径均需释放订阅与清理回调。
- **前端依赖**：新增 `x-markdown-vue`（流式 Markdown 渲染器，GFM、代码高亮、公式、流式淡入动画），一并引入 `shiki` + `shiki-stream`（语法高亮）与 `katex`（公式，需引其样式）；Mermaid 图表插件本期不安装（渲染器对未安装插件自动降级为普通代码块）。渲染保持默认安全（不解析原始 HTML）。
- **前端流式消费**：API 层新增流式请求函数（`fetch` + `ReadableStream` 解析 SSE，支持 `AbortController` 中断），不引入 EventSource 库；封装一个组合式函数把事件流折叠为响应式状态（正文增量、思考增量、工具过程、完成元数据、错误），三个视图复用；axios 保留给普通接口。
- **视图改造**：三个对话页面统一改为"先建空助手消息 + 增量追加内容"，消息正文用统一的 Markdown 渲染组件展示（历史消息同样走该组件）；新增思考折叠面板与"停止生成"按钮；加载、错误回滚、欢迎语逻辑沿用现有模式适配流式。
- **部署与代理**：保持现有 CORS 与同源部署不变；流式响应需防代理缓冲（关注开发代理与反向代理的缓冲行为，必要时响应头声明不缓冲）。

## Testing Decisions

- **好测试的标准**：只测外部行为——事件序列与载荷（无工具：`delta`…→`done` 且元数据正确；有工具：`tool_call`→`tool_result`→`delta`…→`done`；异常：`error` 事件、历史不写入）、历史写入时机与空白回复语义、中断后资源清理。不测响应式流内部组装细节。
- **测试模块**：对话服务测试（扩展既有测试类：既有用例补流式桩适配，新增流式行为用例）+ 控制器层冒烟测试（MockMvc 异步请求验证 SSE 事件序列）。
- **先例**：沿用现有服务测试模式——`@SpringBootTest` + 对外部依赖（ChatModel、RAG、MCP 客户端等）`@MockBean` 替换，运行需占位 API key（`mvn test -Dzhipuai-api-key=dummy`）。
- **前端**：项目无前端测试框架，本期以手动验收清单覆盖：三视图打字机效果、Markdown/代码高亮/公式渲染、停止按钮、MCP 过程展示、错误提示、历史会话渲染。

## Out of Scope

- 工作流 LLM 节点流式（工作流引擎为同步递归整体返回，端到端流式需另立执行端点与上下文协议，另行立项）。
- 独立 RAG 问答端点与提示词生成端点的流式化。
- 会话历史、工具过程的持久化。
- 思考过程的开关控制（Agent 级配置项）与思考内容入历史。
- Mermaid 图表渲染（渲染器支持但插件暂不安装，自动降级）。
- SSE 断线自动重连与续传（中断即终止本轮回复）。
- 移动端适配与无障碍专项优化。

## Further Notes

- 发布：搬运到 GitHub Issues 时打 `ready-for-agent` 标签；本 spec 由对话合成（已过 grill 确认），未经额外 interview。
- 后端实现参考（已验证）：参考教学项目同款技术栈下——SSE 端点直接返回响应式流（`text/event-stream`）无需 WebFlux；流式 + 工具调用采用"禁用自动工具执行 + 流中检测 + 手动执行"模式；深度思考文本从流式增量元数据的 reasoning 字段提取。
- 前端选型说明：`@ant-design/x-markdown` 为 React 组件，不可用于 Vue3；`x-markdown-vue` 为 Vue3 生态对齐实现（注意内容 prop 名为 `markdown` 而非 `content`，样式需单独引入）。备选：`@antdv-next/x-markdown`（同源移植但 0.1.x 成熟度风险）、`markstream-vue`（轻量）。
- 相关决策记录：不新增 ADR；ADR 0001 附件语义在流式路径原样沿用。
- 实现落点提示（仅供搬运后参考，以代码为准）：后端在对话服务、对话控制器、RAG 服务与全局配置；前端在 API 层、新增组合式函数与三个对话视图。
