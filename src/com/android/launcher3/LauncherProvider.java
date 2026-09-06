/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.util.ContentProviderProxy;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Launcher data provider API.
 *
 * @see com.android.launcher3.model.ModelProxyProvider
 */
public class LauncherProvider extends ContentProviderProxy {

    /** $ adb shell dumpsys activity provider com.android.launcher3 */
    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        LauncherComponentProvider.get(getContext()).getDumpManager().dump("", writer, args);
    }

    @Nullable
    @Override
    public ProxyProvider getProxy(@NonNull Context ctx) {
        return LauncherComponentProvider.get(ctx).getModelProxyProvider();
    }
}
