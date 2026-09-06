/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.insight;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.interaction.AttributionDetails;

import java.util.Objects;

/**
 * An insight that contains information for display purposes with no particular action associated.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class DisplayInsight extends ContextInsight {
    private static final String KEY_DISPLAY_DETAILS = "key_display_details";

    private final InsightDisplayDetails mDisplayDetails;

    /** Private constructor used by {@link Builder}. */
    private DisplayInsight(
            @NonNull ContextInsight.ConstructorParams baseParams,
            @NonNull InsightDisplayDetails displayDetails) {
        super(requireNonNull(baseParams));
        mDisplayDetails = requireNonNull(displayDetails);
    }

    /**
     * Internal constructor only for use by {@link ContextInsight#createInsightFromBundle(Bundle)}.
     */
    DisplayInsight(@NonNull ContextInsight.ConstructorParams baseParams, @NonNull Bundle bundle) {
        this(
                requireNonNull(baseParams),
                requireNonNull(
                        bundle.getParcelable(KEY_DISPLAY_DETAILS, InsightDisplayDetails.class)));
    }

    @NonNull
    @Override
    Bundle toBundleImpl(boolean includeHints) {
        final Bundle b = new Bundle();
        b.putParcelable(KEY_DISPLAY_DETAILS, mDisplayDetails);
        return b;
    }

    /** Returns the {@link InsightDisplayDetails} of this insight. */
    @NonNull
    public InsightDisplayDetails getDetails() {
        return mDisplayDetails;
    }

    /** @hide */
    @Override
    @InsightType
    public int getInsightType() {
        return INSIGHT_TYPE_DISPLAY;
    }

    /** @hide */
    @Override
    public void accept(@NonNull InsightVisitor visitor, int index) {
        visitor.visit(this, index);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DisplayInsight that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(mDisplayDetails, that.mDisplayDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mDisplayDetails);
    }

    @Override
    public String toString() {
        return "DisplayInsight{"
                + "mDisplayDetails="
                + mDisplayDetails
                + ", super="
                + super.toString()
                + "}";
    }

    /**
     * Builder for {@link DisplayInsight}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final InsightDisplayDetails mDisplayDetails;

        /**
         * Creates a new builder for a display insight. By default, no hints are present. They can
         * be added using {@link #addOriginHint(PublishedContextHint)}.
         *
         * @param displayDetails the display details of the insight.
         */
        public Builder(
                @NonNull InsightDisplayDetails displayDetails) {
            mDisplayDetails = requireNonNull(displayDetails);
        }

        /**
         * Adds an origin {@link ContextHint} to the resulting {@link DisplayInsight}. This hint
         * will be used in determining how the insight should be delivered based on present
         * {@link android.service.personalcontext.RenderToken}. It can also be potentially used
         * to determine how the insight was formulated (attribution).
         *
         * @param hint the origin {@link ContextHint} to add
         */
        @NonNull
        public Builder addOriginHint(@NonNull PublishedContextHint hint) {
            mBaseBuilder.addOriginHint(requireNonNull(hint));
            return this;
        }

        /**
         * Adds a token to the resulting {@link ContextInsight}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(requireNonNull(token));
            return this;
        }

        /**
         * Sets the attribution details that can be shown to the user.
         *
         * @param attributionDetails Details to show user when they ask for how this insight was
         *                           generated.
         */
        @NonNull
        Builder setAttributionDetails(@Nullable AttributionDetails attributionDetails) {
            mBaseBuilder.setAttributionDetails(attributionDetails);
            return this;
        }

        /** Builds the {@link DisplayInsight}. */
        @NonNull
        public DisplayInsight build() {
            return new DisplayInsight(mBaseBuilder.build(), mDisplayDetails);
        }
    }
}
