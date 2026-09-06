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

package android.service.personalcontext.util;

import android.annotation.Nullable;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.service.personalcontext.IOpCallback;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * This class helps properly manage binder requests across PersonalContext in a consistent fashion.
 * @param <T> The type of entity that should be made available to request, most often the service.
 * @hide
 */
public class BinderRequestProcessor<T> {
    private static final String TAG = "BinderRequestProcessor";

    public interface Initializer<T> {
        /**
         * Invoked when the service should be initialized
         * @param service the service that is being initialized
         * @param identifier the initialization identifier
         */
        void onInitialize(T service, UUID identifier) throws Exception;
    }

    private final Initializer<T> mInitializer;

    private final WeakReference<T> mServiceReference;

    private boolean mInitialized;

    private final Executor mExecutor;

    private BinderRequestProcessor(T service, Executor executor, Initializer<T> initializer) {
        mExecutor = executor;
        mInitializer = initializer;
        mServiceReference = new WeakReference<>(service);
    }

    private T getServiceOrThrow()
            throws RemoteException {
        final T service = mServiceReference.get();
        if (service == null) {
            Log.e(TAG, "Service is no longer available");

            throw new RemoteException("Service is no longer available");
        } else {
            return service;
        }
    }

    /**
     * Interface for running ops within {@link #execute(UUID, OperationHandler, IOpCallback)}
     * @param <T> type of entity to provide to the handler.
     */
    public interface OperationHandler<T> {
        /**
         * Called to run ops.
         * @throws Exception
         */
        void handle(T serviceInstance) throws Exception;
    }

    /**
     * Invoked to execute the specified handler
     */
    public final void execute(ExecutionParams<T> params)
            throws IllegalStateException {
        mExecutor.execute(() -> {
            try {
                final T service = getServiceOrThrow();
                if (!mInitialized) {
                    if (mInitializer != null) {
                        mInitializer.onInitialize(service, params.getComponentId());
                    }
                    mInitialized = true;
                }
                params.getHandler().handle(service);
            } catch (Exception e) {
                // Runnables cannot throw all exceptions, repackaging to throw
                throw new RuntimeException(e);
            } finally {
                try {
                    params.getCallback().signalCompletion();
                } catch (RemoteException e) {
                    Log.e(TAG, "could not signal completion", e);
                }
            }
        });
    }

    /**
     * Builder for {@link BinderRequestProcessor}.
     * @param <T> The target type accessed through the processor
     */
    public static class Builder<T> {

        private Initializer<T> mInitializer;
        private final T mService;
        private final Executor mExecutor;

        public Builder(T service, Executor executor) {
            mService = service;
            mExecutor = executor;
        }

        /**
         * Sets the initializer that is ran on the first operation ran on this processor.
         */
        public Builder<T> setInitializer(Initializer<T> initializer) {
            mInitializer = initializer;
            return this;
        }

        /**
         * Builds the processor.
         */
        public BinderRequestProcessor<T> build() {
            return new BinderRequestProcessor<>(mService, mExecutor, mInitializer);
        }
    }

    /**
     * Parameters for executing an operation on the {@link BinderRequestProcessor}.
     * @param <T> The target type accessed through the processor
     */
    public static class ExecutionParams<T> {
        private final IOpCallback mCallback;
        private final OperationHandler<T> mInputHandler;
        private final UUID mComponentId;

        private ExecutionParams(IOpCallback callback, OperationHandler<T> inputHandler,
                UUID componentId) {
            mCallback = callback;
            mInputHandler = inputHandler;
            mComponentId = componentId;
        }

        /**
         * Returns the {@link IOpCallback} that should always run at the end of execution.
         */
        public IOpCallback getCallback() {
            return mCallback;
        }

        /**
         * Returns the handler code, which houses the core logic being executed.
         */
        public OperationHandler<T> getHandler() {
            return mInputHandler;
        }

        /**
         * Returns the component id (if set) associated with this operation.
         */
        @Nullable
        public UUID getComponentId() {
            return mComponentId;
        }

        /**
         * Builder for {@link ExecutionParams}.
         * @param <T> The target type accessed through the processor
         */
        public static class Builder<T> {
            private final IOpCallback mCallback;
            private final OperationHandler<T> mInputHandler;
            private UUID mComponentId;

            /** */
            public Builder(IOpCallback callback, OperationHandler<T> inputHandler) {
                mCallback = callback;
                mInputHandler = inputHandler;
            }

            /**
             * Set the component id.
             */
            public Builder<T> setComponentId(UUID componentId) {
                mComponentId = componentId;
                return this;
            }

            /**
             * Sets the component id.
             */
            public Builder<T> setComponentId(ParcelUuid componentId) {
                return setComponentId(componentId.getUuid());
            }

            /**
             * Builds the {@link ExecutionParams}.
             */
            public ExecutionParams<T> build() {
                return new ExecutionParams<>(mCallback, mInputHandler, mComponentId);
            }
        }
    }

}
