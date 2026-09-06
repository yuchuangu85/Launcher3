/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.dragndrop

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import com.android.launcher3.BaseActivity
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherComponentProvider
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.SerializedItemItem
import com.android.launcher3.model.WidgetsModel
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.pm.PinRequestHelper
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widgetpicker.WidgetPickerConfig
import kotlin.math.min

/** Activity to show pin widget dialog. */
open class AddItemActivity :
    BaseActivity(), OnBackPressedDispatcherOwner, OnBackAnimationCallback, PinItemAddHandler {

    private lateinit var pinItemRequest: LauncherApps.PinItemRequest
    private lateinit var app: LauncherAppState
    private lateinit var idp: InvariantDeviceProfile
    private lateinit var dragLayer: BaseDragLayer<AddItemActivity>
    private lateinit var accessibilityManager: AccessibilityManager

    // Widget request specific options.
    private var appWidgetHolder: LauncherWidgetHolder? = null
    private var appWidgetManager: WidgetManagerHelper? = null
    private var pendingBindWidgetId = 0
    private var widgetOptions: Bundle? = null

    private var mFinishOnPause = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pinItemRequest =
            PinRequestHelper.getPinItemRequest(intent)
                ?: run {
                    finish()
                    return
                }

        app = getInstance(this)
        idp = app.invariantDeviceProfile

        // Use the application context to get the device profile, as in multiwindow-mode, the
        // confirmation activity might be rotated.
        mDeviceProfile = idp.getDeviceProfile(applicationContext)

        setContentView(R.layout.add_item_confirmation_activity_compose)

        // Set flag to allow activity to draw over navigation and status bar.
        checkNotNull(window)
            .setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
        dragLayer = findViewById(R.id.add_item_drag_layer)
        dragLayer.recreateControllers()
        accessibilityManager =
            checkNotNull(applicationContext.getSystemService(AccessibilityManager::class.java))
        appWidgetManager = WidgetManagerHelper(this)
        appWidgetHolder = LauncherWidgetHolder.newInstance(this)

        val targetApp =
            when (pinItemRequest.requestType) {
                LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> setupShortcut()
                LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> setupWidget()
                else -> null
            }
        if (targetApp == null) {
            // TODO: show error toast?
            finish()
            return
        }
        val info = ApplicationInfoWrapper(this, targetApp.packageName, targetApp.user).getInfo()
        if (info == null) {
            finish()
            return
        }

        window?.decorView?.setViewTreeOnBackPressedDispatcherOwner(this)

        // Wait for activity slide in to happen before starting sheet open animation.
        // Otherwise, the activity background also appears as if it slides in.
        // This is since we use background dim (in styles.xml) for activity.
        dragLayer.postDelayed(
            {
                LauncherComponentProvider.get(this)
                    .widgetPickerComposeWrapper
                    .showWidgetsForPinRequest(
                        activity = this,
                        targetApp = targetApp.toPackageUserKey(),
                        pinItemRequest = pinItemRequest,
                        widgetPickerConfig = WidgetPickerConfig(),
                        pinItemAddHandler = this,
                    )
            },
            ACTIVITY_SLIDE_IN_DURATION_MS,
        )
    }

    override fun onPause() {
        super.onPause()
        if (mFinishOnPause) {
            finish()
        }
    }

    private fun setupShortcut(): PackageItemInfo {
        return checkNotNull(pinItemRequest.shortcutInfo).let {
            PackageItemInfo(it.getPackage(), it.userHandle)
        }
    }

    private fun setupWidget(): PackageItemInfo? {
        val widgetInfo =
            LauncherAppWidgetProviderInfo.fromProviderInfo(
                this,
                pinItemRequest.getAppWidgetProviderInfo(this),
            )
        if (widgetInfo.minSpanX > idp.numColumns || widgetInfo.minSpanY > idp.numRows) {
            // Cannot add widget
            return null
        }

        val pendingInfo =
            PendingAddWidgetInfo(widgetInfo, LauncherSettings.Favorites.CONTAINER_PIN_WIDGETS)
        pendingInfo.spanX = min(idp.numColumns.toDouble(), widgetInfo.spanX.toDouble()).toInt()
        pendingInfo.spanY = min(idp.numRows.toDouble(), widgetInfo.spanY.toDouble()).toInt()
        widgetOptions = pendingInfo.getDefaultSizeOptions(this)

        return WidgetsModel.newPendingItemInfo(this, widgetInfo.component, widgetInfo.user)
    }

    /** Called when place-automatically button is clicked. */
    fun onPlaceAutomaticallyClick(v: View?) {
        if (pinItemRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val shortcutInfo = checkNotNull(pinItemRequest.shortcutInfo)
            ItemInstallQueue.INSTANCE[this].queueItem(SerializedItemItem(shortcutInfo))
            logCommand(LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_PLACED_AUTOMATICALLY)
            pinItemRequest.accept()
            var label = shortcutInfo.longLabel
            if (TextUtils.isEmpty(label)) {
                label = shortcutInfo.shortLabel
            }
            sendWidgetAddedToScreenAccessibilityEvent(label.toString())
            finish()
            return
        }

        appWidgetHolder?.let { widgetHolder ->
            pendingBindWidgetId = widgetHolder.allocateAppWidgetId()
            val widgetProviderInfo = checkNotNull(pinItemRequest.getAppWidgetProviderInfo(this))
            val success =
                checkNotNull(appWidgetManager)
                    .bindAppWidgetIdIfAllowed(
                        pendingBindWidgetId,
                        widgetProviderInfo,
                        widgetOptions,
                    )
            if (success) {
                sendWidgetAddedToScreenAccessibilityEvent(widgetProviderInfo.label)
                acceptWidget(pendingBindWidgetId)
                return
            }

            // request bind widget
            widgetHolder.startBindFlow(
                this,
                pendingBindWidgetId,
                checkNotNull(pinItemRequest.getAppWidgetProviderInfo(this)),
                REQUEST_BIND_APPWIDGET,
            )
        }
    }

    override fun onAddItemClicked() {
        onPlaceAutomaticallyClick(/*view*/ null)
        finish()
    }

    private fun acceptWidget(widgetId: Int) {
        pinItemRequest.getAppWidgetProviderInfo(this)?.let {
            ItemInstallQueue.INSTANCE[this].queueItem(SerializedItemItem(it, widgetId))
        }
        widgetOptions?.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        pinItemRequest.accept(widgetOptions)
        logCommand(LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_PLACED_AUTOMATICALLY)
        finish()
    }

    public override fun onDestroy() {
        super.onDestroy()
        // Necessary to destroy the holder to free up possible activity context
        appWidgetHolder?.destroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        logCommand(LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_BACK)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_BIND_APPWIDGET) {
            val widgetId =
                data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingBindWidgetId)
                    ?: pendingBindWidgetId

            if (resultCode == RESULT_OK) {
                acceptWidget(widgetId)
            } else {
                // Simply wait it out.
                appWidgetHolder?.deleteAppWidgetId(widgetId)
                pendingBindWidgetId = -1
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_EXTRA_WIDGET_ID, pendingBindWidgetId)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        pendingBindWidgetId = savedInstanceState.getInt(STATE_EXTRA_WIDGET_ID, pendingBindWidgetId)
    }

    override fun getDragLayer(): BaseDragLayer<*> {
        return dragLayer
    }

    private fun sendWidgetAddedToScreenAccessibilityEvent(widgetName: String) {
        if (accessibilityManager.isEnabled) {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            event.contentDescription =
                applicationContext.resources.getString(
                    R.string.added_to_home_screen_accessibility_text,
                    widgetName,
                )
            accessibilityManager.sendAccessibilityEvent(event)
        }
    }

    private fun logCommand(command: StatsLogManager.EventEnum) {
        statsLogManager.logger().log(command)
    }

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() =
            OnBackPressedDispatcher().apply {
                if (Build.VERSION.SDK_INT >= 33) {
                    setOnBackInvokedDispatcher(onBackInvokedDispatcher)
                }
            }

    public override fun registerBackDispatcher() {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            this,
        )
    }

    override fun onBackInvoked() {
        finish()
    }

    companion object {
        private const val ACTIVITY_SLIDE_IN_DURATION_MS = 150L // config_activityShortDur
        private const val SHADOW_SIZE = 10

        private const val REQUEST_BIND_APPWIDGET = 1
        private const val STATE_EXTRA_WIDGET_ID = "state.widget.id"

        private fun PackageItemInfo.toPackageUserKey() =
            if (widgetCategory != -1) {
                PackageUserKey(widgetCategory, user)
            } else {
                PackageUserKey(packageName, user)
            }
    }
}

/** Interface for handling the add action in the Pin Item flow. */
interface PinItemAddHandler {
    /** Called when the user confirms adding the item (widget or shortcut). */
    fun onAddItemClicked()
}
