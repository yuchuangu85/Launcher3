/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.quickstep;

import com.android.quickstep.util.GroupTask;

import java.util.function.Predicate;

/**
 * Keeps track of the state of {@code RecentsView}.
 *
 * <p> More specifically, used for keeping track of the state of filters applied on tasks
 * in {@code RecentsView} for multi-instance management.
 */
public class RecentsFilterState {
    // default filter that returns true for any input
    public static final Predicate<GroupTask> EMPTY_FILTER = groupTask -> true;

    /** Returns a predicate for filtering out GroupTasks by displayId. */
    public static Predicate<GroupTask> getDisplayIdFilter(int displayId) {
        return groupTask -> groupTask.matchesDisplayId(displayId);
    }
}
