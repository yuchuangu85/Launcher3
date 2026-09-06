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

package android.service.personalcontext.embedded;

import android.service.personalcontext.embedded.IVisualizationResult;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.RenderToken;
import android.view.SurfaceControlViewHost;

/**
 * An interface implemented by an {@link InsightSurfaceVisualizerService} to be called by the
 * embedded renderer.
 * @hide
 */
interface IInsightSurfaceVisualizer {
    oneway void createVisualizationForClient(
        in PublishedContextInsightWrapper insight,
        in InsightSurfaceClientInfo clientInfo,
        in RenderToken renderToken,
        in IVisualizationResult result,
        in IOpCallback opCallback);
    oneway void onClientDisconnected(in InsightSurfaceClientInfo clientInfo,
    in IOpCallback opCallback);
}
