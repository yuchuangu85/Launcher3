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

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process.myUserHandle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.ViewEx.registerLifecycleTask
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView

/**
 * Renders the On-device search engine's widget [RemoteViews] based on [AppWidgetProviderInfo] by
 * listening to OSE changes through [OseWidgetManager]
 */
class OseWidgetView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    LauncherAppWidgetHostView(context) {

    private val oseWidgetManager = context.appComponent.oseWidgetManager
    @VisibleForTesting var closeActions = RunnableList()
    private val activityContext: ActivityContext = ActivityContext.lookupContext(context)

    internal var autoUpdateTag = true

    init {
        activityContext.appWidgetHolder?.onViewCreationCallback?.accept(this)
        setOnLongClickListener {
            PopupContainer.showForMenuItems(
                activityContext,
                this,
                activityContext.activityComponent.getOseWidgetOptionsProvider().getOptionItems(),
            ) != null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedToWindow()
    }

    @VisibleForTesting
    fun attachedToWindow() {
        closeActions.executeAllAndClear()

        // We use INVALID_APPWIDGET_ID because appWidgetId is not tracked in OseWidgetView. Instead
        // it is managed by OseWidgetManager and QsbAppWidgetHost.
        closeActions.add(
            oseWidgetManager.providerInfo.forEach(activityContext.uiExecutor) {
                setAppWidget(INVALID_APPWIDGET_ID, it)
                // We will get valid updateAppWidget remoteview call from OseWidgetManager again.
                // This is only for resetting the remoteviews using a broken remote view.
                updateAppWidget(RemoteViews(context.packageName, 0))
                if (autoUpdateTag) tag = getTagInfo(it)
                Log.i(TAG, "setAppWidget providerInfo=$it")
            }::close
        )
        closeActions.add(
            oseWidgetManager.views.forEach(activityContext.uiExecutor) {
                updateAppWidget(it)
                Log.i(TAG, "updateAppWidget view=$it")
            }::close
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachedFromWindow()
    }

    @VisibleForTesting
    fun detachedFromWindow() {
        closeActions.executeAllAndClear()
    }

    override fun shouldDelayChildPressedState(): Boolean {
        // Delay the ripple effect on the widget view when swiping up from home screen
        // to go to all apps.
        return true
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun getErrorView(): View {
        val view =
            View.inflate(context, R.layout.ose_default_bubbletext_layout, null) as BubbleTextView
        val oseInfo = context.appComponent.getOseManager().oseInfo.value
        val osePkg: String? =
            when {
                oseInfo.isOseConfigured -> oseInfo.pkg
                else -> null
            }

        val appsStore = activityContext.activityComponent.appsStore
        val title = context.getText(R.string.abandoned_search)
        val updateListener =
            AllAppsStore.OnUpdateListener {
                val appInfo =
                    osePkg
                        ?.let {
                            appsStore.getApp(
                                ComponentKey(ComponentName(osePkg, ""), myUserHandle()),
                                AppInfo.PACKAGE_KEY_COMPARATOR,
                            )
                        }
                        ?.clone()

                if (appInfo == null) {
                    view.tag = null
                    view.applyIconAndLabel(
                        context.getDrawable(R.drawable.ic_allapps_search)!!.mutate().apply {
                            setTint(context.getColor(R.color.materialColorOnSurface))
                        },
                        title,
                        null,
                    )
                    // Since we don't have a valid appInfo, just open the default browser
                    // Set the data to a blank page uri
                    view.setOnClickIntent(Intent(Intent.ACTION_VIEW).setData("http://".toUri()))
                } else {
                    appInfo.title = title
                    view.applyFromApplicationInfo(appInfo)
                    view.setOnClickIntent(
                        when {
                            // Launch search intent.
                            oseInfo.supportsSearchIntent ->
                                Intent(Intent.ACTION_SEARCH).setPackage(appInfo.targetPackage)
                            // Launch main activity
                            else -> appInfo.intent
                        }
                    )
                }
            }

        updateListener.onAppsUpdated()
        view.registerLifecycleTask {
            appsStore.addUpdateListener(updateListener)
            SafeCloseable { appsStore.removeUpdateListener(updateListener) }
        }
        return view
    }

    fun View.setOnClickIntent(intent: Intent) = setOnClickListener {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (intent.action == Intent.ACTION_VIEW) {
            // Browser Intent and set the default browser package.
            val resolveInfo =
                runCatching {
                        context.packageManager.resolveActivity(
                            intent,
                            PackageManager.MATCH_DEFAULT_ONLY,
                        )
                    }
                    .getOrNull()
            resolveInfo?.activityInfo?.packageName.apply { intent.setPackage(this) }
        }
        activityContext.startActivitySafely(
            this@OseWidgetView,
            intent,
            this@OseWidgetView.tag as? ItemInfo,
        )
    }

    private class QsbItemInfo : ItemInfo() {

        override fun getStableId() = STABLE_ID
    }

    companion object {
        private const val TAG = "OseWidgetView"

        private val STABLE_ID = Object()

        private fun getTagInfo(provider: AppWidgetProviderInfo?): ItemInfo {
            val info =
                provider?.let { LauncherAppWidgetInfo(INVALID_APPWIDGET_ID, it.provider) }
                    ?: QsbItemInfo()
            info.id = R.id.search_container_hotseat
            info.container = Favorites.CONTAINER_HOTSEAT
            return info
        }
    }
}
