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

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Contains the details of an action to be performed, either as an {@link PendingIntent} or a {@link
 * RemoteAction}. Note that multiple actions can be specified, in which case it is up to the
 * receiver to decide which action to perform. Use {@link #hasActionType} to determine if a
 * particular action type has been specified. It is an error for no action type to be specified.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightActionDetails implements Parcelable {

    /** @hide */
    @IntDef(
            flag = true,
            prefix = {"ACTION_TYPE_"},
            value = {ACTION_TYPE_PENDING_INTENT, ACTION_TYPE_REMOTE_ACTION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionType {}

    /** The action details contain an {@link PendingIntent}. */
    public static final int ACTION_TYPE_PENDING_INTENT = 1 << 0;

    /** The action details contain a {@link RemoteAction}. */
    public static final int ACTION_TYPE_REMOTE_ACTION = 1 << 1;

    private final int mActionTypes;
    private final PendingIntent mPendingIntent;
    private final RemoteAction mRemoteAction;

    private InsightActionDetails(
            @Nullable PendingIntent actionPendingIntent, @Nullable RemoteAction remoteAction) {
        mPendingIntent = actionPendingIntent;
        mRemoteAction = remoteAction;

        int actionTypes = 0;
        if (actionPendingIntent != null) {
            actionTypes |= ACTION_TYPE_PENDING_INTENT;
        }
        if (remoteAction != null) {
            actionTypes |= ACTION_TYPE_REMOTE_ACTION;
        }
        mActionTypes = actionTypes;
    }

    private InsightActionDetails(@NonNull Parcel in) {
        mActionTypes = in.readInt();
        mPendingIntent = in.readTypedObject(PendingIntent.CREATOR);
        mRemoteAction = in.readTypedObject(RemoteAction.CREATOR);
    }

    /** Returns the valid action types contained in this action details. */
    @ActionType
    public int getActionTypes() {
        return mActionTypes;
    }

    /**
     * Returns whether the action details has the given action type.
    */
    public boolean hasActionType(@ActionType int actionType) {
        return (mActionTypes & actionType) == actionType;
    }

    /**
     * Returns the {@link PendingIntent} these details contain, or null if there is no
     * {@link PendingIntent}.
     */
    @Nullable
    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    /**
     * Returns the {@link RemoteAction} these details contain, or null if there is no remote action.
     */
    @Nullable
    public RemoteAction getRemoteAction() {
        return mRemoteAction;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final InsightActionDetails that = (InsightActionDetails) o;
        final boolean pendingIntentEquals = (mPendingIntent == null && that.mPendingIntent == null)
                || (mPendingIntent
                != null && mPendingIntent.intentFilterEquals(that.mPendingIntent));

        return pendingIntentEquals && Objects.equals(mRemoteAction, that.mRemoteAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPendingIntent, mRemoteAction);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, @WriteFlags int flags) {
        dest.writeInt(mActionTypes);
        dest.writeTypedObject(mPendingIntent, flags);
        dest.writeTypedObject(mRemoteAction, flags);
    }

    @Override
    public String toString() {
        return "InsightActionDetails{"
                + "mPendingIntent="
                + mPendingIntent
                + ", mRemoteAction="
                + mRemoteAction
                + '}';
    }

    @NonNull
    public static final Creator<InsightActionDetails> CREATOR =
            new Creator<>() {
                @Override
                public InsightActionDetails createFromParcel(@NonNull Parcel in) {
                    return new InsightActionDetails(in);
                }

                @Override
                public InsightActionDetails[] newArray(int size) {
                    return new InsightActionDetails[size];
                }
            };

    /**
     * Builder for {@link InsightActionDetails}. A valid {@link InsightActionDetails} requires at
     * least one type of action to be set, either a {@link PendingIntent} or {@link RemoteAction}.
     * The builder will throw an {@link IllegalStateException} if neither is present when
     * {@link #build()} is invoked. Multiple actions can be set for a {@link InsightActionDetails}.
     * The {@link InsightActionDetails} consumer may choose the action that best fits their usage
     * in this case.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private PendingIntent mPendingIntent;
        private RemoteAction mRemoteAction;

        /**
         * Creates a new builder for the insight action details.
         */
        public Builder() {
        }

        /**
         * Sets the {@link PendingIntent} for the insight action details.
         *
         * @param pendingIntent the {@link PendingIntent} to set
         */
        @NonNull
        public Builder setPendingIntent(@NonNull PendingIntent pendingIntent) {
            Objects.requireNonNull(pendingIntent, "pending intent is null");
            mPendingIntent = pendingIntent;
            return this;
        }

        /**
         * Sets the {@link RemoteAction} for the insight action details.
         *
         * @param remoteAction the {@link RemoteAction} to set
         */
        @NonNull
        public Builder setRemoteAction(@NonNull RemoteAction remoteAction) {
            Objects.requireNonNull(remoteAction, "remoteAction is null");
            mRemoteAction = remoteAction;
            return this;
        }

        /**
         * Builds the {@link InsightActionDetails}.
         *
         * @return the {@link InsightActionDetails}.
         */
        @NonNull
        public InsightActionDetails build() {
            Preconditions.checkState(mPendingIntent != null || mRemoteAction != null,
                    "Neither pending intent nor remote action have been set.");
            return new InsightActionDetails(mPendingIntent, mRemoteAction);
        }
    }
}
