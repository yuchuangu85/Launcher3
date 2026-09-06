/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * This is a fork of [AndroidExternalSurface from Compose](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/AndroidExternalSurface.android.kt?q=AndroidExternalSurface)
 * with the following modifications:
 * - Calls [SurfaceView.setChildSurfacePackage] if the given [childSurfacePackage] parameter is non-null.
 * - Passes a reference of the [SurfaceView] to [onInit].
 * - Adds [onReset] and [onRelease] callbacks to allow callers to clean up state
 */

package com.android.personalcontext.ace.client.clientsdk.ui.androidexternalsurface

import android.graphics.PixelFormat
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.AndroidExternalSurfaceScope
import androidx.compose.foundation.AndroidExternalSurfaceZOrder
import androidx.compose.foundation.SurfaceCoroutineScope
import androidx.compose.foundation.SurfaceScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Provides a dedicated drawing [Surface] as a separate layer positioned by default behind the
 * window holding the [AndroidExternalSurface] composable. Because [AndroidExternalSurface] uses a
 * separate window layer, graphics composition is handled by the system compositor which can bypass
 * the GPU and provide better performance and power usage characteristics compared to
 * [AndroidEmbeddedExternalSurface]. It is therefore recommended to use [AndroidExternalSurface]
 * over [AndroidEmbeddedExternalSurface] whenever possible.
 *
 * The [Surface] provided can be used to present content that's external to Compose, such as a video
 * stream (from a camera or a media player), OpenGL, Vulkan...The provided [Surface] can be rendered
 * into using a thread different from the main thread.
 *
 * The z-ordering of the surface can be controlled using the [zOrder] parameter:
 * - [AndroidExternalSurfaceZOrder.Behind]: positions the surface behind the window
 * - [AndroidExternalSurfaceZOrder.MediaOverlay]: positions the surface behind the window but above
 *   other [AndroidExternalSurfaceZOrder.Behind] surfaces
 * - [AndroidExternalSurfaceZOrder.OnTop]: positions the surface above the window
 *
 * The drawing surface is opaque by default, which can be controlled with the [isOpaque] parameter.
 * When the surface is transparent, you may need to change the z-order to see something behind the
 * surface.
 *
 * To start rendering, the caller must first acquire the [Surface] when it's created. This is
 * achieved by providing the [onInit] lambda, which allows the caller to register an appropriate
 * [AndroidExternalSurfaceScope.onSurface] callback. The [onInit] lambda can also be used to
 * initialize/cache resources needed once a surface is available.
 *
 * After acquiring a surface, the caller can start rendering into it. Rendering into a surface can
 * be done from any thread.
 *
 * It is recommended to register the [SurfaceScope.onChanged] and [SurfaceScope.onDestroyed]
 * callbacks to properly handle the lifecycle of the surface and react to dimension changes. You
 * must ensure that the rendering thread stops interacting with the surface when the
 * [SurfaceScope.onDestroyed] callback is invoked.
 *
 * If a [surfaceSize] is specified (set to non-[IntSize.Zero]), the surface will use the specified
 * size instead of the layout size of this composable. The surface will be stretched at render time
 * to fit the layout size. This can be used for instance to render at a lower resolution for
 * performance reasons.
 *
 * @param modifier Modifier to be applied to the [AndroidExternalSurface]
 * @param isOpaque Whether the managed surface should be opaque or transparent.
 * @param surfaceSize Sets the surface size independently of the layout size of this
 *   [AndroidExternalSurface]. If set to [IntSize.Zero], the surface size will be equal to the
 *   [AndroidExternalSurface] layout size.
 * @param zOrder Sets the z-order of the surface relative to its parent window.
 * @param isSecure Control whether the surface view's content should be treated as secure,
 *   preventing it from appearing in screenshots or from being viewed on non-secure displays.
 * @param childSurfacePackage If non-null, passed to [SurfaceView.setChildSurfacePackage] on the
 *   surface view
 * @param onReset Lambda invoked as a signal that the view is about to be attached to the
 *   composition hierarchy in a different context than its original creation. This callback should
 *   prepare the view for general reuse.
 * @param onRelease Lambda invoked as a signal that this view instance has exited the composition
 *   hierarchy entirely and will not be reused again.
 * @param onInit Lambda invoked on first composition. This lambda can be used to declare a
 *   [AndroidExternalSurfaceScope.onSurface] callback that will be invoked when a surface is
 *   available.
 * @sample androidx.compose.foundation.samples.AndroidExternalSurfaceColors
 */
