/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.hint;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.app.assist.AssistStructure;
import android.content.ComponentName;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.service.autofill.FillEventHistory;
import android.service.autofill.augmented.AugmentedAutofillService;
import android.service.autofill.augmented.FillRequest;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.autofill.AugmentedAutofillProxy;
import android.service.personalcontext.hint.autofill.AutofillInlineRequestHintConsumer;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.autofill.IAugmentedAutofillManagerClient;
import android.view.inputmethod.InlineSuggestionsRequest;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.util.Objects;

/**
 * A hint representing an incoming request from the autofill framework for inline suggestions for
 * use by augmented autofill.
 *
 * <p>When regular autofill falls back to augmented autofill, the personal context system and the
 * registered {@link AugmentedAutofillService} both receive requests in parallel. This hint provides
 * the same information as the request to {@link AugmentedAutofillService#handleOnFillRequest} so
 * that personal context can also generate suggestions.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public class AutofillInlineRequestHint extends ContextHint {
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_TASK_ID = "task_id";
    private static final String KEY_REQUEST_TIMESTAMP = "request_timestamp";
    private static final String KEY_ACTIVITY_COMPONENT = "activity_component";
    private static final String KEY_FOCUSED_ID = "focused_id";
    private static final String KEY_AUTOFILL_VALUE = "autofill_value";
    private static final String KEY_INLINE_SUGGESTIONS_REQUEST = "inline_suggestions_request";
    private static final String KEY_AUGMENTED_AUTOFILL_MANAGER_CLIENT =
            "augmented_autofill_manager_client";
    private static final String KEY_FILL_EVENT_HISTORY = "fill_event_history";

    private final int mSessionId;
    private final int mTaskId;
    private final Instant mRequestTimestamp;
    private final ComponentName mActivityComponent;
    private final AutofillId mFocusedId;
    private final AutofillValue mAutofillValue;
    private final InlineSuggestionsRequest mInlineSuggestionsRequest;
    private final AugmentedAutofillProxy mAugmentedAutofillProxy;

    @Nullable private final FillEventHistory mFillEventHistory;

    /** Internal constructor only for use by {@link Builder}. */
    private AutofillInlineRequestHint(
            @NonNull ConstructorParams baseParams,
            int sessionId,
            int taskId,
            @NonNull Instant requestTimestamp,
            @NonNull ComponentName activityComponent,
            @NonNull AutofillId focusedId,
            @NonNull AutofillValue autofillValue,
            @NonNull InlineSuggestionsRequest inlineSuggestionsRequest,
            @NonNull AugmentedAutofillProxy augmentedAutofillProxy,
            @Nullable FillEventHistory fillEventHistory) {
        super(baseParams);
        mSessionId = sessionId;
        mTaskId = taskId;
        mRequestTimestamp = requireNonNull(requestTimestamp);
        mActivityComponent = requireNonNull(activityComponent);
        mFocusedId = requireNonNull(focusedId);
        mAutofillValue = requireNonNull(autofillValue);
        mInlineSuggestionsRequest = requireNonNull(inlineSuggestionsRequest);
        mAugmentedAutofillProxy = requireNonNull(augmentedAutofillProxy);
        mFillEventHistory = fillEventHistory;
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    AutofillInlineRequestHint(@NonNull ConstructorParams baseParams, Bundle bundle) {
        super(baseParams);
        mSessionId = bundle.getInt(KEY_SESSION_ID);
        mTaskId = bundle.getInt(KEY_TASK_ID);
        mRequestTimestamp =
                requireNonNull(bundle.getSerializable(KEY_REQUEST_TIMESTAMP, Instant.class));
        mActivityComponent =
                requireNonNull(bundle.getParcelable(KEY_ACTIVITY_COMPONENT, ComponentName.class));
        mFocusedId = requireNonNull(bundle.getParcelable(KEY_FOCUSED_ID, AutofillId.class));
        mAutofillValue =
                requireNonNull(bundle.getParcelable(KEY_AUTOFILL_VALUE, AutofillValue.class));
        mInlineSuggestionsRequest =
                requireNonNull(
                        bundle.getParcelable(
                                KEY_INLINE_SUGGESTIONS_REQUEST, InlineSuggestionsRequest.class));
        mAugmentedAutofillProxy =
                new AugmentedAutofillProxyImpl(
                        requireNonNull(bundle.getBinder(KEY_AUGMENTED_AUTOFILL_MANAGER_CLIENT)));
        mFillEventHistory = bundle.getParcelable(KEY_FILL_EVENT_HISTORY, FillEventHistory.class);
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_AUTOFILL_INLINE_REQUEST;
    }

    /**
     * Returns the autofill session ID this request is associated with. This is an internal ID used
     * by the renderer to identify what autofill session to associate a produced insight with.
     */
    public int getSessionId() {
        return mSessionId;
    }

    /**
     * Returns the ID of the task of the activity associated with this request.
     *
     * @see android.app.TaskInfo#taskId
     * @see FillRequest#getTaskId()
     */
    public int getTaskId() {
        return mTaskId;
    }

    /** Returns the timestamp of the request. */
    @NonNull
    public Instant getRequestTimestamp() {
        return mRequestTimestamp;
    }

    /** Returns the component name of the activity involved in autofill. */
    @NonNull
    public ComponentName getActivityComponent() {
        return mActivityComponent;
    }

    /** Returns the ID for the node focused by autofill. */
    @NonNull
    public AutofillId getFocusedId() {
        return mFocusedId;
    }

    /** Returns the {@link AutofillValue} associated with the view to be filled. */
    @NonNull
    public AutofillValue getAutofillValue() {
        return mAutofillValue;
    }

    /** Returns the {@link InlineSuggestionsRequest} associated with this hint. */
    @NonNull
    public InlineSuggestionsRequest getInlineSuggestionsRequest() {
        return mInlineSuggestionsRequest;
    }

    /**
     * Returns a {@link AugmentedAutofillProxyImpl} used to request additional autofill information
     * from the augmented autofill service.
     *
     * @hide
     */
    @NonNull
    public AugmentedAutofillProxy getAugmentedAutofillProxy() {
        return mAugmentedAutofillProxy;
    }

    /**
     * Returns the {@link FillEventHistory} for the most recent fill request that personal context
     * was used to fulfill, if any.
     */
    @Nullable
    public FillEventHistory getFillEventHistory() {
        return mFillEventHistory;
    }

    /** @hide */
    @Override
    public void writeToSignatureParcel(@NonNull Parcel dest) {
        dest.writeInt(mSessionId);
        dest.writeInt(mTaskId);
        dest.writeLong(mRequestTimestamp.toEpochMilli());
        dest.writeString(mActivityComponent.flattenToString());
        dest.writeParcelable(mFocusedId, 0);
        dest.writeParcelable(mAutofillValue, 0);
        // Inline suggestion request has binders inside so we can't use it to sign, just write out
        // some relevant parts.
        dest.writeString(mInlineSuggestionsRequest.getHostPackageName());
        dest.writeInt(mInlineSuggestionsRequest.getHostDisplayId());
        dest.writeParcelableList(mInlineSuggestionsRequest.getInlinePresentationSpecs(), 0);
        // IAugmentedAutofillManagerClient is omitted as binders cannot be marshalled.
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AutofillInlineRequestHint that)) return false;
        if (!super.equals(o)) return false;
        return mSessionId == that.mSessionId
                && mTaskId == that.mTaskId
                && Objects.equals(mRequestTimestamp, that.mRequestTimestamp)
                && Objects.equals(mActivityComponent, that.mActivityComponent)
                && Objects.equals(mFocusedId, that.mFocusedId)
                && Objects.equals(mAutofillValue, that.mAutofillValue)
                && Objects.equals(mInlineSuggestionsRequest, that.mInlineSuggestionsRequest)
                && Objects.equals(
                        mAugmentedAutofillProxy.asBinder(), that.mAugmentedAutofillProxy.asBinder())
                && Objects.equals(mFillEventHistory, that.mFillEventHistory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                mSessionId,
                mTaskId,
                mRequestTimestamp,
                mActivityComponent,
                mFocusedId,
                mAutofillValue,
                mInlineSuggestionsRequest,
                mAugmentedAutofillProxy.asBinder(),
                mFillEventHistory);
    }

    @Override
    public String toString() {
        return "AutofillInlineRequestHint{"
                + "mSessionId="
                + mSessionId
                + ", mTaskId="
                + mTaskId
                + ", mRequestTimestamp="
                + mRequestTimestamp
                + ", mActivityComponent="
                + mActivityComponent
                + ", mFocusedId="
                + mFocusedId
                + ", mAutofillValue="
                + mAutofillValue
                + ", mInlineSuggestionsRequest="
                + mInlineSuggestionsRequest
                + ", mAugmentedAutofillProxy="
                + mAugmentedAutofillProxy
                + ", mFillEventHistory="
                + mFillEventHistory
                + '}';
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_SESSION_ID, mSessionId);
        bundle.putInt(KEY_TASK_ID, mTaskId);
        bundle.putSerializable(KEY_REQUEST_TIMESTAMP, mRequestTimestamp);
        bundle.putParcelable(KEY_ACTIVITY_COMPONENT, mActivityComponent);
        bundle.putParcelable(KEY_FOCUSED_ID, mFocusedId);
        bundle.putParcelable(KEY_AUTOFILL_VALUE, mAutofillValue);
        bundle.putParcelable(KEY_INLINE_SUGGESTIONS_REQUEST, mInlineSuggestionsRequest);
        bundle.putBinder(KEY_AUGMENTED_AUTOFILL_MANAGER_CLIENT, mAugmentedAutofillProxy.asBinder());
        bundle.putParcelable(KEY_FILL_EVENT_HISTORY, mFillEventHistory);
        return bundle;
    }

    /**
     * Proxy for requesting information from the augmented autofill service.
     *
     * <p>This class wraps the {@link IAugmentedAutofillManagerClient} binder, which is provided to
     * the main {@link AugmentedAutofillService} directly when an augmented autofill request is
     * sent. This proxy is used by {@link PersonalContextManager} to fetch additional autofill
     * information.
     *
     * @see AutofillInlineRequestHintConsumer#fetchFocusedViewNode()
     * @see AutofillInlineRequestHintConsumer#fetchViewCoordinates()
     */
    private static final class AugmentedAutofillProxyImpl implements AugmentedAutofillProxy {
        private final IAugmentedAutofillManagerClient mClient;

        /**
         * Creates an instance of {@link AugmentedAutofillProxyImpl} with a raw {@link IBinder} of a
         * {@link IAugmentedAutofillManagerClient}.
         */
        private AugmentedAutofillProxyImpl(@NonNull IBinder binder) {
            mClient = IAugmentedAutofillManagerClient.Stub.asInterface(binder);
        }

        /**
         * @see AutofillInlineRequestHintConsumer#fetchFocusedViewNode()
         */
        // TODO(b/491604658): change @NonNull to @Nullable
        @NonNull
        @Override
        public AssistStructure.ViewNode fetchFocusedViewNode(@NonNull AutofillId focusedId) {
            try {
                final AssistStructure.ViewNodeParcelable viewNodeParcelable =
                        mClient.getViewNodeParcelable(focusedId);
                if (viewNodeParcelable != null) {
                    return viewNodeParcelable.getViewNode();
                } else {
                    return null;
                }
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * @see AutofillInlineRequestHintConsumer#fetchViewCoordinates()
         */
        @NonNull
        @Override
        public Rect fetchViewCoordinates(@NonNull AutofillId focusedId) {
            try {
                return mClient.getViewCoordinates(focusedId);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @NonNull
        @Override
        public IBinder asBinder() {
            return mClient.asBinder();
        }
    }

    /** Builder used to create a {@link AutofillInlineRequestHint}. */
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private int mSessionId;
        private int mTaskId;
        private final Instant mRequestTimestamp;
        private final ComponentName mActivityComponent;
        private final AutofillId mFocusedId;
        private final AutofillValue mAutofillValue;
        private final InlineSuggestionsRequest mInlineSuggestionsRequest;
        private final AugmentedAutofillProxy mAugmentedAutofillProxy;
        private FillEventHistory mFillEventHistory;

        /**
         * Creates an instance of {@link AutofillInlineRequestHint.Builder}.
         * @param sessionId ID of the autofill session this request is associated with
         * @param taskId ID of the task of the activity associated with this request
         * @param requestTimestamp the timestamp of the request
         * @param activityComponent component name of the activity involved in autofill
         * @param focusedId ID for the node focused by autofill
         * @param autofillValue information on the view to be filled
         * @param inlineSuggestionsRequest request data for this hint
         * @param augmentedAutofillProxy the {@link IBinder} used for requesting information from
         *                               the augmented autofill service.
         */
        public Builder(int sessionId,
                int taskId,
                @NonNull Instant requestTimestamp,
                @NonNull ComponentName activityComponent,
                @NonNull AutofillId focusedId,
                @NonNull AutofillValue autofillValue,
                @NonNull InlineSuggestionsRequest inlineSuggestionsRequest,
                @NonNull IBinder augmentedAutofillProxy) {
            this(sessionId,
                    taskId,
                    requestTimestamp,
                    activityComponent,
                    focusedId,
                    autofillValue,
                    inlineSuggestionsRequest,
                    new AugmentedAutofillProxyImpl(requireNonNull(augmentedAutofillProxy)));
        }

        /**
         * Creates an instance of {@link AutofillInlineRequestHint.Builder}.
         *
         * @param sessionId ID of the autofill session this request is associated with
         * @param taskId ID of the task of the activity associated with this request
         * @param requestTimestamp the timestamp of the request
         * @param activityComponent component name of the activity involved in autofill
         * @param focusedId ID for the node focused by autofill
         * @param autofillValue information on the view to be filled
         * @param inlineSuggestionsRequest request data for this hint
         * @param augmentedAutofillProxy the {@link IAugmentedAutofillManagerClient} used for
         *                               requesting information from the augmented autofill service.
         * @hide
         */
        @TestApi
        public Builder(int sessionId,
                int taskId,
                @NonNull Instant requestTimestamp,
                @NonNull ComponentName activityComponent,
                @NonNull AutofillId focusedId,
                @NonNull AutofillValue autofillValue,
                @NonNull InlineSuggestionsRequest inlineSuggestionsRequest,
                @NonNull AugmentedAutofillProxy augmentedAutofillProxy) {
            mSessionId = sessionId;
            mTaskId = taskId;
            mRequestTimestamp = requireNonNull(requestTimestamp);
            mActivityComponent = requireNonNull(activityComponent);
            mFocusedId = requireNonNull(focusedId);
            mAutofillValue = requireNonNull(autofillValue);
            mInlineSuggestionsRequest = requireNonNull(inlineSuggestionsRequest);
            mAugmentedAutofillProxy  = requireNonNull(augmentedAutofillProxy);
        }

        /**
         * Sets the {@link FillEventHistory} for the most recent fill request that personal context
         * was used to fulfill.
         *
         * @param fillEventHistory fill event history for the previous request
         */
        @NonNull
        public Builder setFillEventHistory(@Nullable FillEventHistory fillEventHistory) {
            mFillEventHistory = fillEventHistory;
            return this;
        }

        /**
         * Adds a token to the resulting {@link AutofillInlineRequestHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /** Returns the built {@link AutofillInlineRequestHint}. */
        @NonNull
        public AutofillInlineRequestHint build() {
            return new AutofillInlineRequestHint(
                    mBaseBuilder.build(),
                    mSessionId,
                    mTaskId,
                    mRequestTimestamp,
                    mActivityComponent,
                    mFocusedId,
                    mAutofillValue,
                    mInlineSuggestionsRequest,
                    mAugmentedAutofillProxy,
                    mFillEventHistory);
        }
    }
}
