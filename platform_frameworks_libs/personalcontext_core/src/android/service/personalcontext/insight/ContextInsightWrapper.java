/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class wraps a {@link ContextInsight} as a {@link Parcelable}.
 * @hide
 */
public final class ContextInsightWrapper implements Parcelable {
    @NonNull
    private final ContextInsight mInsight;

    /** Construct a wrapper for the given {@link ContextInsight}. */
    public ContextInsightWrapper(@NonNull ContextInsight insight) {
        mInsight = insight;
    }

    /**
     * Utility method to unwrap a collection of {@link ContextInsightWrapper} into a list of
     * {@link ContextInsight}.
     */
    @NonNull
    public static List<ContextInsight> unwrapList(
            @NonNull Collection<ContextInsightWrapper> wrappers) {
        return unwrapInto(wrappers, new ArrayList<>());
    }

    /**
     * Utility method to unwrap a collection of {@link ContextInsightWrapper} into a collection of
     * {@link ContextInsight}.
     */
    @NonNull
    public static <T extends Collection<ContextInsight>> T unwrapInto(
            @NonNull Collection<ContextInsightWrapper> wrappers,
            @NonNull T into) {
        for (ContextInsightWrapper wrapper : wrappers) {
            into.add(wrapper.getContextInsight());
        }
        return into;
    }

    /**
     * Utility method to wrap a collection of {@link ContextInsight} into a list of
     * {@link ContextInsightWrapper}.
     */
    @NonNull
    public static List<ContextInsightWrapper> wrapList(
            @NonNull Collection<ContextInsight> insights) {
        List<ContextInsightWrapper> list = new ArrayList<>();
        for (ContextInsight insight : insights) {
            list.add(new ContextInsightWrapper(insight));
        }
        return list;
    }

    /**
     * Return the {@link ContextInsight} contained within this wrapper.
     */
    @NonNull
    public ContextInsight getContextInsight() {
        return mInsight;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeBundle(mInsight.toBundle());
    }

    @NonNull
    public static final Creator<ContextInsightWrapper> CREATOR =
            new Creator<>() {
                @Override
                public ContextInsightWrapper createFromParcel(@NonNull Parcel source) {
                    return new ContextInsightWrapper(
                            ContextInsight.createInsightFromBundle(source.readBundle()));
                }

                @Override
                public ContextInsightWrapper[] newArray(int size) {
                    return new ContextInsightWrapper[size];
                }
            };
}
