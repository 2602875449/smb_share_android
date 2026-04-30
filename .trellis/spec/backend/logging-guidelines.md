# 日志规范

> 当前 Android/Kotlin 应用的日志约定。

---

## 日志库

项目使用平台 `android.util.Log`。

不要在常规功能工作中引入新的日志库。

示例：

- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
- `app/src/main/java/com/qi/smbshare/MainActivity.kt`

---

## Tag

当前代码通常使用类名作为 tag。

示例：

- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt` 中的 file-level `private const val TAG = "ConnectSMBUseCase"`
- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt` 中的 file-level `private const val TAG = "SMBFileRepository"`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt` 中的 companion object `private const val TAG = "TransferService"`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt` 中的 instance property `private val TAG = "ConnectionViewModel"`

优先沿用正在修改文件的本地风格。

---

## 消息语言

现有日志使用简体中文。

示例：

- `ConnectSMBUseCase` 记录连接开始、成功和失败。
- `SMBFileRepository` 记录文件列表、打开、上传操作和失败详情。
- `TransferService` 记录 service 生命周期和传输 intent action。

触碰这些区域时，新日志应遵循相同的中文消息风格。

---

## 级别

当前用法：

- `Log.d` 用于操作开始、操作成功、生命周期事件、已解析 intent action、路径、任务 ID 和进度相关诊断。
- `Log.w` 用于可恢复兜底路径，例如尝试第二种 SMB 目录打开策略。
- `Log.e` 用于操作失败、缺少必要参数、解析失败和捕获到的 exception。

示例：

- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`

---

## 记录什么

当前代码记录：

- 测试或连接时的 SMB server address、port、share name 和 anonymous flag。
- 文件操作使用的 SMB 路径。
- 连接、文件和传输流程的操作开始/成功/失败。
- SMB 文件操作失败时的 exception class 和 exception message。
- 传输 task ID 和 service action。
- repository/data 操作需要的 config ID。

示例：

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`

---

## 敏感数据

不要记录原始密码或完整序列化 SMB 配置 JSON。

记录连接测试输入时，`SMBConnectionManager.testConnection` 是脱敏 secret 的参考。

示例：

- `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`

当前代码会记录一些操作标识符和路径。保持该行为一致，但避免添加会暴露凭据或完整配置载荷的新日志。

---

## 边界

- 不要添加结构化日志要求；项目当前使用普通 Android log 消息。
- 不要添加日志框架。
- 不要在此添加分析或事件追踪规则；本代码库不存在 analytics 层。
