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

package com.android.launcher3.appfunctions.workspace.validators

import com.android.launcher3.appfunctions.workspace.ErrorCode

/** Result of a validation check. */
sealed class ValidationResult {
    /** The request is valid and will succeed. */
    object Valid : ValidationResult()
    /**
    The request is invalid and will fail
        @param message: Summary of the error causing the failure.
        @param errorCode: The type of error.
        @param resolutionDetails: Details for how this error may be resolved or avoided.
     */
    data class Invalid(
        val message: String,
        val errorCode: ErrorCode? = null,
        val resolutionDetails: String? = null,
    ) : ValidationResult()
}

/** Validator for workspace item selectors. */
interface SelectorValidator {
    /** Performs the validation check. */
    suspend fun validate(): ValidationResult
}
