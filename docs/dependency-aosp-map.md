# 依赖 → AOSP 路径对照(Launcher3-QuickStep-Android17)

对照对象:android17-release,OrbStack VM Yuchuan 的 AOSP 树 `~/aosp`。
所有路径 2026-09-06 在 VM 内实测确认(非推断)。`out/soong/.intermediates` 为编译中间产物树,
`~/aosp/out/...` 即产物所在,jar 源文件在代码树内。

## 1. prebuilts/framework/*.jar(平台 compileOnly jar,12 个)

全部取自 VM 已编译产物(见 tools/fetch-framework-prebuilts.sh,含 sha256 校验)。
"soong 产物" 列相对 `~/aosp/out/soong/.intermediates/`。

| 本工程 jar | 对应 soong 模块 | AOSP 源码位置 | soong 产物(相对 .intermediates/) |
|---|---|---|---|
| framework-android17.jar | framework | frameworks/base(core/java;产物为 framework-minus-apex 编译) | frameworks/base/framework/android_common/turbine-combined/framework.jar |
| SystemUI.jar | SystemUI | frameworks/base/packages/SystemUI | frameworks/base/packages/SystemUI/SystemUI/android_common/withres/SystemUI.jar |
| SystemUISharedLib.jar | SystemUISharedLib | frameworks/base/packages/SystemUI/shared | …/SystemUI/shared/SystemUISharedLib/android_common/javac/SystemUISharedLib.jar |
| SystemUI-statsd.jar | SystemUI-statsd | frameworks/base/packages/SystemUI/shared | …/SystemUI/shared/SystemUI-statsd/android_common/javac/SystemUI-statsd.jar |
| framework-statsd.stubs.module_lib.jar | framework-statsd.stubs.module_lib | packages/modules/StatsD/framework | packages/modules/StatsD/framework/framework-statsd.stubs.module_lib/android_common/turbine-combined/…jar |
| view_capture.jar | view_capture | frameworks/libs/systemui/viewcapturelib | frameworks/libs/systemui/viewcapturelib/view_capture/android_common/javac/view_capture.jar |
| com_android_launcher3_flags_lib.jar | com_android_launcher3_flags_lib | packages/apps/Launcher3(aconfig/launcher3-flags.aconfig 生成) | packages/apps/Launcher3/aconfig/com_android_launcher3_flags_lib/android_common/javac/…jar |
| com_android_systemui_shared_flags_lib.jar | com_android_systemui_shared_flags_lib | frameworks/libs/systemui/aconfig | frameworks/libs/systemui/aconfig/com_android_systemui_shared_flags_lib/android_common/javac/…jar |
| com_android_wm_shell_flags_lib.jar | com_android_wm_shell_flags_lib | frameworks/base/libs/WindowManager/Shell/aconfig | frameworks/base/libs/WindowManager/Shell/aconfig/com_android_wm_shell_flags_lib/android_common/javac/…jar |
| WindowManager-Shell.jar | WindowManager-Shell | frameworks/base/libs/WindowManager/Shell(src) | frameworks/base/libs/WindowManager/Shell/WindowManager-Shell/android_common/javac/WindowManager-Shell.jar |
| WindowManager-Shell-shared.jar | WindowManager-Shell-shared | frameworks/base/libs/WindowManager/Shell/shared | …/WindowManager/Shell/shared/WindowManager-Shell-shared/android_common/javac/…jar |
| WindowManager-Shell-shared-AOSP.jar | WindowManager-Shell-shared-AOSP | frameworks/base/libs/WindowManager/Shell/shared(AOSP 桌面模式变体) | …/shared/WindowManager-Shell-shared-AOSP/android_common/javac/…jar |

注:编译期还用到本地 ext_stub / sysui_anim_stub / wmshell_compat / pccore 源码模块,
其上游对应 frameworks/base/libs/computercontrol、frameworks/base/packages/SystemUI/animation、
frameworks/base/libs/WindowManager/Shell/shared(兼容层)、frameworks/base/core/java/android/service/personalcontext。

## 2. Maven 依赖(gradle/libs.versions.toml)的 AOSP 对照

### 2.1 源码在 AOSP 树内(external/),soong 同名/近名模块

| Gradle 坐标(本工程版本) | soong 模块 | AOSP 路径 |
|---|---|---|
| com.google.guava:guava(33.7.1-jre) | guava / guava-both | external/guava(Android.bp) |
| com.google.dagger:dagger / dagger-compiler(2.60.1) | dagger2 / dagger2-compiler | external/dagger2 |
| com.google.protobuf:protobuf-javalite / protoc(4.28.2) | protobuf(相关模块) | external/protobuf |
| org.jetbrains.kotlinx:kotlinx-coroutines-android(1.11.0) | kotlinx-coroutines-core / -android | external/kotlinx.coroutines |
| com.airbnb.android:lottie(6.7.1) | lottie | external/lottie |
| com.google.code.findbugs:jsr305(3.0.2) | jsr305 | external/jsr305 |
| javax.inject:javax.inject(1) | — | 树内无独立模块(17 无 external/javax.inject);经 dagger 依赖链提供,本工程 gradle 显式声明 |
| javax.annotation:javax.annotation-api(1.3.2) | — | 树内无(external 与 androidx m2repo 均无);gradle 专用 |

