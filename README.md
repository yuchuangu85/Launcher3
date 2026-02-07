# Launcher3

## 更新：20260207
* update: lawnchair-16-dev(3b6ab31c16abbf89a2a3914760d6bee0273051d1)(Sat Feb 7 19:56:06 2026 +0700)

## 主要分支
* main (fork from lawnchair-16-dev)--Android-16
* 
* Launcher3-16-s2-release (android16-s2-release)
* Launcher3-15-qc（高通版本，可以编译Quickstep版本）
* Launcher3-15-s1-release (android15-s1-release)
* Launcher3-14-s2-release (android14-s2-release)
* Launcher3-13 (Launcher3-13.0.0_r83(7e9e70085176ea46bf773019a5054942d2fc3811))--Android-13
* Launcher3-13-Qc（高通版本，可以编译Quickstep版本）
* Launcher3-12（可以编译Quickstep版本）
* Launcher3-11
* Launcher3-10.0 (android-10.0.0_r46)
* Launcher3-9.0
* Launcher3-8.0 (android-8.0.0_r24)
* Launcher3-7.0
* Launcher3-6.0


## 源码参考
https://cs.android.com/android/platform/superproject/+/master:packages/apps/Launcher3/

## android.jar
> 替换 [aosp-android-jar](https://github.com/Reginer/aosp-android-jar) 中的对应的android.jar，可以使不能读取的api读取到。

## 依赖库
### wmshell 
// https://android.googlesource.com/platform/frameworks/base/
> // frameworks/base/libs/WindowManager/Shell

### systemui
// https://android.googlesource.com/platform/frameworks/libs/systemui/
// frameworks/libs/systemui
* aconfig/
* ambientlib/
* animationlib/
* compilelib/
* contextualeducationlib/
* displaylib/
* iconloaderlib/
* mechanics/
* monet/
* motiontoollib/
* msdllib/
* toruslib/
* tracinglib/
* viewcapturelib/
* weathereffects/
* .gitignore
* Android.bp
* PREUPLOAD.cfg


### frameworks/base/libs
// https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-s2-release/libs/
// frameworks/base/libs
* androidfw/
* appfunctions/
* dream/
* hostgraphics/
* hwui/
* incident/
* input/
* nativehelper_jvm/
* protoutil/
* securebox/
* services/
* storage/
* tracingproxy/
* usb/
* WindowManager/

## 教程
注：本教程是基于Android6.0的Launcher3讲解的，最新版与现在版本差异较大，我尽量在关键位置添加相应的注释，方便大家修改。
#### 一.[墨香带你学Launcher之（一）-概述](http://www.codemx.cn/2016/07/30/Launcher01/)

#### 二.[墨香带你学Launcher之（二）-数据加载流程](http://www.codemx.cn/2016/08/05/Launcher02/)

#### 三.[墨香带你学Launcher之（三）-绑定屏幕、图标、文件夹和Widget](http://www.codemx.cn/2016/08/14/Launcher03/)

#### 四.[墨香带你学Launcher之（四）-应用安装、更新、卸载时的数据加载](http://www.codemx.cn/2016/08/21/Launcher04/)

#### 五.[墨香带你学Launcher之（五）-Workspace滑动](http://www.codemx.cn/2016/10/16/Launcher05/)

#### 六.[墨香带你学Launcher之（六）-拖拽](http://www.codemx.cn/2016/11/21/Launcher06/)

#### 七.[墨香带你学Launcher之（七）-小部件的加载、添加以及大小调节](http://www.codemx.cn/2016/12/18/Launcher07/)

#### 八.[墨香带你学Launcher之（八）- 加载Icon、设置壁纸](http://www.codemx.cn/2017/05/19/Launcher08/)

## Protobuf相关信息查看
* [Protobuf](https://github.com/protocolbuffers/protobuf)
* [protobuf-gradle-plugin](https://github.com/google/protobuf-gradle-plugin)

## 预览图
<img width="320" src="/image/launcher01.png"/>    <img width="320" src="/image/launcher02.png"/>
<img width="320" src="/image/launcher03.png"/>    <img width="320" src="/image/launcher04.png"/>
<img width="320" src="/image/launcher05.png"/>    <img width="320" src="/image/launcher06.png"/>
<img width="320" src="/image/launcher07.png"/>    <img width="320" src="/image/launcher08.png"/>
<img width="320" src="/image/launcher09.png"/>    <img width="320" src="/image/launcher10.png"/>





<!--统计访问人数-->

## ⚡ Visitor count

<!-- ![](https://visitor-badge.glitch.me/badge?page_id=yuchuangu85.readme) -->
<!--![](https://profile-counter.glitch.me/yuchuangu85/count.svg) -->

![](https://profile-counter.glitch.me/Launcher3-dev-Launcher3/count.svg)



## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Launcher3-dev/Launcher3&type=Date)

