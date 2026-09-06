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

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.RequiresPermission;
import android.annotation.StyleRes;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.content.Context;
import android.graphics.Color;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.view.SurfaceControlViewHost;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A client object that registers with the personal context engine in order to receive a
 * {@link SurfaceControlViewHost.SurfacePackage} that can be installed in a surface view. Apps that
 * need to receive insight surfaces from the personal context engine must instantiate this class and
 * call {@link #register}. If and when a surface is ready, the
 * {@link ClientCallback#onSurfaceCreated} method will be called with a
 * {@link SurfaceControlViewHost.SurfacePackage}. Call {@link #unregister} to unregister the client.
 * The {@link ClientCallback#onSurfaceReleased} method will be called if/when the surface has been
 * released by the personal context engine.
 * <p>
 * The client can also send extra information back to the personal context engine to assist in
 * refining the insight surfaces it receives. This is accomplished by adding {@link ContextHint}s to
 * the client via its builder. For example, if the client app wanted to receive a surface containing
 * a confirmation number, it could add a custom {@link ContextHint}, like this:
 * <pre>{@code
 * final BundleHint hint = new BundleHint.Builder().build();
 * hint.getDataBundle().putString("field-to-fill", "confirmation-number");
 * final InsightSurfaceClient client =
 *          new InsightSurfaceClient.Builder(context, callbacks).addHint(hint).build();
 * }</pre>
 * This is an example. In practice, a specific {@link ContextHint} would be defined for this
 * purpose.
 *
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public class InsightSurfaceClient implements AutoCloseable {
    private static final String TAG = "InsightSurfaceClient";

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** A receiver that can receive {@link ContextInsight}s from an embedded surface. */
    public interface InsightReceiver {
        /**
         * Receive a ContextInsight from the context engine. For example, an insight with
         * relevant data could be sent to the client when the user interacts with the
         * embedded view. The context engine can choose to send insights to the client in
         * other situations as well. Returns true if the insight was used and false otherwise.
         */
        boolean onReceive(@NonNull ContextInsight insight);
    }

    /**
     * Callbacks that are to be implemented by the owner of the client to be notified when
     * certain events have occurred.
     */
    public interface ClientCallback {
        /**
         * The size of the embedded surface has changed. Subclasses can override this method to be
         * informed of the size change.
         * @param width the new width of the surface in pixels
         * @param height the new height of the surface in pixels
         */
        default void onSizeChanged(int width, int height) {
        }

        /**
         * The given {@link InsightSurfaceSession} has been created. Subclasses can override this
         * method to be informed when a new session has been created. This method will only be
         * called once for a newly created session. Subsequent session updates will call
         * {@link #onSessionUpdated(InsightSurfaceSession)}. If a session is destroyed, then this
         * method will be called again if/when a new session is created.
         */
        default void onSessionCreated(@NonNull InsightSurfaceSession session) {
        }

        /**
         * The given {@link InsightSurfaceSession} has been updated. Subclasses can override this
         * method to be informed when a session has been updated. This method will only be called
         * when a session already exists. If no session yet exists, then
         * {@link #onSessionCreated(InsightSurfaceSession)} will be called instead. The session
         * passed to this method will be the same session that was passed to
         * {@link #onSessionCreated(InsightSurfaceSession)}.
         */
        default void onSessionUpdated(@NonNull InsightSurfaceSession session) {
        }

        /**
         * The given {@link InsightSurfaceSession} has been destroyed. Subclasses can override this
         * method to be informed when a session has been destroyed.
         */
        default void onSessionDestroyed(@NonNull InsightSurfaceSession session) {
        }

        /**
         * An error has occurred with an {@link InsightSurfaceSession}. Subclasses can override this
         * method to be informed of errors.
         * @param exception the {@link InsightSurfaceSessionException} representing the error
         */
        default void onError(@NonNull InsightSurfaceSessionException exception) {
        }
    }

    /** @hide */
    @IntDef(
            prefix = {"REGISTRATION_STATE_"},
            value = {
                    REGISTRATION_STATE_NOT_REGISTERED,
                    REGISTRATION_STATE_REGISTERING,
                    REGISTRATION_STATE_REGISTERED,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RegistrationState {}

    private static final int REGISTRATION_STATE_NOT_REGISTERED = 0;
    private static final int REGISTRATION_STATE_REGISTERING = 1;
    private static final int REGISTRATION_STATE_REGISTERED = 2;

    private InsightSurfaceClientInfo mClientInfo;
    private InsightSurfaceSession mSession;

    private final Context mContext;
    @NonNull
    private final List<InsightReceiver> mInsightReceivers;
    @Nullable
    private CallbackWrapper mCallbacks;
    @RegistrationState
    private int mRegistrationState = REGISTRATION_STATE_NOT_REGISTERED;
    private Set<ContextHint> mPendingHints;
    private final Object mRegistrationLock = new Object();

    private record CallbackWrapper(
            @NonNull Executor executor,
            @NonNull ClientCallback callbacks) {
    }

    private final IInsightSurfaceClient mClient =
            new IInsightSurfaceClient.Stub() {
                @Override
                public void onSurfaceCreated(
                        SurfaceControlViewHost.SurfacePackage surfacePackage,
                        IInsightSurfaceSession session) {
                    if (DEBUG) {
                        Log.d(TAG, "onSurfaceCreated [" + surfacePackage + "]");
                    }
                    executeWithCallbacks(clientCallback -> {
                        if (mSession != null) {
                            mSession.close();
                            clientCallback.onSessionDestroyed(mSession);
                        }
                        mSession = new InsightSurfaceSession(
                                mContext, InsightSurfaceClient.this, surfacePackage, session);
                        clientCallback.onSessionCreated(mSession);
                    });
                }

                @Override
                public void onSurfaceReleased(
                        SurfaceControlViewHost.SurfacePackage surfacePackage) {
                    if (DEBUG) {
                        Log.d(TAG, "onSurfaceReleased [" + surfacePackage + "]");
                    }
                    executeWithCallbacks(clientCallback -> {
                        if (mSession != null) {
                            Preconditions.checkState(
                                    mSession.getSurfacePackage().getSurfaceControl()
                                            .isSameSurface(surfacePackage.getSurfaceControl()));
                            clientCallback.onSessionDestroyed(mSession);
                            mSession.close();
                            mSession = null;
                        }
                    });
                }

                @Override
                public void onSurfaceUpdated(SurfaceControlViewHost.SurfacePackage surfacePackage) {
                    if (DEBUG) {
                        Log.d(TAG, "onSurfaceUpdated [" + surfacePackage + "]");
                    }
                    executeWithCallbacks(clientCallback -> {
                        if (mSession == null) {
                            // The session was released before the update arrived.
                            // TODO(b/497854105): Root-cause and fix the underlying issue.
                            Log.e(TAG, "mSession null in onSurfaceUpdated");
                            return;
                        }

                        // SurfacePackage's SurfaceControl can be null if the SurfaceControlViewHost
                        // has been detached from its window before the update has arrived.
                        if (mSession.getSurfacePackage().getSurfaceControl() == null) {
                            if (DEBUG) {
                                Log.d(TAG,
                                        "onSurfaceUpdated: skipping update, "
                                                + "surfaceControl is null");
                            }
                            return;
                        }

                        Preconditions.checkState(mSession.getSurfacePackage().getSurfaceControl()
                                        .isSameSurface(surfacePackage.getSurfaceControl()));
                        clientCallback.onSessionUpdated(mSession);
                    });
                }

                @Override
                public void onReceiveInsight(ContextInsightWrapper insightWrapper) {
                    final ContextInsight insight = insightWrapper.getContextInsight();
                    if (DEBUG) {
                        Log.d(TAG, "onInsightReceived [" + insight + "]");
                    }
                    executeWithCallbacks(clientCallback ->
                            mInsightReceivers.forEach((receiver) -> receiver.onReceive(insight)));
                }

                @Override
                public void onSizeChanged(int width, int height) {
                    if (DEBUG) {
                        Log.d(TAG, "onSizeChanged [width=" + width + ", height=" + height + "]");
                    }
                    executeWithCallbacks(clientCallback ->
                            clientCallback.onSizeChanged(width, height));
                }

                @Override
                public void onVisualizationError(
                        @InsightSurfaceSessionException.ClientError int errorCode) {
                    if (DEBUG) {
                        Log.d(TAG, "onVisualizationError: errorCode=" + errorCode);
                    }
                    executeWithCallbacks(clientCallback ->
                            clientCallback.onError(
                                    new InsightSurfaceSessionException(
                                            errorCode, "failed to create a visualization")));
                }

                public void onRegistered() {
                    if (DEBUG) {
                        Log.d(TAG, "onRegistered");
                    }

                    synchronized (mRegistrationLock) {
                        mRegistrationState = REGISTRATION_STATE_REGISTERED;

                        if (mPendingHints != null) {
                            if (DEBUG) {
                                Log.d(TAG, "Sending " + mPendingHints.size() + " pending hints.");
                            }
                            publishHints(mPendingHints);
                            mPendingHints = null;
                        }
                    }
                }
            };

    private InsightSurfaceClient(
            Context context,
            int widthMeasureSpec,
            int heightMeasureSpec,
            @NonNull Color backgroundColor,
            int nestedScrollAxes,
            boolean nestedScrollAxisLocked,
            boolean shouldBlur,
            @StyleRes int themeResourceId,
            @NonNull List<InsightReceiver> receivers) {
        mContext = context;
        mInsightReceivers = List.copyOf(receivers);

        mClientInfo = new InsightSurfaceClientInfo(
                UUID.randomUUID(),
                mContext.getDisplay().getDisplayId(),
                widthMeasureSpec,
                heightMeasureSpec,
                backgroundColor,
                nestedScrollAxes,
                nestedScrollAxisLocked,
                shouldBlur,
                themeResourceId,
                mContext.getPackageName(),
                mContext.getResources().getConfiguration(),
                mClient);
    }

    /**
     * Publish new hints to the context engine. This method can be called any time after the client
     * has been created to send new hints to the context engine. Hints published through this method
     * will cause any resulting {@link ContextInsight} to be delivered to this surface client.
     * <p>
     * If the client is not yet registered with the context engine, then the hints won't be
     * published to the context engine until the client is successfully registered. Note that
     * calling this method multiple times before the client is registered will result in only the
     * last published hints being sent after client registration has finished. Hints across
     * multiple calls will not be queued or combined.
     *
     * </p>
     *
     * @param hints a list of {@link ContextHint}s
     */
    public void publishHints(@NonNull Set<ContextHint> hints) {
        Objects.requireNonNull(hints);

        synchronized (mRegistrationLock) {
            if (mRegistrationState == REGISTRATION_STATE_REGISTERED) {
                final PersonalContextManager personalContextManager =
                        mContext.getSystemService(PersonalContextManager.class);
                personalContextManager.publishInsightSurfaceHints(hints, mClientInfo);
            } else {
                mPendingHints = new HashSet<>(hints);
            }
        }
    }

    /**
     * Return the insight receivers for this client.
     *
     * @return a list of {@link ContextInsight} receivers
     */
    @NonNull
    public List<InsightReceiver> getReceivers() {
        return mInsightReceivers;
    }

    /**
     * Return the width {@link View.MeasureSpec} for this client.
     */
    public int getMeasureSpecWidth() {
        return mClientInfo.getMeasureSpecWidth();
    }

    /**
     * Return the height {@link View.MeasureSpec} for this client.
     */
    public int getMeasureSpecHeight() {
        return mClientInfo.getMeasureSpecHeight();
    }

    /**
     * Return the background {@link Color} for this client.
     */
    @NonNull
    public Color getBackgroundColor() {
        return mClientInfo.getBackgroundColor();
    }

    /**
     * Return a bitmask indicating the nested scroll axes supported by the client. This ensures
     * that an embedded surface will only send these nested scroll events back to the client when
     * nested scroll axis is locked. Possible values are
     * {@link View#SCROLL_AXIS_HORIZONTAL},
     * {@link View#SCROLL_AXIS_VERTICAL}, or
     * {@link View#SCROLL_AXIS_NONE}.
     */
    public int getNestedScrollAxes() {
        return mClientInfo.getNestedScrollAxes();
    }

    /**
     * Return whether an embedded surface should report a specific axis when a nested scroll gesture
     * is detected, and whether that axis should be locked such that subsequent nested scroll events
     * are only reported for that axis. A value of {@code true} is typical for Android UIs where
     * scroll axes are locked during a gesture, while a value of {@code false} can be used to give
     * the illusion of a 2D canvas. Only applicable when nested scroll axes is set to
     * {@link View#SCROLL_AXIS_HORIZONTAL} or
     * {@link View#SCROLL_AXIS_VERTICAL}.
     */
    public boolean isNestedScrollAxisLocked() {
        return mClientInfo.getNestedScrollAxisLocked();
    }

    /**
     * Return whether the embedded surface should apply a blur. This should be {@code true} when the
     * client view is blurred so that the embedded surface can also apply a blur to match it.
     */
    public boolean shouldBlur() {
        return mClientInfo.shouldBlur();
    }

    /**
     * Get the name of a theme resource to be passed to the connected visualizer. A visualizer
     * can use this name to look up the theme, which can then be used when creating an embedded
     * surface for the client. See {@link InsightSurfaceClientInfo#getThemeResourceId()} for more
     * information.
     */
    @StyleRes
    public int getThemeResourceId() {
        return mClientInfo.getThemeResourceId();
    }

    /**
     * Register with the personal context engine. Once registered, the client can receive a
     * {@link SurfaceControlViewHost.SurfacePackage} via {@link ClientCallback}. Calling this
     * method more than once (without calling {@link #unregister()} is a nop.
     *
     * @param callbacksExecutor an optional {@link Executor} with which to execute callback methods
     * @param callbacks {@link ClientCallback} to be notified of connection events
     */
    public void register(
            @Nullable Executor callbacksExecutor,
            @NonNull ClientCallback callbacks) {
        if (DEBUG) {
            Log.d(TAG, "registering client...");
        }

        synchronized (mRegistrationLock) {
            if (mRegistrationState != REGISTRATION_STATE_NOT_REGISTERED) {
                // Already registered or in the process of registering.
                Log.w(TAG, "client is already registered or is registering");
                return;
            }

            mRegistrationState = REGISTRATION_STATE_REGISTERING;

            final PersonalContextManager personalContextManager =
                    mContext.getSystemService(PersonalContextManager.class);
            personalContextManager.registerInsightSurfaceClient(mClientInfo);

            mCallbacks = new CallbackWrapper(
                    callbacksExecutor != null ? callbacksExecutor : mContext.getMainExecutor(),
                    callbacks);
        }
    }

    /**
     * Unregister from the personal context engine. If the client has acquired a
     * {@link SurfaceControlViewHost.SurfacePackage}, then it will be released automatically when
     * this method is called (it will also be released if the connection to the visualizer is
     * disconnected for any reason).
     */
    public void unregister() {
        if (DEBUG) {
            Log.d(TAG, "unregistering client...");
        }

        synchronized (mRegistrationLock) {
            if (mRegistrationState == REGISTRATION_STATE_NOT_REGISTERED) {
                Log.w(TAG, "client not registered");
                return;
            }

            final PersonalContextManager personalContextManager =
                    mContext.getSystemService(PersonalContextManager.class);
            personalContextManager.unregisterInsightSurfaceClient(mClientInfo);

            if (mSession != null) {
                // Closing the session releases the SurfacePackage it wraps.
                mSession.close();
                mSession = null;
            }

            mCallbacks = null;
            mRegistrationState = REGISTRATION_STATE_NOT_REGISTERED;
            mPendingHints = null;
        }
    }

    @Override
    public void close() {
        synchronized (mRegistrationLock) {
            if (mRegistrationState != REGISTRATION_STATE_NOT_REGISTERED) {
                unregister();
            }
        }
    }

    /**
     * Return the {@link InsightSurfaceClientInfo} for this client.
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceClientInfo getClientInfo() {
        return mClientInfo;
    }

    /**
     * Update the {@link InsightSurfaceClientInfo} with the given
     * {@link InsightSurfaceClientUpdate}. Returns the old {@link InsightSurfaceClientInfo}.
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceClientInfo updateClientInfo(InsightSurfaceClientUpdate update) {
        final InsightSurfaceClientInfo oldClientInfo = mClientInfo;
        mClientInfo = mClientInfo.createInfoFromUpdate(update);
        return oldClientInfo;
    }

    private void executeWithCallbacks(Consumer<ClientCallback> action) {
        if (mCallbacks == null) {
            return;
        }
        mCallbacks.executor().execute(() -> {
            // The client could have been unregistered by the time this executes, so check that
            // mCallbacks isn't null here as well.
            if (mCallbacks != null) {
                action.accept(mCallbacks.callbacks());
            }
        });
    }

    /** Builder used to build a new {@link InsightSurfaceClient}. */
    public static final class Builder {
        private final Context mContext;
        private final List<InsightReceiver> mReceivers = new ArrayList<>();
        private int mWidthMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        private int mHeightMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        private Color mBackgroundColor = Color.valueOf(Color.TRANSPARENT);
        private int mNestedScrollAxes = View.SCROLL_AXIS_NONE;
        private boolean mNestedScrollAxisLocked = false;
        private boolean mShouldBlur = false;
        private int mThemeResourceId;

        /**
         * Construct a new builder.
         *
         * @param context a {@link Context} used to fetch system services
         */
        public Builder(@NonNull Context context) {
            mContext = context;
        }

        /**
         * Add an insight receiver to the client.
         *
         * @param receiver the {@link InsightReceiver} to be added
         * @return the {@link Builder}
         */
        @NonNull
        public Builder addReceiver(@NonNull InsightReceiver receiver) {
            mReceivers.add(receiver);
            return this;
        }

        /**
         * Set the width {@link View.MeasureSpec} of the client surface
         *
         * @param widthMeasureSpec the width {@link View.MeasureSpec} of the client surface
         * @param heightMeasureSpec the height {@link View.MeasureSpec} of the client surface
         * @throws IllegalArgumentException when the {@link View.MeasureSpec} is not one of
         * {@code View.MeasureSpec.UNSPECIFIED}, {@code View.MeasureSpec.EXACTLY}, or
         * {@code View.MeasureSpec.At_MOST}
         */
        @SuppressLint("MissingGetterMatchingBuilder")
        @NonNull
        public Builder setMeasureSpecs(int widthMeasureSpec, int heightMeasureSpec) {
            Preconditions.checkArgument(isValidMeasureSpec(widthMeasureSpec));
            Preconditions.checkArgument(isValidMeasureSpec(heightMeasureSpec));
            mWidthMeasureSpec = widthMeasureSpec;
            mHeightMeasureSpec = heightMeasureSpec;
            return this;
        }

        /**
         * Set the surface background color. Transparent is the default background color when no
         * background color is set.
         *
         * @param backgroundColor the background color of the client
         */
        @NonNull
        public Builder setBackgroundColor(@NonNull Color backgroundColor) {
            mBackgroundColor = backgroundColor;
            return this;
        }

        /**
         * Sets a bitmask indicating the nested scroll axes supported by the client. This ensures
         * that an embedded surface will only send these nested scroll events for the specified axes
         * back to the client. Possible values are {@link View#SCROLL_AXIS_HORIZONTAL},
         * {@link View#SCROLL_AXIS_VERTICAL}, or {@link View#SCROLL_AXIS_NONE}.
         *
         * @param nestedScrollAxes the axes that the client supports
         * @throws IllegalArgumentException when nestedScrollAxes is not a bitmask of the possible
         * values
         */
        @NonNull
        public Builder setNestedScrollAxes(int nestedScrollAxes) {
            Preconditions.checkArgument(isValidNestedScrollAxes(nestedScrollAxes));
            mNestedScrollAxes = nestedScrollAxes;
            return this;
        }

        /**
         * Set whether the embedded surface should apply a blur. Clients would set this to
         * {@code true} when the client view is blurred so that the embedded surface can also apply
         * a blur to match it.
         * @param shouldBlur whether to apply a blur
         */
        @NonNull
        public Builder setShouldBlur(boolean shouldBlur) {
            mShouldBlur = shouldBlur;
            return this;
        }

        /**
         * Set the id of a custom style to be passed to a connected visualizer. A visualizer
         * will use this id to look up the theme resource in the client's resources. Attributes
         * defined in {@link android.R.styleable#PersonalContextTheme} will be read when
         * creating an embedded surface for the client. The custom theme should be declared in
         * the client app's xml resources. See
         * {@link InsightSurfaceClientInfo#getThemeResourceId()} for more information.
         */
        @NonNull
        public Builder setThemeResourceId(@StyleRes int themeResourceId) {
            mThemeResourceId = themeResourceId;
            return this;
        }

        /**
         * Sets whether nested scrolling is locked (based on the bitmask past to
         * {@link #setNestedScrollAxes(int)}). A value of {@code true} is typical for Android UIs
         * where scroll axes are locked during a gesture, while a value of {@code false} can be
         * used to give the illusion of a 2D canvas. Only applicable when nested scroll axes is
         * set to {@link View#SCROLL_AXIS_HORIZONTAL} or
         * {@link View#SCROLL_AXIS_VERTICAL}.
         *
         * @param nestedScrollAxisLocked {@code true} if the embedded surface should only send
         *                               nested scroll events for the axes the client supports
         */
        @NonNull
        public Builder setNestedScrollAxisLocked(boolean nestedScrollAxisLocked) {
            mNestedScrollAxisLocked = nestedScrollAxisLocked;
            return this;
        }

        /**
         * Build and return an {@link InsightSurfaceClient}.
         *
         * @return the {@link InsightSurfaceClient}
         */
        @NonNull
        public InsightSurfaceClient build() {
            return new InsightSurfaceClient(
                    mContext,
                    mWidthMeasureSpec,
                    mHeightMeasureSpec,
                    mBackgroundColor,
                    mNestedScrollAxes,
                    mNestedScrollAxisLocked,
                    mShouldBlur,
                    mThemeResourceId,
                    mReceivers);
        }
    }

    private static boolean isValidNestedScrollAxes(int nestedScrollAxes) {
        return switch (nestedScrollAxes) {
            case View.SCROLL_AXIS_NONE, View.SCROLL_AXIS_VERTICAL,
                 View.SCROLL_AXIS_HORIZONTAL + View.SCROLL_AXIS_VERTICAL -> true;
            default -> false;
        };
    }

    private static boolean isValidMeasureSpec(int measureSpec) {
        return switch (View.MeasureSpec.getMode(measureSpec)) {
            case View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.EXACTLY, View.MeasureSpec.AT_MOST
                    -> true;
            default -> false;
        };
    }
}
