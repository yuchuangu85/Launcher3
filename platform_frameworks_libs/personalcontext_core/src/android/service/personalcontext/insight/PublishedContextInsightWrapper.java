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
package android.service.personalcontext.insight;

import android.annotation.FlaggedApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class is a wrapper around {@link PublishedContextInsight} that allows it be transported as
 * a {@link Parcelable}.
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class PublishedContextInsightWrapper implements Parcelable {

    private final PublishedContextInsight mPublishedInsight;

    /** Construct a wrapper for the given {@link ContextInsight}. */
    public PublishedContextInsightWrapper(@NonNull PublishedContextInsight insight) {
        mPublishedInsight = insight;
    }

    /**
     * Utility method to unwrap a collection of {@link PublishedContextInsightWrapper} into a list
     * of {@link PublishedContextInsight}.
     */
    @NonNull
    public static List<PublishedContextInsight> unwrapList(
            @NonNull Collection<PublishedContextInsightWrapper> wrappers) {
        return unwrapInto(wrappers, new ArrayList<>());
    }

    /**
     * Return the {@link ContextInsight} contained within this wrapper.
     */
    @NonNull
    public PublishedContextInsight getPublishedContextInsight() {
        return mPublishedInsight;
    }

    /**
     * Utility method to unwrap a collection of {@link PublishedContextInsightWrapper} into a
     * collection of {@link ContextInsight}.
     */
    @NonNull
    public static <T extends Collection<PublishedContextInsight>> T unwrapInto(
            @NonNull Collection<PublishedContextInsightWrapper> wrappers,
            @NonNull T into) {
        for (PublishedContextInsightWrapper wrapper : wrappers) {
            into.add(wrapper.getPublishedContextInsight());
        }
        return into;
    }

    /**
     * Utility method to wrap a collection of {@link PublishedContextInsight} into a list of
     * {@link PublishedContextInsightWrapper}.
     */
    @NonNull
    public static List<PublishedContextInsightWrapper> wrapList(
            @NonNull Collection<PublishedContextInsight> insights) {
        List<PublishedContextInsightWrapper> list = new ArrayList<>();
        for (PublishedContextInsight insight : insights) {
            list.add(new PublishedContextInsightWrapper(insight));
        }
        return list;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeBundle(mPublishedInsight.toBundle());
    }

    @NonNull
    public static final Creator<PublishedContextInsightWrapper> CREATOR =
            new Creator<>() {
                @Override
                public PublishedContextInsightWrapper createFromParcel(@NonNull Parcel source) {
                    return new PublishedContextInsightWrapper(
                            PublishedContextInsight.createPublishedInsightFromBundle(
                                    source.readBundle()));
                }

                @Override
                public PublishedContextInsightWrapper[] newArray(int size) {
                    return new PublishedContextInsightWrapper[size];
                }
            };
}
