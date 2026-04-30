# Backend 开发规范

> 本项目 Android 应用的数据、领域、服务、持久化和非 UI 业务逻辑层规范。

---

## 概览

本仓库不包含服务端后端。本目录中的文件记录的是当前 Android 应用已有的模式，后续 agent 在修改数据、领域、服务、持久化以及面向 UI 的相关逻辑时应遵循这些模式。

---

## 规范索引

| 规范 | 说明 | 状态 |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Android 模块组织和层边界 | 已填充 |
| [Database Guidelines](./database-guidelines.md) | Room、DataStore、序列化和持久化测试 | 已填充 |
| [Error Handling](./error-handling.md) | 应用错误分类、Result 传递、UI 展示 | 已填充 |
| [Quality Guidelines](./quality-guidelines.md) | Kotlin、Compose、主题、本地化和测试模式 | 已填充 |
| [Logging Guidelines](./logging-guidelines.md) | Android Log 用法、tag、级别和敏感数据边界 | 已填充 |

---

## 开发前检查清单

在本仓库编码前，先阅读下面的相关文件：

- 涉及结构、状态归属或包位置时：阅读 [Directory Structure](./directory-structure.md)
- 涉及 Room、DataStore、entity 转换或序列化时：阅读 [Database Guidelines](./database-guidelines.md)
- 涉及异常映射、Result 传递、Snackbar 错误或传输重试时：阅读 [Error Handling](./error-handling.md)
- 涉及日志、tag、级别或敏感数据时：阅读 [Logging Guidelines](./logging-guidelines.md)
- 涉及 Compose 状态、主题颜色、本地化、测试或验证命令时：阅读 [Quality Guidelines](./quality-guidelines.md)

这些规范只描述当前代码示例已经支持的模式。不要把它们理解为可以重构应用架构或添加不受支持框架的许可。

---

**语言**：所有文档应使用**简体中文**编写。
