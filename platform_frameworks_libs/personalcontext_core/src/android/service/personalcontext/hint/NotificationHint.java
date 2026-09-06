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
import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;

/**
 * A hint that contains a notification-related event.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class NotificationHint extends ContextHint {
    private final NotificationEvent mNotificationEvent;

    /**
     * Creates a new {@link NotificationHint}.
     *
     * @hide
     */
    NotificationHint(
            @NonNull ConstructorParams baseParams, @NonNull NotificationEvent notificationEvent) {
        super(baseParams);
        mNotificationEvent = notificationEvent;
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    NotificationHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        super(baseParams);
        mNotificationEvent = NotificationEvent.fromBundle(bundle);
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_NOTIFICATION;
    }

    /**
     * Get the {@link NotificationEvent} contained in this hint.
     */
    @NonNull
    public NotificationEvent getNotificationEvent() {
        return mNotificationEvent;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        return mNotificationEvent.toBundle();
    }

    /** @hide */
    @Override
    public void writeToSignatureParcel(@NonNull Parcel dest) {
        final StatusBarNotification sbn;
        if (mNotificationEvent instanceof NotificationEvent.NotificationEnqueuedEvent) {
            sbn = ((NotificationEvent.NotificationEnqueuedEvent) mNotificationEvent)
                    .getStatusBarNotification();
        } else if (mNotificationEvent instanceof NotificationEvent.NotificationRemovedEvent) {
            sbn = ((NotificationEvent.NotificationRemovedEvent) mNotificationEvent)
                    .getStatusBarNotification();
        } else {
            throw new UnsupportedOperationException("Unexpected NotificationEvent type");
        }

        dest.writeString(sbn.getPackageName());
        dest.writeString(sbn.getKey());
        dest.writeParcelable(sbn.getUser(), 0);
        dest.writeLong(sbn.getPostTime());
    }

    /**
     * Builder used to create a {@link NotificationHint}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private NotificationEvent mNotificationEvent;

        /**
         * Creates an instance of {@link Builder} with the {@link NotificationEvent} contained in
         * the hint.
         */
        public Builder(@NonNull NotificationEvent notificationEvent) {
            requireNonNull(notificationEvent, "NotificationEvent must be provided");
            mNotificationEvent = notificationEvent;
        }

        /**
         * Adds a token to the resulting {@link NotificationHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /**
         * @return the built {@link NotificationHint}.
         */
        @NonNull
        public NotificationHint build() {
            return new NotificationHint(mBaseBuilder.build(), mNotificationEvent);
        }
    }
}
