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

package com.android.launcher3.qsb

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.widget.RemoteViews
import com.android.launcher3.qsb.OSEManager.Companion.OSE_LOOPER
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.SafeCloseable
import java.util.concurrent.CopyOnWriteArraySet

/** AppWidgetHost used for QSB */
abstract class QsbAppWidgetHost(ctx: Context) : AppWidgetHost(WrappedContext(ctx), HOST_ID) {

    private val currentState = MutableState()
    private val callbacks = CopyOnWriteArraySet<MutableState>().apply { add(currentState) }
    private var activeWidgetId = INVALID_APPWIDGET_ID

    fun addCallbacks(c: MutableState): SafeCloseable {
        callbacks.add(c)
        c.providerInfo.dispatchValue(currentState.providerInfo.value)
        c.views.dispatchValue(currentState.views.value)
        return SafeCloseable { callbacks.remove(c) }
    }

    /** Starts listening for any updates for the provided widget id */
    fun setActiveWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        if (activeWidgetId == appWidgetId) return
        if (activeWidgetId != INVALID_APPWIDGET_ID) deleteAppWidgetId(activeWidgetId)

        activeWidgetId = appWidgetId
        if (appWidgetId != INVALID_APPWIDGET_ID) {
            startUpdateListener(appWidgetId, info)
        }
    }

    /** Starts an update listener for the [appWidgetId] with [info] */
    protected abstract fun startUpdateListener(appWidgetId: Int, info: AppWidgetProviderInfo?)

    /** Executes the [update] for all registered callbacks if [appWidgetId] is currently active */
    protected fun updateAllCallbacks(appWidgetId: Int, update: MutableState.() -> Unit) {
        if (appWidgetId == activeWidgetId) callbacks.forEach { update.invoke(it) }
    }

    fun getActiveWidgetId() = activeWidgetId

    /**
     * Returns the currently bound widget id to this host or [INVALID_APPWIDGET_ID] if none are
     * bound. In multiple widgets are bounds, it deletes all except the last one.
     */
    fun getBoundWidgetId(): Int {
        val currentWidgets = appWidgetIds
        if (currentWidgets.isNotEmpty()) {
            // Delete all widgets except the last
            for (i in 0..(currentWidgets.size - 2)) deleteAppWidgetId(currentWidgets[i])
            return currentWidgets.last()
        } else {
            return INVALID_APPWIDGET_ID
        }
    }

    private class WrappedContext(ctx: Context) : ContextWrapper(ctx) {

        override fun getMainLooper() = OSE_LOOPER.looper
    }

    class MutableState {
        val providerInfo = MutableListenableRef<AppWidgetProviderInfo?>(null)
        val views = MutableListenableRef<RemoteViews?>(null)
    }

    companion object {
        // Any fixed integer as long as it doesn't conflict with other widget hosts
        const val HOST_ID = 1025
    }
}
