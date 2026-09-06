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

package android.service.personalcontext.insight.interaction;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.hint.InsightReferenceHint;
import android.service.personalcontext.insight.ActionableInsight;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates info about an insight and how to route a new insight back to the same renderers.
 *
 * <p>Typically this is handed off to another piece of code that uses it to look up additional data.
 * Once the lookup is complete, the new data is encapsulated in one or more {@link ContextHint}s
 * and sent back via {@link PersonalContextManager#publishTriggeringHint(ReturnHintReport, List)}.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ReturnHintReport implements Parcelable {
    private final InsightReferenceHint mInsightReferenceHint;
    private final List<RenderToken> mRenderTokens;

    /** @hide */
    public ReturnHintReport(@NonNull ActionableInsight insight) {
        mInsightReferenceHint = new InsightReferenceHint(insight);
        mRenderTokens = new ArrayList<>(requireNonNull(insight.getRenderTokens()));
    }

    private ReturnHintReport(@NonNull Parcel in) {
        final ContextHintWrapper hintWrapper =
                requireNonNull(in.readTypedObject(ContextHintWrapper.CREATOR));

        mInsightReferenceHint = (InsightReferenceHint) hintWrapper.getContextHint();
        mRenderTokens = in.readParcelableList(new ArrayList<>(), null, RenderToken.class);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(new ContextHintWrapper(mInsightReferenceHint), 0);
        dest.writeParcelableList(mRenderTokens, 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<ReturnHintReport> CREATOR = new Creator<>() {
        @Override
        public ReturnHintReport createFromParcel(Parcel in) {
            return new ReturnHintReport(in);
        }

        @Override
        public ReturnHintReport[] newArray(int size) {
            return new ReturnHintReport[size];
        }
    };

    /** @hide */
    public InsightReferenceHint getInsightReferenceHint() {
        return mInsightReferenceHint;
    }

    /** @hide */
    public List<RenderToken> getRenderTokens() {
        return mRenderTokens;
    }
}
