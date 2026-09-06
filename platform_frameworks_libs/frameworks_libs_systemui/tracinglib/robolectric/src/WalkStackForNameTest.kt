/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.test.tracing.coroutines

import com.android.app.tracing.coroutines.parseStackForName
import java.lang.StackWalker.StackFrame
import java.lang.invoke.MethodType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests behavior of default names using reflection */
class WalkStackForNameTest : TestBase() {

    @Test
    fun testWalkStackForClassName1() {
        checkStackName(
            $$"RepeatOnLifecycleKt$repeatOnLifecycle$3$1<~RepeatOnLifecycleKt$repeatOnLifecycle$3<~JavaAdapterKt$collectFlow$1<~RepeatWhenAttachedKt$createLifecycleOwnerAndRun$1$1",
            $$$"""
com.android.app.tracing.coroutines.StackDump
    at com.android.app.tracing.coroutines.TraceContextElement.<init>(TraceContextElement.kt:314)
    at com.android.app.tracing.coroutines.TraceContextElement.createChildContext(TraceContextElement.kt:474)
    at com.android.app.tracing.coroutines.TraceContextElement.copyForChild(TraceContextElement.kt:446)
    at kotlinx.coroutines.CoroutineContextKt.foldCopies$lambda$1(CoroutineContext.kt:66)
    at kotlinx.coroutines.CoroutineContextKt$$ExternalSyntheticLambda1.invoke(D8$$SyntheticClass:0)
    at kotlin.coroutines.CoroutineContext$Element$DefaultImpls.fold(CoroutineContext.kt:70)
    at com.android.app.tracing.coroutines.CoroutineTraceName.fold(TraceContextElement.kt:202)
    at kotlin.coroutines.CombinedContext.fold(CoroutineContextImpl.kt:133)
    at kotlin.coroutines.CombinedContext.fold(CoroutineContextImpl.kt:133)
    at kotlinx.coroutines.CoroutineContextKt.foldCopies(CoroutineContext.kt:59)
    at kotlinx.coroutines.CoroutineContextKt.newCoroutineContext(CoroutineContext.kt:15)
    at kotlinx.coroutines.BuildersKt__Builders_commonKt.launch(Builders.common.kt:48)
    at kotlinx.coroutines.BuildersKt.launch(Unknown Source:1)
    at kotlinx.coroutines.BuildersKt__Builders_commonKt.launch$default(Builders.common.kt:43)
    at kotlinx.coroutines.BuildersKt.launch$default(Unknown Source:1)
    at androidx.lifecycle.RepeatOnLifecycleKt$repeatOnLifecycle$3$1$1$1.onStateChanged(RepeatOnLifecycle.kt:107)
    at androidx.lifecycle.LifecycleRegistry$ObserverWithState.dispatchEvent(LifecycleRegistry.jvm.kt:316)
    at androidx.lifecycle.LifecycleRegistry.addObserver(LifecycleRegistry.jvm.kt:193)
    at androidx.lifecycle.RepeatOnLifecycleKt$repeatOnLifecycle$3$1.invokeSuspend(RepeatOnLifecycle.kt:122)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
    at kotlinx.coroutines.internal.DispatchedContinuationKt.resumeCancellableWith(DispatchedContinuation.kt:375)
    at kotlinx.coroutines.intrinsics.CancellableKt.startCoroutineCancellable(Cancellable.kt:26)
    at kotlinx.coroutines.BuildersKt__Builders_commonKt.withContext(Builders.common.kt:172)
    at kotlinx.coroutines.BuildersKt.withContext(Unknown Source:1)
    at androidx.lifecycle.RepeatOnLifecycleKt$repeatOnLifecycle$3.invokeSuspend(RepeatOnLifecycle.kt:83)
    at androidx.lifecycle.RepeatOnLifecycleKt$repeatOnLifecycle$3.invoke(RepeatOnLifecycle.kt:8)
    at androidx.lifecycle.RepeatOnLifecycleKt$repeatOnLifecycle$3.invoke(RepeatOnLifecycle.kt:4)
    at kotlinx.coroutines.intrinsics.UndispatchedKt.startUndispatchedOrReturn(Undispatched.kt:43)
    at kotlinx.coroutines.CoroutineScopeKt.coroutineScope(CoroutineScope.kt:285)
    at androidx.lifecycle.RepeatOnLifecycleKt.repeatOnLifecycle(RepeatOnLifecycle.kt:82)
    at androidx.lifecycle.RepeatOnLifecycleKt.repeatOnLifecycle(RepeatOnLifecycle.kt:159)
    at com.android.systemui.util.kotlin.JavaAdapterKt$collectFlow$1.invokeSuspend(JavaAdapter.kt:127)
    at com.android.systemui.util.kotlin.JavaAdapterKt$collectFlow$1.invoke(JavaAdapter.kt:15)
    at com.android.systemui.util.kotlin.JavaAdapterKt$collectFlow$1.invoke(JavaAdapter.kt:6)
    at com.android.systemui.lifecycle.RepeatWhenAttachedKt$createLifecycleOwnerAndRun$1$1.invokeSuspend(RepeatWhenAttached.kt:128)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
    at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
    at android.os.Handler.handleCallback(Handler.java:1095)
    at android.os.Handler.dispatchMessageImpl(Handler.java:135)
    at android.os.Handler.dispatchMessage(Handler.java:125)
    at android.os.Looper.loopOnce(Looper.java:269)
    at android.os.Looper.loop(Looper.java:367)
    at android.app.ActivityThread.main(ActivityThread.java:9316)
    at java.lang.reflect.Method.invoke(Native Method)
    at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:566)
    at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:929)
        """,
        )
    }

