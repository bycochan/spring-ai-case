# Spec：前端流式组件支持渲染 ECharts 图表

状态：本地草稿。搬运到 GitHub Issues 时打 `ready-for-agent` 标签（无需额外 triage）。
背景词汇：见 `CONTEXT.md`（Agent / 会话 / 消息词汇沿用）；不涉及 ADR 0001（附件体系与本特性无关）。本 spec 是聊天流式输出 spec 中"图表插件暂缓"决策的延续。

## Problem Statement

对话用户（普通聊天、Agent 对话、知识库问答三个页面）想让模型把数据画成图表时，模型只能输出文字描述或 Markdown 表格，数据关系不直观。模型其实能生成 ECharts 配置，但前端渲染管线没有对应出口，配置只能以代码块原文裸露，用户看不到真正的图表。

## Solution

模型在回复正文中用 ` ```echarts ` 围栏代码块输出纯 JSON 的 ECharts option；统一的 Markdown 渲染组件将该语言块接管为图表渲染：流式生成期间显示"图表将在回复完成后渲染"占位，回复完成后自动渲染为 ECharts 图表；配置不合法时优雅回退为源码展示并给出明确提示。三个对话入口行为一致，后端零改动。

## User Stories

1. As a 聊天用户，I want 模型输出的 echarts 代码块自动渲染为图表，so that 直观理解数据而不是阅读 JSON
2. As a 聊天用户，I want 流式生成期间看到"图表将在回复完成后渲染"的占位提示，so that 知道图表在等待回复完成而不是页面卡死
3. As a 聊天用户，I want 回复完成后图表自动出现且不闪烁，so that 获得流畅的打字机观感
4. As a 聊天用户，I want 图表配置非法时回退为源码展示并附提示，so that 仍能看到模型输出的原始配置而不是一片空白
5. As a 聊天用户，I want 图表容器随窗口与面板尺寸变化自适应重绘，so that 窄屏或侧栏变化时图表不截断
6. As a 聊天用户，I want 中断生成后已流出的内容按现有规则处理（半截配置回退源码），so that 中断时刻的内容仍可见、行为可预期
7. As a 聊天用户，I want 重新打开历史会话时旧消息中的图表仍能渲染，so that 翻阅历史不丢图
8. As a 聊天用户，I want 图表配色跟随明暗主题，so that 视觉风格与页面一致
9. As an Agent 用户，I want 无需在 Agent 上开启任何新开关图表即可生效，so that 零配置获得可视化能力
10. As an Agent 用户，I want 在系统提示词中引导模型输出 echarts 块来稳定触发图表，so that 按场景控制可视化频率
11. As a 知识库问答用户，I want 问答中的统计数据同样以图表呈现，so that 与其他对话入口体验一致
12. As an Agent 用户，I want 工具调用结果之后正文里的 echarts 块照常渲染，so that 工具与图表组合使用不受影响
13. As a 开发者，I want ECharts 库按需懒加载，so that 不拖累首屏加载体积
14. As a 开发者，I want 图表实例在消息卸载与会话切换时正确销毁，so that 长会话不积累内存泄漏
15. As a 开发者，I want 后端与流式协议零改动，so that 此特性纯属前端渲染增强、风险面最小
16. As a 开发者，I want 渲染失败原因输出到控制台日志，so that 能排查模型输出质量问题

## Implementation Decisions

- **接入缝（已确认，唯一新缝）**：复用统一 Markdown 渲染组件的 codeXRender 扩展点，新增一个图表块组件仅接管 `echarts` 语言块，与既有 mermaid 接管组件同构；不做事后扫描替换，不新增其他语言别名。
- **内容格式（已确认）**：块内容为纯 JSON 的 ECharts option，`JSON.parse` 直出后 `setOption`；不做任何 JS 求值，不支持 formatter 等函数配置，与全局"默认不解析原始 HTML"的安全基线一致。
- **流式行为**：沿用 mermaid 接管组件的既有模式——流式生成中不解析渲染（半截 JSON 必然失败），显示占位提示；回复完成后防抖统一渲染；用序号守卫防内容快速变更导致的竞态。
- **失败降级**：JSON 解析失败或渲染异常时，回退为源码展示并显示"图表渲染失败，已回退为源码展示"提示，控制台 warn 具体原因。
- **加载与生命周期**：动态 import echarts 控制首包体积；容器用 ResizeObserver 触发 `resize()`；组件卸载时 `dispose()` 释放实例。
- **主题**：沿用接管组件拿到的明暗标识选择 ECharts 主题（dark / default）。
- **协议**：消息正文仍为纯 markdown 字符串，SSE 事件、后端、内存存储均零改动；历史会话回放天然支持。

## Testing Decisions

- 测试缝即上述唯一的 codeXRender 接缝：断言组件外部行为——输入（代码块内容 + 流式状态 + 主题）到输出（图表 / 占位 / 源码回退）三态，只测外部可见行为，不测内部实现细节。
- 前端当前无测试设施，本期以手动验收为主：让模型输出合法 echarts 块（成功渲染）、中途观察占位（流式态）、输出非法 JSON（源码回退），并在三个对话入口各验一轮一致性；另验窗口缩放自适应与会话切换无泄漏。
- 后端零改动，不触碰既有 `@SpringBootTest` 集成测试。

## Out of Scope

- JS 函数配置与沙箱求值（formatter 回调等）
- 图表导出图片、配置编辑器、图表与表格联动
- 其他图表语言别名或多种图表库并存
- 后端提示词的内置改造（引导模型输出 echarts 块由用户在 Agent 系统提示词中自行配置）
- 深色主题的全站改造

## Further Notes

- echarts 块接管后会替换内置代码块外观（同 mermaid 现状），源码仅在失败回退时可见。
- 后续如引入前端组件测试设施（如 Vitest），可直接沿 codeXRender 缝补自动化用例，无需改动被测对象。
