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
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import javax.inject.Inject

/** Provides the default set of topics for classification. */
class DefaultTopicProvider @Inject constructor(@ApplicationContext private val context: Context) :
    TopicProvider {
    override suspend fun getTopics(): List<String> {
        return listOf(
            context.getString(R.string.topic_category_games),
            context.getString(R.string.topic_category_audio),
            context.getString(R.string.topic_category_video),
            context.getString(R.string.topic_category_image),
            context.getString(R.string.topic_category_social),
            context.getString(R.string.topic_category_news),
            context.getString(R.string.topic_category_maps),
            context.getString(R.string.topic_category_productivity),
            context.getString(R.string.topic_category_accessibility),
            context.getString(R.string.topic_category_most_used),
        )
    }
}
