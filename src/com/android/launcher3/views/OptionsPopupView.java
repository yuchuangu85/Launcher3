/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.views;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.launcher3.R;
import com.android.launcher3.logging.StatsLogManager.EventEnum;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.shortcuts.DeepShortcutView;

import java.util.List;

/**
 * Popup shown on long pressing an empty space in launcher
 *
 * @param <T> The context showing this popup.
 */
public class OptionsPopupView<T extends Context & ActivityContext> extends ArrowPopup<T>
        implements OnClickListener, OnLongClickListener {

    private final ArrayMap<View, OptionItem> mItemMap = new ArrayMap<>();
    private RectF mTargetRect;
    private boolean mShouldAddArrow;

    public OptionsPopupView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OptionsPopupView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setTargetRect(RectF targetRect) {
        mTargetRect = targetRect;
    }

    @Override
    public void onClick(View view) {
        handleViewClick(view);
    }

    @Override
    public boolean onLongClick(View view) {
        return handleViewClick(view);
    }

    private boolean handleViewClick(View view) {
        OptionItem item = mItemMap.get(view);
        if (item == null) {
            return false;
        }
        if (item.eventId.getId() > 0) {
            mActivityContext.getStatsLogManager().logger().log(item.eventId);
        }
        if (item.clickListener.onLongClick(view)) {
            close(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (getPopupContainer().isEventOverView(this, ev)) {
            return false;
        }
        close(true);
        return true;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_OPTIONS_POPUP) != 0;
    }

    public void setShouldAddArrow(boolean shouldAddArrow) {
        mShouldAddArrow = shouldAddArrow;
    }

    @Override
    protected boolean shouldAddArrow() {
        return mShouldAddArrow;
    }

    @Override
    protected void getTargetObjectLocation(Rect outPos) {
        mTargetRect.roundOut(outPos);
    }

    @Override
    public void assignMarginsAndBackgrounds(ViewGroup viewGroup) {
        assignMarginsAndBackgrounds(viewGroup, mColors[0]);
    }

    @Override
    protected void assignMarginsAndBackgrounds(ViewGroup viewGroup, int backgroundColor) {
        super.assignMarginsAndBackgrounds(viewGroup, backgroundColor);
        // last shortcut doesn't need bottom margin
        final int count = viewGroup.getChildCount() - 1;
        for (int i = 0; i < count; i++) {
            // These are shortcuts and not shortcut containers, but they still need bottom margin
            MarginLayoutParams mlp = (MarginLayoutParams) viewGroup.getChildAt(i).getLayoutParams();
            mlp.bottomMargin = mChildContainerMargin;
        }
    }

    public static void showNoReturn(
            ActivityContext activityContext,
            RectF targetRect,
            List<OptionItem> items,
            boolean shouldAddArrow) {
        show(activityContext, targetRect, items, shouldAddArrow);
    }

    public static <T extends Context & ActivityContext> OptionsPopupView<T> show(
            ActivityContext activityContext,
            RectF targetRect,
            List<OptionItem> items,
            boolean shouldAddArrow) {
        return show(activityContext, targetRect, items, shouldAddArrow, 0 /* width */);
    }

    @Nullable
    private static <T extends Context & ActivityContext> OptionsPopupView<T> show(
            @Nullable ActivityContext activityContext,
            RectF targetRect,
            List<OptionItem> items,
            boolean shouldAddArrow,
            int width) {
        if (activityContext == null) {
            return null;
        }
        OptionsPopupView<T> popup = (OptionsPopupView<T>) activityContext.getLayoutInflater()
                .inflate(R.layout.longpress_options_menu, activityContext.getDragLayer(), false);
        popup.mTargetRect = targetRect;
        popup.setShouldAddArrow(shouldAddArrow);

        for (OptionItem item : items) {
            DeepShortcutView view = popup.inflateAndAdd(R.layout.system_shortcut, popup);
            if (width > 0) {
                view.getLayoutParams().width = width;
            }
            view.getIconView().setBackgroundDrawable(item.icon);
            view.getBubbleText().setText(item.label);
            view.setOnClickListener(popup);
            view.setOnLongClickListener(popup);
            popup.mItemMap.put(view, item);
        }

        popup.show();
        return popup;
    }

    public static class OptionItem {

        // Used to create AccessibilityNodeInfo in AccessibilityActionsView.java.
        public final int labelRes;

        public final CharSequence label;
        public final Drawable icon;
        public final EventEnum eventId;
        public final OnLongClickListener clickListener;

        public OptionItem(Context context, int labelRes, int iconRes, EventEnum eventId,
                OnLongClickListener clickListener) {
            this.labelRes = labelRes;
            this.label = context.getText(labelRes);
            this.icon = ContextCompat.getDrawable(context, iconRes);
            this.eventId = eventId;
            this.clickListener = clickListener;
        }

        public OptionItem(CharSequence label, Drawable icon, EventEnum eventId,
                OnLongClickListener clickListener) {
            this.labelRes = 0;
            this.label = label;
            this.icon = icon;
            this.eventId = eventId;
            this.clickListener = clickListener;
        }
    }
}
