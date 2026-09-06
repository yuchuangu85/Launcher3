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
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.view.autofill.AutofillId;

import java.util.Objects;

/**
 * Additional data for a single message extracted from a message view by the Content Capture API.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ChatMessageContentCaptureData implements Parcelable {

    private final @NonNull String mRawTimeString;
    private final @NonNull String mRawDateString;
    private final @NonNull AutofillId mAutofillId;

    private ChatMessageContentCaptureData(
            @NonNull String rawTimeString,
            @NonNull String rawDateString,
            @NonNull AutofillId autofillId) {
        mRawTimeString = rawTimeString;
        mRawDateString = rawDateString;
        mAutofillId = autofillId;
    }

    private ChatMessageContentCaptureData(Parcel in) {
        mRawTimeString = in.readString8();
        mRawDateString = in.readString8();
        mAutofillId = in.readTypedObject(AutofillId.CREATOR);
    }

    /**
     * Returns a user-friendly string representing the time of the message (e.g., "10:00 AM").
     *
     * <p>This string is copied directly from the message view and returns as-is.
     */
    @NonNull
    public String getRawTimeString() {
        return mRawTimeString;
    }

    /**
     * Returns a user-friendly string representing the date of the message (e.g., "Today").
     *
     * <p>This string is copied directly from the message view and returns as-is.
     */
    @NonNull
    public String getRawDateString() {
        return mRawDateString;
    }

    /** Returns the {@link AutofillId} of the view that this message corresponds to. */
    @NonNull
    public AutofillId getAutofillId() {
        return mAutofillId;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mRawTimeString);
        dest.writeString8(mRawDateString);
        dest.writeTypedObject(mAutofillId, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChatMessageContentCaptureData that)) return false;
        return Objects.equals(mRawTimeString, that.mRawTimeString)
                && Objects.equals(mRawDateString, that.mRawDateString)
                && Objects.equals(mAutofillId, that.mAutofillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRawTimeString, mRawDateString, mAutofillId);
    }

    @Override
    public String toString() {
        return "ChatMessageContentCaptureData{"
                + "mRawTimeString='"
                + mRawTimeString
                + "'"
                + ", mRawDateString='"
                + mRawDateString
                + "'"
                + ", mAutofillId="
                + mAutofillId
                + '}';
    }

    /** Builder for {@link ChatMessageContentCaptureData}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private @NonNull String mRawTimeString;
        private @NonNull String mRawDateString;
        private @NonNull AutofillId mAutofillId;

        /**
         * Sets a user-friendly string representing the time of the message (e.g., "10:00 AM").
         *
         * <p>This is a required builder input and should be a string that is copied directly from
         * the message view and provided as-is.
         */
        @NonNull
        public Builder setRawTimeString(@NonNull String rawTimeString) {
            mRawTimeString = requireNonNull(rawTimeString);
            return this;
        }

        /**
         * Sets a user-friendly string representing the date of the message (e.g., "Today").
         *
         * <p>This is a required builder input should be a string that is copied directly from the
         * message view and provided as-is.
         */
        @NonNull
        public Builder setRawDateString(@NonNull String rawDateString) {
            mRawDateString = requireNonNull(rawDateString);
            return this;
        }

        /**
         * Sets the {@link AutofillId} of the view that this message corresponds to.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setAutofillId(@NonNull AutofillId autofillId) {
            mAutofillId = requireNonNull(autofillId);
            return this;
        }

        /**
         * Builds the {@link ChatMessageContentCaptureData}.
         *
         * @throws NullPointerException if any of {@link #setRawTimeString(String)}, {@link
         *     #setRawDateString(String)}, and {@link #setAutofillId(AutofillId)} are not set.
         */
        @NonNull
        public ChatMessageContentCaptureData build() {
            return new ChatMessageContentCaptureData(
                    requireNonNull(mRawTimeString),
                    requireNonNull(mRawDateString),
                    requireNonNull(mAutofillId));
        }
    }

    public static final @NonNull Creator<ChatMessageContentCaptureData> CREATOR =
            new Creator<>() {
                @Override
                public ChatMessageContentCaptureData createFromParcel(Parcel in) {
                    return new ChatMessageContentCaptureData(in);
                }

                @Override
                public ChatMessageContentCaptureData[] newArray(int size) {
                    return new ChatMessageContentCaptureData[size];
                }
            };
}
