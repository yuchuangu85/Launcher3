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
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A hint that provides information about the text a user is actively typing or entering into an
 * input field.
 *
 * <p>This data is collected by the Content Capture framework by observing user interactions with
 * input fields.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class UserInputHint extends ContextHint {
    private static final String KEY_USER_INPUT_TEXT = "key_user_input_text";
    private static final String KEY_SOURCE_APP_ACTIVITY_COMPONENT_NAME =
            "key_source_app_activity_component_name";
    private final UserInputText mUserInputText;
    private final ComponentName mSourceAppActivityComponentName;

    /** Creates a new {@link UserInputHint}. */
    private UserInputHint(
            @NonNull ConstructorParams baseParams,
            @NonNull UserInputText userInputText,
            @NonNull ComponentName sourceAppActivityComponentName) {
        super(baseParams);
        mUserInputText = requireNonNull(userInputText);
        mSourceAppActivityComponentName = sourceAppActivityComponentName;
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    UserInputHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        super(baseParams);
        mUserInputText = bundle.getParcelable(KEY_USER_INPUT_TEXT, UserInputText.class);
        requireNonNull(mUserInputText, "Bundle must contain user input text");
        mSourceAppActivityComponentName =
                bundle.getParcelable(KEY_SOURCE_APP_ACTIVITY_COMPONENT_NAME, ComponentName.class);
        requireNonNull(
                mSourceAppActivityComponentName,
                "Bundle must contain source app activity component name");
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_USER_INPUT;
    }

    /**
     * Returns the {@link UserInputText} object containing the detailed information about the user's
     * input.
     */
    @NonNull
    public UserInputText getUserInputText() {
        return mUserInputText;
    }

    /** Get the component name of the source app activity associated with the hint. */
    @NonNull
    public ComponentName getSourceAppActivityComponentName() {
        return mSourceAppActivityComponentName;
    }

    /** @hide */
    @Nullable
    @Override
    public String getSourcePackageName() {
        return mSourceAppActivityComponentName.getPackageName();
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        final Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_USER_INPUT_TEXT, mUserInputText);
        bundle.putParcelable(
                KEY_SOURCE_APP_ACTIVITY_COMPONENT_NAME, mSourceAppActivityComponentName);
        return bundle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserInputHint)) return false;
        if (!super.equals(o)) return false;
        UserInputHint that = (UserInputHint) o;
        return Objects.equals(mUserInputText, that.mUserInputText)
                && Objects.equals(
                        mSourceAppActivityComponentName, that.mSourceAppActivityComponentName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mUserInputText, mSourceAppActivityComponentName);
    }

    @Override
    public String toString() {
        return "UserInputHint{"
                + "mUserInputText="
                + mUserInputText
                + ", mSourceAppActivityComponentName="
                + mSourceAppActivityComponentName
                + "} extends "
                + super.toString();
    }

    /** Builder used to create a {@link UserInputHint}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private UserInputText mUserInputText;
        private ComponentName mSourceAppActivityComponentName;

        /**
         * Creates an instance of {@link Builder}.
         *
         * @param userInputText the {@link UserInputText} containing the details of the user's
         *     input.
         */
        public Builder(@NonNull UserInputText userInputText) {
            requireNonNull(userInputText, "userInputText must not be null");
            mUserInputText = userInputText;
        }

        /** Sets the component name of the source app activity associated with the hint. */
        @NonNull
        public Builder setSourceAppActivityComponentName(
                @NonNull ComponentName sourceAppActivityComponentName) {
            requireNonNull(
                    sourceAppActivityComponentName,
                    "sourceAppActivityComponentName must not be null");
            mSourceAppActivityComponentName = sourceAppActivityComponentName;
            return this;
        }

        /**
         * Adds a token to the resulting {@link UserInputHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /**
         * @return the built {@link UserInputHint}.
         */
        @NonNull
        public UserInputHint build() {
            requireNonNull(
                    mSourceAppActivityComponentName,
                    "Source app activity component name must be set");
            return new UserInputHint(
                    mBaseBuilder.build(), mUserInputText, mSourceAppActivityComponentName);
        }
    }
}