    @Test
    fun testWalkStackForClassName2() {
        checkStackName(
            $$"LightRevealScrimViewBinder$bind$1$1<~RepeatOnLifecycleKt$repeatOnLifecycle$3$1$1$1$1$1$1<~RepeatOnLifecycleKt$repeatOnLifecycle$3$1$1$1$1",
            $$$"""
com.android.app.tracing.coroutines.StackDump
	at com.android.app.tracing.coroutines.TraceContextElement.<init>(TraceContextElement.kt:314)
	at com.android.app.tracing.coroutines.TraceContextElement.createChildContext(TraceContextElement.kt:474)
	at com.android.app.tracing.coroutines.TraceContextElement.mergeForChild(TraceContextElement.kt:460)
	at kotlinx.coroutines.CoroutineContextKt.foldCopies$lambda$1(CoroutineContext.kt:72)
	at kotlinx.coroutines.CoroutineContextKt$$ExternalSyntheticLambda1.invoke(D8$$SyntheticClass:0)
	at kotlin.coroutines.CoroutineContext$Element$DefaultImpls.fold(CoroutineContext.kt:70)
	at com.android.app.tracing.coroutines.CoroutineTraceName.fold(TraceContextElement.kt:202)
	at kotlin.coroutines.CombinedContext.fold(CoroutineContextImpl.kt:133)
	at kotlin.coroutines.CombinedContext.fold(CoroutineContextImpl.kt:133)
	at kotlinx.coroutines.CoroutineContextKt.foldCopies(CoroutineContext.kt:59)
	at kotlinx.coroutines.CoroutineContextKt.newCoroutineContext(CoroutineContext.kt:15)
	at kotlinx.coroutines.BuildersKt__Builders_commonKt.launch(Builders.common.kt:48)
	at kotlinx.coroutines.BuildersKt.launch(Unknown Source:1)
	at com.android.app.tracing.coroutines.CoroutineTracingKt.launchTraced(CoroutineTracing.kt:318)
	at com.android.app.tracing.coroutines.CoroutineTracingKt.launchTraced$default(CoroutineTracing.kt:82)
	at com.android.systemui.keyguard.ui.binder.LightRevealScrimViewBinder$bind$1$1.invokeSuspend(LightRevealScrimViewBinder.kt:68)
	at com.android.systemui.keyguard.ui.binder.LightRevealScrimViewBinder$bind$1$1.invoke(LightRevealScrimViewBinder.kt:8)
	at com.android.systemui.keyguard.ui.binder.LightRevealScrimViewBinder$bind$1$1.invoke(LightRevealScrimViewBinder.kt:4)
	at androidx.lifecycle.RepeatOnLifecycleKt$repeatOnLifecycle$3$1$1$1$1$1$1.invokeSuspend(RepeatOnLifecycle.kt:110)
	at androidx.lifecycle.RepeatOnLifecycleKt$repeatOnLifecycle$3$1$1$1$1$1$1.invoke(RepeatOnLifecycle.kt:8)
	at androidx.lifecycle.RepeatOnLifecycleKt$repeatOnLifecycle$3$1$1$1$1$1$1.invoke(RepeatOnLifecycle.kt:4)
	at kotlinx.coroutines.intrinsics.UndispatchedKt.startUndispatchedOrReturn(Undispatched.kt:43)
	at kotlinx.coroutines.CoroutineScopeKt.coroutineScope(CoroutineScope.kt:285)
	at androidx.lifecycle.RepeatOnLifecycleKt$repeatOnLifecycle$3$1$1$1$1.invokeSuspend(RepeatOnLifecycle.kt:110)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at android.os.Handler.handleCallback(Handler.java:1095)
	at android.os.Handler.dispatchMessageImpl(Handler.java:135)
	at android.os.Handler.dispatchMessage(Handler.java:125)
	at android.os.Looper.loopOnce(Looper.java:269)
	at android.os.Looper.loop(Looper.java:367)
	at android.app.ActivityThread.main(ActivityThread.java:9316)
	at java.lang.reflect.Method.invoke(Native Method)
	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:566)
	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:929)
        """,
        )
    }

