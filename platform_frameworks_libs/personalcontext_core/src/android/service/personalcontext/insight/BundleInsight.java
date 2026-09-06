/*
 * Copyright (C) 2025 The Android Open Source Project
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

/**
 * An insight that stores arbitrary data in a {@link Bundle}. Should only be used if there is no
 * appropriate insight type already defined.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class BundleInsight extends ContextInsight {
    private static final String KEY_DATA = "data";
    private static final String KEY_TYPE = "type";

    private final Bundle mDataBundle;
    private final String mInsightTypeName;

    /**
     * Internal constructor only for use by {@link ContextInsight#createInsightFromBundle(Bundle)}
     * and {@link Builder}.
     */
    BundleInsight(
            @NonNull ContextInsight.ConstructorParams baseParams,
            @NonNull Bundle bundle) {
        this(
                baseParams,
                requireNonNull(bundle.getBundle(KEY_DATA)),
                requireNonNull(bundle.getString(KEY_TYPE)));
    }

    private BundleInsight(
            @NonNull ContextInsight.ConstructorParams baseParams,
            @NonNull Bundle data,
            @NonNull String insightTypeName) {
        super(baseParams);
        mDataBundle = requireNonNull(data);
        mInsightTypeName = requireNonNull(insightTypeName);
    }

    /** @hide */
    @Override
    @InsightType
    public int getInsightType() {
        return INSIGHT_TYPE_BUNDLE;
    }

    /** Provides the insightTypeName used when creating the BundleInsight. */
    @Override
    @NonNull
    public String getInsightTypeName() {
        return mInsightTypeName;
    }

    /** @hide */
    @Override
    public void accept(@NonNull InsightVisitor visitor, int index) {
        visitor.visit(this, index);
    }

    /** Returns the insight's data {@link Bundle}. */
    @NonNull
    public Bundle getDataBundle() {
        return mDataBundle;
    }

    @Override
    @NonNull
    Bundle toBundleImpl(boolean includeHints) {
        Bundle result = new Bundle();
        result.putString(KEY_TYPE, getInsightTypeName());
        result.putBundle(KEY_DATA, mDataBundle);
        return result;
    }

    @Override
    public String toString() {
        return super.toString() + ";" + getInsightTypeName();
    }

    /**
     * Builder for {@link BundleInsight}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final Bundle mDataBundle = new Bundle();
        private String mInsightTypeName = BundleInsight.class.getCanonicalName();

        /**
         * Creates a new builder for {@link BundleInsight}.
         *
         * The {@code insightTypeName} provided should be namespaced, and should be unique enough
         * that code can interpret the contents of the Bundle inside this insight without ambiguity.
         * e.g. "com.mycompany.personalcontext.insight.MyAction". An {@link InsightFilter} can
         * filter insights based on this type name with
         * {@link InsightFilter.Builder#addInsightType(String)}. If no type name is set,
         * {@code "android.service.personalcontext.insight.BundleInsight"} will be used as the type
         * name for this insight. An {@link InsightFilter} can filter for {@link BundleInsight}s
         * without an explicit type name with
         * {@code InsightFilter.addInsightType(BundleInsight.class}.
         */
        @NonNull
        public Builder setInsightTypeName(@Nullable String insightTypeName) {
            mInsightTypeName = insightTypeName == null
                    ? BundleInsight.class.getCanonicalName() : insightTypeName;
            return this;
        }

        /**
         * Adds an origin {@link ContextHint} to the resulting {@link BundleInsight}. This hint
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

        /**
         * Sets the data in the given {@link Bundle} to the resulting {@link BundleInsight}'s data
         * bundle.
         *
         * @param dataBundle the {@link Bundle} containing the data to set
         */
        @NonNull
        public Builder setDataBundle(@NonNull Bundle dataBundle) {
            requireNonNull(dataBundle);
            mDataBundle.clear();
            mDataBundle.putAll(dataBundle);
            return this;
        }

        /** Create and return a new {@link BundleInsight}. */
        @NonNull
        public BundleInsight build() {
            return new BundleInsight(mBaseBuilder.build(), mDataBundle, mInsightTypeName);
        }
    }
}
