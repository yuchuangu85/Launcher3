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

package android.service.personalcontext.insight;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.util.ArraySet;

import androidx.annotation.NonNull;

import com.google.android.collect.Sets;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Filter for insight renderers to indicate which insights they want to receive. Create a filter via
 * the {@link Builder} or use {@link InsightFilter#REQUIRE_RENDER_TOKEN} if your
 * renderer should only ever be invoked with a {@link android.service.personalcontext.RenderToken}.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@SystemApi
public final class InsightFilter implements Parcelable {
    /**
     * Pre-built {@link InsightFilter} that only allows insights with a RenderToken attached.
     */
    // Use a random UUID as the type name, so that no insights will match it.
    @NonNull
    public static final InsightFilter REQUIRE_RENDER_TOKEN =
            new Builder().addInsightType(UUID.randomUUID().toString()).build();

    private final Set<String> mAllowedTypes;

    /** @hide */
    public InsightFilter(Collection<String> allowedTypes) {
        mAllowedTypes = Collections.unmodifiableSet(new HashSet<>(allowedTypes));
    }

    @SuppressWarnings({"unchecked"})
    private InsightFilter(Parcel in) {
        mAllowedTypes =
                Collections.unmodifiableSet((Set<String>) in.readArraySet(/* classLoader= */ null));
    }

    /** @hide */
    public boolean isInterestedInInsight(ContextInsight insight) {
        // If we allow types, make sure the insight is one of the allowed types.
        if (!mAllowedTypes.isEmpty() && !mAllowedTypes.contains(insight.getInsightTypeName())) {
            return false;
        }

        // All checks passed.
        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeArraySet(new ArraySet<>(mAllowedTypes));
    }

    @NonNull
    public Set<String> getInsightTypes() {
        return mAllowedTypes;
    }

    @NonNull
    public static final Creator<InsightFilter> CREATOR =
            new Creator<>() {
                @Override
                public InsightFilter createFromParcel(Parcel in) {
                    return new InsightFilter(in);
                }

                @Override
                public InsightFilter[] newArray(int size) {
                    return new InsightFilter[size];
                }
            };

    /** Builder for a {@link InsightFilter}. */
    public static final class Builder {
        private final Set<String> mAllowedTypes = Sets.newHashSet();

        /**
         * Create a new instance of the Builder.
         */
        public Builder() { }

        /**
         * Adds a valid insight class to the filter. By default the filter will allow all insights
         * to be sent to the renderer, regardless of class. Adding one or more valid types changes
         * the filter so that each insight sent to the renderer will be one of those types.
         */
        @SuppressWarnings("MissingGetterMatchingBuilder")
        @NonNull
        public Builder addInsightType(@NonNull String insightType) {
            mAllowedTypes.add(insightType);
            return this;
        }

        /**
         * Adds a valid insight class to the filter. By default the filter will allow all insights
         * to be sent to the renderer, regardless of class. Adding one or more valid types changes
         * the filter so that each insight sent to the renderer will be one of those types.
         */
        @SuppressWarnings("MissingGetterMatchingBuilder")
        @NonNull
        public Builder addInsightType(
                @NonNull Class<? extends ContextInsight> insightClass) {
            return addInsightType(insightClass.getCanonicalName());
        }

        /** Builds the new InsightFilter. */
        @NonNull
        public InsightFilter build() {
            return new InsightFilter(mAllowedTypes);
        }
    }
}
