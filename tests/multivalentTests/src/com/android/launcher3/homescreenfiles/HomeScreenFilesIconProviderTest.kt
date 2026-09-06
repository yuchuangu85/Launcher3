/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.homescreenfiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.homescreenfiles.HomeScreenFilesIconProviderImpl.FolderIconDelegate
import com.android.launcher3.icons.IconShape
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.SafeCloseable
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

/** Tests for [HomeScreenFilesIconProvider]. */
@RunWith(AndroidJUnit4::class)
class HomeScreenFilesIconProviderTest {
    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var idp: InvariantDeviceProfile
    @Mock private lateinit var lifecycle: DaggerSingletonTracker
    private lateinit var provider: HomeScreenFilesIconProviderImpl
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        idp.iconBitmapSize = 24
        provider = HomeScreenFilesIconProviderImpl(context, idp, lifecycle)
    }

    @Test
    fun testFolderIcon() {
        // The same folder icon should be reused across all home screen folder instances.
        assertThat(provider.folderIcon).isSameInstanceAs(provider.folderIcon)

        // The icon should have a custom shape and should paint using the [FolderIconDelegate].
        // Theming is not supported for the folder icon.
        assertThat(provider.folderIcon.defaultIconShape).isNotEqualTo(IconShape.EMPTY)
        assertTrue(provider.folderIcon.newIcon(context, 0).delegate is FolderIconDelegate)
        assertThat(provider.folderIcon.themedBitmap).isNull()

        // The icon's custom shape is dependent on the [idp] and should update dynamically.
        assertThat(provider.folderIcon.defaultIconShape.pathSize).isEqualTo(idp.iconBitmapSize)
        idp.iconBitmapSize *= 2
        val listener = argumentCaptor<OnIDPChangeListener>()
        verify(idp).addOnChangeListener(listener.capture())
        listener.firstValue.onIdpChanged(/* modelPropertiesChanged= */ true)
        assertThat(provider.folderIcon.defaultIconShape.pathSize).isEqualTo(idp.iconBitmapSize)

        // The listener registered to handle changes to [idp] should be cleaned up.
        val closeable = argumentCaptor<SafeCloseable>()
        verify(lifecycle).addCloseable(closeable.capture())
        closeable.firstValue.close()
        verify(idp).removeOnChangeListener(listener.firstValue)
    }
}
