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

package com.android.launcher3.organizer.generator

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.model.data.ItemInfo
import javax.inject.Inject

/**
 * A [ItemInfoClassifier] that uses Android's package manager (via [LauncherApps]) to classify apps
 * based on their system-defined categories.
 *
 * It maps system constants (e.g., [ApplicationInfo.CATEGORY_GAME]) to user-friendly topic names.
 */
class PackageManagerItemInfoClassifier
@Inject
constructor(@ApplicationContext private val context: Context) : ItemInfoClassifier {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val otherTopic = context.getString(R.string.topic_category_other)

    /**
     * Resolves the [ApplicationInfo] for each item's intent and extracts its category.
     *
     * @return A list of [TopicClassifiedItem] based on package manager categories.
     */
    override suspend fun classify(
        items: List<ItemInfo>,
        topics: List<String>,
    ): List<TopicClassifiedItem> {
        return items.mapNotNull { item ->
            val appInfo =
                item.intent?.let { launcherApps?.resolveActivity(it, item.user)?.applicationInfo }
                    ?: return@mapNotNull null

            val categoryTopic = getCategoryTopic(appInfo.category, topics)
            if (topics.contains(categoryTopic)) {
                TopicClassifiedItem(item, categoryTopic, 1.0f)
            } else {
                null
            }
        }
    }

    private fun getCategoryTopic(category: Int, topics: List<String>): String {
        val categoryString =
            when (category) {
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
        return topics.find { it == categoryString } ?: otherTopic
    }
}
