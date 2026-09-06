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
package com.android.personalcontext.ace.client.prototype.entitytype

import android.os.Bundle
import com.android.personalcontext.ace.client.prototype.PrototypeHint
import com.android.personalcontext.ace.client.prototype.PrototypeHint.Creator
import com.android.personalcontext.ace.client.prototype.PrototypeHintId

/** Hint for carrying entity type in Notification Suggestion Response. */
class EntityTypeHint(val entityType: String) :
    PrototypeHint(PrototypeHintId.EntityTypeHintId, EntityTypeHint) {

    override fun exportDataToBundle(bundle: Bundle) {
        bundle.putString(ENTITY_TYPE_KEY, entityType)
    }

    companion object : Creator {
        private const val ENTITY_TYPE_KEY = "entity_type"

        override fun create(bundle: Bundle): EntityTypeHint {
            val entityType = bundle.getString(ENTITY_TYPE_KEY) ?: ""
            return EntityTypeHint(entityType)
        }
    }
}
