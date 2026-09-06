/**
 * Copyright (c) 2026, The Android Open Source Project
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

import android.os.ResultReceiver;
import android.service.personalcontext.embedded.IVisualizationResult;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.IOpCallback;
import android.view.SurfaceControlViewHost;

/**
 * An interface implemented by an {@link InsightSurfaceVisualizerService} representing an open
 * session with an {@link InsightSurfaceClient}.
 * @hide
 */
interface IInsightSurfaceSession {
    const int UPDATE_OK = 0;
    const int UPDATE_DECLINED = 1;

    /** The client has updated its info. */
    oneway void onClientUpdated(
        in InsightSurfaceClientInfo oldClientInfo,
        in InsightSurfaceClientInfo newClientInfo,
        in ResultReceiver result,
        in IOpCallback opCallback);
}
