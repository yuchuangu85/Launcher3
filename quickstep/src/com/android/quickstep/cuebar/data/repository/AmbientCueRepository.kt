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

package com.android.quickstep.cuebar.data.repository

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.app.assist.ActivityId
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.RenderToken
import android.service.personalcontext.hint.AutofillInlineRequestHint
import android.service.personalcontext.hint.BundleHint
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationExitEvent
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationUpdateEvent
import android.service.personalcontext.hint.ContentCaptureConversationHint
import android.service.personalcontext.hint.ContextHint
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ActionableInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.HintInvalidationInsight
import android.service.personalcontext.insight.InsightActionDetails
import android.service.personalcontext.insight.InsightCollection
import android.service.personalcontext.insight.InsightDisplayDetails
import android.service.personalcontext.insight.PublishedContextInsight
import android.service.personalcontext.insight.interaction.InsightEvent
import android.util.Log
import android.view.autofill.AutofillManager
import androidx.annotation.VisibleForTesting
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.LauncherComponentProvider
import com.android.launcher3.taskbar.CueBarInsightRendererService
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.MutableListenableRef
import com.android.quickstep.FocusState
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.cuebar.data.ActionModel
import com.android.quickstep.cuebar.data.IconModel
import com.android.quickstep.cuebar.data.InsightListener
import com.android.quickstep.cuebar.logger.AmbientCueAceLogger
import com.android.quickstep.cuebar.logger.AmbientCueLogger
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import dagger.assisted.AssistedInject
import java.io.PrintWriter
import java.lang.ref.WeakReference
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Source of truth for ambient actions and visibility of their system space. */
interface AmbientCueRepository {
    /** Chips that should be visible on the UI. */
    val actions: ListenableRef<List<ActionModel>>

    /** If IME is visible or not. */
    val isImeVisible: ListenableRef<Boolean>

    /** If the UI is occluded. (Hard to determine globally from Launcher) */
    val isOccludedBySystemUi: ListenableRef<Boolean>

    /** If the UI is deactivated, such as closed by user or not used for a long period. */
    val isDeactivated: MutableListenableRef<Boolean>

    /** If the taskbar is fully visible and not stashed. */
    val isTaskBarVisible: MutableListenableRef<Boolean>

    /** True if in gesture nav mode, false when in 3-button navbar. */
    val isGestureNav: MutableListenableRef<Boolean>

    val recentsButtonPosition: MutableListenableRef<Rect?>

    /* If AmbientCue is enabled. */
    val isAmbientCueEnabled: ListenableRef<Boolean>

    /* The timeout for Ambient Cue to disappear. */
    val ambientCueTimeoutMs: ListenableRef<Int>

    /** Task Id which is globally focused on display. */
    val globallyFocusedTaskId: ListenableRef<Int>

    /** The package name of the task that is in the foreground. */
    val frontTaskPackageName: ListenableRef<String>

    val isTestMode: ListenableRef<Boolean>

    fun updateActions(newActions: List<ActionModel>)

    fun connectToAce()

    fun disconnectFromAce()

    fun dump(pw: PrintWriter, prefix: String)

    fun reportCloseEvent()

    /** Triggers a dummy insight for TAPL/Scenario testing. */
    @VisibleForTesting fun injectTestInsightForCueBar()
}

