/*
 * Copyright (C) 2026
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
package com.android.systemui.plugins;

import android.content.Context;

/** Minimal plugin API retained for Launcher classes in standalone builds. */
public interface Plugin {
    default int getVersion() {
        return 1;
    }

    default void onCreate(Context sysuiContext, Context pluginContext) {}

    default void onDestroy() {}
}
