# SMBJ 共享枚举能力核对

## 结论

当前项目使用 `com.hierynomus:smbj:0.14.0`。本地 Gradle 缓存中的 `smbj-0.14.0-sources.jar` 和 `smbj-0.14.0.jar` 未包含报告所说的 `ServerService` 类。现有代码也没有 `SmbTransportImpl.kt` 或 `listShares()`。

## 证据

* `gradle/libs.versions.toml` 中 `smbj = "0.14.0"`。
* 源码包中与 share 相关的类包括:
  * `com/hierynomus/msfscc/fileinformation/ShareInfo.java`
  * `com/hierynomus/smbj/share/Share.java`
  * `com/hierynomus/smbj/share/DiskShare.java`
  * `com/hierynomus/smbj/share/PipeShare.java`
  * `com/hierynomus/smbj/share/PrinterShare.java`
* 源码包和 class jar 中未检索到 `ServerService`, `SRVSVC` 或等价服务封装类名。

## 对本任务的影响

* 如果产品需求是“用户手动输入共享名后浏览文件”, 当前不需要新增共享枚举, 重点应放在连接稳定性和错误处理。
* 如果产品需求是“输入服务器后自动列出共享”, 不能直接按报告中的 `ServerService` 说法实现, 需要先确认 SMBJ 0.14.0 可用的 RPC 管道调用方式, 或引入/编写最小 SRVSVC `NetShareEnum` 实现。
* 引入共享枚举会影响连接编辑页、发现流程、错误分类和测试范围, 应作为明确功能项管理。
