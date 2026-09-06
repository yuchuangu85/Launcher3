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
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Represents information about the text a user is actively typing or entering into an input field
 * within an application.
 *
 * <p>This data is collected by the Content Capture framework by observing user interactions with
 * input fields.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class UserInputText implements Parcelable {

    /** The field type is unknown. */
    public static final int FIELD_TYPE_UNKNOWN = 0;

    /** The user input is captured from a search box. */
    public static final int FIELD_TYPE_SEARCH_BOX = 1;

    /**
     * The type of field in which the user wrote the text.
     *
     * @hide
     */
    @IntDef(
            prefix = {"FIELD_TYPE_"},
            value = {FIELD_TYPE_UNKNOWN, FIELD_TYPE_SEARCH_BOX})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FieldType {}

    /** The user input text source is unknown. */
    public static final int USER_INPUT_TEXT_SOURCE_UNKNOWN = 0;

    /** The user input text source is typed by the user. */
    public static final int USER_INPUT_TEXT_SOURCE_TYPED = 1;

    /**
     * The user input text source is not typed directly by the user, but inferred from the UI.
     *
     * <p>e.g. text in a search box after tapping suggested text in the host application.
     */
    public static final int USER_INPUT_TEXT_SOURCE_INFERRED = 2;

    /**
     * The user input text source is a clicked suggestion provided outside of the host application.
     *
     * <p>e.g. a clicked autofill suggestion in a messaging app.
     */
    public static final int USER_INPUT_TEXT_SOURCE_CLICKED = 3;

    /**
     * The source of the user input text.
     *
     * @hide
     */
    @IntDef(
            prefix = {"USER_INPUT_TEXT_SOURCE_"},
            value = {
                USER_INPUT_TEXT_SOURCE_UNKNOWN,
                USER_INPUT_TEXT_SOURCE_TYPED,
                USER_INPUT_TEXT_SOURCE_INFERRED,
                USER_INPUT_TEXT_SOURCE_CLICKED
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserInputTextSource {}

    private final @NonNull String mText;
    private final @NonNull Rect mViewNodeBoundingBox;
    @FieldType private final int mFieldType;
    @UserInputTextSource private final int mUserInputTextSource;

    private UserInputText(
            @NonNull String text,
            @NonNull Rect viewNodeBoundingBox,
            @FieldType int fieldType,
            @UserInputTextSource int userInputTextSource) {
        mText = text;
        mViewNodeBoundingBox = viewNodeBoundingBox;
        mFieldType = fieldType;
        mUserInputTextSource = userInputTextSource;
    }

    private UserInputText(Parcel in) {
        mText = in.readString8();
        mViewNodeBoundingBox = in.readTypedObject(Rect.CREATOR);
        mFieldType = in.readInt();
        mUserInputTextSource = in.readInt();
    }

    /** Returns the text content entered by the user. */
    @NonNull
    public String getText() {
        return mText;
    }

    /** Returns the screen relative bounding box of the input field. */
    @NonNull
    public Rect getViewNodeBoundingBox() {
        return mViewNodeBoundingBox;
    }

    /** Returns the type of the input field in which the user entered the text. */
    @FieldType
    public int getFieldType() {
        return mFieldType;
    }

    /** Returns the source of the user input text. */
    @UserInputTextSource
    public int getUserInputTextSource() {
        return mUserInputTextSource;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mText);
        dest.writeTypedObject(mViewNodeBoundingBox, flags);
        dest.writeInt(mFieldType);
        dest.writeInt(mUserInputTextSource);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserInputText)) return false;
        UserInputText that = (UserInputText) o;
        return mFieldType == that.mFieldType
                && mUserInputTextSource == that.mUserInputTextSource
                && Objects.equals(mText, that.mText)
                && Objects.equals(mViewNodeBoundingBox, that.mViewNodeBoundingBox);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mText, mViewNodeBoundingBox, mFieldType, mUserInputTextSource);
    }

    @Override
    public String toString() {
        // mText purposefully omitted to prevent accidentally logging potentially sensitive data.
        return "UserInputText{"
                + "mViewNodeBoundingBox="
                + mViewNodeBoundingBox
                + ", mFieldType="
                + mFieldType
                + ", mUserInputTextSource="
                + mUserInputTextSource
                + '}';
    }

    public static final @NonNull Creator<UserInputText> CREATOR =
            new Creator<>() {
                @Override
                public UserInputText createFromParcel(Parcel in) {
                    return new UserInputText(in);
                }

                @Override
                public UserInputText[] newArray(int size) {
                    return new UserInputText[size];
                }
            };

    /** Builder for {@link UserInputText}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private String mText;
        private Rect mViewNodeBoundingBox;
        @FieldType private int mFieldType = FIELD_TYPE_UNKNOWN;
        @UserInputTextSource private int mUserInputTextSource = USER_INPUT_TEXT_SOURCE_UNKNOWN;

        public Builder() {}

        /** Sets the text content entered by the user. */
        @NonNull
        public Builder setText(@NonNull String text) {
            mText = requireNonNull(text, "text cannot be null");
            return this;
        }

        /** Sets the screen relative bounding box of the input field. */
        @NonNull
        public Builder setViewNodeBoundingBox(@NonNull Rect viewNodeBoundingBox) {
            mViewNodeBoundingBox =
                    requireNonNull(viewNodeBoundingBox, "viewNodeBoundingBox cannot be null");
            return this;
        }

        /** Sets the type of the input field in which the user entered the text. */
        @NonNull
        public Builder setFieldType(@FieldType int fieldType) {
            mFieldType = fieldType;
            return this;
        }

        /** Sets the source of the user input text. */
        @NonNull
        public Builder setUserInputTextSource(@UserInputTextSource int userInputTextSource) {
            mUserInputTextSource = userInputTextSource;
            return this;
        }

        /** Builds a new {@link UserInputText} instance. */
        @NonNull
        public UserInputText build() {
            return new UserInputText(
                    requireNonNull(mText, "text must be set"),
                    requireNonNull(mViewNodeBoundingBox, "viewNodeBoundingBox must be set"),
                    mFieldType,
                    mUserInputTextSource);
        }
    }
}
