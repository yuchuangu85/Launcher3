/*
 * Copyright (C) 2026 The Android Open Source Project
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
package android.service.personalcontext.hint.autofill;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.WorkerThread;
import android.app.assist.AssistStructure;
import android.graphics.Rect;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.AutofillInlineRequestHint;

/**
 * A class for interfacing with
 * {@link android.service.personalcontext.hint.AutofillInlineRequestHint}, providing autofill
 * specific operations over the hint.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class AutofillInlineRequestHintConsumer {
    final AutofillInlineRequestHint mHint;

    /**
     * Default constructor for creating a {@link AutofillInlineRequestHintConsumer} for working with
     * a {@link AutofillInlineRequestHint}.
     *
     * @param hint The {@link AutofillInlineRequestHint} to provide operations on.
     */
    public AutofillInlineRequestHintConsumer(@NonNull AutofillInlineRequestHint hint) {
        mHint = requireNonNull(hint);
    }

    /**
     * Returns the {@link AssistStructure.ViewNode} for the node focused by autofill in the given
     * {@link AutofillInlineRequestHint}.
     */
    @WorkerThread
    @NonNull
    public AssistStructure.ViewNode fetchFocusedViewNode() {
        return mHint.getAugmentedAutofillProxy().fetchFocusedViewNode(mHint.getFocusedId());
    }

    /**
     * Returns the coordinates of the input field view that autofill suggestions are being generated
     * for in the given {@link AutofillInlineRequestHint}.
     */
    @WorkerThread
    @NonNull
    public Rect fetchViewCoordinates() {
        return mHint.getAugmentedAutofillProxy().fetchViewCoordinates(mHint.getFocusedId());
    }
}
