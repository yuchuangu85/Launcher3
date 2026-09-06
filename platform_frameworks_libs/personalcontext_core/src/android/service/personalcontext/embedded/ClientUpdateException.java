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

package android.service.personalcontext.embedded;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A {@link RuntimeException} indicating that an error occurred when updating an
 * {@link InsightSurfaceClient}.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ClientUpdateException  extends RuntimeException implements Parcelable {
    /**
     * Error code associated with the client update failures.
     * @hide
     */
    @IntDef(
            value = {
                    UPDATE_ERROR_UNKNOWN,
                    UPDATE_ERROR_DECLINED_BY_VISUALIZER,
            })
    @Retention(RetentionPolicy.SOURCE)
    @interface UpdateError {
    }

    /** Client update has failed for an unknown reason. */
    public static final int UPDATE_ERROR_UNKNOWN = 1;

    /** Client update has failed because it was declined by the visualizer. */
    public static final int UPDATE_ERROR_DECLINED_BY_VISUALIZER = 2;

    private final int mErrorCode;

    @NonNull
    private final InsightSurfaceClientUpdate mUpdate;

    /**
     * Create a new {@link ClientUpdateException}.
     */
    ClientUpdateException(int errorCode, @NonNull InsightSurfaceClientUpdate update) {
        mErrorCode = errorCode;
        mUpdate = update;
    }

    private ClientUpdateException(Parcel in) {
        mErrorCode = in.readInt();
        mUpdate = in.readParcelable(
                InsightSurfaceClientUpdate.class.getClassLoader(),
                InsightSurfaceClientUpdate.class);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mErrorCode);
        dest.writeParcelable(mUpdate, flags);
    }

    /**
     * Return the error code for this exception.
     */
    public int getErrorCode() {
        return mErrorCode;
    }

    /**
     * Return the {@link InsightSurfaceClientUpdate} that caused this exception.
     */
    @NonNull
    public InsightSurfaceClientUpdate getUpdate() {
        return mUpdate;
    }

    @NonNull
    public static final Creator<ClientUpdateException> CREATOR =
            new Creator<>() {
                @Override
                public ClientUpdateException createFromParcel(Parcel in) {
                    return new ClientUpdateException(in);
                }

                @Override
                public ClientUpdateException[] newArray(int size) {
                    return new ClientUpdateException[size];
                }
            };
}
