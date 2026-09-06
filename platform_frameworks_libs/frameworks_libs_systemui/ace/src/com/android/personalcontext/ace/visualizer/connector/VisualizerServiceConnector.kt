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
package com.android.personalcontext.ace.visualizer.connector

import android.content.Context
import android.service.personalcontext.embedded.InsightSurfaceClientInfo
import android.view.View
import com.android.personalcontext.ace.common.wrappers.IInsightSurfaceClientInfo
import com.android.personalcontext.ace.common.wrappers.IPublishedContextInsight
import com.android.personalcontext.ace.common.wrappers.IRenderToken

/**
 * Connector for [com.google.android.apps.pixel.psi.ace.PsiVisualizerService] to make it testable.
 *
 * This interface defines the contract for a service that visualizes insights and outputs a View.
 */
interface VisualizerServiceConnector {

    /** Called when a client is connected. */
    fun onClientConnected(info: InsightSurfaceClientInfo)

    /** Visualizes the given insights and returns a View. */
    fun onCreateEmbeddedView(
        context: Context,
        publishedInsight: IPublishedContextInsight,
        renderToken: IRenderToken?,
        info: IInsightSurfaceClientInfo,
    ): View?

    /** Updates the View. */
    fun onClientUpdated(
        oldClientInfo: IInsightSurfaceClientInfo,
        newClientInfo: IInsightSurfaceClientInfo,
    ): Boolean

    /** Called when a client is disconnected. */
    fun onClientDisconnected(info: IInsightSurfaceClientInfo)
}
