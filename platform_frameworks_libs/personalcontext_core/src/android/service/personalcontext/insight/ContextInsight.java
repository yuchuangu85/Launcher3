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
import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.hint.PublishedContextHintWrapper;
import android.service.personalcontext.insight.interaction.AttributionDetails;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Abstract base class for insights. Subclasses will provide concrete implementations. The context
 * engine flow will produce these insights, which will ultimately make their way to insight
 * renderers, where they will be rendered as UI to the user.
 *
 * <p>Users of this class can use instanceof to determine the type of the insight.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class ContextInsight {
    private static final String TAG = "ContextInsight";

    private static final String KEY_INSIGHT_ID = "key_insight_id";
    private static final String KEY_INSIGHT_TYPE = "key_insight_type";
    private static final String KEY_ORIGIN_HINTS = "key_origin_hints";
    private static final String KEY_TOKENS = "key_tokens";
    private static final String KEY_INSIGHT_DATA = "key_insight_data";
    private static final String KEY_ATTRIBUTION = "key_attribution";
    private static final String KEY_CREATION_TIME = "key_creation_time";

    /**
     * Enumeration of insight types.
     *
     * @hide
     */
    @IntDef(
            prefix = {"INSIGHT_TYPE_"},
            value = {
                INSIGHT_TYPE_ERROR,
                INSIGHT_TYPE_BUNDLE,
                INSIGHT_TYPE_ACTIONABLE,
                INSIGHT_TYPE_DISPLAY,
                INSIGHT_TYPE_COLLECTION,
                INSIGHT_TYPE_HINT_INVALIDATION,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InsightType {}

    /** Type identifier for an error insight (to return when there is an unparceling error). */
    static final int INSIGHT_TYPE_ERROR = -1;

    /**
     * Type identifier for {@link BundleInsight}.
     *
     * @hide
     */
    @VisibleForTesting
    public static final int INSIGHT_TYPE_BUNDLE = 1;

    /** Type identifier for {@link ActionableInsight}. */
    static final int INSIGHT_TYPE_ACTIONABLE = 2;

    /** Type identifier for {@link DisplayInsight}. */
    static final int INSIGHT_TYPE_DISPLAY = 3;

    /** Type identifier for {@link InsightCollection}. */
    static final int INSIGHT_TYPE_COLLECTION = 4;

    /** Type identifier for {@link InsightCollection}. */
    static final int INSIGHT_TYPE_HINT_INVALIDATION = 5;

    /**
     * Object returned when there is an unparcelling error.
     * @hide
     */
    @NonNull
    protected static final ContextInsight ERROR_INSIGHT = new ContextInsight(
            new ConstructorParams(
                    /* originHints= */ Collections.emptySet(),
                    /* tokens= */ Collections.emptySet(),
                    /* attributionDetails= */ null)) {
        @Override
        @InsightType public int getInsightType() {
            return INSIGHT_TYPE_ERROR;
        }

        @NonNull
        @Override
        Bundle toBundleImpl(boolean includeHints) {
            return new Bundle();
        }

        @Override
        public void accept(@NonNull InsightVisitor visitor, int index) {
            visitor.visitUnknown(this, index);
        }
    };

    private final UUID mId;
    private final Set<PublishedContextHint> mOriginHints;
    private final Set<Token> mTokens;
    private final Instant mCreationTime;
    private final AttributionDetails mAttributionDetails;

    /**
     * Internal constructor for insights. This should be called by subclasses in their public
     * constructors.
     *
     * @hide
     */
    ContextInsight(@NonNull ConstructorParams params) {
        mId = params.mId;
        mOriginHints = Collections.unmodifiableSet(new HashSet<>(params.mOriginHints));
        mTokens = Collections.unmodifiableSet(new HashSet<>(params.mTokens));
        mCreationTime = params.mCreationTime;
        mAttributionDetails = params.mAttributionDetails;
    }

    /**
     * Returns the {@link InsightType} of this hint.
     *
     * @hide
     */
    @InsightType
    public abstract int getInsightType();

    /**
     * Gets the type name of the insight. For {@link BundleInsight} this is the type name that was
     * provided in the builder. For all other insights it is the canonical class name.
     */
    @NonNull
    public String getInsightTypeName() {
        return getClass().getCanonicalName();
    }

    /**
     * Accepts a visitor.
     *
     * @param visitor The visitor to accept.
     *
     * @hide
     */
    public abstract void accept(@NonNull InsightVisitor visitor, int index);

    /**
     * Returns the children of this insight.
     *
     * @return A collection of child {@link ContextInsight} objects. If there are no children, this
     *     method should return an empty collection, not null.
     *
     * @hide
     */
    @NonNull
    public Collection<ContextInsight> getChildren() {
        return Collections.emptyList();
    }

    /**
     * Returns the unique identifier for this insight.
     */
    @NonNull
    public final UUID getInsightId() {
        return mId;
    }

    /** Returns the set of {@link ContextHint}s that were used to generate this insight. */
    @NonNull
    public Set<PublishedContextHint> getOriginHints() {
        return mOriginHints;
    }

    /**
     * Gets the {@link RenderToken} from the insight's {@link ContextHint}s. Throws an
     * {@link IllegalStateException} if hints have conflicting {@link RenderToken}s.
     * @hide
     */
    @Nullable
    public Set<RenderToken> getRenderTokens() {
        final Set<RenderToken> allRenderTokens = new HashSet<>();
        for (PublishedContextHint hint : getOriginHints()) {
            allRenderTokens.addAll(hint.getRenderTokens());
        }

        return allRenderTokens;
    }

    /** Returns the set of tokens that were added to this insight. */
    @NonNull
    public Set<Token> getTokens() {
        return mTokens;
    }

    /** Gets the time this hint was created. */
    @NonNull
    public final Instant getCreationTime() {
        return mCreationTime;
    }

    @NonNull abstract Bundle toBundleImpl(boolean includeHints);

    /**
     * Make a deep copy of the insight without hints.
     * @hide
     */
    @NonNull
    public ContextInsight copyWithoutHints() {
        return ContextInsight.createInsightFromBundle(this.toBundle(/* includeHints= */ false));
    }

    /**
     * Return the {@link Bundle} representation of this insight's data.
     * @hide
     */
    @TestApi
    @NonNull
    public Bundle toBundle() {
        return toBundle(/* includeHints= */ true);
    }

    /**
     * Return the {@link Bundle} representation of this insight's data.
     *
     * @param includeHints whether to include hints in the bundle.
     *
     * @hide
     */
    @NonNull
    public Bundle toBundle(boolean includeHints) {
        final Bundle b = new Bundle();
        b.putInt(KEY_INSIGHT_TYPE, getInsightType());
        b.putString(KEY_INSIGHT_ID, mId.toString());
        b.putParcelableArrayList(
                KEY_ORIGIN_HINTS,
                includeHints
                        ? new ArrayList<>(PublishedContextHintWrapper.wrapList(mOriginHints))
                        : new ArrayList<>());
        b.putParcelableArrayList(KEY_TOKENS, new ArrayList<>(mTokens));
        b.putBundle(KEY_INSIGHT_DATA, toBundleImpl(includeHints));
        b.putLong(KEY_CREATION_TIME, mCreationTime.toEpochMilli());
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContextInsight)) return false;

        final ContextInsight other = (ContextInsight) o;
        return Objects.equals(mId, other.mId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId);
    }

    @Override
    public String toString() {
        return "ContextInsight{"
                + "mId="
                + mId
                + ", mOriginHints="
                + mOriginHints
                + '}';
    }

    /**
     * Unbundles an insight into the correct subclass of insight based on the insight type.
     *
     * @hide
     */
    @TestApi
    @NonNull
    public static ContextInsight createInsightFromBundle(@Nullable Bundle bundle) {
        if (bundle == null) {
            return ERROR_INSIGHT;
        }

        final int type = bundle.getInt(KEY_INSIGHT_TYPE, INSIGHT_TYPE_ERROR);
        final Bundle data = bundle.getBundle(KEY_INSIGHT_DATA);
        final ConstructorParams constructorParams = new ConstructorParams(
                UUID.fromString(bundle.getString(KEY_INSIGHT_ID)),
                PublishedContextHintWrapper.unwrapList(
                        bundle.getParcelableArrayList(
                                KEY_ORIGIN_HINTS, PublishedContextHintWrapper.class)),
                bundle.getParcelableArrayList(KEY_TOKENS, Token.class),
                Instant.ofEpochMilli(bundle.getLong(KEY_CREATION_TIME)),
                bundle.getParcelable(KEY_ATTRIBUTION, AttributionDetails.class));

        try {
            return switch (type) {
                case INSIGHT_TYPE_BUNDLE -> new BundleInsight(constructorParams, data);
                case INSIGHT_TYPE_ACTIONABLE -> new ActionableInsight(constructorParams, data);
                case INSIGHT_TYPE_DISPLAY -> new DisplayInsight(constructorParams, data);
                case INSIGHT_TYPE_COLLECTION -> new InsightCollection(constructorParams, data);
                case INSIGHT_TYPE_HINT_INVALIDATION -> new HintInvalidationInsight(
                        constructorParams, data);
                default -> ERROR_INSIGHT;
            };
        } catch (Exception e) {
            Log.e(TAG, "Error creating insight", e);
            return ERROR_INSIGHT;
        }
    }

    /**
     * Indicates whether the insight has attribution details that can be shown. This can be
     * checked before showing a UI to call {@link PersonalContextManager#showAttribution} to see if
     * attribution is available.
     *
     * @see AttributionDetails
     */
    public boolean hasAttribution() {
        return mAttributionDetails != null;
    }

    /** @hide */
    public AttributionDetails getAttributionDetails() {
        return mAttributionDetails;
    }

    /**
     * Parameters used to create a new {@link ContextInsight}.
     *
     * @hide
     */
    static class ConstructorParams {
        private final UUID mId;
        private final Collection<PublishedContextHint> mOriginHints;
        private final Collection<Token> mTokens;
        private final Instant mCreationTime;
        private final AttributionDetails mAttributionDetails;

        private ConstructorParams(
                Collection<PublishedContextHint> originHints,
                Collection<Token> tokens,
                AttributionDetails attributionDetails) {
            this(
                    UUID.randomUUID(),
                    originHints,
                    tokens,
                    Instant.now(),
                    attributionDetails);
        }

        private ConstructorParams(
                UUID id,
                Collection<PublishedContextHint> originHints,
                Collection<Token> tokens,
                Instant creationTime,
                AttributionDetails attributionDetails) {
            mId = id;
            mOriginHints = originHints;
            mTokens = tokens;
            mCreationTime = creationTime;
            mAttributionDetails = attributionDetails;
        }

        static final class Builder {
            private final Set<PublishedContextHint> mOriginHints = new HashSet<>();
            private final Set<Token> mTokens = new HashSet<>();
            private AttributionDetails mAttributionDetails;
            private UUID mOriginatingComponentId;

            /**
             * Adds an origin {@link ContextHint} to the resulting {@link ContextInsight}. This hint
             * will be used in determining how the insight should be delivered based on present
             * {@link android.service.personalcontext.RenderToken}. It can also be potentially used
             * to determine how the insight was formulated (attribution).
             *
             * @param hint the origin {@link ContextHint} to add
             */
            @NonNull
            Builder addOriginHint(@NonNull PublishedContextHint hint) {
                mOriginHints.add(requireNonNull(hint));
                return this;
            }

            /**
             * Adds a tag to the resulting {@link ContextInsight}.
             *
             * @param token the token to add
             */
            @NonNull
            Builder addToken(@NonNull Token token) {
                mTokens.add(requireNonNull(token));
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
                mAttributionDetails = attributionDetails;
                return this;
            }

            ConstructorParams build() {
                return new ConstructorParams(
                        mOriginHints,
                        mTokens,
                        mAttributionDetails);
            }
        }
    }
}
