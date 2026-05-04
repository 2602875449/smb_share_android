# 审查未提交更改并修复 bug

## Goal

审查当前工作区未提交的 SMB 连接, 文件列表, 删除, 传输管理相关更改, 发现明确 bug 时进行最小范围修复, 并通过直接相关验证确认修复有效.

## What I already know

* 用户要求审查未提交更改, 如果有 bug 则修复.
* 当前工作区已有多处未提交代码改动, 涉及数据层, domain usecase, transfer service, file list UI 和 transfer UI.
* 本次任务不应回滚用户已有改动, 只在确认问题后做必要修复.

## Requirements

* 审查 `git diff HEAD` 中的业务和测试改动.
* 优先关注行为回归, 崩溃风险, 并发/资源释放问题, 错误处理问题和缺失验证.
* 如发现明确 bug, 直接在相关文件内做最小修复.
* 修改后运行与本次修复直接相关的 Gradle 验证.

## Acceptance Criteria

* [ ] 已列出并处理审查发现的明确 bug.
* [ ] 修复不回滚无关用户改动.
* [ ] 直接相关测试或编译检查通过, 或说明阻塞原因.

## Out of Scope

* 不进行大规模重构.
* 不提交 commit.
* 不修改与当前 diff 无关的功能.

## Technical Notes

* 适用规范索引: `.trellis/spec/backend/index.md`.
