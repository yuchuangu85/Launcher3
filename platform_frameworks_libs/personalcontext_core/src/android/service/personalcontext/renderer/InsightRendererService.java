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

package android.service.personalcontext.renderer;

import static android.annotation.SystemApi.Client.PRIVILEGED_APPS;

import android.annotation.FlaggedApi;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.service.personalcontext.Flags;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.InsightFilter;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.util.BinderRequestProcessor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * The base insight renderer service, which is a service responsible for rendering {@link
 * PublishedContextInsight}s to the user as suggestions or other actionable items. This is an
 * abstract class intended to be subclassed by concrete renderer services. Subclasses need to
 * implement {@link #onInitializeFilter()} (which should return an {@link InsightFilter} indicating
 * what types of insights the renderer will accept), and {@link #onRender} (which the personal
 * context engine will call with a list of {@link PublishedContextInsight}s to be rendered).
 *
 * <p>You must declare the service in the AndroidManifest of the app hosting the service with the
 * {@link android.Manifest.permission#BIND_CONTEXT_COMPONENT_SERVICE} permission, and include an
 * intent filter with the necessary action indicating that it is an {@link InsightRendererService}
 * ({@link #SERVICE_INTERFACE}). The application must have the {@link
 * android.Manifest.permission#PERSONAL_CONTEXT_RECEIVE_INSIGHTS} permission.
 *
 * <p>For example:
 *
 * <pre>
 *     &lt;uses-permission
 *         android:name="android.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS"/&gt;
 *
 *     &lt;service android:name=".ExampleInsightRendererService"
 *             android:exported="true"
 *             android:permission="android.permission.BIND_CONTEXT_COMPONENT_SERVICE"&gt;
 *         &lt;intent-filter&gt;
 *             &lt;action
 *             android:name="android.service.personalcontext.renderer.InsightRendererService"
 *             /&gt;
 *         &lt;/intent-filter&gt;
 *     &lt;/service&gt;
 * </pre>
 *
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class InsightRendererService extends Service {
    private static final String TAG = "InsightRendererService";

    /**
     * The {@link Intent} that must be declared as handled by the service.
     *
     * <p>To be supported, the service must also require the
     * {@link android.Manifest.permission#BIND_CONTEXT_COMPONENT_SERVICE} permission.
     */
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String SERVICE_INTERFACE =
            "android.service.personalcontext.renderer.InsightRendererService";

    private UUID mComponentId = null;

    private Executor mBinderExecutor = null;

    @Nullable
    @Override
    public final IBinder onBind(@Nullable Intent intent) {
        final Executor executor = mBinderExecutor != null
                ? mBinderExecutor
                : new HandlerExecutor(new Handler(Looper.getMainLooper()));
        return new Binder(this, executor);
    }

    /**
     * Mint and return a new {@link RenderToken}.
     *
     * A {@link RenderToken} uniquely identifies this renderer to the personal context engine.
     * Triggers can include a {@link RenderToken} in hints to indicate that insights containing
     * those hints are meant to be rendered by the renderer that minted that token. This method
     * will throw an error if it is called before {@link #onConnected} is called.
     *
     * @param tag An optional {@link String} value that can be associated with the token for future
     *            identification.
     * @return a token that uniquely identifies this renderer
     * @throws IllegalStateException if {@link #onConnected()} has not yet been called
     */
    @NonNull
    public final RenderToken mintRenderToken(@Nullable String tag) {
        if (mComponentId == null) {
            throw new IllegalStateException(
                    "RenderTokens can not be minted until after onConnected has been called");
        }
        return new RenderToken(mComponentId, tag);
    }

    /**
     * Mint and return a new {@link RenderToken} without a specified tag.
     *
     * @return a token that uniquely identifies this renderer
     * @see #mintRenderToken(String)
     * @throws IllegalStateException if {@link #onConnected()} has not yet been called
     */
    @NonNull
    public final RenderToken mintRenderToken() {
        if (mComponentId == null) {
            throw new IllegalStateException(
                    "RenderTokens can not be minted until after onConnected has been called");
        }
        return new RenderToken(mComponentId, null);
    }

    private void configure(UUID componentId) {
        if (mComponentId == null) {
            mComponentId = componentId;
            onConnected();
        }
    }

    /**
     * Called when the renderer has been configured and is ready to receive insights.
     * Any actions related to this method should complete before returning.
     */
    public void onConnected() {
        // Default implementation does nothing.
    }

    /**
     * Sets the executor to be used when methods are invoked on this service. By default, an
     * Executor running on the main looper is used. This method should be called within
     * {@link #onCreate()}.
     *
     * @param executor The {@link Executor} to run calls on.
     */
    public final void setExecutor(@androidx.annotation.NonNull Executor executor) {
        mBinderExecutor = executor;
    }

    /**
     * The renderer should return an {@link InsightFilter} that will be used to filter the insights
     * that this renderer's {@link #onRender} method will be called with.
     *
     * The result of this method will be cached and re-used between service bindings. If the filter
     * returned by this method changes, the changes will be ignored.
     *
     * @return a filter that restricts the {@link PublishedContextInsight}s this renderer will
     * receive
     */
    @NonNull
    public abstract InsightFilter onInitializeFilter();

    /**
     * This method will be called when the given
     * {@link android.service.personalcontext.insight.PublishedContextInsight} needs to be rendered.
     * Typically the renderer would use the insight provided to prompt the user with information,
     * or to interact in some way. For example, a notification renderer might use the insight to
     * modify an existing notification with actions the user might want to take.
     *
     * @param insight the {@link android.service.personalcontext.insight.PublishedContextInsight}
     *                to render
     * @param renderToken the {@link RenderToken} that was used to select this renderer
     */
    public abstract void onRender(@NonNull PublishedContextInsight insight,
            @NonNull RenderToken renderToken);

    private static final class Binder extends IInsightRenderer.Stub {
        private final BinderRequestProcessor<InsightRendererService> mRequestProcessor;

        Binder(InsightRendererService service, Executor executor) {
            mRequestProcessor = new BinderRequestProcessor.Builder<>(service, executor)
                    .setInitializer(InsightRendererService::configure)
                    .build();
        }

        @Override
        public void render(ParcelUuid componentId, PublishedContextInsightWrapper insight,
                RenderToken renderToken, IOpCallback opCallback) {
            mRequestProcessor.execute(
                    new BinderRequestProcessor.ExecutionParams.Builder<InsightRendererService>(
                            opCallback, serviceInstance ->
                            serviceInstance.onRender(insight.getPublishedContextInsight(),
                                    renderToken)
                    ).setComponentId(componentId).build());
        }

        @Override
        public void configure(ParcelUuid componentId) {
            // This should no longer be called and will be removed soon.
        }

        @Override
        public void getFilter(ParcelUuid componentId, IGetFilterCallback callback,
                IOpCallback opCallback) {
            mRequestProcessor.execute(
                    new BinderRequestProcessor.ExecutionParams.Builder<InsightRendererService>(
                            opCallback, serviceInstance ->
                            callback.updateFilter(serviceInstance.onInitializeFilter())
                    ).setComponentId(componentId).build());
        }
    }
}
