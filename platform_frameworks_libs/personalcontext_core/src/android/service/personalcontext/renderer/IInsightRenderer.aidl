/**
 * Copyright (c) 2025, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.personalcontext.renderer;

import android.os.ParcelUuid;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.renderer.IGetFilterCallback;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.IOpCallback;

/** @hide */
oneway interface IInsightRenderer {
    /** Called with the published insight to render. The associated RenderToken is provided. */
    void render(in ParcelUuid componentId, in PublishedContextInsightWrapper publishedInsight,
    in RenderToken renderToken, in IOpCallback opCallback);

    /** Provides configuration information to the renderer. */
    void configure(in ParcelUuid componentId);

    /** Gets a filter to be used when deciding whether to send an insight to this renderer. */
    void getFilter(in ParcelUuid componentId, in IGetFilterCallback callback,
    in IOpCallback opCallback);
}
