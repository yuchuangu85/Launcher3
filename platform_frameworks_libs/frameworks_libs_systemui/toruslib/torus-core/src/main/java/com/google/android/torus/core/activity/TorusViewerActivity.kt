/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.torus.core.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.torus.core.content.ConfigurationChangeListener
import com.google.android.torus.core.engine.TorusEngine
import com.google.android.torus.core.engine.listener.TorusTouchListener

/**
 * Helper activity to show a [TorusEngine] into a [SurfaceView]. To use it, you should override
 * [getWallpaperEngine] and return an instance of your[TorusEngine] that will draw inside the
 * given surface.
 *
 * Note: [TorusViewerActivity] subclasses must include the following attribute/s
 * in the AndroidManifest.xml:
 * - android:configChanges="uiMode"
 */
abstract class TorusViewerActivity : AppCompatActivity() {
    private lateinit var wallpaperEngine: TorusEngine
    private lateinit var surfaceView: SurfaceView

    /**
     * Must be implemented to return a new instance of [TorusEngine].
     */
    abstract fun getWallpaperEngine(context: Context, surfaceView: SurfaceView): TorusEngine

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check that class includes the proper attributes in the AndroidManifest.xml
        checkManifestAttributes()

        surfaceView = SurfaceView(this).apply { setContentView(this) }
        wallpaperEngine = getWallpaperEngine(this, surfaceView)
        wallpaperEngine.create()
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                wallpaperEngine.resize(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }

            override fun surfaceCreated(holder: SurfaceHolder) {
            }
        })

        // Pass the touch events.
        if (wallpaperEngine is TorusTouchListener) {
            surfaceView.setOnTouchListener { _, event ->
                (wallpaperEngine as TorusTouchListener).onTouchEvent(event)
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        wallpaperEngine.resume()
    }

    override fun onPause() {
        super.onPause()
        wallpaperEngine.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        wallpaperEngine.destroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (wallpaperEngine is ConfigurationChangeListener) {
            (wallpaperEngine as ConfigurationChangeListener).onConfigurationChanged(newConfig)
        }
    }

    private fun checkManifestAttributes() {
        val configChange = packageManager.getActivityInfo(componentName, 0).configChanges

        // Check if Activity sets android:configChanges="uiMode" in the manifest.
        if ((configChange and ActivityInfo.CONFIG_UI_MODE) != ActivityInfo.CONFIG_UI_MODE) {
            throw RuntimeException(
                "${TorusViewerActivity::class.simpleName} " +
                        "has to include the attribute android:configChanges=\"uiMode\" " +
                        "in the AndroidManifest.xml"
            )
        }
    }
}