@Composable
fun AndroidExternalSurface(
    modifier: Modifier = Modifier,
    isOpaque: Boolean = true,
    surfaceSize: IntSize = IntSize.Zero,
    zOrder: AndroidExternalSurfaceZOrder = AndroidExternalSurfaceZOrder.Behind,
    isSecure: Boolean = false,
    onReset: (SurfaceView) -> Unit = {},
    onRelease: (SurfaceView) -> Unit = {},
    onUpdate: (SurfaceView) -> Unit = {},
    onInit: AndroidExternalSurfaceScope.(SurfaceView) -> Unit,
) {
    val state = rememberAndroidExternalSurfaceState()

    AndroidView(
        factory = { context ->
            Log.d(TAG, "factory {}")

            SurfaceView(context).apply {
                state.onInit(this)
                holder.addCallback(state)
            }
        },
        modifier = modifier,
        onReset = { view ->
            Log.d(TAG, "onReset {}")
            onReset(view)
        },
        onRelease = { view ->
            Log.d(TAG, "onRelease {}")
            onRelease(view)
        },
        update = { view ->
            Log.d(TAG, "update {}")

            if (surfaceSize != IntSize.Zero) {
                view.holder.setFixedSize(surfaceSize.width, surfaceSize.height)
            } else {
                view.holder.setSizeFromLayout()
            }

            view.holder.setFormat(
                if (isOpaque) {
                    PixelFormat.OPAQUE
                } else {
                    PixelFormat.TRANSLUCENT
                }
            )

            when (zOrder) {
                AndroidExternalSurfaceZOrder.Behind -> view.setZOrderOnTop(false)
                AndroidExternalSurfaceZOrder.MediaOverlay -> view.setZOrderMediaOverlay(true)
                AndroidExternalSurfaceZOrder.OnTop -> view.setZOrderOnTop(true)
            }

            view.setSecure(isSecure)

            onUpdate(view)
        },
    )
}

private val TAG = "AndroidExternalSurface"

@Composable
private fun rememberAndroidExternalSurfaceState(): AndroidExternalSurfaceState {
    val scope = rememberCoroutineScope()
    return remember { AndroidExternalSurfaceState(scope) }
}

private class AndroidExternalSurfaceState(scope: CoroutineScope) :
    BaseAndroidExternalSurfaceState(scope), SurfaceHolder.Callback {

    var lastWidth = -1
    var lastHeight = -1

    override fun surfaceCreated(holder: SurfaceHolder) {
        val frame = holder.surfaceFrame
        lastWidth = frame.width()
        lastHeight = frame.height()

        dispatchSurfaceCreated(holder.surface, lastWidth, lastHeight)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (lastWidth != width || lastHeight != height) {
            lastWidth = width
            lastHeight = height

            dispatchSurfaceChanged(holder.surface, width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        dispatchSurfaceDestroyed(holder.surface)
    }
}

/**
 * Base class for [AndroidExternalSurface] and [AndroidEmbeddedExternalSurface] state. This class
 * provides methods to properly dispatch lifecycle events on [Surface] creation, change, and
 * destruction. Surface creation is treated as a coroutine launch, using the specified [scope] as
 * the parent. This scope must be the main thread scope.
 */
private abstract class BaseAndroidExternalSurfaceState(val scope: CoroutineScope) :
    AndroidExternalSurfaceScope, SurfaceScope {

    private var onSurface:
        (suspend SurfaceCoroutineScope.(surface: Surface, width: Int, height: Int) -> Unit)? =
        null
    private var onSurfaceChanged: (Surface.(width: Int, height: Int) -> Unit)? = null
    private var onSurfaceDestroyed: (Surface.() -> Unit)? = null

    private var job: Job? = null

    override fun onSurface(
        onSurface: suspend SurfaceCoroutineScope.(surface: Surface, width: Int, height: Int) -> Unit
    ) {
        this.onSurface = onSurface
    }

    override fun Surface.onChanged(onChanged: Surface.(width: Int, height: Int) -> Unit) {
        onSurfaceChanged = onChanged
    }

    override fun Surface.onDestroyed(onDestroyed: Surface.() -> Unit) {
        onSurfaceDestroyed = onDestroyed
    }

    /**
     * Dispatch a surface creation event by launching a new coroutine in [scope]. Any previous job
     * from a previous surface creation dispatch is cancelled.
     */
    fun dispatchSurfaceCreated(surface: Surface, width: Int, height: Int) {
        if (onSurface != null) {
            job =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    job?.cancelAndJoin()
                    val receiver =
                        object :
                            SurfaceCoroutineScope,
                            SurfaceScope by this@BaseAndroidExternalSurfaceState,
                            CoroutineScope by this {}
                    onSurface?.invoke(receiver, surface, width, height)
                }
        }
    }

    /**
     * Dispatch a surface change event, providing the surface's new width and height. Must be
     * invoked from the main thread.
     */
    fun dispatchSurfaceChanged(surface: Surface, width: Int, height: Int) {
        onSurfaceChanged?.invoke(surface, width, height)
    }

    /**
     * Dispatch a surface destruction event. Any pending job from [dispatchSurfaceCreated] is
     * cancelled before dispatching the event. Must be invoked from the main thread.
     */
    fun dispatchSurfaceDestroyed(surface: Surface) {
        onSurfaceDestroyed?.invoke(surface)
        job?.cancel()
        job = null
    }
}
