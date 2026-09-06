/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_FILES_DELETE_VIA_DRAG_AND_DROP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_CANCEL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_REMOVE;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils;
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtilsKt;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.Preconditions;

public class DeleteDropTarget extends ButtonDropTarget {

    private final StatsLogManager mStatsLogManager;

    private StatsLogManager.LauncherEvent mLauncherEvent;

    public DeleteDropTarget(Context context) {
        this(context, null, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mStatsLogManager = StatsLogManager.newInstance(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setDrawable(R.drawable.ic_remove_no_shadow);
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        super.onDragStart(dragObject, options);
        setTextBasedOnDragSource(dragObject.dragInfo);
        setControlTypeBasedOnDragSource(dragObject.dragInfo);
    }

    /**
     * @return true for items that should have a "Remove" action in accessibility.
     */
    private boolean supportsAccessibilityDrop(ItemInfo info, View view) {
        return UtilitiesKt.isPersistedModelItem(info);
    }

    @Override
    public int getSupportedAccessibilityAction(ItemInfo info, View view) {
        if (supportsAccessibilityDrop(info, view)) {
            return getAccessibilityAction();
        }
        return LauncherAccessibilityDelegate.INVALID;
    }

    @Override
    public int getAccessibilityAction() {
        return LauncherAccessibilityDelegate.REMOVE;
    }

    @Override
    protected void setupItemInfo(ItemInfo info) {}

    @Override
    protected boolean supportsDrop(ItemInfo info) {
        return true;
    }

    /**
     * Set the drop target's text to either "Remove", "Delete permanently", "Move to trash" or
     * "Cancel" depending on the drag item.
     */
    private void setTextBasedOnDragSource(ItemInfo item) {
        if (!TextUtils.isEmpty(mText)) {
            int resId;
            if (canRemove(item)) {
                if (HomeScreenFilesUtilsKt.isFileSystemItem(item)) {
                    resId = HomeScreenFilesUtils.Companion.isTrashingEnabled()
                            ? R.string.home_screen_files_context_menu_move_to_trash_label
                            : R.string.home_screen_files_context_menu_delete_permanently_label;
                } else {
                    resId = R.string.remove_drop_target_label;
                }
            } else {
                resId = android.R.string.cancel;
            }
            mText = getResources().getString(resId);
            setContentDescription(mText);
            requestLayout();
        }
    }

    private boolean canRemove(ItemInfo item) {
        return item.id != ItemInfo.NO_ID;
    }

    /**
     * Set mControlType depending on the drag item.
     */
    private void setControlTypeBasedOnDragSource(ItemInfo item) {
        mLauncherEvent = item.id != ItemInfo.NO_ID ? LAUNCHER_ITEM_DROPPED_ON_REMOVE
                : LAUNCHER_ITEM_DROPPED_ON_CANCEL;
    }

    @Override
    public void onDrop(DragObject d, DragOptions options) {
        if (canRemove(d.dragInfo)) {
            mDropTargetHandler.prepareToUndoDelete(d.dragInfo);
        }
        super.onDrop(d, options);
        mStatsLogManager.logger().withInstanceId(d.logInstanceId)
                .log(mLauncherEvent);
    }

    @Override
    public void completeDrop(DragObject d) {
        ItemInfo item = d.dragInfo;
        if (canRemove(item)) {
            if (HomeScreenFilesUtilsKt.isFileSystemItem(item)) {
                mStatsLogManager.logger().withItemInfo(item).log(
                        LAUNCHER_HOME_SCREEN_FILES_DELETE_VIA_DRAG_AND_DROP);
            }
            mDropTargetHandler.onDeleteComplete(item, /* view */ null);
        } else if (mText == getResources().getText(R.string.remove_drop_target_label)) {
            Log.wtf("b/379606516", "If the drop target text is 'remove', then"
                    + " users should always be able to delete the item from launcher's db."
                    + " Invalid drag ItemInfo: " + item);
        }
    }

    /**
     * Removes the item from the workspace. If the view is not null, it also removes the view.
     */
    @Override
    public void onAccessibilityDrop(View view, ItemInfo item, int action) {
        Preconditions.assertTrue(action == getAccessibilityAction());
        // Remove the item from launcher and the db, we can ignore the containerInfo in this call
        // because we already remove the drag view from the folder (if the drag originated from
        // a folder) in Folder.beginDrag()
        if (canRemove(item)) {
            mDropTargetHandler.prepareToUndoDelete(item);
            mDropTargetHandler.onDeleteComplete(item, view);
        }
    }
}
