/*
 * Copyright 2026 The Android Open Source Project
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

package android.service.personalcontext.hint;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.ContextInsight;

import java.util.UUID;

/**
 * A hint that contains a reference to a {@link ContextInsight}.
 *
 * <p>This is used when a component wants to signal that information is related to an insight that
 * was previously delivered.
 *
 * @see android.service.personalcontext.insight.interaction.ReturnHintReport
 * @see ActionableInsight#createReturnHintReport()
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightReferenceHint extends ContextHint {
    private static final String KEY_INSIGHT_ID = "insight_id";

    private final UUID mInsightId;

    /**
     * Creates a new {@link InsightReferenceHint}.
     *
     * @hide
     */
    public InsightReferenceHint(ContextInsight insight) {
        super(new ConstructorParams.Builder().build());

        mInsightId = insight.getInsightId();
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    InsightReferenceHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        super(baseParams);

        mInsightId = UUID.fromString(bundle.getString(KEY_INSIGHT_ID));
    }

    /** Get the {@link ContextInsight} contained in this hint. */
    @NonNull
    public UUID getInsightId() {
        return mInsightId;
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_INSIGHT_REFERENCE;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        Bundle result = new Bundle();
        result.putString(KEY_INSIGHT_ID, mInsightId.toString());
        return result;
    }
}
