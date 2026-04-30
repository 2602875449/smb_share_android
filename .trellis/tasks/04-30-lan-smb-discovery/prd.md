# 局域网自动发现 SMB 主机

## Goal

降低新建 SMB 连接的门槛：用户进入新建连接页后，可以启动局域网自动扫描，看到同网段内可用的 SMB 主机，点击某一台后自动填充服务器地址和配置名称，用户只需要继续填写共享名、用户名和密码即可连接。

## What I already know

* 当前连接入口包括 `ConnectionScreen`、`EditConnectionScreen`、`ConnectionViewModel`、`ConnectionState` 和 `ConnectionIntent`。
* 当前连接配置模型 `SMBConfig` 已支持服务器地址、端口、共享名、用户名、密码、匿名登录。
* 用户希望同时覆盖两类发现来源：
  * mDNS / Bonjour：监听 `_smb._tcp`，覆盖 macOS 和现代 Linux Samba。
  * NetBIOS：扫描局域网 C 段，结合 445 端口探测和 NBT 名称查询，覆盖 Windows 机器。
* 当前项目使用 Kotlin、Compose、协程、Robolectric、MockK；新增业务逻辑应优先放在 data/domain/ViewModel 层，不直接塞进 Composable。
* UI 必须使用 `MaterialTheme.colorScheme` 和字符串资源，兼容浅色/深色主题。

## Assumptions

* MVP 只发现主机，不枚举共享目录；共享名仍由用户手动填写。
* mDNS 和 NetBIOS 的结果需要去重，优先以 IP 地址合并；显示名称优先使用主机名，其次使用 mDNS service name，最后回退 IP。
* 扫描范围以当前 Wi-Fi 的 IPv4 /24 网段为主；无法确定网段时，仅保留 mDNS 扫描并给出友好错误。
* 发现功能不应阻塞已有手动输入流程；扫描失败或无结果时仍可手动填写。
* 局域网广播、NetBIOS 广播和 mDNS 通常无法跨子网/路由传播；例如手机在 `192.168.2.4`、Win10 SMB 服务在 `192.168.1.55` 时，手动连接可能正常，但默认扫描可能发现不了。

## Requirements

* 新增 SMB 主机发现数据模型，至少包含：
  * `displayName`
  * `address`
  * `port`
  * `source`（mDNS / NetBIOS）
* 新增可测试的发现抽象，例如 `SmbHostDiscovery`，由一个 Android 实现协调 mDNS 和 NetBIOS 扫描。
* mDNS 实现使用 Android 原生 `NsdManager` 监听 `_smb._tcp.`，解析服务后输出主机 IP 和端口。
* NetBIOS 实现扫描本机 IPv4 /24 网段候选地址：
  * 先用短超时检查 TCP 445 是否可达，控制并发，避免 UI 卡顿和过度占用网络。
  * 对可达主机发送 NBT 名称查询，解析 NetBIOS 主机名；解析失败时仍保留 IP 结果。
* `ConnectionViewModel` 增加扫描相关状态和 Intent：
  * 启动扫描
  * 停止/取消扫描
  * 选择发现主机并填充表单
  * 清理扫描错误
* 新建连接页显示扫描入口、加载中、空结果、错误和发现列表。
* 在不改变默认自动扫描行为的前提下，新增可选手动目标探测：
  * 用户可输入单个 IPv4 地址，例如 `192.168.1.55`。
  * 用户可输入 IPv4 CIDR 网段，例如 `192.168.1.0/24`。
  * 手动目标探测通过 TCP 445 可达性和 NetBIOS 名称查询确认 SMB 主机，不依赖当前手机网段广播。
* 点击发现项后自动填充服务器地址、端口和配置名称；不得覆盖用户已经输入的共享名、用户名、密码。
* 扫描生命周期应可取消，离开页面或 ViewModel 清理时停止后台扫描。
* 新增单元测试覆盖发现结果去重/合并、NetBIOS 名称解析、ViewModel 选择发现主机填充表单、扫描失败状态。

## Acceptance Criteria

* [ ] 用户打开新建连接页，可以看到“扫描局域网 SMB 主机”的入口。
* [ ] 扫描中显示加载状态，并允许用户取消或重新扫描。
* [ ] mDNS `_smb._tcp` 发现到的主机能显示在列表中。
* [ ] NetBIOS/445 扫描发现到的 Windows/SMB 主机能显示在列表中。
* [ ] 当 SMB 主机与手机位于不同子网但路由可达时，用户输入目标 IP 或 `/24`-`/32` CIDR 后能主动探测并显示可达主机。
* [ ] 发现列表按 IP 去重，同一个主机不会重复出现。
* [ ] 点击发现项后，服务器地址自动填入表单，端口使用发现端口或默认 445。
* [ ] 未发现主机、网络不可用、权限/系统服务异常等状态有中文提示，且不影响手动填写。
* [ ] 新增或更新的单元测试通过。
* [ ] `./gradlew testDebugUnitTest` 通过。

## Definition of Done

* Tests added/updated for discovery logic and ViewModel behavior.
* Lint / typecheck / unit tests pass where feasible.
* UI 文案加入中英文字符串资源，中文文案使用简体中文。
* 不引入新的第三方 UI 库。
* 不在 Composable 中执行网络扫描或阻塞 IO。

## Out of Scope

* 自动枚举 SMB 共享目录。
* 自动登录、凭据发现或密码保存策略改动。
* 后台持续监控。
* 自动跨路由广播发现；跨子网场景通过用户手动输入 IP/CIDR 探测覆盖。
* Android 设备自身作为 SMB 服务端广播。

## Technical Notes

* 可能涉及文件：
  * `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionIntent.kt`
  * `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionState.kt`
  * `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
  * `app/src/main/java/com/qi/smbshare/ui/connection/EditConnectionScreen.kt`
  * `app/src/main/res/values/strings.xml`
  * `app/src/main/res/values-zh/strings.xml`
  * `app/src/main/AndroidManifest.xml`
* 建议新增包：
  * `app/src/main/java/com/qi/smbshare/data/discovery/`
  * `app/src/test/java/com/qi/smbshare/data/discovery/`
* Android mDNS 可能需要持有 Wi-Fi multicast lock 才能在部分设备上稳定接收组播；如实现中使用，需要补充 `CHANGE_WIFI_MULTICAST_STATE` 权限。
* NetBIOS 和端口扫描必须在 `Dispatchers.IO` 中执行，并设置短超时与并发上限。

## Research References

* 待补充：`.trellis/tasks/04-30-lan-smb-discovery/research/android-smb-discovery.md`
