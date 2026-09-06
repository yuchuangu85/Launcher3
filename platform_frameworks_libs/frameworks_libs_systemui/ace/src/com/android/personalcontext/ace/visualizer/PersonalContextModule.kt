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
package com.android.personalcontext.ace.visualizer

import com.android.personalcontext.ace.visualizer.compose.ComposeViewFactory
import com.android.personalcontext.ace.visualizer.compose.ComposeViewFactoryImpl
import com.android.personalcontext.ace.visualizer.connector.VisualizerServiceConnector
import com.android.personalcontext.ace.visualizer.connector.VisualizerServiceConnectorImpl
import com.android.personalcontext.ace.visualizer.session.VisualizerSessionFactory
import com.android.personalcontext.ace.visualizer.session.VisualizerSessionFactoryImpl
import com.android.personalcontext.ace.visualizer.templates.VisualizerTemplate
import com.android.personalcontext.ace.visualizer.templates.call.CallVisualizerTemplate
import com.android.personalcontext.ace.visualizer.templates.message.MessageVisualizerTemplate
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet

/** Dagger module for platform dependencies. */
@Module
interface PersonalContextModule {
    companion object {

        @Provides
        fun provideVisualizerServiceConnector(
            impl: Lazy<VisualizerServiceConnectorImpl>
        ): VisualizerServiceConnector {
            return impl.get()
        }

        @Provides
        fun provideVisualizerSessionFactory(
            impl: Lazy<VisualizerSessionFactoryImpl>
        ): VisualizerSessionFactory {
            return impl.get()
        }

        @Provides
        fun provideComposeViewFactory(impl: Lazy<ComposeViewFactoryImpl>): ComposeViewFactory {
            return impl.get()
        }

        @Provides
        @IntoSet
        fun provideCallVisualizerTemplate(impl: Lazy<CallVisualizerTemplate>): VisualizerTemplate {
            return impl.get()
        }

        @Provides
        @IntoSet
        fun provideMessageVisualizerTemplate(
            impl: Lazy<MessageVisualizerTemplate>
        ): VisualizerTemplate {
            return impl.get()
        }
    }
}
