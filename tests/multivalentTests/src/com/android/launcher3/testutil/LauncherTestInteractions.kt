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

package com.android.launcher3.testutil

import android.content.Intent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsStore.DEFER_UPDATES_TEST
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.TestUtil

/** Common shared test interactions with the Launcher activity [LauncherActivityScenarioRule]. */
class LauncherTestInteractions<LAUNCHER_TYPE : Launcher>
@JvmOverloads
constructor(
    private val launcherActivity: LauncherActivityScenarioRule<LAUNCHER_TYPE>,
    private val mainThreadExecutor: LooperExecutor = Executors.MAIN_EXECUTOR,
) {

    fun addToWorkspace(view: View) {
        TestUtil.runOnExecutorSync(mainThreadExecutor) {
            view.accessibilityDelegate.performAccessibilityAction(
                view,
                R.id.action_add_to_workspace,
                null,
            )
        }
        UiDevice.getInstance(getInstrumentation()).waitForIdle()
    }

    fun freezeAllApps() =
        launcherActivity.executeOnLauncher {
            it.appsView.appsStore.enableDeferUpdates(DEFER_UPDATES_TEST)
        }

    @JvmOverloads
    fun startAppFast(
        packageName: String,
        intent: Intent =
            getInstrumentation()
                .targetContext
                .packageManager
                .getLaunchIntentForPackage(packageName)!!,
    ) {
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        getInstrumentation().targetContext.startActivity(intent)
        UiDevice.getInstance(getInstrumentation()).waitForIdle()
    }

    /** Open all-apps and scrolls to the item satisfying [predicate] */
    fun scrollToAllAppIcon(predicate: (ItemInfo) -> Boolean) {
        launcherActivity.goToState(LauncherState.ALL_APPS)
        freezeAllApps()
        launcherActivity.executeOnLauncher { l ->
            l.hideKeyboard()
            val rv = l.appsView.activeRecyclerView
            val pos =
                rv.apps.adapterItems.indexOfFirst { i ->
                    i.itemInfo != null && predicate.invoke(i.itemInfo)
                }
            rv.layoutManager!!.scrollToPosition(pos)
        }
    }
}
