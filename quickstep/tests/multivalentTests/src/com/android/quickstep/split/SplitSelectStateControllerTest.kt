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

package com.android.quickstep.split

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Rect
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import android.view.LayoutInflater
import androidx.compose.foundation.layout.add
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherApplication
import com.android.launcher3.LauncherState
import com.android.launcher3.SplitScreenUiState
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.StatsLogger
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ResolvedTargetInfo
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StatefulActivity
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.ComponentKey
import com.android.quickstep.OverviewComponentObserver
import com.android.quickstep.RecentsModel
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.split.SplitSelectStateController.SplitFromDesktopController
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SplitTask
import com.android.quickstep.util.binder.OneWayBinderList
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.window.RecentsWindowManager
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.Flags.FLAG_RESOLVE_TRAMPOLINE_DESTINATION_PACKAGES
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import com.android.wm.shell.splitscreen.ISplitSelectListener
import java.util.function.Consumer
import java.util.function.Predicate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer

@RunWith(AndroidJUnit4::class)
class SplitSelectStateControllerTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val systemUiProxy: SystemUiProxy = mock()
    private val depthController: DepthController<*, *> = mock()
    private val statsLogManager: StatsLogManager = mock()
    private val statsLogger: StatsLogger = mock()
    private val stateManager: StateManager<LauncherState, StatefulActivity<LauncherState>> = mock()
    private val context: RecentsViewContainer = mock()

    private val mockContext: Context = mock()
    private val mockApp: LauncherApplication = mock()
    private val mockAppComponent: LauncherAppComponent = mock()
    private val mockResources: Resources = mock()
    private val mockLayoutInflater: LayoutInflater = mock()

    private val recentsModel: RecentsModel = mock()
    private val pendingIntent: PendingIntent = mock()
    private val splitFromDesktopController: SplitFromDesktopController = mock()
    private val recentsView: RecentsView<*, *> = mock()
    private val splitScreenUiState: SplitScreenUiState = SplitScreenUiState()
    private val mSplitScreenAppResolver: SplitScreenAppResolver = mock()
    private val mOverviewComponentObserver: OverviewComponentObserver = mock()

    private lateinit var splitSelectStateController: SplitSelectStateController

    private val primaryUserHandle = UserHandle(ActivityManager.RunningTaskInfo().userId)
    private val nonPrimaryUserHandle = UserHandle(ActivityManager.RunningTaskInfo().userId + 10)

    private var taskIdCounter = 0

    private val mockContextAnswer = Answer { invocation ->
        if (invocation.method.name == "asContext") {
            return@Answer invocation.mock
        }
        Answers.RETURNS_DEFAULTS.answer(invocation)
    }

    private fun getUniqueId(): Int {
        return ++taskIdCounter
    }

    @Before
    fun setup() {
        whenever(statsLogManager.logger()).thenReturn(statsLogger)
        whenever(statsLogger.withInstanceId(any())).thenReturn(statsLogger)
        whenever(statsLogger.withItemInfo(any())).thenReturn(statsLogger)
        whenever(mSplitScreenAppResolver.getResolvedDestinationPackage(any(), any()))
            .thenReturn(null)
        whenever(mSplitScreenAppResolver.isTaskAppSingleInstance(any(), any(), any(), any(), any()))
            .thenReturn(false)

        // Setup the LauncherAppComponent chain
        whenever(mockAppComponent.getSystemUiProxy()).thenReturn(systemUiProxy)
        whenever(mockAppComponent.getOverviewComponentObserver())
            .thenReturn(mOverviewComponentObserver)
        whenever(mockApp.appComponent).thenReturn(mockAppComponent)

        // Ensure mockContext returns the mockApp
        whenever(mockContext.applicationContext).thenReturn(mockApp)
        whenever(mockApp.applicationContext).thenReturn(mockApp)

        // Ensure context.asContext() returns our special mockContext
        whenever(context.asContext()).thenReturn(mockContext)

        // Setup Resources mock
        whenever(mockResources.getDimensionPixelSize(any())).thenReturn(100)
        whenever(mockApp.resources).thenReturn(mockResources)

        // Stub getSystemService for LayoutInflater on mockApp
        whenever(mockApp.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
            .thenReturn(mockLayoutInflater)

        doReturn(OneWayBinderList(ISplitSelectListener.Stub::asInterface))
            .whenever(systemUiProxy)
            .splitSelectListeners

        splitSelectStateController =
            SplitSelectStateController(
                context,
                stateManager,
                depthController,
                statsLogManager,
                systemUiProxy,
                recentsModel,
                null, /*activityBackCallback*/
                splitScreenUiState,
                mSplitScreenAppResolver,
            )
    }

    @Test
    fun activeTasks_noMatchingTasks() {
        val nonMatchingResolvedTargetInfo =
            ResolvedTargetInfo(null, ComponentName("no", "match"), primaryUserHandle)
        val groupTask1 =
            generateSplitTask(
                ComponentName("pomegranate", "juice"),
                ComponentName("pumpkin", "pie"),
            )
        val groupTask2 =
            generateSplitTask(
                ComponentName("hotdog", "juice"),
                ComponentName("personal", "computer"),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask1)
        tasks.add(groupTask2)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> { assertNull("No tasks should have matched", it[0] /*task*/) }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(nonMatchingResolvedTargetInfo),
                        false /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_singleMatchingTask() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                primaryUserHandle,
            )
        val groupTask1 =
            generateSplitTask(
                ComponentName(matchingPackage, matchingClass),
                ComponentName("pomegranate", "juice"),
            )
        val groupTask2 =
            generateSplitTask(
                ComponentName("pumpkin", "pie"),
                ComponentName("personal", "computer"),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask1)
        tasks.add(groupTask2)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> {
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage,
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass,
                )
                assertEquals(it[0], groupTask1.topLeftTask)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingResolvedTargetInfo),
                        false /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_passesFilterToRecentsModel() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                primaryUserHandle,
            )
        // Capture callback from recentsModel#getTasks()
        val filter = Predicate<GroupTask> { task -> true }
        splitSelectStateController.findLastActiveTasksAndRunCallback(
            filter,
            listOf(matchingResolvedTargetInfo),
            false, /* findExactPairMatch */
        ) {}
        verify(recentsModel).getTasks(eq(filter), any())
    }

    @Test
    fun activeTasks_skipTaskWithDifferentUser() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val nonPrimaryUserResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                nonPrimaryUserHandle,
            )
        val groupTask1 =
            generateSplitTask(
                ComponentName(matchingPackage, matchingClass),
                ComponentName("pomegranate", "juice"),
            )
        val groupTask2 =
            generateSplitTask(
                ComponentName("pumpkin", "pie"),
                ComponentName("personal", "computer"),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask1)
        tasks.add(groupTask2)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> { assertNull("No tasks should have matched", it[0] /*task*/) }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(nonPrimaryUserResolvedTargetInfo),
                        false /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_findTaskAsNonPrimaryUser() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val nonPrimaryUserResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                nonPrimaryUserHandle,
            )
        val groupTask1 =
            generateSplitTask(
                ComponentName(matchingPackage, matchingClass),
                nonPrimaryUserHandle,
                ComponentName("pomegranate", "juice"),
                nonPrimaryUserHandle,
            )
        val groupTask2 =
            generateSplitTask(
                ComponentName("pumpkin", "pie"),
                ComponentName("personal", "computer"),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask1)
        tasks.add(groupTask2)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> {
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage,
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass,
                )
                assertEquals("userId mismatched", it[0].key.userId, nonPrimaryUserHandle.identifier)
                assertEquals(it[0], groupTask1.topLeftTask)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(nonPrimaryUserResolvedTargetInfo),
                        false /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleMatchMostRecentTask() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                primaryUserHandle,
            )
        val groupTask1 =
            generateSplitTask(
                ComponentName(matchingPackage, matchingClass),
                ComponentName("pumpkin", "pie"),
            )
        val groupTask2 =
            generateSplitTask(
                ComponentName("pomegranate", "juice"),
                ComponentName(matchingPackage, matchingClass),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> {
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage,
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass,
                )
                assertEquals(it[0], groupTask1.topLeftTask)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingResolvedTargetInfo),
                        false /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleSearchShouldFindTask() {
        val noMatchingResolvedTargetInfo =
            ResolvedTargetInfo(null, ComponentName("no", "match"), primaryUserHandle)
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                primaryUserHandle,
            )

        val groupTask1 =
            generateSplitTask(ComponentName("hotdog", "pie"), ComponentName("pumpkin", "pie"))
        val groupTask2 =
            generateSplitTask(
                ComponentName("pomegranate", "juice"),
                ComponentName(matchingPackage, matchingClass),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> {
                assertEquals("Expected array length 2", 2, it.size)
                assertNull("No tasks should have matched", it[0] /*task*/)
                assertEquals(
                    "ComponentName package mismatched",
                    it[1].key.baseIntent.component?.packageName,
                    matchingPackage,
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[1].key.baseIntent.component?.className,
                    matchingClass,
                )
                assertEquals(it[1], groupTask2.bottomRightTask)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(noMatchingResolvedTargetInfo, matchingResolvedTargetInfo),
                        false /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)

        verify(mSplitScreenAppResolver, never()).getResolvedDestinationPackage(any(), any())
    }

    @Test
    @EnableFlags(FLAG_RESOLVE_TRAMPOLINE_DESTINATION_PACKAGES)
    fun activeTasks_trampolineMultipleSearchShouldIgnore() {
        val noMatchingResolvedTargetInfo =
            ResolvedTargetInfo(null, ComponentName("no", "match"), primaryUserHandle)
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                primaryUserHandle,
            )

        val groupTask1 =
            generateSplitTask(ComponentName("hotdog", "pie"), ComponentName("pumpkin", "pie"))
        val groupTask2 =
            generateSplitTask(
                ComponentName("pomegranate", "juice"),
                ComponentName(matchingPackage, matchingClass),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> {
                assertEquals("Expected array length 2", 2, it.size)
                assertNull("No tasks should have matched", it[0] /*task*/)
                assertEquals(
                    "ComponentName package mismatched",
                    it[1].key.baseIntent.component?.packageName,
                    matchingPackage,
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[1].key.baseIntent.component?.className,
                    matchingClass,
                )
                assertEquals(it[1], groupTask2.bottomRightTask)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(noMatchingResolvedTargetInfo, matchingResolvedTargetInfo),
                        false /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    @EnableFlags(FLAG_RESOLVE_TRAMPOLINE_DESTINATION_PACKAGES)
    fun activeTasks_trampolineMultipleSearchShouldFindTask() {
        val matchingOriginalPackage = "original"
        val matchingDestinationPackage = "destination"
        val matchingClass = "juice"
        val originalComponentName = ComponentName(matchingOriginalPackage, matchingClass)
        val noMatchingResolvedTargetInfo =
            ResolvedTargetInfo(null, ComponentName("no", "match"), primaryUserHandle)
        val matchingResolvedTargetInfo =
            ResolvedTargetInfo(null, originalComponentName, primaryUserHandle)

        val task11 = generateSingleTask(ComponentName("hotdog", "pie"))
        val task12 = generateSingleTask(ComponentName("pumpkin", "pie"))
        val groupTask1 = generateSplitTask(task11, task12)

        val task21 = generateSingleTask(ComponentName("pomegranate", "juice"))
        val taskMatching =
            generateSingleTask(ComponentName(matchingDestinationPackage, matchingClass))
        val groupTask2 = generateSplitTask(task21, taskMatching)
        val mockAppInfo: AppInfo = mock()

        whenever(
                mSplitScreenAppResolver.getResolvedDestinationPackage(
                    any(),
                    eq(originalComponentName),
                )
            )
            .thenReturn(matchingDestinationPackage)
        whenever(
                mSplitScreenAppResolver.resolveAppInfoByComponent(
                    eq(
                        ComponentKey(
                            taskMatching.key.component,
                            UserHandle.of(taskMatching.key.userId),
                        )
                    )
                )
            )
            .thenReturn(mockAppInfo)
        whenever(
                mSplitScreenAppResolver.isTaskAppSingleInstance(
                    eq(taskMatching),
                    any(),
                    eq(mockAppInfo),
                    eq(matchingDestinationPackage),
                    any(),
                )
            )
            .thenReturn(true)

        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> {
                assertEquals("Expected array length 2", 2, it.size)
                assertNull("First search (no match) should be null", it[0])

                assertEquals(
                    "ComponentName package mismatched",
                    matchingDestinationPackage,
                    it[1].key.baseIntent.component?.packageName,
                )

                assertEquals(
                    "ComponentName class mismatched",
                    matchingClass,
                    it[1].key.baseIntent.component?.className,
                )
                assertEquals("Task object mismatch", it[1], groupTask2.bottomRightTask)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(noMatchingResolvedTargetInfo, matchingResolvedTargetInfo),
                        false, /* findExactPairMatch */
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleSearchShouldNotFindSameTaskTwice() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                primaryUserHandle,
            )

        val groupTask1 =
            generateSplitTask(ComponentName("hotdog", "pie"), ComponentName("pumpkin", "pie"))
        val groupTask2 =
            generateSplitTask(
                ComponentName("pomegranate", "juice"),
                ComponentName(matchingPackage, matchingClass),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> {
                assertEquals("Expected array length 2", 2, it.size)
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage,
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass,
                )
                assertEquals(it[0], groupTask2.bottomRightTask)
                assertNull("No tasks should have matched", it[1] /*task*/)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingResolvedTargetInfo, matchingResolvedTargetInfo),
                        false /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any<Predicate<GroupTask>>(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleSearchShouldFindDifferentInstancesOfSameTask() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                primaryUserHandle,
            )

        val groupTask1 =
            generateSplitTask(
                ComponentName(matchingPackage, matchingClass),
                ComponentName("pumpkin", "pie"),
            )
        val groupTask2 =
            generateSplitTask(
                ComponentName("pomegranate", "juice"),
                ComponentName(matchingPackage, matchingClass),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> {
                assertEquals("Expected array length 2", 2, it.size)
                assertEquals(
                    "ComponentName package mismatched",
                    it[0].key.baseIntent.component?.packageName,
                    matchingPackage,
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[0].key.baseIntent.component?.className,
                    matchingClass,
                )
                assertEquals(it[0], groupTask1.topLeftTask)
                assertEquals(
                    "ComponentName package mismatched",
                    it[1].key.baseIntent.component?.packageName,
                    matchingPackage,
                )
                assertEquals(
                    "ComponentName class mismatched",
                    it[1].key.baseIntent.component?.className,
                    matchingClass,
                )
                assertEquals(it[1], groupTask2.bottomRightTask)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingResolvedTargetInfo, matchingResolvedTargetInfo),
                        false /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun activeTasks_multipleSearchShouldFindExactPairMatch() {
        val matchingPackage = "hotdog"
        val matchingClass = "juice"
        val matchingResolvedTargetInfo =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage, matchingClass),
                primaryUserHandle,
            )

        val matchingPackage2 = "pomegranate"
        val matchingClass2 = "juice"
        val matchingResolvedTargetInfo2 =
            ResolvedTargetInfo(
                null,
                ComponentName(matchingPackage2, matchingClass2),
                primaryUserHandle,
            )

        val groupTask1 =
            generateSplitTask(ComponentName("hotdog", "pie"), ComponentName("pumpkin", "pie"))
        val groupTask2 =
            generateSplitTask(
                ComponentName(matchingPackage2, matchingClass2),
                ComponentName(matchingPackage, matchingClass),
            )
        val groupTask3 =
            generateSplitTask(
                ComponentName("hotdog", "pie"),
                ComponentName(matchingPackage, matchingClass),
            )
        val tasks: ArrayList<GroupTask> = ArrayList()
        tasks.add(groupTask3)
        tasks.add(groupTask2)
        tasks.add(groupTask1)

        // Assertions happen in the callback we get from what we pass into
        // #findLastActiveTasksAndRunCallback
        val taskConsumer =
            Consumer<Array<Task>> {
                assertEquals("Expected array length 2", 2, it.size)
                assertEquals("Found wrong task", it[0], groupTask2.topLeftTask)
            }

        // Capture callback from recentsModel#getTasks()
        val consumer =
            argumentCaptor<Consumer<List<GroupTask>>> {
                    splitSelectStateController.findLastActiveTasksAndRunCallback(
                        listOf(matchingResolvedTargetInfo2, matchingResolvedTargetInfo),
                        true /* findExactPairMatch */,
                        taskConsumer,
                    )
                    verify(recentsModel).getTasks(any(), capture())
                }
                .lastValue

        // Send our mocked tasks
        consumer.accept(tasks)
    }

    @Test
    fun setInitialApp_withTaskId() {
        splitSelectStateController.setInitialTaskSelect(
            null /*intent*/,
            -1 /*stagePosition*/,
            ItemInfo(),
            null /*splitEvent*/,
            10, /*alreadyRunningTask*/
        )
        assertTrue(splitSelectStateController.isSplitSelectActive)
    }

    @Test
    fun setInitialApp_withIntent() {
        splitSelectStateController.setInitialTaskSelect(
            Intent() /*intent*/,
            -1 /*stagePosition*/,
            ItemInfo(),
            null /*splitEvent*/,
            -1, /*alreadyRunningTask*/
        )
        assertTrue(splitSelectStateController.isSplitSelectActive)
    }

    @Test
    fun resetAfterInitial() {
        whenever(context.getOverviewPanel<RecentsView<*, *>>()).thenReturn(recentsView)
        splitSelectStateController.setInitialTaskSelect(
            Intent() /*intent*/,
            -1 /*stagePosition*/,
            ItemInfo(),
            null /*splitEvent*/,
            -1,
        )
        splitSelectStateController.resetState()
        verify(recentsView, times(1)).resetDesktopTaskFromSplitSelectState()
        assertFalse(splitSelectStateController.isSplitSelectActive)
    }

    @Test
    fun secondPendingIntentSet() {
        val itemInfo = ItemInfo()
        val itemInfo2 = ItemInfo()
        whenever(pendingIntent.creatorUserHandle).thenReturn(primaryUserHandle)
        splitSelectStateController.setInitialTaskSelect(null, 0, itemInfo, null, 1)
        splitSelectStateController.setSecondTask(pendingIntent, itemInfo2)
        assertTrue(splitSelectStateController.isBothSplitAppsConfirmed)
    }

    @Test
    fun splitSelectStateControllerDestroyed_SplitFromDesktopControllerAlsoDestroyed() {
        // Initiate split from desktop controller
        splitSelectStateController.initSplitFromDesktopController(splitFromDesktopController)

        // Simulate default controller being destroyed
        splitSelectStateController.onDestroy()

        // Verify desktop controller is also destroyed
        verify(splitFromDesktopController).onDestroy()
    }

    @Test
    fun splitSelectStateControllerDestroyed_doNotResetDeskTopTasks() {
        whenever(context.getOverviewPanel<RecentsView<*, *>>()).thenReturn(recentsView)
        splitSelectStateController.setInitialTaskSelect(
            Intent(), /*intent*/
            -1, /*stagePosition*/
            ItemInfo(),
            null, /*splitEvent*/
            -1,
        )
        splitSelectStateController.onDestroy()
        splitSelectStateController.resetState()
        verify(recentsView, times(0)).resetDesktopTaskFromSplitSelectState()
    }

    /** Generates a [SplitTask] with default userId. */
    private fun generateSplitTask(
        task1ComponentName: ComponentName,
        task2ComponentName: ComponentName,
    ): SplitTask {
        val task1 = Task()
        var taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = getUniqueId()
        var intent = Intent()
        intent.component = task1ComponentName
        taskInfo.baseIntent = intent
        task1.key = Task.TaskKey(taskInfo)

        val task2 = Task()
        taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = getUniqueId()
        intent = Intent()
        intent.component = task2ComponentName
        taskInfo.baseIntent = intent
        task2.key = Task.TaskKey(taskInfo)
        return SplitTask(
            task1,
            task2,
            SplitBounds(
                /* leftTopBounds = */ Rect(),
                /* rightBottomBounds = */ Rect(),
                /* leftTopTaskId = */ task1.key.id,
                /* rightBottomTaskId = */ task2.key.id,
                /* snapPosition = */ SNAP_TO_2_50_50,
            ),
        )
    }

    /** Generates a [SplitTask] with custom user handles. */
    private fun generateSplitTask(
        task1ComponentName: ComponentName,
        userHandle1: UserHandle,
        task2ComponentName: ComponentName,
        userHandle2: UserHandle,
    ): SplitTask {
        val task1 = Task()
        var taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = getUniqueId()
        // Apply custom userHandle1
        taskInfo.userId = userHandle1.identifier
        var intent = Intent()
        intent.component = task1ComponentName
        taskInfo.baseIntent = intent
        task1.key = Task.TaskKey(taskInfo)
        val task2 = Task()
        taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = getUniqueId()
        // Apply custom userHandle2
        taskInfo.userId = userHandle2.identifier
        intent = Intent()
        intent.component = task2ComponentName
        taskInfo.baseIntent = intent
        task2.key = Task.TaskKey(taskInfo)
        return SplitTask(
            task1,
            task2,
            SplitBounds(
                /* leftTopBounds = */ Rect(),
                /* rightBottomBounds = */ Rect(),
                /* leftTopTaskId = */ task1.key.id,
                /* rightBottomTaskId = */ task2.key.id,
                /* snapPosition = */ SNAP_TO_2_50_50,
            ),
        )
    }

    /** Generates a [SplitTask] with [Task]. */
    private fun generateSplitTask(task1: Task, task2: Task): SplitTask {
        return SplitTask(
            task1,
            task2,
            SplitBounds(
                /* leftTopBounds = */ Rect(),
                /* rightBottomBounds = */ Rect(),
                /* leftTopTaskId = */ task1.key.id,
                /* rightBottomTaskId = */ task2.key.id,
                /* snapPosition = */ SNAP_TO_2_50_50,
            ),
        )
    }

    /** Generates a [Task] with default userId. */
    private fun generateSingleTask(taskComponentName: ComponentName): Task {
        val task = Task()
        val taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = getUniqueId()
        val intent = Intent()
        intent.component = taskComponentName
        taskInfo.baseIntent = intent
        task.key = Task.TaskKey(taskInfo)
        return task
    }

    @Test
    fun ableToStartSplitSelectAnimation_launcherOnDefaultDisplay_taskOnDefaultDisplay_returnsTrue() {
        val mockLauncher = mock<QuickstepLauncher>(defaultAnswer = mockContextAnswer)
        doReturn(mockApp).whenever(mockLauncher).applicationContext
        doReturn(mockResources).whenever(mockLauncher).resources
        doReturn(mockLayoutInflater)
            .whenever(mockLauncher)
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE)

        // Instantiate the controller class with mock QuickstepLauncher
        val controller = splitSelectStateController.SplitFromDesktopController(mockLauncher)

        val taskInfo = RunningTaskInfo()
        taskInfo.displayId = DEFAULT_DISPLAY

        val result = controller.ableToStartSplitSelectAnimation(taskInfo)

        assertTrue("Launcher on default display should handle tasks on default display", result)
    }

    @Test
    fun ableToStartSplitSelectAnimation_launcherOnDefaultDisplay_taskOnSecondaryDisplay_returnsFalse() {
        val mockLauncher = mock<QuickstepLauncher>(defaultAnswer = mockContextAnswer)
        doReturn(mockApp).whenever(mockLauncher).applicationContext
        doReturn(mockResources).whenever(mockLauncher).resources
        doReturn(mockLayoutInflater)
            .whenever(mockLauncher)
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE)

        val controller = splitSelectStateController.SplitFromDesktopController(mockLauncher)

        val taskInfo = RunningTaskInfo()
        taskInfo.displayId = 2 // Secondary display

        val result = controller.ableToStartSplitSelectAnimation(taskInfo)

        assertFalse(
            "Launcher on default display should NOT handle tasks on secondary display",
            result,
        )
    }

    @Test
    fun ableToStartSplitSelectAnimation_rwmOnSecondaryDisplay_taskOnSameDisplay_returnsTrue() {
        val secondaryDisplayId = 2
        val mockRwm = mock<RecentsWindowManager>(defaultAnswer = mockContextAnswer)
        doReturn(secondaryDisplayId).whenever(mockRwm).displayId

        doReturn(mockApp).whenever(mockRwm).applicationContext
        doReturn(mockResources).whenever(mockRwm).resources
        doReturn(mockLayoutInflater)
            .whenever(mockRwm)
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE)

        // Instantiate the controller class RecentsWindowManager
        val controller = splitSelectStateController.SplitFromDesktopController(mockRwm)

        val taskInfo = RunningTaskInfo()
        taskInfo.displayId = secondaryDisplayId

        val result = controller.ableToStartSplitSelectAnimation(taskInfo)

        assertTrue(
            "RWM on display $secondaryDisplayId should handle tasks on display $secondaryDisplayId",
            result,
        )
    }

    @Test
    fun ableToStartSplitSelectAnimation_rwmOnSecondaryDisplay_taskOnDifferentDisplay_returnsFalse() {
        val secondaryDisplayId = 2
        val otherDisplayId = 3
        val mockRwm = mock<RecentsWindowManager>(defaultAnswer = mockContextAnswer)

        doReturn(secondaryDisplayId).whenever(mockRwm).displayId
        doReturn(mockApp).whenever(mockRwm).applicationContext
        doReturn(mockResources).whenever(mockRwm).resources
        doReturn(mockLayoutInflater)
            .whenever(mockRwm)
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE)

        val controller = splitSelectStateController.SplitFromDesktopController(mockRwm)

        val taskInfo = RunningTaskInfo()
        taskInfo.displayId = otherDisplayId

        val result = controller.ableToStartSplitSelectAnimation(taskInfo)

        assertFalse(
            "RWM on display $secondaryDisplayId should NOT handle tasks on display $otherDisplayId",
            result,
        )
    }

    @Test
    fun ableToStartSplitSelectAnimation_rwmOnSecondaryDisplay_taskOnDefaultDisplay_returnsFalse() {
        val secondaryDisplayId = 2
        val mockRwm = mock<RecentsWindowManager>(defaultAnswer = mockContextAnswer)

        doReturn(secondaryDisplayId).whenever(mockRwm).displayId
        doReturn(mockApp).whenever(mockRwm).applicationContext
        doReturn(mockResources).whenever(mockRwm).resources
        doReturn(mockLayoutInflater)
            .whenever(mockRwm)
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE)

        val controller = splitSelectStateController.SplitFromDesktopController(mockRwm)

        val taskInfo = RunningTaskInfo()
        taskInfo.displayId = DEFAULT_DISPLAY

        val result = controller.ableToStartSplitSelectAnimation(taskInfo)

        assertFalse("RWM on secondary display should NOT handle tasks on default display", result)
    }
}