### 2.2 androidx / compose / material — AOSP 以 prebuilt 提供,无源码

AOSP 的 androidx 均为预编译 m2repo + soong 模块,位于:
`prebuilts/sdk/current/androidx/m2repository/<group>/<artifact>/<version>/`(内含 jar + Android.bp),
soong 模块名规则 `androidx.<group>_<artifact>`(如 androidx.core_core、androidx.compose.runtime_runtime)。
android17 树内版本(实测代表例,与 gradle 版本多数一致或相邻):

| 本工程依赖(版本) | m2repo artifact 目录(树内版本) | soong 模块名 |
|---|---|---|
| androidx.compose runtime/foundation/ui/animation/material3 | androidx/compose/{runtime,runtime-livedata,foundation,ui,animation,material3} 的 *-android(如 runtime 1.12.0-alpha01) | androidx.compose.runtime_runtime 等 |
| androidx.compose.material:material-icons-extended(1.7.8) | androidx/compose/material/material-icons-extended-android/1.7.0-alpha01 | androidx.compose.material_material-icons-extended |
| androidx.compose.ui:ui-tooling(-preview) | androidx/compose/ui/ui-tooling-android/1.12.0-alpha01 | androidx.compose.ui_ui-tooling 等 |
| androidx.core:core-ktx(1.19.0) | androidx/core/core-ktx/…(core 1.20.0-alpha01) | androidx.core_core(-ktx) |
| androidx.core:core-animation(1.0.0) | androidx/core/core-animation/… | androidx.core_core-animation |
| androidx.activity:activity-compose(1.13.0) | androidx/activity/activity-compose/1.14.0-alpha01 | androidx.activity_activity-compose |
| androidx.fragment:fragment(1.9.0) | androidx/fragment/fragment/1.9.0-alpha01 | androidx.fragment_fragment |
| androidx.recyclerview:recyclerview(1.4.0) | androidx/recyclerview/recyclerview/1.5.0-alpha01 | androidx.recyclerview_recyclerview |
| androidx.window:window(1.5.1) | androidx/window/window/1.6.0-alpha02 | androidx.window_window |
| androidx.slice:slice-view(1.1.0-alpha02) | androidx/slice/slice-view/1.1.0-alpha02(版本一致) | androidx.slice_slice-view |
| androidx.appfunctions 系列(1.0.0-alpha08) | androidx/appfunctions/{appfunctions,appfunctions-compiler,appfunctions-service}/1.0.0-alpha08(一致) | androidx.appfunctions_* |
| androidx.dynamicanimation(1.1.0) | androidx/dynamicanimation/dynamicanimation/1.1.0(一致) | androidx.dynamicanimation_dynamicanimation |
| androidx.constraintlayout(2.2.2) | androidx/constraintlayout/constraintlayout/2.3.0-alpha01 | androidx.constraintlayout_constraintlayout |
| androidx.concurrent:concurrent-futures(1.3.0) | androidx/concurrent/concurrent-futures/1.3.0-rc01 | androidx.concurrent_concurrent-futures |
| androidx.lifecycle / navigation / preference / savedstate / annotation / material | androidx/{lifecycle,navigation,preference,savedstate,annotation}/…(同 m2repo 结构) | androidx.<group>_<artifact> |

com.google.android.material:material(1.14.0):AOSP 17 树内未见对应(AOSP 老 maven_repo 与 androidx
m2repo 均无 material 组件;SystemUI/Launcher 的 soong 链不引用)→ gradle 专用依赖。

launcher soong 侧真实引用的 androidx/compose 模块见 packages/apps/Launcher3/Android.bp
(Launcher3QuickStepLib static_libs:androidx.compose.runtime_runtime、material3_material3、
material_material-icons-extended、ui_ui-tooling(-preview) 等;另含 lottie、dagger2、SystemUISharedLib、
displaylib、protolog-group、SettingsLibSettingsTheme)。

## 3. 使用提示
- 平台 jar 升级/重取:改 VM AOSP 编译后重跑 `tools/fetch-framework-prebuilts.sh`(清单+sha256 校验内建)。
- 版本对照规则:树内 androidx 版本是 android17 编译快照;gradle 实际解析走 settings.gradle 的
  aliyun google maven,不受树内版本影响。表内 "树内版本" 仅供核对来源。
