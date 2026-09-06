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

import android.annotation.FlaggedApi;
import android.annotation.StyleRes;
import android.annotation.SystemApi;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.service.personalcontext.Flags;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.view.SurfaceControlViewHost;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;

import java.util.UUID;

/**
 * Contains the properties of an {@link InsightSurfaceClient} that will be passed through the
 * personal context engine to the visualizer that will be providing a surface to embed in the
 * client. This class will be instantiated by {@link InsightSurfaceClient}.
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class InsightSurfaceClientInfo implements Parcelable {
    private static final String TAG = "InsightSrfcClientInfo";

    private final UUID mId;
    private final int mDisplayId;
    private final int mMeasureSpecWidth;
    private final int mMeasureSpecHeight;
    private final Color mBackgroundColor;
    private final int mNestedScrollAxes;
    private final boolean mNestedScrollAxisLocked;
    private final boolean mShouldBlur;
    private final int mThemeResourceId;
    private final String mPackageName;
    private final Configuration mConfiguration;
    private final IInsightSurfaceClient mClient;

    /**
     * Create a new insight surface client info object.
     *
     * @param id the client's unique id
     * @param displayId the client app's {@link android.view.Display#getDisplayId}
     * @param measureSpecWidth the width MeasureSpec of the client surface
     * @param measureSpecHeight the height MeasureSpec of the client surface
     * @param backgroundColor the background color of the client surface
     * @param nestedScrollAxes the nested scroll axes supported by the client surface
     * @param nestedScrollAxisLocked whether scrolling is locked to the nested scroll axes
     * @param shouldBlur {@code true} if the client surface should be blurred
     * @param themeResourceId the name of a theme resource specifying client styling
     * @param packageName the package name of the client application
     * @param configuration resource configuration from the client's local context
     * @param client interface used to pass sessions and insights back to the client
     *
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceClientInfo(
            UUID id,
            int displayId,
            int measureSpecWidth,
            int measureSpecHeight,
            @NonNull Color backgroundColor,
            int nestedScrollAxes,
            boolean nestedScrollAxisLocked,
            boolean shouldBlur,
            @StyleRes int themeResourceId,
            @NonNull String packageName,
            @NonNull Configuration configuration,
            @NonNull IInsightSurfaceClient client) {
        mId = id;
        mDisplayId = displayId;
        mMeasureSpecWidth = measureSpecWidth;
        mMeasureSpecHeight = measureSpecHeight;
        mBackgroundColor = backgroundColor;
        mNestedScrollAxes = nestedScrollAxes;
        mNestedScrollAxisLocked = nestedScrollAxisLocked;
        mShouldBlur = shouldBlur;
        mPackageName = packageName;
        mThemeResourceId = themeResourceId;
        mConfiguration = configuration;
        mClient = client;
    }

    private InsightSurfaceClientInfo(Parcel in) {
        mId = UUID.fromString(in.readString8());
        mDisplayId = in.readInt();
        mMeasureSpecWidth = in.readInt();
        mMeasureSpecHeight = in.readInt();
        mBackgroundColor = Color.valueOf(in.readInt());
        mNestedScrollAxes = in.readInt();
        mNestedScrollAxisLocked = in.readBoolean();
        mShouldBlur = in.readBoolean();
        mThemeResourceId = in.readInt();
        mPackageName = in.readString8();
        mConfiguration =
                in.readParcelable(Configuration.class.getClassLoader(), Configuration.class);
        mClient = IInsightSurfaceClient.Stub.asInterface(in.readStrongBinder());
    }

    /**
     * Get the client's unique id.
     *
     * @return the client's {@link UUID}
     */
    @NonNull
    public UUID getId() {
        return mId;
    }

    /**
     * Get the client's display id.
     *
     * @return the client's {@link android.view.Display} id
     */
    @IntRange(from = 0)
    public int getDisplayId() {
        return mDisplayId;
    }

    /**
     * Get the client's width MeasureSpec.
     *
     * @return the client's width {@link android.view.View.MeasureSpec}
     */
    public int getMeasureSpecWidth() {
        return mMeasureSpecWidth;
    }

    /**
     * Get the client's height MeasureSpec.
     *
     * @return the client's height {@link android.view.View.MeasureSpec}
     */
    public int getMeasureSpecHeight() {
        return mMeasureSpecHeight;
    }

    /**
     * Get the background color of the client over which the embedded surface will be rendered.
     *
     * @return the client's background color
     */
    @NonNull
    public Color getBackgroundColor() {
        return mBackgroundColor;
    }

    /**
     * Get the bitfield indicating the nested scroll axes supported by the client. This ensures that
     * an embedded surface will only send these nested scroll events back to the client. Possible
     * values are {@link android.view.View#SCROLL_AXIS_HORIZONTAL},
     * {@link android.view.View#SCROLL_AXIS_VERTICAL},
     * or {@link android.view.View#SCROLL_AXIS_NONE}.
     */
    public int getNestedScrollAxes() {
        return mNestedScrollAxes;
    }

    /**
     * Return whether an embedded surface should report a specific axis when a nested scroll gesture
     * is detected, and whether that axis should be locked such that subsequent nested scroll events
     * are only reported for that axis. A value of {@code true} is typical for Android UIs where
     * scroll axes are locked during a gesture, while a value of {@code false} can be used to give
     * the illusion of a 2D canvas. Only applicable when nested scroll axes is set to
     * {@link android.view.View#SCROLL_AXIS_HORIZONTAL} or
     * {@link android.view.View#SCROLL_AXIS_VERTICAL}.
     */
    public boolean getNestedScrollAxisLocked() {
        return mNestedScrollAxisLocked;
    }

    /**
     * Return whether the embedded surface should apply a blur. This should be {@code true} when the
     * client view is blurred so that the embedded surface can also apply a blur to match it.
     */
    public boolean shouldBlur() {
        return mShouldBlur;
    }

    /**
     * Get the id of a theme resource to be passed to the connected visualizer. A visualizer
     * can use this id to look up the theme, which can then be used when creating an embedded
     * surface for the client. If this method returns {@link android.content.res.Resources#ID_NULL},
     * or the resource can't be found, then the caller should fall back to default attribute values.
     * Note that the caller needs to be able to query the package related to this theme resource in
     * order to retrieve any values.
     * <p/>
     * Visualizers can obtain the style attributes with this id as follows:
     * <pre>
     * int themeResourceId = clientInfo.getThemeResourceId();
     * if (themeResourceId != Resources.ID_NULL) {
     *     TypedArray styleAttrs;
     *     try {
     *         String packageName = clientInfo.getPackageName();
     *         Context context = getPackageManager().getResourcesForApplication(packageName);
     *         Resources res = context.getResources();
     *         styleAttrs = res.obtainStyledAttributes(themeResourceId, attrs);
     *     } catch (PackageManager.NameNotFoundException e) {
     *         // Custom client theme not found, apply default attributes...
     *     } finally {
     *         // Recycle the TypedArray when finished with it.
     *         styleAttrs.recycle();
     *     }
     *     // Apply custom attributes from client's resources...
     * } else {
     *     // Apply default attributes...
     * }
     * </pre>
     */
    @StyleRes
    public int getThemeResourceId() {
        return mThemeResourceId;
    }

    /**
     * Return the package name of the client application.
     */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Get the client's {@link Configuration}.
     *
     * @return the client's {@link Configuration}
     */
    @NonNull
    public Configuration getConfiguration() {
        return mConfiguration;
    }

    /**
     * Get the {@link IInsightSurfaceClient} interface for the client.
     *
     * @return the client's {@link IInsightSurfaceClient}
     *
     * @hide
     */
    @NonNull
    public IInsightSurfaceClient getClient() {
        return mClient;
    }

    /**
     * The given {@link SurfaceControlViewHost.SurfacePackage} has been created.
     *
     * @param surfacePackage the created {@link SurfaceControlViewHost.SurfacePackage}
     * @param session the {@link IInsightSurfaceSession} for the surface
     *
     * @hide
     */
    public void onSurfaceCreated(
            @NonNull SurfaceControlViewHost.SurfacePackage surfacePackage,
            @NonNull IInsightSurfaceSession session) {
        try {
            mClient.onSurfaceCreated(surfacePackage, session);
        } catch (RemoteException e) {
            Log.e(TAG, "Error creating SurfacePackage", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * The given {@link SurfaceControlViewHost.SurfacePackage} has been updated.
     *
     * @param surfacePackage the updated {@link SurfaceControlViewHost.SurfacePackage}
     *
     * @hide
     */
    public void onSurfaceUpdated(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
        try {
            mClient.onSurfaceUpdated(surfacePackage);
        } catch (RemoteException e) {
            Log.e(TAG, "Error updating SurfacePackage", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * The given {@link SurfaceControlViewHost.SurfacePackage} has been released.
     *
     * @param surfacePackage the released {@link SurfaceControlViewHost.SurfacePackage}
     *
     * @hide
     */
    public void onSurfaceReleased(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
        try {
            mClient.onSurfaceReleased(surfacePackage);
        } catch (RemoteException e) {
            Log.e(TAG, "Error releasing SurfacePackage", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Called when there has been a visualization error, such as no visualization was produced.
     *
     * @hide
     */
    public void onVisualizationError(@InsightSurfaceSessionException.ClientError int errorCode) {
        try {
            mClient.onVisualizationError(errorCode);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling onVisualizationError", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * The client has been registered with the personal context engine.
     *
     * @hide
     */
    public void onRegistered() {
        try {
            mClient.onRegistered();
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling onRegistered", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sends the given {@link ContextInsight} to the client. This is used to egress data from the
     * embedded surface to the client (e.g. the user has tapped a button in the embedded surface,
     * resulting in data being sent to the embedding client based on the state of the embedded
     * surface).
     *
     * @param insight the {@link ContextInsight} to send to the client
     */
    public void onReceiveInsight(@NonNull ContextInsight insight) {
        try {
            mClient.onReceiveInsight(new ContextInsightWrapper(insight.copyWithoutHints()));
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending insight to client", e);
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Called by a visualizer in order to inform the client that the embedded surface size has
     * changed.
     *
     * @param width the new width of the surface in pixels
     * @param height the new height of the surface in pixels
     */
    public void onSizeChanged(int width, int height) {
        try {
            mClient.onSizeChanged(width, height);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending insight to client", e);
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mId.toString());
        dest.writeInt(mDisplayId);
        dest.writeInt(mMeasureSpecWidth);
        dest.writeInt(mMeasureSpecHeight);
        dest.writeInt(mBackgroundColor.toArgb());
        dest.writeInt(mNestedScrollAxes);
        dest.writeBoolean(mNestedScrollAxisLocked);
        dest.writeBoolean(mShouldBlur);
        dest.writeInt(mThemeResourceId);
        dest.writeString8(mPackageName);
        dest.writeParcelable(mConfiguration, flags);
        dest.writeStrongInterface(mClient);
    }

    /**
     * Returns a new {@link InsightSurfaceClientInfo} from this info and the updated values in the
     * given {@link InsightSurfaceClientUpdate}.
     *
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceClientInfo createInfoFromUpdate(InsightSurfaceClientUpdate update) {
        return new InsightSurfaceClientInfo(
                mId,
                mDisplayId,
                update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_WIDTH)
                        ? update.getMeasureSpecWidth() : mMeasureSpecWidth,
                update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_HEIGHT)
                        ? update.getMeasureSpecHeight() : mMeasureSpecHeight,
                update.hasUpdate(InsightSurfaceClientUpdate.KEY_BACKGROUND_COLOR)
                        ? update.getBackgroundColor() : mBackgroundColor,
                update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXES)
                        ? update.getNestedScrollAxes() : mNestedScrollAxes,
                update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXIS_LOCKED)
                        ? update.isNestedScrollAxisLocked() : mNestedScrollAxisLocked,
                update.hasUpdate(InsightSurfaceClientUpdate.KEY_SHOULD_BLUR)
                        ? update.shouldBlur() : mShouldBlur,
                update.hasUpdate(InsightSurfaceClientUpdate.KEY_THEME_RESOURCE_NAME)
                        ? update.getThemeResourceId() : mThemeResourceId,
                mPackageName,
                update.hasUpdate(InsightSurfaceClientUpdate.KEY_CONFIGURATION)
                        ? update.getConfiguration() : mConfiguration,
                mClient);
    }

    @NonNull
    public static final Creator<InsightSurfaceClientInfo> CREATOR =
            new Creator<>() {
                @Override
                public InsightSurfaceClientInfo createFromParcel(@NonNull Parcel source) {
                    return new InsightSurfaceClientInfo(source);
                }

                @Override
                public InsightSurfaceClientInfo[] newArray(int size) {
                    return new InsightSurfaceClientInfo[size];
                }
            };
}
