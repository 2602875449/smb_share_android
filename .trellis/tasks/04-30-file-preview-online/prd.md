# 文件在线预览（图片/文本）

## Goal

为 SMB 文件列表页新增「在线预览」能力：用户点击图片或文本文件时，无需下载即可在应用内直接预览内容。通过流式读取 SMB 远端文件，提升用户体验，减少不必要的下载。

## What I already know

* `SMBFileRepository.getFileInputStream(filePath)` 已存在，返回远端文件的 `InputStream`，但当前未用于预览
* `FileTypeHelper` 已有按扩展名判断文件类型的辅助方法（图片 / 文档 / 视频等）
* 文件列表 `FileListIntent` 中无预览相关 Intent，点击文件目前仅触发 `ShowFileMenu`
* 导航为 MainActivity 状态机（非 NavHost），新增预览需用 `remember` 状态或对话框
* 图片加载库：项目当前**未引入** Coil / Glide 等图片加载库；Material3 + Compose Foundation 已引入
* 依赖限制：AGENTS.md 禁止随意引入第三方 UI 库，但功能性库（图片加载）需说明原因

## Assumptions (temporary)

* 预览入口：在现有「文件操作菜单」中新增「预览」选项（而非替换点击行为）
* 图片预览：支持 JPEG / PNG / GIF / WebP，流式加载，支持缩放
* 文本预览：支持常见文本文件（.txt / .log / .md / .xml / .json 等），限制最大读取大小（如 1 MB）避免 OOM
* 预览 UI：新增 `PreviewScreen`（全屏） 或 BottomSheet/Dialog（局部）
* 大文件保护：超过限制大小时提示用户，不强制预览

## Open Questions

* **Q1（阻塞）**: 预览 UI 形式——全屏新页面 vs 弹出 Dialog/BottomSheet？
* **Q2（功能边界）**: 图片预览是否需要引入 Coil？还是纯 Compose 流式解码（`BitmapFactory` + `ImageBitmap`）？

## Requirements (evolving)

* 文件菜单新增「预览」选项，仅对可预览类型（图片 / 文本）显示
* 图片预览：流式从 SMB 读取 → 显示，支持双指缩放、单击退出
* 文本预览：流式从 SMB 读取（最多 1 MB）→ 可滚动文本，支持单击退出
* 加载中显示进度指示器；加载失败显示错误信息
* 支持浅色/深色主题

## Acceptance Criteria (evolving)

* [ ] 图片文件（jpg/png/gif/webp）在菜单中显示「预览」选项
* [ ] 点击预览后能看到图片内容（无需下载）
* [ ] 文本文件（txt/log/md/xml/json）在菜单中显示「预览」选项
* [ ] 点击预览后能看到文本内容，可滚动
* [ ] 文件 > 1 MB 时，文本预览提示截断警告
* [ ] 加载失败时显示错误信息，可关闭预览
* [ ] 单测覆盖文件类型判断逻辑（已有 FileTypeHelper）
* [ ] UI 兼容浅色/深色主题

## Definition of Done

* [ ] 单测通过
* [ ] Lint / 编译无报错
* [ ] 浅色 / 深色主题下可用
* [ ] 加载 / 空 / 错误态均已处理

## Out of Scope

* 视频预览
* PDF 预览（可后续扩展）
* 本地文件预览（只针对 SMB 远端）
* 预览页内的编辑能力

## Technical Notes

* 相关文件：
  * `ui/filelist/FileListScreen.kt`（文件菜单 UI）
  * `ui/filelist/FileListViewModel.kt`（业务逻辑）
  * `ui/filelist/FileListIntent.kt`（Intent 定义）
  * `ui/filelist/FileListState.kt`（State 定义）
  * `data/repository/SMBFileRepository.kt`（流式读取 API）
  * `util/FileTypeHelper.kt`（类型判断）
* 导航方案：用 `remember { mutableStateOf<FileItem?>(null) }` 持有「当前预览文件」，非空时覆盖渲染 `PreviewScreen`（仿照 MainActivity 现有状态机模式）
* 图片加载方案待定：Coil（推荐，异步/缓存）vs 纯 BitmapFactory（无额外依赖）