    @Test
    fun testWalkStackForClassName3() {
        checkStackName(
            $$"ChannelFlow$collect$2<~CombineKt$combineInternal$2$1<~SnapshotStateKt__SnapshotFlowKt$snapshotFlow$1<~GlobalSnapshotManager$ensureStarted$1",
            $$$"""
com.android.app.tracing.coroutines.StackDump
	at com.android.app.tracing.coroutines.TraceContextElement.<init>(TraceContextElement.kt:314)
	at com.android.app.tracing.coroutines.TraceContextElement.createChildContext(TraceContextElement.kt:474)
	at com.android.app.tracing.coroutines.TraceContextElement.copyForChild(TraceContextElement.kt:446)
	at kotlinx.coroutines.CoroutineContextKt.foldCopies$lambda$1(CoroutineContext.kt:66)
	at kotlinx.coroutines.CoroutineContextKt$$ExternalSyntheticLambda1.invoke(D8$$SyntheticClass:0)
	at kotlin.coroutines.CoroutineContext$Element$DefaultImpls.fold(CoroutineContext.kt:70)
	at com.android.app.tracing.coroutines.CoroutineTraceName.fold(TraceContextElement.kt:202)
	at kotlin.coroutines.CombinedContext.fold(CoroutineContextImpl.kt:133)
	at kotlin.coroutines.CombinedContext.fold(CoroutineContextImpl.kt:133)
	at kotlinx.coroutines.CoroutineContextKt.foldCopies(CoroutineContext.kt:59)
	at kotlinx.coroutines.CoroutineContextKt.newCoroutineContext(CoroutineContext.kt:15)
	at kotlinx.coroutines.channels.ProduceKt.produce(Produce.kt:278)
	at kotlinx.coroutines.channels.ProduceKt.produce$default(Produce.kt:269)
	at kotlinx.coroutines.flow.internal.ChannelFlow.produceImpl(ChannelFlow.kt:115)
	at kotlinx.coroutines.flow.internal.ChannelFlow$collect$2.invokeSuspend(ChannelFlow.kt:119)
	at kotlinx.coroutines.flow.internal.ChannelFlow$collect$2.invoke(ChannelFlow.kt:8)
	at kotlinx.coroutines.flow.internal.ChannelFlow$collect$2.invoke(ChannelFlow.kt:4)
	at kotlinx.coroutines.intrinsics.UndispatchedKt.startUndispatchedOrReturn(Undispatched.kt:43)
	at kotlinx.coroutines.CoroutineScopeKt.coroutineScope(CoroutineScope.kt:285)
	at kotlinx.coroutines.flow.internal.ChannelFlow.collect$suspendImpl(ChannelFlow.kt:118)
	at kotlinx.coroutines.flow.internal.ChannelFlow.collect(ChannelFlow.kt:0)
	at kotlinx.coroutines.flow.internal.ChannelFlowOperator.collect$suspendImpl(ChannelFlow.kt:169)
	at kotlinx.coroutines.flow.internal.ChannelFlowOperator.collect(ChannelFlow.kt:0)
	at kotlinx.coroutines.flow.internal.CombineKt$combineInternal$2$1.invokeSuspend(Combine.kt:28)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at kotlinx.coroutines.EventLoop.processUnconfinedEvent(EventLoop.common.kt:65)
	at kotlinx.coroutines.DispatchedTaskKt.resumeUnconfined(DispatchedTask.kt:243)
	at kotlinx.coroutines.DispatchedTaskKt.dispatch(DispatchedTask.kt:147)
	at kotlinx.coroutines.CancellableContinuationImpl.dispatchResume(CancellableContinuationImpl.kt:470)
	at kotlinx.coroutines.CancellableContinuationImpl.completeResume(CancellableContinuationImpl.kt:591)
	at kotlinx.coroutines.channels.BufferedChannelKt.tryResume0(BufferedChannel.kt:2957)
	at kotlinx.coroutines.channels.BufferedChannelKt.access$tryResume0(BufferedChannel.kt:1)
	at kotlinx.coroutines.channels.BufferedChannel.tryResumeReceiver(BufferedChannel.kt:666)
	at kotlinx.coroutines.channels.BufferedChannel.updateCellSend(BufferedChannel.kt:478)
	at kotlinx.coroutines.channels.BufferedChannel.access$updateCellSend(BufferedChannel.kt:33)
	at kotlinx.coroutines.channels.BufferedChannel.trySend-JP2dKIU(BufferedChannel.kt:3362)
	at androidx.compose.runtime.SnapshotStateKt__SnapshotFlowKt$snapshotFlow$1.invokeSuspend$lambda$1(SnapshotFlow.kt:130)
	at androidx.compose.runtime.SnapshotStateKt__SnapshotFlowKt$snapshotFlow$1$$ExternalSyntheticLambda1.invoke(D8$$SyntheticClass:0)
	at androidx.compose.runtime.snapshots.SnapshotKt.advanceGlobalSnapshot(Snapshot.kt:2023)
	at androidx.compose.runtime.snapshots.SnapshotKt.advanceGlobalSnapshot(Snapshot.kt:2037)
	at androidx.compose.runtime.snapshots.SnapshotKt.access$advanceGlobalSnapshot(Snapshot.kt:1)
	at androidx.compose.runtime.snapshots.Snapshot$Companion.sendApplyNotifications(Snapshot.kt:689)
	at androidx.compose.ui.platform.GlobalSnapshotManager$ensureStarted$1.invokeSuspend(GlobalSnapshotManager.android.kt:48)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at androidx.compose.ui.platform.AndroidUiDispatcher.performTrampolineDispatch(AndroidUiDispatcher.android.kt:79)
	at androidx.compose.ui.platform.AndroidUiDispatcher.access$performTrampolineDispatch(AndroidUiDispatcher.android.kt:41)
	at androidx.compose.ui.platform.AndroidUiDispatcher$dispatchCallback$1.doFrame(AndroidUiDispatcher.android.kt:68)
	at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1645)
	at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1656)
	at android.view.Choreographer.doCallbacks(Choreographer.java:1252)
	at android.view.Choreographer.doFrame(Choreographer.java:1177)
	at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:1630)
	at android.os.Handler.handleCallback(Handler.java:1095)
	at android.os.Handler.dispatchMessageImpl(Handler.java:135)
	at android.os.Handler.dispatchMessage(Handler.java:125)
	at android.os.Looper.loopOnce(Looper.java:269)
	at android.os.Looper.loop(Looper.java:367)
	at android.app.ActivityThread.main(ActivityThread.java:9316)
	at java.lang.reflect.Method.invoke(Native Method)
	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:566)
	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:929)
        """,
        )
    }
}

