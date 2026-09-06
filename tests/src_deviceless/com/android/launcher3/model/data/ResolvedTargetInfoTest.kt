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

package com.android.launcher3.model.data

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.ComponentKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ResolvedTargetInfoTest {

    private val testTargetActivityComponent =
        ComponentName("com.example.target", "com.example.target.TargetActivity")
    private val testComponent = ComponentName("com.example.app", "com.example.app.MainActivity")
    private val dummyComponent = ComponentName("com.example.app", "com.example.app.DummyActivity")
    private val testUser: UserHandle = UserHandle.getUserHandleForUid(0)
    private val testIntent = Intent(Intent.ACTION_MAIN).setComponent(testComponent)
    private val testTargetIntent =
        Intent(Intent.ACTION_MAIN).setComponent(testTargetActivityComponent)
    private val testNullComponentIntent = Intent(Intent.ACTION_MAIN).setComponent(null)
    private val dummyIntent =
        Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName("com.example.app", "com.example.app.DummyActivity"))

    @Test
    fun `getTargetComponentKey targetActivityComponentName isNotNull returnsTargetActivityComponentKey`() {
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)
        val expectedKey = ComponentKey(testTargetActivityComponent, testUser)

        val actualKey = resolvedInfo.getTargetComponentKey()

        assertThat(actualKey).isNotNull()
        assertThat(actualKey!!.componentName).isEqualTo(testTargetActivityComponent)
        assertThat(actualKey).isEqualTo(expectedKey)
        assertThat(actualKey.user).isEqualTo(testUser)
    }

    @Test
    fun `getTargetComponentKey targetActivityComponentName isNull componentName isNotNull returnsComponentKey`() {
        val resolvedInfo = ResolvedTargetInfo(null, testComponent, testUser)
        val expectedKey = ComponentKey(testComponent, testUser)

        val actualKey = resolvedInfo.getTargetComponentKey()

        assertThat(actualKey).isNotNull()
        assertThat(actualKey!!.componentName).isEqualTo(testComponent)
        assertThat(actualKey.user).isEqualTo(testUser)
        assertThat(actualKey).isEqualTo(expectedKey)
    }

    @Test
    fun `getTargetComponentKey both targetActivityComponentName and componentName areNull returnsNull`() {
        val resolvedInfo = ResolvedTargetInfo(null, null, testUser)

        val actualKey = resolvedInfo.getTargetComponentKey()

        assertThat(actualKey).isNull()
    }

    @Test
    fun `matchTaskLaunchActivity different userId`() {
        // Test matchTaskLaunchActivity returns false when taskKey.userId is different from
        // ResolvedTargetInfo.user.identifier.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskLaunchActivity(testIntent, UserHandle.of(1))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskLaunchActivity with different user objects but same identifier`() {
        // Test matchTaskLaunchActivity returns true when taskKey.userId is the same as
        // ResolvedTargetInfo.user.identifier, even if the UserHandle objects are different
        // instances, and other conditions for matching are met.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskLaunchActivity(testIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }

    @Test
    fun `matchTaskLaunchActivity with baseIntent matching targetActivityComponentName`() {
        // Test matchTaskLaunchActivity returns true when taskKey.userId matches and
        // taskKey.baseIntent
        // matches ResolvedTargetInfo's targetActivityComponentName (when
        // targetActivityComponentName is not null).
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskLaunchActivity(testTargetIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }

    @Test
    fun `matchTaskLaunchActivity with baseIntent matching componentName`() {
        // Test matchTaskLaunchActivity returns true when taskKey.userId matches and
        // taskKey.baseIntent
        // matches ResolvedTargetInfo's componentName (when targetActivityComponentName is null and
        // componentName is not null).
        val resolvedInfo = ResolvedTargetInfo(null, testComponent, testUser)

        val matches = resolvedInfo.matchTaskLaunchActivity(testIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }

    @Test
    fun `matchTaskLaunchActivity with baseIntent not matching`() {
        // Test matchTaskLaunchActivity returns false when taskKey.userId matches but
        // taskKey.baseIntent does not match either targetActivityComponentName or componentName.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskLaunchActivity(dummyIntent, UserHandle.of(0))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskLaunchActivity with baseIntent and null targetComponentKey`() {
        // Test matchTaskLaunchActivity returns false when taskKey.userId matches,
        // taskKey.baseIntent is
        // not null, but getTargetComponentKey() returns null (both targetActivityComponentName and
        // componentName are null).
        val resolvedInfo = ResolvedTargetInfo(null, null, testUser)

        val matches = resolvedInfo.matchTaskLaunchActivity(dummyIntent, UserHandle.of(0))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskLaunchActivity with baseIntent component not matching`() {
        // Test matchTaskLaunchActivity returns false when taskKey.userId matches,
        // taskKey.baseIntent.component does not match either componentName or
        // targetActivityComponentName.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskLaunchActivity(dummyIntent, UserHandle.of(0))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskLaunchActivity with null baseActivity and null baseIntent component`() {
        // Test matchTaskLaunchActivity returns false when taskKey.userId matches,
        // taskKey.baseIntent.component is null.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches =
            resolvedInfo.matchTaskLaunchActivity(testNullComponentIntent, UserHandle.of(0))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskLaunchActivity with baseIntent when only targetActivityComponentName is set`() {
        // Test matchTaskLaunchActivity with taskKey.baseIntent when ResolvedTargetInfo has
        // targetActivityComponentName set but componentName is null.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, null, testUser)

        val matches1 = resolvedInfo.matchTaskLaunchActivity(testTargetIntent, UserHandle.of(0))
        val matches2 = resolvedInfo.matchTaskLaunchActivity(dummyIntent, UserHandle.of(0))

        assertThat(matches1).isTrue()
        assertThat(matches2).isFalse()
    }

    @Test
    fun `matchTaskLaunchActivity with baseActivity when only componentName is set`() {
        // Test matchTaskLaunchActivity with taskKey.baseIntent when ResolvedTargetInfo has
        // componentName set but targetActivityComponentName is null.
        val resolvedInfo = ResolvedTargetInfo(null, testComponent, testUser)

        val matches1 = resolvedInfo.matchTaskLaunchActivity(dummyIntent, UserHandle.of(0))
        val matches2 = resolvedInfo.matchTaskLaunchActivity(testIntent, UserHandle.of(0))

        assertThat(matches1).isFalse()
        assertThat(matches2).isTrue()
    }
}
