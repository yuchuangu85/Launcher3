/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.popup;

import android.content.Context;
import android.view.View;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.picker.model.WidgetPickerDataProvider;
import com.android.launcher3.widget.picker.model.WidgetPickerDataProvider.WidgetPickerDataChangeListener;

import kotlin.Unit;

/**
 * Utility class to handle updates while the popup is visible (like widgets and
 * notification changes)
 *
 * @param <T> The activity on which the popup shows
 */
public abstract class PopupLiveUpdateHandler<T extends Context & ActivityContext> implements
        WidgetPickerDataChangeListener, View.OnAttachStateChangeListener {

    protected final T mContext;
    protected final PopupContainerWithArrow<T> mPopupContainerWithArrow;

    private SafeCloseable mCleanupTask;

    public PopupLiveUpdateHandler(
            T context, PopupContainerWithArrow<T> popupContainerWithArrow) {
        mContext = context;
        mPopupContainerWithArrow = popupContainerWithArrow;
    }

    private void completeCleanupTask() {
        if (mCleanupTask != null) {
            mCleanupTask.close();
            mCleanupTask = null;
        }
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        completeCleanupTask();
        if (LauncherModel.useModelRepositoryBinding()) {
            mCleanupTask = LauncherComponentProvider.get(mContext).getHomeScreenRepository()
                    .getAllWidgets().forEach(mContext.getUiExecutor(), d -> {
                        onWidgetsBound();
                        return Unit.INSTANCE;
                    });
        } else {
            WidgetPickerDataProvider widgetsDataProvider = mContext.getWidgetPickerDataProvider();
            if (widgetsDataProvider != null) {
                mCleanupTask = widgetsDataProvider.setChangeListener(this);
            }
        }
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        completeCleanupTask();
    }

    @Override
    public void onWidgetsBound() {} // NO_OP

}