class AmbientCueRepositoryImpl
@AssistedInject
constructor(
    taskbarActivityContext: TaskbarActivityContext,
    private val ambientCueLogger: AmbientCueLogger,
    private val ambientCueAceLogger: AmbientCueAceLogger,
    @Background private val bgExecutor: Executor,
    @Ui private val uiExecutor: Executor,
) : AmbientCueRepository, InsightListener {

    // Repository should not hold strong ref to TaskbarActivityContext to avoid leak.
    private val appContext = taskbarActivityContext.applicationContext
    private val weakTaskbarActivityContext = WeakReference(taskbarActivityContext)
    private val insightHandler: InsightHandler =
        LauncherComponentProvider.get(taskbarActivityContext).getInsightHandler()

    private val backgroundScope = CoroutineScope(bgExecutor.asCoroutineDispatcher())
    private val autofillManager: AutofillManager? =
        taskbarActivityContext.getSystemService(AutofillManager::class.java)

    private val _actions = MutableListenableRef<List<ActionModel>>(emptyList())
    override val actions: MutableListenableRef<List<ActionModel>> = _actions

    override val isDeactivated = MutableListenableRef(false)

    // These need to be updated from outside, e.g., by CuebarController
    override val isTaskBarVisible = MutableListenableRef(true)
    override val isGestureNav = MutableListenableRef(taskbarActivityContext.isGestureNav)
    override val recentsButtonPosition = MutableListenableRef<Rect?>(null)

    private val _isTestMode = MutableListenableRef(false)
    override val isTestMode: ListenableRef<Boolean> = _isTestMode

    // IME and Occlusion are hard to track from Launcher for other apps.
    private val _isImeVisible = MutableListenableRef(false)
    override val isImeVisible: MutableListenableRef<Boolean> = _isImeVisible

    private val _isOccludedBySystemUi = MutableListenableRef(false)
    override val isOccludedBySystemUi: MutableListenableRef<Boolean> = _isOccludedBySystemUi

    private val _isAmbientCueEnabled = MutableListenableRef(isAmbientCueSettingEnabled())
    override val isAmbientCueEnabled: ListenableRef<Boolean> = _isAmbientCueEnabled

    private val _ambientCueTimeoutMs = MutableListenableRef(getAmbientCueTimeoutMs())
    override val ambientCueTimeoutMs: ListenableRef<Int> = _ambientCueTimeoutMs

    private val _globallyFocusedTaskId = MutableListenableRef(INVALID_TASK_ID)
    override val globallyFocusedTaskId: ListenableRef<Int> = _globallyFocusedTaskId

    private val _frontTaskPackageName = MutableListenableRef("")
    override val frontTaskPackageName: ListenableRef<String> = _frontTaskPackageName

    private var debounceTaskJob: Job? = null

    // The hint ID of the current displayed conversation hint. Used to determine if a
    // HintInvalidationInsight is for the current conversation.
    private var currentConversationHintId: UUID? = null

    // The timestamp of the current conversation hint is generated. If an insight has an original
    // hint with a timestamp earlier than this, it should be ignored.
    private var currentConversationHintGenerationTimestamp: Instant? = null

    private val focusListener = AmbientCueFocusListener(WeakReference(this), bgExecutor)

    private fun launchPendingIntent(pendingIntent: PendingIntent) {
        val options = BroadcastOptions.makeBasic()
        options.isInteractive = true
        options.pendingIntentBackgroundActivityStartMode =
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        try {
            pendingIntent.send(options.toBundle())
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "pending intent of $pendingIntent was canceled", e)
        }
    }

    /**
     * The [RunningTaskInfo] for the task that is currently in the foreground. Updated whenever a
     * new task moves to the front. Used to derive the package name for logging and for CueBar to
     * display itself when the user is actually looking at the target app by checking
     * globallyFocusedTaskId == targetTaskId in the viewmodel.
     */
    internal fun onTaskMovedToFront(runningTaskInfo: RunningTaskInfo) {
        debounceTaskJob?.cancel()
        debounceTaskJob =
            backgroundScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                withContext(uiExecutor.asCoroutineDispatcher()) {
                    _globallyFocusedTaskId.dispatchValue(runningTaskInfo.taskId)
                    _frontTaskPackageName.dispatchValue(
                        runningTaskInfo.baseIntent?.component?.packageName ?: ""
                    )
                    debounceTaskJob = null
                }
            }
    }

    override fun updateActions(newActions: List<ActionModel>) {
        Log.d(TAG, "updateActions: DISPATCHING: $newActions")
        _actions.dispatchValue(newActions)
    }

    private fun isAmbientCueSettingEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(appContext.contentResolver, AMBIENT_CUE_SETTING) == OPTED_IN
        } catch (e: Settings.SettingNotFoundException) {
            Log.w(TAG, "$AMBIENT_CUE_SETTING not found, default to enabled")
            true
        }
    }

    private fun getAmbientCueTimeoutMs(): Int {
        return try {
            val timeout =
                Settings.Secure.getInt(
                    appContext.contentResolver,
                    Settings.Secure.ACCESSIBILITY_INTERACTIVE_UI_TIMEOUT_MS,
                )
            if (timeout == 0) AMBIENT_CUE_DEFAULT_TIMEOUT_MS else timeout
        } catch (e: Settings.SettingNotFoundException) {
            AMBIENT_CUE_DEFAULT_TIMEOUT_MS
        }
    }

    override fun dump(pw: PrintWriter, prefix: String) {
        pw.println("$prefix AmbientCueRepositoryImpl:")
        pw.println("$prefix isDeactivated: ${isDeactivated.value}")
        pw.println("$prefix isImeVisible: ${isImeVisible.value}")
        pw.println("$prefix isOccludedBySystemUi: ${isOccludedBySystemUi.value}")
        pw.println("$prefix isTaskBarVisible: ${isTaskBarVisible.value}")
        pw.println("$prefix isGestureNav: ${isGestureNav.value}")
        pw.println("$prefix actions: ${actions.value.size} actions")
        pw.println("$prefix isAmbientCueEnabled: ${isAmbientCueEnabled.value}")
        pw.println("$prefix ambientCueTimeoutMs: ${ambientCueTimeoutMs.value}")
        pw.println("$prefix globallyFocusedTaskId: ${globallyFocusedTaskId.value}")
        pw.println("$prefix debounceTaskJob active: ${debounceTaskJob?.isActive == true}")
        pw.println("$prefix frontTaskPackageName: ${frontTaskPackageName.value}")
        pw.println("$prefix lastPublishedInsight: ${ambientCueAceLogger.lastPublishedInsight}")
        pw.println("$prefix lastRenderToken: ${ambientCueAceLogger.lastRenderToken}")
    }

    private fun ContextInsight.flatten(): List<ContextInsight> {
        return if (this is InsightCollection) {
            this.insights.flatMap { it.flatten() }
        } else {
            listOf(this)
        }
    }

    override fun onInsightReceived(insight: PublishedContextInsight, token: RenderToken) {
        uiExecutor.execute {
            ambientCueAceLogger.onInsightReceived(insight, token)

            if (!insightEligibleForCueBar(insight.getInsight())) {
                return@execute
            }

            val actions = mapInsightToActions(insight.getInsight())

            insight
                .getInsight()
                .originHints
                .map { it.contextHint }
                .filterIsInstance<ContentCaptureConversationHint>()
                .firstOrNull()
                ?.let {
                    currentConversationHintGenerationTimestamp =
                        it.conversationEvent.clientEventTimestamp
                }

            if (actions.isNotEmpty()) {
                // Update the current conversation hint ID if the action is non-empty.
                insight.getInsight().originHints
                    .map { it.contextHint }
                    .filterIsInstance<ContentCaptureConversationHint>()
                    .firstOrNull()
                    ?.let {
                        currentConversationHintId = it.hintId
                    }
                isDeactivated.dispatchValue(false)
            } else {
                Log.i(TAG, "No actions, clear cuebar")
            }
            updateActions(actions)
        }
    }

    private fun hintEligibleForCueBar(contextHint: ContextHint): Boolean {
        return when (contextHint) {
            is BundleHint -> contextHint.dataBundle.getBoolean(RENDER_IN_CUE_BAR, false)
            is ContentCaptureConversationHint -> isValidConversationHint(contextHint)
            else -> false
        }
    }

    private fun isValidConversationHint(contextHint: ContentCaptureConversationHint): Boolean {
        val event = contextHint.conversationEvent
        val isValidEvent = event is ConversationUpdateEvent || event is ConversationExitEvent

        val generationTimestamp = currentConversationHintGenerationTimestamp
        val isValidTimestamp = generationTimestamp == null ||
                event.clientEventTimestamp >= generationTimestamp
        if (!isValidTimestamp) {
            Log.i(TAG, "invalid timestamp: ${event.clientEventTimestamp} < $generationTimestamp")
        }

        return isValidEvent && isValidTimestamp
    }

    private fun insightEligibleForCueBar(insight: ContextInsight): Boolean {
        if (insight.originHints.any { it.contextHint is AutofillInlineRequestHint }) {
            // Always ignore the insight together with AutofillInlineRequestHint.
            return false
        }

        if (insight is HintInvalidationInsight) {
            Log.d(
                TAG,
                "cuebar HintInvalidationInsight: $insight, " +
                    "currentConversationHintId: $currentConversationHintId"
            )
            return insight.invalidatedHintId == currentConversationHintId
        }

        return insight.originHints.any { hintEligibleForCueBar(it.contextHint) }
    }

    @VisibleForTesting
    fun mapInsightToActions(insight: ContextInsight): List<ActionModel> {
        Log.d(TAG, "cuebar eligible insight: $insight")
        val hintToMap =
            insight.originHints.firstOrNull { hintEligibleForCueBar(it.contextHint) }
                ?: return emptyList()
        return mapContextInsightToAction(insight, hintToMap.contextHint)
    }

    @VisibleForTesting
    fun mapContextInsightToAction(
        insight: ContextInsight,
        contextHint: ContextHint,
    ): List<ActionModel> {
        // Keep check here in case this method is called independently like in tests.
        if (insight is InsightCollection) {
            return insight.insights.flatMap { child ->
                mapContextInsightToAction(child, contextHint)
            }
        }
        val display =
            when (insight) {
                is ActionableInsight -> insight.displayDetails
                is DisplayInsight -> insight.details
                else -> return emptyList()
            }
        val actionType: String
        var activityId =
            if (contextHint is ContentCaptureConversationHint) {
                val conversationEvent = contextHint.conversationEvent
                (conversationEvent as? ConversationUpdateEvent)?.conversationData?.activityId
            } else if (contextHint is BundleHint) {
                contextHint.dataBundle.getParcelable<ActivityId>(EXTRA_ACTIVITY_ID)
            } else {
                null
            }
        val isEnabledWithImeVisible =
            insight.originHints
                .mapNotNull { it.contextHint as? BundleHint }
                .firstOrNull { it.hintTypeName == IME_VISIBILITY_HINT_TYPE }
                ?.dataBundle
                ?.getBoolean(EXTRA_ENABLED_WITH_IME_VISIBLE, false) ?: false
        val attributionDialogPendingIntent: PendingIntent? =
            insight.originHints
                .mapNotNull { it.contextHint as? BundleHint }
                .firstOrNull { it.hintTypeName == ATTRIBUTION_INTENT_HINT_TYPE }
                ?.dataBundle
                ?.getParcelable(EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT)
        val oneTapEnabled =
            insight.originHints
                .mapNotNull { it.contextHint as? BundleHint }
                .firstOrNull { it.hintTypeName == ONE_TAP_HINT_TYPE }
                ?.dataBundle
                ?.getBoolean(EXTRA_ONE_TAP_ENABLED, false) ?: false
        val onPerformAction: () -> Unit
        val title = display.title.toString()
        when (insight) {
            is ActionableInsight -> {
                actionType = MA_ACTION_TYPE_NAME
                val action = insight.actionDetails
                val actionPendingIntent = action.pendingIntent
                // TODO(b/485706132): Update due to switchover to PendingIntent

                onPerformAction = {
                    reportInsightEvent(insight, InsightEvent.EVENT_USER_TAP)
                    if (
                        contextHint is BundleHint &&
                            contextHint.dataBundle.getBoolean(NEEDS_DATA_EGRESS, false)
                    ) {
                        insightHandler.egress(insight)
                    } else {
                        when {
                            // 1. Remote Action Send
                            action.hasActionType(
                                InsightActionDetails.ACTION_TYPE_REMOTE_ACTION
                            ) -> {
                                action.remoteAction?.actionIntent?.let { launchPendingIntent(it) }
                            }
                            // 2. Start Activity Intent
                            action.hasActionType(
                                InsightActionDetails.ACTION_TYPE_PENDING_INTENT
                            ) -> {
                                actionPendingIntent?.let { launchPendingIntent(it) }
                            }
                        }
                    }
                    ambientCueLogger.setFulfilledWithMaStatus()
                }
            }
            is DisplayInsight -> {
                actionType = MR_ACTION_TYPE_NAME
                val autofillId =
                    if (contextHint is ContentCaptureConversationHint) {
                        val conversationEvent = contextHint.conversationEvent
                        (conversationEvent as? ConversationUpdateEvent)
                            ?.conversationData
                            ?.inputBoxAutofillId
                    } else {
                        null
                    }
                onPerformAction = {
                    reportInsightEvent(insight, InsightEvent.EVENT_USER_TAP)
                    val token = activityId?.token
                    if (token != null && autofillId != null) {
                        autofillManager?.autofillRemoteApp(
                            autofillId,
                            title,
                            token,
                            activityId.taskId,
                        )
                    }
                    Log.d(TAG, "DisplayInsight ActionModel performed. Logging MR status.")
                    ambientCueLogger.setFulfilledWithMrStatus()
                }
            }
            else -> {
                // Safe return if a new unhandled insight appears.
                return emptyList()
            }
        }
        val attribution = display.subtitle?.toString()
        val iconDrawable =
            try {
                display.icon?.loadDrawable(weakTaskbarActivityContext.get())
            } catch (e: Exception) {
                Log.e(TAG, "Resource loading failed for ID: ${display.icon?.resId}", e)
                null
            } ?: weakTaskbarActivityContext.get()?.getDrawable(R.drawable.ic_paste_spark)!!
        return listOf(
            ActionModel(
                icon =
                    IconModel(
                        small = iconDrawable.mutate(),
                        large = iconDrawable.mutate(),
                        iconId = display.icon?.resPackage + "#" + display.icon?.resId,
                    ),
                label = title,
                attribution = attribution,
                onPerformAction = onPerformAction,
                onPerformLongClick = {
                    Log.i(TAG, "AmbientCueRepositoryImpl: onPerformLongClick")
                    reportInsightEvent(insight, InsightEvent.EVENT_USER_LONG_PRESS)
                    val pendingIntent = attributionDialogPendingIntent
                    if (pendingIntent != null) {
                        Log.i(TAG, "Performing long click: $pendingIntent")
                        launchPendingIntent(pendingIntent)
                    }
                },
                taskId = activityId?.taskId ?: INVALID_TASK_ID,
                actionType = actionType,
                oneTapEnabled = oneTapEnabled,
                oneTapDelayMs = DEFAULT_ONE_TAP_DELAY_MS,
                isEnabledWithImeVisible = isEnabledWithImeVisible,
            )
        )
    }

    override fun reportCloseEvent() {
        ambientCueAceLogger.reportCloseEvent()
    }

    override fun connectToAce() {
        if (!isAmbientCueEnabled.value) {
            Log.d(TAG, "Ace listener register skipped: Ambient Cue setting is disabled.")
            return
        }
        Log.d(TAG, "connectToAce: " + "connecting TO ACE and registering")
        CueBarInsightRendererService.registerListener(this)
        SystemUiProxy.INSTANCE[appContext].focusState.addListener(focusListener)
    }

    private fun reportInsightEvent(childInsight: ContextInsight, event: Int) {
        ambientCueAceLogger.reportInsightEvent(event, childInsight)
    }

    override fun disconnectFromAce() {
        CueBarInsightRendererService.unregisterListener(this)
        backgroundScope.cancel()
        SystemUiProxy.INSTANCE[appContext].focusState.removeListener(focusListener)
    }

    @VisibleForTesting
    override fun injectTestInsightForCueBar() {
        // In test, the listeners are not registered upon start up.
        CueBarInsightRendererService.registerListener(this)
        SystemUiProxy.INSTANCE[appContext].focusState.addListener(focusListener)
        val testTitle = "Test Title"
        val testSubtitle = "Test Subtitle"
        _isTestMode.dispatchValue(true)
        val displayDetails =
            InsightDisplayDetails.Builder(testTitle).setSubtitle(testSubtitle).build()
        val mockInsightBuilder = DisplayInsight.Builder(displayDetails)

        val bundle = Bundle().apply { putBoolean(RENDER_IN_CUE_BAR, true) }
        val hint = BundleHint.Builder().setDataBundle(bundle).build()

        val signedHint =
            PublishedContextHint.Builder(hint, SecretKeySpec(ByteArray(16), "HmacSHA256")).build()
        val mockInsight = mockInsightBuilder.addOriginHint(signedHint).build()
        val publishedInsight = PublishedContextInsight(mockInsight, UUID.randomUUID())
        onInsightReceived(publishedInsight, RenderToken(UUID.randomUUID(), "test_tag"))
    }

    companion object {
        // In-coming intent extras from the intelligent service.
        @VisibleForTesting const val EXTRA_ACTIVITY_ID = "activityId"
        @VisibleForTesting const val EXTRA_AUTOFILL_ID = "autofillId"
        @VisibleForTesting
        const val EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT = "attributionDialogPendingIntent"
        @VisibleForTesting const val EXTRA_ACTION_TYPE = "actionType"
        private const val ONE_TAP_HINT_TYPE = "oneTapHint"
        private const val EXTRA_ONE_TAP_ENABLED = "oneTapEnabled"
        private const val DEFAULT_ONE_TAP_DELAY_MS = 200L
        @VisibleForTesting const val EXTRA_ENABLED_WITH_IME_VISIBLE = "enabledWithImeVisible"
        const val RENDER_IN_CUE_BAR = "renderInCueBar"
        const val NEEDS_DATA_EGRESS = "needsDataEgress"

        // Key to identify the specific BundleHint for IME visibility
        @VisibleForTesting const val IME_VISIBILITY_HINT_TYPE = "imeVisibilityHint"

        // Timeout to hide cuebar if it wasn't interacted with
        private const val TAG = "AmbientCueRepository"
        private const val DEBUG = false
        private const val INVALID_TASK_ID = ActivityTaskManager.INVALID_TASK_ID
        @VisibleForTesting const val AMBIENT_CUE_SETTING = "spoonBarOptedIn"
        @VisibleForTesting const val OPTED_IN = 0x10
        const val DEBOUNCE_DELAY_MS = 100L
        private const val AMBIENT_CUE_DEFAULT_TIMEOUT_MS = 30_000
        @VisibleForTesting const val MA_ACTION_TYPE_NAME = "ma"
        @VisibleForTesting const val MR_ACTION_TYPE_NAME = "mr"

        private const val ATTRIBUTION_INTENT_HINT_TYPE = "attributionIntentHint"
    }
}

private class AmbientCueFocusListener(
    private val repositoryRef: WeakReference<AmbientCueRepositoryImpl>,
    private val bgExecutor: Executor,
) : FocusState.FocusChangeListener {

    override fun onFocusedTaskChanged(focusedTaskInfo: RunningTaskInfo) {
        val repository = repositoryRef.get() ?: return
        // Defer to background executor to handle any non-UI work since TaskStackChangeListener
        // can be called on a Binder thread. This then dispatches to the UI executor inside the
        // repository.
        bgExecutor.execute { repository.onTaskMovedToFront(focusedTaskInfo) }
    }
}