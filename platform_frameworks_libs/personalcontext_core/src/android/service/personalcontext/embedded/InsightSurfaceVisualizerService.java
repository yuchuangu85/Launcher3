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

package android.service.personalcontext.embedded;

import static android.annotation.SystemApi.Client.PRIVILEGED_APPS;
import static android.service.personalcontext.embedded.InsightSurfaceSessionException.ERROR_FAILED_TO_CREATE_SESSION;

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.IBinder;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.service.personalcontext.Flags;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.util.BinderRequestProcessor;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.InputTransferToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * An InsightSurfaceVisualizerService is a personal context system component that is responsible
 * for generating embeddable {@link View}s from {@link ContextInsight}s produced by the
 * personal context engine. Implementations should subclass this class and override the
 * {@link #onCreateEmbeddedView} method to return a {@link View} to be installed in a connected
 * client surface. Subclasses will be notified that a client has connected or disconnected via the
 * {@link #onClientConnected} and {@link #onClientDisconnected} methods. The
 * {@link #onClientConnected} callback will be called after {@link #onCreateEmbeddedView} returns a
 * non-null {@link View}. A connected client will remain connected until it explicitly disconnects,
 * at which point the {@link #onClientDisconnected} callback will be called.
 *
 * <p>Visualizer subclasses are expected to create a {@link View} that will be embedded in a
 * connected client surface based on the {@link ContextInsight} they receive in
 * {@link #onCreateEmbeddedView}. Client apps publish {@link ContextHint}s to the personal context
 * engine, which can then produce insights that will arrive at a visualizer to be turned into
 * a {@link View}. For example, a messaging application might publish hints about the current
 * conversation that turn into an insight to display a time to meet or a phone number to call. A
 * visualizer can then use that insight to create a suitable {@link View} that would be embedded in
 * the client application.
 *
 * <p>Client properties (such as configuration and dimensions) are passed to visualizers in
 * an {@link InsightSurfaceClientInfo} object, which visualizers receive in
 * {@link #onClientConnected}. Updates to client properties will be reported to visualizer
 * subclasses by calls to {@link #onClientUpdated}, which will receive both old and updated
 * {@link InsightSurfaceClientInfo} objects. See {@link InsightSurfaceClientInfo} for more
 * information about the properties that a client can report to a visualizer.
 *
 * <p>You must declare the service in the AndroidManifest of the app hosting the service with the
 * {@link Manifest.permission#BIND_INSIGHT_SURFACE_VISUALIZER_SERVICE} permission, and include an
 * intent filter with the necessary action indicating that it is an {@link
 * InsightSurfaceVisualizerService} ({@link #SERVICE_INTERFACE}). The application must have the
 * {@link Manifest.permission#PERSONAL_CONTEXT_RECEIVE_INSIGHTS} permission.
 *
 * <p>For example:
 * <pre>
 *     &lt;uses-permission
 *         android:name="android.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS"/&gt;
 *
 *     &lt;service android:name=".ExampleInsightSurfaceVisualizerService"
 *         android:exported="true"
 *         android:permission="android.permission.BIND_INSIGHT_SURFACE_VISUALIZER_SERVICE"&gt;
 *         &lt;intent-filter&gt;
 *             &lt;action
 *           android:name="android.service.personalcontext.embedded.InsightSurfaceVisualizerService"
 *             /&gt;
 *         &lt;/intent-filter&gt;
 *     &lt;/service&gt;
 * </pre>
 *
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class InsightSurfaceVisualizerService extends Service {
    private static final String TAG = "InsightSurfaceVisualizr";

    /** The {@link Intent} that must be declared as handled by the service. */
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String SERVICE_INTERFACE =
            "android.service.personalcontext.embedded.InsightSurfaceVisualizerService";

    private final Injector mInjector;

    private Executor mBinderExecutor;
    private BinderService mBinder;

    /**
     * A helper object to create {@link SurfaceControlViewHost}s.
     * @hide
     */
    @VisibleForTesting
    public interface SurfaceControlViewHostFactory {
        /**
         * Create a {@link SurfaceControlViewHost}.
         */
        SurfaceControlViewHost createSurfaceControlViewHost(
                @NonNull Context context,
                @NonNull Display display,
                @Nullable InputTransferToken inputTransferToken);
    }

    /**
     * A helper object to inject dependencies into {@link InsightSurfaceVisualizerService}.
     * @hide
     */
    @VisibleForTesting
    public interface Injector {
        /**
         * Return the display context that will be used when instantiating the {@link View}
         * hierarchy to be returned from the visualizer.
         * @return the display {@link Context}
         */
        Context getDisplayContext();

        /**
         * Get the default display that will be used when instantiating the {@link View} hierarchy
         * to be returned from the visualizer.
         * @return the default {@link Display}
         */
        Display getDisplay();

        /**
         * Return the SurfaceControlViewHostFactory used to create a new
         * {@link SurfaceControlViewHost}.
         */
        SurfaceControlViewHostFactory getSurfaceControlViewHostFactory();

        /**
         * Create a new root view to hold the surface view.
         */
        RootView createRootView(
                Context context, InsightSurfaceClientInfo clientInfo, SurfaceControlViewHost host);
    }

    private static final class DefaultInjector implements Injector {
        private final Context mContext;
        private final SurfaceControlViewHostFactory mSurfaceControlViewHostFactory =
                SurfaceControlViewHost::new;

        DefaultInjector(Context context) {
            mContext = context;
        }

        @Override
        public Context getDisplayContext() {
            return mContext.getApplicationContext().createDisplayContext(getDisplay())
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION, null);
        }

        @Override
        public Display getDisplay() {
            // TODO(b/462739275): Needs to support displays other than DEFAULT_DISPLAY
            return mContext
                    .getApplicationContext()
                    .getSystemService((DisplayManager.class))
                    .getDisplay(Display.DEFAULT_DISPLAY);
        }

        @Override
        public SurfaceControlViewHostFactory getSurfaceControlViewHostFactory() {
            return mSurfaceControlViewHostFactory;
        }

        @Override
        public RootView createRootView(
                Context context, InsightSurfaceClientInfo clientInfo, SurfaceControlViewHost host) {
            return new RootView(context, clientInfo, host);
        }
    }

    /**
     * Create an {@link InsightSurfaceVisualizerService}.
     */
    public InsightSurfaceVisualizerService() {
        mInjector = new DefaultInjector(this);
    }

    /**
     * Create an {@link InsightSurfaceVisualizerService} for test purposes.
     *
     * @param injector an {@link Injector} to be provided by tests
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceVisualizerService(Injector injector) {
        mInjector = injector;
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

    @Nullable
    @Override
    public final IBinder onBind(@Nullable Intent intent) {

        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            if (mBinder == null) {
                mBinder =
                        new BinderService(
                                this,
                                mInjector.getDisplayContext(),
                                mInjector.getDisplay(),
                                mBinderExecutor != null
                                    ? mBinderExecutor
                                    : new HandlerExecutor(new Handler(Looper.getMainLooper())),
                                mInjector.getSurfaceControlViewHostFactory());
            }
            return mBinder;
        }
        Log.w(
                TAG,
                "Tried to bind to wrong intent (should be " + SERVICE_INTERFACE + "): " + intent);
        return null;
    }

    /**
     * An insight surface client has connected to the visualizer.
     *
     * @param client the {@link InsightSurfaceClientInfo} of the connecting client
     */
    public abstract void onClientConnected(@NonNull InsightSurfaceClientInfo client);

    /**
     * An insight surface client has disconnected from the visualizer.
     *
     * @param client the {@link InsightSurfaceClientInfo} of the disconnecting client.
     */
    public abstract void onClientDisconnected(@NonNull InsightSurfaceClientInfo client);

    /**
     * Create and return a {@link View} for the given {@link InsightSurfaceClientInfo} based on the
     * list of {@link PublishedContextInsight}s.
     *
     * @param context a {@link Context} suitable for creating {@link View}s in the current display
     * @param publishedContextInsight insight the {@link PublishedContextInsight} that subclasses
     *                                can use to create the embedded {@link View}
     * @param renderToken the {@link RenderToken} associated with the insight
     * @param info        the {@link InsightSurfaceClientInfo} containing information about the
     *                    client
     * @return the {@link View} that will be passed to the client
     */
    @Nullable
    public abstract View onCreateEmbeddedView(
            @NonNull Context context,
            @NonNull PublishedContextInsight publishedContextInsight,
            @Nullable RenderToken renderToken,
            @NonNull InsightSurfaceClientInfo info);

    /**
     * An insight surface client has been updated. Subclasses that wish to handled updates should
     * override this method and return whether the update was accepted. The return value will be
     * sent back to the client via an {@link OutcomeReceiver} callback (see
     * {@link InsightSurfaceSession#update} for more details).
     *
     * @param oldClientInfo the old {@link InsightSurfaceClientInfo} for the client
     * @param newClientInfo the new {@link InsightSurfaceClientInfo} for the client
     * @return true if the update was accepted by the visualizer; the client will be informed of
     * update acceptance through its {@link InsightSurfaceSession}
     */
    public boolean onClientUpdated(
            @NonNull InsightSurfaceClientInfo oldClientInfo,
            @NonNull InsightSurfaceClientInfo newClientInfo) {
        return false;
    }

    private void onClientUpdated(
            InsightSurfaceClientInfo oldClientInfo,
            InsightSurfaceClientInfo newClientInfo,
            ResultReceiver receiver) {
        final boolean success = onClientUpdated(oldClientInfo, newClientInfo);
        if (receiver != null) {
            receiver.send(
                    success
                            ? IInsightSurfaceSession.UPDATE_OK
                            : IInsightSurfaceSession.UPDATE_DECLINED,
                    null);
        }
    }

    private static final class BinderService extends IInsightSurfaceVisualizer.Stub {
        private final Context mContext;
        private final Display mDisplay;
        private final SurfaceControlViewHostFactory mSurfaceControlViewHostFactory;
        private final Map<UUID, SurfaceInfo> mSurfacesByClient = new HashMap<>();
        private final BinderRequestProcessor<InsightSurfaceVisualizerService> mRequestProcessor;

        BinderService(
                InsightSurfaceVisualizerService service,
                Context context,
                Display display,
                Executor executor,
                SurfaceControlViewHostFactory surfaceControlViewHostFactory) {
            mContext = context;
            mDisplay = display;
            mSurfaceControlViewHostFactory = surfaceControlViewHostFactory;
            mRequestProcessor = new BinderRequestProcessor.Builder<>(service, executor)
                    .build();
        }

        @Override
        public void createVisualizationForClient(
                PublishedContextInsightWrapper insightWrapper,
                InsightSurfaceClientInfo clientInfo,
                RenderToken renderToken,
                IVisualizationResult result,
                IOpCallback callback) {
            mRequestProcessor.execute(new BinderRequestProcessor.ExecutionParams
                    .Builder<InsightSurfaceVisualizerService>(callback, serviceInstance -> {
                        final View view = serviceInstance.onCreateEmbeddedView(
                                mContext,
                                insightWrapper.getPublishedContextInsight(),
                                renderToken,
                                clientInfo);

                        final SurfaceInfo existingSurface =
                                mSurfacesByClient.get(clientInfo.getId());

                        if (view == null) {
                            Log.e(TAG, "onCreateEmbeddedView returned null for client: "
                                    + clientInfo);
                            disconnectClient(clientInfo, serviceInstance);
                            sendResult(/*visualizationCreated= */ false, result);
                            return;
                        }

                        if (existingSurface == null) {
                            final SurfaceControlViewHost newSurfaceHost =
                                    mSurfaceControlViewHostFactory.createSurfaceControlViewHost(
                                            mContext, mDisplay, null);

                            final RootView rootView =
                                    serviceInstance.mInjector.createRootView(
                                            mContext, clientInfo, newSurfaceHost);
                            // Set the SCVH to not focusable in order to avoid the keyboard being
                            // incorrectly dismissed in certain situations (e.g. when users
                            // long-press on the embedded view).
                            newSurfaceHost.setView(
                                    rootView,
                                    new SurfaceControlViewHost.LayoutParams(
                                            /* width= */ 0,
                                            /* height= */ 0,
                                            /* focusable= */ false));
                            rootView.setContentView(view);

                            final SurfaceInfo newSurface =
                                    new SurfaceInfo(
                                            rootView,
                                            newSurfaceHost,
                                            newSurfaceHost.getSurfacePackage());
                            mSurfacesByClient.put(clientInfo.getId(), newSurface);
                            scheduleOnSurfaceCreated(
                                    rootView, clientInfo, newSurface.surfacePackage);

                            // Tell the visualizer that a new client is now connected.
                            serviceInstance.onClientConnected(clientInfo);
                        } else {
                            existingSurface.rootView().setContentView(view);
                            clientInfo.onSurfaceUpdated(existingSurface.surfacePackage());
                        }

                        sendResult(/*visualizationCreated= */ true, result);
                    }).build());
        }

        private void scheduleOnSurfaceCreated(
                RootView rootView,
                InsightSurfaceClientInfo clientInfo,
                SurfaceControlViewHost.SurfacePackage surfacePackage) {
            rootView.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            clientInfo.onSurfaceCreated(
                                    surfacePackage,
                                    new IInsightSurfaceSession.Stub() {
                                        @Override
                                        public void onClientUpdated(
                                                InsightSurfaceClientInfo oldInfo,
                                                InsightSurfaceClientInfo newInfo,
                                                ResultReceiver receiver,
                                                IOpCallback opCallback) {
                                            handleClientUpdate(
                                                    oldInfo,
                                                    newInfo,
                                                    receiver,
                                                    opCallback);
                                        }
                                    });
                            rootView.getViewTreeObserver()
                                    .removeOnPreDrawListener(this);
                            return true;
                        }
                    });
        }

        private void handleClientUpdate(
                InsightSurfaceClientInfo oldClientInfo,
                InsightSurfaceClientInfo newClientInfo,
                ResultReceiver receiver,
                IOpCallback opCallback) {
            mRequestProcessor.execute(new BinderRequestProcessor
                    .ExecutionParams.Builder<InsightSurfaceVisualizerService>(
                            opCallback,
                            serviceInstance -> {
                                final SurfaceInfo surface =
                                        mSurfacesByClient.get(oldClientInfo.getId());
                                if (surface != null) {
                                    surface.rootView().updateClientInfo(newClientInfo);
                                }
                                serviceInstance.onClientUpdated(
                                        oldClientInfo, newClientInfo, receiver);
                            }).build());
        }

        @Override
        public void onClientDisconnected(InsightSurfaceClientInfo client, IOpCallback callback) {
            mRequestProcessor.execute(new BinderRequestProcessor
                    .ExecutionParams.Builder<InsightSurfaceVisualizerService>(
                            callback,
                            serviceInstance -> disconnectClient(client, serviceInstance)
                    ).build());
        }

        // This method must be called on the executor thread.
        private void disconnectClient(
                InsightSurfaceClientInfo client, InsightSurfaceVisualizerService service) {
            if (releaseSurfaceForClient(client)) {
                service.onClientDisconnected(client);
            }
        }

        private boolean releaseSurfaceForClient(InsightSurfaceClientInfo client) {
            final SurfaceInfo surface = mSurfacesByClient.remove(client.getId());
            if (surface == null) {
                return false;
            }

            client.onSurfaceReleased(surface.surfacePackage());
            surface.rootView().onDestroy();
            surface.host().release();
            return true;
        }

        private void sendResult(
                boolean visualizationCreated, IVisualizationResult result) {
            try {
                result.onResult(visualizationCreated);
            } catch (RemoteException e) {
                Log.e(TAG, "Error sending result", e);
            }
        }
    }

    private record SurfaceInfo(
            RootView rootView,
            SurfaceControlViewHost host,
            SurfaceControlViewHost.SurfacePackage surfacePackage) {}

    /**
     * @hide
     */
    @VisibleForTesting
    public static class RootView extends FrameLayout {
        private InsightSurfaceClientInfo mClientInfo;
        private final SurfaceControlViewHost mHost;

        private Runnable mOnRequestLayoutAction = null;
        private final Runnable mOnRequestLayoutRunnable = () -> {
            if (mOnRequestLayoutAction != null) {
                mOnRequestLayoutAction.run();
            }
        };

        public RootView(
                @NonNull Context context,
                @NonNull InsightSurfaceClientInfo clientInfo,
                @NonNull SurfaceControlViewHost host) {
            super(context);
            mClientInfo = clientInfo;
            mHost = host;
        }

        @Override
        public void requestLayout() {
            removeCallbacks(mOnRequestLayoutRunnable);
            if (mOnRequestLayoutAction != null) {
                post(mOnRequestLayoutRunnable);
            }

            super.requestLayout();
        }

        /**
         * Update the root view's {@link InsightSurfaceClientInfo} reference.
         *
         * @param clientInfo the new {@link InsightSurfaceClientInfo}
         */
        public void updateClientInfo(@NonNull InsightSurfaceClientInfo clientInfo) {
            mClientInfo = clientInfo;
            requestLayout();
        }

        /**
         * Set the root view's content view.
         *
         * @param view the {@link View} to be set as the content view
         */
        public void setContentView(View view) {
            mOnRequestLayoutAction = null;

            removeAllViews();
            addView(view);

            mOnRequestLayoutAction = this::remeasure;
            requestLayout();
        }

        /**
         * Called when the root view is being destroyed.
         */
        public void onDestroy() {
            mOnRequestLayoutAction = null;
            removeCallbacks(mOnRequestLayoutRunnable);
        }

        /**
         * Execute the on request layout action, if there is one. The is only intended to be called
         * by tests.
         */
        @VisibleForTesting
        public void executeOnRequestLayoutAction() {
            if (mOnRequestLayoutAction != null) {
                mOnRequestLayoutAction.run();
            }
        }

        private void remeasure() {
            final int prevMeasuredWidth = getMeasuredWidth();
            final int prevMeasuredHeight = getMeasuredHeight();

            try {
                measure(mClientInfo.getMeasureSpecWidth(), mClientInfo.getMeasureSpecHeight());
            } catch (Exception e) {
                // TODO(b/485873401): Create and use a better error code.
                mClientInfo.onVisualizationError(ERROR_FAILED_TO_CREATE_SESSION);
                throw e;
            }

            final int newMeasuredWidth = getMeasuredWidth();
            final int newMeasuredHeight = getMeasuredHeight();

            if (newMeasuredWidth != prevMeasuredWidth || newMeasuredHeight != prevMeasuredHeight) {
                mHost.relayout(newMeasuredWidth, newMeasuredHeight);
                mClientInfo.onSizeChanged(newMeasuredWidth, newMeasuredHeight);
            }
        }
    }
}
