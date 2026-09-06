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

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps.PinItemRequest
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import com.android.launcher3.BaseActivity
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.Utilities.shouldReduceWorkspaceBlurUsage
import com.android.launcher3.concurrent.annotations.BackgroundContext
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dragndrop.PinItemAddHandler
import com.android.launcher3.dragndrop.PinItemDragListener
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.WindowBlurState
import com.android.launcher3.widgetpicker.WidgetPickerConfig.Companion.EXTRA_IS_PENDING_WIDGET_DRAG
import com.android.launcher3.widgetpicker.data.repository.WidgetAppIconsRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetUsersRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository.InitializationOptions
import com.android.launcher3.widgetpicker.listeners.WidgetPickerAddItemListener
import com.android.launcher3.widgetpicker.listeners.WidgetPickerDragItemListener
import com.android.launcher3.widgetpicker.logging.LauncherWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.shared.model.CloseBehavior
import com.android.launcher3.widgetpicker.shared.model.HostConstraint
import com.android.launcher3.widgetpicker.shared.model.SheetStyle
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.isAppWidget
import com.android.launcher3.widgetpicker.theme.LauncherWidgetPickerTheme
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.WidgetInteractionSource
import com.android.launcher3.widgetpicker.ui.WidgetPickerEventListeners
import com.android.launcher3.widgetpicker.ui.components.WidgetPickerHostStateProvider
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.launch

/**
 * An helper that bootstraps widget picker UI (from [WidgetPickerComponent]) in to
 * [WidgetPickerActivity] when compose is available and widget picker refactor flags are on.
 *
 * Sets up the bindings necessary for widget picker component.
 */
