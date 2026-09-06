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

package android.service.personalcontext.hint;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;

/**
 * A hint that stores arbitrary data in a {@link Bundle}. Should only be used if there is no
 * appropriate hint type already defined.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class BundleHint extends ContextHint {
    private static final String KEY_DATA = "data";
    private static final String KEY_TYPE = "type";

    /**
     * {@link Bundle} of arbitrary data provided to the personal context engine.
     */
    private final Bundle mDataBundle;

    private final String mHintTypeName;

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     */
    BundleHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        this(
                baseParams,
                requireNonNull(bundle.getBundle(KEY_DATA)),
                requireNonNull(bundle.getString(KEY_TYPE)));
    }

    private BundleHint(
            @NonNull ConstructorParams baseParams,
            @NonNull Bundle data,
            @NonNull String typeName) {
        super(baseParams);
        mDataBundle = data;
        mHintTypeName = typeName;
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_BUNDLE;
    }

    /** Provides the hintTypeName used when creating the BundleHint. */
    @Override
    @NonNull
    public String getHintTypeName() {
        return mHintTypeName;
    }

    /**
     * Returns the data bundle contained within this hint.
     */
    @NonNull
    public Bundle getDataBundle() {
        return mDataBundle;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        Bundle result = new Bundle();
        result.putString(KEY_TYPE, getHintTypeName());
        result.putBundle(KEY_DATA, mDataBundle);
        return result;
    }

    @Override
    public String toString() {
        return super.toString() + ";" + getHintTypeName();
    }

    /** @hide */
    @Override
    public void writeToSignatureParcel(@NonNull Parcel dest) {
        dest.writeString(mHintTypeName);
    }

    /**
     * Builder used to create a {@link BundleHint}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final Bundle mBundle = new Bundle();
        private String mHintTypeName = BundleHint.class.getCanonicalName();

        /**
         * Creates a new builder for {@link BundleHint}.
         *
         * The {@code hintTypeName} provided should be namespaced, and should be unique enough that
         * code can interpret the contents of the Bundle inside this hint without ambiguity.
         * e.g. "com.mycompany.personalcontext.hint.MyData". A {@link HintFilter} can filter hints
         * based on this type name with {@link HintFilter.Builder#addHintType(String, boolean)}. If
         * no type name is set, {@code "android.service.personalcontext.hint.BundleHint"} will be
         * used as the type name for this insight. A {@link HintFilter} can filter for
         * {@link BundleHint}s without an explicit type name with
         * {@code HintFilter.addHintType(BundleHint.class}.
         */
        @NonNull
        public Builder setHintTypeName(@Nullable String hintTypeName) {
            mHintTypeName =
                    hintTypeName == null ? BundleHint.class.getCanonicalName() : hintTypeName;
            return this;
        }

        /**
         * Adds a token to the resulting {@link BundleHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /**
         * Copies all bundle data to be included in the resulting {@link BundleHint}.
         *
         * @param bundle Bundle to copy data from
         */
        @NonNull
        public Builder setDataBundle(@NonNull Bundle bundle) {
            mBundle.putAll(bundle);
            return this;
        }

        /**
         * @return the built {@link BundleHint}.
         */
        @NonNull
        public BundleHint build() {
            return new BundleHint(mBaseBuilder.build(), mBundle, mHintTypeName);
        }
    }
}
