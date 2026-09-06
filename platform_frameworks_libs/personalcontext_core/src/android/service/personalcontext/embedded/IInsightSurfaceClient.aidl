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

import android.service.personalcontext.embedded.IInsightSurfaceSession;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.view.SurfaceControlViewHost;

/**
 * An interface implemented by an {@link InisghtSurfaceClient} and called by a visualizer.
 * @hide
 */
interface IInsightSurfaceClient {
    oneway void onSurfaceCreated(
        in SurfaceControlViewHost.SurfacePackage surfacePackage, in IInsightSurfaceSession session);
    oneway void onSurfaceReleased(in SurfaceControlViewHost.SurfacePackage surfacePackage);
    oneway void onSurfaceUpdated(in SurfaceControlViewHost.SurfacePackage surfacePackage);
    oneway void onReceiveInsight(in ContextInsightWrapper insight);
    oneway void onSizeChanged(in int width, in int height);
    oneway void onVisualizationError(int errorCode);
    oneway void onRegistered();
}
