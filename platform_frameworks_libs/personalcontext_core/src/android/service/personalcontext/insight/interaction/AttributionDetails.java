/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.service.personalcontext.insight.interaction;

import android.annotation.FlaggedApi;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Information about an insight that can be shown to a user about why that insight was generated.
 *
 * <p>Understanders populate this with line items about what was included in the insight, where it
 * came from, and why it was included. This attribution is then provided back to the understander
 * for display when the user wants to see why an insight was generated. A typical attribution might
 * look like:
 *
 * <pre>
 * {@code
 * [Email Logo] Content from Email
 * You have an email from Up Up and Away Airlines titled "Your Upcoming Flight" with flight details.
 * ----------------------------------------------------------------------
 * [Calendar Logo] Upcoming Calendar Event
 * (Flight to Denver)
 * ----------------------------------------------------------------------
 * Your Current Call
 * (Phone App)
 * You are calling Up Up and Away Airlines Customer Service.
 * }
 * </pre>
 *
 * Each line item must have at least an icon or a title, but may also have a subtitle and a
 * description explaining where the information came from or why it was included. Each of these
 * fields is free-form text, and should be suitable to show to the user.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class AttributionDetails implements Parcelable {
    private final List<AttributionLine> mLines;

    /**
     * Creates a new {@link AttributionDetails} with all of the attribution lines.
     *
     * @param lines Attribution lines to show user on demand
     */
    public AttributionDetails(@NonNull Collection<AttributionLine> lines) {
        mLines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    private AttributionDetails(@NonNull Parcel in) {
        final ArrayList<AttributionLine> lines = new ArrayList<>();
        in.readTypedList(lines, AttributionLine.CREATOR);
        mLines = Collections.unmodifiableList(lines);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedList(mLines);
    }

    @NonNull
    public static final Creator<AttributionDetails> CREATOR = new Creator<AttributionDetails>() {
        @Override
        public AttributionDetails createFromParcel(Parcel in) {
            return new AttributionDetails(in);
        }

        @Override
        public AttributionDetails[] newArray(int size) {
            return new AttributionDetails[size];
        }
    };

    /** Gets all of the {@link AttributionLine} lines to be shown to the user. */
    @NonNull
    public List<AttributionLine> getLines() {
        return mLines;
    }

    /**
     * Provides a single line of attribution data, explaining where information to generate an
     * insight came from
     */
    public static final class AttributionLine implements Parcelable {
        private final CharSequence mTitle;
        private final Icon mIcon;
        private final CharSequence mContentDescription;
        private final CharSequence mSubtitle;

        /**
         * Creates a new {@link AttributionLine}.
         *
         * @throws IllegalArgumentException if both title and icon are {@code null}
         *
         * @param title the title of the line item
         * @param icon the icon of the line item
         * @param contentDescription the detailed description of the line item
         * @param subtitle the subtitle of the line item
         */
        public AttributionLine(
                @Nullable CharSequence title,
                @Nullable Icon icon,
                @Nullable CharSequence contentDescription,
                @Nullable CharSequence subtitle) {
            if (title == null && icon == null) {
                throw new IllegalArgumentException("Either title or icon must be supplied");
            }
            this.mTitle = title;
            this.mIcon = icon;
            this.mContentDescription = contentDescription;
            this.mSubtitle = subtitle;
        }

        private AttributionLine(@NonNull Parcel in) {
            mTitle = in.readCharSequence();
            mIcon = in.readTypedObject(Icon.CREATOR);
            mContentDescription = in.readCharSequence();
            mSubtitle = in.readCharSequence();
        }

        @NonNull
        public static final Creator<AttributionLine> CREATOR = new Creator<AttributionLine>() {
            @Override
            public AttributionLine createFromParcel(Parcel in) {
                return new AttributionLine(in);
            }

            @Override
            public AttributionLine[] newArray(int size) {
                return new AttributionLine[size];
            }
        };

        /** Returns the title of the line item. If {@code null}, the icon should be used instead. */
        @Nullable
        public CharSequence getTitle() {
            return mTitle;
        }

        /** Returns the icon of the line item. If {@code null}, the title should be used instead. */
        @Nullable
        public Icon getIcon() {
            return mIcon;
        }

        /** Returns the content description of the line item. */
        @Nullable
        public CharSequence getContentDescription() {
            return mContentDescription;
        }

        /** Returns the subtitle of the line item. */
        @Nullable
        public CharSequence getSubtitle() {
            return mSubtitle;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeCharSequence(mTitle);
            dest.writeTypedObject(mIcon, flags);
            dest.writeCharSequence(mContentDescription);
            dest.writeCharSequence(mSubtitle);
        }
    }
}
