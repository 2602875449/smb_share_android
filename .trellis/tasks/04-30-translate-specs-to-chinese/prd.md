# Translate Trellis Specs To Chinese

## Goal

将上一轮生成的 Trellis 项目规范文档从英文改为简体中文，让中文开发者和后续 AI 子代理更容易理解项目约定。

## What I already know

- 用户明确希望“将这些变更规范更改为中文”，原因是“开发能够更好的理解”。
- 需要保留上一轮已经确认的约束：这是现有 Android/Kotlin 代码库，暂时不要重构代码。
- 规范内容应继续只描述当前代码示例已经支持的模式，不写理想化或不存在的服务端/后端架构。
- 主要规范文件位于 `.trellis/spec/backend/*.md`。

## Requirements

- 将 `.trellis/spec/backend/` 下已填充的规范正文改为简体中文。
- 保留真实代码路径、类名、函数名、命令和 Markdown 链接等技术标识，不翻译路径或符号名。
- 保留“本仓库不是服务端后端，backend 在此表示 Android 应用的数据、领域、服务、持久化和非 UI 业务逻辑层”的语义。
- 更新 `.trellis/spec/backend/index.md` 中的语言说明，使其要求中文文档。
- 不修改 app 源码、Gradle 配置、`AGENTS.md` 或与翻译无关的 Trellis 初始化文件。

## Acceptance Criteria

- [x] `.trellis/spec/backend/*.md` 主要内容均为简体中文。
- [x] 文档仍引用真实存在的 `app/src/...` 示例路径。
- [x] 文档中不新增要求重构、接入新框架、服务端 API、HTTP 响应格式或不存在模块的规则。
- [x] 常见英文占位短语不再出现。

## Definition of Done

- `trellis-implement` 完成翻译。
- `trellis-check` 检查文档范围、中文可读性、示例路径和约束一致性。
- 本次不要求运行 Gradle 测试；这是文档-only 修改。

## Out of Scope

- 不翻译应用源码注释之外的代码内容。
- 不改动 UI、业务逻辑、测试、Gradle 或依赖。
- 不调整上一轮规范的实际规则含义。
