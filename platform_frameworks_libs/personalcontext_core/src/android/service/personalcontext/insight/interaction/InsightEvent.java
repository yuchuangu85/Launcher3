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
import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;

import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Provides information about an event that occurred on an insight.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightEvent implements Parcelable {
    /**
     * Enumeration of event types.
     *
     * @hide
     */
    @IntDef(
            prefix = {"EVENT_"},
            value = {
                    EVENT_UNKNOWN,
                    EVENT_SHOW,
                    EVENT_HIDE,
                    EVENT_USER_TAP,
                    EVENT_USER_LONG_PRESS,
                    EVENT_USER_DISMISS,
                    EVENT_USER_ATTRIBUTION_REQUESTED,
                    EVENT_USER_FEEDBACK_POSITIVE,
                    EVENT_USER_FEEDBACK_NEGATIVE,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EventType {
    }

    /** Event type for unknown events. */
    public static final int EVENT_UNKNOWN = 0;

    /** Event type for when the insight is shown to the user. */
    public static final int EVENT_SHOW = 1;

    /** Event type for when the insight is hidden from the user. */
    public static final int EVENT_HIDE = 2;

    /** Event type for when the user taps on the insight. */
    public static final int EVENT_USER_TAP = 3;

    /** Event type for when the user long-presses on the insight. */
    public static final int EVENT_USER_LONG_PRESS = 4;

    /** Event type for when the user dismisses the insight. */
    public static final int EVENT_USER_DISMISS = 5;

    /** Event type for when the user requests attribution for the insight. */
    public static final int EVENT_USER_ATTRIBUTION_REQUESTED = 6;

    /** Event type for when the user enters positive feedback on the insight. */
    public static final int EVENT_USER_FEEDBACK_POSITIVE = 7;

    /** Event type for when the user enters negative feedback on the insight. */
    public static final int EVENT_USER_FEEDBACK_NEGATIVE = 8;

    private final @EventType int mEventType;
    private final PublishedContextInsight mPublishedContextInsight;
    private final long mTimestamp;

    @NonNull
    private final RenderToken mRenderToken;

    /** @hide */
    public InsightEvent(
            @EventType int eventType,
            @NonNull PublishedContextInsight publishedInsight,
            long timestamp,
            @NonNull RenderToken renderToken) {
        mEventType = eventType;
        mPublishedContextInsight = publishedInsight;
        mTimestamp = timestamp;
        mRenderToken = renderToken;
    }

    private InsightEvent(Parcel in) {
        mEventType = in.readInt();
        mPublishedContextInsight = in.readParcelable(
                /* classLoader= */ null, PublishedContextInsightWrapper.class)
                .getPublishedContextInsight();
        mTimestamp = in.readLong();
        mRenderToken = in.readParcelable(null, RenderToken.class);
    }

    /** Gets the type of event that occurred. */
    public int getEventType() {
        return mEventType;
    }

    /** Gets the insight that the event occurred on. */
    @NonNull
    public PublishedContextInsight getInsight() {
        return mPublishedContextInsight;
    }

    /** Gets the system timethat the event occurred at. */
    public long getTimestamp() {
        return mTimestamp;
    }

    /** Gets the RenderToken of the Renderer that triggered the event. */
    @NonNull
    public RenderToken getRenderToken() {
        return mRenderToken;
    }

    @NonNull
    public static final Creator<InsightEvent> CREATOR = new Creator<InsightEvent>() {
        @Override
        public InsightEvent createFromParcel(Parcel in) {
            return new InsightEvent(in);
        }

        @Override
        public InsightEvent[] newArray(int size) {
            return new InsightEvent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mEventType);
        dest.writeParcelable(new PublishedContextInsightWrapper(mPublishedContextInsight), 0);
        dest.writeLong(mTimestamp);
        dest.writeParcelable(mRenderToken, flags);
    }
}
