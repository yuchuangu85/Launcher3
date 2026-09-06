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
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.HintInvalidationHint;
import android.service.personalcontext.hint.PublishedContextHint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An insight that contains information about a hint that should be invalidated.
 *
 * @see HintInvalidationHint
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class HintInvalidationInsight extends ContextInsight {
    private final PublishedContextHint mInvalidationHint;

    /** Private constructor used by {@link Builder}. */
    private HintInvalidationInsight(
            @NonNull ConstructorParams baseParams) {
        super(baseParams);

        final List<PublishedContextHint> hintList = new ArrayList<>(getOriginHints());
        if (hintList.size() != 1
                || (!(hintList.getFirst().getContextHint() instanceof HintInvalidationHint))) {
            throw new IllegalArgumentException(
                    "HintInvalidationInsight must have a single HintInvalidationHint origin hint");
        }

        mInvalidationHint = hintList.getFirst();
    }

    /**
     * Internal constructor only for use by {@link ContextInsight#createInsightFromBundle(Bundle)}.
     */
    HintInvalidationInsight(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        this(baseParams); // Origin hints provide the only necessary data.
    }

    @NonNull
    @Override
    Bundle toBundleImpl(boolean includeHints) {
        return new Bundle();
    }

    /** Returns the ID of the {@link ContextHint} that was invalidated. */
    @NonNull
    public UUID getInvalidatedHintId() {
        return ((HintInvalidationHint) mInvalidationHint.getContextHint()).getInvalidatedHintId();
    }

    /** Confirms whether the provided hint was invalidated by this insight. */
    public boolean isHintInvalidated(@NonNull PublishedContextHint hint) {
        return getInvalidatedHintId().equals(hint.getContextHint().getHintId())
                && mInvalidationHint.getOriginatingPackage().equals(hint.getOriginatingPackage());
    }

    /** @hide */
    @Override
    @InsightType
    public int getInsightType() {
        return INSIGHT_TYPE_HINT_INVALIDATION;
    }

    /** @hide */
    @Override
    public void accept(@NonNull InsightVisitor visitor, int index) {
        visitor.visit(this, index);
    }

    @Override
    public String toString() {
        return "HintInvalidationInsight{"
                + "mInvalidationHint="
                + mInvalidationHint
                + ", super="
                + super.toString()
                + "}";
    }

    /**
     * Builder for {@link HintInvalidationInsight}.
     *
     * Only the built-in {@link com.android.server.personalcontext.HintInvalidationUnderstander} is
     * allowed to create this.
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder;

        /**
         * Creates a new builder for a {@link HintInvalidationInsight}.
         *
         * @param invalidationHint the hint with information about which hint to invalidate.
         */
        public Builder(@NonNull PublishedContextHint invalidationHint) {
            requireNonNull(invalidationHint);
            if (!(invalidationHint.getContextHint() instanceof HintInvalidationHint)) {
                throw new IllegalArgumentException(
                        "invalidationHint must contain a HintInvalidationHint");
            }
            mBaseBuilder = new ConstructorParams.Builder()
                    .addOriginHint(invalidationHint);
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

        /** Builds the {@link HintInvalidationInsight}. */
        @NonNull
        public HintInvalidationInsight build() {
            return new HintInvalidationInsight(mBaseBuilder.build());
        }
    }
}
