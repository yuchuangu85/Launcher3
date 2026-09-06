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
import android.annotation.SystemApi;
import android.app.Person;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;

import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A hint that contains call related information (e.g. "phone call", "video call", etc.).
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class CallHint extends ContextHint {
    private static final String TAG = "CallHint";

    private static final String KEY_PARTICIPANTS = "participants";
    private static final String KEY_MODALITY = "modality";

    /**
     * Enumeration of call modalities.
     * @hide
     */
    @IntDef(
            prefix = {"MODALITY_"},
            value = {
                    MODALITY_UNKNOWN,
                    MODALITY_AUDIO,
                    MODALITY_VIDEO
            }
    )
    @Retention(RetentionPolicy.SOURCE)
    public @interface Modality {}

    /** Call modality for calls where the type is unknown. */
    public static final int MODALITY_UNKNOWN = -1;

    /** Call modality for audio calls. */
    public static final int MODALITY_AUDIO = 1;

    /** Call modality for video calls. */
    public static final int MODALITY_VIDEO = 2;

    @Modality
    private final int mModality;
    @NonNull
    private final HashSet<Person> mParticipants;

    /** Constructs a new {@link CallHint}. */
    private CallHint(
            @NonNull ConstructorParams baseParams,
            @Modality int modality,
            @NonNull Set<Person> participants) {
        super(baseParams);
        mModality = modality;
        mParticipants =
                new HashSet<>(requireNonNull(participants, "participants must not be null"));
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     */
    CallHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        super(baseParams);

        mModality = bundle.getInt(KEY_MODALITY);

        final Person[] participants =
                requireNonNull(
                        bundle.getParcelableArray(KEY_PARTICIPANTS, Person.class),
                        "participants must not be null");
        Preconditions.checkArgument(
                participants.length > 0, "participants must include at least one member");
        mParticipants = new HashSet<>(List.of(participants));
    }

    /** Return the modality of the call. */
    @Modality
    public int getModality() {
        return mModality;
    }

    /** Returns the set of {@link Person}s in the call. */
    @NonNull
    public Set<Person> getParticipants() {
        return mParticipants;
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_CALL;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        final Bundle bundle = new Bundle();
        bundle.putInt(KEY_MODALITY, mModality);
        bundle.putParcelableArray(KEY_PARTICIPANTS, mParticipants.toArray(new Person[0]));
        return bundle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallHint that)) return false;
        return mModality == that.mModality && Objects.equals(mParticipants, that.mParticipants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mModality, mParticipants);
    }

    /**
     * Builder used to create a {@link CallHint}.
     */
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        @Modality
        private final int mModality;
        private final Set<Person> mParticipants;

        /**
         * Creates an instance of {@link CallHint.Builder} from a {@link Modality} and a {@link Set}
         * of {@link Person} participants.
         *
         * @param modality the modality of the call (e.g. audio or video)
         * @param participants the set of {@link Person} participants in the call; must include at
         *                     least one member
         */
        public Builder(
                @Modality int modality,
                @NonNull Set<Person> participants) {
            requireNonNull(participants, "participants must not be null");
            Preconditions.checkArgument(
                    !participants.isEmpty(), "participants must include at least one member");

            mModality = modality;
            mParticipants = participants;
        }

        /**
         * Adds a token to the resulting {@link CallHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /**
         * @return the built {@link CallHint}.
         */
        @NonNull
        public CallHint build() {
            return new CallHint(mBaseBuilder.build(), mModality, mParticipants);
        }
    }
}
