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

package com.android.launcher3.organizer.creation.screen.ui.spacecreator

import android.graphics.Bitmap

/**
 * Data class for topic information.
 *
 * @param topic the name of the topic.
 * @param icons list of icons associated with the topic.
 */
data class TopicData(val topic: String, val icons: List<Bitmap> = emptyList())

/**
 * State for [CreateScreen] and all it's composables.
 *
 * @param topics list of topic data.
 */
data class CreateScreenState(val topics: List<TopicData> = emptyList())
