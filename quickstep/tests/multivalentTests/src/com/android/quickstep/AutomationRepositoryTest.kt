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

package com.android.quickstep

import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.extensions.computercontrol.AutomatedPackageListener
import com.android.extensions.computercontrol.ComputerControlExtensions
import com.android.launcher3.automation.AutomationChange
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.IMMEDIATE_EXECUTOR
import com.android.launcher3.util.PackageUserKey
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/** Test for [AutomationRepositoryImpl] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AutomationRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val computerControlExtensions = mock<ComputerControlExtensions>()

    private lateinit var automationRepo: AutomationRepositoryImpl
    private lateinit var automatedPackageListener: AutomatedPackageListener
    private val lifeCycleTracker = mock<DaggerSingletonTracker>()

    @Before
    fun setup() {
        automationRepo =
            AutomationRepositoryImpl(
                context,
                computerControlExtensions,
                IMMEDIATE_EXECUTOR,
                lifeCycleTracker,
            )

        val automatedPackageListenerCaptor = argumentCaptor<AutomatedPackageListener>()
        verify(computerControlExtensions)
            .registerAutomatedPackageListener(
                eq(context),
                eq(IMMEDIATE_EXECUTOR),
                automatedPackageListenerCaptor.capture(),
            )
        assertThat(automatedPackageListenerCaptor.lastValue).isNotNull()
        automatedPackageListener = automatedPackageListenerCaptor.lastValue
    }

    @Test
    fun onAutomatedPackagesChanged_addPackage_dispatchesAutomationChange() {
        val receivedChanges = mutableListOf<AutomationChange>()
        // Given
        automationRepo.automatedPackages.changes
            .forEach(IMMEDIATE_EXECUTOR) { receivedChanges.add(it) }
            .use {
                // When
                automatedPackageListener.onAutomatedPackagesChanged(
                    AUTOMATING_PACKAGE,
                    listOf(AUTOMATED_PACKAGE),
                    USER_HANDLE,
                )

                // Then
                assertThat(receivedChanges).hasSize(1)
                receivedChanges.first().run {
                    assertThat(userHandle).isEqualTo(USER_HANDLE)
                    assertThat(addedPackages).isEqualTo(setOf(AUTOMATED_PACKAGE))
                    assertThat(removedPackages).isEmpty()
                }
            }
    }

    @Test
    fun onAutomatedPackagesChanged_removePackage_dispatchesAutomationChange() {
        val receivedChanges = mutableListOf<AutomationChange>()
        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )
        automationRepo.automatedPackages.changes
            .forEach(IMMEDIATE_EXECUTOR) { receivedChanges.add(it) }
            .use {
                // When
                automatedPackageListener.onAutomatedPackagesChanged(
                    AUTOMATING_PACKAGE,
                    emptyList(),
                    USER_HANDLE,
                )

                // Then
                assertThat(receivedChanges).hasSize(1)
                receivedChanges.first().run {
                    assertThat(userHandle).isEqualTo(USER_HANDLE)
                    assertThat(addedPackages).isEmpty()
                    assertThat(removedPackages).isEqualTo(setOf(AUTOMATED_PACKAGE))
                }
            }
    }

    @Test
    fun automatedPackages_returnsCorrectPackages() {
        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )

        // Then
        val automatedPackages = automationRepo.automatedPackages.value
        assertThat(automatedPackages)
            .containsExactly(PackageUserKey(AUTOMATED_PACKAGE, USER_HANDLE))
    }

    @Test
    fun isPackageAutomated_automatedPkg_returnsTrue() {
        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )

        // Then
        assertThat(automationRepo.isPackageAutomated(USER_HANDLE, AUTOMATED_PACKAGE)).isTrue()
        assertThat(automationRepo.isPackageAutomated(USER_ID, AUTOMATED_PACKAGE)).isTrue()
    }

    @Test
    fun isPackageAutomated_notAutomatedPkg_returnsFalse() {
        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )

        // Then
        assertThat(automationRepo.isPackageAutomated(USER_HANDLE, NOT_AUTOMATED_PACKAGE)).isFalse()
        assertThat(automationRepo.isPackageAutomated(USER_ID, NOT_AUTOMATED_PACKAGE)).isFalse()
    }

    @Test
    fun isPackageAutomated_differentUser_returnsFalse() {
        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            UserHandle(999),
        )

        // Then
        assertThat(automationRepo.isPackageAutomated(USER_HANDLE, AUTOMATED_PACKAGE)).isFalse()
        assertThat(automationRepo.isPackageAutomated(USER_ID, AUTOMATED_PACKAGE)).isFalse()
    }

    @Test
    fun isPackageAutomated_differentAutomatingPkg_returnsTrue() {
        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )
        automatedPackageListener.onAutomatedPackagesChanged(
            "Other.Automating.Package",
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            emptyList(),
            USER_HANDLE,
        )

        // Then
        assertThat(automationRepo.isPackageAutomated(USER_HANDLE, AUTOMATED_PACKAGE)).isTrue()
        assertThat(automationRepo.isPackageAutomated(USER_ID, AUTOMATED_PACKAGE)).isTrue()
    }

    @Test
    fun onAutomatedPackagesChanged_alreadyAutomated_doesNotDispatchChange() {
        val receivedChanges = mutableListOf<AutomationChange>()
        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )

        automationRepo.automatedPackages.changes
            .forEach(IMMEDIATE_EXECUTOR) { receivedChanges.add(it) }
            .use {
                // When
                automatedPackageListener.onAutomatedPackagesChanged(
                    "com.other.package",
                    listOf(AUTOMATED_PACKAGE),
                    USER_HANDLE,
                )

                // Then
                assertThat(receivedChanges).isEmpty()
            }
    }

    @Test
    fun onAutomatedPackagesChanged_stillAutomatedByAnother_doesNotDispatchRemoved() {
        val receivedChanges = mutableListOf<AutomationChange>()
        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )
        automatedPackageListener.onAutomatedPackagesChanged(
            "com.test.automating2",
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )

        automationRepo.automatedPackages.changes
            .forEach(IMMEDIATE_EXECUTOR) { receivedChanges.add(it) }
            .use {
                // When
                automatedPackageListener.onAutomatedPackagesChanged(
                    AUTOMATING_PACKAGE,
                    emptyList(),
                    USER_HANDLE,
                )

                // Then
                assertThat(receivedChanges).isEmpty()
                assertThat(automationRepo.isPackageAutomated(USER_HANDLE, AUTOMATED_PACKAGE))
                    .isTrue()
                assertThat(automationRepo.isPackageAutomated(USER_ID, AUTOMATED_PACKAGE)).isTrue()
            }
    }

    @Test
    fun onAutomatedPackagesChanged_whenExistingEntryChanged_dispatchRemoveAndAdd() {
        val receivedChanges = mutableListOf<AutomationChange>()

        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )
        val otherAutomatedPackage = "com.test.automated2"
        automationRepo.automatedPackages.changes
            .forEach(IMMEDIATE_EXECUTOR) { receivedChanges.add(it) }
            .use {

                // When
                automatedPackageListener.onAutomatedPackagesChanged(
                    AUTOMATING_PACKAGE,
                    listOf(otherAutomatedPackage),
                    USER_HANDLE,
                )

                // Then
                assertThat(receivedChanges).hasSize(1)
                receivedChanges.first().run {
                    assertThat(userHandle).isEqualTo(USER_HANDLE)
                    assertThat(addedPackages).containsExactly(otherAutomatedPackage)
                    assertThat(removedPackages).containsExactly(AUTOMATED_PACKAGE)
                }
            }
    }

    @Test
    fun onAutomatedPackagesChanged_withEmptyList_removesPackageEntry() {
        // Given
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            listOf(AUTOMATED_PACKAGE),
            USER_HANDLE,
        )
        assertThat(automationRepo.isPackageAutomated(USER_HANDLE, AUTOMATED_PACKAGE)).isTrue()
        assertThat(automationRepo.isPackageAutomated(USER_ID, AUTOMATED_PACKAGE)).isTrue()

        // When
        automatedPackageListener.onAutomatedPackagesChanged(
            AUTOMATING_PACKAGE,
            emptyList(),
            USER_HANDLE,
        )

        // Then
        assertThat(automationRepo.isPackageAutomated(USER_HANDLE, AUTOMATED_PACKAGE)).isFalse()
        assertThat(automationRepo.isPackageAutomated(USER_ID, AUTOMATED_PACKAGE)).isFalse()
    }

    companion object {
        private val USER_HANDLE = UserHandle.of(0)
        private val USER_ID = USER_HANDLE.identifier
        private const val AUTOMATING_PACKAGE = "com.test.automating"
        private const val AUTOMATED_PACKAGE = "com.test.automated1"
        private const val NOT_AUTOMATED_PACKAGE = "com.test.not.automated"
    }
}
