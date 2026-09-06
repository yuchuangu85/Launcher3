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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.TestApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parcelable version of PublishedContextHint.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@TestApi
public final class PublishedContextHintWrapper implements Parcelable {
    private final PublishedContextHint mHint;

    /** Creates a new Parcelable wrapper around a {@link PublishedContextHint}. */
    public PublishedContextHintWrapper(@NonNull PublishedContextHint hint) {
        mHint = hint;
    }

    private PublishedContextHintWrapper(Parcel source) {
        mHint = new PublishedContextHint(source);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        mHint.writeToParcel(dest, flags);
    }

    @NonNull
    public PublishedContextHint getPublishedContextHint() {
        return mHint;
    }

    /**
     * Utility method to unwrap a collection of {@link PublishedContextHint} into a list of
     * {@link ContextHint}.
     *
     * @hide
     */
    @NonNull
    public static List<PublishedContextHint> unwrapList(
            @NonNull Collection<PublishedContextHintWrapper> wrappers) {
        return unwrapInto(wrappers, new ArrayList<>());
    }

    /**
     * Utility method to unwrap a collection of {@link PublishedContextHintWrapper} into a
     * collection of {@link PublishedContextHint}.
     */
    @NonNull
    public static <T extends Collection<PublishedContextHint>> T unwrapInto(
            @NonNull Collection<PublishedContextHintWrapper> wrappers,
            @NonNull T into) {
        for (PublishedContextHintWrapper wrapper : wrappers) {
            into.add(wrapper.getPublishedContextHint());
        }
        return into;
    }

    /**
     * Utility method to wrap a collection of {@link PublishedContextHint} into a list of
     * {@link PublishedContextHintWrapper}.
     */
    @NonNull
    public static List<PublishedContextHintWrapper> wrapList(
            @NonNull Collection<PublishedContextHint> hints) {
        ArrayList<PublishedContextHintWrapper> list = new ArrayList<>();
        for (PublishedContextHint hint : hints) {
            list.add(new PublishedContextHintWrapper(hint));
        }
        return list;
    }

    public static final @NonNull Creator<PublishedContextHintWrapper> CREATOR =
            new Creator<>() {
                @Override
                public PublishedContextHintWrapper createFromParcel(Parcel source) {
                    return new PublishedContextHintWrapper(source);
                }

                @Override
                public PublishedContextHintWrapper[] newArray(int size) {
                    return new PublishedContextHintWrapper[size];
                }
            };
}
