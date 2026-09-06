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

package com.android.quickstep

import android.app.WindowConfiguration
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import com.android.wm.shell.shared.compat.AnimatedSurface
import com.android.wm.shell.shared.compat.AnimatedSurfaceUtils
import com.android.wm.shell.shared.compat.AnimatedSurfaceUtils.AnimatedSurfaceMode
import java.io.PrintWriter
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "AnimatedSurfaces"

class AnimatedSurfaces
private constructor(
    @JvmField val unfilteredApps: Array<AnimatedSurface>?,
    @JvmField val apps: Array<AnimatedSurface>?,
    @JvmField val wallpapers: Array<AnimatedSurface>?,
    @JvmField val nonApps: Array<AnimatedSurface>?,
    @JvmField val extras: Bundle?,
    @JvmField @AnimatedSurfaceMode val mode: Int,
    @JvmField val hasRecents: Boolean,
) {
    private val releaseChecks = CopyOnWriteArrayList<SurfaceReleaseCheck>()

    private var released: Boolean = false

    fun findTask(taskId: Int): AnimatedSurface? {
        apps?.forEach { app ->
            if (app.taskId == taskId) {
                return app
            }
        }
        Log.e(TAG, "taskId: $taskId not found")
        return null
    }

    fun getNavBarAnimatedSurface(): AnimatedSurface? {
        return getNonAppAnimatedSurfaceOfType(WindowManager.LayoutParams.TYPE_NAVIGATION_BAR)
    }

    fun getNonAppAnimatedSurfaceOfType(type: Int): AnimatedSurface? {
        nonApps?.forEach { nonApp ->
            if (nonApp.windowType == type) {
                return nonApp
            }
        }
        return null
    }

    fun getFirstAppSurface(): AnimatedSurface? {
        return apps?.firstOrNull()
    }

    fun getFirstSurfaceTaskId(): Int {
        return getFirstAppSurface()?.taskId ?: -1
    }

    fun addReleaseCheck(check: SurfaceReleaseCheck) {
        releaseChecks.add(check)
    }

    fun release() {
        if (released) {
            return
        }

        releaseChecks.forEach { check ->
            if (!check.canRelease) {
                check.addOnSafeToReleaseCallback(::release)
                return
            }
        }

        releaseChecks.clear()
        released = true
        release(unfilteredApps)
        release(wallpapers)
        release(nonApps)
    }

    private fun release(surfaces: Array<AnimatedSurface>?) {
        surfaces?.forEach { surface ->
            surface.leash?.release()
            surface.startLeash?.release()
        }
    }

    fun dump(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "RemoteAnimationTargets:")
        pw.println("$prefix\ttargetMode=$mode")
        pw.println("$prefix\thasRecents=$hasRecents")
        pw.println("$prefix\tmReleased=$released")
    }

    companion object {

        @JvmStatic
        @JvmOverloads
        fun from(
            apps: Array<AnimatedSurface>?,
            wallpapers: Array<AnimatedSurface>?,
            nonApps: Array<AnimatedSurface>?,
            @AnimatedSurfaceMode mode: Int,
            extras: Bundle = Bundle(),
        ): AnimatedSurfaces {
            val filteredApps = mutableListOf<AnimatedSurface>()
            var hasRecents = false
            apps?.forEach { app ->
                if (app.mode == mode) {
                    filteredApps.add(app)
                }
                hasRecents =
                    hasRecents or
                        (app.windowConfiguration.activityType ==
                            WindowConfiguration.ACTIVITY_TYPE_RECENTS)
            }
            return AnimatedSurfaces(
                unfilteredApps = apps,
                apps = filteredApps.toTypedArray(),
                wallpapers = wallpapers,
                nonApps = nonApps,
                mode = mode,
                extras = extras,
                hasRecents = hasRecents,
            )
        }

        @JvmStatic
        fun from(targets: RemoteAnimationTargets): AnimatedSurfaces {
            return AnimatedSurfaces(
                unfilteredApps = AnimatedSurfaceUtils.mapFromTargets(targets.unfilteredApps),
                apps = AnimatedSurfaceUtils.mapFromTargets(targets.apps),
                wallpapers = AnimatedSurfaceUtils.mapFromTargets(targets.wallpapers),
                nonApps = AnimatedSurfaceUtils.mapFromTargets(targets.nonApps),
                extras = targets.extras,
                mode = AnimatedSurfaceUtils.mappedModeFromTarget(targets.targetMode),
                hasRecents = targets.hasRecents,
            )
        }
    }
}
