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
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents text content captured from a single UI element (a "view node") on the user's screen.
 *
 * <p>This data originates from the Android Content Capture framework.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class CapturedText implements Parcelable {
    private final @NonNull String mViewNodeText;
    private final @NonNull String mViewNodeDescription;
    private final @NonNull String mViewId;
    private final @NonNull Rect mViewNodeBoundingBox;
    private final @NonNull String mResourceId;
    private final @NonNull Instant mViewNodeLastUpdated;
    private final @Nullable Instant mViewNodeLastSeen;

    private CapturedText(
            @NonNull String viewNodeText,
            @NonNull String viewNodeDescription,
            @NonNull String viewId,
            @NonNull Rect viewNodeBoundingBox,
            @NonNull String resourceId,
            @NonNull Instant viewNodeLastUpdated,
            @Nullable Instant viewNodeLastSeen) {
        mViewNodeText = viewNodeText;
        mViewNodeDescription = viewNodeDescription;
        mViewId = viewId;
        mViewNodeBoundingBox = viewNodeBoundingBox;
        mResourceId = resourceId;
        mViewNodeLastUpdated = viewNodeLastUpdated;
        mViewNodeLastSeen = viewNodeLastSeen;
    }

    private CapturedText(Parcel in) {
        mViewNodeText = in.readString8();
        mViewNodeDescription = in.readString8();
        mViewId = in.readString8();
        mViewNodeBoundingBox = in.readTypedObject(Rect.CREATOR);
        mResourceId = in.readString8();
        mViewNodeLastUpdated = Instant.ofEpochMilli(in.readLong());
        if (in.readBoolean()) {
            mViewNodeLastSeen = Instant.ofEpochMilli(in.readLong());
        } else {
            mViewNodeLastSeen = null;
        }
    }

    /** Returns the actual text from the UI element. */
    @NonNull
    public String getViewNodeText() {
        return mViewNodeText;
    }

    /** Returns the accessibility content description of the UI element. */
    @NonNull
    public String getViewNodeDescription() {
        return mViewNodeDescription;
    }

    /** Returns a unique identifier for the UI element (e.g., Autofill ID). */
    @NonNull
    public String getViewId() {
        return mViewId;
    }

    /** Returns the screen relative bounding box of the text element, in pixels. */
    @NonNull
    public Rect getViewNodeBoundingBox() {
        return mViewNodeBoundingBox;
    }

    /**
     * Returns the view's <a
     * href="https://developer.android.com/guide/topics/resources/providing-resources#fromXmlSyntax">fully
     * qualified resource ID</a>.
     */
    @NonNull
    public String getResourceId() {
        return mResourceId;
    }

    /** Returns the timestamp when the view node was last updated. */
    @NonNull
    public Instant getViewNodeLastUpdated() {
        return mViewNodeLastUpdated;
    }

    /**
     * Returns the timestamp when the view node was last seen on screen by the Content Capture
     * framework. This is only populated for nodes that are no longer in the view hierarchy.
     */
    @Nullable
    public Instant getViewNodeLastSeen() {
        return mViewNodeLastSeen;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mViewNodeText);
        dest.writeString8(mViewNodeDescription);
        dest.writeString8(mViewId);
        dest.writeTypedObject(mViewNodeBoundingBox, flags);
        dest.writeString8(mResourceId);
        dest.writeLong(mViewNodeLastUpdated.toEpochMilli());
        if (mViewNodeLastSeen == null) {
            dest.writeBoolean(false);
        } else {
            dest.writeBoolean(true);
            dest.writeLong(mViewNodeLastSeen.toEpochMilli());
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CapturedText)) return false;
        CapturedText that = (CapturedText) o;
        return Objects.equals(mViewNodeLastUpdated, that.mViewNodeLastUpdated)
                && Objects.equals(mViewNodeLastSeen, that.mViewNodeLastSeen)
                && Objects.equals(mViewNodeText, that.mViewNodeText)
                && Objects.equals(mViewNodeDescription, that.mViewNodeDescription)
                && Objects.equals(mViewId, that.mViewId)
                && Objects.equals(mViewNodeBoundingBox, that.mViewNodeBoundingBox)
                && Objects.equals(mResourceId, that.mResourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mViewNodeText,
                mViewNodeDescription,
                mViewId,
                mViewNodeBoundingBox,
                mResourceId,
                mViewNodeLastUpdated,
                mViewNodeLastSeen);
    }

    @Override
    public String toString() {
        return "CapturedText{"
                + "mViewNodeText='"
                + mViewNodeText
                + '\''
                + ", mViewNodeDescription='"
                + mViewNodeDescription
                + '\''
                + ", mViewId='"
                + mViewId
                + '\''
                + ", mViewNodeBoundingBox="
                + mViewNodeBoundingBox
                + ", mResourceId='"
                + mResourceId
                + '\''
                + ", mViewNodeLastUpdated="
                + mViewNodeLastUpdated
                + ", mViewNodeLastSeen="
                + mViewNodeLastSeen
                + '}';
    }

    public static final @NonNull Creator<CapturedText> CREATOR =
            new Creator<>() {
                @Override
                public CapturedText createFromParcel(Parcel in) {
                    return new CapturedText(in);
                }

                @Override
                public CapturedText[] newArray(int size) {
                    return new CapturedText[size];
                }
            };

    /** Builder for {@link CapturedText}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private String mViewNodeText;
        private String mViewNodeDescription;
        private String mViewId;
        private Rect mViewNodeBoundingBox;
        private String mResourceId;
        private Instant mViewNodeLastUpdated;
        private Instant mViewNodeLastSeen;

        public Builder() {}

        /** Sets the actual text from the UI element. */
        @NonNull
        public Builder setViewNodeText(@NonNull String viewNodeText) {
            mViewNodeText = requireNonNull(viewNodeText, "viewNodeText cannot be null");
            return this;
        }

        /** Sets the accessibility content description of the UI element. */
        @NonNull
        public Builder setViewNodeDescription(@NonNull String viewNodeDescription) {
            mViewNodeDescription =
                    requireNonNull(viewNodeDescription, "viewNodeDescription cannot be null");
            return this;
        }

        /** Sets a unique identifier for the UI element. */
        @NonNull
        public Builder setViewId(@NonNull String viewId) {
            mViewId = requireNonNull(viewId, "viewId cannot be null");
            return this;
        }

        /** Sets the screen relative bounding box of the text element, in pixels. */
        @NonNull
        public Builder setViewNodeBoundingBox(@NonNull Rect viewNodeBoundingBox) {
            mViewNodeBoundingBox =
                    requireNonNull(viewNodeBoundingBox, "viewNodeBoundingBox cannot be null");
            return this;
        }

        /** Sets the resource ID of the view. */
        @NonNull
        public Builder setResourceId(@NonNull String resourceId) {
            mResourceId = requireNonNull(resourceId, "resourceId cannot be null");
            return this;
        }

        /** Sets the timestamp when the view node was last updated. */
        @NonNull
        public Builder setViewNodeLastUpdated(@NonNull Instant timestamp) {
            mViewNodeLastUpdated = requireNonNull(timestamp, "timestamp cannot be null");
            return this;
        }

        /** Sets the timestamp when the view node was last seen. */
        @NonNull
        public Builder setViewNodeLastSeen(@Nullable Instant timestamp) {
            mViewNodeLastSeen = timestamp;
            return this;
        }

        /** Returns the built {@link CapturedText}. */
        @NonNull
        public CapturedText build() {
            return new CapturedText(
                    requireNonNull(mViewNodeText, "viewNodeText must be set"),
                    requireNonNull(mViewNodeDescription, "viewNodeDescription must be set"),
                    requireNonNull(mViewId, "viewId must be set"),
                    requireNonNull(mViewNodeBoundingBox, "viewNodeBoundingBox must be set"),
                    requireNonNull(mResourceId, "resourceId must be set"),
                    requireNonNull(mViewNodeLastUpdated, "viewNodeLastUpdated must be set"),
                    mViewNodeLastSeen);
        }
    }
}
