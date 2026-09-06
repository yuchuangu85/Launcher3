# Launcher3 (android17) → Gradle 工程迁移记录

目标：把 AOSP android17-release 的 Launcher3 (QuickStep 变体) 源码迁移为可独立 Gradle 构建的
Android 工程，目标运行环境为 OrbStack VM 内已编译好的 android17 系统镜像（privileged/system_ext）。

## 源
| 组件 | AOSP 源路径 (VM ~/aosp) | 工程内路径 |
|---|---|---|
| Launcher3 全部源码/资源 | packages/apps/Launcher3 | ./ (工程根即 Launcher3 源码) |
| 依赖模块 (frameworks/libs/systemui) | frameworks/libs/systemui | platform_frameworks_libs/frameworks_libs_systemui/ |
| SystemUI shared 参考 | frameworks/base/packages/SystemUI/shared | platform_frameworks_libs/systemui_shared/ (仅备份参考，不参与编译) |
| 预编译 framework/module jars | ~/aosp/out/soong/.intermediates/... | prebuilts/framework/*.jar |

拷贝均为 rsync --exclude=.git，源码零修改。

## 结构（Lawnchair 布局：Launcher3 源码即根工程）
- 根工程 = Android Application（Launcher3QuickStep 等价体）
  java.srcDirs = src, quickstep/src, quickstep/dagger, quickstep/src_protolog,
                 modules/{widgetpicker,appfunctions,concurrent}/src, generated-src
  res.srcDirs = res, quickstep/res           (soong merged-R: package com.android.launcher3)
  manifest = quickstep/AndroidManifest.xml
- Gradle 子模块（platform_frameworks_libs 下，源码库）：
  iconloaderlib, animationlib, contextualeducationlib, viewcapturelib, usertypelib,
  dynamiccolors(res-only), cuebarlib, acecommon, aceclient

## 依赖策略（用户已确认）
1. 隐藏 framework API：用 VM 已编译产物 frameworks/base/framework android_common/turbine-combined/
   framework.jar 作 compileOnly 并强制 classpath 首位（Lawnchair addFrameworkJar 手法）。
2. 其余平台模块（SystemUISharedLib / SystemUI-statsd / WindowManager-Shell(-shared,-AOSP) /
   各 aconfig flags lib / framework-statsd stubs / com_android_launcher3_flags_lib）→ VM out 的
   javac/turbine jar compileOnly，不引入源码。com.android.launcher3.Flags 等 aconfig 生成类由
   com_android_launcher3_flags_lib.jar 提供，无需本地跑 aconfig。
3. 带官方 gradle 且依赖干净的小模块 → 源码子模块；官方 gradle 中指向内网工程(:NexusLauncher:*)
   的依赖已改写为本地 jar/maven。

## BuildConfig
tools/buildconfig.sh 生成 com.android.launcher3.BuildConfig 于 generated-src/（macOS bash3 缺
declare -A，但默认 flag 输出正确；-d/-e 变体需 bash4+ 环境）。

## 与 soong 的偏差（暂不改源码）
- 不跑 protologtool：quickstep/src_protolog 原样编译（直调 framework ProtoLog 类）。
- quickstep/AndroidManifest-launcher.xml 与 AndroidManifest-common.xml 未并入 app manifest
  （影响运行期组件声明；编译无碍）。
- widgetpicker/appfunctions/concurrent 并入根工程编译（各自 manifest/命名空间留运行期处理）。
- 无 minify；测试不参与；构建目标 assembleDebug。
- framework/statsd/flags jar 仅 compileOnly。

## 里程碑
- [x] 源码与依赖模块拷贝（零修改）
- [x] Gradle 骨架：settings / root(app) / 9 模块 / BuildConfig
- [x] assembleDebug 首次通过（2026-09-06；修复记录见下）
- [ ] VM 模拟器安装运行（manifest、权限、protolog 等）

## assembleDebug 编译期修复记录（2026-09-06）
均为 Gradle 独立构建专属问题，soong 路径不受影响：
- kapt (Kotlin 2.4.10) stub 把 `Array<Pair<TaskId, TaskModel?>>` 参数渲染成非法
  `kotlin.Pair[]<kotlin.Pair<...>>` → TaskViewModel.kt mapToTaskData 改收
  `List<Pair<...>>`，调用处 `taskModels.toList()`（私有方法，语义等价）。
- LooperExecutor（guava AbstractListeningExecutorService）的 `submit(Callable<T>)`
  重载对 Kotlin 2.4.10 不可见（只剩 submit(Runnable)）→ ModelProxyProvider /
  TaskbarManagerImplWrapper 两处改 `FutureTask + execute() + get()`。
- ext_stub 的 AutomatedPackageListener 桩写错（class + Runnable ctor），真实 API 是
  接口 `onAutomatedPackagesChanged(String, List<String>, UserHandle)`（见 VM
  frameworks/base/libs/computercontrol）→ 按真实签名改接口。
- tools/buildconfig.sh 用了 `declare -A`（bash4+），macOS bash3 下所有 flag 退化成
  `= com.android.launcher3;` → 改为普通变量实现，重新生成 BuildConfig.java。
- wm-shell-shared 的 res 在 soong 里并入 launcher 包 R，Gradle 构建无此机制，手工镜像：
  - quickstep/res/drawable/floating_dismiss_background(.xml) / _ic_close(.xml)
    （源：frameworks/base/libs/WindowManager/Shell/shared/res/drawable/）
  - quickstep/res/values/wm_shell_config.xml（integer to_desktop_animation_duration_ms=336）
  - quickstep/res/values/wm_shell_dimens.xml（drag_zone_bubble_fold/tablet）
- prebuilts/framework 重建：tools/fetch-framework-prebuilts.sh 从 VM out/soong 拉取
  12 个 jar 并 sha256 校验（jar 清单取自 .gradle executionHistory）。

## 变体打包（2026-09-06）
soong 的 launcher 家族有 4 个 app 目标（Launcher3 纯版 / Launcher3QuickStep / Launcher3Go /
Launcher3QuickStepGo，见 Android.bp）。Gradle 工程建模 quickstep 内核的两档：
- flavorDimensions "launcher"：flavor = quickstep（默认）/ go。
  go = quickstep 全量 + go/quickstep/src（10 个新增类，与主源码无同名冲突）+ go/quickstep/res
  + generated-src-go 的 BuildConfig（WIDGETS_ENABLED / NOTIFICATION_DOTS_ENABLED=false，
  镜像 soong launcher-go-build-config genrule）。
- 变体 × buildType：quickstep{Debug,Release} / go{Debug,Release}。assembleDebug 仍可用（聚合
  两 flavor 的 debug）。
- 产物按 build/outputs/apk/<flavor>/<buildType>/ 分目录，apk 名带 flavor。
- 已用 dex/资源清单验证隔离：AppShareabilityManager 等 go 类与 round_rect_dialog 等
  go res 只进 go 包。
- 未建模：纯 Launcher3（src_no_quickstep 变体，无 quickstep 内核、不依赖本工程预置的平台
  jar 体系）；go 变体未并入 go/AndroidManifest.xml / go/AndroidManifest-launcher.xml
  （AGP overlay manifest 合并与 soong additional_manifests 语义不同；组件声明差异留运行期，
  与 common/launcher manifest 未并入同类偏差）。
- 相关改动：proto 生成目录与 kapt/compile dependsOn 改按变体匹配（任务名
  kaptGenerateStubs<Flavor><BuildType>Kotlin / generate<Flavor><BuildType>Proto；
  protobuf 插件自把生成目录接入对应变体 sourceSet，之前硬编码 debug/release 目录已移除）。

## 依赖 → AOSP 路径
三方依赖与 prebuilts jar 的 AOSP 源码/产物/预编译路径对照（VM 实测）：见
docs/dependency-aosp-map.md。
