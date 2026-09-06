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

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;

import java.util.UUID;

/**
 * A hint that serves as a notice that another hint should be invalidated.
 *
 * <p>A component may want to invalidate a hint if there is no point in continuing to process it.
 * For example, there may be a hint regarding a notification circulating through the various
 * components; if that notification was dismissed, there may be no point in responding to it. This
 * is especially useful if the hint requires a large memory footprint, or is otherwise expensive to
 * hold onto. Only the process that reported a hint is allowed to invalidate it, and it should be
 * sent with the same {@link android.service.personalcontext.RenderToken}s as the original hint.
 *
 * <p>The system will automatically convert this hint into a
 * {@link android.service.personalcontext.insight.HintInvalidationInsight} and deliver it to
 * components. Components can subscribe to get hint invalidation by adding
 * {@link HintInvalidationHint} to a {@link HintFilter} ({@link HintFilter.Builder#addHintType}),
 * or by adding {@link android.service.personalcontext.insight.HintInvalidationInsight} to a
 * {@link android.service.personalcontext.insight.InsightFilter}
 * {@link android.service.personalcontext.insight.InsightFilter.Builder#addInsightType}). Components
 * that hold onto hints for long periods of time (particularly Understanders) should subscribe to
 * these and release hints that are invalidated.
 *
 * <p>Components must not rely on hint invalidation, and must be tolerant of receiving hints and
 * insights with a hint that has been invalidated.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class HintInvalidationHint extends ContextHint {
    private static final String KEY_HINT_ID = "hint_id";
    private final UUID mInvalidatedHintId;

    /**
     * Creates a new {@link HintInvalidationHint}.
     *
     * @hide
     */
    HintInvalidationHint(
            @NonNull ConstructorParams baseParams, @NonNull UUID invalidatedHintId) {
        super(baseParams);
        mInvalidatedHintId = invalidatedHintId;
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    HintInvalidationHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        this(
                baseParams,
                UUID.fromString(bundle.getString(KEY_HINT_ID)));
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_HINT_INVALIDATION;
    }

    /** Get the ID of the {@link ContextHint} that this hint invalidates. */
    @NonNull
    public UUID getInvalidatedHintId() {
        return mInvalidatedHintId;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        Bundle result = new Bundle();
        result.putString(KEY_HINT_ID, mInvalidatedHintId.toString());
        return result;
    }

    /**
     * Builder used to create a {@link HintInvalidationHint}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final UUID mInvalidatedHintId;

        /**
         * Creates an instance of {@link Builder} with the id of the {@link ContextHint} to be
         * invalidated.
         */
        public Builder(@NonNull UUID invalidatedHintId) {
            requireNonNull(invalidatedHintId, "invalidatedHintId must be provided");
            mInvalidatedHintId = invalidatedHintId;
        }

        /**
         * Creates an instance of {@link Builder} with the {@link ContextHint} to be invalidated.
         *
         * <p>This is equivalent to calling {@link Builder#Builder(UUID)} with the id of the hint
         * being provided.
         */
        public Builder(@NonNull ContextHint invalidatedHint) {
            this(requireNonNull(invalidatedHint, "invalidatedHint must be provided")
                    .getHintId());
        }

        /**
         * Adds a token to the resulting {@link HintInvalidationHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /**
         * @return the built {@link HintInvalidationHint}.
         */
        @NonNull
        public HintInvalidationHint build() {
            return new HintInvalidationHint(mBaseBuilder.build(), mInvalidatedHintId);
        }
    }
}
