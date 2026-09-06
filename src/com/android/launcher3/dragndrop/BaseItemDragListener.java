/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import static com.android.launcher3.Flags.enableSystemDrag;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
import static com.android.launcher3.states.RotationHelper.REQUEST_LOCK;
import static com.android.launcher3.states.RotationHelper.REQUEST_NONE;

import android.content.ClipDescription;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ContextTracker.SchedulerCallback;
import com.android.launcher3.views.ActivityContext;

import java.util.UUID;

/**
 * {@link DragSource} for handling drop from a different window.
 *
 * @param <T> The type for the context associated with the sequence.
 */
public abstract class BaseItemDragListener<T extends ActivityContext>
        implements DragController.SystemDragHandler, View.OnDragListener, DragSource,
        DragOptions.PreDragCondition, SchedulerCallback<T> {

    private static final String TAG = "BaseItemDragListener";

    /** Mime type for a deep shortcut dragged within Launcher, e.g. between Home and Taskbar. */
    public static final String MIME_TYPE_INTERNAL_APP_SHORTCUT =
            "vnd.android.launcher3/app-shortcut";

    /** Mime type for an app folder dragged within Launcher, e.g. between Home and Taskbar. */
    public static final String MIME_TYPE_INTERNAL_FOLDER =
            "vnd.android.launcher3/app-folder";

    /** Mime type for an app group item dragged within Launcher, e.g. between Home and Taskbar. */
    public static final String MIME_TYPE_INTERNAL_APP_GROUP =
            "vnd.android.launcher3/app-group";

    /** Mime type for an app dragged within Launcher , e.g. between Home and Taskbar. */
    public static final String MIME_TYPE_INTERNAL_APP_ACTIVITY = "vnd.android.launcher3/app-item";

    private static final String MIME_TYPE_PREFIX = "com.android.launcher3.drag_and_drop/";
    public static final String EXTRA_PIN_ITEM_DRAG_LISTENER = "pin_item_drag_listener";

    /** Key for an Extra ItemInfo wrapped by {@link com.android.launcher3.util.ObjectWrapper}. */
    public static final String EXTRA_WRAPPED_ITEM_INFO = "wrapped_item_info";

    /** Returns an internal MIME type that should be used as drag clip description for an item. */
    @Nullable
    public static String getInternalMimeTypeForItem(ItemInfo item) {
        return switch (item.itemType) {
            case ITEM_TYPE_APPLICATION -> MIME_TYPE_INTERNAL_APP_ACTIVITY;
            case ITEM_TYPE_DEEP_SHORTCUT -> MIME_TYPE_INTERNAL_APP_SHORTCUT;
            case ITEM_TYPE_FOLDER -> MIME_TYPE_INTERNAL_FOLDER;
            case ITEM_TYPE_APP_GROUP -> MIME_TYPE_INTERNAL_APP_GROUP;
            default -> null;
        };
    }

    // Position of preview relative to the touch location
    private final Rect mPreviewRect;

    private final int mPreviewBitmapWidth;
    private final int mPreviewViewWidth;

    // Randomly generated id used to verify the drag event.
    private final String mId;

    protected T mContext;
    private DragController mDragController;

    public BaseItemDragListener(Rect previewRect, int previewBitmapWidth, int previewViewWidth) {
        mPreviewRect = previewRect;
        mPreviewBitmapWidth = previewBitmapWidth;
        mPreviewViewWidth = previewViewWidth;
        mId = UUID.randomUUID().toString();
    }

    public String getMimeType() {
        return MIME_TYPE_PREFIX + mId;
    }

    @Override
    public boolean init(T context, boolean isHomeStarted) {
        initInternal(context, isHomeStarted, /* closeAllOpenViews= */ true);
        return false;
    }

    protected void initInternal(T context, boolean isHomeStarted, boolean closeAllOpenViews) {
        if (closeAllOpenViews) {
            AbstractFloatingView.closeAllOpenViews(context, /* animate= */ isHomeStarted);
        }

        if (context instanceof Launcher launcher) {
            launcher.getRotationHelper().setStateHandlerRequest(REQUEST_LOCK);
        }

        mContext = context;
        mDragController = context.getDragController();

        if (!enableSystemDrag()) {
            mContext.getDragLayer().setOnDragListener(this);
        } else if (mDragController != null) {
            mDragController.addSystemDragHandler(this);
        }
    }

    @Override
    public boolean onDrag(DragEvent event) {
        if (mContext == null || mDragController == null) {
            postCleanup();
            return false;
        }

        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED
                || (!mDragController.isDragging()
                        && event.getAction() == DragEvent.ACTION_DRAG_ENTERED)) {
            if (onDragStart(event)) {
                return true;
            } else {
                postCleanup();
                return false;
            }
        }
        return enableSystemDrag() || mDragController.onDragEvent(event);
    }

    @Override
    public boolean onDrag(View view, DragEvent event) {
        return onDrag(event);
    }

    protected boolean onDragStart(DragEvent event) {
        return onDragStart(event, this);
    }

    protected boolean onDragStart(DragEvent event, DragOptions.PreDragCondition preDragCondition) {
        ClipDescription desc =  event.getClipDescription();
        if (desc == null || !desc.hasMimeType(getMimeType())) {
            Log.e(TAG, "Someone started a dragAndDrop before us.");
            return false;
        }

        Point downPos = new Point((int) event.getX(), (int) event.getY());
        DragOptions options = new DragOptions();
        options.simulatedDndStartPoint = downPos;
        options.preDragCondition = preDragCondition;

        // We use drag event position as the screenPos for the preview image. Since mPreviewRect
        // already includes the view position relative to the drag event on the source window,
        // and the absolute position (position relative to the screen) of drag event is same
        // across windows, using drag position here give a good estimate for relative position
        // to source window.
        startDrag(new Rect(mPreviewRect), mPreviewBitmapWidth, mPreviewViewWidth, downPos, options);
        return true;
    }

    protected abstract void startDrag(Rect previewRect, int previewBitmapWidth,
            int previewViewWidth, Point screenPos, DragOptions options);

    @Override
    public boolean shouldStartDrag(double distanceDragged) {
        // Stay in pre-drag mode, if workspace is locked.
        return !(mContext instanceof Launcher launcher) || !launcher.isWorkspaceLocked();
    }

    @Override
    public void onPreDragStart(DragObject dragObject) {
        // The predrag starts when the workspace is not yet loaded. In some cases we set
        // the dragLayer alpha to 0 to have a nice fade-in animation. But that will prevent the
        // dragView from being visible. Instead just skip the fade-in animation here.
        mContext.getDragLayer().setAlpha(1);
        dragObject.dragView.setAlpha(.5f);
    }

    @Override
    public void onPreDragEnd(DragObject dragObject, boolean dragStarted) {
        if (dragStarted) {
            dragObject.dragView.setAlpha(1f);
        }
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {
        postCleanup();
    }

    protected void postCleanup() {
        if (mContext instanceof Launcher launcher) {
            // Remove any drag params from the launcher intent since the drag operation is complete.
            Intent newIntent = new Intent(launcher.getIntent());
            newIntent.removeExtra(EXTRA_PIN_ITEM_DRAG_LISTENER);
            launcher.setIntent(newIntent);
        }

        new Handler(Looper.getMainLooper()).post(this::removeListener);
    }

    public void removeListener() {
        final boolean enableSystemDrag = enableSystemDrag();

        if (mContext != null) {
            if (mContext instanceof Launcher launcher) {
                launcher.getRotationHelper().setStateHandlerRequest(REQUEST_NONE);
            }
            if (!enableSystemDrag) {
                mContext.getDragLayer().setOnDragListener(null);
            }
        }

        if (enableSystemDrag && mDragController != null) {
            mDragController.removeSystemDragHandler(this);
        }
    }
}
