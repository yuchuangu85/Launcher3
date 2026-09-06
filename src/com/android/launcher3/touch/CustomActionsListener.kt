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

import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Launcher
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_LAUNCH
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_POPUP_MENU
import com.android.launcher3.touch.CustomActionsListener.Companion.ACTION_START_DRAG
import com.android.launcher3.touch.CustomActionsListener.Companion.hasFlags
import com.android.launcher3.util.ShortcutUtil
import com.android.launcher3.views.BubbleTextHolder
import com.android.launcher3.widget.LauncherAppWidgetHostView

/**
 * Interface for listening to custom actions performed on a view.
 *
 * This listener is used to abstract the handling of actions like launching, showing a popup menu,
 * or starting a drag operation, typically triggered by custom touch events or gestures.
 */
interface CustomActionsListener {
    /**
     * Called when custom actions should be performed on the view.
     *
     * @param view The view on which the actions are performed.
     * @param actionMask A bitmask representing the actions to be performed. See [Companion] for
     *   possible action flags.
     */
    fun performActions(view: View, actionMask: Int)

    /** Companion object containing action flags. */
    companion object {
        const val ACTION_POPUP_MENU = 1 shl 0
        const val ACTION_START_DRAG = 1 shl 1
        const val ACTION_LAUNCH = 1 shl 2

        /** Checks if the mask contains ALL of the specified flags */
        fun hasFlags(mask: Int, flags: Int): Boolean {
            return (mask and flags) == flags
        }
    }
}

private val View.logicalTarget: View
    get() = (parent as? BubbleTextHolder)?.let { it as? View } ?: this

private val View.asBubbleTextView: BubbleTextView?
    get() = this as? BubbleTextView ?: (this as? BubbleTextHolder)?.bubbleText

internal val ItemInfoWithIcon.isNotPinnable: Boolean
    get() = (runtimeStatusFlags and ItemInfoWithIcon.FLAG_NOT_PINNABLE) != 0

/** Base class for listeners that act on specific items (Icons, Folders, App Pairs). */
abstract class BaseItemCustomActionsListener : CustomActionsListener {

    final override fun performActions(view: View, actionMask: Int) {
        val target = view.logicalTarget
        val btv = target.asBubbleTextView

        when {
            hasFlags(actionMask, ACTION_POPUP_MENU or ACTION_START_DRAG) ->
                target.performLongClick()
            hasFlags(actionMask, ACTION_LAUNCH) -> target.performClick()
            hasFlags(actionMask, ACTION_POPUP_MENU) -> onOpenPopupMenu(target, btv)
            hasFlags(actionMask, ACTION_START_DRAG) -> onStartDrag(target, btv)
        }
    }

    /**
     * Called to open a popup menu for the given target.
     *
     * @param target The logical target view for the action. This could be a [BubbleTextHolder] or
     *   the view itself.
     * @param btv The [BubbleTextView] associated with the target, if it exists. Implementers should
     *   generally prefer using this view for operations that require a [BubbleTextView] or its
     *   [ItemInfo].
     */
    abstract fun onOpenPopupMenu(target: View, btv: BubbleTextView?)

    /**
     * Called to start a drag operation for the given target.
     *
     * @param target The logical target view for the action. This could be a [BubbleTextHolder] or
     *   the view itself.
     * @param btv The [BubbleTextView] associated with the target, if it exists. Implementers should
     *   generally prefer using this view for operations that require a [BubbleTextView] or its
     *   [ItemInfo].
     */
    abstract fun onStartDrag(target: View, btv: BubbleTextView?)
}

/** Implementation of [CustomActionsListener] for widgets. */
object WorkspaceWidgetCustomActionsListener : CustomActionsListener {
    override fun performActions(view: View, actionMask: Int) {
        view as? LauncherAppWidgetHostView ?: return
        when (actionMask) {
            ACTION_POPUP_MENU or ACTION_START_DRAG -> view.onLongClick(view)

            ACTION_POPUP_MENU -> {
                val launcher = Launcher.getLauncher(view.context)
                launcher.closeOpenViews()
                launcher.popupControllerForHomeScreenItems.show(view)
            }

            ACTION_START_DRAG -> {
                val launcher = Launcher.getLauncher(view.context)
                if (ItemLongClickListener.canStartDrag(launcher)) {
                    view.beforeDragStart()
                    val options = DragOptions().apply { isMouseDrag = true }
                    val tag = view.tag as? ItemInfo ?: return
                    ItemLongClickListener.beginDrag(view, launcher, tag, options)
                }
            }
        }
    }
}

/**
 * Implementation of [BaseItemCustomActionsListener] for workspace items (icons, folders, app
 * pairs).
 */
object WorkspaceItemCustomActionsListener : BaseItemCustomActionsListener() {
    override fun onOpenPopupMenu(target: View, btv: BubbleTextView?) {
        val viewForPopup = btv ?: target
        val tag = viewForPopup.tag as? ItemInfo ?: return

        val launcher = Launcher.getLauncher(viewForPopup.context)
        if (ShortcutUtil.supportsShortcuts(tag)) {
            launcher.popupControllerForAppIcons.show(viewForPopup)
        } else {
            launcher.popupControllerForHomeScreenItems.show(viewForPopup)
        }
    }

    override fun onStartDrag(target: View, btv: BubbleTextView?) {
        val viewForDrag = btv ?: target
        val tag = viewForDrag.tag as? ItemInfo ?: return

        val launcher = Launcher.getLauncher(viewForDrag.context)
        if (ItemLongClickListener.canStartDrag(launcher)) {
            val options = DragOptions().apply { isMouseDrag = true }
            // TODO: To trigger a drag and not show a popup at the same time, we currently
            // rely on setting isMouseDrag to true. Refactor beginDrag code not to rely on
            // ItemLongClickListener path.
            ItemLongClickListener.beginDrag(viewForDrag, launcher, tag, options)
        }
    }
}

/** Implementation of [BaseItemCustomActionsListener] for AllApps items. */
object AllAppsItemCustomActionsListener : BaseItemCustomActionsListener() {
    override fun onOpenPopupMenu(target: View, btv: BubbleTextView?) {
        if (btv == null) return

        // Allow the view to handle its own popup menu if it has a custom implementation.
        if (btv.showPopup() != null) return

        Launcher.getLauncher(btv.context).popupControllerForAppIcons.show(btv)
    }

    override fun onStartDrag(target: View, btv: BubbleTextView?) {
        if (btv == null) return

        val info = btv.tag as? ItemInfoWithIcon
        if (info?.isNotPinnable == true) return

        val launcher = Launcher.getLauncher(btv.context)
        if (!ItemLongClickListener.canStartAllAppsItemDrag(launcher)) return

        val dragController: DragController = launcher.dragController
        dragController.addDragSessionListener(
            object : DragController.DragSessionListener {
                override fun onDragSessionStart(dragObject: DragObject, options: DragOptions) {
                    btv.visibility = View.INVISIBLE
                }

                override fun onDragSessionEnd() {
                    btv.visibility = View.VISIBLE
                    dragController.removeDragSessionListener(this)
                }
            }
        )

        val dragOptions = DragOptions().apply { isMouseDrag = true }
        launcher.workspace.beginDragShared(btv, launcher.appsView, dragOptions)
    }
}
