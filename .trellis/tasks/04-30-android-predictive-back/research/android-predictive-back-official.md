# Android Predictive Back 官方资料结论

## 官方入口

* Predictive Back Gesture: https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
* Compose setup: https://developer.android.com/develop/ui/compose/system/predictive-back-setup

## 关键结论

* Android 15（API 35）及以上，面向新 SDK 的应用会默认展示系统级预测式返回动画。
* Android 13/14 上可以通过开发者选项预览预测式返回效果。
* 如果应用需要继续兼容旧式返回回调，可在 manifest 的 `application` 或 `activity` 上配置 `android:enableOnBackInvokedCallback`；接入预测式返回时应启用该能力。
* `androidx.activity.compose.BackHandler` 负责普通返回拦截，但只给“返回已触发”的结果，不提供拖拽进度，无法单独做完整预测式动效。
* Compose 自定义返回进度需要使用 Activity Compose 提供的 `PredictiveBackHandler`，根据手势 progress 驱动页面平移、缩放或透明度。
* `Navigation Compose 2.8.0+` 的 `NavHost` 对预测式返回有内建支持。项目当前使用 `navigation-compose 2.9.6`，版本满足要求。

## 对本项目的影响

* 项目当前 `compileSdk = 36`、`targetSdk = 36`、`minSdk = 28`，基础 SDK 约束满足。
* 项目当前主导航不是 `NavHost`，而是 `NavigationTab`、`showEditScreen`、`settingsDestination` 等状态变量配合多个 `BackHandler`。因此不能只依赖 Navigation Compose 自动获得应用内预测式返回。
* 可分阶段接入：
  * MVP：启用 manifest 回调能力，补一个可复用的 Compose 预测式返回包装，用于文件预览、编辑页、设置二级页等最明显的“返回很硬”的场景。
  * 后续：把主界面内页迁移到 `NavHost`，让应用内页面返回由 Navigation Compose 自动承接预测式返回。

## 风险与兼容

* `PredictiveBackHandler` 属于较新的 Activity Compose API，使用时要保留普通 `BackHandler` 作为旧系统/非手势路径兜底。
* 过度改动主导航会影响 SMB 连接恢复、文件路径状态、传输管理等核心流程；首轮不建议重构整套导航。
