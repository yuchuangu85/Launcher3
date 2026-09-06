/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.personalcontext.ace.client.clientsdk.state

/**
 * Allows transiently overriding the resolved z-order from the [AceEmbeddedOverlapBehavior], while
 * still allowing the other effects from the [AceEmbeddedOverlapBehavior] to trigger.
 */
sealed interface AceEmbeddedZOrderOverrideValue {

    /** Do not override the resolved z-order from the [AceEmbeddedOverlapBehavior]. */
    data object None : AceEmbeddedZOrderOverrideValue

    /**
     * Forces the z-order to be [OnTop], disabling any behaviors defined in
     * [AceEmbeddedOverlapBehavior].
     */
    data object ForceOnTop : AceEmbeddedZOrderOverrideValue

    /**
     * Forces the z-order to be [Behind], but continues to trigger any behaviors defined in
     * [AceEmbeddedOverlapBehavior].
     */
    data object ForceBehind : AceEmbeddedZOrderOverrideValue
}
