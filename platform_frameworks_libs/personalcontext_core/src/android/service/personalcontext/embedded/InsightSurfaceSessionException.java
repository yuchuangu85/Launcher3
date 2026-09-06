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
import android.service.personalcontext.Flags;

import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An {@link Exception} that indicates an error has occurred with an {@link InsightSurfaceSession}.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public class InsightSurfaceSessionException extends Exception {
    /**
     * Client error codes.
     * @hide
     */
    @IntDef(prefix = {"ERROR_"},
            value = {
                    ERROR_FAILED_TO_CREATE_SESSION
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ClientError {}

    /** Error code indicating there was a failure to create a session. */
    public static final int ERROR_FAILED_TO_CREATE_SESSION = 1;

    private final int mErrorCode;
    private final String mMessage;

    /**
     * Create a new {@link InsightSurfaceSessionException}.
     * @param errorCode the error code for the exception
     * @param message an optional message for the exception
     */
    public InsightSurfaceSessionException(int errorCode, @Nullable String message) {
        mErrorCode = errorCode;
        mMessage = message;
    }

    /**
     * Return the exception's error code.
     */
    public int getErrorCode() {
        return mErrorCode;
    }

    /**
     * Return the exception's message or null if there is no message.
     */
    @Nullable
    public String getMessage() {
        return mMessage;
    }
}
