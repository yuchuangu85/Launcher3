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
package com.android.launcher3.workspacefunctions.translators

import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.launcher3.R

/**
 * Returns a localized topic string for the given [category].
 */
internal fun getCategoryTopic(context: Context, category: Int): String? {
    return when (category) {
        ApplicationInfo.CATEGORY_GAME -> context.getString(R.string.topic_category_games)
        ApplicationInfo.CATEGORY_AUDIO -> context.getString(R.string.topic_category_audio)
        ApplicationInfo.CATEGORY_VIDEO -> context.getString(R.string.topic_category_video)
        ApplicationInfo.CATEGORY_IMAGE -> context.getString(R.string.topic_category_image)
        ApplicationInfo.CATEGORY_SOCIAL -> context.getString(R.string.topic_category_social)
        ApplicationInfo.CATEGORY_NEWS -> context.getString(R.string.topic_category_news)
        ApplicationInfo.CATEGORY_MAPS -> context.getString(R.string.topic_category_maps)
        ApplicationInfo.CATEGORY_PRODUCTIVITY ->
            context.getString(R.string.topic_category_productivity)
        ApplicationInfo.CATEGORY_ACCESSIBILITY ->
            context.getString(R.string.topic_category_accessibility)
        else -> null
    }
}
