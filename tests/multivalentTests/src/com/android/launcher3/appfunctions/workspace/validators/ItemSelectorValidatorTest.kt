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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.appfunctions.workspace.ErrorCode
import com.android.launcher3.appfunctions.workspace.ItemSelectorSpec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemSelectorValidatorTest {

    @Test
    fun validate_withLabel_returnsValid(): Unit = runBlocking {
        val selector = createSelector(label = "Gmail")
        val validator = ItemSelectorValidator(selector)

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun validate_withHotseatRank_returnsValid(): Unit = runBlocking {
        val selector = createSelector(hotseatRank = 0)
        val validator = ItemSelectorValidator(selector)

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun validate_withCoordinates_returnsValid(): Unit = runBlocking {
        val selector = createSelector(screenIndex = 0, x = 1, y = 2)
        val validator = ItemSelectorValidator(selector)

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun validate_withComponent_returnsValid(): Unit = runBlocking {
        val selector = createSelector(packageName = "com.pkg", className = "com.cls")
        val validator = ItemSelectorValidator(selector)

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun validate_withIncompleteCoordinates_returnsInvalid(): Unit = runBlocking {
        val selector = createSelector(screenIndex = 0, x = 1) // missing y
        val validator = ItemSelectorValidator(selector)

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
        val invalidResult = result as ValidationResult.Invalid
        assertThat(invalidResult.message).isEqualTo("Invalid item selector")
        assertThat(invalidResult.errorCode?.code).isEqualTo(ErrorCode.INVALID_PARAMETERS)
    }

    @Test
    fun validate_withIncompleteComponent_returnsInvalid(): Unit = runBlocking {
        val selector = createSelector(packageName = "com.pkg") // missing className
        val validator = ItemSelectorValidator(selector)

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun validate_emptySelector_returnsInvalid(): Unit = runBlocking {
        val selector = createSelector()
        val validator = ItemSelectorValidator(selector)

        val result = validator.validate()

        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    private fun createSelector(
        label: String? = null,
        screenIndex: Int? = null,
        x: Int? = null,
        y: Int? = null,
        hotseatRank: Int? = null,
        packageName: String? = null,
        className: String? = null
    ) = ItemSelectorSpec(
        label = label,
        screenIndex = screenIndex,
        x = x,
        y = y,
        hotseatRank = hotseatRank,
        packageName = packageName,
        className = className
    )
}
