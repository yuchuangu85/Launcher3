/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.launcher3.util

import android.os.UserHandle
import com.android.launcher3.icons.BitmapInfo
import com.android.users.UserType

/**
 * Data class which stores various properties of a [android.os.UserHandle] which affects rendering
 */
data class UserIconInfo
@JvmOverloads
constructor(
    @JvmField val user: UserHandle,
    @JvmField val type: UserType,
    @JvmField val userSerial: Long = user.hashCode().toLong(),
) {

    val isMain: Boolean
        get() = type == UserType.MAIN

    val isWork: Boolean
        get() = type == UserType.WORK

    val isCloned: Boolean
        get() = type == UserType.CLONED

    val isSystemHeadless: Boolean
        get() = type == UserType.SYSTEM_HEADLESS

    val isPrivate: Boolean
        get() = type == UserType.PRIVATE

    fun applyBitmapInfoFlags(op: FlagOp): FlagOp =
        op.setFlag(BitmapInfo.FLAG_WORK, isWork)
            .setFlag(BitmapInfo.FLAG_CLONE, isCloned)
            .setFlag(BitmapInfo.FLAG_PRIVATE, isPrivate)
            .setFlag(BitmapInfo.FLAG_SYSTEM_HEADLESS, isSystemHeadless)
}
