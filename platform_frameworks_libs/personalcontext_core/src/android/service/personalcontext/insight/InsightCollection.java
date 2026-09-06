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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.interaction.AttributionDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An insight that is a collection of other {@link ContextInsight}s. This may be used to group
 * insights that should be processed together by the framework.
 *
 * <p>The origin hints and tokens for this collection are derived from its child elements.
 * Therefore, child elements should not contain hints with conflicting render tokens. If
 * conflicting render tokens are present, an {@link IllegalStateException} will be thrown when the
 * render token is accessed. This may result in the system dropping the entire collection.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightCollection extends ContextInsight implements Iterable<ContextInsight> {
    private static final String KEY_INSIGHTS = "key_insights";

    private final List<ContextInsight> mInsights;

    /** Private constructor. Used by the builder. */
    private InsightCollection(
            @NonNull ContextInsight.ConstructorParams baseParams,
            @NonNull Collection<ContextInsight> insights) {
        super(baseParams);
        mInsights = Collections.unmodifiableList(new ArrayList<>(insights));
    }

    /**
     * Internal constructor only for use by {@link ContextInsight#createInsightFromBundle(Bundle)}.
     */
    InsightCollection(@NonNull ContextInsight.ConstructorParams baseParams, @NonNull Bundle b) {
        this(
                baseParams,
                ContextInsightWrapper.unwrapList(
                        Objects.requireNonNullElse(
                                b.getParcelableArrayList(KEY_INSIGHTS, ContextInsightWrapper.class),
                                new ArrayList<>())));
    }

    @NonNull
    @Override
    Bundle toBundleImpl(boolean includeHints) {
        final Collection<ContextInsight> insights = new ArrayList<>();
        if (includeHints) {
            insights.addAll(mInsights);
        } else {
            for (final ContextInsight insight : mInsights) {
                insights.add(insight.copyWithoutHints());
            }
        }

        final Bundle b = new Bundle();
        b.putParcelableArrayList(
                KEY_INSIGHTS, new ArrayList<>(ContextInsightWrapper.wrapList(insights)));

        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InsightCollection)) return false;
        if (!super.equals(o)) return false;
        InsightCollection that = (InsightCollection) o;
        return mInsights.equals(that.mInsights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mInsights);
    }

    @Override
    public String toString() {
        return "InsightCollection{" + "mInsights=" + mInsights + ", " + super.toString() + '}';
    }

    @Override
    @NonNull
    public Set<PublishedContextHint> getOriginHints() {
        final Set<PublishedContextHint> originHints = new HashSet<>();
        for (final ContextInsight insight : mInsights) {
            originHints.addAll(insight.getOriginHints());
        }
        return originHints;
    }

    @Override
    @NonNull
    public Set<Token> getTokens() {
        final Set<Token> tokens = new HashSet<>();
        for (final ContextInsight insight : mInsights) {
            tokens.addAll(insight.getTokens());
        }
        return tokens;
    }

    /** Returns a copy of the list of insights in this collection. */
    @NonNull
    public List<ContextInsight> getInsights() {
        return new ArrayList<>(mInsights);
    }

    /** Returns an iterator over the insights in this collection. */
    @Override
    @NonNull
    public Iterator<ContextInsight> iterator() {
        return mInsights.iterator();
    }

    /** @hide */
    @Override
    @InsightType
    public int getInsightType() {
        return INSIGHT_TYPE_COLLECTION;
    }

    /** @hide */
    @Override
    public void accept(@NonNull InsightVisitor visitor, int index) {
        visitor.visit(this, index);
    }

    /** @hide */
    @NonNull
    @Override
    public Collection<ContextInsight> getChildren() {
        return Collections.unmodifiableList(mInsights);
    }

    /**
     * Builder for {@link InsightCollection}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final List<ContextInsight> mInsights = new ArrayList<>();
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();

        /** Creates a new builder for an insight collection. */
        public Builder() {}

        /**
         * Creates a new builder for an insight collection.
         *
         * @param insights the initial collection of insights.
         */
        public Builder(@NonNull Collection<ContextInsight> insights) {
            Objects.requireNonNull(insights, "insights cannot be null");
            for (ContextInsight insight : insights) {
                mInsights.add(
                        Objects.requireNonNull(insight, "insight collection cannot contain nulls"));
            }
        }

        /**
         * Adds an insight to the collection.
         *
         * @param insight the insight to add.
         */
        @NonNull
        public Builder addInsight(@NonNull ContextInsight insight) {
            mInsights.add(Objects.requireNonNull(insight, "insight cannot be null"));
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
         * Builds the insight collection.
         *
         * @return the insight collection.
         * @throws IllegalStateException if the collection is empty.
         */
        @NonNull
        public InsightCollection build() {
            if (mInsights.isEmpty()) {
                throw new IllegalStateException("InsightCollection cannot be empty");
            }

            return new InsightCollection(mBaseBuilder.build(), mInsights);
        }
    }
}