class WidgetPickerComposeWrapperImpl
@Inject
constructor(
    private val widgetPickerComponentProvider: Provider<WidgetPickerComponent.Factory>,
    private val widgetsRepository: WidgetsRepository,
    private val widgetUsersRepository: WidgetUsersRepository,
    private val widgetAppIconsRepository: WidgetAppIconsRepository,
    @BackgroundContext private val backgroundContext: CoroutineContext,
    @ApplicationContext private val appContext: Context,
    private val apiWrapper: ApiWrapper,
) : WidgetPickerComposeWrapper {

    override fun showAllWidgets(activity: BaseActivity, widgetPickerConfig: WidgetPickerConfig) {
        val widgetPickerComponent = newWidgetPickerComponent(widgetPickerConfig)
        val fullWidgetsCatalog = widgetPickerComponent.getFullWidgetsCatalog()

        setupComposeView(activity = activity, widgetPickerConfig = widgetPickerConfig) {
            eventListeners,
            eventsReporter ->
            fullWidgetsCatalog.Content(
                eventListeners = eventListeners,
                cuiReporter = eventsReporter,
                hostStateProvider = activity.buildWidgetPickerHostStateProvider(),
            )
        }
    }

    override fun showWidgetsFor(
        packageName: String,
        userHandle: UserHandle,
        activity: BaseActivity,
        widgetPickerConfig: WidgetPickerConfig,
    ) {
        val widgetAppId =
            WidgetAppId(
                packageName = packageName,
                userHandle = userHandle,
                category = NO_WIDGET_APP_CATEGORY,
            )

        val widgetPickerComponent = newWidgetPickerComponent(widgetPickerConfig)
        val singleAppCatalog = widgetPickerComponent.getSingleAppWidgetsCatalog()

        setupComposeView(
            activity = activity,
            widgetPickerConfig = widgetPickerConfig,
            widgetAppId = widgetAppId,
        ) { eventListeners, uiEventsReporter ->
            singleAppCatalog.Content(
                widgetAppId = widgetAppId,
                eventListeners = eventListeners,
                cuiReporter = uiEventsReporter,
                hostStateProvider = activity.buildWidgetPickerHostStateProvider(),
            )
        }
    }

    override fun showWidgetsForPinRequest(
        activity: BaseActivity,
        targetApp: PackageUserKey,
        pinItemRequest: PinItemRequest,
        widgetPickerConfig: WidgetPickerConfig,
        pinItemAddHandler: PinItemAddHandler,
    ) {
        val widgetPickerComponent = newWidgetPickerComponent(widgetPickerConfig)
        val pinAppWidgetCatalog = widgetPickerComponent.getPinAppWidgetCatalog()

        val widgetAppId =
            WidgetAppId(
                packageName = targetApp.mPackageName,
                userHandle = targetApp.mUser,
                category = targetApp.mWidgetCategory,
            )

        setupComposeView(
            activity = activity,
            widgetPickerConfig = widgetPickerConfig,
            pinItemRequest = pinItemRequest,
            pinItemAddHandler = pinItemAddHandler,
        ) { eventListeners, uiEventsReporter ->
            pinAppWidgetCatalog.Content(
                widgetAppId = widgetAppId,
                eventListeners = eventListeners,
                cuiReporter = uiEventsReporter,
                previewOverridesProvider = {
                    if (pinItemRequest.requestType == PinItemRequest.REQUEST_TYPE_APPWIDGET) {
                        val extras: Bundle? = pinItemRequest.extras
                        val previewExtra =
                            extras?.getParcelable(
                                AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,
                                RemoteViews::class.java,
                            )
                        if (previewExtra != null) {
                            val context = activity.asContext()
                            val widgetInfo =
                                checkNotNull(pinItemRequest.getAppWidgetProviderInfo(context))
                            val widgetId =
                                WidgetId(
                                    componentName = widgetInfo.provider,
                                    userHandle = widgetInfo.profile,
                                )
                            val preview = WidgetPreview.RemoteViewsWidgetPreview(previewExtra)

                            return@Content mapOf(widgetId to preview)
                        }
                    }
                    emptyMap()
                },
            )
        }
    }

    private fun setupComposeView(
        activity: BaseActivity,
        widgetPickerConfig: WidgetPickerConfig,
        widgetAppId: WidgetAppId? = null,
        pinItemRequest: PinItemRequest? = null,
        pinItemAddHandler: PinItemAddHandler? = null,
        content: @Composable (WidgetPickerEventListeners, LauncherWidgetPickerCuiReporter) -> Unit,
    ) {
        val callbacks =
            activity.buildEventListeners(
                widgetPickerConfig = widgetPickerConfig,
                apiWrapper = apiWrapper,
                pinItemRequest = pinItemRequest,
                pinItemAddHandler = pinItemAddHandler,
            )
        val uiEventsReporter = LauncherWidgetPickerCuiReporter(activity.statsLogManager)

        val composeView = ComposeView(activity.asContext())

        val supportsBlurTokens =
            Flags.enableWidgetPickerBlur() &&
                activity
                    .asContext()
                    .resources
                    .getBoolean(R.bool.config_widgetPickerSupportsBlurTokens)
        val isBlurEnabled =
            !shouldReduceWorkspaceBlurUsage(activity) &&
                WindowBlurState.getInstance(activity.asContext()).value

        composeView.apply {
            setContent {
                val scope = rememberCoroutineScope()
                val view = LocalView.current

                LauncherWidgetPickerTheme(
                    supportsBlurTokens = supportsBlurTokens,
                    isBlurEnabled = isBlurEnabled,
                ) {
                    val eventListeners = remember { callbacks }
                    content(eventListeners, uiEventsReporter)
                }

                DisposableEffect(view) {
                    scope.launch { initializeRepositories(widgetAppId, pinItemRequest) }

                    onDispose { cleanUpRepositories() }
                }
            }
        }

        checkNotNull(activity.dragLayer).addView(composeView)
    }

    private fun newWidgetPickerComponent(
        widgetPickerConfig: WidgetPickerConfig
    ): WidgetPickerComponent {
        return widgetPickerComponentProvider
            .get()
            .build(
                widgetsRepository = widgetsRepository,
                widgetUsersRepository = widgetUsersRepository,
                widgetAppIconsRepository = widgetAppIconsRepository,
                widgetHostInfo =
                    WidgetHostInfo(
                        title =
                            widgetPickerConfig.title
                                ?: appContext.resources.getString(R.string.widget_button_text),
                        description = widgetPickerConfig.description,
                        constraints = widgetPickerConfig.asHostConstraints(),
                        showDragShadow = !widgetPickerConfig.isForHomeScreen,
                        enableSwipeUpToDismiss = widgetPickerConfig.enableSwipeUpToDismiss,
                        closeBehavior =
                            if (widgetPickerConfig.isDesktopFormFactor) CloseBehavior.CLOSE_BUTTON
                            else CloseBehavior.DRAG_HANDLE,
                        sheetStyle =
                            if (widgetPickerConfig.enableCursorDrivenWorkflows)
                                SheetStyle.FLOATING_SHEET
                            else SheetStyle.BOTTOM_SHEET,
                    ),
                backgroundContext = backgroundContext,
            )
    }

    private fun initializeRepositories(
        widgetAppId: WidgetAppId? = null,
        pinItemRequest: PinItemRequest? = null,
    ) {
        val loadAllData = widgetAppId == null && pinItemRequest == null

        widgetsRepository.initialize(
            options =
                when {
                    widgetAppId != null -> InitializationOptions.SingleAppWidgets(widgetAppId)
                    pinItemRequest != null -> InitializationOptions.PinWidget(pinItemRequest)
                    else -> InitializationOptions.AllWidgets
                }
        )

        if (loadAllData) {
            widgetUsersRepository.initialize()
            widgetAppIconsRepository.initialize()
        }
    }

    private fun cleanUpRepositories() {
        widgetsRepository.cleanUp()
        widgetUsersRepository.cleanUp()
        widgetAppIconsRepository.cleanUp()
    }

    companion object {
        private const val TAG = "WidgetPickerComposeWrapperImpl"
        private const val HOME_SCREEN_WIDGET_INTERACTION_REASON_STRING =
            "WidgetPickerActivity.OnWidgetInteraction"
        private const val NO_WIDGET_APP_CATEGORY = -1

        private fun BaseActivity.buildWidgetPickerHostStateProvider() =
            object : WidgetPickerHostStateProvider {
                val listeners: MutableList<(Boolean) -> Unit> = mutableListOf()
                var activityObserver: Runnable? = null

                override fun observeIsTopResumed(listener: (Boolean) -> Unit) {
                    if (activityObserver == null) {
                        activityObserver =
                            object : Runnable {
                                override fun run() {
                                    listeners.forEach { it(isTopResumedActivity()) }
                                }
                            }
                        addTopResumedChangedCallback(activityObserver)
                    }
                    listeners.add(listener)
                }

                override fun stopObservingIsTopResumed(listener: (Boolean) -> Unit) {
                    listeners.remove(listener)

                    if (listeners.isEmpty() && activityObserver != null) {
                        removeTopResumedChangedCallback(activityObserver)
                        activityObserver = null
                    }
                }
            }

        private fun Activity.buildEventListeners(
            widgetPickerConfig: WidgetPickerConfig,
            apiWrapper: ApiWrapper,
            pinItemRequest: PinItemRequest?,
            pinItemAddHandler: PinItemAddHandler?,
        ) =
            object : WidgetPickerEventListeners {
                override fun onSheetProgress(progress: Float) {
                    if (this@buildEventListeners is WidgetPickerProgressHandler) {
                        this@buildEventListeners.onProgress(progress)
                    }
                }

                override fun onClose() {
                    Log.d(TAG, "Closing widget picker")
                    finish()
                }

                override fun onWidgetInteraction(widgetInteractionInfo: WidgetInteractionInfo) {
                    if (
                        pinItemRequest != null &&
                            widgetInteractionInfo is WidgetInteractionInfo.WidgetAddInfo
                    ) {
                        pinItemAddHandler?.onAddItemClicked()
                        return
                    }

                    if (widgetPickerConfig.isForHomeScreen || pinItemRequest != null) {
                        handleWidgetInteractionForHomeScreen(
                            widgetInteractionInfo,
                            apiWrapper,
                            pinItemRequest,
                        )
                    } else {
                        handleWidgetInteractionForExternalHost(widgetInteractionInfo)
                    }
                }
            }

        /**
         * Handles communication with the home screen about the "add" and "drag" interactions on
         * widgets within widget picker.
         *
         * For home screen, we register a listener that is called back when home screen is shown;
         * - WidgetPickerDragItemListener / PinItemDragListener: bootstraps the drag helper that
         *   displays the shadow and handles the drag until completion.
         * - WidgetPickerAddItemListener: once launcher is shown, triggers the flow to add the
         *   widget to workspace. For pin flow, there is no listener and the activity handles the
         *   add.
         */
        private fun Activity.handleWidgetInteractionForHomeScreen(
            interactionInfo: WidgetInteractionInfo,
            apiWrapper: ApiWrapper,
            pinItemRequest: PinItemRequest?,
        ) {
            val interactionListener =
                when (interactionInfo) {
                    is WidgetInteractionInfo.WidgetDragInfo ->
                        if (pinItemRequest != null) {
                            PinItemDragListener(
                                pinItemRequest,
                                /*previewRect=*/ interactionInfo.bounds,
                                /*previewBitmapWidth=*/ interactionInfo.widthPx,
                                /*previewViewWidth=*/ interactionInfo.widthPx,
                                /*mimeType=*/ interactionInfo.mimeType,
                            )
                        } else {
                            WidgetPickerDragItemListener(
                                container = interactionInfo.source.toContainer(),
                                mimeType = interactionInfo.mimeType,
                                widgetInfo = interactionInfo.widgetInfo,
                                widgetPreview = interactionInfo.previewInfo,
                                previewRect = interactionInfo.bounds,
                                previewWidth = interactionInfo.widthPx,
                            )
                        }

                    is WidgetInteractionInfo.WidgetAddInfo ->
                        WidgetPickerAddItemListener(
                            container = interactionInfo.source.toContainer(),
                            widgetInfo = interactionInfo.widgetInfo,
                        )
                }
            Launcher.ACTIVITY_TRACKER.registerCallback(
                interactionListener,
                HOME_SCREEN_WIDGET_INTERACTION_REASON_STRING,
            )
            startActivity(
                /*intent=*/ Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(packageName)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                /*options=*/ apiWrapper.createFadeOutAnimOptions().toBundle(),
            )
            finish()
        }

        /**
         * Handles communication with the external host about the "add" and "drag" interactions on
         * widgets within widget picker.
         * - In case of drag and drop, finishes the activity with result indicating that there is a
         *   pending drag [EXTRA_IS_PENDING_WIDGET_DRAG] (that would contain the widget info as part
         *   of clip data) that the host should be handling.
         * - In case of add, finishes the activity with result containing extra information about
         *   the widget being added (namely [Intent.EXTRA_COMPONENT_NAME] and [Intent.EXTRA_USER].
         */
        private fun Activity.handleWidgetInteractionForExternalHost(
            widgetInteractionInfo: WidgetInteractionInfo
        ) {
            when (widgetInteractionInfo) {
                is WidgetInteractionInfo.WidgetDragInfo ->
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_IS_PENDING_WIDGET_DRAG, true))

                is WidgetInteractionInfo.WidgetAddInfo -> {
                    val widgetInfo = widgetInteractionInfo.widgetInfo
                    if (widgetInfo.isAppWidget()) {
                        val providerInfo = widgetInfo.appWidgetProviderInfo
                        setResult(
                            RESULT_OK,
                            Intent().apply {
                                putExtra(Intent.EXTRA_COMPONENT_NAME, providerInfo.provider)
                                putExtra(Intent.EXTRA_USER, providerInfo.profile)
                            },
                        )
                    } else {
                        throw IllegalStateException(
                            "AppWidgetInfo not provided for external host drag"
                        )
                    }
                }
            }

            finish()
        }

        /** Builds the host constraints to provide to the widget picker module. */
        fun WidgetPickerConfig.asHostConstraints() = buildList {
            if (filteredUsers.isNotEmpty()) {
                add(HostConstraint.HostUserConstraint(filteredUsers))
            }
            if (!isForHomeScreen) {
                add(HostConstraint.NoShortcutsConstraint)
            }
            if (categoryInclusionFilter != 0 || categoryExclusionFilter != 0) {
                add(
                    HostConstraint.HostCategoryConstraint(
                        categoryInclusionMask = categoryInclusionFilter,
                        categoryExclusionMask = categoryExclusionFilter,
                    )
                )
            }
        }

        private fun WidgetInteractionSource.toContainer(): Int =
            when (this) {
                WidgetInteractionSource.FEATURED -> Favorites.CONTAINER_WIDGETS_PREDICTION
                WidgetInteractionSource.SEARCH,
                WidgetInteractionSource.BROWSE -> Favorites.CONTAINER_WIDGETS_TRAY

                WidgetInteractionSource.APP_SPECIFIC_PICKER ->
                    Favorites.CONTAINER_BOTTOM_WIDGETS_TRAY

                WidgetInteractionSource.PIN_WIDGET_PICKER -> Favorites.CONTAINER_PIN_WIDGETS
            }
    }
}

/**
 * Interface for activities to perform an operation (e.g. background scrim animation) on while
 * widget picker opens / closes.
 */
interface WidgetPickerProgressHandler {
    /**
     * Callback during opening / closing of widget picker. Progress is between 0-1 where 0 is fully
     * closed and 1 is fully open.
     */
    fun onProgress(progress: Float) {} // NO-op
}
