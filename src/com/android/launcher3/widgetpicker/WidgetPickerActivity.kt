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

package com.android.launcher3.widgetpicker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.view.ContextThemeWrapper
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.LifecycleOwner
import com.android.launcher3.BaseActivity
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherComponentProvider
import com.android.launcher3.dragndrop.SimpleDragLayer
import com.android.launcher3.util.ScreenOnTracker
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.util.Themes

/** Activity that shows widget picker UI; shows content only if compose is available. */
open class WidgetPickerActivity :
    BaseActivity(), OnBackPressedDispatcherOwner, OnBackAnimationCallback, LifecycleOwner {
    private var _dragLayer: SimpleDragLayer<WidgetPickerActivity>? = null
    protected var widgetPickerConfig: WidgetPickerConfig = WidgetPickerConfig()

    private val screenOnListener =
        ScreenOnTracker.ScreenOnListener { on: Boolean -> this.onScreenOnChange(on) }

    override fun getDragLayer(): SimpleDragLayer<WidgetPickerActivity>? = _dragLayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val component = LauncherComponentProvider.get(this)
        component.screenOnTracker.addListener(screenOnListener)
        mDeviceProfile = component.idp.getDeviceProfile(this)

        setContentView(R.layout.widget_picker_activity)
        _dragLayer = findViewById(R.id.drag_layer)
        checkNotNull(_dragLayer).recreateControllers()

        updateStatusBarColor()
        checkNotNull(window)
            .decorView
            .setViewTreeOnBackPressedDispatcherOwner(onBackPressedDispatcherOwner = this)

        val appPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)

        if (appPackageName != null) {
            val userHandle = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)

            userHandle?.let {
                component.widgetPickerComposeWrapper.showWidgetsFor(
                    packageName = appPackageName,
                    userHandle = userHandle,
                    activity = this,
                    widgetPickerConfig = widgetPickerConfig,
                )
            } ?: finish()
        } else {
            component.widgetPickerComposeWrapper.showAllWidgets(this, widgetPickerConfig)
        }
    }

    private fun updateStatusBarColor() {
        val contextTheme = ContextThemeWrapper(this, Themes.getActivityThemeRes(this))
        val flags =
            Themes.getAttrBoolean(contextTheme, R.attr.isWorkspaceDarkText).let { isLight ->
                if (isLight) {
                    SystemUiController.FLAG_LIGHT_STATUS
                } else {
                    SystemUiController.FLAG_DARK_STATUS
                }
            }
        systemUiController?.updateUiState(SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, flags)
    }

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() =
            OnBackPressedDispatcher().apply {
                if (Build.VERSION.SDK_INT >= 33) {
                    setOnBackInvokedDispatcher(onBackInvokedDispatcher)
                }
            }

    override fun registerBackDispatcher() {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            this,
        )
    }

    override fun onBackInvoked() {
        finish()
    }

    private fun onScreenOnChange(on: Boolean) {
        if (!on) {
            finish() // auto close when user locks device.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenOnTracker.INSTANCE[this].removeListener(screenOnListener)
    }
}
