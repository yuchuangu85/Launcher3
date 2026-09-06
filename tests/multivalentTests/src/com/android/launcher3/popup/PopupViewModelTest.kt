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

package com.android.launcher3.popup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.popup.ui.ExpandedSection
import com.android.launcher3.popup.ui.MainSegmentsStyle
import com.android.launcher3.popup.ui.PopupItem
import com.android.launcher3.popup.ui.PopupViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PopupViewModelTest {

    private lateinit var viewModel: PopupViewModel
    private lateinit var launcherPrefs: LauncherPrefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        launcherPrefs = LauncherPrefs.get(context)
        launcherPrefs.put(LauncherPrefs.EXPANDED_POPUP_MENU_SECTION, ExpandedSection.SYSTEM)
        viewModel = PopupViewModel()
    }

    private val itemInfoWithIcon =
        object : ItemInfoWithIcon() {
            override fun clone(): ItemInfoWithIcon? {
                return null
            }
        }

    private val popupDataUi =
        PopupItem(
            iconResId = 0,
            labelResId = 0,
            popupAction = {},
            category = PopupCategory.SYSTEM_SHORTCUT,
        )
    private val popupDataUiFixed =
        PopupItem(
            iconResId = 0,
            labelResId = 0,
            popupAction = {},
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        )

    @Test
    fun init_noDeepShortcuts_fewSystemShortcuts_showsList() {
        val systemShortcuts = List(3) { popupDataUi }

        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 0,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.LIST)
        assertThat(state.compactSystemShortcuts).isEmpty()
        assertThat(state.standardSystemShortcuts.size).isEqualTo(3)
        assertThat(state.deepShortcuts.size).isEqualTo(0)
        assertThat(viewModel.expandedSection).isNull()
    }

    @Test
    fun init_noDeepShortcuts_manySystemShortcuts_showsListWithNoCompact() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(4) { popupDataUi })
        systemShortcuts.addAll(List(3) { popupDataUiFixed })

        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 0,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.LIST)
        assertThat(state.compactSystemShortcuts.size).isEqualTo(0)
        assertThat(state.standardSystemShortcuts.size).isEqualTo(7)
        assertThat(state.deepShortcuts.size).isEqualTo(0)
        assertThat(viewModel.expandedSection).isNull()
    }

    @Test
    fun init_withDeepShortcuts_totalLessThanOrEqualTo7_showsList() {
        val systemShortcuts = List(3) { popupDataUiFixed }

        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 4,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.LIST)
        assertThat(state.compactSystemShortcuts).isEmpty()
        assertThat(state.standardSystemShortcuts.size).isEqualTo(3)
        assertThat(state.deepShortcuts.size).isEqualTo(4)
        assertThat(viewModel.expandedSection).isNull()
    }

    @Test
    fun init_withDeepShortcuts_totalMoreThan7_showsAccordion_noCompact() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(4) { popupDataUi })
        systemShortcuts.addAll(List(2) { popupDataUiFixed })

        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 2,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.ACCORDION)
        assertThat(state.compactSystemShortcuts.size).isEqualTo(0)
        assertThat(state.standardSystemShortcuts.size).isEqualTo(6)
        assertThat(state.deepShortcuts.size).isEqualTo(2)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)
    }

    @Test
    fun init_withDeepShortcuts_totalMoreThan7_showsAccordion_someCompact() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(4) { popupDataUi })
        systemShortcuts.addAll(List(5) { popupDataUiFixed })

        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 3,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.ACCORDION)
        assertThat(state.compactSystemShortcuts.size).isEqualTo(4)
        assertThat(state.standardSystemShortcuts.size).isEqualTo(5)
        assertThat(state.deepShortcuts.size).isEqualTo(3)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)
    }

    @Test
    fun init_withDeepShortcuts_showsAccordion_allCompact_5_rows() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(3) { popupDataUi })
        systemShortcuts.addAll(List(3) { popupDataUiFixed })

        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 3,
            availableHeightDp = 270f,
            launcherPrefs = launcherPrefs,
        )

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.ACCORDION)
        assertThat(state.compactSystemShortcuts.size).isEqualTo(3)
        assertThat(state.standardSystemShortcuts.size).isEqualTo(3)
        assertThat(state.deepShortcuts.size).isEqualTo(3)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)
    }

    @Test
    fun init_withDeepShortcuts_showsAccordion_someCompact_6_rows() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(3) { popupDataUi })
        systemShortcuts.addAll(List(3) { popupDataUiFixed })

        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 3,
            availableHeightDp = 360f,
            launcherPrefs = launcherPrefs,
        )

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.ACCORDION)
        assertThat(state.compactSystemShortcuts.size).isEqualTo(2)
        assertThat(state.standardSystemShortcuts.size).isEqualTo(4)
        assertThat(state.deepShortcuts.size).isEqualTo(3)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)
    }

    @Test
    fun init_withDeepShortcuts_showsAccordion_noCompact_7_rows() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(3) { popupDataUi })
        systemShortcuts.addAll(List(3) { popupDataUiFixed })

        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 3,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )

        val state = viewModel.state
        assertThat(state.mainSegmentsStyle).isEqualTo(MainSegmentsStyle.ACCORDION)
        assertThat(state.compactSystemShortcuts.size).isEqualTo(0)
        assertThat(state.standardSystemShortcuts.size).isEqualTo(6)
        assertThat(state.deepShortcuts.size).isEqualTo(3)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)
    }

    @Test
    fun onDeepShortcutsLoaded_updatesDeepShortcutsList() {
        val systemShortcuts = List(2) { popupDataUiFixed }
        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 2,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )
        val deepShortcuts = listOf(itemInfoWithIcon, itemInfoWithIcon)

        viewModel.onDeepShortcutsLoaded(deepShortcuts)

        assertThat(viewModel.state.deepShortcuts[0]).isEqualTo(deepShortcuts[0])
        assertThat(viewModel.state.deepShortcuts[1]).isEqualTo(deepShortcuts[1])
    }

    @Test
    fun expandSection_notInAccordion_doesNothing() {
        val systemShortcuts = List(2) { popupDataUi }
        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 2,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )
        assertThat(viewModel.expandedSection).isNull()

        viewModel.expandSection(ExpandedSection.DEEP)

        assertThat(viewModel.expandedSection).isNull()
    }

    @Test
    fun expandSection_inAccordion_togglesSectionAndUpdatesPrefs() {
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(4) { popupDataUi })
        systemShortcuts.addAll(List(3) { popupDataUiFixed })
        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 4,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)

        viewModel.expandSection(ExpandedSection.DEEP)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.DEEP)
        assertThat(launcherPrefs.get(LauncherPrefs.EXPANDED_POPUP_MENU_SECTION))
            .isEqualTo(ExpandedSection.DEEP)

        viewModel.expandSection(ExpandedSection.DEEP)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.DEEP)

        viewModel.expandSection(ExpandedSection.SYSTEM)
        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.SYSTEM)
        assertThat(launcherPrefs.get(LauncherPrefs.EXPANDED_POPUP_MENU_SECTION))
            .isEqualTo(ExpandedSection.SYSTEM)
    }

    @Test
    fun init_withAccordion_usesInitialPrefValue() {
        launcherPrefs.put(LauncherPrefs.EXPANDED_POPUP_MENU_SECTION, ExpandedSection.DEEP)
        val systemShortcuts = mutableListOf<PopupItem>()
        systemShortcuts.addAll(List(4) { popupDataUi })
        systemShortcuts.addAll(List(3) { popupDataUiFixed })

        viewModel.init(
            systemShortcuts,
            deepShortcutCount = 4,
            availableHeightDp = 1000f,
            launcherPrefs = launcherPrefs,
        )

        assertThat(viewModel.expandedSection).isEqualTo(ExpandedSection.DEEP)
    }
}
