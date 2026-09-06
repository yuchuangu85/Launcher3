/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.graphics

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.annotation.WorkerThread
import androidx.core.graphics.withTranslation
import com.android.launcher3.automation.AutomationRepository
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.IconShape
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max

/** Pre-calculated masks for AGSL glow shader. */
data class GlowMasks(
    val outerMask: BitmapShader,
    val innerMask: BitmapShader,
    val silhouetteMask: BitmapShader,
    val paddingOffset: Float,
)

/** Cache for icon glow masks, handling caching and asynchronous generation. */
@LauncherAppSingleton
class GlowMaskCache
@Inject
constructor(
    @Ui private val uiExecutor: Executor,
    @LightweightBackground(LightweightBackgroundPriority.UI) private val bgExecutor: Executor,
    automationRepo: AutomationRepository,
    lifecycle: DaggerSingletonTracker,
) {

    private val cache = WeakHashMap<IconShape, CompletableFuture<GlowMasks>>()

    init {
        // Automatically clear the cache when there are no more automated packages.
        automationRepo.automatedPackages.forEach(uiExecutor) { packages ->
            if (packages.isEmpty()) {
                clear()
            }
        }

        lifecycle.addCloseable { clear() }
    }

    /** Returns a [CompletableFuture] for glow masks of the given [shape]. */
    fun getMasks(shape: IconShape): CompletableFuture<GlowMasks> {
        synchronized(cache) {
            val existing = cache[shape]
            if (existing != null) {
                return existing
            }
            val pathCopy = Path(shape.path)
            val pathSize = shape.pathSize
            val future =
                CompletableFuture.supplyAsync({ generateMasks(pathCopy, pathSize) }, bgExecutor)
            cache[shape] = future
            return future
        }
    }

    /** Clears all cached masks. */
    fun clear() {
        synchronized(cache) {
            cache.values.forEach { it.cancel(/* mayCancelIfRunning */ true) }
            cache.clear()
        }
    }

    @WorkerThread
    private fun generateMasks(path: Path, size: Int): GlowMasks {
        val blurRadius = max(size * BLUR_RATIO, 0.01f)
        val padding = ceil(blurRadius * 3.0).toInt()
        val bitmapSize = size + (padding * 2)
        val maskMatrix = Matrix().apply { setTranslate(-padding.toFloat(), -padding.toFloat()) }

        fun createAlphaMask(filter: BlurMaskFilter?): BitmapShader {
            val bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ALPHA_8)
            val canvas = Canvas(bitmap)
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    maskFilter = filter
                    color = Color.BLACK
                }
            canvas.withTranslation(padding.toFloat(), padding.toFloat()) { drawPath(path, paint) }
            // Hint to the graphics system to start the GPU upload asynchronously.
            bitmap.prepareToDraw()
            return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(maskMatrix)
            }
        }

        return GlowMasks(
            outerMask = createAlphaMask(BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.OUTER)),
            innerMask = createAlphaMask(BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.INNER)),
            silhouetteMask = createAlphaMask(null),
            paddingOffset = padding.toFloat(),
        )
    }

    companion object {
        private const val BLUR_RATIO = 0.10f

        @JvmField val INSTANCE = DaggerSingletonObject { it.glowMaskCache }
    }
}
