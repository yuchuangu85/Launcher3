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

package com.android.launcher3.taskbar.edu

import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.text.Spannable
import android.text.style.URLSpan
import android.view.View
import androidx.core.net.toUri
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.asProperty
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_BUBBLES_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_PINNING_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_SEARCH_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_SPLIT_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipEduCombinator.Companion.TASKBAR_SWIPE_EDU_SEEN_FLAG
import com.android.launcher3.taskbar.edu.TooltipsEduPage.DisplayLocation
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.THREE_BUTTONS
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.OnboardingPrefs
import com.android.systemui.shared.Flags.FLAG_ENABLE_RECENTS_IN_TASKBAR
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_ENABLE_RECENTS_IN_TASKBAR)
class TooltipEduCombinatorTest {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    @get:Rule(order = 1) val context = TaskbarWindowSandboxContext.create()

    @get:Rule(order = 2) val taskbarModeRule = TaskbarModeRule(context)

    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(context)

    private val taskbarStashController by taskbarUnitTestRule.delegate { it.taskbarStashController }

    private lateinit var tooltipEduCombinator: TooltipEduCombinator

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    private val wasInTestHarness = Utilities.isRunningInTestHarness()

    private var sysuiLocked = false

    private var tooltipStep by OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP.prefItem.asProperty(context)
    private var taskbarSeenEduFlags by OnboardingPrefs.TASKBAR_SEEN_EDU_FLAGS.asProperty(context)

    private var swipeEduSeen: Boolean
        get() = getFlag(TASKBAR_SWIPE_EDU_SEEN_FLAG)
        set(value) = setFlag(TASKBAR_SWIPE_EDU_SEEN_FLAG, value)

    private var splitEduSeen: Boolean
        get() = getFlag(TASKBAR_SPLIT_EDU_SEEN_FLAG)
        set(value) = setFlag(TASKBAR_SPLIT_EDU_SEEN_FLAG, value)

    private var bubblesEduSeen: Boolean
        get() = getFlag(TASKBAR_BUBBLES_EDU_SEEN_FLAG)
        set(value) = setFlag(TASKBAR_BUBBLES_EDU_SEEN_FLAG, value)

    private var suggestionsEduSeen: Boolean
        get() = getFlag(TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG)
        set(value) = setFlag(TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG, value)

    private var pinningEduSeen: Boolean
        get() = getFlag(TASKBAR_PINNING_EDU_SEEN_FLAG)
        set(value) = setFlag(TASKBAR_PINNING_EDU_SEEN_FLAG, value)

    private var searchEduSeen: Boolean
        get() = getFlag(TASKBAR_SEARCH_EDU_SEEN_FLAG)
        set(value) = setFlag(TASKBAR_SEARCH_EDU_SEEN_FLAG, value)

    @Before
    fun setUp() {
        // TODO(b/496164737): Remove when we enable EDU tooltips on desktop.
        assume().that(taskbarContext.isDesktopFormFactor).isFalse()

        tooltipEduCombinator =
            TooltipEduCombinator(taskbarContext, taskbarStashController, { sysuiLocked }, { true })
        Utilities.disableRunningInTestHarnessForTests()
    }

