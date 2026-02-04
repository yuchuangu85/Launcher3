/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.mechanics.spec

/**
 * Handler to allow for custom segment-change logic.
 *
 * This handler is called whenever the new input (position or direction) does not match
 * [currentSegment] anymore (see [SegmentData.isValidForInput]).
 *
 * This is intended to implement custom effects on direction-change.
 *
 * Implementations can return:
 * 1. [currentSegment] to delay/suppress segment change.
 * 2. `null` to use the default segment lookup based on [newPosition] and [newDirection]
 * 3. manually looking up segments on this [MotionSpec]
 * 4. create a [SegmentData] that is not in the spec.
 */
typealias OnChangeSegmentHandler =
    MotionSpec.(
        currentSegment: SegmentData, newPosition: Float, newDirection: InputDirection,
    ) -> SegmentData?

/** Generic change segment handlers. */
object ChangeSegmentHandlers {
    /** Prevents direction changes, as long as the input is still valid on the current segment. */
    val PreventDirectionChangeWithinCurrentSegment: OnChangeSegmentHandler =
        { currentSegment, newInput, newDirection ->
            currentSegment.takeIf {
                newDirection != currentSegment.direction &&
                    it.isValidForInput(newInput, currentSegment.direction)
            }
        }
}
