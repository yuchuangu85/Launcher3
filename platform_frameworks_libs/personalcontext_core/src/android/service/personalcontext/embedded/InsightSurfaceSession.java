/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.service.personalcontext.embedded.ClientUpdateException.UPDATE_ERROR_DECLINED_BY_VISUALIZER;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.content.Context;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.service.personalcontext.Flags;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.PersonalContextManager;
import android.util.Log;
import android.view.SurfaceControlViewHost;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;

/**
 * This class represents an open session between an {@link InsightSurfaceClient} and an
 * {@link InsightSurfaceVisualizerService}. InsightSurfaceSession is essentially a wrapper around a
 * {@link SurfaceControlViewHost.SurfacePackage}, while also providing APIs for sending client
 * updates to the visualizer.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public class InsightSurfaceSession implements AutoCloseable {
    private static final String TAG = "InsightSurfaceSession";

    private final Context mContext;
    private final WeakReference<InsightSurfaceClient> mClient;
    private SurfaceControlViewHost.SurfacePackage mSurfacePackage;
    private final IInsightSurfaceSession mSession;

    /**
     * Create a new {@link InsightSurfaceSession}.
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceSession(
            Context context,
            InsightSurfaceClient client,
            SurfaceControlViewHost.SurfacePackage surfacePackage,
            IInsightSurfaceSession session) {
        mContext = context;
        mClient = new WeakReference<>(client);
        mSurfacePackage = surfacePackage;
        mSession = session;
    }

    /**
     * Return the {@link InsightSurfaceClient} for this session. Can be null if the client no longer
     * exists.
     */
    @Nullable
    public InsightSurfaceClient getClient() {
        return mClient.get();
    }

    /**
     * Return the {@link SurfaceControlViewHost.SurfacePackage} for this session. This will return
     * {@code null} if the session has been closed.
     */
    @Nullable
    public SurfaceControlViewHost.SurfacePackage getSurfacePackage() {
        return mSurfacePackage;
    }

    /**
     * Update the client for this session and inform the connected visualizer of the update. The
     * result of the update (success or failure) will be reported back to the caller via the
     * {@link OutcomeReceiver} callback. Note that the client for this session will be updated even
     * if there is a failure to report the update to the visualizer.
     *
     * @param update an {@link InsightSurfaceClientUpdate} with updated client properties
     * @param callback an optional {@link OutcomeReceiver} callback that will be used to inform the
     *                 caller of the result of the update
     */
    public void update(
            @NonNull InsightSurfaceClientUpdate update,
            @Nullable OutcomeReceiver<InsightSurfaceClientUpdate, ClientUpdateException> callback) {
        final InsightSurfaceClient client = mClient.get();
        if (client == null) {
            return;
        }

        final InsightSurfaceClientInfo oldClientInfo = client.updateClientInfo(update);
        final InsightSurfaceClientInfo newClientInfo = client.getClientInfo();

        final PersonalContextManager personalContextManager =
                mContext.getSystemService(PersonalContextManager.class);

        // Tell the embedded renderer that the client has been updated.
        personalContextManager.updateEmbeddedClientInfo(oldClientInfo, newClientInfo);

        // Tell the session (visualizer) that the client has been updated.
        try {
            final ResultReceiver receiver =
                    callback != null
                        ? new ResultReceiver(null) {
                            @Override
                            public void onReceiveResult(int resultCode, Bundle result) {
                                super.onReceiveResult(resultCode, result);
                                if (resultCode == IInsightSurfaceSession.UPDATE_OK) {
                                    callback.onResult(update);
                                } else {
                                    callback.onError(
                                            new ClientUpdateException(
                                                    UPDATE_ERROR_DECLINED_BY_VISUALIZER,
                                                    update));
                                }
                            }
                        } : null;
            // TODO(b/485403335): Track connection lifetime.
            final IOpCallback opCallback = new IOpCallback.Stub() {
                @Override
                public void signalCompletion() throws RemoteException {

                }
            };
            mSession.onClientUpdated(oldClientInfo, newClientInfo, receiver, opCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "Error updating session client", e);
        }
    }

    /**
     * Close the session.
     */
    @Override
    public void close() {
        if (mSurfacePackage != null) {
            mSurfacePackage.release();
            mSurfacePackage = null;
        }
        mClient.clear();
    }
}
