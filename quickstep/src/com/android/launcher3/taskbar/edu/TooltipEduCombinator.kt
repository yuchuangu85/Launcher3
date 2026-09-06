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
import android.os.UserManager
import android.text.SpannableString
import android.text.style.URLSpan
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.TOOLTIP_STEP_FEATURES
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarStashController
import com.android.launcher3.taskbar.edu.TooltipsEduPage.DisplayLocation
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_SEEN_EDU_FLAGS
import com.android.launcher3.views.ActivityContext
import com.android.systemui.shared.Flags.enableRecentsInTaskbar
import java.io.PrintWriter

/**
 * Class that encapsulates the logic for determining which educational tooltip should be shown to
 * the user. It manages the display conditions and content for various educational tooltips related
 * to the taskbar, such as swipe gestures, features, pinning, and search.
 */
class TooltipEduCombinator(
    activityContext: ActivityContext,
    private val taskbarStashController: TaskbarStashController,
    private val blockedBySysUiState: () -> Boolean,
    private val shouldShowSearchEduResolver: () -> Boolean,
) {

    private val context = activityContext as TaskbarActivityContext

    /** Determines if not a test harnesses, phone mode, and when the taskbar is tiny. */
    private val isTooltipEnabled: Boolean
        get() {
            return !Utilities.isRunningInTestHarness() &&
                !context.isPhoneMode &&
                !context.isTinyTaskbar &&
                !context.isDesktopFormFactor &&
                !blockedBySysUiState() &&
                !UserManager.isDeviceInDemoMode(context)
        }

    /** Indicates whether the user has seen the original educational flow for the taskbar. */
    private val userHasSeenOldEdu: Boolean
        get() = TASKBAR_EDU_TOOLTIP_STEP.get(context) > 0

    /**
     * Indicates whether the user has seen the original pinning educational flow for the taskbar.
     * Use old value of tooltipStep that was set to the previous value of TOOLTIP_STEP_NONE (2 for
     * the original edu steps) as a proxy to needing to show the separate pinning edu
     */
    private val userHasSeenOldPinningEdu: Boolean
        get() = TASKBAR_EDU_TOOLTIP_STEP.get(context) > TOOLTIP_STEP_FEATURES

    /** Indicates whether app bubbles are enabled. */
    @VisibleForTesting var createAnyBubbleEnabled: Boolean = context.areAppBubblesSupported()

    /** Creates the [TooltipInfo] for the split-screen educational tooltip. */
    private val splitTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = context.getString(R.string.taskbar_edu_splitscreen),
                animationResId =
                    if (context.isTransientTaskbar) {
                        R.raw.taskbar_edu_splitscreen_transient
                    } else {
                        R.raw.taskbar_edu_splitscreen_persistent
                    },
                animationDescription =
                    context.getString(R.string.taskbar_edu_split_screen_animation_description),
            )

    /** Creates the [TooltipInfo] for the bubbles educational tooltip. */
    private val bubbleTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = context.getString(R.string.taskbar_edu_bubbles),
                animationResId =
                    if (context.isTransientTaskbar) {
                        R.raw.taskbar_edu_bubbles_transient
                    } else {
                        R.raw.taskbar_edu_bubbles_persistent
                    },
                animationDescription =
                    context.getString(R.string.taskbar_edu_bubbles_animation_description),
            )

    /** Creates the [TooltipInfo] for the bubbles educational tooltip when it is single item. */
    private val bubbleTooltipInfoStandAlone: TooltipInfo
        get() =
            bubbleTooltipInfo.copy(
                message = context.getString(R.string.taskbar_edu_bubbles_standalone)
            )

    /** Creates the [TooltipInfo] for the app suggestions educational tooltip. */
    private val suggestionsTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = context.getString(R.string.taskbar_edu_suggestions),
                animationResId =
                    if (context.isTransientTaskbar) {
                        R.raw.taskbar_edu_suggestions_transient
                    } else {
                        R.raw.taskbar_edu_suggestions_persistent
                    },
                animationDescription =
                    context.getString(R.string.taskbar_edu_suggested_app_animation_description),
            )

    /** Creates the [TooltipInfo] for the taskbar pinning educational tooltip. */
    private val pinningTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = context.getString(R.string.taskbar_edu_pinning),
                animationResId = R.raw.taskbar_edu_pinning_transient,
                animationDescription =
                    context.getString(R.string.taskbar_edu_pinning_animation_description),
            )

    /** Creates the [TooltipInfo] for the pinning educational tooltip when it is single item. */
    private val pinningTooltipInfoStandAlone: TooltipInfo
        get() =
            pinningTooltipInfo.copy(
                message = context.getString(R.string.taskbar_edu_pinning_standalone)
            )

    /** Creates the [TooltipInfo] for the taskbar search educational tooltip. */
    private val searchTooltipInfo: TooltipInfo
        get() =
            TooltipInfo(
                message = getDisclosureText(),
                animationResId = R.raw.taskbar_edu_search,
                animationDescription =
                    context.getString(R.string.taskbar_edu_suggested_search_animation_description),
            )

    /** Returns whether there are educational tooltips to show. */
    fun hasFeaturesEduToShow(): Boolean {
        return collectFeatureTooltipsUpdateFlags(false).isNotEmpty()
    }

    /**
     * Returns the [TooltipsEduPage] page for the swipe gesture to stash the taskbar, or `null` if
     * it should not be shown (e.g., if tooltips are disabled, in phone mode, in tiny taskbar mode,
     * or if the user has already seen this or an older educational flow).
     */
    fun getSwipeEdu(): TooltipsEduPage? {
        if (
            !setFlagIfUnset(
                optionalCondition = {
                    isTooltipEnabled && context.isTransientTaskbar && !userHasSeenOldEdu
                },
                flag = TASKBAR_SWIPE_EDU_SEEN_FLAG,
            )
        ) {
            return null
        }
        return TooltipsEduPage(
            title = context.getString(R.string.taskbar_edu_stashing),
            canBeSkipped = true,
            tooltips =
                listOf(
                    TooltipInfo(
                        animationResId = R.raw.taskbar_edu_stashing,
                        animationDescription =
                            context.getString(R.string.taskbar_edu_swipe_animation_description),
                    )
                ),
            location = DisplayLocation.TASKBAR_HANDLE,
        )
    }

    /**
     * Returns a list of [TooltipsEduPage]s for taskbar features, or `null` if feature education
     * should not be shown (e.g., if tooltips are disabled).
     *
     * This method also handles marking the swipe EDU as seen, as a swipe up is necessary to show
     * these feature tooltips.
     */
    fun getFeaturesTooltipsEduPages(): List<TooltipsEduPage>? {
        if (!isTooltipEnabled) {
            return null
        }
        setFlag(TASKBAR_SWIPE_EDU_SEEN_FLAG)
        val tooltipsToShow = collectFeatureTooltipsUpdateFlags()
        return if (tooltipsToShow.isEmpty()) {
            onFeaturesEduShown()
        } else {
            convertToFeaturesEduPages(tooltipsToShow)
        }
    }

    /**
     * Returns the [TooltipsEduPage] for taskbar search, or `null` if it should not be shown (e.g.,
     * if taskbar pinning is not enabled, not in pinned taskbar mode, etc.).
     */
    fun getSearchEdu(): TooltipsEduPage? {
        if (
            !setFlagIfUnset(
                optionalCondition = {
                    isTooltipEnabled &&
                        context.isPinnedTaskbar &&
                        taskbarStashController.isTaskbarVisibleAndNotStashing &&
                        shouldShowSearchEduResolver.invoke()
                },
                flag = TASKBAR_SEARCH_EDU_SEEN_FLAG,
            )
        ) {
            return null
        }
        return TooltipsEduPage(
            title = context.getString(R.string.taskbar_search_edu_title),
            canBeSkipped = true,
            tooltips = listOf(searchTooltipInfo),
            location = DisplayLocation.SEARCH_ICON,
        )
    }

    /**
     * Determines the next educational flow to show after the main features EDU has been completed.
     * It prioritizes the pinning EDU, followed by the search EDU.
     *
     * This method also marks the pinning EDU as seen if it's included in the returned tooltips.
     */
    private fun onFeaturesEduShown(): List<TooltipsEduPage>? {
        val searchEdu = getSearchEdu()
        if (searchEdu != null) {
            return listOf(searchEdu)
        }
        return null
    }

    /**
     * Converts a collection of [TooltipInfo] objects into a list of [TooltipsEduPage] objects,
     * paginating them based on [MAX_TOOLTIPS_PER_PAGE].
     */
    private fun convertToFeaturesEduPages(
        tooltipsToShow: MutableCollection<TooltipInfo>
    ): List<TooltipsEduPage>? {
        if (tooltipsToShow.isEmpty()) {
            return null
        }
        val isSingleMultipanePage = tooltipsToShow.size in 2..MAX_TOOLTIPS_PER_PAGE
        val tooltipsPages = mutableListOf<TooltipsEduPage>()
        while (tooltipsToShow.isNotEmpty()) {
            // Get tooltips for current page, not exceeding list size
            val currentPageTooltipsCount = minOf(MAX_TOOLTIPS_PER_PAGE, tooltipsToShow.size)
            val currentPageTooltips = tooltipsToShow.take(currentPageTooltipsCount)
            // Remove taken tooltips from the list
            tooltipsToShow.removeAll(currentPageTooltips.toSet())
            // Check if there are more tooltips to show
            val hasMorePages = tooltipsToShow.isNotEmpty()
            // Determine action button text for current page
            val actionButtonTextResId =
                when {
                    isSingleMultipanePage ->
                        R.string.taskbar_edu_done // done for single page tooltip
                    hasMorePages -> R.string.taskbar_edu_next // next for multi-page, non last page
                    else -> null // nothing for the last tutorial page
                }
            // check if current page is standalone pining tooltip page
            val isStandAlonePinningPage =
                currentPageTooltipsCount == 1 &&
                    currentPageTooltips.first() == pinningTooltipInfoStandAlone
            tooltipsPages.add(
                TooltipsEduPage(
                    title =
                        if (isStandAlonePinningPage) {
                            context.getString(R.string.taskbar_edu_pinning_title)
                        } else {
                            context.getString(R.string.taskbar_edu_features)
                        },
                    // can be skipped if not single multipane page and it is the last page (no more
                    // pages)
                    canBeSkipped = !isSingleMultipanePage && !hasMorePages,
                    tooltips = currentPageTooltips,
                    actionButton = actionButtonTextResId?.let { context.getString(it) },
                    location =
                        if (isStandAlonePinningPage) {
                            DisplayLocation.SEARCH_DIVIDER
                        } else {
                            DisplayLocation.TASKBAR_CENTER
                        },
                )
            )
        }
        return tooltipsPages
    }

    /**
     * Gathers the appropriate feature tooltips to be shown to the user. This method also marks the
     * pinning EDU as seen if it's included in the returned tooltips.
     */
    private fun collectFeatureTooltipsUpdateFlags(
        updateFlags: Boolean = true
    ): MutableCollection<TooltipInfo> {
        val tooltipsToShow = mutableListOf<TooltipInfo>()

        if (blockedBySysUiState() || context.isDesktopFormFactor) {
            return tooltipsToShow
        }

        var bubblesTooltipIndex = -1
        var pinningTooltipIndex = -1
        if (
            setFlagIfUnset(
                optionalCondition = { !userHasSeenOldEdu },
                flag = TASKBAR_SPLIT_EDU_SEEN_FLAG,
                updateFlag = updateFlags,
            )
        ) {
            tooltipsToShow.add(splitTooltipInfo)
        }
        if (
            setFlagIfUnset(
                optionalCondition = { createAnyBubbleEnabled },
                flag = TASKBAR_BUBBLES_EDU_SEEN_FLAG,
                updateFlag = updateFlags,
            )
        ) {
            bubblesTooltipIndex = tooltipsToShow.size
            tooltipsToShow.add(bubbleTooltipInfo)
        }
        if (
            setFlagIfUnset(
                optionalCondition = { enableRecentsInTaskbar() },
                flag = TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG,
                updateFlag = updateFlags,
            )
        ) {
            tooltipsToShow.add(suggestionsTooltipInfo)
        }

        if (
            setFlagIfUnset(
                optionalCondition = { context.isTransientTaskbar && !userHasSeenOldPinningEdu },
                flag = TASKBAR_PINNING_EDU_SEEN_FLAG,
                updateFlag = updateFlags,
            )
        ) {
            pinningTooltipIndex = tooltipsToShow.size
            tooltipsToShow.add(pinningTooltipInfo)
        }
        // adjust messages if tooltip is the single item one on the edu page
        if (tooltipsToShow.isSingleItemOnTheLastPage(bubblesTooltipIndex)) {
            tooltipsToShow[bubblesTooltipIndex] = bubbleTooltipInfoStandAlone
        }
        if (tooltipsToShow.isSingleItemOnTheLastPage(pinningTooltipIndex)) {
            tooltipsToShow[pinningTooltipIndex] = pinningTooltipInfoStandAlone
        }
        return tooltipsToShow
    }

    /**
     * Returns true if flag was not set and optionalCondition is null or met. If function returns
     * true it also updates the flag, unless updateFlag is false.
     */
    private fun setFlagIfUnset(
        optionalCondition: (() -> Boolean)? = null,
        flag: Int,
        updateFlag: Boolean = true,
    ): Boolean {
        val result = (optionalCondition?.invoke() ?: true) && !getFlag(flag)
        if (result && updateFlag) {
            setFlag(flag)
        }
        return result
    }

    /**
     * Check weather index corresponds to the single item on the last page. The page size is taken
     * from [MAX_TOOLTIPS_PER_PAGE].
     */
    private fun List<Any>.isSingleItemOnTheLastPage(index: Int) =
        index % MAX_TOOLTIPS_PER_PAGE == 0 && index == lastIndex

    /**
     * Generates the formatted disclosure text for the search educational tooltip, including
     * clickable links for the Privacy Policy and Terms of Service.
     */
    private fun getDisclosureText(
        stringId: Int = R.string.taskbar_edu_search_disclosure
    ): CharSequence {
        val resources = context.resources
        val locale = resources.configuration.locales[0]
        val text =
            SpannableString(
                HtmlCompat.fromHtml(
                    resources.getString(
                        stringId,
                        PRIVACY_POLICY_BASE_URL + locale.language,
                        TOS_BASE_URL + locale.language,
                    ),
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                )
            )
        // Directly process URLSpan clicks
        text.getSpans(0, text.length, URLSpan::class.java).forEach { urlSpan ->
            val url: URLSpan =
                object : URLSpan(urlSpan.url) {
                    override fun onClick(widget: View) {
                        val uri = urlSpan.url.toUri()
                        val context = widget.context
                        val intent =
                            Intent(Intent.ACTION_VIEW, uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }

            val spanStart = text.getSpanStart(urlSpan)
            val spanEnd = text.getSpanEnd(urlSpan)
            val spanFlags = text.getSpanFlags(urlSpan)
            text.removeSpan(urlSpan)
            text.setSpan(url, spanStart, spanEnd, spanFlags)
        }
        return text
    }

    @VisibleForTesting
    fun getFlag(flag: Int): Boolean {
        return TASKBAR_SEEN_EDU_FLAGS.get(context) and flag == flag
    }

    @VisibleForTesting
    fun setFlag(flag: Int, seen: Boolean = true) {
        val flags = TASKBAR_SEEN_EDU_FLAGS.get(context)
        val newFlags = if (seen) flags or flag else flags and flag.inv()
        LauncherPrefs.get(context).put(TASKBAR_SEEN_EDU_FLAGS, newFlags)
    }

    fun dumpLogs(prefix: String?, pw: PrintWriter?) {
        pw?.println(prefix + "TooltipEduCombinator:")
        val shownTooltips = mutableListOf<String>()

        if (getFlag(TASKBAR_SWIPE_EDU_SEEN_FLAG) || userHasSeenOldEdu) {
            shownTooltips.add("swipe_up")
        }
        if (getFlag(TASKBAR_SPLIT_EDU_SEEN_FLAG) || userHasSeenOldEdu) {
            shownTooltips.add("split")
        }
        if (getFlag(TASKBAR_BUBBLES_EDU_SEEN_FLAG)) {
            shownTooltips.add("bubbles")
        }
        if (getFlag(TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG)) {
            shownTooltips.add("suggestions")
        }
        if (getFlag(TASKBAR_PINNING_EDU_SEEN_FLAG)) {
            shownTooltips.add("pinning")
        }
        if (getFlag(TASKBAR_SEARCH_EDU_SEEN_FLAG)) {
            shownTooltips.add("search")
        }
        pw?.println("$prefix\tShown tooltips: [${shownTooltips.joinToString()}]")
    }

    companion object {
        /** The maximum amount of the tooltips that can be shown per page. */
        const val MAX_TOOLTIPS_PER_PAGE = 3

        /** The base URL for the Privacy Policy that will later be localized. */
        private const val PRIVACY_POLICY_BASE_URL =
            "https://policies.google.com/privacy/embedded?hl="

        /** The base URL for the Terms of Service that will later be localized. */
        private const val TOS_BASE_URL = "https://policies.google.com/terms?hl="

        /**
         * Flag indicating whether the user has seen the educational tooltip for the swipe gesture.
         */
        const val TASKBAR_SWIPE_EDU_SEEN_FLAG = 1 shl 0

        /** Flag indicating whether the user has seen the educational tooltip for split screen. */
        const val TASKBAR_SPLIT_EDU_SEEN_FLAG = 1 shl 1

        /** Flag indicating whether the user has seen the educational tooltip for bubbles. */
        const val TASKBAR_BUBBLES_EDU_SEEN_FLAG = 1 shl 2

        /** Flag indicating whether the user has seen the educational tooltip for suggestions. */
        const val TASKBAR_SUGGESTIONS_EDU_SEEN_FLAG = 1 shl 3

        /**
         * Flag indicating whether the user has seen the educational tooltip for taskbar pinning.
         */
        const val TASKBAR_PINNING_EDU_SEEN_FLAG = 1 shl 4

        /** Flag indicating whether the user has seen the educational tooltip for taskbar search. */
        const val TASKBAR_SEARCH_EDU_SEEN_FLAG = 1 shl 5
    }
}
