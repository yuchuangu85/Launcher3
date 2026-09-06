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

import android.annotation.FlaggedApi;
import android.annotation.TestApi;
import android.app.assist.AssistStructure;
import android.graphics.Rect;
import android.os.IBinder;
import android.service.personalcontext.Flags;
import android.view.autofill.AutofillId;

import androidx.annotation.NonNull;

/**
 * Interface that abstracts the communication with the augmented autofill service.
 *
 * @hide
 */
@TestApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public interface AugmentedAutofillProxy {
    /** Returns the {@link AssistStructure.ViewNode} for the node focused by autofill. */
    @NonNull
    AssistStructure.ViewNode fetchFocusedViewNode(@NonNull AutofillId focusedId);

    /** Returns the coordinates of the input field view focused by autofill. */
    @NonNull
    Rect fetchViewCoordinates(@NonNull AutofillId focusedId);

    /** Returns the binder for the client. */
    @NonNull
    IBinder asBinder();
}
