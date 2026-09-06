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

package android.service.personalcontext;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.hint.ContextHint;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token for a specific renderer that can be included in a {@link ContextHint} or insight. If
 * included in a hint, indicates that insights generated from the hint should only go to the
 * specific renderer associated with this token. If included in an insight, indicates the insight
 * should only be sent to the specific renderer associated with this token.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class Token implements Parcelable {
    private static final AtomicLong ID_SEQUENCE = new AtomicLong(0);

    /**
     * Unique identifier for this token.
     */
    private final long mId;

    /**
     * Creates a new {@link Token}.
     * @hide
     */
    public Token() {
        mId = ID_SEQUENCE.incrementAndGet();
    }

    private Token(Parcel in) {
        mId = in.readLong();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(mId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final @NonNull Creator<Token> CREATOR = new Creator<>() {
        @Override
        public Token createFromParcel(Parcel in) {
            return new Token(in);
        }

        @Override
        public Token[] newArray(int size) {
            return new Token[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;

        final Token other = (Token) o;
        return mId == other.mId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId);
    }

    @Override
    public String toString() {
        return "Token{" + mId + "}";
    }
}
