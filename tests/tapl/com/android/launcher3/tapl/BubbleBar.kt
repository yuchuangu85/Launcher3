/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.tapl

import androidx.test.uiautomator.UiObject2
import org.junit.Assert

/**
 * **THE BubbleBar CONSTRUCTOR IS NOT INTENDED TO BE USED FROM TESTS**
 *
 * Provides an API for interacting with the bubble bar within launcher automation tests tests.
 *
 * Note that this class does not represent the state of the bubble bar being stashed.
 */
class BubbleBar(private val mLauncher: LauncherInstrumentation) {

    private val bubbleBarViewSelector = mLauncher.getLauncherObjectSelector(RES_ID_NAME_BUBBLE_BAR)

    init {
        Assert.assertFalse(bubbles.isEmpty())
    }

    /**
     * Returns all the bubbles in the bubble bar.
     *
     * Note that the overflow bubble is not included in the result because it is never visible when
     * the bubble bar is collapsed.
     */
    private val bubbles: List<UiObject2>
        get() = mLauncher.waitForLauncherObject(bubbleBarViewSelector).children

    /** Expands the bubble bar if it is collapsed */
    fun expand() {
        mLauncher.eventsCheck().use {
            verifyCollapsed {
                mLauncher.addContextLayer("Expand bubble bar").use {
                    mLauncher.waitForLauncherObject(bubbleBarViewSelector).click()
                    verifyExpanded()
                }
            }
        }
    }

    /** Verifies that the bubble bar is collapsed. */
    private fun verifyCollapsed(furtherChecks: (() -> Unit)? = null) {
        mLauncher.addContextLayer("Check bubble bar expanded view is gone").use {
            mLauncher.waitUntilSystemUiObjectGone(RES_ID_EXPANDED_VIEW)
            furtherChecks?.invoke()
        }
    }

    /** Verifies that the bubble bar is expanded. */
    fun verifyExpanded(furtherChecks: (() -> Unit)? = null) {
        mLauncher.addContextLayer("Check bubble bar expanded view is visible").use {
            mLauncher.waitForSystemUiObject(RES_ID_EXPANDED_VIEW)
            furtherChecks?.invoke()
        }
    }

    /** Cleans up the bubble bar if test failed to remove it. */
    fun cleanup() {
        mLauncher.collapseBubbleBar()
        mLauncher.addContextLayer("Verify bubble bar is removed").use {
            mLauncher.waitUntilLauncherObjectGone(bubbleBarViewSelector)
        }
    }

    companion object {
        const val RES_ID_NAME_BUBBLE_BAR = "taskbar_bubbles"
        const val RES_ID_EXPANDED_VIEW = "bubble_expanded_view"
    }
}
