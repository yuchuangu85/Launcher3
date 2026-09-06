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
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.NotificationChannel;
import android.os.Bundle;
import android.service.notification.NotificationAssistantService;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.Flags;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Base class for notification-related events. These events mirror the callbacks in
 * {@link NotificationListenerService}.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class NotificationEvent {
    /**
     * Bundle key containing the type of the event.
     */
    private static final String KEY_EVENT_TYPE = "key_event_type";

    /**
     * Bundle key containing the {@link Bundle} representation of the event, retrieved through
     * {@link #toBundleImpl()}.
     */
    private static final String KEY_EVENT_DATA = "key_event_data";

    /**
     * Indicates an attempt to unbundle an unknown event type.
     *
     * @hide
     */
    static final int EVENT_TYPE_UNKNOWN = -1;

    /**
     * Indicates a notification was posted. Corresponds to {@link NotificationEnqueuedEvent}.
     *
     * @hide
     */
    @VisibleForTesting
    public static final int EVENT_TYPE_ENQUEUED = 1;

    /**
     * Indicates a notification was removed. Corresponds to {@link NotificationRemovedEvent}.
     *
     * @hide
     */
    static final int EVENT_TYPE_REMOVED = 2;

    /**
     * Enumeration of notification event types.
     *
     * @hide
     */
    @IntDef(prefix = {"EVENT_TYPE_"}, value = {EVENT_TYPE_UNKNOWN, EVENT_TYPE_ENQUEUED,
            EVENT_TYPE_REMOVED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EventType {
    }

    NotificationEvent() {
    }

    /**
     * @return {@link EventType} of this notification event.
     * @hide
     */
    @VisibleForTesting
    @EventType
    public abstract int getEventType();

    /**
     * Event type implementations should implement this to store their data into a bundle.
     */
    @NonNull
    abstract Bundle toBundleImpl();

    /**
     * Writes the event data to a {@link Bundle}.
     */
    @NonNull
    Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putInt(KEY_EVENT_TYPE, getEventType());
        bundle.putBundle(KEY_EVENT_DATA, toBundleImpl());
        return bundle;
    }

    /**
     * Unbundles a notification event into the correct subclass of event based on the event type.
     *
     * Bundle should have come from {@link #toBundle()} and must contain keys
     * {@link #KEY_EVENT_TYPE} and {@link #KEY_EVENT_DATA}.
     */
    @NonNull
    static NotificationEvent fromBundle(@NonNull Bundle bundle) {
        final int eventType = bundle.getInt(KEY_EVENT_TYPE);
        final Bundle eventData = bundle.getBundle(KEY_EVENT_DATA);
        Preconditions.checkNotNull(eventData, "Bundle is missing KEY_EVENT_DATA");

        return switch (eventType) {
            case EVENT_TYPE_ENQUEUED -> new NotificationEnqueuedEvent(eventData);
            case EVENT_TYPE_REMOVED -> new NotificationRemovedEvent(eventData);
            default -> new NotificationEvent() {
                @Override
                public int getEventType() {
                    return EVENT_TYPE_UNKNOWN;
                }

                @NonNull
                @Override
                Bundle toBundleImpl() {
                    return new Bundle();
                }
            };
        };
    }

    /**
     * An event representing a notification being enqueued in preparation for being posted.
     *
     * @see NotificationAssistantService#onNotificationEnqueued(StatusBarNotification,
     * NotificationChannel, NotificationListenerService.RankingMap)
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class NotificationEnqueuedEvent extends NotificationEvent {
        private static final String KEY_STATUS_BAR_NOTIFICATION = "key_status_bar_notification";
        private static final String KEY_NOTIFICATION_CHANNEL = "key_notification_channel";
        private static final String KEY_RANKING_MAP = "key_ranking_map";

        private final StatusBarNotification mStatusBarNotification;
        private final NotificationChannel mNotificationChannel;
        private final NotificationListenerService.RankingMap mRankingMap;

        /**
         * Creates a new {@link NotificationEnqueuedEvent}.
         *
         * @param statusBarNotification {@link StatusBarNotification} that was just enqueued.
         * @param channel the {@link NotificationChannel} of the notification.
         * @param rankingMap ranking information for active notifications, including the newly
         *                   enqueued one.
         * @see NotificationAssistantService#onNotificationEnqueued(StatusBarNotification,
         * NotificationChannel, NotificationListenerService.RankingMap)
         */
        public NotificationEnqueuedEvent(@NonNull StatusBarNotification statusBarNotification,
                @NonNull NotificationChannel channel,
                @NonNull NotificationListenerService.RankingMap rankingMap) {
            mStatusBarNotification = statusBarNotification;
            mNotificationChannel = channel;
            mRankingMap = rankingMap;
        }

        /**
         * Creates a new {@link NotificationEnqueuedEvent} from a bundle. The bundle should come
         * from a call to {@link #toBundleImpl()}.
         *
         * <p>The expected keys are {@link #KEY_STATUS_BAR_NOTIFICATION}, {@link
         * #KEY_NOTIFICATION_CHANNEL}, and {@link #KEY_RANKING_MAP}.
         *
         * @hide
         */
        NotificationEnqueuedEvent(@NonNull Bundle bundle) {
            mStatusBarNotification = bundle.getParcelable(KEY_STATUS_BAR_NOTIFICATION,
                    StatusBarNotification.class);
            mNotificationChannel = bundle.getParcelable(KEY_NOTIFICATION_CHANNEL,
                    NotificationChannel.class);
            mRankingMap = bundle.getParcelable(KEY_RANKING_MAP,
                    NotificationListenerService.RankingMap.class);
            Preconditions.checkNotNull(mStatusBarNotification,
                    "Bundle is missing StatusBarNotification");
            Preconditions.checkNotNull(mNotificationChannel,
                    "Bundle is missing NotificationChannel");
            Preconditions.checkNotNull(mRankingMap, "Bundle is missing RankingMap");
        }

        /**
         * @return {@link StatusBarNotification} associated with this event.
         */
        @NonNull
        public StatusBarNotification getStatusBarNotification() {
            return mStatusBarNotification;
        }

        /**
         * @return {@link NotificationChannel} the notification was posted to.
         */
        @NonNull
        public NotificationChannel getNotificationChannel() {
            return mNotificationChannel;
        }

        /**
         * @return {@link NotificationListenerService.RankingMap} associated with this event.
         */
        @NonNull
        public NotificationListenerService.RankingMap getRankingMap() {
            return mRankingMap;
        }

        @Override
        @EventType
        public int getEventType() {
            return EVENT_TYPE_ENQUEUED;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            final Bundle bundle = new Bundle();
            bundle.putParcelable(KEY_STATUS_BAR_NOTIFICATION, mStatusBarNotification);
            bundle.putParcelable(KEY_NOTIFICATION_CHANNEL, mNotificationChannel);
            bundle.putParcelable(KEY_RANKING_MAP, mRankingMap);
            return bundle;
        }
    }

    /**
     * An event representing a notification being removed.
     *
     * @see NotificationListenerService#onNotificationRemoved(StatusBarNotification,
     * NotificationListenerService.RankingMap, int)
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class NotificationRemovedEvent extends NotificationEvent {
        private static final String KEY_STATUS_BAR_NOTIFICATION = "key_status_bar_notification";
        private static final String KEY_RANKING_MAP = "key_ranking_map";

        private static final String KEY_REASON = "key_reason";

        private final StatusBarNotification mStatusBarNotification;
        private final NotificationListenerService.RankingMap mRankingMap;
        @NotificationListenerService.NotificationCancelReason
        private final int mReason;

        /**
         * Creates a new {@link NotificationRemovedEvent}.
         *
         * @param statusBarNotification {@link StatusBarNotification} that was just removed.
         * @param rankingMap ranking information for currently active notifications.
         * @param reason reason code explaining why the notification was removed.
         * @see NotificationListenerService#onNotificationRemoved(StatusBarNotification,
         * NotificationListenerService.RankingMap, int)
         */
        public NotificationRemovedEvent(@NonNull StatusBarNotification statusBarNotification,
                @NonNull NotificationListenerService.RankingMap rankingMap,
                @NotificationListenerService.NotificationCancelReason int reason) {
            mStatusBarNotification = statusBarNotification;
            mRankingMap = rankingMap;
            mReason = reason;
        }

        /**
         * Creates a new {@link NotificationRemovedEvent} from a bundle. The bundle should come from
         * a call to {@link #toBundleImpl()}.
         *
         * The expected keys are {@link #KEY_STATUS_BAR_NOTIFICATION} and {@link #KEY_RANKING_MAP}.
         *
         * @hide
         */
        NotificationRemovedEvent(@NonNull Bundle bundle) {
            mStatusBarNotification = bundle.getParcelable(KEY_STATUS_BAR_NOTIFICATION,
                    StatusBarNotification.class);
            mRankingMap = bundle.getParcelable(KEY_RANKING_MAP,
                    NotificationListenerService.RankingMap.class);
            mReason = bundle.getInt(KEY_REASON, -1);
            Preconditions.checkNotNull(mStatusBarNotification,
                    "Bundle is missing StatusBarNotification");
            Preconditions.checkNotNull(mRankingMap, "Bundle is missing RankingMap");
            Preconditions.checkArgument(mReason != -1, "Bundle is missing reason");
        }

        /**
         * @return {@link StatusBarNotification} associated with this event.
         */
        @NonNull
        public StatusBarNotification getStatusBarNotification() {
            return mStatusBarNotification;
        }

        /**
         * @return {@link NotificationListenerService.RankingMap} associated with this event.
         */
        @NonNull
        public NotificationListenerService.RankingMap getRankingMap() {
            return mRankingMap;
        }

        /**
         * @return reason code for why the notification was removed.
         */
        @NotificationListenerService.NotificationCancelReason
        public int getReason() {
            return mReason;
        }

        @Override
        @EventType
        public int getEventType() {
            return EVENT_TYPE_REMOVED;
        }

        @NonNull
        @Override
        Bundle toBundleImpl() {
            final Bundle bundle = new Bundle();
            bundle.putParcelable(KEY_STATUS_BAR_NOTIFICATION, mStatusBarNotification);
            bundle.putParcelable(KEY_RANKING_MAP, mRankingMap);
            bundle.putInt(KEY_REASON, mReason);
            return bundle;
        }
    }
}
