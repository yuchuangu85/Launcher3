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

package com.android.launcher3.qsb

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting

/** Implementation of [QsbAppWidgetHost] which uses views to dispatch events */
class QsbAppWidgetHostImpl @VisibleForTesting constructor(private val context: Context) :
    QsbAppWidgetHost(context) {

    override fun startUpdateListener(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        createView(context, appWidgetId, info)
    }

    override fun onCreateView(
        context: Context?,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = DelegateHostView(appWidgetId)

    private inner class DelegateHostView(val widgetId: Int) : AppWidgetHostView(context) {

        override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) =
            updateAllCallbacks(widgetId) { providerInfo.dispatchValue(info) }

        override fun updateAppWidget(remoteViews: RemoteViews?) =
            updateAllCallbacks(widgetId) { views.dispatchValue(remoteViews) }
    }

    companion object {

        private val lock = Any()
        @SuppressLint("StaticFieldLeak") private var staticHost: QsbAppWidgetHostImpl? = null

        /**
         * Returns a static instance of [QsbAppWidgetHost]
         *
         * AppWidget service only allows one host per process with a particular hostId. Since
         * Launcher creates multiple dagger graphs during it lifecycle, it can end up creating
         * multiple hosts. Any new host creation causes all previous hosts to stop working.
         *
         * Maintaining a static singleton ensures that we do not run into that situation.
         */
        fun getStaticInstance(context: Context): QsbAppWidgetHost {
            val existingHost = staticHost
            if (existingHost != null) return existingHost
            synchronized(lock) {
                var appContext = context.applicationContext

                // Loop through parent contexts, in case we are running in SandboxApplication
                var previousAppContext: Context
                var wrappedApp: Context?
                do {
                    wrappedApp = (appContext as? ContextWrapper)?.baseContext?.applicationContext
                    previousAppContext = appContext
                    appContext = wrappedApp ?: appContext
                } while (appContext !== previousAppContext)

                return QsbAppWidgetHostImpl(appContext).also {
                    it.startListening()
                    staticHost = it
                }
            }
        }
    }
}
