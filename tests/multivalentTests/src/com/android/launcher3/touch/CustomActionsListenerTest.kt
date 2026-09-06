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

package com.android.launcher3.touch

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.popup.PopupController
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_LAUNCH
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_POPUP_MENU
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_START_DRAG
import com.android.launcher3.views.BubbleTextHolder
import com.android.launcher3.widget.LauncherAppWidgetHostView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class CustomActionsListenerTest {

    private lateinit var context: Context
    private val view = mock<View>()
    private val bubbleTextView = mock<BubbleTextView>()
    private val launcher = mock<Launcher>()
    private val widgetHostView = mock<LauncherAppWidgetHostView>()
    private val popupController = mock<PopupController<Launcher>>()
    private lateinit var baseListener: TestBaseItemCustomActionsListener

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        baseListener = spy(TestBaseItemCustomActionsListener())
    }

    @Test
    fun performActions_actionLaunch_performsClick() {
        baseListener.performActions(view, ACTION_LAUNCH)
        verify(view).performClick()
    }

    @Test
    fun performActions_actionLaunch_withBubbleTextHolderParent_performsClickOnParent() {
        val parent = mock<TestBubbleTextHolderView>()
        whenever(view.parent).thenReturn(parent)

        baseListener.performActions(view, ACTION_LAUNCH)
        verify(parent).performClick()
    }

    @Test
    fun performActions_actionPopupAndDrag_performsLongClick() {
        baseListener.performActions(view, ACTION_POPUP_MENU or ACTION_START_DRAG)
        verify(view).performLongClick()
    }

    @Test
    fun performActions_actionPopupAndDrag_withBubbleTextHolderParent_performsLongClickOnParent() {
        val parent = mock<TestBubbleTextHolderView>()
        whenever(view.parent).thenReturn(parent)

        baseListener.performActions(view, ACTION_POPUP_MENU or ACTION_START_DRAG)
        verify(parent).performLongClick()
    }

    @Test
    fun performActions_actionPopupMenu_callsOnOpenPopupMenu() {
        baseListener.performActions(view, ACTION_POPUP_MENU)
        verify(baseListener).onOpenPopupMenu(view, null)
    }

    @Test
    fun performActions_actionStartDrag_callsOnStartDrag() {
        baseListener.performActions(view, ACTION_START_DRAG)
        verify(baseListener).onStartDrag(view, null)
    }

    @Test
    fun performActions_withBubbleTextView_callsWithBtv() {
        val parent = mock<TestBubbleTextHolderView>()
        whenever(view.parent).thenReturn(parent)
        whenever(parent.bubbleText).thenReturn(bubbleTextView)

        baseListener.performActions(view, ACTION_POPUP_MENU)
        verify(baseListener).onOpenPopupMenu(parent, bubbleTextView)
    }

    @Test
    fun workspaceWidget_actionPopupMenu_showsPopup() {
        whenever(widgetHostView.context).thenReturn(launcher)
        whenever(launcher.popupControllerForHomeScreenItems).thenReturn(popupController)

        WorkspaceWidgetCustomActionsListener.performActions(widgetHostView, ACTION_POPUP_MENU)

        verify(launcher).closeOpenViews()
        verify(popupController).show(widgetHostView)
    }

    @Test
    fun workspaceWidget_actionPopupAndDrag_callsOnLongClick() {
        WorkspaceWidgetCustomActionsListener.performActions(
            widgetHostView,
            ACTION_POPUP_MENU or ACTION_START_DRAG,
        )
        verify(widgetHostView).onLongClick(widgetHostView)
    }

    @Test
    fun workspaceWidget_invalidViewType_doesNothing() {
        WorkspaceWidgetCustomActionsListener.performActions(view, ACTION_POPUP_MENU)
        // No crash, just returns
    }

    class TestBaseItemCustomActionsListener : BaseItemCustomActionsListener() {
        override fun onOpenPopupMenu(target: View, btv: BubbleTextView?) {}

        override fun onStartDrag(target: View, btv: BubbleTextView?) {}
    }

    abstract class TestBubbleTextHolderView(context: Context) :
        ViewGroup(context), BubbleTextHolder
}
