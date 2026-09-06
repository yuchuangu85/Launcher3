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

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.annotation.RequiresPermission
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.model.data.ItemInfo
import javax.inject.Inject

/** A [ItemInfoClassifier] that uses usage statistics to identify the most used apps. */
class MostUsedItemInfoClassifier
@Inject
constructor(@ApplicationContext private val context: Context) : ItemInfoClassifier {

    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    @RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
    override suspend fun classify(
        items: List<ItemInfo>,
        topics: List<String>,
    ): List<TopicClassifiedItem> {
        val mostUsedTopic = context.getString(R.string.topic_category_most_used)
        if (!topics.contains(mostUsedTopic)) {
            return emptyList()
        }

        val endTime = System.currentTimeMillis()
        val beginTime = endTime - USAGE_STATS_PERIOD
        val usageStatsMap =
            usageStatsManager?.queryAndAggregateUsageStats(beginTime, endTime) ?: return emptyList()

        // This algorithm filters out all apps that have usage stats of less than
        // HIGH_CONFIDENCE_THRESHOLD_MS (1 minute) in foreground. We get the highest usage app
        // which gets a score of 1, and normalize all other scores within the [0.8, 1] range based
        // off of that app's usage. If no items meet the minimum usage, we return an empty list.
        // The idea with this is to
        var highestValue = 0L
        val usedItems =
            items.mapNotNull { item ->
                val packageName = item.targetPackage
                val stats = usageStatsMap[packageName]
                val usageTime = stats?.totalTimeInForeground ?: 0L
                if (usageTime >= HIGH_CONFIDENCE_THRESHOLD_MS) {
                    if (usageTime > highestValue) {
                        highestValue = usageTime
                    }
                    item to usageTime
                } else {
                    null
                }
            }

        if (usedItems.isEmpty()) {
            return emptyList()
        }

        return usedItems.map { (item, usageTime) ->
            // We just normalize score based off of the most used app.
            val normalized = usageTime / highestValue
            val score = HIGH_CONFIDENCE_SCORE + (normalized * (MAX_SCORE - HIGH_CONFIDENCE_SCORE))
            TopicClassifiedItem(item, mostUsedTopic, score)
        }
    }

    companion object {
        private const val USAGE_STATS_PERIOD = 1000L * 60 * 60 * 24 // 24 hours
        private const val HIGH_CONFIDENCE_THRESHOLD_MS = 60 * 1000L // 1 minute
        private const val HIGH_CONFIDENCE_SCORE = 0.8f
        private const val MAX_SCORE = 1.0f
    }
}
