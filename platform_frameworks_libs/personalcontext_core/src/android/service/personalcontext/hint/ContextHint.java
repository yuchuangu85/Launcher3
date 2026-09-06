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
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.os.Bundle;
import android.os.Parcel;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;
import android.util.Log;

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
 * A piece of input data into the personal context engine.
 *
 * <p>Hints may describe some current state of the device or represent an event that may be of use
 * for kicking off an understanding flow.
 *
 * <p>Users of this class can use instanceof to determine the type of the hint.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class ContextHint {
    private static final String TAG = "ContextHint";

    /**
     * Enumeration of hint types.
     *
     * @hide
     */
    @IntDef(
            prefix = {"HINT_TYPE_"},
            value = {
                HINT_TYPE_ERROR,
                HINT_TYPE_BUNDLE,
                HINT_TYPE_NOTIFICATION,
                HINT_TYPE_TEXT_CLASSIFICATION,
                HINT_TYPE_CONVERSATION,
                HINT_TYPE_RECENT_VIEW,
                HINT_TYPE_USER_INPUT,
                HINT_TYPE_AUTOFILL_INLINE_REQUEST,
                HINT_TYPE_CALL,
                HINT_TYPE_HINT_INVALIDATION,
                HINT_TYPE_INSIGHT_REFERENCE,
                HINT_TYPE_MESSAGE
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface HintType {}

    /** Hint type indicating an error when unparceling. */
    static final int HINT_TYPE_ERROR = -1;

    /**
     * Hint type for {@link BundleHint}.
     *
     * @hide
     */
    @VisibleForTesting public static final int HINT_TYPE_BUNDLE = 1;

    /** Hint type for {@link NotificationHint}. */
    static final int HINT_TYPE_NOTIFICATION = 2;

    /** Hint type for {@link TextClassificationHint}. */
    static final int HINT_TYPE_TEXT_CLASSIFICATION = 3;

    /** Hint type for {@link ContentCaptureConversationHint}. */
    static final int HINT_TYPE_CONVERSATION = 4;

    /** Hint type for {@link RecentViewHint}. */
    static final int HINT_TYPE_RECENT_VIEW = 5;

    /** Hint type for {@link UserInputHint}. */
    static final int HINT_TYPE_USER_INPUT = 6;

    /**
     * Hint type for {@link AutofillInlineRequestHint}.
     */
    static final int HINT_TYPE_AUTOFILL_INLINE_REQUEST = 7;

    /** Hint type for {@link CallHint}. */
    static final int HINT_TYPE_CALL = 8;

    /** Hint type for {@link HintInvalidationHint}. */
    static final int HINT_TYPE_HINT_INVALIDATION = 9;

    /** Hint type for {@link InsightReferenceHint}. */
    static final int HINT_TYPE_INSIGHT_REFERENCE = 10;

    /** Hint type for {@link MessagesHint}. */
    static final int HINT_TYPE_MESSAGE = 11;

    /**
     * Object returned when there is an unparceling error.
     *
     * @hide
     */
    private static final @NonNull ContextHint ERROR_HINT =
            new ContextHint(new ConstructorParams.Builder().build()) {
                @Override
                public int getHintType() {
                    return HINT_TYPE_ERROR;
                }

                @NonNull
                @Override
                Bundle toBundleImpl() {
                    return new Bundle();
                }
            };

    // Bundle keys for data stored in the base ContextHint.
    private static final String KEY_HINT_TYPE = "key_hint_type";
    private static final String KEY_HINT_ID = "key_hint_id";
    private static final String KEY_HINT_TOKENS = "key_hint_tokens";
    private static final String KEY_HINT_DATA = "key_hint_data";
    private static final String KEY_CREATION_TIME = "key_creation_time";
    private static final String KEY_HINT_TYPE_NAME = "key_hint_type_name";

    /** Unique identifier for this hint. */
    private final UUID mId;
    private final Set<Token> mTokens;
    private final Instant mCreationTime;

    /**
     * Internal constructor for generating a new hint. This should be called by subclasses in their
     * public constructors.
     *
     * @hide
     */
    ContextHint(ConstructorParams params) {
        mId = params.mId;
        mTokens = Collections.unmodifiableSet(new HashSet<>(params.mTokens));
        mCreationTime = params.mCreationTime;
    }

    /**
     * Returns the {@link HintType} of this hint.
     *
     * @hide
     */
    @HintType
    public abstract int getHintType();

    /**
     * Gets the type name of the hint. For {@link BundleHint} this is the type name that was
     * provided in the builder. For all other hints it is the canonical class name.
     */
    @NonNull
    public String getHintTypeName() {
        return getClass().getCanonicalName();
    }

    /**
     * Gets the package that the contents of this hint came from (if available).
     * @hide
     */
    @Nullable
    public String getSourcePackageName() {
        return null;
    }

    /** Returns the unique ID of this hint. */
    public final @NonNull UUID getHintId() {
        return mId;
    }

    /** Returns the set of tokens that were added to this hint. */
    @NonNull
    public final Set<Token> getTokens() {
        return mTokens;
    }

    /** Gets the time this hint was created. */
    @NonNull
    public final Instant getCreationTime() {
        return mCreationTime;
    }

    @NonNull
    abstract Bundle toBundleImpl();

    /**
     * Writes data should be used to verify the hint has not been tampered with to a {@link Parcel}.
     *
     * <p>If your hint stores binders or file descriptors, you must override this method
     *
     * @hide
     */
    public void writeToSignatureParcel(@NonNull Parcel dest) {
        dest.writeBundle(toBundle());
    }

    /**
     * Return the {@link Bundle} representation of this hint's data for writing to a {@link
     * ContextHintWrapper}.
     *
     * @hide
     */
    @TestApi
    @NonNull
    public Bundle toBundle() {
        final Bundle b = new Bundle();
        b.putInt(KEY_HINT_TYPE, getHintType());
        b.putString(KEY_HINT_ID, mId.toString());
        b.putParcelableArrayList(KEY_HINT_TOKENS, new ArrayList<>(mTokens));
        b.putBundle(KEY_HINT_DATA, toBundleImpl());
        b.putLong(KEY_CREATION_TIME, mCreationTime.toEpochMilli());
        b.putString(KEY_HINT_TYPE_NAME, getHintTypeName());
        return b;
    }

    /** @hide */
    public static String peekAtHintTypeName(Bundle bundle) {
        return bundle.getString(KEY_HINT_TYPE_NAME);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + mId + "}";
    }

    /**
     * Unbundles a hint into the correct subclass of hint based on the hint type.
     *
     * @hide
     */
    @TestApi
    @NonNull
    public static ContextHint createHintFromBundle(@Nullable Bundle bundle) {
        if (bundle == null) {
            return ERROR_HINT;
        }

        final Bundle data = bundle.getBundle(KEY_HINT_DATA);
        final ConstructorParams constructorParams = new ConstructorParams(
                UUID.fromString(bundle.getString(KEY_HINT_ID)),
                bundle.getParcelableArrayList(KEY_HINT_TOKENS, Token.class),
                Instant.ofEpochMilli(bundle.getLong(KEY_CREATION_TIME)));

        try {
            return switch (bundle.getInt(KEY_HINT_TYPE, HINT_TYPE_ERROR)) {
                case HINT_TYPE_BUNDLE -> new BundleHint(constructorParams, data);
                case HINT_TYPE_NOTIFICATION -> new NotificationHint(constructorParams, data);
                case HINT_TYPE_TEXT_CLASSIFICATION -> new TextClassificationHint(constructorParams,
                        data);
                case HINT_TYPE_CONVERSATION ->new ContentCaptureConversationHint(constructorParams,
                        data);
                case HINT_TYPE_RECENT_VIEW -> new RecentViewHint(constructorParams, data);
                case HINT_TYPE_USER_INPUT -> new UserInputHint(constructorParams, data);
                case HINT_TYPE_AUTOFILL_INLINE_REQUEST -> new AutofillInlineRequestHint(
                        constructorParams, data);
                case HINT_TYPE_CALL ->  new CallHint(constructorParams, data);
                case HINT_TYPE_HINT_INVALIDATION -> new HintInvalidationHint(
                        constructorParams, data);
                case HINT_TYPE_INSIGHT_REFERENCE ->
                        new InsightReferenceHint(constructorParams, data);
                case HINT_TYPE_MESSAGE -> new MessagesHint(constructorParams, data);
                default -> ERROR_HINT;
            };
        } catch (Exception e) {
            Log.e(TAG, "Error creating hint", e);
            return ERROR_HINT;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ContextHint that)) return false;
        return Objects.deepEquals(mId, that.mId)
                && Objects.deepEquals(getHintType(), that.getHintType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, getHintType());
    }

    /**
     * Parameters used to create a new {@link ContextHint}.
     *
     * @hide
     */
    static class ConstructorParams {
        private final UUID mId;
        private final Collection<Token> mTokens;
        private final Instant mCreationTime;

        private ConstructorParams(Collection<Token> tokens) {
            this(UUID.randomUUID(), tokens, Instant.now());
        }

        private ConstructorParams(UUID id, Collection<Token> tokens, Instant creationTime) {
            mId = id;
            mTokens = tokens;
            mCreationTime = creationTime;
        }

        static final class Builder {
            private final Set<Token> mTokens = new HashSet<>();

            /**
             * Adds a token to the resulting {@link ContextHint}.
             *
             * @param token the token to add
             */
            @NonNull
            Builder addToken(@NonNull Token token) {
                mTokens.add(requireNonNull(token, "token must not be null"));
                return this;
            }

            ConstructorParams build() {
                return new ConstructorParams(mTokens);
            }
        }
    }
}
