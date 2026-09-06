/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.TestActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Test for AbstractFloatingViewHelper */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AbstractFloatingViewHelperTest {
    @get:Rule val activityContext = TestActivityContext()

    private lateinit var dragLayer: BaseDragLayer<*>
    private lateinit var view: View
    private lateinit var folderView: AbstractFloatingView
    private lateinit var taskMenuView: AbstractFloatingView

    private val abstractFloatingViewHelper = AbstractFloatingViewHelper

    private class FakeFolderView(context: Context) : AbstractFloatingView(context, null) {
        override fun isOfType(type: Int): Boolean = (type and TYPE_FOLDER) != 0

        override fun onControllerInterceptTouchEvent(ev: MotionEvent) = false

        override fun handleClose(animate: Boolean) {
            (parent as? ViewGroup)?.removeView(this)
        }
    }

    private class FakeTaskMenuView(context: Context) : AbstractFloatingView(context, null) {
        override fun isOfType(type: Int): Boolean = (type and TYPE_TASK_MENU) != 0

        override fun onControllerInterceptTouchEvent(ev: MotionEvent) = false

        override fun handleClose(animate: Boolean) {
            (parent as? ViewGroup)?.removeView(this)
        }
    }

    @Before
    fun setup() {
        dragLayer = activityContext.dragLayer
        view = View(activityContext)
        folderView = FakeFolderView(activityContext)
        taskMenuView = FakeTaskMenuView(activityContext)

        dragLayer.addView(view)
        dragLayer.addView(folderView)
        dragLayer.addView(taskMenuView)
    }

    @Test
    fun closeOpenViews_all() {
        abstractFloatingViewHelper.closeOpenViews(
            activityContext,
            false,
            AbstractFloatingView.TYPE_ALL,
        )

        assertThat(view.parent).isNotNull()
        assertThat(folderView.parent).isNull()
        assertThat(taskMenuView.parent).isNull()
    }

    @Test
    fun closeOpenViews_taskMenu() {
        abstractFloatingViewHelper.closeOpenViews(
            activityContext,
            false,
            AbstractFloatingView.TYPE_TASK_MENU,
        )

        assertThat(view.parent).isNotNull()
        assertThat(folderView.parent).isNotNull()
        assertThat(taskMenuView.parent).isNull()
    }

    @Test
    fun closeOpenViews_other() {
        abstractFloatingViewHelper.closeOpenViews(
            activityContext,
            false,
            AbstractFloatingView.TYPE_PIN_IME_POPUP,
        )

        assertThat(view.parent).isNotNull()
        assertThat(folderView.parent).isNotNull()
        assertThat(taskMenuView.parent).isNotNull()
    }

    @Test
    fun closeOpenViews_folderAndTaskMenu() {
        abstractFloatingViewHelper.closeOpenViews(
            activityContext,
            false,
            AbstractFloatingView.TYPE_FOLDER or AbstractFloatingView.TYPE_TASK_MENU,
        )

        assertThat(view.parent).isNotNull()
        assertThat(folderView.parent).isNull()
        assertThat(taskMenuView.parent).isNull()
    }
}
