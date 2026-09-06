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

package com.android.launcher3.taskbar

import android.app.PendingIntent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.service.personalcontext.hint.BundleHint
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationUpdateEvent
import android.content.ComponentName
import android.service.personalcontext.hint.ContentCaptureConversationHint
import android.service.personalcontext.hint.ConversationData
import java.time.Instant
import android.service.personalcontext.hint.AutofillInlineRequestHint
import android.service.personalcontext.hint.ContextHint
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.ActionableInsight
import android.service.personalcontext.hint.HintInvalidationHint
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.HintInvalidationInsight
import android.service.personalcontext.insight.InsightActionDetails
import android.service.personalcontext.insight.InsightCollection
import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.RenderToken
import android.service.personalcontext.insight.InsightDisplayDetails
import android.service.personalcontext.insight.PublishedContextInsight
import android.service.personalcontext.insight.interaction.InsightEvent
import android.view.autofill.AutofillManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.quickstep.cuebar.data.ActionModel
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl.Companion.EXTRA_ENABLED_WITH_IME_VISIBLE
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl.Companion.IME_VISIBILITY_HINT_TYPE
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl.Companion.MA_ACTION_TYPE_NAME
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl.Companion.MR_ACTION_TYPE_NAME
import com.android.quickstep.cuebar.data.repository.AmbientCueRepositoryImpl.Companion.RENDER_IN_CUE_BAR
import com.android.quickstep.cuebar.logger.AmbientCueAceLogger
import com.android.quickstep.cuebar.logger.AmbientCueLogger
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class AmbientCueRepositoryTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val taskbarActivityContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    @Mock private lateinit var mockTaskbarContext: TaskbarActivityContext
    @Mock private lateinit var mockAmbientCueLogger: AmbientCueLogger
    private lateinit var ambientCueAceLogger: AmbientCueAceLogger
    @Mock private lateinit var mockBgExecutor: Executor
    @Mock private lateinit var mockUiExecutor: Executor
    @Mock private lateinit var mockAutofillManager: AutofillManager
    @Mock private lateinit var mockPersonalContextManager: PersonalContextManager
    @Mock private lateinit var mockDrawable: Drawable
    @Mock private lateinit var mockMutatedDrawable: Drawable

    private lateinit var repositoryImpl: AmbientCueRepositoryImpl
    private lateinit var repository: AmbientCueRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        `when`(mockTaskbarContext.getSystemService(AutofillManager::class.java))
            .thenReturn(mockAutofillManager)
        `when`(mockTaskbarContext.getSystemService(PersonalContextManager::class.java))
            .thenReturn(mockPersonalContextManager)
        `when`(mockTaskbarContext.applicationContext).thenReturn(context)
        ambientCueAceLogger = AmbientCueAceLogger(mockPersonalContextManager)
        repositoryImpl =
            AmbientCueRepositoryImpl(
                mockTaskbarContext,
                mockAmbientCueLogger,
                ambientCueAceLogger,
                mockBgExecutor,
                mockUiExecutor,
            )
        `when`(mockDrawable.mutate()).thenReturn(mockMutatedDrawable)
        `when`(mockTaskbarContext.getDrawable(anyInt())).thenReturn(mockDrawable)
        `when`(mockTaskbarContext.resources)
            .thenReturn(mock(android.content.res.Resources::class.java))
        doNothing().`when`(mockTaskbarContext).startActivity(any())
        repository = spy(repositoryImpl)
        doAnswer { invocation ->
                (invocation.getArgument(0) as Runnable).run()
                null
            }
            .`when`(mockBgExecutor)
            .execute(any())
        doAnswer { invocation ->
                (invocation.getArgument(0) as Runnable).run()
                null
            }
            .`when`(mockUiExecutor)
            .execute(any())
    }

    /** Mocks a ContextInsight with the given ContextHints. */
    private fun mockInsight(vararg hints: ContextHint): ContextInsight {
        val insight = mock(ActionableInsight::class.java)
        val originHintList =
            hints.map { contextHint ->
                mock(PublishedContextHint::class.java).apply {
                    `when`(this.contextHint).thenReturn(contextHint)
                }
            }
        `when`(insight.originHints).thenReturn(originHintList.toSet())
        return insight
    }

    /** Mocks the common display details for an insight. */
    private fun mockInsightDisplayDetails(): InsightDisplayDetails {
        val displayDetails = mock(InsightDisplayDetails::class.java)
        val mockIcon = mock(android.graphics.drawable.Icon::class.java)
        `when`(displayDetails.icon).thenReturn(mockIcon)

        val titleSequence = mock(CharSequence::class.java)
        `when`(titleSequence.toString()).thenReturn(TITLE)
        `when`(displayDetails.title).thenReturn(titleSequence)

        val subtitleSequence = mock(CharSequence::class.java)
        `when`(subtitleSequence.toString()).thenReturn(SUBTITLE)
        `when`(displayDetails.subtitle).thenReturn(subtitleSequence)

        return displayDetails
    }

    private fun mockActionableInsight(): ActionableInsight {
        val insight = mock(ActionableInsight::class.java)
        val displayDetails = mockInsightDisplayDetails()
        `when`(insight.displayDetails).thenReturn(displayDetails)
        val actionDetails = mock(InsightActionDetails::class.java)
        `when`(actionDetails.hasActionType(InsightActionDetails.ACTION_TYPE_REMOTE_ACTION))
            .thenReturn(true)
        val pendingIntent = mock(PendingIntent::class.java)
        `when`(actionDetails.pendingIntent).thenReturn(pendingIntent)
        val remoteAction = mock(android.app.RemoteAction::class.java)
        val remoteActionIntent = mock(android.app.PendingIntent::class.java)
        `when`(actionDetails.remoteAction).thenReturn(remoteAction)
        `when`(remoteAction.actionIntent).thenReturn(remoteActionIntent)
        `when`(insight.actionDetails).thenReturn(actionDetails)
        return insight
    }

    private fun mockDisplayInsight(): DisplayInsight {
        val insight = mock(DisplayInsight::class.java)
        val displayDetails = mockInsightDisplayDetails()
        `when`(insight.details).thenReturn(displayDetails)
        return insight
    }

    /** Asserts the result list contains exactly one ActionModel and returns it. */
    private fun getSingleActionModel(
        result: List<ActionModel>,
        expectedActionTypeName: String,
    ): ActionModel {
        assertThat(result).hasSize(1)
        val actionModel = result.first()
        assertThat(actionModel.actionType).isEqualTo(expectedActionTypeName)
        return actionModel
    }

    private fun createBundleHint(renderInCueBar: Boolean): BundleHint {
        val bundle = Bundle().apply { putBoolean(RENDER_IN_CUE_BAR, renderInCueBar) }
        return mock(BundleHint::class.java).apply { `when`(dataBundle).thenReturn(bundle) }
    }

    private fun createImeVisibilityHint(enabledWithImeVisible: Boolean): BundleHint {
        val bundle =
            Bundle().apply { putBoolean(EXTRA_ENABLED_WITH_IME_VISIBLE, enabledWithImeVisible) }
        return mock(BundleHint::class.java).apply {
            `when`(dataBundle).thenReturn(bundle)
            `when`(hintTypeName).thenReturn(IME_VISIBILITY_HINT_TYPE)
        }
    }

    @Test
    fun mapInsightToActions_bundleHint_callsMapContextInsightToAction() {
        val bundleHint = createBundleHint(true)
        val insight = mockInsight(bundleHint)
        doReturn(listOf(mock(ActionModel::class.java)))
            .`when`(repository)
            .mapContextInsightToAction(any(), any())

        val result = repository.mapInsightToActions(insight)

        verify(repository).mapContextInsightToAction(insight, bundleHint)
        assertThat(result).isNotEmpty()
    }

    @Test
    fun mapInsightToActions_conversationHint_callsMapContextInsightToAction() {
        val conversationHint = createConversationHint()
        val insight = mockInsight(conversationHint)
        doReturn(listOf(mock(ActionModel::class.java)))
            .`when`(repository)
            .mapContextInsightToAction(any(), any())

        val result = repository.mapInsightToActions(insight)

        verify(repository).mapContextInsightToAction(insight, conversationHint)
        assertThat(result).isNotEmpty()
    }

    @Test
    fun mapContextInsightToAction_actionableInsight_remoteAction_createsMAModel() {
        val insight = mockActionableInsight()
        val publishedInsight = mock(PublishedContextInsight::class.java)
        `when`(publishedInsight.insight).thenReturn(insight)
        val renderToken = mock(RenderToken::class.java)
        val conversationHint = createConversationHint()

        // Populate last received insight/token
        repository.onInsightReceived(publishedInsight, renderToken)

        // Since onInsightReceived is async on uiExecutor, verify actions updated or access directly if tested method doesn't rely on actions list but on internal mapping.
        // But mapContextInsightToAction is tested here directly.
        // We need to verify side effects of onPerformAction which USES lastPublishedInsight.

        val result = repository.mapContextInsightToAction(insight, conversationHint)
        val actionModel = getSingleActionModel(result, MA_ACTION_TYPE_NAME)

        actionModel.onPerformAction.invoke()

        verify(mockPersonalContextManager).reportInsightEvent(publishedInsight, InsightEvent.EVENT_USER_TAP, renderToken)
        verify(insight.actionDetails.remoteAction?.actionIntent)?.send(any<Bundle>())
        verify(mockAmbientCueLogger).setFulfilledWithMaStatus()
        verify(mockAmbientCueLogger, never()).setFulfilledWithMrStatus()
    }

    @Test
    fun mapContextInsightToAction_displayInsight_conversationHint_createsMRModel() {
        val insight = mockDisplayInsight()
        val publishedInsight = mock(PublishedContextInsight::class.java)
        `when`(publishedInsight.insight).thenReturn(insight)
        val renderToken = mock(RenderToken::class.java)
        val conversationHint = createConversationHint()

        repository.onInsightReceived(publishedInsight, renderToken)

        val result = repository.mapContextInsightToAction(insight, conversationHint)
        val actionModel = getSingleActionModel(result, MR_ACTION_TYPE_NAME)

        actionModel.onPerformAction.invoke()

        verify(mockPersonalContextManager).reportInsightEvent(publishedInsight, InsightEvent.EVENT_USER_TAP, renderToken)
        verify(mockAmbientCueLogger).setFulfilledWithMrStatus()
        verify(mockAmbientCueLogger, never()).setFulfilledWithMaStatus()
    }

    @Test
    fun onPerformLongClick_reportsEvent() {
        val insight = mockActionableInsight()
        val publishedInsight = mock(PublishedContextInsight::class.java)
        `when`(publishedInsight.insight).thenReturn(insight)
        val renderToken = mock(RenderToken::class.java)
        val conversationHint = createConversationHint()

        repository.onInsightReceived(publishedInsight, renderToken)
        val result = repository.mapContextInsightToAction(insight, conversationHint)
        val actionModel = result.first()

        actionModel.onPerformLongClick.invoke()

        verify(mockPersonalContextManager).reportInsightEvent(publishedInsight, InsightEvent.EVENT_USER_LONG_PRESS, renderToken)
    }

    @Test
    fun reportCloseEvent_reportsEventToPersonalContextManager() {
        val insight = mockActionableInsight()
        val publishedInsight = mock(PublishedContextInsight::class.java)
        `when`(publishedInsight.insight).thenReturn(insight)
        val renderToken = mock(RenderToken::class.java)

        repository.onInsightReceived(publishedInsight, renderToken)
        repository.reportCloseEvent()

        verify(mockPersonalContextManager)
            .reportInsightEvent(publishedInsight, InsightEvent.EVENT_USER_DISMISS, renderToken)
    }

    @Test
    fun mapInsightToActions_noRenderableHint_returnsEmptyList() {
        val irrelevantHint = mock(ContextHint::class.java)
        val insight = mockInsight(irrelevantHint)
        doReturn(emptyList<ActionModel>())
            .`when`(repository)
            .mapContextInsightToAction(any(), any())

        val result = repository.mapInsightToActions(insight)

        verify(repository, never()).mapContextInsightToAction(any(), any())
        assertThat(result).isEmpty()
    }

    @Test
    fun mapContextInsightToAction_nestedInsightCollection_flattensAndMapsChildren() {
        val actionableInsight = mockActionableInsight()
        val displayInsight = mockDisplayInsight()
        val nestedCollection = InsightCollection.Builder().addInsight(displayInsight).build()
        val rootCollection =
            InsightCollection.Builder()
                .addInsight(actionableInsight)
                .addInsight(nestedCollection)
                .build()
        val conversationHint = createConversationHint()

        val result = repository.mapContextInsightToAction(rootCollection, conversationHint)

        assertThat(result).hasSize(2)
        assertThat(result[0].actionType).isEqualTo(MA_ACTION_TYPE_NAME)
        assertThat(result[1].actionType).isEqualTo(MR_ACTION_TYPE_NAME)
    }

    @Test
    fun mapContextInsightToAction_flagSetToTrue_setsIsEnabledWithImeVisibleTrue() {
        val renderHint = createBundleHint(true)
        val imeHint = createImeVisibilityHint(true)
        val insightWithIme = mockActionableInsight()
        val renderHintWithSignature =
            mock(PublishedContextHint::class.java).apply {
                `when`(contextHint).thenReturn(renderHint)
            }
        val imeHintWithSignature =
            mock(PublishedContextHint::class.java).apply { `when`(contextHint).thenReturn(imeHint) }
        `when`(insightWithIme.originHints)
            .thenReturn(setOf(renderHintWithSignature, imeHintWithSignature))

        val resultWithIme = repository.mapInsightToActions(insightWithIme)

        assertThat(resultWithIme).hasSize(1)
        assertThat(resultWithIme[0].isEnabledWithImeVisible).isTrue()
    }

    @Test
    fun mapContextInsightToAction_flagSetToFalse_setsIsEnabledWithImeVisibleFalse() {
        val renderHint = createBundleHint(true)
        val imeHint = createImeVisibilityHint(false)
        val insightWithoutIme = mockActionableInsight()
        val renderHintWithSignature =
            mock(PublishedContextHint::class.java).apply {
                `when`(contextHint).thenReturn(renderHint)
            }
        val imeHintWithSignature =
            mock(PublishedContextHint::class.java).apply { `when`(contextHint).thenReturn(imeHint) }
        `when`(insightWithoutIme.originHints)
            .thenReturn(setOf(renderHintWithSignature, imeHintWithSignature))

        val resultWithoutIme = repository.mapInsightToActions(insightWithoutIme)

        assertThat(resultWithoutIme).hasSize(1)
        assertThat(resultWithoutIme[0].isEnabledWithImeVisible).isFalse()
    }

    @Test
    fun mapContextInsightToAction_noFlag_setsIsEnabledWithImeVisibleFalse() {
        val renderHint = createBundleHint(true)
        // No IME visibility hint provided
        val insightDefaultIme = mockActionableInsight()
        val renderHintWithSignature =
            mock(PublishedContextHint::class.java).apply {
                `when`(contextHint).thenReturn(renderHint)
            }
        `when`(insightDefaultIme.originHints).thenReturn(setOf(renderHintWithSignature))

        val resultDefaultIme = repository.mapInsightToActions(insightDefaultIme)

        assertThat(resultDefaultIme).hasSize(1)
        assertThat(resultDefaultIme[0].isEnabledWithImeVisible).isFalse()
    }

    @Test
    fun onInsightReceived_withAutofillInlineRequestHint_ignoresInsight() {
        val autofillHint = mock(AutofillInlineRequestHint::class.java)
        val validHint = createBundleHint(true)
        val insight = mockInsight(autofillHint, validHint)
        val publishedInsight = mock(PublishedContextInsight::class.java)
        `when`(publishedInsight.insight).thenReturn(insight)
        val renderToken = mock(RenderToken::class.java)

        repository.onInsightReceived(publishedInsight, renderToken)

        verify(repository, never()).mapInsightToActions(any())
        verify(repository, never()).updateActions(any())
    }

    @Test
    fun onInsightReceived_withMatchingHintInvalidationInsight_clearsActions() {
        val conversationHint = createConversationHint()
        val insight = mockActionableInsight()
        val publishedInsight = mock(PublishedContextInsight::class.java)
        val renderHintWithSignature = mock(PublishedContextHint::class.java).apply {
            `when`(contextHint).thenReturn(conversationHint)
        }
        `when`(insight.originHints).thenReturn(setOf(renderHintWithSignature))
        `when`(publishedInsight.insight).thenReturn(insight)
        val renderToken = mock(RenderToken::class.java)

        repository.onInsightReceived(publishedInsight, renderToken)

        // Verify actions are updated with the actionable insight
        assertThat(repository.actions.value).isNotEmpty()

        // Create a HintInvalidationInsight with the matching UUID
        val invalidationHint = mock(HintInvalidationHint::class.java)
        `when`(invalidationHint.invalidatedHintId).thenReturn(conversationHint.hintId)
        val publishedInvalidationHint =
            PublishedContextHint.Builder(
                    invalidationHint,
                    SecretKeySpec(ByteArray(16), "HmacSHA256"),
                )
                .setOriginatingPackage("android")
                .build()
        val invalidationInsight = HintInvalidationInsight.Builder(publishedInvalidationHint).build()
        val publishedInvalidationInsight = mock(PublishedContextInsight::class.java)
        `when`(publishedInvalidationInsight.insight).thenReturn(invalidationInsight)

        repository.onInsightReceived(publishedInvalidationInsight, renderToken)

        assertThat(repository.actions.value).isEmpty()
    }

    @Test
    fun onInsightReceived_withMismatchingHintInvalidationInsight_doesNotClearActions() {
        val conversationHint = createConversationHint()
        val insight = mockActionableInsight()
        val publishedInsight = mock(PublishedContextInsight::class.java)
        val renderHintWithSignature = mock(PublishedContextHint::class.java).apply {
            `when`(contextHint).thenReturn(conversationHint)
        }
        `when`(insight.originHints).thenReturn(setOf(renderHintWithSignature))
        `when`(publishedInsight.insight).thenReturn(insight)
        val renderToken = mock(RenderToken::class.java)

        repository.onInsightReceived(publishedInsight, renderToken)

        // Verify actions are updated with the actionable insight
        assertThat(repository.actions.value).isNotEmpty()

        // Create a HintInvalidationInsight with a mismatching UUID
        val invalidationHint = mock(HintInvalidationHint::class.java)
        `when`(invalidationHint.invalidatedHintId).thenReturn(UUID.randomUUID())
        val publishedInvalidationHint =
            PublishedContextHint.Builder(
                    invalidationHint,
                    SecretKeySpec(ByteArray(16), "HmacSHA256"),
                )
                .setOriginatingPackage("android")
                .build()
        val invalidationInsight = HintInvalidationInsight.Builder(publishedInvalidationHint).build()
        val publishedInvalidationInsight = mock(PublishedContextInsight::class.java)
        `when`(publishedInvalidationInsight.insight).thenReturn(invalidationInsight)

        repository.onInsightReceived(publishedInvalidationInsight, renderToken)

        // Verify actions are NOT cleared
        assertThat(repository.actions.value).isNotEmpty()
    }

    private fun createConversationHint(): ContentCaptureConversationHint {
        val conversationData = ConversationData.Builder()
            .setKeyboardShown(false)
            .setLastMessageFromTheUser(false)
            .setHasNewMessage(false)
            .setProcessingStartTimestamp(Instant.now())
            .setProcessingEndTimestamp(Instant.now())
            .setComponentName(ComponentName("pkg", "cls"))
            .setInputBoxText("")
            .setConversationTitle("")
            .setChatMessages(emptyList())
            .build()
        val updateEvent = ConversationUpdateEvent(
            "sessionId", Instant.now(), Instant.now(), conversationData
        )
        return ContentCaptureConversationHint.Builder(updateEvent).build()
    }

    private companion object {
        const val TITLE = "title"
        const val SUBTITLE = "subtitle"
    }
}
