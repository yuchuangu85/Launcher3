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
import com.android.launcher3.appfunctions.workspace.ItemSelectorSpec

/**
 * Validates that an [ItemSelectorSpec] is correctly specified.
 *
 * @property selector The selector to validate.
 */
class ItemSelectorValidator(private val selector: ItemSelectorSpec) : SelectorValidator {
    override suspend fun validate(): ValidationResult {
        // Valid if at least one identification method is complete and valid.
        val isValid = (selector.label != null) || (selector.hotseatRank != null) ||
                (selector.screenIndex != null && selector.x != null && selector.y != null) ||
                (selector.packageName != null && selector.className != null)

        return if (isValid) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(
                message = "Invalid item selector",
                errorCode = ErrorCode(ErrorCode.INVALID_PARAMETERS)
            )
        }
    }
}