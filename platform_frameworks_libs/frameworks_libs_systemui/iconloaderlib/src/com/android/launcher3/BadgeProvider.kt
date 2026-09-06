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

package com.android.launcher3

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.android.launcher3.icons.BitmapInfo.Companion.FLAG_THEMED
import com.android.launcher3.icons.BitmapInfo.Companion.hasMask
import com.android.launcher3.icons.R
import com.android.launcher3.icons.UserBadgeDrawable
import com.android.launcher3.icons.mono.ColorList

/**
 * Provides badge drawable based on [BadgeType] for a [com.android.launcher3.icons.BitmapInfo].
 * Custom providers can be set on [com.android.launcher3.icons.IconThemeController].
 */
interface BadgeProvider {

    fun getDrawable(context: Context, type: BadgeType, creationFlag: Int): Drawable

    object DefaultBadgeProvider : BadgeProvider {

        override fun getDrawable(context: Context, type: BadgeType, creationFlag: Int): Drawable =
            if (creationFlag.hasMask(FLAG_THEMED)) {
                UserBadgeDrawable(
                    context.getDrawable(type.drawableRes),
                    context.getColor(R.color.themed_badge_icon_background_color),
                    context.getColor(R.color.themed_badge_icon_color),
                )
            } else if (type == BadgeType.SYSTEM_HEADLESS) {
                // SYSTEM_HEADLESS requires a white icon on a colored background,
                // which is the inverse of standard profile badges.
                UserBadgeDrawable(
                    context.getDrawable(type.drawableRes),
                    context.getColor(type.colorRes),
                    Color.WHITE,
                )
            } else {
                UserBadgeDrawable(
                    context.getDrawable(type.drawableRes),
                    Color.WHITE,
                    context.getColor(type.colorRes),
                )
            }
    }

    class ColoredBadgeProvider(val colorProvider: (Context) -> ColorList) : BadgeProvider {
        override fun getDrawable(context: Context, type: BadgeType, creationFlag: Int): Drawable {
            val colors = colorProvider(context)
            if (type == BadgeType.SYSTEM_HEADLESS) {
                return UserBadgeDrawable(
                    context.getDrawable(type.drawableRes),
                    colors.badgeForegroundColor,
                    colors.badgeBackgroundColor,
                )
            }
            return UserBadgeDrawable(
                context.getDrawable(type.drawableRes),
                colors.badgeBackgroundColor,
                colors.badgeForegroundColor,
            )
        }
    }

    /**
     * Drawables backing a specific badge shown on app icons.
     *
     * @param persistedName name of the info, which can be used to tag persisted data
     * @param drawableRes Drawable resource for the badge.
     * @param colorRes Color resource to tint the badge.
     */
    enum class BadgeType(
        val persistedName: String,
        @field:DrawableRes @param:DrawableRes val drawableRes: Int,
        @field:ColorRes @param:ColorRes val colorRes: Int,
    ) {
        WORK("work", R.drawable.ic_work_app_badge, R.color.badge_tint_work),
        CLONE("clone", R.drawable.ic_clone_app_badge, R.color.badge_tint_clone),
        PRIVATE("private", R.drawable.ic_private_profile_app_badge, R.color.badge_tint_private),
        SYSTEM_HEADLESS(
            "system_headless",
            R.drawable.ic_system_headless_app_badge,
            R.color.badge_tint_system_headless,
        ),
        INSTANT("instant", R.drawable.ic_instant_app_badge, R.color.badge_tint_instant),
    }
}