    @After
    fun tearDown() {
        if (wasInTestHarness) {
            Utilities.enableRunningInTestHarnessForTests()
        }
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetSwipeEdu_whenTaskbarIsTransientNotSeenBefore_returnSwipeEdu() {
        val eduPage = tooltipEduCombinator.getSwipeEdu()!!
        checkEduPage(
            eduPage = eduPage,
            titleResId = R.string.taskbar_edu_stashing,
            canBeSkipped = true,
            tooltipCheckers =
                listOf { tooltipInfo ->
                    checkTooltip(
                        tooltipInfo = tooltipInfo,
                        animationResId = R.raw.taskbar_edu_stashing,
                        animationDescriptionResId = R.string.taskbar_edu_swipe_animation_description,
                    )
                },
            location = DisplayLocation.TASKBAR_HANDLE,
        )
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetSwipeEdu_whenSysuiLocked_returnNull() {
        sysuiLocked = true
        assertThat(tooltipEduCombinator.getSwipeEdu()).isNull()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testGetSwipeEdu_whenTaskbarIsInThreeButton_returnsNull() {
        assertThat(tooltipEduCombinator.getSwipeEdu()).isNull()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testGetSwipeEdu_whenTaskbarIsTransientSeenBefore_returnsNull() {
        swipeEduSeen = true
        assertThat(tooltipEduCombinator.getSwipeEdu()).isNull()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun getFeaturesTooltipsEduPages_whenTransientMode_andAllTooltipsPresent_returnsCorrectPaginatedPages() {
        assertThat(swipeEduSeen).isFalse()
        assertThat(splitEduSeen).isFalse()
        assertThat(bubblesEduSeen).isFalse()
        assertThat(suggestionsEduSeen).isFalse()
        assertThat(pinningEduSeen).isFalse()
        val eduPages = tooltipEduCombinator.getFeaturesTooltipsEduPages()!!

        // Should be split into 2 pages as there are 4 tooltips in total.
        assertThat(eduPages).hasSize(2)

        // Check first page.
        checkEduPage(
            eduPage = eduPages[0],
            titleResId = R.string.taskbar_edu_features,
            canBeSkipped = false,
            actionButtonTextResId = R.string.taskbar_edu_next,
            tooltipCheckers =
                listOf(
                    { checkIfSplitScreenTooltip(it) },
                    { checkIfBubbleTooltip(it) },
                    { checkIfSuggestionsTooltip(it) },
                ),
            location = DisplayLocation.TASKBAR_CENTER,
        )
        // Check second page.
        checkEduPage(
            eduPage = eduPages[1],
            titleResId = R.string.taskbar_edu_pinning_title,
            canBeSkipped = true,
            actionButtonTextResId = null,
            tooltipCheckers = listOf { checkIfPinningTooltip(it, isStandAlone = true) },
            location = DisplayLocation.SEARCH_DIVIDER,
        )
        // Check that the seen flags are updated.
        assertThat(swipeEduSeen).isTrue()
        assertThat(splitEduSeen).isTrue()
        assertThat(bubblesEduSeen).isTrue()
        assertThat(suggestionsEduSeen).isTrue()
        assertThat(pinningEduSeen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun getFeaturesTooltipsEduPages_whenTransientMode_oldToolTipsSeen_returnsCorrectPaginatedPages() {
        setShownOldEdu()
        val eduPages = tooltipEduCombinator.getFeaturesTooltipsEduPages()!!

        assertThat(eduPages).hasSize(1)

        // Check first page.
        checkEduPage(
            eduPage = eduPages[0],
            titleResId = R.string.taskbar_edu_features,
            canBeSkipped = false,
            actionButtonTextResId = R.string.taskbar_edu_done,
            tooltipCheckers =
                listOf(
                    { checkIfBubbleTooltip(it) },
                    { checkIfSuggestionsTooltip(it) },
                    { checkIfPinningTooltip(it, isStandAlone = false) },
                ),
            location = DisplayLocation.TASKBAR_CENTER,
        )
        // Check that the seen flags are updated.
        assertThat(pinningEduSeen).isTrue()
        assertThat(swipeEduSeen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun getFeaturesTooltipsEduPages_whenTransientMode_createAnyBubbleDisabled_returnsCorrectPaginatedPages() {
        tooltipEduCombinator.createAnyBubbleEnabled = false
        val eduPages = tooltipEduCombinator.getFeaturesTooltipsEduPages()!!

        assertThat(eduPages).hasSize(1)

        // Check first page.
        checkEduPage(
            eduPage = eduPages[0],
            titleResId = R.string.taskbar_edu_features,
            canBeSkipped = false,
            actionButtonTextResId = R.string.taskbar_edu_done,
            tooltipCheckers =
                listOf(
                    { checkIfSplitScreenTooltip(it) },
                    { checkIfSuggestionsTooltip(it) },
                    { checkIfPinningTooltip(it, isStandAlone = false) },
                ),
            location = DisplayLocation.TASKBAR_CENTER,
        )
        // Check that the seen flags are updated.
        assertThat(pinningEduSeen).isTrue()
        assertThat(swipeEduSeen).isTrue()
    }

    @Test
    fun getFeaturesTooltipsEduPages_whenTooltipsDisabled_returnsNull() {
        Utilities.enableRunningInTestHarnessForTests()
        assertThat(tooltipEduCombinator.getFeaturesTooltipsEduPages()).isNull()
    }

    @Test
    fun getFeaturesTooltipsEduPages_whenSysuiLocked_returnsNull() {
        sysuiLocked = true
        assertThat(tooltipEduCombinator.getFeaturesTooltipsEduPages()).isNull()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun getFeaturesTooltipsEduPages_allFeaturesExceptPinningEduSeenBefore_returnsPinningEdu() {
        tooltipStep = 1
        bubblesEduSeen = true
        suggestionsEduSeen = true

        val eduPages = tooltipEduCombinator.getFeaturesTooltipsEduPages()!!

        assertThat(eduPages).hasSize(1)
        checkEduPage(
            eduPage = eduPages.first(),
            titleResId = R.string.taskbar_edu_pinning_title,
            canBeSkipped = true,
            tooltipCheckers = listOf { checkIfPinningTooltip(it, isStandAlone = true) },
            location = DisplayLocation.SEARCH_DIVIDER,
        )
        assertThat(pinningEduSeen).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun getFeaturesTooltipsEduPages_whenOldEduPinningSeenBefore_returnsCorrectPaginatedPages() {
        tooltipStep = 2

        val eduPages = tooltipEduCombinator.getFeaturesTooltipsEduPages()!!

        assertThat(eduPages).hasSize(1)
        checkEduPage(
            eduPage = eduPages.first(),
            titleResId = R.string.taskbar_edu_features,
            actionButtonTextResId = R.string.taskbar_edu_done,
            canBeSkipped = false,
            tooltipCheckers =
                listOf(
                    { checkIfBubbleTooltip(it, isStandAlone = false) },
                    { checkIfSuggestionsTooltip(it) },
                ),
            location = DisplayLocation.TASKBAR_CENTER,
        )
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun getFeaturesTooltipsEduPages_whenPersistentMode_returnsSinglePage() {
        val eduPages = tooltipEduCombinator.getFeaturesTooltipsEduPages()!!

        assertThat(eduPages).hasSize(1)
        val page = eduPages.first()

        // Pinning tooltip should not be shown in persistent mode.
        checkEduPage(
            eduPage = page,
            titleResId = R.string.taskbar_edu_features,
            canBeSkipped = false,
            actionButtonTextResId = R.string.taskbar_edu_done,
            tooltipCheckers =
                listOf(
                    { checkIfSplitScreenTooltip(it, isTransient = false) },
                    { checkIfBubbleTooltip(it, isTransient = false) },
                    { checkIfSuggestionsTooltip(it, isTransient = false) },
                ),
            location = DisplayLocation.TASKBAR_CENTER,
        )
    }

    @Test
    @TaskbarMode(PINNED)
    fun getSearchEdu_whenPinnedTaskbar_returnsSearchEdu() {
        val searchEdu = tooltipEduCombinator.getSearchEdu()
        checkEduPage(
            eduPage = searchEdu!!,
            titleResId = R.string.taskbar_search_edu_title,
            canBeSkipped = true,
            tooltipCheckers = listOf { checkIfSearchTooltip(it) },
            location = DisplayLocation.SEARCH_ICON,
        )
        assertThat(searchEduSeen).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun getSearchEdu_whenSysuiLocked_returnsNull() {
        sysuiLocked = true
        assertThat(tooltipEduCombinator.getSearchEdu()).isNull()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun getSearchEdu_whenTransientTaskbar_returnsNull() {
        assertThat(tooltipEduCombinator.getSearchEdu()).isNull()
    }

    @Test
    @TaskbarMode(PINNED)
    fun getSearchEdu_whenPinnedTaskbarSearchEduSeen_returnsNull() {
        searchEduSeen = true
        assertThat(tooltipEduCombinator.getSearchEdu()).isNull()
    }

    @Test
    @TaskbarMode(PINNED)
    fun getSearchEdu_whenPinnedTaskbarShouldNotShowSearchEdu_returnsNull() {
        tooltipEduCombinator =
            TooltipEduCombinator(taskbarContext, taskbarStashController, { sysuiLocked }, { false })
        assertThat(tooltipEduCombinator.getSearchEdu()).isNull()
    }

    @Test
    @TaskbarMode(PINNED)
    fun getSearchEdu_returnsEduWithClickableDisclosureLinks() {
        // Initialize Espresso Intents to capture intents sent from the context.
        Intents.init()
        try {
            // GIVEN a search EDU is available
            val searchEdu = tooltipEduCombinator.getSearchEdu()!!
            val searchTooltip = searchEdu.tooltips.first()
            val disclosureText = searchTooltip.message

            // THEN the disclosure text should be a Spannable containing URL links
            assertThat(disclosureText).isInstanceOf(Spannable::class.java)
            val spannable = disclosureText as Spannable
            val urlSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
            assertThat(urlSpans).isNotEmpty()

            // WHEN the first link in the disclosure text is clicked
            urlSpans.first().onClick(View(taskbarContext))

            // THEN an Intent to view the URL should be sent
            Intents.intended(
                allOf(
                    hasAction(Intent.ACTION_VIEW),
                    hasData(urlSpans.first().url.toUri())
                )
            )
        } finally {
            // Clean up and release Espresso Intents.
            Intents.release()
        }
    }

    private fun checkEduPage(
        eduPage: TooltipsEduPage,
        titleResId: Int,
        canBeSkipped: Boolean,
        actionButtonTextResId: Int? = null,
        tooltipCheckers: List<(TooltipInfo) -> Unit>,
        location: DisplayLocation,
    ) {
        assertThat(eduPage.title).isEqualTo(context.getString(titleResId))
        assertThat(eduPage.canBeSkipped).isEqualTo(canBeSkipped)
        actionButtonTextResId?.let {
            assertThat(eduPage.actionButton).isEqualTo(context.getString(it))
        }
        assertThat(eduPage.location).isEqualTo(location)
        assertThat(tooltipCheckers.size).isEqualTo(eduPage.tooltips.size)
        tooltipCheckers.zip(eduPage.tooltips).forEach { (checker, tooltip) -> checker(tooltip) }
    }

    private fun checkIfSearchTooltip(tooltipInfo: TooltipInfo) =
        checkTooltip(
            tooltipInfo,
            animationResId = R.raw.taskbar_edu_search,
            animationDescriptionResId = R.string.taskbar_edu_suggested_search_animation_description,
        )

    private fun checkIfSplitScreenTooltip(tooltipInfo: TooltipInfo, isTransient: Boolean = true) =
        checkTooltip(
            tooltipInfo = tooltipInfo,
            messageResId = R.string.taskbar_edu_splitscreen,
            animationResId =
                if (isTransient) {
                    R.raw.taskbar_edu_splitscreen_transient
                } else {
                    R.raw.taskbar_edu_splitscreen_persistent
                },
            animationDescriptionResId = R.string.taskbar_edu_split_screen_animation_description,
        )

    private fun checkIfBubbleTooltip(
        tooltipInfo: TooltipInfo,
        isTransient: Boolean = true,
        isStandAlone: Boolean = false,
    ) =
        checkTooltip(
            tooltipInfo = tooltipInfo,
            messageResId =
                if (isStandAlone) {
                    R.string.taskbar_edu_bubbles_standalone
                } else {
                    R.string.taskbar_edu_bubbles
                },
            animationResId =
                if (isTransient) {
                    R.raw.taskbar_edu_bubbles_transient
                } else {
                    R.raw.taskbar_edu_bubbles_persistent
                },
            animationDescriptionResId = R.string.taskbar_edu_bubbles_animation_description,
        )

    private fun checkIfSuggestionsTooltip(tooltipInfo: TooltipInfo, isTransient: Boolean = true) =
        checkTooltip(
            tooltipInfo = tooltipInfo,
            messageResId = R.string.taskbar_edu_suggestions,
            animationResId =
                if (isTransient) {
                    R.raw.taskbar_edu_suggestions_transient
                } else {
                    R.raw.taskbar_edu_suggestions_persistent
                },
            animationDescriptionResId = R.string.taskbar_edu_suggested_app_animation_description,
        )

    private fun checkIfPinningTooltip(tooltipInfo: TooltipInfo, isStandAlone: Boolean = false) =
        checkTooltip(
            tooltipInfo = tooltipInfo,
            messageResId =
                if (isStandAlone) {
                    R.string.taskbar_edu_pinning_standalone
                } else {
                    R.string.taskbar_edu_pinning
                },
            animationResId = R.raw.taskbar_edu_pinning_transient,
            animationDescriptionResId = R.string.taskbar_edu_pinning_animation_description,
        )

    private fun checkTooltip(
        tooltipInfo: TooltipInfo,
        messageResId: Int? = null,
        animationResId: Int,
        animationDescriptionResId: Int,
    ) {
        messageResId?.let { assertThat(tooltipInfo.message).isEqualTo(context.getString(it)) }
        assertThat(tooltipInfo.animationResId).isEqualTo(animationResId)
        assertThat(tooltipInfo.animationDescription)
            .isEqualTo(context.getString(animationDescriptionResId))
    }

    private fun getFlag(flag: Int): Boolean {
        return taskbarSeenEduFlags and flag == flag
    }

    private fun setFlag(flag: Int, seen: Boolean) {
        taskbarSeenEduFlags =
            if (seen) {
                taskbarSeenEduFlags or flag
            } else {
                taskbarSeenEduFlags and flag.inv()
            }
    }

    private fun setShownOldEdu() {
        tooltipStep = 1
    }
}
