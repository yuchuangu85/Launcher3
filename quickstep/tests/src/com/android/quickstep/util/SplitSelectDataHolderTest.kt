/*
 *  Copyright (C) 2023 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.quickstep.util

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.SplitScreenUiState
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.quickstep.split.SplitSelectDataHolder
import com.android.quickstep.split.SplitSelectDataHolder.Companion.SPLIT_PENDINGINTENT_PENDINGINTENT
import com.android.quickstep.split.SplitSelectDataHolder.Companion.SPLIT_PENDINGINTENT_TASK
import com.android.quickstep.split.SplitSelectDataHolder.Companion.SPLIT_SHORTCUT_TASK
import com.android.quickstep.split.SplitSelectDataHolder.Companion.SPLIT_SINGLE_INTENT_FULLSCREEN
import com.android.quickstep.split.SplitSelectDataHolder.Companion.SPLIT_SINGLE_SHORTCUT_FULLSCREEN
import com.android.quickstep.split.SplitSelectDataHolder.Companion.SPLIT_SINGLE_TASK_FULLSCREEN
import com.android.quickstep.split.SplitSelectDataHolder.Companion.SPLIT_TASK_PENDINGINTENT
import com.android.quickstep.split.SplitSelectDataHolder.Companion.SPLIT_TASK_SHORTCUT
import com.android.quickstep.split.SplitSelectDataHolder.Companion.SPLIT_TASK_TASK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitSelectDataHolderTest {
    private lateinit var splitSelectDataHolder: SplitSelectDataHolder

    private val context: Context = ContextWrapper(getInstrumentation().targetContext)
    private val sampleTaskInfo = RunningTaskInfo()
    private val sampleTaskId = 10
    private val sampleTaskId2 = 11
    private val sampleUser = UserHandle(Process.myUserHandle().identifier)
    private val sampleIntent = Intent()
    private val sampleIntent2 = Intent()
    private val sampleShortcut = Intent()
    private val sampleShortcut2 = Intent()
    private val sampleItemInfo = ItemInfo()
    private val sampleItemInfo2 = ItemInfo()
    private val samplePackage = getInstrumentation().targetContext.packageName
    private val splitScreenUiState = SplitScreenUiState()

    @Before
    fun setup() {
        splitSelectDataHolder = SplitSelectDataHolder(context, splitScreenUiState)

        sampleTaskInfo.taskId = sampleTaskId
        sampleItemInfo.user = sampleUser
        sampleIntent.setPackage(samplePackage)
        sampleIntent2.setPackage(samplePackage)
        sampleShortcut.setPackage(samplePackage)
        sampleShortcut2.setPackage(samplePackage)
        sampleShortcut.putExtra(ShortcutKey.EXTRA_SHORTCUT_ID, "sampleShortcut")
        sampleShortcut2.putExtra(ShortcutKey.EXTRA_SHORTCUT_ID, "sampleShortcut2")
    }

    @Test
    fun setInitialAsTask() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            null,
            null,
        )
        assertTrue(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun setInitialAsIntent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        assertTrue(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun setInitialAsIntentWithAlreadyRunningTask() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            sampleTaskId,
        )
        assertTrue(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun setInitialAsShortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        assertTrue(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun setSecondAsTask() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        splitSelectDataHolder.setSecondTask(sampleTaskId, sampleItemInfo2)
        assertTrue(splitSelectDataHolder.isBothSplitAppsConfirmed())
    }

    @Test
    fun setSecondAsIntent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            null,
            null,
        )
        splitSelectDataHolder.setSecondTask(sampleIntent, sampleUser, sampleItemInfo2)
        assertTrue(splitSelectDataHolder.isBothSplitAppsConfirmed())
    }

    @Test
    fun setSecondAsShortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        splitSelectDataHolder.setSecondTask(sampleShortcut, sampleUser, sampleItemInfo2)
        assertTrue(splitSelectDataHolder.isBothSplitAppsConfirmed())
    }

    @Test
    fun setSecondAsPendingIntent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            null,
            null,
        )
        val pendingIntent =
            PendingIntent.getActivity(context, 0, sampleIntent, PendingIntent.FLAG_MUTABLE)
        splitSelectDataHolder.setSecondTask(pendingIntent, sampleItemInfo2)
        assertTrue(splitSelectDataHolder.isBothSplitAppsConfirmed())

        // Also verify that the launch data is correct
        val launchData = splitSelectDataHolder.getSplitLaunchData()
        assertEquals(SPLIT_TASK_PENDINGINTENT, launchData.splitLaunchType)
        assertNotNull(launchData.secondTask.pendingIntent)
        assertEquals(pendingIntent, launchData.secondTask.pendingIntent)
    }

    @Test
    fun generateLaunchData_Task_Task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
        )
        splitSelectDataHolder.setSecondTask(sampleTaskId2, sampleItemInfo2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_TASK_TASK)

        // should contain a valid task ID for first app, and no intent or shortcut
        assertNotEquals(launchData.initialTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.initialTask.pendingIntent)
        assertNull(launchData.initialShortcut)

        // should contain a valid task ID for second app, and no intent or shortcut
        assertNotEquals(launchData.secondTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.secondTask.pendingIntent)
        assertNull(launchData.secondShortcut)

        // Stage position should not be swapped for this launch type
        assertEquals(STAGE_POSITION_TOP_OR_LEFT, launchData.initialStagePosition)
    }

    @Test
    fun generateLaunchData_Task_Intent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
        )
        splitSelectDataHolder.setSecondTask(sampleIntent, sampleUser, sampleItemInfo2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_TASK_PENDINGINTENT)

        // should contain a valid task ID for first app, and no intent or shortcut
        assertNotEquals(launchData.initialTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.initialTask.pendingIntent)
        assertNull(launchData.initialShortcut)

        // should contain a valid intent for second app, and no task ID or shortcut
        assertNotNull(launchData.secondTask.pendingIntent)
        assertEquals(launchData.secondTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.secondShortcut)

        // Stage position should be swapped for this launch type
        assertEquals(STAGE_POSITION_BOTTOM_OR_RIGHT, launchData.initialStagePosition)
    }

    @Test
    fun generateLaunchData_Task_Shortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
        )
        splitSelectDataHolder.setSecondTask(sampleShortcut, sampleUser, sampleItemInfo2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_TASK_SHORTCUT)

        // should contain a valid task ID for first app, and no intent or shortcut
        assertNotEquals(launchData.initialTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.initialTask.pendingIntent)
        assertNull(launchData.initialShortcut)

        // should contain a valid shortcut and intent for second app, and no task ID
        assertNotNull(launchData.secondShortcut)
        assertNotNull(launchData.secondTask.pendingIntent)
        assertEquals(launchData.secondTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.secondShortcut!!.activity)

        // Stage position should be swapped for this launch type
        assertEquals(STAGE_POSITION_BOTTOM_OR_RIGHT, launchData.initialStagePosition)
    }

    @Test
    fun generateLaunchData_Task_Shortcut_withComponent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
        )
        // Create a shortcut intent and explicitly set a component on it
        val shortcutWithComponent = Intent(sampleShortcut)
        val componentName = ComponentName(samplePackage, "TestActivity")
        shortcutWithComponent.component = componentName
        splitSelectDataHolder.setSecondTask(shortcutWithComponent, sampleUser, sampleItemInfo2)

        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_TASK_SHORTCUT)
        assertNotNull(launchData.secondShortcut)
        // Verify the activity component was correctly set
        assertEquals(componentName, launchData.secondShortcut!!.activity)
    }

    @Test
    fun generateLaunchData_Intent_Task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        splitSelectDataHolder.setSecondTask(sampleTaskId, sampleItemInfo2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_PENDINGINTENT_TASK)

        // should contain a valid intent for first app, and no task ID or shortcut
        assertNotNull(launchData.initialTask.pendingIntent)
        assertEquals(launchData.initialTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.initialShortcut)

        // should contain a valid task ID for second app, and no intent or shortcut
        assertNotEquals(launchData.secondTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.secondTask.pendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Shortcut_Task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        splitSelectDataHolder.setSecondTask(sampleTaskId, sampleItemInfo2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_SHORTCUT_TASK)

        // should contain a valid shortcut and intent for first app, and no task ID
        assertNotNull(launchData.initialShortcut)
        assertNotNull(launchData.initialTask.pendingIntent)
        assertEquals(launchData.initialTask.taskId, INVALID_TASK_ID)

        // should contain a valid task ID for second app, and no intent or shortcut
        assertNotEquals(launchData.secondTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.secondTask.pendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Intent_Intent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        splitSelectDataHolder.setSecondTask(sampleIntent2, sampleUser, sampleItemInfo2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_PENDINGINTENT_PENDINGINTENT)

        // should contain a valid intent for first app, and no task ID or shortcut
        assertNotNull(launchData.initialTask.pendingIntent)
        assertEquals(launchData.initialTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.initialShortcut)

        // should contain a valid intent for second app, and no task ID or shortcut
        assertNotNull(launchData.secondTask.pendingIntent)
        assertEquals(launchData.secondTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Shortcut_Intent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        splitSelectDataHolder.setSecondTask(sampleIntent2, sampleUser, sampleItemInfo2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(SPLIT_PENDINGINTENT_PENDINGINTENT, launchData.splitLaunchType)

        // should contain a valid shortcut and intent for first app, and no task ID
        assertNotNull(launchData.initialShortcut)
        assertNotNull(launchData.initialTask.pendingIntent)
        assertEquals(INVALID_TASK_ID, launchData.initialTask.taskId)

        // should contain a valid intent for second app, and no task ID or shortcut
        assertNotNull(launchData.secondTask.pendingIntent)
        assertEquals(INVALID_TASK_ID, launchData.secondTask.taskId)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Intent_Shortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        splitSelectDataHolder.setSecondTask(sampleShortcut2, sampleUser, sampleItemInfo2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(SPLIT_PENDINGINTENT_PENDINGINTENT, launchData.splitLaunchType)

        // should contain a valid intent for first app, and no task ID or shortcut
        assertNotNull(launchData.initialTask.pendingIntent)
        assertEquals(INVALID_TASK_ID, launchData.initialTask.taskId)
        assertNull(launchData.initialShortcut)

        // should contain a valid shortcut and intent for second app, and no task ID
        assertNotNull(launchData.secondShortcut)
        assertNotNull(launchData.secondTask.pendingIntent)
        assertEquals(INVALID_TASK_ID, launchData.secondTask.taskId)
    }

    @Test
    fun generateLaunchData_Shortcut_Shortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        splitSelectDataHolder.setSecondTask(sampleShortcut2, sampleUser, sampleItemInfo2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(SPLIT_PENDINGINTENT_PENDINGINTENT, launchData.splitLaunchType)

        // should contain a valid shortcut and intent for first app, and no task ID
        assertNotNull(launchData.initialShortcut)
        assertNotNull(launchData.initialTask.pendingIntent)
        assertEquals(INVALID_TASK_ID, launchData.initialTask.taskId)

        // should contain a valid shortcut and intent for second app, and no task ID
        assertNotNull(launchData.secondShortcut)
        assertNotNull(launchData.secondTask.pendingIntent)
        assertEquals(INVALID_TASK_ID, launchData.secondTask.taskId)
    }

    @Test
    fun generateLaunchData_Single_Task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
        )
        val launchData = splitSelectDataHolder.getFullscreenLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_SINGLE_TASK_FULLSCREEN)

        // should contain a valid task ID for first app, and no intent or shortcut
        assertNotEquals(launchData.initialTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.initialTask.pendingIntent)
        assertNull(launchData.initialShortcut)

        // should contain no task ID, intent, or shortcut for second app
        assertEquals(launchData.secondTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.secondTask.pendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Single_Intent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        val launchData = splitSelectDataHolder.getFullscreenLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_SINGLE_INTENT_FULLSCREEN)

        // should contain a valid intent for first app, and no task ID or shortcut
        assertNotNull(launchData.initialTask.pendingIntent)
        assertEquals(launchData.initialTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.initialShortcut)

        // should contain no task ID, intent, or shortcut for second app
        assertEquals(launchData.secondTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.secondTask.pendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Single_Shortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        val launchData = splitSelectDataHolder.getFullscreenLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_SINGLE_SHORTCUT_FULLSCREEN)

        // should contain a valid shortcut and intent for first app, and no task ID
        assertNotNull(launchData.initialShortcut)
        assertNotNull(launchData.initialTask.pendingIntent)
        assertEquals(launchData.initialTask.taskId, INVALID_TASK_ID)

        // should contain no task ID, intent, or shortcut for second app
        assertEquals(launchData.secondTask.taskId, INVALID_TASK_ID)
        assertNull(launchData.secondTask.pendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun clearState_task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            null,
            null,
        )
        splitSelectDataHolder.setSecondTask(sampleIntent, sampleUser, sampleItemInfo2)
        splitSelectDataHolder.resetState()
        assertFalse(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun clearState_intent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID,
        )
        splitSelectDataHolder.setSecondTask(sampleIntent, sampleUser, sampleItemInfo2)
        splitSelectDataHolder.resetState()
        assertFalse(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun getSplitLaunchType_intentsNotConverted_throwsException() {
        // Set up the data holder with two intents, which is a valid pre-conversion state.
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        splitSelectDataHolder.setSecondTask(sampleIntent2, sampleUser, sampleItemInfo2)

        // Calling getSplitLaunchType before converting intents should throw an exception.
        val e =
            assertThrows(IllegalStateException::class.java) {
                splitSelectDataHolder.getSplitLaunchType()
            }
        assertEquals("Intents need to be converted", e.message)
    }

    @Test
    fun getSplitLaunchType_unidentifiedLaunchType_throwsException() {
        // Set up an invalid state for a split launch (e.g., only the initial intent is set).
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )

        // Calling getSplitLaunchData will convert intents, then fail to find a valid launch type.
        val e =
            assertThrows(IllegalStateException::class.java) {
                splitSelectDataHolder.getSplitLaunchData()
            }
        assertEquals("Unidentified split launch type", e.message)
    }

    @Test
    fun callingTwoSetters_shouldNotMerge() {
        splitSelectDataHolder.setSecondTask(9, sampleItemInfo)
        assertEquals(9, splitSelectDataHolder.getSecondTaskId())
        splitSelectDataHolder.setSecondTask(sampleIntent, sampleUser, sampleItemInfo2)
        assertEquals(INVALID_TASK_ID, splitSelectDataHolder.getSecondTaskId())
    }
}