private fun checkStackName(expectedName: String, stackTrace: String) {
    assertEquals(expectedName, parseStackForName(parseStackTrace(stackTrace).stream()))
}

/**
 * Parses a standard Java/Kotlin stack trace string into a `List` of fake `StackFrame` objects
 *
 * The expected format is for each line of the stack trace to contain: "at
 * com.package.Class.method(File.java:123)". The header line, such as "java.lang.Exception:", will
 * be ignored
 */
private fun parseStackTrace(stackTrace: String): List<StackFrame> {
    return stackTrace
        .trim()
        .lines()
        .map { it.trim() }
        // Only process lines starting with "at ", ignoring headers like "java.lang.Exception:"
        .filter { it.startsWith("at ") }
        .map { line ->
            // Remove "at " prefix and remove source info "(FileName.kt:123)"
            val cleanLine = line.substringAfter("at ").substringBefore("(")
            // Split class and method by the last dot
            val lastDotIndex = cleanLine.lastIndexOf('.')
            if (lastDotIndex > 0) {
                val className = cleanLine.take(lastDotIndex)
                val methodName = cleanLine.substring(lastDotIndex + 1)
                FakeStackFrame(className, methodName)
            } else {
                throw IllegalArgumentException("Malformed stack trace")
            }
        }
}

private class FakeStackFrame(private val _className: String, private val _methodName: String) :
    StackFrame {
    override fun getByteCodeIndex(): Int {
        throw UnsupportedOperationException()
    }

    override fun getClassName(): String = _className

    override fun getDeclaringClass(): Class<*> {
        throw UnsupportedOperationException()
    }

    override fun getMethodName(): String = _methodName

    override fun getMethodType(): MethodType {
        throw UnsupportedOperationException()
    }

    override fun getFileName(): String {
        throw UnsupportedOperationException()
    }

    override fun getLineNumber(): Int {
        throw UnsupportedOperationException()
    }

    override fun isNativeMethod(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun toStackTraceElement(): StackTraceElement {
        throw UnsupportedOperationException()
    }
}
