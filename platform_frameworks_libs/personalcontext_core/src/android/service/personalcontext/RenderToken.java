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

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.hint.ContextHint;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.UUID;

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
public final class RenderToken implements Parcelable, Comparable<RenderToken> {
    /**
     * Unique identifier for this token.
     */
    private final UUID mId;

    /**
     * A {@link String} value that can be set at construction time to help the creator identify the
     * token when returned to it in the future.
     */
    private final String mTag;

    /**
     * Unique identifier of the renderer this token is associated with.
     */
    private final UUID mRendererComponentId;

    /**
     * Creates a new {@link RenderToken} for the renderer with the given ID and tag.
     *
     * @param rendererComponentId The {@link UUID} of the renderer as identified by the
     *                            PersonalContext service.
     * @param tag An optional value that can be associated with the token for future identification.
     * @hide
     */
    @TestApi
    public RenderToken(@NonNull UUID rendererComponentId, @Nullable String tag) {
        mId = UUID.randomUUID();
        mRendererComponentId = requireNonNull(rendererComponentId);
        mTag = tag;
    }

    private RenderToken(Parcel in) {
        mId = UUID.fromString(in.readString8());
        mRendererComponentId = UUID.fromString(in.readString8());
        mTag = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mId.toString());
        dest.writeString8(mRendererComponentId.toString());
        dest.writeString(mTag);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns the unique ID of this token.
     */
    public @NonNull UUID getTokenId() {
        return mId;
    }

    /**
     * Returns the optional tag that was associated with this token at creation. {@link RenderToken}
     * creators can use this value to uniquely identify this token from others based on semantics
     * that make sense in their particular use-case.
     */
    public @Nullable String getTag() {
        return mTag;
    }

    @Override
    public int compareTo(@NonNull RenderToken o) {
        return mId.compareTo(o.mId);
    }

    /**
     * Returns the unique ID of the renderer this token is associated with.
     */
    public @NonNull UUID getRendererComponentId() {
        return mRendererComponentId;
    }

    public static final @NonNull Creator<RenderToken> CREATOR = new Creator<>() {
        @Override
        public RenderToken createFromParcel(Parcel in) {
            return new RenderToken(in);
        }

        @Override
        public RenderToken[] newArray(int size) {
            return new RenderToken[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RenderToken)) return false;

        final RenderToken other = (RenderToken) o;
        return Objects.equals(mId, other.mId)
                && Objects.equals(mRendererComponentId, other.mRendererComponentId)
                && TextUtils.equals(mTag, other.mTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mRendererComponentId, mTag);
    }
}
